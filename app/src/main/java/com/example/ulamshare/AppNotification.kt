package com.example.ulamshare

import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class for in-app notifications
 * Stored in SharedPreferences and displayed in Profile screen
 */
data class AppNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val type: String = "campaign" // "campaign", "system", etc.
) {
    
    fun getTimeAgo(): String {
        val currentTime = System.currentTimeMillis()
        val diffInMs = currentTime - timestamp
        val diffInSeconds = diffInMs / 1000
        val diffInMinutes = diffInSeconds / 60
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24
        
        return when {
            diffInSeconds < 60 -> "Just now"
            diffInMinutes < 60 -> "$diffInMinutes minute${if (diffInMinutes > 1) "s" else ""} ago"
            diffInHours < 24 -> "$diffInHours hour${if (diffInHours > 1) "s" else ""} ago"
            diffInDays < 7 -> "$diffInDays day${if (diffInDays > 1) "s" else ""} ago"
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
}
