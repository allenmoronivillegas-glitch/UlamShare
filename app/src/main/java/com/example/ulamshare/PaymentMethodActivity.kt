package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

class PaymentMethodActivity : AppCompatActivity() {

    private var selectedOptionId: Int = R.id.optionGCash

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_method)

        val campaignId = intent.getStringExtra("campaignId")
        val title = intent.getStringExtra("title")
        val amount = intent.getStringExtra("amount")

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnConfirm = findViewById<Button>(R.id.btnConfirmPayment)

        val optionGCash = findViewById<ConstraintLayout>(R.id.optionGCash)
        val optionMaya = findViewById<ConstraintLayout>(R.id.optionMaya)
        val optionCard = findViewById<ConstraintLayout>(R.id.optionCard)

        val rbGCash = findViewById<RadioButton>(R.id.rbGCash)
        val rbMaya = findViewById<RadioButton>(R.id.rbMaya)
        val rbCard = findViewById<RadioButton>(R.id.rbCard)

        btnBack.setOnClickListener {
            finish()
        }

        fun updateSelection(selectedId: Int) {
            selectedOptionId = selectedId
            
            // Update backgrounds and radio buttons
            optionGCash.setBackgroundResource(if (selectedId == R.id.optionGCash) R.drawable.bg_card_selected else R.drawable.rounded_input_border)
            rbGCash.isChecked = (selectedId == R.id.optionGCash)

            optionMaya.setBackgroundResource(if (selectedId == R.id.optionMaya) R.drawable.bg_card_selected else R.drawable.rounded_input_border)
            rbMaya.isChecked = (selectedId == R.id.optionMaya)

            optionCard.setBackgroundResource(if (selectedId == R.id.optionCard) R.drawable.bg_card_selected else R.drawable.rounded_input_border)
            rbCard.isChecked = (selectedId == R.id.optionCard)
        }

        optionGCash.setOnClickListener { updateSelection(R.id.optionGCash) }
        optionMaya.setOnClickListener { updateSelection(R.id.optionMaya) }
        optionCard.setOnClickListener { 
            updateSelection(R.id.optionCard)
            // Directly navigate to Credit Card payment layout as requested
            val intent = Intent(this, PaymentActivity::class.java).apply {
                putExtra("campaignId", campaignId)
                putExtra("title", title)
                putExtra("amount", amount)
            }
            startActivity(intent)
        }

        btnConfirm.setOnClickListener {
            if (selectedOptionId == R.id.optionCard) {
                val intent = Intent(this, PaymentActivity::class.java).apply {
                    putExtra("campaignId", campaignId)
                    putExtra("title", title)
                    putExtra("amount", amount)
                }
                startActivity(intent)
            } else {
                val method = when (selectedOptionId) {
                    R.id.optionGCash -> "GCash"
                    R.id.optionMaya -> "Maya"
                    else -> "Credit / Debit Card"
                }
                
                val intent = Intent(this, ReviewDonationActivity::class.java).apply {
                    putExtra("campaignId", campaignId)
                    putExtra("title", title)
                    putExtra("amount", amount)
                    putExtra("paymentMethod", method)
                    putExtra("donateType", "One-Time") 
                }
                startActivity(intent)
            }
        }
    }
}
