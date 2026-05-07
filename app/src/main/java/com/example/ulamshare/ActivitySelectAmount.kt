package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ActivitySelectAmount : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etAmount: EditText
    private lateinit var btnNext: Button
    private lateinit var amountButtons: List<MaterialButton>
    private lateinit var campaignRef: DatabaseReference

    private var campaignId: String? = null
    private var campaignTitle: String? = null
    private var campaignGoal: Int = 0
    private var canDonateToCampaign: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_amount)

        campaignId = intent.getStringExtra("campaignId")
        campaignTitle = intent.getStringExtra("title")
        campaignGoal = intent.getIntExtra("goal", 0)

        Log.d("SelectAmount", "Received Campaign: $campaignTitle (ID: $campaignId, Goal: $campaignGoal)")

        campaignRef = FirebaseDatabase.getInstance().getReference("campaigns")

        tvTitle = findViewById(R.id.tvTitle)
        etAmount = findViewById(R.id.etAmount)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)

        tvTitle.text = "Donate to ${campaignTitle ?: "Campaign"}"

        btnBack.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            if (GuestDonationGuard.blockIfGuest(
                    context = this,
                    campaignId = campaignId,
                    campaignTitle = campaignTitle,
                    finishAfterNavigation = { finish() }
                )
            ) {
                return@setOnClickListener
            }

            refreshCampaignAvailability {
                if (!canDonateToCampaign) {
                    showExpiredCampaignMessage()
                    return@refreshCampaignAvailability
                }

                val enteredAmount = etAmount.text.toString().trim()
                if (enteredAmount.isEmpty() || enteredAmount.toIntOrNull() == null || enteredAmount.toInt() <= 0) {
                    Toast.makeText(this, "Enter a valid donation amount.", Toast.LENGTH_SHORT).show()
                    return@refreshCampaignAvailability
                }

                startActivity(Intent(this, PaymentMethodActivity::class.java).apply {
                    putExtra(PaymentMethodActivity.EXTRA_SOURCE, PaymentMethodActivity.SOURCE_DONATION)
                    putExtra("campaignId", campaignId)
                    putExtra("title", campaignTitle)
                    putExtra("amount", enteredAmount)
                })
            }
        }

        val btn50 = findViewById<MaterialButton>(R.id.btn50)
        val btn100 = findViewById<MaterialButton>(R.id.btn100)
        val btn200 = findViewById<MaterialButton>(R.id.btn200)
        val btn500 = findViewById<MaterialButton>(R.id.btn500)
        val btn1000 = findViewById<MaterialButton>(R.id.btn1000)
        val btn2000 = findViewById<MaterialButton>(R.id.btn2000)

        amountButtons = listOf(btn50, btn100, btn200, btn500, btn1000, btn2000)

        amountButtons.forEach { button ->
            button.setOnClickListener {
                amountButtons.forEach { it.isChecked = false }
                button.isChecked = true

                val amountText = button.text.toString()
                    .replace("\u20B1", "")
                    .replace(",", "")
                etAmount.setText(amountText)
            }
        }

        etAmount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                amountButtons.forEach { it.isChecked = false }
            }
        }

        if (GuestDonationGuard.blockIfGuest(
                context = this,
                campaignId = campaignId,
                campaignTitle = campaignTitle,
                finishAfterNavigation = { finish() },
                finishOnCancel = { finish() }
            )
        ) {
            setDonationEnabled(false)
            return
        }

        refreshCampaignAvailability()
    }

    private fun refreshCampaignAvailability(onReady: (() -> Unit)? = null) {
        val id = campaignId
        if (id.isNullOrBlank()) {
            Log.e("SelectAmount", "Missing campaignId in donate flow")
            canDonateToCampaign = false
            setDonationEnabled(false)
            Toast.makeText(this, "This campaign is unavailable right now.", Toast.LENGTH_SHORT).show()
            onReady?.invoke()
            return
        }

        campaignRef.child(id).get()
            .addOnSuccessListener { snapshot ->
                val campaign = CampaignDisplayHelper.parseCampaign(snapshot)
                canDonateToCampaign = campaign != null && CampaignDisplayHelper.canDonate(campaign)
                Log.d(
                    "SelectAmount",
                    "Campaign availability refreshed. campaignId=$id canDonate=$canDonateToCampaign status=${campaign?.status} date=${campaign?.date}"
                )
                setDonationEnabled(canDonateToCampaign)
                if (!canDonateToCampaign) {
                    showExpiredCampaignMessage()
                }
                onReady?.invoke()
            }
            .addOnFailureListener { error ->
                Log.e("SelectAmount", "Unable to verify campaign availability", error)
                canDonateToCampaign = false
                setDonationEnabled(false)
                Toast.makeText(this, "Unable to verify this campaign right now.", Toast.LENGTH_SHORT).show()
                onReady?.invoke()
            }
    }

    private fun setDonationEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
        btnNext.alpha = if (enabled) 1f else 0.5f
        etAmount.isEnabled = enabled
        amountButtons.forEach { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.5f
        }
    }

    private fun showExpiredCampaignMessage() {
        Toast.makeText(
            this,
            "This campaign has expired and is no longer accepting donations.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
