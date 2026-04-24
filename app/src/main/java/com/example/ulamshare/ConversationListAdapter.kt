package com.example.ulamshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationListAdapter(
    private val onConversationSelected: (MessengerConversation) -> Unit
) : RecyclerView.Adapter<ConversationListAdapter.ConversationViewHolder>() {

    private val items = mutableListOf<MessengerConversation>()
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_messenger_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, timeFormat, onConversationSelected)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(conversations: List<MessengerConversation>) {
        items.clear()
        items.addAll(conversations)
        notifyDataSetChanged()
    }

    class ConversationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: TextView = view.findViewById(R.id.tvConversationAvatar)
        private val title: TextView = view.findViewById(R.id.tvConversationTitle)
        private val type: TextView = view.findViewById(R.id.tvConversationType)
        private val preview: TextView = view.findViewById(R.id.tvConversationPreview)
        private val time: TextView = view.findViewById(R.id.tvConversationTime)

        fun bind(
            item: MessengerConversation,
            timeFormat: SimpleDateFormat,
            onConversationSelected: (MessengerConversation) -> Unit
        ) {
            avatar.text = initials(item.title)
            title.text = item.title
            type.text = item.typeLabel
            preview.text = item.preview
            time.text = if (item.updatedAt > 0L) {
                timeFormat.format(Date(item.updatedAt))
            } else {
                ""
            }

            itemView.setOnClickListener { onConversationSelected(item) }
        }

        private fun initials(value: String): String {
            val words = value.trim().split(" ").filter { it.isNotBlank() }
            return when {
                words.size >= 2 -> {
                    "${words[0].first()}${words[1].first()}".uppercase(Locale.getDefault())
                }
                words.isNotEmpty() -> words[0].take(2).uppercase(Locale.getDefault())
                else -> "CH"
            }
        }
    }
}
