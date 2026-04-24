package com.example.ulamshare

data class MessengerConversation(
    val key: String,
    val channel: String,
    val rootPath: String,
    val title: String,
    val typeLabel: String,
    val preview: String,
    val updatedAt: Long,
    val chatType: String,
    val participantUserId: String = "",
    val participantEmail: String = ""
)
