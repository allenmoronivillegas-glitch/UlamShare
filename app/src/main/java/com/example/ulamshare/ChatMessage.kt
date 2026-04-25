package com.example.ulamshare

data class ChatReactionEntry(
    val emoji: String = "",
    val by: String = "",
    val role: String = "",
    val time: Long = 0L
)

data class ChatMessage(
    val key: String = "",
    val text: String = "",
    val sender: String = "",
    val time: Long = 0L,
    val senderRole: String = "",
    val senderName: String = "",
    val senderId: String = "",
    val deleted: Boolean = false,
    val replyTo: String = "",
    val replyText: String = "",
    val replySenderName: String = "",
    val replySenderRole: String = "",
    val reactions: Map<String, ChatReactionEntry> = emptyMap()
)
