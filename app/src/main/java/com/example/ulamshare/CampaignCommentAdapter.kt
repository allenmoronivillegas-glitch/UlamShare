package com.example.ulamshare

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class CampaignCommentAdapter(
    private val onReplyClicked: (CampaignPostComment, CampaignPostReply?) -> Unit
) : RecyclerView.Adapter<CampaignCommentAdapter.CommentViewHolder>() {

    private val items = mutableListOf<CampaignPostComment>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_campaign_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(comments: List<CampaignPostComment>) {
        items.clear()
        items.addAll(comments)
        notifyDataSetChanged()
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarView: TextView = itemView.findViewById(R.id.tvCommentAvatar)
        private val authorView: TextView = itemView.findViewById(R.id.tvCommentAuthor)
        private val roleView: TextView = itemView.findViewById(R.id.tvCommentRole)
        private val textView: TextView = itemView.findViewById(R.id.tvCommentText)
        private val metaView: TextView = itemView.findViewById(R.id.tvCommentMeta)
        private val replyButton: TextView = itemView.findViewById(R.id.btnCommentReply)
        private val repliesContainer: LinearLayout = itemView.findViewById(R.id.repliesContainer)

        fun bind(comment: CampaignPostComment) {
            avatarView.text = initials(comment.authorName)
            authorView.text = comment.authorName
            roleView.text = roleLabel(comment.authorRole)
            textView.text = comment.text
            metaView.text = formatTime(comment.createdAt)
            replyButton.setOnClickListener { onReplyClicked(comment, null) }
            bindReplies(comment, comment.replies)
        }

        private fun bindReplies(comment: CampaignPostComment, replies: List<CampaignPostReply>) {
            repliesContainer.removeAllViews()
            repliesContainer.visibility = if (replies.isEmpty()) View.GONE else View.VISIBLE
            replies.forEach { reply ->
                repliesContainer.addView(buildReplyView(comment, reply))
            }
        }

        private fun buildReplyView(comment: CampaignPostComment, reply: CampaignPostReply): View {
            val context = itemView.context
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 8, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val avatar = TextView(context).apply {
                text = initials(reply.authorName)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_avatar_blue)
                layoutParams = LinearLayout.LayoutParams(30.dp(), 30.dp())
            }

            val bubble = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.rounded_soft_blue_rect)
                setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = 8.dp()
                }
            }

            val author = TextView(context).apply {
                text = reply.authorName
                setTextColor(ContextCompat.getColor(context, R.color.text_black))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }

            val body = TextView(context).apply {
                text = styledReplyText(reply)
                setTextColor(ContextCompat.getColor(context, R.color.text_black))
                textSize = 13f
                setPadding(0, 4.dp(), 0, 0)
            }

            val meta = TextView(context).apply {
                text = formatTime(reply.createdAt)
                setTextColor(ContextCompat.getColor(context, R.color.text_grey))
                textSize = 10f
                setPadding(0, 6.dp(), 0, 0)
            }

            val replyAction = TextView(context).apply {
                text = context.getString(R.string.choose_campaign_reply)
                setTextColor(ContextCompat.getColor(context, R.color.primary_blue))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 6.dp(), 12.dp(), 0)
                setOnClickListener { onReplyClicked(comment, reply) }
            }

            bubble.addView(author)
            bubble.addView(body)
            bubble.addView(meta)
            bubble.addView(replyAction)
            container.addView(avatar)
            container.addView(bubble)
            return container
        }

        private fun styledReplyText(reply: CampaignPostReply): CharSequence {
            val mentionName = reply.mentionedUserName
            val mention = if (mentionName.isNotBlank()) "@$mentionName" else ""
            if (mention.isBlank() || !reply.text.startsWith(mention)) return reply.text

            return SpannableString(reply.text).apply {
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(itemView.context, R.color.primary_blue)),
                    0,
                    mention.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    mention.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun formatTime(createdAt: Long): CharSequence {
            return if (createdAt > 0L) {
                DateUtils.getRelativeTimeSpanString(
                    createdAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else {
                itemView.context.getString(R.string.choose_campaign_just_now)
            }
        }

        private fun Int.dp(): Int {
            return (this * itemView.resources.displayMetrics.density).toInt()
        }
    }

    private fun initials(name: String): String {
        val parts = name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
        if (parts.isEmpty()) return "HG"
        return parts.joinToString(separator = "") { it.first().uppercase(Locale.getDefault()) }
    }

    private fun roleLabel(role: String): String {
        return when (role.trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.ROLE_SUPER_ADMIN -> "Super Admin"
            CampaignFeedPost.ROLE_ADMIN -> "Admin"
            CampaignFeedPost.ROLE_GUEST -> "Guest"
            else -> "User"
        }
    }
}
