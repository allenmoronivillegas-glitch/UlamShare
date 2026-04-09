package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

class HomeFragment : Fragment() {

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
        
        // Using the correct region-specific URL from google-services.json
        val databaseUrl = "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
        dbRef = FirebaseDatabase.getInstance(databaseUrl).getReference("campaigns")

        tvUserName = view.findViewById(R.id.tvUserName)
        tvProfileInitials = view.findViewById(R.id.tvProfileInitials)
        btnRegisterHeader = view.findViewById(R.id.btnRegisterHeader)
        rvRecentlyAdded = view.findViewById(R.id.rvRecentlyAdded)
        emptyCampaignsCard = view.findViewById(R.id.emptyCampaignsCard)
        tvEmptyCampaignsMessage = view.findViewById(R.id.tvEmptyCampaignsMessage)

        rvRecentlyAdded.layoutManager = LinearLayoutManager(requireContext())
        adapter = CampaignAdapter(campaignList)
        rvRecentlyAdded.adapter = adapter

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
                val result = CampaignVisibility.filterVisibleCampaigns(snapshot, "HomeFragment")
                campaignList.clear()
                campaignList.addAll(result.visibleCampaigns)
                val recentList = if (campaignList.size > 3) campaignList.take(3) else campaignList

                campaignList.clear()
                campaignList.addAll(recentList)

                if (campaignList.isEmpty()) {
                    rvRecentlyAdded.visibility = View.GONE
                    emptyCampaignsCard.visibility = View.VISIBLE
                    tvEmptyCampaignsMessage.text =
                        if (result.totalCampaigns > 0 && result.filteredCount > 0) {
                            "Campaigns exist, but none are Active and visible right now."
                        } else {
                            "No active campaigns yet."
                        }
                } else {
                    rvRecentlyAdded.visibility = View.VISIBLE
                    emptyCampaignsCard.visibility = View.GONE
                    adapter.notifyDataSetChanged()
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
