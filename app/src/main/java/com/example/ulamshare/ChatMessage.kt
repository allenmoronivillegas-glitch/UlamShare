package com.example.ulamshare

data class ChatMessage(
    val text: String = "",
    val sender: String = "",
    val time: Long = 0L,
    val senderRole: String = "",
    val senderName: String = "",
    val senderId: String = ""
)
