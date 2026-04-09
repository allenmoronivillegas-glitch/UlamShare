package com.example.ulamshare

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object CampaignAssignmentManager {
    private const val DATABASE_URL = "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
    private const val USERS_COLLECTION = "users"
    private const val CAMPAIGNS_NODE = "campaigns"

    fun syncForAuthenticatedUser(
        context: Context,
        user: FirebaseUser,
        profileSeed: Map<String, Any> = emptyMap(),
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userRef = Firebase.firestore.collection(USERS_COLLECTION).document(user.uid)

        userRef.get()
            .addOnSuccessListener { document ->
                val existingCampaign = UserCampaign.fromMap(document.get("campaign").toCampaignMap() ?: emptyMap())
                if (existingCampaign != null) {
                    CampaignSessionManager.save(context, existingCampaign)
                    if (profileSeed.isNotEmpty()) {
                        userRef.set(profileSeed + ("campaign" to existingCampaign.toMap()), SetOptions.merge())
                            .addOnSuccessListener { onComplete() }
                            .addOnFailureListener(onError)
                    } else {
                        onComplete()
                    }
                    return@addOnSuccessListener
                }

                fetchPreferredCampaign(
                    onSuccess = { campaign ->
                        val payload = profileSeed.toMutableMap()
                        campaign?.let { resolved ->
                            payload["campaign"] = resolved.toMap()
                            CampaignSessionManager.save(context, resolved)
                        } ?: CampaignSessionManager.clear(context)

                        if (campaign == null && document.exists()) {
                            userRef.set(payload, SetOptions.merge())
                                .addOnSuccessListener { onComplete() }
                                .addOnFailureListener(onError)
                            return@fetchPreferredCampaign
                        }

                        if (payload.isEmpty()) {
                            onComplete()
                        } else {
                            userRef.set(payload, SetOptions.merge())
                                .addOnSuccessListener { onComplete() }
                                .addOnFailureListener(onError)
                        }
                    },
                    onError = onError
                )
            }
            .addOnFailureListener(onError)
    }

    fun ensureCampaignForAuthenticatedUser(
        context: Context,
        user: FirebaseUser,
        profileSeed: Map<String, Any> = emptyMap(),
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = { onComplete() }
    ) {
        Firebase.firestore.collection(USERS_COLLECTION).document(user.uid).get()
            .addOnSuccessListener { document ->
                val campaign = UserCampaign.fromMap(document.get("campaign").toCampaignMap() ?: emptyMap())
                if (campaign != null) {
                    CampaignSessionManager.save(context, campaign)
                    if (profileSeed.isNotEmpty()) {
                        Firebase.firestore.collection(USERS_COLLECTION).document(user.uid)
                            .set(profileSeed + ("campaign" to campaign.toMap()), SetOptions.merge())
                            .addOnSuccessListener { onComplete() }
                            .addOnFailureListener(onError)
                    } else {
                        onComplete()
                    }
                } else {
                    syncForAuthenticatedUser(
                        context = context,
                        user = user,
                        profileSeed = profileSeed,
                        onComplete = onComplete,
                        onError = onError
                    )
                }
            }
            .addOnFailureListener(onError)
    }

    fun fetchPreferredCampaign(
        onSuccess: (UserCampaign?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        FirebaseDatabase.getInstance(DATABASE_URL)
            .getReference(CAMPAIGNS_NODE)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onSuccess(resolvePreferredCampaign(snapshot))
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.toException())
                }
            })
    }

    fun resolvePreferredCampaign(snapshot: DataSnapshot): UserCampaign? {
        return CampaignVisibility.filterVisibleCampaigns(snapshot, "CampaignAssignmentManager")
            .visibleCampaigns
            .firstOrNull()
            ?.toUserCampaign()
    }

    private fun Campaign.toUserCampaign(): UserCampaign {
        return UserCampaign(
            campaignId = campaignId,
            title = title,
            description = description,
            status = status,
            category = cat,
            assignedAt = System.currentTimeMillis()
        )
    }

    private fun Any?.toCampaignMap(): Map<String, Any?>? {
        val rawMap = this as? Map<*, *> ?: return null
        return rawMap.entries
            .filter { it.key is String }
            .associate { (key, value) -> key as String to value }
    }
}
