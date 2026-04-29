package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardActivity : BaseActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var tvTrendingTag: TextView
    private lateinit var tvCampTitle: TextView
    private lateinit var tvRaised: TextView
    private lateinit var tvTrendingDetails: TextView
    private lateinit var pbCampProgress: ProgressBar
    private lateinit var tvActiveCampaignEmoji: TextView
    private lateinit var tvActiveCampTitle: TextView
    private lateinit var tvActiveCampSub: TextView
    private lateinit var pbMiniProgress: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvRaisedAmountSmall: TextView
    private lateinit var cardTrendingCampaign: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Using the correct region-specific URL
        val databaseUrl = "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
        dbRef = FirebaseDatabase.getInstance(databaseUrl).getReference("campaigns")

        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnDonateNow = findViewById<Button>(R.id.btnDonateNow)
        
        // Quick Donate Buttons
        val btnDonate100 = findViewById<Button>(R.id.btnDonate100)
        val btnDonate300 = findViewById<Button>(R.id.btnDonate300)
        val btnDonate500 = findViewById<Button>(R.id.btnDonate500)

        // Campaign Views
        tvTrendingTag = findViewById(R.id.tvTrendingTag)
        tvCampTitle = findViewById(R.id.tvCampTitle)
        tvRaised = findViewById(R.id.tvRaised)
        tvTrendingDetails = findViewById(R.id.tvTrendingDetails)
        pbCampProgress = findViewById(R.id.pbCampProgress)
        cardTrendingCampaign = findViewById(R.id.cardTrendingCampaign)
        tvActiveCampaignEmoji = findViewById(R.id.tvActiveCampaignEmoji)
        tvActiveCampTitle = findViewById(R.id.tvActiveCampTitle)
        tvActiveCampSub = findViewById(R.id.tvActiveCampSub)
        pbMiniProgress = findViewById(R.id.pbMiniProgress)
        
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvRaisedAmountSmall = findViewById(R.id.tvRaisedAmount)

        btnSignIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnDonateNow.setOnClickListener {
            showGuestBottomSheet()
        }

        val donateListener = View.OnClickListener {
            showGuestBottomSheet()
        }

        btnDonate100.setOnClickListener(donateListener)
        btnDonate300.setOnClickListener(donateListener)
        btnDonate500.setOnClickListener(donateListener)

        fetchCampaignsRealtime()
    }

    private fun fetchCampaignsRealtime() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("DashboardActivity", "Data changed: ${snapshot.childrenCount}")
                val campaigns = mutableListOf<Campaign>()
                for (campaignSnapshot in snapshot.children) {
                    val campaign = campaignSnapshot.getValue(Campaign::class.java)
                    if (campaign != null) {
                        campaigns.add(campaign)
                    }
                }
                
                campaigns.sortByDescending { it.createdAt }

                val trendingCampaign = selectTrendingCampaign(campaigns)
                if (trendingCampaign != null) {
                    val progress = calculateProgress(trendingCampaign)
                    val daysLeft = calculateDaysLeft(trendingCampaign)
                    val tag = if (daysLeft != null && daysLeft <= 3) "🚨 EMERGENCY" else " EMERGENCY"
                    val details = if (daysLeft != null) {
                        "$progress% • $daysLeft day${if (daysLeft == 1) "" else "s"} left"
                    } else {
                        "$progress% • No deadline"
                    }

                    tvTrendingTag.text = tag
                    tvCampTitle.text = trendingCampaign.title ?: "Untitled campaign"
                    tvRaised.text = "₱${trendingCampaign.raised ?: 0} raised"
                    pbCampProgress.progress = progress
                    tvTrendingDetails.text = details

                    cardTrendingCampaign.setOnClickListener {
                        showGuestBottomSheet()
                    }
                } else if (campaigns.isNotEmpty()) {
                    val featured = campaigns[0]
                    val progress = calculateProgress(featured)
                    tvTrendingTag.text = " EMERGENCY"
                    tvCampTitle.text = featured.title
                    tvRaised.text = "₱${featured.raised ?: 0} raised"
                    pbCampProgress.progress = progress
                    tvTrendingDetails.text = "$progress% • No deadline"
                    cardTrendingCampaign.setOnClickListener {
                        showGuestBottomSheet()
                    }                }

                val activeCampaign = campaigns.filter { it.campaignId != trendingCampaign?.campaignId }.maxByOrNull { it.createdAt ?: 0L }
                if (activeCampaign != null) {
                    val activeGoal = activeCampaign.goal ?: 0
                    val activeRaised = activeCampaign.raised ?: 0
                    val progress = if (activeGoal > 0) (activeRaised * 100 / activeGoal) else 0
                    tvActiveCampaignEmoji.text = CampaignDisplayHelper.campaignEmoji(activeCampaign)
                    tvActiveCampTitle.text = activeCampaign.title
                    tvActiveCampSub.text = activeCampaign.description
                    pbMiniProgress.progress = progress
                    tvProgressPercent?.text = "$progress%"
                    tvRaisedAmountSmall?.text = "₱$activeRaised raised"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DashboardActivity", "Database error: ${error.message}")
            }
        })
    }

    private fun showGuestBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.activity_guest, null)
        bottomSheetDialog.setContentView(view)

        val btnCreateAccount = view.findViewById<Button>(R.id.btnCreateAccount)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val btnContinueGuest = view.findViewById<TextView>(R.id.btnContinueGuest)

        btnCreateAccount.setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnContinueGuest.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun parseCampaignDate(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateString)
        } catch (e: ParseException) {
            null
        }
    }

    private fun calculateDaysLeft(campaign: Campaign): Int? {
        val deadline = parseCampaignDate(campaign.date) ?: return null
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val diffMillis = deadline.time - today.time
        return Math.ceil(diffMillis.toDouble() / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    private fun calculateProgress(campaign: Campaign): Int {
        val goal = campaign.goal ?: 0
        val raised = campaign.raised ?: 0
        return if (goal > 0) {
            ((raised.toDouble() / goal.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    private fun selectTrendingCampaign(campaigns: List<Campaign>): Campaign? {
        val visibleCampaigns = campaigns.filter { it.hidden != true }
        return visibleCampaigns
            .filter { calculateDaysLeft(it)?.let { days -> days <= 3 } ?: false }
            .sortedWith(compareBy({ calculateDaysLeft(it) ?: Int.MAX_VALUE }, { -calculateProgress(it) }))
            .firstOrNull() ?: visibleCampaigns.maxWithOrNull(compareBy({ calculateProgress(it) }, { it.createdAt ?: 0L }))
    }
}
