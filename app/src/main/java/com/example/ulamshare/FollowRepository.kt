package com.example.ulamshare

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
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
    fun sendFriendRequest(
        firestore: FirebaseFirestore,
        currentUser: FollowProfile,
        targetUser: FollowProfile,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (currentUser.uid.isBlank() || targetUser.uid.isBlank() || currentUser.uid == targetUser.uid) {
            onComplete(Result.failure(IllegalArgumentException("Invalid friend request target.")))
            return
        }

        val requestId = friendRequestId(currentUser.uid, targetUser.uid)
        val requestRef = firestore.collection(FRIEND_REQUESTS_COLLECTION).document(requestId)
        val payload = hashMapOf<String, Any>(
            "id" to requestId,
            "fromUserId" to currentUser.uid,
            "fromUserName" to currentUser.fullName,
            "toUserId" to targetUser.uid,
            "toUserName" to targetUser.fullName,
            "status" to FRIEND_REQUEST_PENDING,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        requestRef.set(payload, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Friends", "Friend request sent")
                FirestoreNotificationRepository.createNotification(
                    firestore = firestore,
                    recipientId = targetUser.uid,
                    recipientRole = targetUser.role,
                    senderId = currentUser.uid,
                    senderName = currentUser.fullName,
                    senderRole = currentUser.role.ifBlank { "user" },
                    type = FirestoreNotificationRepository.TYPE_FRIEND_REQUEST,
                    title = "Friend request",
                    message = "${currentUser.fullName} sent you a friend request.",
                    relatedUserId = currentUser.uid
                )
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

    fun cancelFriendRequest(
        firestore: FirebaseFirestore,
        currentUserId: String,
        targetUserId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        updateFriendRequestStatus(
            firestore = firestore,
            currentUserId = currentUserId,
            targetUserId = targetUserId,
            status = FRIEND_REQUEST_CANCELLED,
            onComplete = onComplete
        )
    }

    fun declineFriendRequest(
        firestore: FirebaseFirestore,
        currentUserId: String,
        targetUserId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        updateFriendRequestStatus(
            firestore = firestore,
            currentUserId = targetUserId,
            targetUserId = currentUserId,
            status = FRIEND_REQUEST_DECLINED,
            onComplete = onComplete
        )
    }

    fun acceptFriendRequest(
        firestore: FirebaseFirestore,
        currentUser: FollowProfile,
        requester: FollowProfile,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (currentUser.uid.isBlank() || requester.uid.isBlank() || currentUser.uid == requester.uid) {
            onComplete(Result.failure(IllegalArgumentException("Invalid friend request.")))
            return
        }

        val requestId = friendRequestId(requester.uid, currentUser.uid)
        val requestRef = firestore.collection(FRIEND_REQUESTS_COLLECTION).document(requestId)
        val currentRef = firestore.collection(USERS_COLLECTION).document(currentUser.uid)
        val requesterRef = firestore.collection(USERS_COLLECTION).document(requester.uid)
        val currentFriendRef = currentRef.collection(FRIENDS_COLLECTION).document(requester.uid)
        val requesterFriendRef = requesterRef.collection(FRIENDS_COLLECTION).document(currentUser.uid)

        firestore.runTransaction { transaction ->
            val currentSnapshot = transaction.get(currentRef)
            val requesterSnapshot = transaction.get(requesterRef)
            val alreadyFriends = transaction.get(currentFriendRef).exists()

            transaction.set(
                requestRef,
                mapOf(
                    "status" to FRIEND_REQUEST_ACCEPTED,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            transaction.set(currentFriendRef, requester.toFriendRecord(), SetOptions.merge())
            transaction.set(requesterFriendRef, currentUser.toFriendRecord(), SetOptions.merge())

            if (!alreadyFriends) {
                transaction.set(
                    currentRef,
                    mapOf("friendsCount" to numberToLong(currentSnapshot.get("friendsCount")) + 1L),
                    SetOptions.merge()
                )
                transaction.set(
                    requesterRef,
                    mapOf("friendsCount" to numberToLong(requesterSnapshot.get("friendsCount")) + 1L),
                    SetOptions.merge()
                )
            }
        }.addOnSuccessListener {
            Log.d("Friends", "Friend request accepted, updating friends only")
            FirestoreNotificationRepository.createNotification(
                firestore = firestore,
                recipientId = requester.uid,
                recipientRole = requester.role,
                senderId = currentUser.uid,
                senderName = currentUser.fullName,
                senderRole = currentUser.role.ifBlank { "user" },
                type = FirestoreNotificationRepository.TYPE_FRIEND_REQUEST_ACCEPTED,
                title = "Friend request accepted",
                message = "${currentUser.fullName} accepted your friend request.",
                relatedUserId = currentUser.uid
            )
            onComplete(Result.success(Unit))
        }.addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

    fun unfriend(
        firestore: FirebaseFirestore,
        currentUserId: String,
        targetUserId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) {
            onComplete(Result.failure(IllegalArgumentException("Invalid unfriend target.")))
            return
        }

        val currentRef = firestore.collection(USERS_COLLECTION).document(currentUserId)
        val targetRef = firestore.collection(USERS_COLLECTION).document(targetUserId)
        val currentFriendRef = currentRef.collection(FRIENDS_COLLECTION).document(targetUserId)
        val targetFriendRef = targetRef.collection(FRIENDS_COLLECTION).document(currentUserId)

        firestore.runTransaction { transaction ->
            val currentSnapshot = transaction.get(currentRef)
            val targetSnapshot = transaction.get(targetRef)
            val wasFriends = transaction.get(currentFriendRef).exists()

            transaction.delete(currentFriendRef)
            transaction.delete(targetFriendRef)

            if (wasFriends) {
                transaction.set(
                    currentRef,
                    mapOf("friendsCount" to (numberToLong(currentSnapshot.get("friendsCount")) - 1L).coerceAtLeast(0L)),
                    SetOptions.merge()
                )
                transaction.set(
                    targetRef,
                    mapOf("friendsCount" to (numberToLong(targetSnapshot.get("friendsCount")) - 1L).coerceAtLeast(0L)),
                    SetOptions.merge()
                )
            }
        }.addOnSuccessListener {
            Log.d("Friends", "Unfriended user, updating friends only")
            onComplete(Result.success(Unit))
        }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

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
            Log.d("Follow", "Followed user, updating following/followers only")
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
            Log.d("Follow", "Unfollowed user, updating following/followers only")
            onComplete(Result.success(Unit))
        }.addOnFailureListener { error ->
            onComplete(Result.failure(error))
        }
    }

    fun recalculateRelationshipCounts(
        firestore: FirebaseFirestore,
        userId: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onComplete(Result.failure(IllegalArgumentException("Missing userId.")))
            return
        }

        val userRef = firestore.collection(USERS_COLLECTION).document(userId)
        val friendsTask = userRef.collection(FRIENDS_COLLECTION).get()
        val followingTask = userRef.collection(FOLLOWING_COLLECTION).get()
        val followersTask = userRef.collection(FOLLOWERS_COLLECTION).get()

        Tasks.whenAllSuccess<QuerySnapshot>(
            friendsTask,
            followingTask,
            followersTask
        ).addOnSuccessListener { snapshots ->
            val friendsCount = snapshots.getOrNull(0)?.size() ?: 0
            val followingCount = snapshots.getOrNull(1)?.size() ?: 0
            val followersCount = snapshots.getOrNull(2)?.size() ?: 0

            userRef.set(
                mapOf(
                    "friendsCount" to friendsCount,
                    "followingCount" to followingCount,
                    "followersCount" to followersCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                Log.d(
                    "Friends",
                    "Relationship counts recalculated uid=$userId friends=$friendsCount following=$followingCount followers=$followersCount"
                )
                onComplete(Result.success(Unit))
            }.addOnFailureListener { error ->
                onComplete(Result.failure(error))
            }
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

    private fun FollowProfile.toFriendRecord(): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "fullName" to fullName,
            "email" to email,
            "profilePhotoUrl" to profilePhotoUrl,
            "profilePhotoLocalUri" to profilePhotoLocalUri,
            "role" to role,
            "status" to status,
            "friendedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun updateFriendRequestStatus(
        firestore: FirebaseFirestore,
        currentUserId: String,
        targetUserId: String,
        status: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || currentUserId == targetUserId) {
            onComplete(Result.failure(IllegalArgumentException("Invalid friend request.")))
            return
        }

        firestore.collection(FRIEND_REQUESTS_COLLECTION)
            .document(friendRequestId(currentUserId, targetUserId))
            .set(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
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
    private const val FRIENDS_COLLECTION = "friends"
    private const val FRIEND_REQUESTS_COLLECTION = "friend_requests"

    const val FRIEND_REQUEST_PENDING = "pending"
    const val FRIEND_REQUEST_ACCEPTED = "accepted"
    const val FRIEND_REQUEST_DECLINED = "declined"
    const val FRIEND_REQUEST_CANCELLED = "cancelled"

    fun friendRequestId(fromUserId: String, toUserId: String): String = "${fromUserId}_${toUserId}"
}
