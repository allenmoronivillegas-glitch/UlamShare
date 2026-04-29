package com.example.ulamshare

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class FollowProfile(
    val uid: String,
    val fullName: String,
    val email: String = "",
    val profilePhotoUrl: String = "",
    val profilePhotoLocalUri: String = "",
    val role: String = "",
    val status: String = ""
)

object FollowRepository {
    fun follow(
        firestore: FirebaseFirestore,
        currentUser: FollowProfile,
        targetUser: FollowProfile,
        notificationType: String = FirestoreNotificationRepository.TYPE_FOLLOWED,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (currentUser.uid.isBlank() || targetUser.uid.isBlank() || currentUser.uid == targetUser.uid) {
            onComplete(Result.failure(IllegalArgumentException("Invalid follow target.")))
            return
        }

        val currentRef = firestore.collection(USERS_COLLECTION).document(currentUser.uid)
        val targetRef = firestore.collection(USERS_COLLECTION).document(targetUser.uid)
        val followingRef = currentRef.collection(FOLLOWING_COLLECTION).document(targetUser.uid)
        val followerRef = targetRef.collection(FOLLOWERS_COLLECTION).document(currentUser.uid)

        firestore.runTransaction { transaction ->
            val currentSnapshot = transaction.get(currentRef)
            val targetSnapshot = transaction.get(targetRef)
            val alreadyFollowing = transaction.get(followingRef).exists()

            transaction.set(followingRef, targetUser.toFollowRecord(), SetOptions.merge())
            transaction.set(followerRef, currentUser.toFollowRecord(), SetOptions.merge())

            if (!alreadyFollowing) {
                transaction.set(
                    currentRef,
                    mapOf("followingCount" to numberToLong(currentSnapshot.get("followingCount")) + 1L),
                    SetOptions.merge()
                )
                transaction.set(
                    targetRef,
                    mapOf("followersCount" to numberToLong(targetSnapshot.get("followersCount")) + 1L),
                    SetOptions.merge()
                )
            }
        }.addOnSuccessListener {
            createFollowNotification(
                firestore = firestore,
                currentUser = currentUser,
                targetUser = targetUser,
                notificationType = notificationType
            )
            onComplete(Result.success(Unit))
        }.addOnFailureListener { error ->
            onComplete(Result.failure(error))
        }
    }

    fun unfollow(
        firestore: FirebaseFirestore,
        currentUserId: String,
        targetUserId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) {
            onComplete(Result.failure(IllegalArgumentException("Invalid unfollow target.")))
            return
        }

        val currentRef = firestore.collection(USERS_COLLECTION).document(currentUserId)
        val targetRef = firestore.collection(USERS_COLLECTION).document(targetUserId)
        val followingRef = currentRef.collection(FOLLOWING_COLLECTION).document(targetUserId)
        val followerRef = targetRef.collection(FOLLOWERS_COLLECTION).document(currentUserId)

        firestore.runTransaction { transaction ->
            val currentSnapshot = transaction.get(currentRef)
            val targetSnapshot = transaction.get(targetRef)
            val wasFollowing = transaction.get(followingRef).exists()

            transaction.delete(followingRef)
            transaction.delete(followerRef)

            if (wasFollowing) {
                transaction.set(
                    currentRef,
                    mapOf(
                        "followingCount" to (numberToLong(currentSnapshot.get("followingCount")) - 1L)
                            .coerceAtLeast(0L)
                    ),
                    SetOptions.merge()
                )
                transaction.set(
                    targetRef,
                    mapOf(
                        "followersCount" to (numberToLong(targetSnapshot.get("followersCount")) - 1L)
                            .coerceAtLeast(0L)
                    ),
                    SetOptions.merge()
                )
            }
        }.addOnSuccessListener {
            onComplete(Result.success(Unit))
        }.addOnFailureListener { error ->
            onComplete(Result.failure(error))
        }
    }

    private fun FollowProfile.toFollowRecord(): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "fullName" to fullName,
            "email" to email,
            "profilePhotoUrl" to profilePhotoUrl,
            "profilePhotoLocalUri" to profilePhotoLocalUri,
            "role" to role,
            "status" to status,
            "followedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun createFollowNotification(
        firestore: FirebaseFirestore,
        currentUser: FollowProfile,
        targetUser: FollowProfile,
        notificationType: String
    ) {
        val isFriendNotification = notificationType == FirestoreNotificationRepository.TYPE_FRIEND_ADDED
        FirestoreNotificationRepository.createNotification(
            firestore = firestore,
            recipientId = targetUser.uid,
            recipientRole = targetUser.role,
            senderId = currentUser.uid,
            senderName = currentUser.fullName,
            senderRole = currentUser.role.ifBlank { "user" },
            type = notificationType,
            title = if (isFriendNotification) "New friend" else "New follower",
            message = if (isFriendNotification) {
                "${currentUser.fullName} added you as a friend."
            } else {
                "${currentUser.fullName} started following you."
            },
            relatedUserId = currentUser.uid
        )
    }

    private fun numberToLong(value: Any?): Long {
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private const val USERS_COLLECTION = "users"
    private const val FOLLOWING_COLLECTION = "following"
    private const val FOLLOWERS_COLLECTION = "followers"
}
