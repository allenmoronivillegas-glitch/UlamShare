package com.example.ulamshare

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object CampaignAssignmentManager {
    private const val TAG = "CampaignAssignment"
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
                // Safely extract campaign map
                val campaignData = document.get("campaign") as? Map<String, Any?>
                val existingCampaign = campaignData?.let { UserCampaign.fromMap(it) }
                
                if (existingCampaign != null) {
                    Log.d(TAG, "Existing campaign found for user: ${existingCampaign.campaignId}")
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

                Log.d(TAG, "No campaign in profile, fetching preferred...")
                fetchPreferredCampaign(
                    onSuccess = { campaign ->
                        val payload = profileSeed.toMutableMap()
                        if (campaign != null) {
                            Log.d(TAG, "Assigned new preferred campaign: ${campaign.campaignId}")
                            payload["campaign"] = campaign.toMap()
                            CampaignSessionManager.save(context, campaign)
                        } else {
                            Log.w(TAG, "No campaigns available to assign.")
                            CampaignSessionManager.clear(context)
                        }

                        // If user profile doesn't exist yet, we definitely want to set the seed
                        // Even if campaign is null, we want to save the user data
                        userRef.set(payload, SetOptions.merge())
                            .addOnSuccessListener { onComplete() }
                            .addOnFailureListener(onError)
                    },
                    onError = onError
                )
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
                    val preferred = resolvePreferredCampaign(snapshot)
                    onSuccess(preferred)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to fetch campaigns: ${error.message}")
                    onError(error.toException())
                }
            })
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
}
