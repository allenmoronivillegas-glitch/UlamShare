package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeGuestActivity : BaseActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: CampaignAdapter
    private val campaignList = mutableListOf<Campaign>()
    private lateinit var rvTrending: RecyclerView
    private lateinit var emptyCampaignsCard: ConstraintLayout
    private lateinit var activeCampaignCard: CardView
    private lateinit var tvEmptyCampaignsMessage: TextView
    private lateinit var btnSeeAllCampaigns: TextView
    private lateinit var tvActiveCampaignEmoji: TextView

    private lateinit var tvActiveCampaignTitle: TextView
    private lateinit var tvActiveCampaignDescription: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCampaignRaised: TextView
    private lateinit var tvCampaignProgressPercent: TextView
    private var campaignsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_guest)

        dbRef = FirebaseDatabase.getInstance().getReference("campaigns")

        val btnSignInTop = findViewById<Button>(R.id.btnSignInTop)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)
        val btnCreateAccountTop = findViewById<Button>(R.id.btnCreateAccountTop)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        rvTrending = findViewById(R.id.rvTrendingCampaigns)
        emptyCampaignsCard = findViewById(R.id.emptyCampaignsCard)
        activeCampaignCard = findViewById(R.id.activeCampaignCard)
        tvEmptyCampaignsMessage = findViewById(R.id.tvEmptyCampaignsMessage)
        btnSeeAllCampaigns = findViewById(R.id.btnSeeAllCampaigns)
        tvActiveCampaignEmoji = findViewById(R.id.tvActiveCampaignEmoji)

        rvTrending.layoutManager = LinearLayoutManager(this)
        adapter = CampaignAdapter(campaignList)
        rvTrending.adapter = adapter

        tvActiveCampaignTitle = findViewById(R.id.tvActiveCampTitle)
        tvActiveCampaignDescription = findViewById(R.id.tvActiveCampContent)
        progressBar = findViewById(R.id.progressBarCampaign)
        tvCampaignRaised = findViewById(R.id.tvCampaignRaised)
        tvCampaignProgressPercent = findViewById(R.id.tvCampaignProgressPercent)

        btnSignInTop.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnCreateAccountTop.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnSeeAllCampaigns.setOnClickListener {
            startActivity(Intent(this, CampaignCatalogActivity::class.java))
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_campaigns -> {
                    startActivity(Intent(this, CampaignsGuestActivity::class.java))
                    false
                }
                R.id.nav_profile -> {
                    Toast.makeText(this, "Sign in to access your profile", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }

        listenToCampaignsRealtime()
    }

    private fun listenToCampaignsRealtime() {
        campaignsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("HomeGuestActivity", "Realtime campaign update received")
                val buckets = CampaignDisplayHelper.groupCampaigns(snapshot, "HomeGuestActivity")
                val recentCampaigns = CampaignDisplayHelper.recentPreview(buckets.activeCampaigns, buckets.activeCampaigns.size)

                campaignList.clear()
                campaignList.addAll(recentCampaigns)

                if (campaignList.isEmpty()) {
                    rvTrending.visibility = View.GONE
                    renderEmptyState(
                        hasFilteredCampaigns = buckets.filteredCount > 0 || buckets.expiredCampaigns.isNotEmpty(),
                        message = if (buckets.expiredCampaigns.isNotEmpty()) {
                            "Active campaigns are not available right now. Browse expired campaigns from See All."
                        } else {
                            null
                        }
                    )
                } else {
                    rvTrending.visibility = View.VISIBLE
                    emptyCampaignsCard.visibility = View.GONE
                    adapter.submitList(campaignList.toList())
                    renderCampaign(CampaignDisplayHelper.recentCampaigns(buckets.activeCampaigns).first())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeGuestActivity", "Campaign listener cancelled", error.toException())
                rvTrending.visibility = View.GONE
                renderEmptyState(false, "Unable to load campaigns right now.")
            }
        }
        dbRef.addValueEventListener(campaignsListener!!)
    }

    private fun renderCampaign(campaign: Campaign) {
        activeCampaignCard.visibility = View.VISIBLE
        emptyCampaignsCard.visibility = View.GONE
        tvActiveCampaignEmoji.text = CampaignDisplayHelper.campaignEmoji(campaign)
        tvActiveCampaignTitle.text = campaign.title ?: "Untitled Campaign"
        tvActiveCampaignDescription.text =
            campaign.description?.ifBlank { "No campaign description yet." } ?: "No campaign description yet."
        val progressPercent = CampaignDisplayHelper.progressPercent(campaign)
        progressBar.progress = progressPercent
        tvCampaignRaised.text = "${CampaignDisplayHelper.formatPeso(CampaignDisplayHelper.campaignRaised(campaign))} raised"
        tvCampaignProgressPercent.text = "$progressPercent%"
        activeCampaignCard.setOnClickListener {
            if (GuestDonationGuard.blockIfGuest(
                    context = this,
                    campaignId = campaign.campaignId,
                    campaignTitle = campaign.title
                )
            ) {
                return@setOnClickListener
            }

            val intent = Intent(this, ActivitySelectAmount::class.java).apply {
                putExtra("campaignId", campaign.campaignId)
                putExtra("title", campaign.title)
                putExtra("goal", CampaignDisplayHelper.campaignGoal(campaign))
            }
            startActivity(intent)
        }
    }

    private fun renderEmptyState(hasFilteredCampaigns: Boolean, message: String? = null) {
        activeCampaignCard.visibility = View.GONE
        emptyCampaignsCard.visibility = View.VISIBLE
        tvEmptyCampaignsMessage.text = message ?: if (hasFilteredCampaigns) {
            "Campaigns exist, but none are active right now."
        } else {
            "No active campaigns available yet."
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_home
    }

    override fun onDestroy() {
        campaignsListener?.let { dbRef.removeEventListener(it) }
        super.onDestroy()
    }
}
