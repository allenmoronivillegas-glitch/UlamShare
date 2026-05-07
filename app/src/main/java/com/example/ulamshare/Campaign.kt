package com.example.ulamshare

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Campaign(
    var campaignId: String? = "",
    var title: String? = "",
    var description: String? = "",
    var emoji: String? = "",
    var goal: Int? = 0,
    var goalAmount: Int? = null,
    var targetAmount: Int? = null,
    var raised: Int? = 0,
    var raisedAmount: Int? = null,
    var currentAmount: Int? = null,
    var amountRaised: Int? = null,
    var status: String? = "",
    var hidden: Boolean? = false,
    var cat: String? = "",
    var date: String? = "",
    var createdAt: Long? = 0
)
