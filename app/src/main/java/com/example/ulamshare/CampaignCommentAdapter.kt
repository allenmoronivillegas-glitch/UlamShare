package com.example.ulamshare

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class CampaignCommentAdapter : RecyclerView.Adapter<CampaignCommentAdapter.CommentViewHolder>() {

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

        fun bind(comment: CampaignPostComment) {
            avatarView.text = initials(comment.userName)
            authorView.text = comment.userName
            roleView.text = roleLabel(comment.userRole)
            textView.text = comment.text
            metaView.text = if (comment.createdAt > 0L) {
                DateUtils.getRelativeTimeSpanString(
                    comment.createdAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else {
                itemView.context.getString(R.string.choose_campaign_just_now)
            }
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
