package com.example.ulamshare

data class DiscoverUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val profilePhotoUrl: String = "",
    val profilePhotoLocalUri: String = "",
    val role: String = "",
    val status: String = "",
    var isFollowing: Boolean = false
)
