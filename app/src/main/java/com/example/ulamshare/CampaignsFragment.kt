package com.example.ulamshare

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class CampaignsFragment : Fragment() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: CampaignAdapter
    private val campaignList = mutableListOf<Campaign>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_choose_campaign, container, false)

        // Pointing to Realtime Database 'campaigns' node
        dbRef = FirebaseDatabase.getInstance().getReference("campaigns")

        val rvCampaigns = view.findViewById<RecyclerView>(R.id.rvCampaigns)
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyStateContainer)

        rvCampaigns.layoutManager = LinearLayoutManager(requireContext())
        adapter = CampaignAdapter(campaignList)
        rvCampaigns.adapter = adapter

        fetchCampaignsRealtime(rvCampaigns, emptyState)

        return view
    }

    private fun fetchCampaignsRealtime(rv: RecyclerView, empty: LinearLayout) {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                campaignList.clear()
                for (campaignSnapshot in snapshot.children) {
                    val campaign = campaignSnapshot.getValue(Campaign::class.java)
                    if (campaign != null && campaign.status == "Published") {
                        campaignList.add(campaign)
                    }
                }
                
                // Sort by createdAt descending (if RTDB doesn't do it)
                campaignList.sortByDescending { it.createdAt }

                if (campaignList.isEmpty()) {
                    rv.visibility = View.GONE
                    empty.visibility = View.VISIBLE
                } else {
                    rv.visibility = View.VISIBLE
                    empty.visibility = View.GONE
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CampaignsFragment", "Error fetching campaigns", error.toException())
                rv.visibility = View.GONE
                empty.visibility = View.VISIBLE
            }
        })
    }
}
