package com.example.ulamshare

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

data class FollowListUser(
    val userId: String,
    val fullName: String,
    val email: String = "",
    val profilePhotoUrl: String = "",
    val profilePhotoLocalUri: String = "",
    val role: String = "",
    val status: String = ""
)

class FollowListAdapter(
    private val onUserClick: (FollowListUser) -> Unit,
    private val onUnfollowClick: (FollowListUser) -> Unit
) : RecyclerView.Adapter<FollowListAdapter.FollowUserViewHolder>() {

    private val items = mutableListOf<FollowListUser>()
    private var showUnfollowAction = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follow_user, parent, false)
        return FollowUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowUserViewHolder, position: Int) {
        holder.bind(items[position], showUnfollowAction, onUserClick, onUnfollowClick)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(users: List<FollowListUser>, canUnfollow: Boolean) {
        items.clear()
        items.addAll(users)
        showUnfollowAction = canUnfollow
        notifyDataSetChanged()
    }

    class FollowUserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val photo: ImageView = view.findViewById(R.id.ivFollowPhoto)
        private val initials: TextView = view.findViewById(R.id.tvFollowInitials)
        private val name: TextView = view.findViewById(R.id.tvFollowName)
        private val meta: TextView = view.findViewById(R.id.tvFollowMeta)
        private val actionButton: Button = view.findViewById(R.id.btnFollowAction)

        fun bind(
            user: FollowListUser,
            showUnfollow: Boolean,
            onUserClick: (FollowListUser) -> Unit,
            onUnfollowClick: (FollowListUser) -> Unit
        ) {
            val publicName = PrivacyDisplayHelper.publicName(user.fullName)
            name.text = publicName
            meta.text = buildMeta(user)
            meta.visibility = if (meta.text.isBlank()) View.GONE else View.VISIBLE
            initials.text = initials(publicName)
            bindAvatar(user)

            actionButton.visibility = if (showUnfollow) View.VISIBLE else View.GONE
            actionButton.setOnClickListener { onUnfollowClick(user) }
            itemView.setOnClickListener { onUserClick(user) }
            photo.setOnClickListener { onUserClick(user) }
            initials.setOnClickListener { onUserClick(user) }
            name.setOnClickListener { onUserClick(user) }
        }

        private fun bindAvatar(user: FollowListUser) {
            val localUri = user.profilePhotoLocalUri
            val remoteUrl = user.profilePhotoUrl
            if (localUri.isBlank() && remoteUrl.isBlank()) {
                showInitials()
                return
            }

            photo.visibility = View.VISIBLE
            initials.visibility = View.GONE
            photo.clipToOutline = true

            if (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://")) {
                CampaignImageLoader.load(photo, remoteUrl, R.drawable.plant)
            } else {
                val imageUri = localUri.ifBlank { remoteUrl }
                runCatching {
                    photo.setImageURI(Uri.parse(imageUri))
                }.onFailure {
                    showInitials()
                }
            }
        }

        private fun showInitials() {
            photo.setImageDrawable(null)
            photo.visibility = View.GONE
            initials.visibility = View.VISIBLE
        }

        private fun buildMeta(user: FollowListUser): String {
            return PrivacyDisplayHelper.publicMeta(user.role, user.status)
        }

        private fun initials(value: String): String {
            val parts = value.trim().split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
                parts.isNotEmpty() -> parts[0].take(2).uppercase(Locale.getDefault())
                else -> "HG"
            }
        }
    }
}
