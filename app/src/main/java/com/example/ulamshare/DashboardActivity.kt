package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.*

class DashboardActivity : BaseActivity() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var tvCampTitle: TextView
    private lateinit var tvRaised: TextView
    private lateinit var pbCampProgress: ProgressBar
    private lateinit var tvActiveCampTitle: TextView
    private lateinit var tvActiveCampSub: TextView
    private lateinit var pbMiniProgress: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvRaisedAmountSmall: TextView

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
        tvCampTitle = findViewById(R.id.tvCampTitle)
        tvRaised = findViewById(R.id.tvRaised)
        pbCampProgress = findViewById(R.id.pbCampProgress)
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

                if (campaigns.isNotEmpty()) {
                    val featured = campaigns[0]
                    tvCampTitle.text = featured.title
                    tvRaised.text = "₱${featured.raised} raised"
                    val featuredGoal = featured.goal ?: 0
                    val featuredRaised = featured.raised ?: 0
                    val progress = if (featuredGoal > 0) (featuredRaised * 100 / featuredGoal) else 0
                    pbCampProgress.progress = progress
                }

                if (campaigns.size > 1) {
                    val active = campaigns[1]
                    tvActiveCampTitle.text = active.title
                    tvActiveCampSub.text = active.description
                    val activeGoal = active.goal ?: 0
                    val activeRaised = active.raised ?: 0
                    val progress = if (activeGoal > 0) (activeRaised * 100 / activeGoal) else 0
                    pbMiniProgress.progress = progress
                    tvProgressPercent?.text = "$progress%"
                    tvRaisedAmountSmall?.text = "₱${active.raised} raised"
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
}
