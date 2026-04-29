package com.example.ulamshare

import android.app.Application
import android.util.Log
import com.cloudinary.android.MediaManager

class HopeGiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FacebookKeyHashLogger.logDebugKeyHash(this)

        val config = mapOf("cloud_name" to CloudinaryConfig.CLOUD_NAME)
        try {
            MediaManager.init(this, config)
        } catch (_: IllegalStateException) {
            Log.d(TAG, "MediaManager already initialized")
        }
    }

    private companion object {
        const val TAG = "Cloudinary"
    }
}
