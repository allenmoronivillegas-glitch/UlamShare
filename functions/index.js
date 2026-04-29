const admin = require("firebase-admin");
const crypto = require("crypto");
const { onValueCreated } = require("firebase-functions/v2/database");
const { HttpsError, onCall, onRequest } = require("firebase-functions/v2/https");

admin.initializeApp();

const OTP_EXPIRY_MS = 5 * 60 * 1000;
const OTP_RESEND_COOLDOWN_MS = 30 * 1000;
const OTP_MAX_ATTEMPTS = 5;

function json(res, status, body) {
  res.status(status).json(body);
}

function requestPath(req) {
  return String(req.path || req.url || "")
    .split("?")[0]
    .replace(/^\/+/, "");
}

function normalizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function generateOtp() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function hashValue(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function hashOtp(requestId, otp) {
  const pepper = process.env.OTP_PEPPER || "";
  return hashValue(`${requestId}:${otp}:${pepper}`);
}

function timestampToMillis(value) {
  if (!value) return 0;
  if (typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return Number(value) || 0;
}

function otpEmailHtml(otp) {
  return `
    <p>Your HopeGive verification code is:</p>
    <p style="font-size:28px;font-weight:700;letter-spacing:6px;">${otp}</p>
    <p>This code expires in 5 minutes. If you did not request it, you can ignore this email.</p>
  `;
}

function normalizeRole(value) {
  const raw = String(value || "").trim().toLowerCase();
  if (raw === "super admin" || raw === "super_admin" || raw === "superadmin") {
    return { label: "Super Admin", key: "super_admin" };
  }
  if (raw === "admin") {
    return { label: "Admin", key: "admin" };
  }
  if (raw === "moderator") {
    return { label: "Moderator", key: "moderator" };
  }
  return { label: "Viewer", key: "viewer" };
}

exports.otpApi = onRequest(
  {
    region: "asia-southeast1",
  },
  async (req, res) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    res.set("Access-Control-Allow-Methods", "POST, OPTIONS");

    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    if (req.method !== "POST") {
      json(res, 405, { success: false, message: "Method not allowed" });
      return;
    }

    const path = requestPath(req);
    const db = admin.firestore();

    try {
      if (path === "api/otp/send") {
        const userId = String(req.body && req.body.userId || "").trim();
        const destination = normalizeEmail(req.body && req.body.destination);
        const channel = String(req.body && req.body.channel || "email").trim().toLowerCase();

        if (channel !== "email") {
          json(res, 400, { success: false, message: "Only email OTP is supported right now." });
          return;
        }

        if (!isValidEmail(destination)) {
          json(res, 400, { success: false, message: "A valid email address is required." });
          return;
        }

        const now = Date.now();
        const rateLimitRef = db.collection("otp_rate_limits").doc(hashValue(destination));
        const rateLimitSnapshot = await rateLimitRef.get();
        const lastSentAt = timestampToMillis(rateLimitSnapshot.get("lastSentAt"));

        if (lastSentAt && now - lastSentAt < OTP_RESEND_COOLDOWN_MS) {
          json(res, 429, {
            success: false,
            message: "Please wait before requesting another OTP.",
          });
          return;
        }

        const requestId = crypto.randomUUID();
        const otp = generateOtp();
        const expiresAt = admin.firestore.Timestamp.fromMillis(now + OTP_EXPIRY_MS);
        const batch = db.batch();

        batch.set(db.collection("otp_requests").doc(requestId), {
          requestId,
          userId,
          destination,
          channel,
          hashedOtp: hashOtp(requestId, otp),
          expiresAt,
          used: false,
          attemptCount: 0,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        batch.set(rateLimitRef, {
          destination,
          lastSentAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });

        batch.set(db.collection("mail").doc(), {
          to: [destination],
          message: {
            subject: "Your HopeGive verification code",
            text: `Your HopeGive verification code is ${otp}. It expires in 5 minutes.`,
            html: otpEmailHtml(otp),
          },
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        await batch.commit();

        console.log("OTP request created", { requestId, destination, userId, channel });
        json(res, 200, { success: true, requestId, message: "OTP sent" });
        return;
      }

      if (path === "api/otp/verify") {
        const requestId = String(req.body && req.body.requestId || "").trim();
        const otp = String(req.body && req.body.otp || "").trim();

        if (!requestId || !/^\d{6}$/.test(otp)) {
          json(res, 400, {
            success: false,
            status: "INVALID_OR_EXPIRED",
            message: "Invalid or expired OTP",
          });
          return;
        }

        const requestRef = db.collection("otp_requests").doc(requestId);
        const result = await db.runTransaction(async (transaction) => {
          const snapshot = await transaction.get(requestRef);
          if (!snapshot.exists) {
            return { ok: false, reason: "missing" };
          }

          const data = snapshot.data() || {};
          const attemptCount = Number(data.attemptCount || 0);
          const expiresAt = timestampToMillis(data.expiresAt);
          const expired = !expiresAt || expiresAt < Date.now();

          if (data.used === true || expired || attemptCount >= OTP_MAX_ATTEMPTS) {
            return { ok: false, reason: "expired" };
          }

          if (data.hashedOtp !== hashOtp(requestId, otp)) {
            transaction.update(requestRef, {
              attemptCount: admin.firestore.FieldValue.increment(1),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            return { ok: false, reason: "invalid" };
          }

          transaction.update(requestRef, {
            used: true,
            verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
          return { ok: true };
        });

        if (result.ok) {
          json(res, 200, { success: true, status: "VERIFIED", message: "OTP verified" });
        } else {
          json(res, 400, {
            success: false,
            status: "INVALID_OR_EXPIRED",
            message: "Invalid or expired OTP",
          });
        }
        return;
      }

      json(res, 404, { success: false, message: "OTP endpoint not found." });
    } catch (error) {
      console.error("OTP API failed", error);
      json(res, 500, { success: false, message: "Failed to process OTP request" });
    }
  }
);

exports.syncCurrentUserRole = onCall(
  {
    region: "asia-southeast1",
  },
  async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "You must be signed in.");
    }

    const profileSnapshot = await admin.database().ref(`users/${uid}`).get();
    const profile = profileSnapshot.val();
    const role = normalizeRole(profile && profile.role);

    if (!["admin", "super_admin"].includes(role.key)) {
      throw new HttpsError("permission-denied", "Only Admin and Super Admin can sync admin role.");
    }

    const authUser = await admin.auth().getUser(uid);
    await admin.auth().setCustomUserClaims(uid, {
      ...(authUser.customClaims || {}),
      role: role.key,
    });

    await admin.firestore().collection("users").doc(uid).set(
      {
        uid,
        email: (profile && profile.email) || authUser.email || "",
        username:
          (profile && profile.username) ||
          authUser.displayName ||
          (authUser.email ? authUser.email.split("@")[0] : "Admin"),
        fullName:
          (profile && (profile.fullName || profile.username)) ||
          authUser.displayName ||
          "",
        role: role.label,
        roleKey: role.key,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    return { role: role.label, roleKey: role.key };
  }
);

exports.notifyOnNewCampaign = onValueCreated(
  {
    ref: "/campaigns/{campaignId}",
    region: "asia-southeast1",
  },
  async (event) => {
    const campaign = event.data.val();
    if (!campaign) {
      return;
    }

    const status = String(campaign.status || "").toLowerCase();
    const hidden = Boolean(campaign.hidden);

    if (status !== "active" || hidden) {
      console.log("Skipping notification for campaign", {
        campaignId: event.params.campaignId,
        status: campaign.status,
        hidden: campaign.hidden,
      });
      return;
    }

    const title = "New Campaign Available";
    const body = campaign.title || "A new campaign is now available.";

    await admin.messaging().send({
      topic: "campaigns",
      data: {
        title,
        body,
        campaignId: event.params.campaignId,
        screen: "home",
      },
      android: {
        priority: "high",
      },
    });

    console.log("Campaign notification sent", {
      campaignId: event.params.campaignId,
      title: campaign.title,
    });
  }
);
