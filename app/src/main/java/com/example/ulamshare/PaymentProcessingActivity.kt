package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class PaymentProcessingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_processing)

        val amount = intent.getStringExtra("amount")

        // Simulate processing for 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, SuccessActivity::class.java)
            intent.putExtra("amount", amount)
            startActivity(intent)
            finish()
        }, 3000)
    }
}
