package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ActivitySelectAmount : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_amount)

        // PART 2 — RECEIVE DATA
        val campaignId = intent.getStringExtra("campaignId")
        val title = intent.getStringExtra("title")
        val goal = intent.getIntExtra("goal", 0)

        // Log to confirm
        Log.d("SelectAmount", "Received Campaign: $title (ID: $campaignId, Goal: $goal)")

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val etAmount = findViewById<EditText>(R.id.etAmount)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnNext = findViewById<Button>(R.id.btnNext)

        // 🔥 Dynamic title
        tvTitle.text = "Donate to ${title ?: "Campaign"}"

        btnBack.setOnClickListener {
            finish()
        }

        btnNext.setOnClickListener {
            val intent = Intent(this, PaymentMethodActivity::class.java).apply {
                putExtra("campaignId", campaignId)
                putExtra("title", title)
                putExtra("amount", etAmount.text.toString())
            }
            startActivity(intent)
        }

        // Amount buttons logic for single selection
        val btn50 = findViewById<MaterialButton>(R.id.btn50)
        val btn100 = findViewById<MaterialButton>(R.id.btn100)
        val btn200 = findViewById<MaterialButton>(R.id.btn200)
        val btn500 = findViewById<MaterialButton>(R.id.btn500)
        val btn1000 = findViewById<MaterialButton>(R.id.btn1000)
        val btn2000 = findViewById<MaterialButton>(R.id.btn2000)

        val amountButtons = listOf(btn50, btn100, btn200, btn500, btn1000, btn2000)

        amountButtons.forEach { button ->
            button.setOnClickListener {
                // Deselect all others
                amountButtons.forEach { it.isChecked = false }
                // Select this one
                button.isChecked = true
                
                // Update the custom amount field
                val amountText = button.text.toString().replace("₱", "").replace(",", "")
                etAmount.setText(amountText)
            }
        }

        // Clear button selection if user types in the EditText
        etAmount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                amountButtons.forEach { it.isChecked = false }
            }
        }
    }
}
