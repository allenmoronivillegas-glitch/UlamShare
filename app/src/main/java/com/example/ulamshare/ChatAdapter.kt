package com.example.ulamshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String,
    private val interactionListener: MessageInteractionListener,
    private val actionsEnabled: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface MessageInteractionListener {
        fun onMessageActionsRequested(anchor: View, message: ChatMessage)
        fun onReactionTapped(message: ChatMessage, reactionType: String)
    }

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
        val messageBody = if (message.deleted) {
            holder.itemView.context.getString(R.string.deleted_message_label)
        } else {
            message.text
        }

        when (holder) {
            is UserViewHolder -> {
                holder.tvSender.text = holder.itemView.context.getString(R.string.you_label)
                bindReplyPreview(
                    holder.replyContainer,
                    holder.tvReplySender,
                    holder.tvReplyText,
                    message
                )
                bindActions(holder.btnMessageActions, message)
                bindReactionBar(holder.reactionBar, message)
                holder.tvMessage.text = messageBody
                holder.tvTime.text = formattedTime
            }
            is AdminViewHolder -> {
                val senderRole = normalizeRole(message)
                val senderLabel = resolveSenderLabel(message)
                holder.tvSender.text = senderLabel
                holder.tvRole.text = roleLabel(senderRole)
                holder.tvAvatar.text = senderInitial(senderLabel)
                bindReplyPreview(
                    holder.replyContainer,
                    holder.tvReplySender,
                    holder.tvReplyText,
                    message
                )
                bindActions(holder.btnMessageActions, message)
                bindReactionBar(holder.reactionBar, message)
                holder.tvMessage.text = messageBody
                holder.tvTime.text = formattedTime
            }
        }
    }

    override fun getItemCount() = messages.size

    private fun normalizeRole(message: ChatMessage): String {
        return normalizeRoleValue(message.senderRole.ifBlank { message.sender })
    }

    private fun normalizeRoleValue(value: String): String {
        val rawRole = value.trim().lowercase(Locale.getDefault())
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

    private fun bindReplyPreview(
        container: View,
        senderView: TextView,
        textView: TextView,
        message: ChatMessage
    ) {
        val preview = resolveReplyPreview(message)
        if (preview == null) {
            container.visibility = View.GONE
            senderView.text = ""
            textView.text = ""
            return
        }

        container.visibility = View.VISIBLE
        senderView.text = preview.sender.ifBlank {
            container.context.getString(R.string.reply_label)
        }
        textView.text = preview.text
    }

    private fun resolveReplyPreview(message: ChatMessage): ReplyPreview? {
        if (message.replyTo.isBlank() && message.replyText.isBlank()) return null

        val quoted = message.replyTo.takeIf { it.isNotBlank() }?.let { replyKey ->
            messages.firstOrNull { it.key == replyKey }
        }

        val replyText = when {
            quoted != null && quoted.text.isNotBlank() -> quoted.text.trim()
            message.replyText.isNotBlank() -> message.replyText.trim()
            else -> ""
        }
        if (replyText.isBlank()) return null

        val replySender = when {
            quoted != null && quoted.senderId == currentUserId -> "You"
            quoted != null -> resolveSenderLabel(quoted)
            message.replySenderName.isNotBlank() -> message.replySenderName
            message.replySenderRole.isNotBlank() -> roleLabel(normalizeRoleValue(message.replySenderRole))
            else -> ""
        }

        return ReplyPreview(replySender, replyText)
    }

    private fun bindActions(button: ImageButton, message: ChatMessage) {
        if (!actionsEnabled || message.deleted) {
            button.visibility = View.GONE
            button.setOnClickListener(null)
            return
        }

        button.visibility = View.VISIBLE
        button.setOnClickListener {
            interactionListener.onMessageActionsRequested(it, message)
        }
    }

    private fun bindReactionBar(container: LinearLayout, message: ChatMessage) {
        container.removeAllViews()

        if (message.deleted) {
            container.visibility = View.GONE
            return
        }

        val reactions = MessageReactionUi.summarize(message.reactions, currentUserId)
        if (reactions.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        val context = container.context
        val horizontalPadding = dp(context, 10)
        val verticalPadding = dp(context, 4)
        val endMargin = dp(context, 6)

        reactions.forEach { summary ->
            val chip = TextView(context).apply {
                text = "${MessageReactionUi.displayLabel(summary.type)} ${summary.count}"
                textSize = 11f
                setTextColor(
                    if (summary.reactedByMe) {
                        ContextCompat.getColor(context, android.R.color.white)
                    } else {
                        Color.parseColor("#1E63BF")
                    }
                )
                background = ContextCompat.getDrawable(
                    context,
                    if (summary.reactedByMe) {
                        R.drawable.bg_support_chip_active
                    } else {
                        R.drawable.bg_support_chip
                    }
                )
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                setOnClickListener {
                    interactionListener.onReactionTapped(message, summary.type)
                }
            }

            container.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = endMargin
            })
        }
    }

    private fun senderInitial(name: String): String {
        if (name.equals("HopeGive Assistant", ignoreCase = true)) return "HG"

        val clean = name.trim()
        if (clean.isEmpty()) return "S"
        val parts = clean.split(" ").filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            (parts[0].first().toString() + parts[1].first().toString()).uppercase(Locale.getDefault())
        } else {
            clean.take(2).uppercase(Locale.getDefault())
        }
    }

    private fun dp(context: android.content.Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val replyContainer: View = view.findViewById(R.id.replyContainer)
        val tvReplySender: TextView = view.findViewById(R.id.tvReplySender)
        val tvReplyText: TextView = view.findViewById(R.id.tvReplyText)
        val btnMessageActions: ImageButton = view.findViewById(R.id.btnMessageActions)
        val reactionBar: LinearLayout = view.findViewById(R.id.reactionBar)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    class AdminViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvRole: TextView = view.findViewById(R.id.tvRole)
        val replyContainer: View = view.findViewById(R.id.replyContainer)
        val tvReplySender: TextView = view.findViewById(R.id.tvReplySender)
        val tvReplyText: TextView = view.findViewById(R.id.tvReplyText)
        val btnMessageActions: ImageButton = view.findViewById(R.id.btnMessageActions)
        val reactionBar: LinearLayout = view.findViewById(R.id.reactionBar)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    private data class ReplyPreview(
        val sender: String,
        val text: String
    )
}
