package com.example.ulamshare

import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.util.Locale

class CampaignFeedRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
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

                onUpdate(snapshot?.documents.orEmpty().map(::mapComment))
            }
    }

    fun createPost(
        author: CampaignPostAuthor,
        draft: CampaignComposerDraft,
        onComplete: (Result<Unit>) -> Unit
    ) {
        val postRef = postsCollection().document()
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
                "postType" to postType,
                "text" to draft.text.trim(),
                "imageUrl" to imageUrl,
                "campaignTitle" to if (isLiveCampaign) draft.campaignTitle.trim() else "",
                "campaignGoal" to if (isLiveCampaign) draft.campaignGoal.coerceAtLeast(0L) else 0L,
                "campaignRaised" to if (isLiveCampaign) draft.campaignRaised.coerceAtLeast(0L) else 0L,
                "campaignStatus" to if (isLiveCampaign) normalizeStatus(draft.campaignStatus) else "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "reactCount" to 0,
                "commentCount" to 0,
                "shareCount" to 0,
                "isLiveCampaign" to isLiveCampaign
            )

            Log.d(TAG, "Saving campaign post payload to campaign_posts/${postRef.id}: $payload")

            postRef.set(payload)
                .addOnSuccessListener { onComplete(Result.success(Unit)) }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to save campaign post: ${firebaseErrorDetails(error)}", error)
                    onComplete(Result.failure(error))
                }
        }

        val imageUri = draft.imageUri
        if (imageUri == null) {
            writePost("")
            return
        }

        uploadPostImage(postRef.id, imageUri) { result ->
            result
                .onSuccess { imageUrl -> writePost(imageUrl) }
                .onFailure { error -> onComplete(Result.failure(error)) }
        }
    }

    fun toggleReaction(
        postId: String,
        actorId: String,
        actorName: String,
        onComplete: (Result<Boolean>) -> Unit
    ) {
        val postRef = postDocument(postId)
        val reactionRef = postRef.collection(REACTIONS_COLLECTION).document(actorId)

        firestore.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)
            val reactionSnapshot = transaction.get(reactionRef)
            val currentCount = numberToInt(postSnapshot.get("reactCount"))

            if (reactionSnapshot.exists()) {
                transaction.delete(reactionRef)
                transaction.update(postRef, "reactCount", (currentCount - 1).coerceAtLeast(0))
                false
            } else {
                transaction.set(
                    reactionRef,
                    hashMapOf<String, Any>(
                        "userId" to actorId,
                        "userName" to actorName,
                        "type" to DEFAULT_REACTION_TYPE,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(postRef, "reactCount", currentCount + 1)
                true
            }
        }.addOnSuccessListener { reacted -> onComplete(Result.success(reacted)) }
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

        firestore.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)
            val currentCount = numberToInt(postSnapshot.get("commentCount"))

            transaction.set(
                commentRef,
                hashMapOf<String, Any>(
                    "userId" to userId,
                    "userName" to userName,
                    "userRole" to normalizeRole(userRole),
                    "text" to text.trim(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            transaction.update(postRef, "commentCount", currentCount + 1)
        }.addOnSuccessListener { onComplete(Result.success(Unit)) }
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
                val batch = firestore.batch()
                reactionsSnapshot.documents.forEach { batch.delete(it.reference) }
                commentsSnapshot.documents.forEach { batch.delete(it.reference) }
                batch.delete(postRef)
                batch.commit()
                    .addOnSuccessListener {
                        if (post.imageUrl.isNotBlank()) {
                            storage.reference
                                .child("campaign_posts/${post.id}/post_image.jpg")
                                .delete()
                                .addOnCompleteListener {
                                    onComplete(Result.success(Unit))
                                }
                        } else {
                            onComplete(Result.success(Unit))
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
        val reactedIds = mutableSetOf<String>()
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
                        reactedIds += post.id
                    }
                }
                .addOnCompleteListener {
                    remaining -= 1
                    if (remaining == 0) {
                        onUpdate(posts.map { item ->
                            item.copy(reactedByMe = reactedIds.contains(item.id))
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
        val imageRef = storage.reference.child("campaign_posts/$postId/post_image.jpg")
        Log.d(TAG, "Uploading selected campaign post image from uri=$imageUri to ${imageRef.path}")
        imageRef.putFile(imageUri)
            .continueWithTask { uploadTask ->
                if (!uploadTask.isSuccessful) {
                    throw uploadTask.exception ?: IllegalStateException("Upload failed")
                }
                imageRef.downloadUrl
            }
            .addOnSuccessListener { uri -> onComplete(Result.success(uri.toString())) }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to upload campaign post image: ${firebaseErrorDetails(error)}", error)
                onComplete(Result.failure(error))
            }
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
            postType = snapshot.getString("postType").orEmpty().ifBlank { CampaignFeedPost.TYPE_NOTE },
            text = snapshot.getString("text").orEmpty(),
            imageUrl = snapshot.getString("imageUrl").orEmpty(),
            campaignTitle = snapshot.getString("campaignTitle").orEmpty(),
            campaignGoal = numberToLong(snapshot.get("campaignGoal")),
            campaignRaised = numberToLong(snapshot.get("campaignRaised")),
            campaignStatus = normalizeStatus(snapshot.getString("campaignStatus")),
            createdAt = timestampToMillis(snapshot.getTimestamp("createdAt")),
            updatedAt = timestampToMillis(snapshot.getTimestamp("updatedAt")),
            reactCount = numberToInt(snapshot.get("reactCount")),
            commentCount = numberToInt(snapshot.get("commentCount")),
            shareCount = numberToInt(snapshot.get("shareCount")),
            isLiveCampaign = snapshot.getBoolean("isLiveCampaign") ?: false
        )
    }

    private fun mapComment(snapshot: DocumentSnapshot): CampaignPostComment {
        return CampaignPostComment(
            id = snapshot.id,
            userId = snapshot.getString("userId").orEmpty(),
            userName = snapshot.getString("userName").orEmpty().ifBlank { "HopeGive User" },
            userRole = normalizeRole(snapshot.getString("userRole")),
            text = snapshot.getString("text").orEmpty(),
            createdAt = timestampToMillis(snapshot.getTimestamp("createdAt"))
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

    private fun normalizeRole(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            "super admin", "super_admin", "superadmin" -> CampaignFeedPost.ROLE_SUPER_ADMIN
            "admin" -> CampaignFeedPost.ROLE_ADMIN
            "guest" -> CampaignFeedPost.ROLE_GUEST
            else -> CampaignFeedPost.ROLE_USER
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

    private fun normalizeStatus(raw: String?): String {
        return when (raw.orEmpty().trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.STATUS_COMPLETED -> CampaignFeedPost.STATUS_COMPLETED
            CampaignFeedPost.STATUS_PAUSED -> CampaignFeedPost.STATUS_PAUSED
            else -> CampaignFeedPost.STATUS_ACTIVE
        }
    }

    private fun firebaseErrorDetails(error: Exception): String {
        val firestoreCode = (error as? FirebaseFirestoreException)?.code?.name
        val storageCode = (error as? StorageException)?.errorCode
        return "type=${error.javaClass.simpleName}, firestoreCode=${firestoreCode.orEmpty()}, storageCode=${storageCode ?: ""}, message=${error.message.orEmpty()}"
    }

    companion object {
        private const val TAG = "CampaignFeed"
        private const val POSTS_COLLECTION = "campaign_posts"
        private const val SETTINGS_COLLECTION = "app_settings"
        private const val CAMPAIGN_FEED_SETTINGS_DOCUMENT = "campaign_feed"
        private const val REACTIONS_COLLECTION = "reactions"
        private const val COMMENTS_COLLECTION = "comments"
        private const val DEFAULT_REACTION_TYPE = "like"
    }
}
