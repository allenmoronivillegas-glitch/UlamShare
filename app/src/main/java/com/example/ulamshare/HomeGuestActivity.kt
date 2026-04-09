package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseReference
import kotlin.math.roundToInt

class HomeGuestActivity : BaseActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: CampaignAdapter
    private val campaignList = mutableListOf<Campaign>()
    private lateinit var rvTrending: RecyclerView
    private lateinit var emptyCampaignsCard: ConstraintLayout

    private lateinit var tvActiveCampaignTitle: TextView
    private lateinit var tvActiveCampaignDescription: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCampaignRaised: TextView
    private lateinit var tvCampaignProgressPercent: TextView

    private val campaignsRef = FirebaseDatabase.getInstance().getReference("campaigns")
    private var campaignsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_guest)

        val databaseUrl = "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
        dbRef = FirebaseDatabase.getInstance(databaseUrl).getReference("campaigns")

        val btnSignInTop = findViewById<Button>(R.id.btnSignInTop)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)
        val btnCreateAccountTop = findViewById<Button>(R.id.btnCreateAccountTop)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        rvTrending = findViewById(R.id.rvTrendingCampaigns)
        emptyCampaignsCard = findViewById(R.id.emptyCampaignsCard)

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

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_campaigns -> {
                    // Navigate to guest campaigns instead of MainActivity/Login
                    startActivity(Intent(this, CampaignsGuestActivity::class.java))
                    false
                }
                R.id.nav_history -> {
                    Toast.makeText(this, "Sign in to view your donation history", Toast.LENGTH_SHORT).show()
                    false
                }
                R.id.nav_profile -> {
                    Toast.makeText(this, "Sign in to access your profile", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }

        fetchTrendingCampaignsRealtime()
        listenToCampaigns()
    }

    private fun fetchTrendingCampaignsRealtime() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                campaignList.clear()
                for (campaignSnapshot in snapshot.children) {
                    try {
                        val campaign = campaignSnapshot.getValue(Campaign::class.java)
                        if (campaign != null) {
                            campaignList.add(campaign)
                        }
                    } catch (e: Exception) {
                        Log.e("HomeGuestActivity", "Error parsing campaign", e)
                    }
                }
                campaignList.sortByDescending { it.createdAt }
                if (campaignList.isEmpty()) {
                    rvTrending.visibility = View.GONE
                } else {
                    rvTrending.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                rvTrending.visibility = View.GONE
            }
        })
    }

    private fun listenToCampaigns() {
        campaignsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val campaign = snapshot.children
                    .mapNotNull { child ->
                        val isPublished = child.child("isPublished").getValue(Boolean::class.java) ?: false
                        if (!isPublished) return@mapNotNull null

                        CampaignPreview(
                            title = child.child("title").getValue(String::class.java) ?: "",
                            description = child.child("description").getValue(String::class.java).orEmpty(),
                            goalAmount = child.child("goalAmount").getValue(Double::class.java) ?: 0.0,
                            raisedAmount = child.child("raisedAmount").getValue(Double::class.java) ?: 0.0,
                            isFeatured = child.child("isFeatured").getValue(Boolean::class.java) ?: false
                        )
                    }
                    .sortedWith(compareByDescending<CampaignPreview> { it.isFeatured }
                        .thenByDescending { it.raisedAmount })
                    .firstOrNull()

                if (campaign == null) renderEmptyState() else renderCampaign(campaign)
            }

            override fun onCancelled(error: DatabaseError) {
                renderEmptyState()
            }
        }
        campaignsRef.addValueEventListener(campaignsListener!!)
    }

    private fun renderCampaign(campaign: CampaignPreview) {
        emptyCampaignsCard.visibility = View.GONE
        tvActiveCampaignTitle.text = campaign.title
        tvActiveCampaignDescription.text = campaign.description.ifBlank { "No campaign description yet." }
        val progressPercent = if (campaign.goalAmount > 0) {
            ((campaign.raisedAmount / campaign.goalAmount) * 100).coerceIn(0.0, 100.0)
        } else 0.0
        progressBar.progress = progressPercent.roundToInt()
        tvCampaignRaised.text = "₱${campaign.raisedAmount.roundToInt()} raised"
        tvCampaignProgressPercent.text = "${progressPercent.roundToInt()}%"
    }

    private fun renderEmptyState() {
        emptyCampaignsCard.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_home
    }

    override fun onDestroy() {
        campaignsListener?.let { campaignsRef.removeEventListener(it) }
        super.onDestroy()
    }
}

data class CampaignPreview(
    val title: String,
    val description: String,
    val goalAmount: Double,
    val raisedAmount: Double,
    val isFeatured: Boolean
)
