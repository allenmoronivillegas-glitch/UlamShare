package com.example.ulamshare

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for managing in-app notifications using SharedPreferences
 * Handles storage and retrieval of notification history
 */
object NotificationRepository {
    private const val TAG = "NotificationRepository"
    private const val PREFS_NAME = "campaign_notifications"
    private const val NOTIFICATIONS_KEY = "notifications_list"
    private const val MAX_NOTIFICATIONS = 50 // Keep last 50 notifications
    
    private var preferences: SharedPreferences? = null
    private val gson = Gson()

    fun init(context: Context) {
        if (preferences == null) {
            preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "NotificationRepository initialized")
        }
    }

    /**
     * Save a new notification to the list
     */
    fun saveNotification(notification: AppNotification) {
        try {
            val notifications = getNotifications().toMutableList()
            notifications.add(0, notification) // Add to top
            
            // Keep only the last MAX_NOTIFICATIONS
            if (notifications.size > MAX_NOTIFICATIONS) {
                notifications.dropLast(notifications.size - MAX_NOTIFICATIONS)
            }
            
            val json = gson.toJson(notifications)
            preferences?.edit()?.putString(NOTIFICATIONS_KEY, json)?.apply()
            
            Log.d(TAG, "✓ Notification saved: ${notification.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification: ${e.message}", e)
        }
    }

    /**
     * Get all notifications from storage
     */
    fun getNotifications(): List<AppNotification> {
        return try {
            val json = preferences?.getString(NOTIFICATIONS_KEY, null) ?: return emptyList()
            val type = object : TypeToken<List<AppNotification>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving notifications: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Clear all notifications
     */
    fun clearAll() {
        try {
            preferences?.edit()?.remove(NOTIFICATIONS_KEY)?.apply()
            Log.d(TAG, "All notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing notifications: ${e.message}", e)
        }
    }

    /**
     * Delete a specific notification
     */
    fun deleteNotification(id: String) {
        try {
            val notifications = getNotifications().toMutableList()
            notifications.removeAll { it.id == id }
            
            val json = gson.toJson(notifications)
            preferences?.edit()?.putString(NOTIFICATIONS_KEY, json)?.apply()
            
            Log.d(TAG, "Notification deleted: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification: ${e.message}", e)
        }
    }

    /**
     * Get notification count
     */
    fun getNotificationCount(): Int {
        return getNotifications().size
    }
}
