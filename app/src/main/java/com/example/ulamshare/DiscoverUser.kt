package com.example.ulamshare

data class DiscoverUser(
    val uid: String,
    val displayName: String,
    val email: String,
    var isFollowing: Boolean = false
)
