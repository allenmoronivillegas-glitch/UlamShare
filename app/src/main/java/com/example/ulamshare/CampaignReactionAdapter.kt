package com.example.ulamshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class CampaignReactionAdapter : RecyclerView.Adapter<CampaignReactionAdapter.ReactionViewHolder>() {
    private val allItems = mutableListOf<CampaignPostReaction>()
    private val visibleItems = mutableListOf<CampaignPostReaction>()
    private var activeFilter: String = FILTER_ALL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_campaign_reaction, parent, false)
        return ReactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReactionViewHolder, position: Int) {
        holder.bind(visibleItems[position])
    }

    override fun getItemCount(): Int = visibleItems.size

    fun submitList(reactions: List<CampaignPostReaction>) {
        allItems.clear()
        allItems.addAll(reactions)
        applyFilter(activeFilter)
    }

    fun applyFilter(type: String) {
        activeFilter = type
        visibleItems.clear()
        visibleItems.addAll(
            if (type == FILTER_ALL) {
                allItems
            } else {
                allItems.filter { it.type == type }
            }
        )
        notifyDataSetChanged()
    }

    class ReactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarView: TextView = itemView.findViewById(R.id.tvReactionAvatar)
        private val nameView: TextView = itemView.findViewById(R.id.tvReactionName)
        private val roleView: TextView = itemView.findViewById(R.id.tvReactionRole)
        private val emojiView: TextView = itemView.findViewById(R.id.tvReactionEmoji)

        fun bind(reaction: CampaignPostReaction) {
            avatarView.text = initials(reaction.actorName)
            nameView.text = reaction.actorName
            roleView.text = roleLabel(reaction.actorRole)
            emojiView.text = CampaignReactionUi.emoji(reaction.type)
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
                CampaignFeedPost.ROLE_MODERATOR -> "Moderator"
                CampaignFeedPost.ROLE_GUEST -> "Guest"
                else -> "User"
            }
        }
    }

    companion object {
        const val FILTER_ALL = "all"
    }
}
