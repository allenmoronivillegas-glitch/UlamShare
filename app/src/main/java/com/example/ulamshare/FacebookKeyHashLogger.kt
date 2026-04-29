package com.example.ulamshare

import android.content.pm.ApplicationInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

object FacebookKeyHashLogger {
    private const val TAG = "FacebookKeyHash"

    fun logDebugKeyHash(context: Context) {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable) return

        try {
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            )

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures.orEmpty()
            }

            signatures.forEach { signature ->
                val messageDigest = MessageDigest.getInstance("SHA")
                messageDigest.update(signature.toByteArray())
                val keyHash = Base64.encodeToString(messageDigest.digest(), Base64.NO_WRAP)
                Log.d(TAG, "Key Hash: $keyHash")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to get key hash", error)
        }
    }
}
