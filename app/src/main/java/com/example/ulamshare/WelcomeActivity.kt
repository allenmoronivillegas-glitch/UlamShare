package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.widget.Button
import android.widget.TextView

class WelcomeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)
        val btnContinueGuest = findViewById<TextView>(R.id.btnContinueGuest)

        // Highlight "Guest" in the text
        val guestText = getString(R.string.continue_as_guest)
        btnContinueGuest.text = Html.fromHtml(guestText, Html.FROM_HTML_MODE_LEGACY)

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnContinueGuest.setOnClickListener {
            // Navigate to DashboardActivity
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }
}