package com.example.ulamshare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.UUID

object AppNotificationManager {
    private const val CHANNEL_ID = "ulamshare_updates"
    private const val CHANNEL_NAME = "UlamShare Updates"

    fun notifyFriendAdded(context: Context, friendName: String) {
        postEvent(
            context = context,
            title = "Friend Added",
            message = "$friendName is now in your friends list.",
            type = "friend",
            destinationIntent = Intent(context, AddFriendsActivity::class.java)
        )
    }

    fun notifyFriendRemoved(context: Context, friendName: String) {
        postEvent(
            context = context,
            title = "Friend Removed",
            message = "$friendName was removed from your friends list.",
            type = "friend",
            destinationIntent = Intent(context, AddFriendsActivity::class.java)
        )
    }

    fun notifyDonation(context: Context, campaignTitle: String, amount: Int) {
        val safeTitle = campaignTitle.ifBlank { "Unknown Campaign" }
        postEvent(
            context = context,
            title = "Donation Received",
            message = "You donated \u20B1$amount to $safeTitle.",
            type = "donation",
            destinationIntent = Intent(context, NotificationActivity::class.java)
        )
    }

    fun notifyCampaignUpdate(
        context: Context,
        title: String,
        message: String,
        campaignId: String? = null
    ) {
        val destinationIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("campaignId", campaignId.orEmpty())
        }

        postEvent(
            context = context,
            title = title,
            message = message,
            type = "campaign",
            destinationIntent = destinationIntent
        )
    }

    private fun postEvent(
        context: Context,
        title: String,
        message: String,
        type: String,
        destinationIntent: Intent
    ) {
        val appContext = context.applicationContext
        NotificationRepository.init(appContext)
        NotificationRepository.saveNotification(
            AppNotification(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                timestamp = System.currentTimeMillis(),
                type = type
            )
        )
        showSystemNotification(appContext, title, message, destinationIntent)
    }

    private fun showSystemNotification(
        context: Context,
        title: String,
        message: String,
        destinationIntent: Intent
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = destinationIntent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.app_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
