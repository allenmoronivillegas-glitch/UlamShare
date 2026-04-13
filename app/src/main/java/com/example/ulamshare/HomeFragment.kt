package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
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
    private lateinit var rvRecentlyAdded: RecyclerView
    private lateinit var emptyCampaignsCard: ConstraintLayout
    private lateinit var tvEmptyCampaignsMessage: TextView
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
        val btnViewMyDonations = view.findViewById<Button>(R.id.btnViewMyDonations)
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

        btnViewMyDonations?.setOnClickListener {
            startActivity(Intent(requireContext(), ActivityHistory::class.java))
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
}
