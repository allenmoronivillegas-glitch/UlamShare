package com.example.ulamshare

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Locale

object CampaignAssignmentManager {
    private const val TAG = "CampaignAssignment"
    private const val DATABASE_URL = "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
    private const val USERS_COLLECTION = "users"
    private const val CAMPAIGN_ASSIGNMENTS_COLLECTION = "campaign_assignments"
    private const val CAMPAIGNS_NODE = "campaigns"

    fun syncForAuthenticatedUser(
        context: Context,
        user: FirebaseUser,
        profileSeed: Map<String, Any> = emptyMap(),
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val firestore = Firebase.firestore
        val userRef = firestore.collection(USERS_COLLECTION).document(user.uid)

        userRef.get()
            .addOnSuccessListener { document ->
                val profilePayload = cleanUserProfilePayload(
                    userId = user.uid,
                    profileSeed = profileSeed,
                    existingRole = document.getString("role").orEmpty()
                        .ifBlank { document.getString("roleKey").orEmpty() },
                    isNewUser = !document.exists(),
                    existingFields = { key -> document.get(key) }
                )

                val legacyCampaign = legacyCampaignFromUserDocument(document.get("campaign"))
                if (legacyCampaign != null) {
                    Log.d(TAG, "Migrating legacy user campaign assignment uid=${user.uid} campaignId=${legacyCampaign.campaignId}")
                    CampaignSessionManager.save(context, legacyCampaign)
                    saveUserProfileAndAssignment(
                        firestore = firestore,
                        userId = user.uid,
                        profilePayload = profilePayload,
                        campaign = legacyCampaign,
                        onComplete = onComplete,
                        onError = onError
                    )
                    return@addOnSuccessListener
                }

                loadActiveAssignment(
                    firestore = firestore,
                    userId = user.uid,
                    onLoaded = { existingAssignment ->
                        if (existingAssignment != null) {
                            Log.d(TAG, "Existing campaign assignment found: ${existingAssignment.campaignId}")
                            CampaignSessionManager.save(context, existingAssignment)
                            saveUserProfileOnly(
                                firestore = firestore,
                                userId = user.uid,
                                profilePayload = profilePayload,
                                onComplete = onComplete,
                                onError = onError
                            )
                            return@loadActiveAssignment
                        }

                        assignPreferredCampaign(
                            context = context,
                            firestore = firestore,
                            userId = user.uid,
                            profilePayload = profilePayload,
                            onComplete = onComplete,
                            onError = onError
                        )
                    },
                    onError = { error ->
                        Log.w(TAG, "Unable to load campaign assignment; assigning preferred fallback", error)
                        assignPreferredCampaign(
                            context = context,
                            firestore = firestore,
                            userId = user.uid,
                            profilePayload = profilePayload,
                            onComplete = onComplete,
                            onError = onError
                        )
                    }
                )
            }
            .addOnFailureListener(onError)
    }

    private fun assignPreferredCampaign(
        context: Context,
        firestore: FirebaseFirestore,
        userId: String,
        profilePayload: Map<String, Any>,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        Log.d(TAG, "No campaign assignment found, fetching preferred...")
        fetchPreferredCampaign(
            onSuccess = { campaign ->
                if (campaign != null) {
                    Log.d(TAG, "Assigned preferred campaign: ${campaign.campaignId}")
                    CampaignSessionManager.save(context, campaign)
                    saveUserProfileAndAssignment(
                        firestore = firestore,
                        userId = userId,
                        profilePayload = profilePayload,
                        campaign = campaign,
                        onComplete = onComplete,
                        onError = onError
                    )
                } else {
                    Log.w(TAG, "No campaigns available to assign.")
                    CampaignSessionManager.clear(context)
                    saveUserProfileOnly(
                        firestore = firestore,
                        userId = userId,
                        profilePayload = profilePayload,
                        onComplete = onComplete,
                        onError = onError
                    )
                }
            },
            onError = onError
        )
    }

