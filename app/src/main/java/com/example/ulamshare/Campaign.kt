package com.example.ulamshare

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Campaign(
    val title: String = "",
    val description: String = "",
    val goal: Int = 0,
    val raised: Int = 0,
    val status: String = "",
    val cat: String = "",
    val date: String = "",
    val createdAt: Long = 0
)
