package com.example.ulamshare

data class ChatMessage(
    val text: String = "",
    val sender: String = "",
    val time: Long = 0L
)