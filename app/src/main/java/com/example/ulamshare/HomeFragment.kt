package com.example.ulamshare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class HomeFragment : Fragment() {
    private var lastCampaignCount = 0

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbRef: DatabaseReference
    private lateinit var tvUserName: TextView
    private lateinit var tvProfileInitials: TextView
    private lateinit var btnRegisterHeader: Button
    private lateinit var btnSeeAllCampaigns: TextView
    private lateinit var tvTrendingEmoji: TextView
    private lateinit var tvTrendingTag: TextView
    private lateinit var tvCampTitle: TextView
    private lateinit var tvRaised: TextView
    private lateinit var tvTrendingDetails: TextView
    private lateinit var pbCampProgress: ProgressBar
    private lateinit var rvRecentlyAdded: RecyclerView
    private lateinit var emptyCampaignsCard: ConstraintLayout
    private lateinit var tvEmptyCampaignsMessage: TextView
    private lateinit var trendingContainer: ConstraintLayout
    private lateinit var adapter: CampaignAdapter
    private val campaignList = mutableListOf<Campaign>()
    private var campaignsListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.activity_home, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        dbRef = FirebaseDatabase.getInstance().getReference("campaigns")

        tvUserName = view.findViewById(R.id.tvUserName)
        tvProfileInitials = view.findViewById(R.id.tvProfileInitials)
        btnRegisterHeader = view.findViewById(R.id.btnRegisterHeader)
        btnSeeAllCampaigns = view.findViewById(R.id.btnSeeAllCampaigns)
        tvTrendingEmoji = view.findViewById(R.id.tvTrendingEmoji)
        tvTrendingTag = view.findViewById(R.id.tvTrendingTag)
        tvCampTitle = view.findViewById(R.id.tvCampTitle)
        tvRaised = view.findViewById(R.id.tvRaised)
        tvTrendingDetails = view.findViewById(R.id.tvTrendingDetails)
        pbCampProgress = view.findViewById(R.id.pbCampProgress)
        trendingContainer = view.findViewById(R.id.trendingContainer)
        rvRecentlyAdded = view.findViewById(R.id.rvRecentlyAdded)
        emptyCampaignsCard = view.findViewById(R.id.emptyCampaignsCard)
        tvEmptyCampaignsMessage = view.findViewById(R.id.tvEmptyCampaignsMessage)

        rvRecentlyAdded.layoutManager = LinearLayoutManager(requireContext())
        adapter = CampaignAdapter(campaignList)
        rvRecentlyAdded.adapter = adapter
        Log.d("HomeFragment", "RecyclerView initialized. firebasePath=campaigns")

        btnRegisterHeader.setOnClickListener {
            startActivity(Intent(requireContext(), RegisterActivity::class.java))
        }

        btnSeeAllCampaigns.setOnClickListener {
            startActivity(Intent(requireContext(), CampaignCatalogActivity::class.java))
        }

        loadUserData()
        fetchRecentCampaignsRealtime()

        return view
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            btnRegisterHeader.visibility = View.GONE
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val fullName = document.getString("fullName") ?: "User"
                        val firstName = fullName.split(" ").firstOrNull() ?: "User"
                        tvUserName.text = "$firstName \u2728"
                        val initials = fullName.split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase()
                        tvProfileInitials.text = initials
                    }
                }
        } else {
            tvUserName.text = "Guest \u2728"
            tvProfileInitials.text = "G"
            btnRegisterHeader.visibility = View.VISIBLE
        }
    }

    private fun fetchRecentCampaignsRealtime() {
        campaignsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("HomeFragment", "Realtime update received for campaigns")
                Log.d("HomeFragment", "snapshot.value=${snapshot.value}")

                val allCampaigns = snapshot.children.mapNotNull { child ->
                    CampaignDisplayHelper.parseCampaign(child)
                }
                val buckets = CampaignDisplayHelper.groupCampaigns(snapshot, "HomeFragment")
                val activeCampaigns = buckets.activeCampaigns
                val recentList = CampaignDisplayHelper.recentPreview(activeCampaigns)

                campaignList.clear()
                campaignList.addAll(activeCampaigns)

                val count = snapshot.childrenCount.toInt()
                val latestCampaign = allCampaigns.maxByOrNull { it.createdAt ?: 0L }

                if (lastCampaignCount != 0 && count > lastCampaignCount && latestCampaign != null) {
                    NotificationRepository.init(requireContext())
                    val newNotif = AppNotification(
                        id = UUID.randomUUID().toString(),
                        title = "New Campaign: ${latestCampaign.title}",
                        message = "A new campaign has been launched. Tap to view and support!",
                        timestamp = System.currentTimeMillis(),
                        type = "campaign"
                    )
                    NotificationRepository.saveNotification(newNotif)

                    Toast.makeText(requireContext(), "New campaign added!", Toast.LENGTH_SHORT).show()

                    val manager = requireContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = "campaign_channel"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Campaign Notifications",
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                        manager.createNotificationChannel(channel)
                    }

                    val intent = Intent(requireContext(), CampaignCatalogActivity::class.java).apply {
                        putExtra(CampaignCatalogActivity.EXTRA_INITIAL_TAB, CampaignCatalogActivity.TAB_RECENT)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        requireContext(),
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification = NotificationCompat.Builder(requireContext(), channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(latestCampaign.title)
                        .setContentText("Tap to browse campaigns")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(latestCampaign.title))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                    manager.notify(1, notification)
                }

                lastCampaignCount = count

                if (recentList.isEmpty()) {
                    rvRecentlyAdded.visibility = View.GONE
                    emptyCampaignsCard.visibility = View.VISIBLE
                    tvEmptyCampaignsMessage.text = if (buckets.expiredCampaigns.isNotEmpty()) {
                        "Active campaigns are not available right now. You can still browse expired campaigns."
                    } else {
                        "No campaigns available yet."
                    }
                } else {
                    rvRecentlyAdded.visibility = View.VISIBLE
                    emptyCampaignsCard.visibility = View.GONE
                    adapter.submitList(recentList)
                }

                updateTrendingCampaign(activeCampaigns)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Database error: ${error.message}")
                rvRecentlyAdded.visibility = View.GONE
                emptyCampaignsCard.visibility = View.VISIBLE
                tvEmptyCampaignsMessage.text = "Unable to load campaigns right now."
            }
        }
        dbRef.addValueEventListener(campaignsListener!!)
    }

    override fun onDestroyView() {
        campaignsListener?.let { dbRef.removeEventListener(it) }
        super.onDestroyView()
    }

    private fun calculateProgress(campaign: Campaign): Int {
        return CampaignDisplayHelper.progressPercent(campaign)
    }

    private fun selectTrendingCampaign(campaigns: List<Campaign>): Campaign? {
        val urgentCampaigns = campaigns.filter {
            val daysLeft = CampaignDisplayHelper.daysUntilDeadline(it)
            daysLeft != null && daysLeft <= 3
        }.sortedWith(compareBy({ CampaignDisplayHelper.daysUntilDeadline(it) ?: Int.MAX_VALUE }, { -calculateProgress(it) }))

        if (urgentCampaigns.isNotEmpty()) return urgentCampaigns.first()

        return campaigns.maxWithOrNull(compareBy({ calculateProgress(it) }, { it.createdAt ?: 0L }))
    }

    private fun updateTrendingCampaign(campaigns: List<Campaign>) {
        val trending = selectTrendingCampaign(campaigns)
        if (trending == null) {
            tvTrendingEmoji.text = "\uD83D\uDC99"
            tvTrendingTag.text = "\uD83D\uDD25 TRENDING"
            tvCampTitle.text = "No highlighted campaign"
            pbCampProgress.progress = 0
            tvTrendingDetails.text = "No urgent or featured campaigns available"
            tvRaised.text = ""
            trendingContainer.setOnClickListener(null)
            return
        }

        val progress = calculateProgress(trending)
        val daysLeft = CampaignDisplayHelper.daysUntilDeadline(trending)
        val tag = if (daysLeft != null && daysLeft <= 3) "\uD83D\uDEA8 EMERGENCY" else "\uD83D\uDD25 TRENDING"
        val details = if (daysLeft != null) {
            "$progress% \u2022 $daysLeft day${if (daysLeft == 1) "" else "s"} left"
        } else {
            "$progress% \u2022 No deadline"
        }

        tvTrendingEmoji.text = CampaignDisplayHelper.campaignEmoji(trending)
        tvTrendingTag.text = tag
        tvCampTitle.text = trending.title ?: "Untitled campaign"
        tvRaised.text = "${CampaignDisplayHelper.formatPeso(trending.raised)} raised"
        pbCampProgress.progress = progress
        tvTrendingDetails.text = details

        trendingContainer.setOnClickListener {
            val intent = Intent(requireContext(), ActivitySelectAmount::class.java).apply {
                putExtra("campaignId", trending.campaignId)
                putExtra("title", trending.title)
                putExtra("goal", trending.goal ?: 0)
            }
            startActivity(intent)
        }
    }
}
