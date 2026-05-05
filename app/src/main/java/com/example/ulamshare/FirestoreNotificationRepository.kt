package com.example.ulamshare

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

data class MentionedUser(
    val userId: String,
    val userName: String,
    val userRole: String = ""
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "userId" to userId,
            "userName" to userName
        )
    }
}

object FirestoreNotificationRepository {
    private const val TAG = "FirestoreNotifications"
    private const val USERS_COLLECTION = "users"
    private const val NOTIFICATIONS_COLLECTION = "notifications"

    const val TYPE_DONATION_SUCCESS = "donation_success"
    const val TYPE_NEW_DONATION_ADMIN = "new_donation_admin"
    const val TYPE_CAMPAIGN_ADDED = "campaign_added"
    const val TYPE_CAMPAIGN_UPDATED = "campaign_updated"
    const val TYPE_FRIEND_REQUEST = "friend_request"
    const val TYPE_FRIEND_REQUEST_ACCEPTED = "friend_request_accepted"
    const val TYPE_FRIEND_ADDED = "friend_added"
    const val TYPE_FRIEND_REMOVED = "friend_removed"
    const val TYPE_FOLLOWED = "followed"
    const val TYPE_UNFOLLOWED = "unfollowed"
    const val TYPE_MENTION_POST = "mention_post"
    const val TYPE_MENTION_COMMENT = "mention_comment"
    const val TYPE_MENTION_REPLY = "mention_reply"
    const val TYPE_POST_COMMENT = "post_comment"
    const val TYPE_COMMENT_REPLY = "comment_reply"
    const val TYPE_REPLY_REPLY = "reply_reply"
    const val TYPE_POST_REACTION = "post_reaction"
    const val TYPE_POST_HIDDEN = "post_hidden"
    const val TYPE_POST_DELETED = "post_deleted"
    const val TYPE_COMMENT_HIDDEN = "comment_hidden"
    const val TYPE_COMMENT_DELETED = "comment_deleted"
    const val TYPE_REPLY_HIDDEN = "reply_hidden"
    const val TYPE_REPLY_DELETED = "reply_deleted"

    fun createNotification(
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        recipientId: String,
        recipientRole: String = "",
        senderId: String,
        senderName: String,
        senderRole: String,
        type: String,
        title: String,
        message: String,
        relatedUserId: String = "",
        campaignId: String = "",
        campaignTitle: String = "",
        donationId: String = "",
        postId: String = "",
        commentId: String = "",
        replyId: String = "",
        replyingToReplyId: String = "",
        amount: Double = 0.0,
        allowSelfNotification: Boolean = false,
        onComplete: ((Result<Unit>) -> Unit)? = null
    ) {
        if (recipientId.isBlank() || senderId.isBlank() || message.isBlank()) {
            onComplete?.invoke(Result.success(Unit))
            return
        }
        if (!allowSelfNotification && recipientId == senderId) {
            onComplete?.invoke(Result.success(Unit))
            return
        }

        val notificationRef = firestore.collection(NOTIFICATIONS_COLLECTION).document()
        val payload = hashMapOf<String, Any>(
            "id" to notificationRef.id,
            "recipientId" to recipientId,
            "recipientRole" to recipientRole,
            "senderId" to senderId,
            "senderName" to senderName,
            "senderRole" to normalizeRole(senderRole),
            "type" to type,
            "title" to title,
            "message" to message,
            "relatedUserId" to relatedUserId,
            "campaignId" to campaignId,
            "campaignTitle" to campaignTitle,
            "donationId" to donationId,
            "postId" to postId,
            "commentId" to commentId,
            "replyId" to replyId,
            "amount" to amount,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (replyingToReplyId.isNotBlank()) payload["replyingToReplyId"] = replyingToReplyId

        notificationRef.set(payload, SetOptions.merge())
            .addOnSuccessListener { onComplete?.invoke(Result.success(Unit)) }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to create notification: $type", error)
                onComplete?.invoke(Result.failure(error))
            }
    }

