package com.example.ulamshare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing in-app notifications using SharedPreferences.
 * Notifications are stored per signed-in user so histories do not mix together.
 */
object NotificationRepository {
    private const val TAG = "NotificationRepository"
    private const val PREFS_NAME = "app_notifications"
    private const val NOTIFICATIONS_KEY_PREFIX = "notifications_list"
    private const val MAX_NOTIFICATIONS = 50
    private const val DUPLICATE_WINDOW_MS = 2 * 60 * 1000L

    private var preferences: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (preferences == null) {
            preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "NotificationRepository initialized")
        }
    }

    fun saveNotification(notification: AppNotification) {
        try {
            val notifications = getNotifications().toMutableList()
            val duplicateExists = notifications.any {
                it.type == notification.type &&
                    it.title == notification.title &&
                    it.message == notification.message &&
                    kotlin.math.abs(it.timestamp - notification.timestamp) <= DUPLICATE_WINDOW_MS
            }

            if (duplicateExists) {
                Log.d(TAG, "Skipping duplicate notification: ${notification.title}")
                return
            }

            notifications.add(0, notification)

            if (notifications.size > MAX_NOTIFICATIONS) {
                notifications.subList(MAX_NOTIFICATIONS, notifications.size).clear()
            }

            val json = gson.toJson(notifications)
            preferences?.edit()?.putString(resolveNotificationsKey(), json)?.apply()

            Log.d(TAG, "Notification saved: ${notification.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification: ${e.message}", e)
        }
    }

    fun getNotifications(): List<AppNotification> {
        return try {
            val json = preferences?.getString(resolveNotificationsKey(), null) ?: return emptyList()
            val type = object : TypeToken<List<AppNotification>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving notifications: ${e.message}", e)
            emptyList()
        }
    }

    fun clearAll() {
        try {
            preferences?.edit()?.remove(resolveNotificationsKey())?.apply()
            Log.d(TAG, "All notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing notifications: ${e.message}", e)
        }
    }

    fun deleteNotification(id: String) {
        try {
            val notifications = getNotifications().toMutableList()
            notifications.removeAll { it.id == id }

            val json = gson.toJson(notifications)
            preferences?.edit()?.putString(resolveNotificationsKey(), json)?.apply()

            Log.d(TAG, "Notification deleted: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification: ${e.message}", e)
        }
    }

    fun getNotificationCount(): Int {
        return getNotifications().size
    }

    private fun resolveNotificationsKey(): String {
        val userKey = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        return "${NOTIFICATIONS_KEY_PREFIX}_$userKey"
    }
}
