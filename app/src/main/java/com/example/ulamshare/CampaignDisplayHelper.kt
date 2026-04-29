package com.example.ulamshare

import android.util.Log
import com.google.firebase.database.DataSnapshot
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CampaignDisplayHelper {

    data class CampaignBuckets(
        val activeCampaigns: List<Campaign>,
        val expiredCampaigns: List<Campaign>,
        val totalCampaigns: Int,
        val hiddenCount: Int,
        val nonActiveCount: Int,
        val invalidCount: Int
    ) {
        val filteredCount: Int
            get() = hiddenCount + nonActiveCount + invalidCount + expiredCampaigns.size
    }

    fun groupCampaigns(snapshot: DataSnapshot, logTag: String): CampaignBuckets {
        val activeCampaigns = mutableListOf<Campaign>()
        val expiredCampaigns = mutableListOf<Campaign>()
        var hiddenCount = 0
        var nonActiveCount = 0
        var invalidCount = 0

        snapshot.children.forEach { child ->
            val campaign = parseCampaign(child)
            if (campaign == null) {
                invalidCount++
                Log.w(logTag, "Skipping campaign ${child.key}: unable to parse snapshot")
                return@forEach
            }

            if (campaign.hidden == true) {
                hiddenCount++
                Log.d(logTag, "HIDDEN campaignId=${campaign.campaignId} title=${campaign.title}")
                return@forEach
            }

            if (isExpired(campaign)) {
                expiredCampaigns += campaign
                Log.d(logTag, "EXPIRED campaignId=${campaign.campaignId} title=${campaign.title}")
                return@forEach
            }

            if (!isActiveStatus(campaign.status)) {
                nonActiveCount++
                Log.d(
                    logTag,
                    "FILTERED campaignId=${campaign.campaignId} title=${campaign.title} status=${campaign.status}"
                )
                return@forEach
            }

            activeCampaigns += campaign
            Log.d(logTag, "ACTIVE campaignId=${campaign.campaignId} title=${campaign.title}")
        }

        val sortedActive = sortActiveCampaigns(activeCampaigns)
        val sortedExpired = sortExpiredCampaigns(expiredCampaigns)

        Log.d(
            logTag,
            "Campaign buckets: total=${snapshot.childrenCount}, active=${sortedActive.size}, " +
                "expired=${sortedExpired.size}, hidden=$hiddenCount, nonActive=$nonActiveCount, invalid=$invalidCount"
        )

        return CampaignBuckets(
            activeCampaigns = sortedActive,
            expiredCampaigns = sortedExpired,
            totalCampaigns = snapshot.childrenCount.toInt(),
            hiddenCount = hiddenCount,
            nonActiveCount = nonActiveCount,
            invalidCount = invalidCount
        )
    }

    fun parseCampaign(snapshot: DataSnapshot): Campaign? {
        val raw = snapshot.getValue(Campaign::class.java) ?: return null
        val key = snapshot.key.orEmpty()
        return raw.copy(
            campaignId = raw.campaignId?.ifBlank { key } ?: key
        )
    }

    fun isExpired(campaign: Campaign): Boolean {
        val normalizedStatus = campaign.status.orEmpty().trim().lowercase(Locale.getDefault())
        if (normalizedStatus == "expired" || normalizedStatus == "completed") {
            return true
        }

        val deadline = parseCampaignDate(campaign.date) ?: return false
        val endOfDay = Calendar.getInstance().apply {
            time = deadline
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return System.currentTimeMillis() > endOfDay.timeInMillis
    }

    fun isActiveStatus(status: String?): Boolean {
        val normalized = status.orEmpty().trim().lowercase(Locale.getDefault())
        return normalized.isBlank() || normalized == "active"
    }

    fun canDonate(campaign: Campaign): Boolean {
        return campaign.hidden != true && !isExpired(campaign) && isActiveStatus(campaign.status)
    }

    fun campaignEmoji(campaign: Campaign): String {
        return campaignEmoji(campaign.emoji, campaign.cat, campaign.title)
    }

    fun campaignEmoji(emoji: String?, category: String?, title: String?): String {
        val savedEmoji = emoji.orEmpty().trim()
        if (savedEmoji.isNotEmpty()) {
            return savedEmoji
        }

        val haystack = buildString {
            append(category.orEmpty())
            append(' ')
            append(title.orEmpty())
        }.trim().lowercase(Locale.getDefault())

        return when {
            haystack.contains("hospital") || haystack.contains("health") || haystack.contains("medical") -> "\uD83C\uDFE5"
            haystack.contains("school") || haystack.contains("education") || haystack.contains("supplies") || haystack.contains("student") || haystack.contains("book") -> "\uD83D\uDCDA"
            haystack.contains("typhoon") || haystack.contains("flood") || haystack.contains("storm") || haystack.contains("wave") -> "\uD83C\uDF0A"
            haystack.contains("disaster") || haystack.contains("relief") || haystack.contains("emergency") -> "\uD83D\uDEA8"
            haystack.contains("environment") || haystack.contains("tree") || haystack.contains("earth") || haystack.contains("nature") -> "\uD83C\uDF31"
            haystack.contains("food") || haystack.contains("meal") || haystack.contains("feeding") || haystack.contains("hunger") -> "\uD83C\uDF72"
            haystack.contains("animal") || haystack.contains("pet") -> "\uD83D\uDC3E"
            haystack.contains("housing") || haystack.contains("shelter") || haystack.contains("home") -> "\uD83C\uDFE0"
            else -> "\uD83D\uDC99"
        }
    }

    fun statusLabel(campaign: Campaign): String {
        return when {
            isExpired(campaign) -> "Expired"
            !campaign.status.isNullOrBlank() -> campaign.status.orEmpty().trim()
            else -> "Active"
        }
    }

    fun progressPercent(campaign: Campaign): Int {
        val goal = campaign.goal ?: 0
        val raised = campaign.raised ?: 0
        return if (goal > 0) {
            ((raised.toDouble() / goal.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    fun formatPeso(amount: Int?): String {
        return String.format(Locale.US, "\u20B1%,d", amount ?: 0)
    }

    fun recentPreview(campaigns: List<Campaign>, maxItems: Int = 5): List<Campaign> {
        return recentCampaigns(campaigns).take(maxItems)
    }

    fun recentCampaigns(campaigns: List<Campaign>): List<Campaign> {
        return campaigns.sortedByDescending { createdSortValue(it) }
    }

    fun sortActiveCampaigns(campaigns: List<Campaign>): List<Campaign> {
        return campaigns.sortedWith(
            compareBy<Campaign>(
                { daysUntilDeadline(it) ?: Int.MAX_VALUE },
                { -(createdSortValue(it)) }
            )
        )
    }

    fun sortExpiredCampaigns(campaigns: List<Campaign>): List<Campaign> {
        return campaigns.sortedByDescending { expiredSortValue(it) }
    }

    fun daysUntilDeadline(campaign: Campaign): Int? {
        val deadline = parseCampaignDate(campaign.date) ?: return null
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val diffMillis = deadline.time - today.time
        return kotlin.math.ceil(diffMillis.toDouble() / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    private fun createdSortValue(campaign: Campaign): Long {
        val createdAt = campaign.createdAt ?: 0L
        return if (createdAt > 0L) createdAt else (parseCampaignDate(campaign.date)?.time ?: 0L)
    }

    private fun expiredSortValue(campaign: Campaign): Long {
        return parseCampaignDate(campaign.date)?.time ?: createdSortValue(campaign)
    }

    private fun parseCampaignDate(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            DATE_FORMAT.parse(dateString)
        } catch (_: ParseException) {
            null
        }
    }

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
}
