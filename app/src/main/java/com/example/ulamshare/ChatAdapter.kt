package com.example.ulamshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_OUTGOING = 1
    private val TYPE_INCOMING = 2

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isCurrentUser = message.senderId == currentUserId ||
            (message.sender.equals("user", ignoreCase = true) && message.senderId.isBlank())
        return if (isCurrentUser) TYPE_OUTGOING else TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_OUTGOING) {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AdminViewHolder(inflater.inflate(R.layout.item_chat_admin, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val formattedTime = if (message.time != 0L) timeFormat.format(Date(message.time)) else ""

        when (holder) {
            is UserViewHolder -> {
                holder.tvSender.text = holder.itemView.context.getString(R.string.you_label)
                holder.tvMessage.text = message.text
                holder.tvTime.text = formattedTime
            }
            is AdminViewHolder -> {
                val senderRole = normalizeRole(message)
                val senderLabel = resolveSenderLabel(message)
                holder.tvSender.text = senderLabel
                holder.tvRole.text = roleLabel(senderRole)
                holder.tvAvatar.text = senderInitial(senderLabel)
                holder.tvMessage.text = message.text
                holder.tvTime.text = formattedTime
            }
        }
    }

    override fun getItemCount() = messages.size

    private fun normalizeRole(message: ChatMessage): String {
        val rawRole = message.senderRole.ifBlank { message.sender }.trim().lowercase(Locale.getDefault())
        return when (rawRole) {
            "super admin", "super_admin", "superadmin" -> "superadmin"
            "moderator", "mod" -> "moderator"
            "admin" -> "admin"
            "support", "agent" -> "support"
            else -> "user"
        }
    }

    private fun roleLabel(role: String): String {
        return when (role) {
            "superadmin" -> "Super Admin"
            "moderator" -> "Moderator"
            "admin" -> "Admin"
            "support" -> "Support"
            else -> "User"
        }
    }

    private fun resolveSenderLabel(message: ChatMessage): String {
        if (message.senderName.isNotBlank()) return message.senderName
        return when (normalizeRole(message)) {
            "superadmin" -> "Super Admin"
            "moderator" -> "Moderator"
            "admin" -> "Admin"
            "support" -> "Support Team"
            else -> "User"
        }
    }

    private fun senderInitial(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) return "S"
        val parts = clean.split(" ").filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            (parts[0].first().toString() + parts[1].first().toString()).uppercase(Locale.getDefault())
        } else {
            clean.take(2).uppercase(Locale.getDefault())
        }
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    class AdminViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvRole: TextView = view.findViewById(R.id.tvRole)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }
}
