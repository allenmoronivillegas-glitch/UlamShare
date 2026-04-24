package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ReviewDonationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_donation)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance("https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app")
        firestore = FirebaseFirestore.getInstance()

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnComplete = findViewById<MaterialButton>(R.id.btnComplete)
        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val tvReviewCampaign = findViewById<TextView>(R.id.tvReviewCampaign)
        val tvReviewType = findViewById<TextView>(R.id.tvReviewType)
        val tvReviewPayment = findViewById<TextView>(R.id.tvReviewPayment)
        val tvReviewAmount = findViewById<TextView>(R.id.tvReviewAmount)

        val title = intent.getStringExtra("title")
        val amount = intent.getStringExtra("amount")
        val paymentMethod = intent.getStringExtra("paymentMethod")
        val donateType = intent.getStringExtra("donateType")

        tvHeaderTitle.text = title ?: "Campaign Name"
        tvReviewCampaign.text = title ?: "Campaign Name"
        tvReviewType.text = donateType ?: "One-Time"
        tvReviewPayment.text = paymentMethod ?: "GCash"
        tvReviewAmount.text = "\u20B1${amount ?: "0.00"}"

        btnBack.setOnClickListener {
            finish()
        }

        btnComplete.setOnClickListener {
            saveDonationToFirebase(title, amount, paymentMethod, donateType)
        }
    }

    private fun saveDonationToFirebase(
        campaignTitle: String?,
        amount: String?,
        paymentMethod: String?,
        donateType: String?
    ) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to make a donation", Toast.LENGTH_SHORT).show()
            return
        }

        val campaignId = intent.getStringExtra("campaignId") ?: ""
        val donationAmount = amount?.toIntOrNull() ?: 0

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val fullName = document.getString("fullName") ?: user.displayName ?: "Anonymous"

                val donationId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()
                val dateString = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.getDefault())
                    .format(Date())
                val referenceId = System.currentTimeMillis().toString().takeLast(8)

                val donationData = mapOf(
                    "amount" to donationAmount,
                    "campaignId" to campaignId,
                    "campaignTitle" to (campaignTitle ?: "Unknown Campaign"),
                    "dateString" to dateString,
                    "donationId" to donationId,
                    "donationType" to (donateType ?: "One-Time"),
                    "donorEmail" to user.email,
                    "donorName" to fullName,
                    "paymentMethod" to (paymentMethod ?: "N/A"),
                    "referenceId" to referenceId,
                    "timestamp" to timestamp,
                    "userId" to user.uid
                )

                db.getReference("donations").child(donationId).setValue(donationData)
                    .addOnSuccessListener {
                        Log.d("ReviewDonation", "Donation saved: $donationId")

                        db.getReference("campaigns").child(campaignId).get()
                            .addOnSuccessListener { snapshot ->
                                val campaign = snapshot.value as? Map<*, *> ?: emptyMap<String, Any>()
                                val currentRaised = (campaign["raised"] as? Number)?.toLong()?.toInt() ?: 0
                                val newRaised = currentRaised + donationAmount

                                db.getReference("campaigns").child(campaignId).child("raised").setValue(newRaised)
                                    .addOnSuccessListener {
                                        Log.d("ReviewDonation", "Campaign raised amount updated to \u20B1$newRaised")

                                        AppNotificationManager.notifyDonation(
                                            this@ReviewDonationActivity,
                                            campaignTitle.orEmpty(),
                                            donationAmount
                                        )

                                        Toast.makeText(
                                            this@ReviewDonationActivity,
                                            "Donation confirmed!",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        val intent = Intent(
                                            this@ReviewDonationActivity,
                                            PaymentProcessingActivity::class.java
                                        ).apply {
                                            putExtra("amount", amount)
                                            putExtra("donationId", donationId)
                                            putExtra("referenceId", referenceId)
                                        }
                                        startActivity(intent)
                                    }
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ReviewDonation", "Error saving donation: ${e.message}", e)
                        Toast.makeText(this, "Error saving donation: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ReviewDonation", "Error fetching user data: ${e.message}", e)
                Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
