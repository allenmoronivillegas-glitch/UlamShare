package com.example.ulamshare

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CampaignMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("CampaignMessaging", "FCM token refreshed: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"] ?: "New Campaign Available"
        val body = message.data["body"] ?: "A new campaign is now available."
        AppNotificationManager.notifyCampaignUpdate(this, title, body, message.data["campaignId"])
    }
}
