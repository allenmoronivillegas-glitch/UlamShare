package com.example.ulamshare

import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale

class CampaignFeedRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun listenToSettings(
        onUpdate: (CampaignFeedSettings) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(SETTINGS_COLLECTION)
            .document(CAMPAIGN_FEED_SETTINGS_DOCUMENT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                onUpdate(mapSettings(snapshot))
            }
    }

    fun listenToPosts(
        currentUserId: String?,
        onUpdate: (List<CampaignFeedPost>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return postsCollection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val posts = snapshot?.documents.orEmpty().map(::mapPost)
                if (currentUserId.isNullOrBlank() || posts.isEmpty()) {
                    onUpdate(posts)
                    return@addSnapshotListener
                }

                decorateReactionState(posts, currentUserId, onUpdate)
            }
    }

    fun listenToComments(
        postId: String,
        onUpdate: (List<CampaignPostComment>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return postDocument(postId)
            .collection(COMMENTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                val commentDocuments = snapshot?.documents.orEmpty()
                if (commentDocuments.isEmpty()) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val commentsWithIndex = mutableListOf<Pair<Int, CampaignPostComment>>()
                var remaining = commentDocuments.size
                var reportedError = false
                commentDocuments.forEachIndexed { index, document ->
                    document.reference
                        .collection(REPLIES_COLLECTION)
                        .orderBy("createdAt", Query.Direction.ASCENDING)
                        .get()
                        .addOnSuccessListener { repliesSnapshot ->
                            val replies = repliesSnapshot.documents.map(::mapReply)
                            commentsWithIndex += index to mapComment(document).copy(replies = replies)
                        }
                        .addOnFailureListener { replyError ->
                            if (!reportedError) {
                                reportedError = true
                                onError(replyError)
                            }
                        }
                        .addOnCompleteListener {
                            remaining -= 1
                            if (remaining == 0 && !reportedError) {
                                onUpdate(
                                    commentsWithIndex
                                        .sortedBy { it.first }
                                        .map { it.second }
                                )
                            }
                        }
                }
            }
    }

    fun loadReactions(
        postId: String,
        onComplete: (Result<List<CampaignPostReaction>>) -> Unit
    ) {
        postDocument(postId)
            .collection(REACTIONS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                onComplete(
                    Result.success(
                        snapshot.documents
                            .map(::mapReaction)
                            .sortedByDescending { it.updatedAt.takeIf { value -> value > 0L } ?: it.createdAt }
                    )
                )
            }
            .addOnFailureListener { error ->
                onComplete(Result.failure(error))
            }
    }

    fun createPost(
        author: CampaignPostAuthor,
        draft: CampaignComposerDraft,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val postRef = postsCollection().document()
        FirestoreNotificationRepository.resolveMentionedUsers(
            firestore = firestore,
            text = draft.text,
            senderId = author.id
        ) { mentionedUsers ->
        val writePost: (String) -> Unit = { imageUrl ->
            val postType = draft.resolvedPostType()
            val isLiveCampaign = postType == CampaignFeedPost.TYPE_LIVE_CAMPAIGN
            val payload = hashMapOf<String, Any>(
                "id" to postRef.id,
                "authorId" to author.id,
                "authorName" to author.name,
                "authorRole" to normalizeRole(author.role),
                "category" to normalizeCategory(
                    raw = draft.category,
                    authorRole = normalizeRole(author.role),
                    postType = postType
                ),
                "postTarget" to normalizePostTarget(draft.postTarget),
                "postType" to postType,
                "text" to draft.text.trim(),
                "imageUrl" to imageUrl,
                "linkedCampaignId" to draft.linkedCampaignId,
                "linkedCampaignTitle" to draft.linkedCampaignTitle,
                "linkedCampaignCategory" to draft.linkedCampaignCategory,
                "linkedCampaignEmoji" to draft.linkedCampaignEmoji,
                "campaignTitle" to if (isLiveCampaign) draft.campaignTitle.trim() else "",
                "campaignGoal" to if (isLiveCampaign) draft.campaignGoal.coerceAtLeast(0L) else 0L,
                "campaignRaised" to if (isLiveCampaign) draft.campaignRaised.coerceAtLeast(0L) else 0L,
                "campaignStatus" to if (isLiveCampaign) normalizeStatus(draft.campaignStatus) else "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "reactCount" to 0,
                "commentCount" to 0,
                "shareCount" to 0,
                "isLiveCampaign" to isLiveCampaign,
                "moderationStatus" to CampaignFeedPost.MODERATION_ACTIVE
            )
            if (mentionedUsers.isNotEmpty()) {
                payload["mentionedUsers"] = mentionedUsers.map { it.toMap() }
            }

            Log.d(TAG, "Saving campaign post payload to campaign_posts/${postRef.id}: $payload")

            postRef.set(payload)
                .addOnSuccessListener {
                    FirestoreNotificationRepository.createMentionNotifications(
                        firestore = firestore,
                        mentionedUsers = mentionedUsers,
                        senderId = author.id,
                        senderName = author.name,
                        senderRole = normalizeRole(author.role),
                        type = FirestoreNotificationRepository.TYPE_MENTION_POST,
                        title = "New mention",
                        message = "${author.name} mentioned you in a post.",
                        postId = postRef.id
                    )
                    onComplete(Result.success(Unit))
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Firestore post save failed", error)
                    Log.e(TAG, "Firestore post save failed details: ${firebaseErrorDetails(error)}")
                    onComplete(Result.failure(CampaignFirestoreSaveException(error)))
                }
        }

        val imageUri = draft.imageUri
        if (imageUri == null) {
            writePost("")
        } else {
            uploadPostImage(postRef.id, imageUri) { result ->
                result
                    .onSuccess { imageUrl -> writePost(imageUrl) }
                    .onFailure { error -> onComplete(Result.failure(error)) }
            }
        }
        }
    }

    fun toggleReaction(
        post: CampaignFeedPost,
        postId: String,
        actorId: String,
        actorName: String,
        actorRole: String,
        reactionType: String = DEFAULT_REACTION_TYPE,
        onComplete: (Result<String>) -> Unit
    ) {
        val postRef = postDocument(postId)
        val reactionRef = postRef.collection(REACTIONS_COLLECTION).document(actorId)
        val normalizedReactionType = normalizeReactionType(reactionType)

        firestore.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)
            val reactionSnapshot = transaction.get(reactionRef)
            val currentCount = numberToInt(postSnapshot.get("reactCount"))
            val reactionCounts = mapReactionCounts(postSnapshot.get("reactionCounts"))
            val existingType = normalizeReactionType(reactionSnapshot.getString("type"))

            if (reactionSnapshot.exists() && existingType == normalizedReactionType) {
                transaction.delete(reactionRef)
                decrementReactionCount(reactionCounts, existingType)
                transaction.update(
                    postRef,
                    mapOf(
                        "reactCount" to (currentCount - 1).coerceAtLeast(0),
                        "reactionCounts" to reactionCounts,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                ""
            } else {
                transaction.set(
                    reactionRef,
                    hashMapOf<String, Any>(
                        "actorId" to actorId,
                        "actorName" to actorName,
                        "actorRole" to normalizeRole(actorRole),
                        "actorPhotoUrl" to "",
                        "type" to normalizedReactionType,
                        "createdAt" to if (reactionSnapshot.exists()) {
                            reactionSnapshot.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
                        } else {
                            FieldValue.serverTimestamp()
                        },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (reactionSnapshot.exists()) {
                    decrementReactionCount(reactionCounts, existingType)
                    incrementReactionCount(reactionCounts, normalizedReactionType)
                    transaction.update(
                        postRef,
                        mapOf(
                            "reactionCounts" to reactionCounts,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    incrementReactionCount(reactionCounts, normalizedReactionType)
                    transaction.update(
                        postRef,
                        mapOf(
                            "reactCount" to currentCount + 1,
                            "reactionCounts" to reactionCounts,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                createNotificationInTransaction(
                    transaction = transaction,
                    recipientId = postSnapshot.getString("authorId").orEmpty().ifBlank { post.authorId },
                    senderId = actorId,
                    senderName = actorName,
                    senderRole = normalizeRole(actorRole),
                    type = "post_reaction",
                    postId = postId,
                    commentId = "",
                    replyId = "",
                    message = "$actorName liked your post."
                )
                normalizedReactionType
            }
        }.addOnSuccessListener { selectedReaction -> onComplete(Result.success(selectedReaction)) }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

    fun addComment(
        postId: String,
        userId: String,
        userName: String,
        userRole: String,
        text: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val postRef = postDocument(postId)
        val commentRef = postRef.collection(COMMENTS_COLLECTION).document()

        FirestoreNotificationRepository.resolveMentionedUsers(
            firestore = firestore,
            text = text,
            senderId = userId
        ) { mentionedUsers ->
        firestore.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)
            val currentCount = numberToInt(postSnapshot.get("commentCount"))
            val commentPayload = hashMapOf<String, Any>(
                "id" to commentRef.id,
                "postId" to postId,
                "authorId" to userId,
                "authorName" to userName,
                "authorRole" to normalizeRole(userRole),
                "text" to text.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "replyCount" to 0,
                "moderationStatus" to CampaignFeedPost.MODERATION_ACTIVE
            )
            if (mentionedUsers.isNotEmpty()) {
                commentPayload["mentionedUsers"] = mentionedUsers.map { it.toMap() }
            }

            transaction.set(
                commentRef,
                commentPayload
            )
            transaction.update(postRef, "commentCount", currentCount + 1)

            createNotificationInTransaction(
                transaction = transaction,
                recipientId = postSnapshot.getString("authorId").orEmpty(),
                senderId = userId,
                senderName = userName,
                senderRole = normalizeRole(userRole),
                type = FirestoreNotificationRepository.TYPE_POST_COMMENT,
                postId = postId,
                commentId = commentRef.id,
                replyId = "",
                message = "$userName commented on your post."
            )
            postSnapshot.getString("authorId").orEmpty()
        }.addOnSuccessListener { postAuthorId ->
            FirestoreNotificationRepository.createMentionNotifications(
                firestore = firestore,
                mentionedUsers = mentionedUsers,
                excludedRecipientIds = setOf(postAuthorId),
                senderId = userId,
                senderName = userName,
                senderRole = normalizeRole(userRole),
                type = FirestoreNotificationRepository.TYPE_MENTION_COMMENT,
                title = "New mention",
                message = "$userName mentioned you in a comment.",
                postId = postId,
                commentId = commentRef.id
            )
            onComplete(Result.success(Unit))
        }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
        }
    }

    fun addReply(
        postId: String,
        parentComment: CampaignPostComment,
        replyingToReply: CampaignPostReply?,
        userId: String,
        userName: String,
        userRole: String,
        text: String,
        mentionedUserId: String,
        mentionedUserName: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val postRef = postDocument(postId)
        val commentRef = postRef.collection(COMMENTS_COLLECTION).document(parentComment.id)
        val replyRef = commentRef.collection(REPLIES_COLLECTION).document()

        firestore.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)
            val commentSnapshot = transaction.get(commentRef)
            val postCommentCount = numberToInt(postSnapshot.get("commentCount"))
            val replyCount = numberToInt(commentSnapshot.get("replyCount"))
            val parentAuthorId = commentSnapshot.getString("authorId").orEmpty()
                .ifBlank { commentSnapshot.getString("userId").orEmpty() }
                .ifBlank { parentComment.authorId }
            val parentAuthorName = commentSnapshot.getString("authorName").orEmpty()
                .ifBlank { commentSnapshot.getString("userName").orEmpty() }
                .ifBlank { parentComment.authorName }
            val targetUserId = replyingToReply?.authorId?.ifBlank { mentionedUserId }
                ?: mentionedUserId.ifBlank { parentAuthorId }
            val targetUserName = replyingToReply?.authorName?.ifBlank { mentionedUserName }
                ?: mentionedUserName.ifBlank { parentAuthorName }
            val replyingToReplyId = replyingToReply?.id.orEmpty()
            val replyPayload = hashMapOf<String, Any>(
                "id" to replyRef.id,
                "postId" to postId,
                "parentCommentId" to parentComment.id,
                "replyingToReplyId" to replyingToReplyId,
                "authorId" to userId,
                "authorName" to userName,
                "authorRole" to normalizeRole(userRole),
                "text" to text.trim(),
                "mentionedUserId" to targetUserId,
                "mentionedUserName" to targetUserName,
                "replyingToUserId" to targetUserId,
                "replyingToUserName" to targetUserName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "moderationStatus" to CampaignFeedPost.MODERATION_ACTIVE
            )
            if (targetUserId.isNotBlank()) {
                replyPayload["mentionedUsers"] = listOf(
                    mapOf(
                        "userId" to targetUserId,
                        "userName" to targetUserName
                    )
                )
            }

            transaction.set(
                replyRef,
                replyPayload
            )
            transaction.update(
                commentRef,
                mapOf(
                    "replyCount" to replyCount + 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.update(postRef, "commentCount", postCommentCount + 1)

            createNotificationInTransaction(
                transaction = transaction,
                recipientId = targetUserId,
                senderId = userId,
                senderName = userName,
                senderRole = normalizeRole(userRole),
                type = if (replyingToReplyId.isBlank()) "comment_reply" else "reply_reply",
                postId = postId,
                commentId = parentComment.id,
                replyId = replyRef.id,
                replyingToReplyId = replyingToReplyId,
                message = if (replyingToReplyId.isBlank()) {
                    "$userName replied to your comment."
                } else {
                    "$userName replied to your reply."
                }
            )

            if (
                targetUserId.isNotBlank() &&
                targetUserId != userId &&
                targetUserId != parentAuthorId &&
                targetUserId != replyingToReply?.authorId.orEmpty()
            ) {
                createNotificationInTransaction(
                    transaction = transaction,
                    recipientId = targetUserId,
                    senderId = userId,
                    senderName = userName,
                    senderRole = normalizeRole(userRole),
                    type = FirestoreNotificationRepository.TYPE_MENTION_REPLY,
                    postId = postId,
                    commentId = parentComment.id,
                    replyId = replyRef.id,
                    replyingToReplyId = replyingToReplyId,
                    message = "$userName mentioned you in a reply."
                )
            }

            targetUserId
        }.addOnSuccessListener { targetUserId ->
            FirestoreNotificationRepository.resolveMentionedUsers(
                firestore = firestore,
                text = text,
                senderId = userId
            ) { mentionedUsers ->
                val extraMentions = mentionedUsers.filterNot { it.userId == targetUserId }
                if (extraMentions.isNotEmpty()) {
                    replyRef.update("mentionedUsers", (
                        listOf(MentionedUser(targetUserId, mentionedUserName.ifBlank { parentComment.authorName }))
                            .filter { it.userId.isNotBlank() } + extraMentions
                        ).distinctBy { it.userId }.map { it.toMap() }
                    )
                    FirestoreNotificationRepository.createMentionNotifications(
                        firestore = firestore,
                        mentionedUsers = extraMentions,
                        excludedRecipientIds = setOf(targetUserId),
                        senderId = userId,
                        senderName = userName,
                        senderRole = normalizeRole(userRole),
                        type = FirestoreNotificationRepository.TYPE_MENTION_REPLY,
                        title = "New mention",
                        message = "$userName mentioned you in a reply.",
                        postId = postId,
                        commentId = parentComment.id,
                        replyId = replyRef.id,
                        replyingToReplyId = replyingToReply?.id.orEmpty()
                    )
                }
            }
            onComplete(Result.success(Unit))
        }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

    fun incrementShare(postId: String) {
        postDocument(postId).update("shareCount", FieldValue.increment(1))
    }

    fun deletePost(post: CampaignFeedPost, onComplete: (Result<Unit>) -> Unit) {
        val postRef = postDocument(post.id)
        val reactionsTask = postRef.collection(REACTIONS_COLLECTION).get()
        val commentsTask = postRef.collection(COMMENTS_COLLECTION).get()

        reactionsTask.continueWithTask { reactionStage ->
            if (!reactionStage.isSuccessful) {
                throw reactionStage.exception ?: IllegalStateException("Unable to load reactions")
            }
            commentsTask
        }.addOnSuccessListener { commentsSnapshot ->
            reactionsTask.addOnSuccessListener { reactionsSnapshot ->
                val replyTasks = commentsSnapshot.documents.map { commentDocument ->
                    commentDocument.reference.collection(REPLIES_COLLECTION).get()
                }
                Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(replyTasks)
                    .addOnSuccessListener { repliesSnapshots ->
                        val batch = firestore.batch()
                        reactionsSnapshot.documents.forEach { batch.delete(it.reference) }
                        repliesSnapshots.forEach { repliesSnapshot ->
                            repliesSnapshot.documents.forEach { batch.delete(it.reference) }
                        }
                        commentsSnapshot.documents.forEach { batch.delete(it.reference) }
                        batch.delete(postRef)
                        batch.commit()
                            .addOnSuccessListener {
                                // Cloudinary images are not deleted from the client because unsigned
                                // uploads must not expose destructive credentials in the Android app.
                                onComplete(Result.success(Unit))
                            }
                            .addOnFailureListener { error ->
                                onComplete(Result.failure(error))
                            }
                    }
                    .addOnFailureListener { error ->
                        onComplete(Result.failure(error))
                    }
            }.addOnFailureListener { error ->
                onComplete(Result.failure(error))
            }
        }.addOnFailureListener { error ->
            onComplete(Result.failure(error))
        }
    }

    private fun decorateReactionState(
        posts: List<CampaignFeedPost>,
        currentUserId: String,
        onUpdate: (List<CampaignFeedPost>) -> Unit
    ) {
        val selectedReactions = mutableMapOf<String, String>()
        if (posts.isEmpty()) {
            onUpdate(posts)
            return
        }

        var remaining = posts.size
        posts.forEach { post ->
            postDocument(post.id)
                .collection(REACTIONS_COLLECTION)
                .document(currentUserId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        selectedReactions[post.id] = normalizeReactionType(snapshot.getString("type"))
                    }
                }
                .addOnCompleteListener {
                    remaining -= 1
                    if (remaining == 0) {
                        onUpdate(posts.map { item ->
                            val selectedReaction = selectedReactions[item.id].orEmpty()
                            item.copy(
                                reactedByMe = selectedReaction.isNotBlank(),
                                myReactionType = selectedReaction
                            )
                        })
                    }
                }
        }
    }

    private fun uploadPostImage(
        postId: String,
        imageUri: Uri,
        onComplete: (Result<String>) -> Unit
    ) {
        Log.d(TAG, "Selected image uri: $imageUri")
        Log.d(CLOUDINARY_TAG, "Starting upload for campaign post $postId")

        MediaManager.get()
            .upload(imageUri)
            .unsigned(CloudinaryConfig.UPLOAD_PRESET)
            .option("folder", CloudinaryConfig.FOLDER)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    Log.d(CLOUDINARY_TAG, "Upload started: $requestId")
                }

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    Log.d(CLOUDINARY_TAG, "Upload progress: $bytes/$totalBytes")
                }

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val secureUrl = resultData["secure_url"]?.toString().orEmpty()
                    if (secureUrl.isBlank()) {
                        val error = IllegalStateException("Cloudinary did not return secure_url")
                        Log.e(CLOUDINARY_TAG, "Upload failed", error)
                        onComplete(Result.failure(CampaignImageUploadException(error)))
                        return
                    }

                    Log.d(CLOUDINARY_TAG, "Upload success imageUrl: $secureUrl")
                    onComplete(Result.success(secureUrl))
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    val message = error.description ?: "Unknown Cloudinary error"
                    val exception = IllegalStateException(message)
                    Log.e(CLOUDINARY_TAG, "Upload failed", exception)
                    onComplete(Result.failure(CampaignImageUploadException(exception)))
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    Log.d(CLOUDINARY_TAG, "Upload rescheduled: ${error.description.orEmpty()}")
                }
            })
            .dispatch()
    }

    private fun mapPost(snapshot: DocumentSnapshot): CampaignFeedPost {
        return CampaignFeedPost(
            id = snapshot.getString("id").orEmpty().ifBlank { snapshot.id },
            authorId = snapshot.getString("authorId").orEmpty(),
            authorName = snapshot.getString("authorName").orEmpty().ifBlank { "HopeGive" },
            authorRole = normalizeRole(snapshot.getString("authorRole")),
            category = normalizeCategory(
                raw = snapshot.getString("category"),
                authorRole = normalizeRole(snapshot.getString("authorRole")),
                postType = snapshot.getString("postType").orEmpty()
            ),
            postTarget = normalizePostTarget(snapshot.getString("postTarget")),
            postType = snapshot.getString("postType").orEmpty().ifBlank { CampaignFeedPost.TYPE_NOTE },
            text = snapshot.getString("text").orEmpty(),
            imageUrl = snapshot.getString("imageUrl").orEmpty(),
            linkedCampaignId = snapshot.getString("linkedCampaignId").orEmpty(),
            linkedCampaignTitle = snapshot.getString("linkedCampaignTitle").orEmpty(),
            linkedCampaignCategory = snapshot.getString("linkedCampaignCategory").orEmpty(),
            linkedCampaignEmoji = snapshot.getString("linkedCampaignEmoji").orEmpty(),
            campaignTitle = snapshot.getString("campaignTitle").orEmpty(),
            campaignGoal = numberToLong(snapshot.get("campaignGoal")),
            campaignRaised = numberToLong(snapshot.get("campaignRaised")),
            campaignStatus = normalizeStatus(snapshot.getString("campaignStatus")),
            createdAt = timestampToMillis(snapshot.getTimestamp("createdAt")),
            updatedAt = timestampToMillis(snapshot.getTimestamp("updatedAt")),
            reactCount = numberToInt(snapshot.get("reactCount")),
            reactionCounts = mapReactionCounts(snapshot.get("reactionCounts")),
            commentCount = numberToInt(snapshot.get("commentCount")),
            shareCount = numberToInt(snapshot.get("shareCount")),
            isLiveCampaign = snapshot.getBoolean("isLiveCampaign") ?: false,
            moderationStatus = normalizeModerationStatus(snapshot.getString("moderationStatus"))
        )
    }

    private fun mapComment(snapshot: DocumentSnapshot): CampaignPostComment {
        return CampaignPostComment(
            id = snapshot.getString("id").orEmpty().ifBlank { snapshot.id },
            postId = snapshot.getString("postId").orEmpty(),
            authorId = snapshot.getString("authorId").orEmpty()
                .ifBlank { snapshot.getString("userId").orEmpty() },
            authorName = snapshot.getString("authorName").orEmpty()
                .ifBlank { snapshot.getString("userName").orEmpty() }
                .ifBlank { "HopeGive User" },
            authorRole = normalizeRole(
                snapshot.getString("authorRole").orEmpty()
                    .ifBlank { snapshot.getString("userRole").orEmpty() }
            ),
            text = snapshot.getString("text").orEmpty(),
            createdAt = timestampToMillis(snapshot.getTimestamp("createdAt")),
            updatedAt = timestampToMillis(snapshot.getTimestamp("updatedAt")),
            replyCount = numberToInt(snapshot.get("replyCount")),
            moderationStatus = normalizeModerationStatus(snapshot.getString("moderationStatus"))
        )
    }

    private fun mapReply(snapshot: DocumentSnapshot): CampaignPostReply {
        return CampaignPostReply(
            id = snapshot.getString("id").orEmpty().ifBlank { snapshot.id },
            postId = snapshot.getString("postId").orEmpty(),
            parentCommentId = snapshot.getString("parentCommentId").orEmpty(),
            replyingToReplyId = snapshot.getString("replyingToReplyId").orEmpty(),
            authorId = snapshot.getString("authorId").orEmpty(),
            authorName = snapshot.getString("authorName").orEmpty().ifBlank { "HopeGive User" },
            authorRole = normalizeRole(snapshot.getString("authorRole")),
            text = snapshot.getString("text").orEmpty(),
            mentionedUserId = snapshot.getString("mentionedUserId").orEmpty(),
            mentionedUserName = snapshot.getString("mentionedUserName").orEmpty(),
            replyingToUserId = snapshot.getString("replyingToUserId").orEmpty(),
            replyingToUserName = snapshot.getString("replyingToUserName").orEmpty(),
            createdAt = timestampToMillis(snapshot.getTimestamp("createdAt")),
            updatedAt = timestampToMillis(snapshot.getTimestamp("updatedAt")),
            moderationStatus = normalizeModerationStatus(snapshot.getString("moderationStatus"))
        )
    }

    private fun mapReaction(snapshot: DocumentSnapshot): CampaignPostReaction {
        return CampaignPostReaction(
            actorId = snapshot.getString("actorId").orEmpty().ifBlank { snapshot.id },
            actorName = snapshot.getString("actorName").orEmpty()
                .ifBlank { snapshot.getString("userName").orEmpty() }
                .ifBlank { "Guest User" },
            actorRole = normalizeRole(snapshot.getString("actorRole")),
            actorPhotoUrl = snapshot.getString("actorPhotoUrl").orEmpty(),
            type = normalizeReactionType(snapshot.getString("type")),
            createdAt = timestampToMillis(snapshot.getTimestamp("createdAt")),
            updatedAt = timestampToMillis(snapshot.getTimestamp("updatedAt"))
        )
    }

    private fun mapSettings(snapshot: DocumentSnapshot?): CampaignFeedSettings {
        return CampaignFeedSettings(
            allowUserPosts = snapshot?.getBoolean("allowUserPosts") ?: true,
            allowGuestPosts = snapshot?.getBoolean("allowGuestPosts") ?: false,
            allowGuestReactions = snapshot?.getBoolean("allowGuestReactions") ?: true,
            allowGuestComments = snapshot?.getBoolean("allowGuestComments") ?: true
        )
    }

    private fun postsCollection() = firestore.collection(POSTS_COLLECTION)

    private fun postDocument(postId: String) = postsCollection().document(postId)

    private fun timestampToMillis(timestamp: Timestamp?): Long {
        return timestamp?.toDate()?.time ?: 0L
    }

    private fun numberToInt(value: Any?): Int {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Float -> value.toInt()
            is String -> value.toIntOrNull() ?: 0
            else -> 0
        }
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

    private fun mapReactionCounts(value: Any?): MutableMap<String, Int> {
        val rawMap = value as? Map<*, *> ?: return CampaignReactionUi.reactionOrder.associateWith { 0 }
            .toMutableMap()
        return CampaignReactionUi.reactionOrder.associateWith { key ->
            numberToInt(rawMap[key])
        }.toMutableMap()
    }

    private fun incrementReactionCount(counts: MutableMap<String, Int>, type: String) {
        counts[type] = (counts[type] ?: 0) + 1
    }

    private fun decrementReactionCount(counts: MutableMap<String, Int>, type: String) {
        if (type.isBlank()) return
        counts[type] = ((counts[type] ?: 0) - 1).coerceAtLeast(0)
    }

    private fun normalizeReactionType(raw: String?): String {
        val normalized = raw.orEmpty().trim().lowercase(Locale.getDefault())
        return if (normalized in CampaignReactionUi.reactionOrder) normalized else CampaignReactionUi.LIKE
    }

    private fun normalizeRole(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            "super admin", "super_admin", "superadmin" -> CampaignFeedPost.ROLE_SUPER_ADMIN
            "admin" -> CampaignFeedPost.ROLE_ADMIN
            "moderator", "mod" -> CampaignFeedPost.ROLE_MODERATOR
            "guest" -> CampaignFeedPost.ROLE_GUEST
            else -> CampaignFeedPost.ROLE_USER
        }
    }

    private fun normalizeModerationStatus(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.MODERATION_HIDDEN -> CampaignFeedPost.MODERATION_HIDDEN
            CampaignFeedPost.MODERATION_DELETED -> CampaignFeedPost.MODERATION_DELETED
            else -> CampaignFeedPost.MODERATION_ACTIVE
        }
    }

    private fun normalizeCategory(raw: String?, authorRole: String, postType: String): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.CATEGORY_OFFICIAL -> CampaignFeedPost.CATEGORY_OFFICIAL
            CampaignFeedPost.CATEGORY_COMMUNITY -> CampaignFeedPost.CATEGORY_COMMUNITY
            else -> {
                if (
                    authorRole == CampaignFeedPost.ROLE_ADMIN ||
                    authorRole == CampaignFeedPost.ROLE_SUPER_ADMIN ||
                    postType == CampaignFeedPost.TYPE_LIVE_CAMPAIGN
                ) {
                    CampaignFeedPost.CATEGORY_OFFICIAL
                } else {
                    CampaignFeedPost.CATEGORY_COMMUNITY
                }
            }
        }
    }

    private fun normalizePostTarget(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.TARGET_CAMPAIGN -> CampaignFeedPost.TARGET_CAMPAIGN
            else -> CampaignFeedPost.TARGET_COMMUNITY
        }
    }

    private fun normalizeStatus(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.STATUS_COMPLETED -> CampaignFeedPost.STATUS_COMPLETED
            CampaignFeedPost.STATUS_PAUSED -> CampaignFeedPost.STATUS_PAUSED
            else -> CampaignFeedPost.STATUS_ACTIVE
        }
    }

    private fun firebaseErrorDetails(error: Exception): String {
        val firestoreCode = (error as? FirebaseFirestoreException)?.code?.name
        return "type=${error.javaClass.simpleName}, firestoreCode=${firestoreCode.orEmpty()}, message=${error.message.orEmpty()}"
    }

    private fun createNotificationInTransaction(
        transaction: com.google.firebase.firestore.Transaction,
        recipientId: String,
        senderId: String,
        senderName: String,
        senderRole: String,
        type: String,
        postId: String,
        commentId: String,
        replyId: String,
        replyingToReplyId: String = "",
        message: String
    ) {
        if (recipientId.isBlank() || senderId.isBlank() || recipientId == senderId) return

        val notificationRef = firestore.collection(NOTIFICATIONS_COLLECTION).document()
        val payload = hashMapOf<String, Any>(
            "id" to notificationRef.id,
            "recipientId" to recipientId,
            "recipientRole" to "",
            "senderId" to senderId,
            "senderName" to senderName,
            "senderRole" to normalizeRole(senderRole),
            "type" to type,
            "title" to notificationTitleForType(type),
            "relatedUserId" to senderId,
            "campaignId" to "",
            "campaignTitle" to "",
            "donationId" to "",
            "postId" to postId,
            "commentId" to commentId,
            "replyId" to replyId,
            "amount" to 0.0,
            "message" to message,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (replyingToReplyId.isNotBlank()) payload["replyingToReplyId"] = replyingToReplyId
        transaction.set(notificationRef, payload)
    }

    private fun notificationTitleForType(type: String): String {
        return when (type) {
            FirestoreNotificationRepository.TYPE_POST_COMMENT -> "New comment"
            FirestoreNotificationRepository.TYPE_COMMENT_REPLY,
            FirestoreNotificationRepository.TYPE_REPLY_REPLY -> "New reply"
            FirestoreNotificationRepository.TYPE_MENTION_REPLY -> "New mention"
            FirestoreNotificationRepository.TYPE_POST_REACTION -> "New reaction"
            else -> "HopeGive notification"
        }
    }

    companion object {
        private const val TAG = "CampaignFeed"
        private const val POSTS_COLLECTION = "campaign_posts"
        private const val SETTINGS_COLLECTION = "app_settings"
        private const val CAMPAIGN_FEED_SETTINGS_DOCUMENT = "campaign_feed"
        private const val REACTIONS_COLLECTION = "reactions"
        private const val COMMENTS_COLLECTION = "comments"
        private const val REPLIES_COLLECTION = "replies"
        private const val NOTIFICATIONS_COLLECTION = "notifications"
        private const val DEFAULT_REACTION_TYPE = "like"
        private const val CLOUDINARY_TAG = "Cloudinary"
    }
}

class CampaignImageUploadException(cause: Throwable) : Exception("Image upload failed", cause)

class CampaignFirestoreSaveException(cause: Exception) : Exception("Firestore post save failed", cause)
