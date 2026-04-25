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
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val isLiveCampaign: Boolean = false,
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
    val userId: String = "",
    val userName: String = "",
    val userRole: String = CampaignFeedPost.ROLE_USER,
    val text: String = "",
    val createdAt: Long = 0L
)

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
