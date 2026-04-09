package com.example.ulamshare

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Campaign(
    val campaignId: String = "",
    val title: String = "",
    val description: String = "",
    val goal: Int = 0,
    val raised: Int = 0,
    val status: String = "",
    val hidden: Boolean = false,
    val cat: String = "",
    val date: String = "",
    val createdAt: Long = 0
) {
    val goalAmount: Double
        get() = goal.toDouble()

    val raisedAmount: Double
        get() = raised.toDouble()
}
