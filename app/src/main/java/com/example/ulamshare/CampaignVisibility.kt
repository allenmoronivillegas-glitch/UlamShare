package com.example.ulamshare

import android.util.Log
import com.google.firebase.database.DataSnapshot

object CampaignVisibility {
    private const val ACTIVE_STATUS = "Active"

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
        val visibleCampaigns = mutableListOf<Campaign>()
        var hiddenCount = 0
        var inactiveCount = 0
        var invalidCount = 0

        snapshot.children.forEach { child ->
            val rawCampaign = child.getValue(Campaign::class.java)
            if (rawCampaign == null) {
                invalidCount++
                Log.w(logTag, "Skipping campaign ${child.key}: unable to parse snapshot")
                return@forEach
            }

            val campaign = rawCampaign.copy(
                campaignId = rawCampaign.campaignId?.ifBlank { child.key.orEmpty() } ?: child.key.orEmpty()
            )

            val isActive = campaign.status.equals(ACTIVE_STATUS, ignoreCase = true)
            val isVisible = isActive && campaign.hidden != true

            if (isVisible) {
                visibleCampaigns.add(campaign)
                Log.d(
                    logTag,
                    "VISIBLE campaignId=${campaign.campaignId} title=${campaign.title} status=${campaign.status} hidden=${campaign.hidden}"
                )
            } else {
                if (campaign.hidden == true) hiddenCount++
                if (!isActive) inactiveCount++
                Log.d(
                    logTag,
                    "FILTERED campaignId=${campaign.campaignId} title=${campaign.title} status=${campaign.status} hidden=${campaign.hidden}"
                )
            }
        }

        visibleCampaigns.sortByDescending { it.createdAt ?: 0L }
        Log.d(
            logTag,
            "Campaign filter summary: total=${snapshot.childrenCount}, visible=${visibleCampaigns.size}, hidden=$hiddenCount, inactive=$inactiveCount, invalid=$invalidCount"
        )

        return FilterResult(
            visibleCampaigns = visibleCampaigns,
            totalCampaigns = snapshot.childrenCount.toInt(),
            hiddenCount = hiddenCount,
            inactiveCount = inactiveCount,
            invalidCount = invalidCount
        )
    }
}
