package com.example.ulamshare

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class CampaignFeedAdapter(
    private val onReactClicked: (CampaignFeedPost) -> Unit,
    private val onCommentClicked: (CampaignFeedPost) -> Unit,
    private val onShareClicked: (CampaignFeedPost) -> Unit,
    private val onAuthorClicked: (CampaignFeedPost) -> Unit,
    private val onCampaignClicked: (CampaignFeedPost) -> Unit,
    private val onPostOptionsClicked: (View, CampaignFeedPost) -> Unit,
    private val canManagePost: (CampaignFeedPost) -> Boolean
) : RecyclerView.Adapter<CampaignFeedAdapter.CampaignFeedViewHolder>() {

    private val items = mutableListOf<CampaignFeedPost>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CampaignFeedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_campaign_feed_post, parent, false)
        return CampaignFeedViewHolder(view)
    }

    override fun onBindViewHolder(holder: CampaignFeedViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<CampaignFeedPost>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class CampaignFeedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarView: TextView = itemView.findViewById(R.id.tvPostAvatar)
        private val authorView: TextView = itemView.findViewById(R.id.tvPostAuthor)
        private val badgeView: TextView = itemView.findViewById(R.id.tvPostRole)
        private val metaView: TextView = itemView.findViewById(R.id.tvPostMeta)
        private val optionsButton: ImageButton = itemView.findViewById(R.id.btnPostOptions)
        private val contentView: TextView = itemView.findViewById(R.id.tvPostContent)
        private val imageContainer: FrameLayout = itemView.findViewById(R.id.postImageContainer)
        private val imageView: ImageView = itemView.findViewById(R.id.ivPostImage)
        private val campaignInfoContainer: View = itemView.findViewById(R.id.campaignInfoContainer)
        private val campaignTitleView: TextView = itemView.findViewById(R.id.tvCampaignTitle)
        private val campaignStatusView: TextView = itemView.findViewById(R.id.tvCampaignStatus)
        private val raisedView: TextView = itemView.findViewById(R.id.tvCampaignRaised)
        private val goalView: TextView = itemView.findViewById(R.id.tvCampaignGoal)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.pbCampaignProgress)
        private val reactionSummaryView: TextView = itemView.findViewById(R.id.tvPostReactionSummary)
        private val reactButton: TextView = itemView.findViewById(R.id.btnPostReact)
        private val commentButton: TextView = itemView.findViewById(R.id.btnPostComment)
        private val shareButton: TextView = itemView.findViewById(R.id.btnPostShare)

        fun bind(post: CampaignFeedPost) {
            val publicAuthorName = PrivacyDisplayHelper.publicName(post.authorName)
            avatarView.text = buildInitials(publicAuthorName)
            authorView.text = publicAuthorName
            avatarView.setOnClickListener { onAuthorClicked(post) }
            authorView.setOnClickListener { onAuthorClicked(post) }
            badgeView.text = post.badgeLabel
            badgeView.setBackgroundResource(
                if (post.isOfficialPost) {
                    R.drawable.bg_campaign_badge_official
                } else {
                    R.drawable.bg_campaign_badge_community
                }
            )
            badgeView.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (post.isOfficialPost) R.color.primary_blue else R.color.text_grey
                )
            )
            metaView.text = itemView.context.getString(
                R.string.choose_campaign_post_meta_format,
                roleLabel(post.authorRole),
                formatMeta(post.createdAt)
            )

            optionsButton.visibility = if (canManagePost(post)) View.VISIBLE else View.GONE
            optionsButton.setOnClickListener {
                onPostOptionsClicked(it, post)
            }

            if (post.hasText) {
                contentView.visibility = View.VISIBLE
                contentView.text = post.text
            } else {
                contentView.visibility = View.GONE
            }

            if (post.hasImage) {
                imageContainer.visibility = View.VISIBLE
                CampaignImageLoader.load(imageView, post.imageUrl, R.drawable.plant)
            } else {
                imageContainer.visibility = View.GONE
                CampaignImageLoader.load(imageView, "", R.drawable.plant)
            }

            if (post.hasCampaignInfo) {
                campaignInfoContainer.visibility = View.VISIBLE
                campaignInfoContainer.isClickable = false
                campaignInfoContainer.setOnClickListener(null)
                campaignTitleView.text = post.campaignTitle.ifBlank {
                    itemView.context.getString(R.string.choose_campaign_live_campaign)
                }
                campaignStatusView.text = statusLabel(post.campaignStatus)
                campaignStatusView.setBackgroundResource(statusBackground(post.campaignStatus))
                campaignStatusView.setTextColor(
                    ContextCompat.getColor(itemView.context, statusTextColor(post.campaignStatus))
                )
                raisedView.text = itemView.context.getString(
                    R.string.choose_campaign_raised_format,
                    formatCurrency(post.campaignRaised)
                )
                goalView.text = itemView.context.getString(
                    R.string.choose_campaign_goal_format,
                    formatCurrency(post.campaignGoal)
                )
                goalView.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                progressBar.progress = calculateProgress(post.campaignRaised, post.campaignGoal)
            } else if (post.hasLinkedCampaign) {
                campaignInfoContainer.visibility = View.VISIBLE
                campaignInfoContainer.isClickable = true
                campaignInfoContainer.setOnClickListener { onCampaignClicked(post) }
                campaignTitleView.text = "${post.linkedCampaignEmoji.ifBlank { DEFAULT_CAMPAIGN_EMOJI }} ${post.linkedCampaignTitle}"
                campaignStatusView.text = itemView.context.getString(R.string.choose_campaign_community_chip)
                campaignStatusView.setBackgroundResource(R.drawable.bg_campaign_badge_community)
                campaignStatusView.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.primary_blue)
                )
                raisedView.text = post.linkedCampaignCategory.ifBlank {
                    itemView.context.getString(R.string.choose_campaign_campaign_target)
                }
                goalView.visibility = View.GONE
                progressBar.visibility = View.GONE
            } else {
                campaignInfoContainer.visibility = View.GONE
                campaignInfoContainer.isClickable = false
                campaignInfoContainer.setOnClickListener(null)
            }

            bindReactionSummary(post)
            bindReactionButton(post)
            bindActionButton(
                button = commentButton,
                label = itemView.context.getString(R.string.choose_campaign_action_comment),
                iconRes = R.drawable.ic_comment,
                count = post.commentCount,
                selected = false
            )
            bindActionButton(
                button = shareButton,
                label = itemView.context.getString(R.string.choose_campaign_action_share),
                iconRes = R.drawable.ic_share,
                count = post.shareCount,
                selected = false
            )

            reactButton.setOnClickListener {
                animateActionTap(reactButton)
                onReactClicked(post)
            }
            reactButton.setOnLongClickListener(null)
            reactButton.isLongClickable = false
            commentButton.setOnClickListener { onCommentClicked(post) }
            shareButton.setOnClickListener {
                animateActionTap(shareButton)
                onShareClicked(post)
            }
        }

        private fun bindReactionSummary(post: CampaignFeedPost) {
            val summaryParts = mutableListOf<String>()
            if (post.reactCount > 0) {
                summaryParts += countLabel(post.reactCount, "like")
            }
            if (post.commentCount > 0) {
                summaryParts += countLabel(post.commentCount, "comment")
            }
            if (post.shareCount > 0) {
                summaryParts += countLabel(post.shareCount, "share")
            }

            if (summaryParts.isEmpty()) {
                reactionSummaryView.visibility = View.GONE
                reactionSummaryView.text = ""
                reactionSummaryView.setOnClickListener(null)
                reactionSummaryView.isClickable = false
                reactionSummaryView.isFocusable = false
                return
            }

            reactionSummaryView.visibility = View.VISIBLE
            reactionSummaryView.text = summaryParts.joinToString(separator = " \u00B7 ")
            reactionSummaryView.setOnClickListener(null)
            reactionSummaryView.isClickable = false
            reactionSummaryView.isFocusable = false
        }

        private fun bindReactionButton(post: CampaignFeedPost) {
            val selectedType = post.myReactionType.ifBlank {
                if (post.reactedByMe) CampaignReactionUi.LIKE else ""
            }
            bindActionButton(
                button = reactButton,
                label = itemView.context.getString(R.string.choose_campaign_action_react),
                iconRes = R.drawable.ic_like,
                count = post.reactCount,
                selected = selectedType.isNotBlank()
            )
        }

        private fun bindActionButton(
            button: TextView,
            label: String,
            @DrawableRes iconRes: Int,
            count: Int,
            selected: Boolean
        ) {
            button.text = count.coerceAtLeast(0).toString()
            button.contentDescription = label
            button.setBackgroundResource(
                if (selected) {
                    R.drawable.bg_campaign_action_chip_selected
                } else {
                    R.drawable.bg_campaign_action_chip
                }
            )
            val colorRes = if (selected) R.color.primary_blue else R.color.text_grey
            val color = ContextCompat.getColor(button.context, colorRes)
            button.setTextColor(color)
            button.compoundDrawablePadding = 6.dp()
            button.setCompoundDrawablesWithIntrinsicBounds(
                tintedIcon(iconRes, color),
                null,
                null,
                null
            )
        }

        private fun tintedIcon(
            @DrawableRes iconRes: Int,
            @ColorInt color: Int
        ): Drawable? {
            val drawable = ContextCompat.getDrawable(itemView.context, iconRes) ?: return null
            return DrawableCompat.wrap(drawable).mutate().also {
                DrawableCompat.setTint(it, color)
            }
        }

        private fun animateActionTap(view: View) {
            val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.12f, 1f)
            val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.12f, 1f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 180L
                start()
            }
        }

        private fun countLabel(count: Int, singular: String): String {
            val noun = if (count == 1) singular else "${singular}s"
            return "$count $noun"
        }

        private fun Int.dp(): Int {
            return (this * itemView.resources.displayMetrics.density).toInt()
        }
    }

    private fun buildInitials(name: String): String {
        val parts = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
        if (parts.isEmpty()) return "HG"
        return parts.joinToString(separator = "") { it.first().uppercase(Locale.getDefault()) }
    }

    private fun formatCurrency(amount: Long): String {
        return String.format(Locale.US, "\u20B1%,d", amount)
    }

    private fun formatMeta(createdAt: Long): String {
        return if (createdAt > 0L) {
            DateUtils.getRelativeTimeSpanString(
                createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            ).toString()
        } else {
            "Just now"
        }
    }

    private fun calculateProgress(raised: Long, goal: Long): Int {
        if (goal <= 0L) return 0
        return ((raised.toDouble() / goal.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun roleLabel(role: String): String {
        return when (role.trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.ROLE_SUPER_ADMIN -> "Super Admin"
            CampaignFeedPost.ROLE_ADMIN -> "Admin"
            CampaignFeedPost.ROLE_MODERATOR -> "Moderator"
            CampaignFeedPost.ROLE_GUEST -> "Guest"
            else -> "User"
        }
    }

    private fun statusLabel(status: String): String {
        return when (status.trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.STATUS_COMPLETED -> "Completed"
            CampaignFeedPost.STATUS_PAUSED -> "Paused"
            else -> "Active"
        }
    }

    private fun statusBackground(status: String): Int {
        return when (status.trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.STATUS_COMPLETED -> R.drawable.bg_campaign_status_completed
            CampaignFeedPost.STATUS_PAUSED -> R.drawable.bg_campaign_status_paused
            else -> R.drawable.bg_campaign_status_active
        }
    }

    private fun statusTextColor(status: String): Int {
        return when (status.trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.STATUS_COMPLETED -> R.color.primary_blue
            CampaignFeedPost.STATUS_PAUSED -> R.color.orange_alert_stroke
            else -> R.color.accent_green
        }
    }

    private companion object {
        const val DEFAULT_CAMPAIGN_EMOJI = "\uD83D\uDC99"
    }
}
