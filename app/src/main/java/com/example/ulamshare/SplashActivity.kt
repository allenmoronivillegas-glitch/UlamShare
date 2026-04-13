package com.example.ulamshare

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging

class SplashActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)
        
        auth = FirebaseAuth.getInstance()
        
        // Initialize Notification Repository
        NotificationRepository.init(this)
        
        enableFullScreen()
        subscribeToCampaignNotifications()
        requestNotificationPermissionIfNeeded()

        // Delay for 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, 3000)
    }

    private fun checkUserStatus() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            CampaignAssignmentManager.syncForAuthenticatedUser(
                context = this,
                user = currentUser,
                profileSeed = currentUser.toProfileSeed(),
                onComplete = {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                },
                onError = {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            )
        } else {
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun FirebaseUser.toProfileSeed(): Map<String, Any> {
        val seed = mutableMapOf<String, Any>(
            "uid" to uid,
            "email" to (email ?: "")
        )
        displayName?.takeIf { it.isNotBlank() }?.let { seed["fullName"] = it }
        return seed
    }

    private fun subscribeToCampaignNotifications() {
        FirebaseMessaging.getInstance().subscribeToTopic("campaigns")
            .addOnSuccessListener {
                Log.d("SplashActivity", "Subscribed to campaigns topic")
            }
            .addOnFailureListener { error ->
                Log.e("SplashActivity", "Failed to subscribe to campaigns topic", error)
            }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }
}
