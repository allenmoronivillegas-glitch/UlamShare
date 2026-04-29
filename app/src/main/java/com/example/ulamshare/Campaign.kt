package com.example.ulamshare

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Campaign(
    var campaignId: String? = "",
    var title: String? = "",
    var description: String? = "",
    var emoji: String? = "",
    var goal: Int? = 0,
    var raised: Int? = 0,
    var status: String? = "",
    var hidden: Boolean? = false,
    var cat: String? = "",
    var date: String? = "",
    var createdAt: Long? = 0
) {
    val goalAmount: Double
        get() = (goal ?: 0).toDouble()

    val raisedAmount: Double
        get() = (raised ?: 0).toDouble()
}