    fun notifyAdminTeam(
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        senderId: String,
        senderName: String,
        senderRole: String,
        type: String,
        title: String,
        message: String,
        campaignId: String = "",
        campaignTitle: String = "",
        donationId: String = "",
        amount: Double = 0.0
    ) {
        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents
                    .mapNotNull { document ->
                        val uid = document.getString("uid").orEmpty().ifBlank { document.id }
                        val role = document.getString("role").orEmpty()
                            .ifBlank { document.getString("roleKey").orEmpty() }
                        if (uid.isBlank() || !isAdminTeamRole(role)) return@mapNotNull null
                        uid to role
                    }
                    .distinctBy { it.first }
                    .forEach { (recipientId, role) ->
                        createNotification(
                            firestore = firestore,
                            recipientId = recipientId,
                            recipientRole = role,
                            senderId = senderId,
                            senderName = senderName,
                            senderRole = senderRole,
                            type = type,
                            title = title,
                            message = message,
                            campaignId = campaignId,
                            campaignTitle = campaignTitle,
                            donationId = donationId,
                            amount = amount
                        )
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to load admin notification recipients", error)
            }
    }

    fun resolveMentionedUsers(
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        text: String,
        senderId: String,
        onComplete: (List<MentionedUser>) -> Unit
    ) {
        val body = text.trim()
        if (!body.contains("@")) {
            onComplete(emptyList())
            return
        }

        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val normalizedBody = body.lowercase(Locale.getDefault())
                val mentioned = snapshot.documents.mapNotNull { document ->
                    val userId = document.getString("uid").orEmpty().ifBlank { document.id }
                    if (userId.isBlank() || userId == senderId) return@mapNotNull null

                    val fullName = document.getString("fullName").orEmpty()
                        .ifBlank { document.getString("displayName").orEmpty() }
                        .ifBlank { document.getString("name").orEmpty() }
                    val candidates = listOf(fullName, fullName.substringBefore(" "))
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

                    val matched = candidates.any { candidate ->
                        normalizedBody.contains("@${candidate.lowercase(Locale.getDefault())}")
                    }
                    if (!matched) return@mapNotNull null

                    MentionedUser(
                        userId = userId,
                        userName = fullName.ifBlank { "HopeGive User" },
                        userRole = document.getString("role").orEmpty()
                            .ifBlank { document.getString("roleKey").orEmpty() }
                    )
                }.distinctBy { it.userId }

                onComplete(mentioned)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to resolve mentioned users", error)
                onComplete(emptyList())
            }
    }

    fun createMentionNotifications(
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        mentionedUsers: List<MentionedUser>,
        excludedRecipientIds: Set<String> = emptySet(),
        senderId: String,
        senderName: String,
        senderRole: String,
        type: String,
        title: String,
        message: String,
        postId: String = "",
        commentId: String = "",
        replyId: String = "",
        replyingToReplyId: String = "",
        campaignId: String = "",
        campaignTitle: String = ""
    ) {
        mentionedUsers
            .filterNot { it.userId in excludedRecipientIds }
            .distinctBy { it.userId }
            .forEach { user ->
                createNotification(
                    firestore = firestore,
                    recipientId = user.userId,
                    recipientRole = user.userRole,
                    senderId = senderId,
                    senderName = senderName,
                    senderRole = senderRole,
                    type = type,
                    title = title,
                    message = message,
                    relatedUserId = senderId,
                    postId = postId,
                    commentId = commentId,
                    replyId = replyId,
                    replyingToReplyId = replyingToReplyId,
                    campaignId = campaignId,
                    campaignTitle = campaignTitle
                )
            }
    }

    private fun isAdminTeamRole(role: String): Boolean {
        return normalizeRole(role) in setOf("admin", "super_admin", "moderator")
    }

    fun normalizeRole(role: String): String {
        return role.trim()
            .lowercase(Locale.getDefault())
            .replace(" ", "_")
            .ifBlank { "user" }
    }
}
