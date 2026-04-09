package com.example.ulamshare

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class CampaignsFragment : Fragment() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: CampaignAdapter
    private val campaignList = mutableListOf<Campaign>()
    private var campaignsListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_choose_campaign, container, false)

        // Pointing to Realtime Database 'campaigns' node
        dbRef = FirebaseDatabase.getInstance().getReference("campaigns")

        val rvCampaigns = view.findViewById<RecyclerView>(R.id.rvCampaigns)
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyStateContainer)
        val emptyTitle = view.findViewById<TextView>(R.id.tvEmptyStateTitle)
        val emptySubtitle = view.findViewById<TextView>(R.id.tvEmptyStateSubtitle)

        rvCampaigns.layoutManager = LinearLayoutManager(requireContext())
        adapter = CampaignAdapter(campaignList)
        rvCampaigns.adapter = adapter

        fetchCampaignsRealtime(rvCampaigns, emptyState, emptyTitle, emptySubtitle)

        return view
    }

    private fun fetchCampaignsRealtime(
        rv: RecyclerView,
        empty: LinearLayout,
        emptyTitle: TextView,
        emptySubtitle: TextView
    ) {
        campaignsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("CampaignsFragment", "Realtime campaign update received")
                val result = CampaignVisibility.filterVisibleCampaigns(snapshot, "CampaignsFragment")
                campaignList.clear()
                campaignList.addAll(result.visibleCampaigns)

                if (campaignList.isEmpty()) {
                    rv.visibility = View.GONE
                    empty.visibility = View.VISIBLE
                    if (result.totalCampaigns > 0 && result.filteredCount > 0) {
                        emptyTitle.text = "No visible campaigns yet"
                        emptySubtitle.text = "Campaigns exist in Firebase, but none are Active and visible right now."
                    } else {
                        emptyTitle.text = "No campaigns available"
                        emptySubtitle.text = "New campaigns are coming soon. Please check back later."
                    }
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
                emptyTitle.text = "Unable to load campaigns"
                emptySubtitle.text = "Please try again in a moment."
            }
        }
        dbRef.addValueEventListener(campaignsListener!!)
    }

    override fun onDestroyView() {
        campaignsListener?.let { dbRef.removeEventListener(it) }
        super.onDestroyView()
    }
}
