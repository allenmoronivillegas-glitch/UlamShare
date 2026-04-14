package com.example.ulamshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_USER = 1
    private val TYPE_ADMIN = 2

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].sender == "user") TYPE_USER else TYPE_ADMIN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_USER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_admin, parent, false)
            AdminViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        if (holder is UserViewHolder) {
            holder.text.text = message.text
        } else if (holder is AdminViewHolder) {
            holder.text.text = message.text
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessage)
    }

    class AdminViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessage)
    }
}