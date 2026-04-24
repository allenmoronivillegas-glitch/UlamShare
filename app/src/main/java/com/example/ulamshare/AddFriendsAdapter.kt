package com.example.ulamshare

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AddFriendsAdapter(
    private val onPrimaryAction: (DiscoverUser) -> Unit,
    private val onSecondaryAction: (DiscoverUser) -> Unit
) : RecyclerView.Adapter<AddFriendsAdapter.AddFriendViewHolder>() {

    private val items = mutableListOf<DiscoverUser>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddFriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_add_friend_user, parent, false)
        return AddFriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddFriendViewHolder, position: Int) {
        holder.bind(items[position], onPrimaryAction, onSecondaryAction)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(users: List<DiscoverUser>) {
        items.clear()
        items.addAll(users)
        notifyDataSetChanged()
    }

    class AddFriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: TextView = view.findViewById(R.id.tvDiscoverAvatar)
        private val name: TextView = view.findViewById(R.id.tvDiscoverName)
        private val email: TextView = view.findViewById(R.id.tvDiscoverEmail)
        private val hint: TextView = view.findViewById(R.id.tvDiscoverHint)
        private val primaryActionButton: Button = view.findViewById(R.id.btnDiscoverPrimaryAction)
        private val secondaryActionButton: Button = view.findViewById(R.id.btnDiscoverSecondaryAction)

        fun bind(
            user: DiscoverUser,
            onPrimaryAction: (DiscoverUser) -> Unit,
            onSecondaryAction: (DiscoverUser) -> Unit
        ) {
            val context = itemView.context

            avatar.text = initials(user.displayName)
            name.text = user.displayName
            email.text = user.email

            if (user.isFollowing) {
                hint.text = context.getString(R.string.message_result_hint)
                primaryActionButton.text = context.getString(R.string.message_action)
                primaryActionButton.setBackgroundResource(R.drawable.bg_friend_secondary_action)
                primaryActionButton.setTextColor(Color.parseColor("#1B5FBE"))
                secondaryActionButton.visibility = View.VISIBLE
                secondaryActionButton.text = context.getString(R.string.unfriend_action)
                secondaryActionButton.setBackgroundResource(R.drawable.bg_friend_danger_action)
                secondaryActionButton.setTextColor(Color.parseColor("#D23F4C"))
            } else {
                hint.text = context.getString(R.string.add_friend_result_hint)
                primaryActionButton.text = context.getString(R.string.add_friend_action)
                primaryActionButton.setBackgroundResource(R.drawable.bg_friend_primary_action)
                primaryActionButton.setTextColor(context.getColor(android.R.color.white))
                secondaryActionButton.visibility = View.GONE
            }

            itemView.setOnClickListener { onPrimaryAction(user) }
            primaryActionButton.setOnClickListener { onPrimaryAction(user) }
            secondaryActionButton.setOnClickListener { onSecondaryAction(user) }
        }

        private fun initials(value: String): String {
            val parts = value.trim().split(" ").filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
                parts.isNotEmpty() -> parts[0].take(2).uppercase(Locale.getDefault())
                else -> "FR"
            }
        }
    }
}
