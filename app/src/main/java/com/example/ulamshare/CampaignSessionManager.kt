package com.example.ulamshare

import android.content.Context

object CampaignSessionManager {
    private const val PREFS_NAME = "ulamshare_session"
    private const val KEY_CAMPAIGN_ID = "campaign_id"
    private const val KEY_CAMPAIGN_TITLE = "campaign_title"
    private const val KEY_CAMPAIGN_DESCRIPTION = "campaign_description"
    private const val KEY_CAMPAIGN_STATUS = "campaign_status"
    private const val KEY_CAMPAIGN_CATEGORY = "campaign_category"
    private const val KEY_ASSIGNED_AT = "campaign_assigned_at"

    fun save(context: Context, campaign: UserCampaign) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CAMPAIGN_ID, campaign.campaignId)
            .putString(KEY_CAMPAIGN_TITLE, campaign.title)
            .putString(KEY_CAMPAIGN_DESCRIPTION, campaign.description)
            .putString(KEY_CAMPAIGN_STATUS, campaign.status)
            .putString(KEY_CAMPAIGN_CATEGORY, campaign.category)
            .putLong(KEY_ASSIGNED_AT, campaign.assignedAt)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
