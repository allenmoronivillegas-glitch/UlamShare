const admin = require("firebase-admin");
const { onValueCreated } = require("firebase-functions/v2/database");
const { HttpsError, onCall } = require("firebase-functions/v2/https");

admin.initializeApp();

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
