package com.example.ulamshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_USER = 1
    private val TYPE_ADMIN = 2

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender == "user") TYPE_USER else TYPE_ADMIN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AdminViewHolder(inflater.inflate(R.layout.item_chat_admin, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val formattedTime = if (message.time != 0L)
            timeFormat.format(Date(message.time)) else ""

        when (holder) {
            is UserViewHolder -> {
                holder.tvMessage.text = message.text
                holder.tvTime.text = formattedTime
            }
            is AdminViewHolder -> {
                holder.tvMessage.text = message.text
                holder.tvTime.text = formattedTime
            }
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    class AdminViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }
}