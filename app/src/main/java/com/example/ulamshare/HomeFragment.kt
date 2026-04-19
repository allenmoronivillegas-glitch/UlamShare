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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class HomeFragment : Fragment() {
    private var lastCampaignCount = 0

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var dbRef: DatabaseReference
    private lateinit var tvUserName: TextView
    private lateinit var tvProfileInitials: TextView
    private lateinit var btnRegisterHeader: Button
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
    ): View? {
        val view = inflater.inflate(R.layout.activity_home, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        dbRef = FirebaseDatabase.getInstance().getReference("campaigns")

        tvUserName = view.findViewById(R.id.tvUserName)
        tvProfileInitials = view.findViewById(R.id.tvProfileInitials)
        btnRegisterHeader = view.findViewById(R.id.btnRegisterHeader)
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
                        tvUserName.text = "$firstName ✨"
                        val initials = fullName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
                        tvProfileInitials.text = initials
                    }
                }
        } else {
            tvUserName.text = "Guest ✨"
            tvProfileInitials.text = "G"
            btnRegisterHeader.visibility = View.VISIBLE
        }
    }

    private fun fetchRecentCampaignsRealtime() {
        campaignsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("HomeFragment", "Realtime update received for campaigns")
                Log.d("HomeFragment", "snapshot.value=${snapshot.value}")

                campaignList.clear()

                val count = snapshot.childrenCount.toInt()
                var lastCampaign: Campaign? = null

                for (campaignSnapshot in snapshot.children) {
                    val campaign = campaignSnapshot.getValue(Campaign::class.java)
                    if (campaign != null) {
                        if (campaign.campaignId.isNullOrBlank()) {
                            campaign.campaignId = campaignSnapshot.key
                        }

                        campaignList.add(campaign)
                        lastCampaign = campaign

                        Log.d(
                            "HomeFragment",
                            "campaign title=${campaign.title}, status=${campaign.status}, hidden=${campaign.hidden}"
                        )
                    }
                }

                // ✅ ONLY trigger when NEW campaign is added
                if (lastCampaignCount != 0 && count > lastCampaignCount && lastCampaign != null) {

                    // 🔔 SAVE TO HISTORY
                    NotificationRepository.init(requireContext())
                    val newNotif = AppNotification(
                        id = UUID.randomUUID().toString(),
                        title = "New Campaign: ${lastCampaign.title}",
                        message = "A new campaign has been launched. Tap to view and support!",
                        timestamp = System.currentTimeMillis(),
                        type = "campaign"
                    )
                    NotificationRepository.saveNotification(newNotif)

                    // 🔔 TOAST
                    Toast.makeText(requireContext(), "New campaign added!", Toast.LENGTH_SHORT).show()

                    val manager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = "campaign_channel"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Campaign Notifications",
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                        manager.createNotificationChannel(channel)
                    }

                    // 🔗 PART 1 — MAKE NOTIFICATION CLICKABLE & PASS DATA
                    val intent = Intent(requireContext(), ActivitySelectAmount::class.java).apply {
                        putExtra("campaignId", lastCampaign.campaignId)
                        putExtra("title", lastCampaign.title)
                        putExtra("goal", lastCampaign.goal ?: 0)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        requireContext(),
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // 🔔 PART 3 — IMPROVE NOTIFICATION DESIGN
                    val notification = NotificationCompat.Builder(requireContext(), channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(lastCampaign.title)
                        .setContentText("Tap to donate now")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(lastCampaign.title))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                    manager.notify(1, notification)
                    
                }

                lastCampaignCount = count

                // ✅ NORMAL DISPLAY (UNCHANGED)
                campaignList.sortByDescending { it.createdAt ?: 0L }
                val recentList = if (campaignList.size > 3) campaignList.take(3) else campaignList

                if (recentList.isEmpty()) {
                    rvRecentlyAdded.visibility = View.GONE
                    emptyCampaignsCard.visibility = View.VISIBLE
                    tvEmptyCampaignsMessage.text = "No campaigns available yet."
                } else {
                    rvRecentlyAdded.visibility = View.VISIBLE
                    emptyCampaignsCard.visibility = View.GONE
                    adapter.submitList(recentList.toList())
                }

                updateTrendingCampaign(campaignList)
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

    private fun formatAmount(amount: Int?): String {
        return String.format(Locale.US, "₱%,d", amount ?: 0)
    }

    private fun selectTrendingCampaign(campaigns: List<Campaign>): Campaign? {
        val visibleCampaigns = campaigns.filter { it.hidden != true }
        val urgentCampaigns = visibleCampaigns.filter {
            val daysLeft = calculateDaysLeft(it)
            daysLeft != null && daysLeft <= 3
        }.sortedWith(compareBy({ calculateDaysLeft(it) ?: Int.MAX_VALUE }, { -calculateProgress(it) }))

        if (urgentCampaigns.isNotEmpty()) return urgentCampaigns.first()

        return visibleCampaigns.maxWithOrNull(compareBy({ calculateProgress(it) }, { it.createdAt ?: 0L }))
    }

    private fun updateTrendingCampaign(campaigns: List<Campaign>) {
        val trending = selectTrendingCampaign(campaigns)
        if (trending == null) {
            tvTrendingTag.text = "🔥 TRENDING"
            tvCampTitle.text = "No highlighted campaign"
            pbCampProgress.progress = 0
            tvTrendingDetails.text = "No urgent or featured campaigns available"
            tvRaised.text = ""
            return
        }

        val progress = calculateProgress(trending)
        val daysLeft = calculateDaysLeft(trending)
        val tag = if (daysLeft != null && daysLeft <= 3) "🚨 EMERGENCY" else "🔥 TRENDING"
        val details = if (daysLeft != null) {
            "$progress% • $daysLeft day${if (daysLeft == 1) "" else "s"} left"
        } else {
            "$progress% • No deadline"
        }

        tvTrendingTag.text = tag
        tvCampTitle.text = trending.title ?: "Untitled campaign"
        tvRaised.text = "${formatAmount(trending.raised)} raised"
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
