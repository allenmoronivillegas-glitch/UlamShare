package com.example.ulamshare

import android.net.Uri

data class CampaignFeedPost(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = ROLE_USER,
    val category: String = CATEGORY_COMMUNITY,
    val postType: String = TYPE_NOTE,
    val text: String = "",
    val imageUrl: String = "",
    val campaignTitle: String = "",
    val campaignGoal: Long = 0L,
    val campaignRaised: Long = 0L,
    val campaignStatus: String = STATUS_ACTIVE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val reactCount: Int = 0,
    val reactionCounts: Map<String, Int> = emptyMap(),
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val isLiveCampaign: Boolean = false,
    val myReactionType: String = "",
    val reactedByMe: Boolean = false
) {
    val hasText: Boolean
        get() = text.isNotBlank()

    val hasImage: Boolean
        get() = imageUrl.isNotBlank()

    val hasCampaignInfo: Boolean
        get() = isLiveCampaign || postType == TYPE_LIVE_CAMPAIGN

    val isOfficialPost: Boolean
        get() = category == CATEGORY_OFFICIAL ||
            authorRole == ROLE_ADMIN ||
            authorRole == ROLE_SUPER_ADMIN ||
            postType == TYPE_LIVE_CAMPAIGN

    val isCommunityPost: Boolean
        get() = !isOfficialPost || category == CATEGORY_COMMUNITY

    val badgeLabel: String
        get() = if (isOfficialPost) "Official" else "Community"

    companion object {
        const val TYPE_NOTE = "note"
        const val TYPE_PHOTO = "photo"
        const val TYPE_LIVE_CAMPAIGN = "live_campaign"

        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_PAUSED = "paused"

        const val ROLE_USER = "user"
        const val ROLE_ADMIN = "admin"
        const val ROLE_SUPER_ADMIN = "super_admin"
        const val ROLE_GUEST = "guest"

        const val CATEGORY_OFFICIAL = "official"
        const val CATEGORY_COMMUNITY = "community"
    }
}

data class CampaignPostComment(
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = CampaignFeedPost.ROLE_USER,
    val text: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val replyCount: Int = 0,
    val replies: List<CampaignPostReply> = emptyList()
) {
    val userId: String
        get() = authorId

    val userName: String
        get() = authorName

    val userRole: String
        get() = authorRole
}

data class CampaignPostReply(
    val id: String = "",
    val postId: String = "",
    val parentCommentId: String = "",
    val replyingToReplyId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: String = CampaignFeedPost.ROLE_USER,
    val text: String = "",
    val mentionedUserId: String = "",
    val mentionedUserName: String = "",
    val replyingToUserId: String = "",
    val replyingToUserName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class CampaignPostReaction(
    val actorId: String = "",
    val actorName: String = "",
    val actorRole: String = CampaignFeedPost.ROLE_USER,
    val actorPhotoUrl: String = "",
    val type: String = CampaignReactionUi.LIKE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

object CampaignReactionUi {
    const val LIKE = "like"
    const val HEART = "heart"
    const val HAHA = "haha"
    const val SAD = "sad"
    const val CARE = "care"
    const val ANGRY = "angry"

    val reactionOrder = listOf(LIKE, HEART, HAHA, SAD, CARE, ANGRY)

    val displayMap = linkedMapOf(
        LIKE to "\uD83D\uDC4D",
        HEART to "\u2764\uFE0F",
        HAHA to "\uD83D\uDE02",
        SAD to "\uD83D\uDE22",
        CARE to "\uD83E\uDD17",
        ANGRY to "\uD83D\uDE21"
    )

    fun emoji(type: String): String = displayMap[type] ?: displayMap.getValue(LIKE)

    fun label(type: String): String {
        return when (type) {
            HEART -> "Heart"
            HAHA -> "Haha"
            SAD -> "Sad"
            CARE -> "Care"
            ANGRY -> "Angry"
            else -> "Like"
        }
    }

    fun displayLabel(type: String): String = "${emoji(type)} ${label(type)}"
}

data class CampaignComposerDraft(
    val text: String = "",
    val imageUri: Uri? = null,
    val category: String = CampaignFeedPost.CATEGORY_COMMUNITY,
    val isLiveCampaign: Boolean = false,
    val campaignTitle: String = "",
    val campaignGoal: Long = 0L,
    val campaignRaised: Long = 0L,
    val campaignStatus: String = CampaignFeedPost.STATUS_ACTIVE
) {
    fun resolvedPostType(): String {
        return when {
            isLiveCampaign -> CampaignFeedPost.TYPE_LIVE_CAMPAIGN
            imageUri != null -> CampaignFeedPost.TYPE_PHOTO
            else -> CampaignFeedPost.TYPE_NOTE
        }
    }
}

data class CampaignPostAuthor(
    val id: String = "",
    val name: String = "",
    val role: String = CampaignFeedPost.ROLE_USER
)

data class CampaignFeedSettings(
    val allowUserPosts: Boolean = true,
    val allowGuestPosts: Boolean = false,
    val allowGuestReactions: Boolean = true,
    val allowGuestComments: Boolean = true
)
