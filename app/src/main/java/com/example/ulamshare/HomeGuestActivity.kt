package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*

class HomeGuestActivity : BaseActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: CampaignAdapter
    private val campaignList = mutableListOf<Campaign>()
    private lateinit var rvTrending: RecyclerView
    private lateinit var emptyCampaignsCard: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_guest)

        // Using the correct region-specific URL
        val databaseUrl = "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
        dbRef = FirebaseDatabase.getInstance(databaseUrl).getReference("campaigns")

        val btnSignInTop = findViewById<View>(R.id.btnSignInTop)
        val btnCreateAccount = findViewById<View>(R.id.btnCreateAccount)
        val btnCreateAccountTop = findViewById<View>(R.id.btnCreateAccountTop)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        rvTrending = findViewById(R.id.rvTrendingCampaigns)
        emptyCampaignsCard = findViewById(R.id.emptyCampaignsCard)

        rvTrending.layoutManager = LinearLayoutManager(this)
        adapter = CampaignAdapter(campaignList)
        rvTrending.adapter = adapter

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
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("TARGET_FRAGMENT", "CAMPAIGNS")
                    startActivity(intent)
                    true
                }
                R.id.nav_history, R.id.nav_profile -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    false
                }
                else -> false
            }
        }

        fetchTrendingCampaignsRealtime()
    }

    private fun fetchTrendingCampaignsRealtime() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("HomeGuestActivity", "Realtime data changed: ${snapshot.childrenCount} items")
                campaignList.clear()
                for (campaignSnapshot in snapshot.children) {
                    try {
                        val campaign = campaignSnapshot.getValue(Campaign::class.java)
                        if (campaign != null) {
                            campaignList.add(campaign)
                        }
                    } catch (e: Exception) {
                        Log.e("HomeGuestActivity", "Error parsing campaign: ${campaignSnapshot.key}", e)
                    }
                }

                campaignList.sortByDescending { it.createdAt }

                if (campaignList.isEmpty()) {
                    rvTrending.visibility = View.GONE
                    emptyCampaignsCard.visibility = View.VISIBLE
                } else {
                    rvTrending.visibility = View.VISIBLE
                    emptyCampaignsCard.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeGuestActivity", "Database error: ${error.message}")
                rvTrending.visibility = View.GONE
                emptyCampaignsCard.visibility = View.VISIBLE
            }
        })
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation.selectedItemId = R.id.nav_home
    }
}