    private fun saveUserProfileOnly(
        firestore: FirebaseFirestore,
        userId: String,
        profilePayload: Map<String, Any>,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION).document(userId)
            .set(profilePayload, SetOptions.merge())
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to save authenticated user profile uid=$userId", error)
                onError(error)
            }
    }

    private fun saveUserProfileAndAssignment(
        firestore: FirebaseFirestore,
        userId: String,
        profilePayload: Map<String, Any>,
        campaign: UserCampaign,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userRef = firestore.collection(USERS_COLLECTION).document(userId)
        val assignmentRef = userRef.collection(CAMPAIGN_ASSIGNMENTS_COLLECTION)
            .document(campaign.campaignId)
        val assignmentPayload = campaign.toAssignmentMap(
            userId = userId,
            assignmentId = campaign.campaignId
        )

        firestore.runBatch { batch ->
            batch.set(userRef, profilePayload, SetOptions.merge())
            batch.set(assignmentRef, assignmentPayload, SetOptions.merge())
        }.addOnSuccessListener {
            onComplete()
        }.addOnFailureListener { error ->
            Log.e(TAG, "Failed to save user campaign assignment uid=$userId campaignId=${campaign.campaignId}", error)
            onError(error)
        }
    }

    private fun loadActiveAssignment(
        firestore: FirebaseFirestore,
        userId: String,
        onLoaded: (UserCampaign?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CAMPAIGN_ASSIGNMENTS_COLLECTION)
            .whereEqualTo("status", "active")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val assignment = snapshot.documents.firstOrNull()?.let { document ->
                    UserCampaign(
                        campaignId = document.getString("campaignId").orEmpty(),
                        title = document.getString("campaignTitle").orEmpty(),
                        description = document.getString("campaignDescription").orEmpty(),
                        status = document.getString("campaignStatus").orEmpty(),
                        category = document.getString("campaignCategory").orEmpty(),
                        assignedAt = document.getLong("assignedAtMillis") ?: 0L
                    )
                }?.takeIf { it.campaignId.isNotBlank() }
                onLoaded(assignment)
            }
            .addOnFailureListener(onError)
    }

    private fun cleanUserProfilePayload(
        userId: String,
        profileSeed: Map<String, Any>,
        existingRole: String,
        isNewUser: Boolean,
        existingFields: (String) -> Any?
    ): MutableMap<String, Any> {
        val payload = profileSeed.toMutableMap()
        payload["uid"] = userId
        payload["isActiveUser"] = true
        payload["isDuplicate"] = false
        payload["recordType"] = "user"
        payload["updatedAt"] = FieldValue.serverTimestamp()

        if (isNewUser) {
            payload.putIfAbsent("createdAt", FieldValue.serverTimestamp())
        }
        if (existingRole.isBlank() && !payload.containsKey("role")) {
            payload["role"] = "user"
        }

        putZeroIfMissing(payload, "friendsCount", existingFields)
        putZeroIfMissing(payload, "followingCount", existingFields)
        putZeroIfMissing(payload, "followersCount", existingFields)
        putZeroIfMissing(payload, "totalDonated", existingFields)
        putZeroIfMissing(payload, "donationsCount", existingFields)
        putZeroIfMissing(payload, "campaignsDonatedCount", existingFields)

        payload.remove("campaign")
        return payload
    }

    private fun putZeroIfMissing(
        payload: MutableMap<String, Any>,
        key: String,
        existingFields: (String) -> Any?
    ) {
        if (!payload.containsKey(key) && existingFields(key) == null) {
            payload[key] = 0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun legacyCampaignFromUserDocument(value: Any?): UserCampaign? {
        return (value as? Map<String, Any?>)?.let { UserCampaign.fromMap(it) }
    }

    private fun UserCampaign.toAssignmentMap(
        userId: String,
        assignmentId: String
    ): Map<String, Any> {
        return mapOf(
            "id" to assignmentId,
            "userId" to userId,
            "campaignId" to campaignId,
            "campaignTitle" to title,
            "campaignDescription" to description,
            "campaignCategory" to category,
            "campaignStatus" to status,
            "assignedAtMillis" to assignedAt,
            "assignedAt" to FieldValue.serverTimestamp(),
            "status" to "active",
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun fetchPreferredCampaign(
        onSuccess: (UserCampaign?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        FirebaseDatabase.getInstance(DATABASE_URL)
            .getReference(CAMPAIGNS_NODE)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val preferred = resolvePreferredCampaign(snapshot)
                    onSuccess(preferred)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to fetch campaigns: ${error.message}")
                    onError(error.toException())
                }
            })
    }

    fun migrateUserCampaignFields(
        firestore: FirebaseFirestore = Firebase.firestore,
        onComplete: (Result<Int>) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val documents = snapshot.documents
                val canonicalByIdentity = resolveCanonicalUsers(documents)
                val batch = firestore.batch()
                var writeCount = 0

                documents.forEach { document ->
                    val uid = document.getString("uid").orEmpty()
                    val emailKey = normalizeEmail(document.getString("email"))
                    val identityKey = uid.ifBlank { emailKey }
                    val userRef = document.reference

                    if (uid.isBlank()) {
                        batch.set(
                            userRef,
                            mapOf(
                                "isActiveUser" to false,
                                "isDuplicate" to false,
                                "recordType" to "legacy_or_invalid",
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        writeCount++
                        return@forEach
                    }

                    val canonical = canonicalByIdentity[identityKey]
                    if (canonical != null && canonical.id != document.id) {
                        batch.set(
                            userRef,
                            mapOf(
                                "isActiveUser" to false,
                                "isDuplicate" to true,
                                "duplicateOf" to canonical.getString("uid").orEmpty().ifBlank { canonical.id },
                                "recordType" to "duplicate_user",
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        writeCount++
                        return@forEach
                    }

                    val profileUpdate = mutableMapOf<String, Any>(
                        "uid" to uid,
                        "isActiveUser" to true,
                        "isDuplicate" to false,
                        "recordType" to "user",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (document.get("friendsCount") == null) profileUpdate["friendsCount"] = 0
                    if (document.get("followingCount") == null) profileUpdate["followingCount"] = 0
                    if (document.get("followersCount") == null) profileUpdate["followersCount"] = 0
                    if (document.get("totalDonated") == null) profileUpdate["totalDonated"] = 0
                    if (document.get("donationsCount") == null) profileUpdate["donationsCount"] = 0
                    if (document.get("campaignsDonatedCount") == null) profileUpdate["campaignsDonatedCount"] = 0
                    if (document.getString("role").orEmpty().isBlank()) profileUpdate["role"] = "user"

                    batch.set(userRef, profileUpdate, SetOptions.merge())
                    writeCount++

                    val campaign = legacyCampaignFromUserDocument(document.get("campaign"))
                    if (campaign != null) {
                        val assignmentRef = userRef.collection(CAMPAIGN_ASSIGNMENTS_COLLECTION)
                            .document(campaign.campaignId)
                        batch.set(
                            assignmentRef,
                            campaign.toAssignmentMap(userId = uid, assignmentId = campaign.campaignId) +
                                mapOf("legacySource" to "users.campaign"),
                            SetOptions.merge()
                        )
                        batch.set(
                            userRef,
                            mapOf("legacyCampaignMigratedAt" to FieldValue.serverTimestamp()),
                            SetOptions.merge()
                        )
                        writeCount += 2
                    }
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "Migrated user campaign fields writes=$writeCount")
                        onComplete(Result.success(writeCount))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to migrate user campaign fields", error)
                        onComplete(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to load users for campaign migration", error)
                onComplete(Result.failure(error))
            }
    }

    private fun resolvePreferredCampaign(snapshot: DataSnapshot): UserCampaign? {
        val result = CampaignVisibility.filterVisibleCampaigns(snapshot, TAG)
        return result.visibleCampaigns.firstOrNull()?.let { campaign ->
            UserCampaign(
                campaignId = campaign.campaignId ?: "",
                title = campaign.title ?: "Untitled",
                description = campaign.description ?: "",
                status = campaign.status ?: "Active",
                category = campaign.cat ?: "",
                assignedAt = System.currentTimeMillis()
            )
        }
    }

    private fun resolveCanonicalUsers(documents: List<DocumentSnapshot>): Map<String, DocumentSnapshot> {
        val result = mutableMapOf<String, DocumentSnapshot>()
        documents.forEach { document ->
            val uid = document.getString("uid").orEmpty()
            val emailKey = normalizeEmail(document.getString("email"))
            val identityKey = uid.ifBlank { emailKey }
            if (identityKey.isBlank()) return@forEach

            val current = result[identityKey]
            if (current == null || canonicalScore(document) > canonicalScore(current)) {
                result[identityKey] = document
            }
        }
        return result
    }

    private fun canonicalScore(document: DocumentSnapshot): Long {
        val uid = document.getString("uid").orEmpty()
        val hasName = document.getString("fullName").orEmpty().isNotBlank() ||
            document.getString("displayName").orEmpty().isNotBlank()
        val updatedAt = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
        return listOf(
            if (uid.isNotBlank() && document.id == uid) 1_000_000L else 0L,
            if (document.getBoolean("isActiveUser") == true) 100_000L else 0L,
            if (document.getBoolean("isDuplicate") != true) 10_000L else 0L,
            if (hasName) 1_000L else 0L,
            updatedAt.coerceAtMost(999L)
        ).sum()
    }

    private fun normalizeEmail(value: String?): String {
        return value.orEmpty().trim().lowercase(Locale.US)
    }
}
