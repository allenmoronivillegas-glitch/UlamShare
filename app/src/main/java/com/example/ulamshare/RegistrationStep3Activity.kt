package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class RegistrationStep3Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration_step3)

        applyBottomSafeArea()
        bindAccountSummary()

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            goToHome()
        }
        findViewById<Button>(R.id.btnContinueHome).setOnClickListener {
            goToHome()
        }
    }

    private fun bindAccountSummary() {
        val fullName = intent.getStringExtra(EXTRA_FULL_NAME).orEmpty()
        val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        val mobile = intent.getStringExtra(EXTRA_MOBILE).orEmpty()

        findViewById<TextView>(R.id.tvSummaryName).text =
            fullName.ifBlank { getString(R.string.registration_step3_not_available) }
        findViewById<TextView>(R.id.tvSummaryEmail).text =
            email.ifBlank { getString(R.string.registration_step3_not_available) }
        findViewById<TextView>(R.id.tvSummaryMobile).text =
            mobile.ifBlank { getString(R.string.registration_step3_not_available) }
    }

    private fun applyBottomSafeArea() {
        val root = findViewById<View>(R.id.registrationStep3Root)
        val baseBottomPadding = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = baseBottomPadding + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun goToHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_FULL_NAME = "fullName"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_MOBILE = "mobile"
    }
}
