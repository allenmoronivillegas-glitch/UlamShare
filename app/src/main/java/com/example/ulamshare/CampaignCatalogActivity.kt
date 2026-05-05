package com.example.ulamshare

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CampaignCatalogActivity : AppCompatActivity() {

    private enum class CampaignTab {
        RECENT,
        ACTIVE,
        EXPIRED
    }

    private lateinit var campaignsRef: DatabaseReference
    private lateinit var adapter: CampaignAdapter
    private lateinit var rvCampaignCatalog: RecyclerView
    private lateinit var emptyCampaignsCard: View
    private lateinit var tvEmptyCampaignsMessage: TextView
    private lateinit var tvSectionSummary: TextView
    private lateinit var btnRecentTab: TextView
    private lateinit var btnActiveTab: TextView
    private lateinit var btnExpiredTab: TextView

    private var campaignBuckets = CampaignDisplayHelper.CampaignBuckets(
        activeCampaigns = emptyList(),
        expiredCampaigns = emptyList(),
        totalCampaigns = 0,
        hiddenCount = 0,
        nonActiveCount = 0,
        invalidCount = 0
    )
    private var selectedTab: CampaignTab = CampaignTab.RECENT
    private var highlightCampaignId: String = ""
    private var campaignsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_campaign_catalog)

        campaignsRef = FirebaseDatabase.getInstance().getReference("campaigns")

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        rvCampaignCatalog = findViewById(R.id.rvCampaignCatalog)
        emptyCampaignsCard = findViewById(R.id.emptyCampaignsCard)
        tvEmptyCampaignsMessage = findViewById(R.id.tvEmptyCampaignsMessage)
        tvSectionSummary = findViewById(R.id.tvSectionSummary)
        btnRecentTab = findViewById(R.id.btnRecentTab)
        btnActiveTab = findViewById(R.id.btnActiveTab)
        btnExpiredTab = findViewById(R.id.btnExpiredTab)

        adapter = CampaignAdapter(emptyList())
        rvCampaignCatalog.layoutManager = LinearLayoutManager(this)
        rvCampaignCatalog.adapter = adapter

        selectedTab = when (intent.getStringExtra(EXTRA_INITIAL_TAB)) {
            TAB_ACTIVE -> CampaignTab.ACTIVE
            TAB_EXPIRED -> CampaignTab.EXPIRED
            else -> CampaignTab.RECENT
        }
        highlightCampaignId = intent.getStringExtra(MainActivity.EXTRA_CAMPAIGN_ID).orEmpty()
        if (highlightCampaignId.isNotBlank()) {
            selectedTab = CampaignTab.ACTIVE
        }

        btnRecentTab.setOnClickListener {
            selectedTab = CampaignTab.RECENT
            renderCurrentTab()
        }
        btnActiveTab.setOnClickListener {
            selectedTab = CampaignTab.ACTIVE
            renderCurrentTab()
        }
        btnExpiredTab.setOnClickListener {
            selectedTab = CampaignTab.EXPIRED
            renderCurrentTab()
        }

        listenToCampaigns()
    }

    private fun listenToCampaigns() {
        campaignsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                campaignBuckets = CampaignDisplayHelper.groupCampaigns(snapshot, TAG)
                renderCurrentTab()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load campaigns", error.toException())
                rvCampaignCatalog.visibility = View.GONE
                emptyCampaignsCard.visibility = View.VISIBLE
                tvEmptyCampaignsMessage.text = "Unable to load campaigns right now."
                tvSectionSummary.text = "Campaigns are temporarily unavailable."
            }
        }
        campaignsRef.addValueEventListener(campaignsListener!!)
    }

    private fun renderCurrentTab() {
        val campaigns = when (selectedTab) {
            CampaignTab.RECENT -> CampaignDisplayHelper.recentCampaigns(campaignBuckets.activeCampaigns)
            CampaignTab.ACTIVE -> CampaignDisplayHelper.sortActiveCampaigns(campaignBuckets.activeCampaigns)
            CampaignTab.EXPIRED -> campaignBuckets.expiredCampaigns
        }

        adapter.submitList(campaigns)
        updateTabStyles()
        scrollToHighlightedCampaign(campaigns)

        val emptyMessage = when (selectedTab) {
            CampaignTab.RECENT -> "No recently added campaigns yet."
            CampaignTab.ACTIVE -> "No active campaigns available right now."
            CampaignTab.EXPIRED -> "No expired campaigns yet."
        }
        val summary = when (selectedTab) {
            CampaignTab.RECENT -> "Showing ${campaigns.size} recently added campaign${if (campaigns.size == 1) "" else "s"}."
            CampaignTab.ACTIVE -> "Showing ${campaigns.size} active campaign${if (campaigns.size == 1) "" else "s"}."
            CampaignTab.EXPIRED -> "Showing ${campaigns.size} expired campaign${if (campaigns.size == 1) "" else "s"}."
        }

        tvSectionSummary.text = summary
        if (campaigns.isEmpty()) {
            rvCampaignCatalog.visibility = View.GONE
            emptyCampaignsCard.visibility = View.VISIBLE
            tvEmptyCampaignsMessage.text = emptyMessage
        } else {
            rvCampaignCatalog.visibility = View.VISIBLE
            emptyCampaignsCard.visibility = View.GONE
        }
    }

    private fun scrollToHighlightedCampaign(campaigns: List<Campaign>) {
        val targetId = highlightCampaignId
        if (targetId.isBlank()) return
        val index = campaigns.indexOfFirst { it.campaignId == targetId }
        if (index >= 0) {
            rvCampaignCatalog.post {
                rvCampaignCatalog.scrollToPosition(index)
            }
        }
    }

    private fun updateTabStyles() {
        updateTabView(btnRecentTab, selectedTab == CampaignTab.RECENT)
        updateTabView(btnActiveTab, selectedTab == CampaignTab.ACTIVE)
        updateTabView(btnExpiredTab, selectedTab == CampaignTab.EXPIRED)
    }

    private fun updateTabView(tabView: TextView, selected: Boolean) {
        tabView.setBackgroundResource(
            if (selected) R.drawable.bg_support_chip_active else R.drawable.bg_support_chip
        )
        tabView.setTextColor(
            if (selected) 0xFFFFFFFF.toInt() else 0xFF2172E5.toInt()
        )
    }

    override fun onDestroy() {
        campaignsListener?.let { campaignsRef.removeEventListener(it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "initial_tab"
        const val TAB_RECENT = "recent"
        const val TAB_ACTIVE = "active"
        const val TAB_EXPIRED = "expired"
        private const val TAG = "CampaignCatalog"
    }
}
