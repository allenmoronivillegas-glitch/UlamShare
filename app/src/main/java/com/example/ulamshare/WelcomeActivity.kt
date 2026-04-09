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

        // Using simple text instead of HTML mode to avoid potential crashes or formatting issues
        btnContinueGuest.text = "Continue as Guest"

        btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnContinueGuest.setOnClickListener {
            // Updated to go to HomeGuestActivity since it handles the Guest UI better
            val intent = Intent(this, HomeGuestActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
