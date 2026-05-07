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
    private lateinit var tvCampaignEmoji: TextView
    private lateinit var btnComplete: MaterialButton
    private var isSavingDonation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_donation)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance("https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app")
        firestore = FirebaseFirestore.getInstance()

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnComplete = findViewById(R.id.btnComplete)
        tvCampaignEmoji = findViewById(R.id.tvCampaignEmoji)
        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val tvReviewCampaign = findViewById<TextView>(R.id.tvReviewCampaign)
        val tvReviewType = findViewById<TextView>(R.id.tvReviewType)
        val tvReviewPayment = findViewById<TextView>(R.id.tvReviewPayment)
        val tvReviewAmount = findViewById<TextView>(R.id.tvReviewAmount)

        val title = intent.getStringExtra("title")
        val amount = intent.getStringExtra("amount")
        val paymentMethod = intent.getStringExtra("paymentMethod")
        val donateType = intent.getStringExtra("donateType")

        if (GuestDonationGuard.blockIfGuest(
                context = this,
                campaignId = intent.getStringExtra("campaignId"),
                campaignTitle = title,
                finishAfterNavigation = { finish() },
                finishOnCancel = { finish() }
            )
        ) {
            return
        }

        tvHeaderTitle.text = title ?: "Campaign Name"
        tvCampaignEmoji.text = CampaignDisplayHelper.campaignEmoji(
            emoji = intent.getStringExtra("campaignEmoji"),
            category = null,
            title = title
        )
        tvReviewCampaign.text = title ?: "Campaign Name"
        tvReviewType.text = donateType ?: "One-Time"
        tvReviewPayment.text = paymentMethod ?: "GCash"
        tvReviewAmount.text = "\u20B1${amount ?: "0.00"}"

        btnBack.setOnClickListener { finish() }
        loadCampaignEmoji(intent.getStringExtra("campaignId").orEmpty(), title)

        btnComplete.setOnClickListener {
            if (isSavingDonation) return@setOnClickListener
            validateCampaignAndSaveDonation(title, amount, paymentMethod, donateType)
        }
    }

    private fun loadCampaignEmoji(campaignId: String, campaignTitle: String?) {
        if (campaignId.isBlank()) return
        db.getReference("campaigns").child(campaignId).get()
            .addOnSuccessListener { snapshot ->
                val campaign = CampaignDisplayHelper.parseCampaign(snapshot)
                tvCampaignEmoji.text = if (campaign != null) {
                    CampaignDisplayHelper.campaignEmoji(campaign)
                } else {
                    CampaignDisplayHelper.campaignEmoji(null, null, campaignTitle)
                }
            }
            .addOnFailureListener { error ->
                Log.e("ReviewDonation", "Unable to load campaign emoji", error)
                tvCampaignEmoji.text = CampaignDisplayHelper.campaignEmoji(null, null, campaignTitle)
            }
    }

    private fun validateCampaignAndSaveDonation(
        campaignTitle: String?,
        amount: String?,
        paymentMethod: String?,
        donateType: String?
    ) {
        val campaignId = intent.getStringExtra("campaignId").orEmpty()
        if (GuestDonationGuard.blockIfGuest(
                context = this,
                campaignId = campaignId,
                campaignTitle = campaignTitle,
                finishAfterNavigation = { finish() }
            )
        ) {
            return
        }

        if (campaignId.isBlank()) {
            Toast.makeText(this, "This campaign is unavailable right now.", Toast.LENGTH_SHORT).show()
            return
        }

        db.getReference("campaigns").child(campaignId).get()
            .addOnSuccessListener { snapshot ->
                val campaign = CampaignDisplayHelper.parseCampaign(snapshot)
                if (campaign == null || !CampaignDisplayHelper.canDonate(campaign)) {
                    Log.w(
                        "ReviewDonation",
                        "Blocked donation for unavailable campaign. campaignId=$campaignId status=${campaign?.status} date=${campaign?.date}"
                    )
                    Toast.makeText(
                        this,
                        "This campaign has expired and is no longer accepting donations.",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@addOnSuccessListener
                }

                saveDonationToFirebase(campaignTitle, amount, paymentMethod, donateType)
            }
            .addOnFailureListener { error ->
                Log.e("ReviewDonation", "Unable to validate campaign before donation", error)
                Toast.makeText(this, "Unable to validate this campaign right now.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDonationToFirebase(
        campaignTitle: String?,
        amount: String?,
        paymentMethod: String?,
        donateType: String?
    ) {
        val user = auth.currentUser
        if (user == null || user.isAnonymous || GuestDonationGuard.isGuestUser(this)) {
            GuestDonationGuard.showLoginRequiredDialog(
                context = this,
                campaignId = intent.getStringExtra("campaignId"),
                campaignTitle = campaignTitle,
                finishAfterNavigation = { finish() }
            )
            return
        }

        val campaignId = intent.getStringExtra("campaignId") ?: ""
        val donationAmount = parseDonationAmount(amount)
        if (donationAmount <= 0L) {
            Toast.makeText(this, "Please enter a valid donation amount.", Toast.LENGTH_SHORT).show()
            return
        }

        setDonationSaving(true)
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
                    "campaignCategory" to intent.getStringExtra("campaignCategory").orEmpty(),
                    "dateString" to dateString,
                    "donationId" to donationId,
                    "donationType" to (donateType ?: "One-Time"),
                    "donorId" to user.uid,
                    "donorEmail" to user.email,
                    "donorName" to fullName,
                    "paymentMethod" to (paymentMethod ?: "N/A"),
                    "referenceId" to referenceId,
                    "status" to "successful",
                    "statsApplied" to false,
                    "campaignProgressApplied" to false,
                    "timestamp" to timestamp,
                    "userId" to user.uid
                )

                Log.d("DonationFlow", "Saving donation donationId=$donationId campaignId=$campaignId amount=$donationAmount")
                db.getReference("donations").child(donationId).setValue(donationData)
                    .addOnSuccessListener {
                        Log.d("DonationFlow", "Donation saved successfully")
                        UserDonationStatsRepository.applySuccessfulDonation(
                            donationId = donationId,
                            userId = user.uid,
                            amount = donationAmount,
                            campaignId = campaignId,
                            campaignTitle = campaignTitle ?: "Unknown Campaign",
                            campaignCategory = intent.getStringExtra("campaignCategory").orEmpty(),
                            firestore = firestore,
                            realtimeDatabase = db,
                            donationPayload = donationData
                        ) { result ->
                            result
                                .onSuccess {
                                    setDonationSaving(false)
                                    Log.d("ReviewDonation", "Donation effects applied donationId=$donationId")
                                    AppNotificationManager.notifyDonation(
                                        this@ReviewDonationActivity,
                                        campaignTitle.orEmpty(),
                                        donationAmount.toInt()
                                    )
                                    createDonationNotifications(
                                        donorId = user.uid,
                                        donorName = fullName,
                                        campaignId = campaignId,
                                        campaignTitle = campaignTitle ?: "Unknown Campaign",
                                        donationId = donationId,
                                        amount = donationAmount.toInt()
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
                                .onFailure { error ->
                                    setDonationSaving(false)
                                    Log.e("CampaignProgress", "Progress update failed", error)
                                    Log.e("UserDonationStats", "User stats update failed", error)
                                    Log.e("ReviewDonation", "Unable to apply donation effects", error)
                                    Toast.makeText(
                                        this@ReviewDonationActivity,
                                        "Donation was saved, but progress could not update. Please try refreshing.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        setDonationSaving(false)
                        Log.e("ReviewDonation", "Error saving donation: ${e.message}", e)
                        Toast.makeText(this, "Error saving donation: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                setDonationSaving(false)
                Log.e("ReviewDonation", "Error fetching user data: ${e.message}", e)
                Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setDonationSaving(saving: Boolean) {
        isSavingDonation = saving
        if (::btnComplete.isInitialized) {
            btnComplete.isEnabled = !saving
            btnComplete.text = if (saving) "Saving..." else "Confirm Donation"
        }
    }

    private fun parseDonationAmount(value: String?): Long {
        return value.orEmpty()
            .replace("\u20B1", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull()
            ?.toLong()
            ?: 0L
    }

    private fun createDonationNotifications(
        donorId: String,
        donorName: String,
        campaignId: String,
        campaignTitle: String,
        donationId: String,
        amount: Int
    ) {
        FirestoreNotificationRepository.createNotification(
            firestore = firestore,
            recipientId = donorId,
            senderId = donorId,
            senderName = donorName,
            senderRole = "user",
            type = FirestoreNotificationRepository.TYPE_DONATION_SUCCESS,
            title = "Donation successful",
            message = "Your donation was successful.",
            campaignId = campaignId,
            campaignTitle = campaignTitle,
            donationId = donationId,
            amount = amount.toDouble(),
            allowSelfNotification = true
        )

        FirestoreNotificationRepository.notifyAdminTeam(
            firestore = firestore,
            senderId = donorId,
            senderName = donorName,
            senderRole = "user",
            type = FirestoreNotificationRepository.TYPE_NEW_DONATION_ADMIN,
            title = "New donation",
            message = "$donorName donated \u20B1${String.format("%,d", amount)} to $campaignTitle.",
            campaignId = campaignId,
            campaignTitle = campaignTitle,
            donationId = donationId,
            amount = amount.toDouble()
        )
    }
}
