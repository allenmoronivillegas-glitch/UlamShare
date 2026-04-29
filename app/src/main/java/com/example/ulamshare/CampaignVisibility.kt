package com.example.ulamshare

import com.google.firebase.database.DataSnapshot

object CampaignVisibility {

    data class FilterResult(
        val visibleCampaigns: List<Campaign>,
        val totalCampaigns: Int,
        val hiddenCount: Int,
        val inactiveCount: Int,
        val invalidCount: Int
    ) {
        val filteredCount: Int
            get() = hiddenCount + inactiveCount + invalidCount
    }

    fun filterVisibleCampaigns(snapshot: DataSnapshot, logTag: String): FilterResult {
        val buckets = CampaignDisplayHelper.groupCampaigns(snapshot, logTag)
        return FilterResult(
            visibleCampaigns = buckets.activeCampaigns,
            totalCampaigns = buckets.totalCampaigns,
            hiddenCount = buckets.hiddenCount,
            inactiveCount = buckets.nonActiveCount + buckets.expiredCampaigns.size,
            invalidCount = buckets.invalidCount
        )
    }
}
