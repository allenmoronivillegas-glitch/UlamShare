package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class SplashActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)
        
        auth = FirebaseAuth.getInstance()
        
        enableFullScreen()

        // Delay for 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, 3000)
    }

    private fun checkUserStatus() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            CampaignAssignmentManager.ensureCampaignForAuthenticatedUser(
                context = this,
                user = currentUser,
                profileSeed = currentUser.toProfileSeed(),
                onComplete = {
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
}
