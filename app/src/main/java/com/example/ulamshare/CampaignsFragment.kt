package com.example.ulamshare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

class CampaignsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_choose_campaign, container, false)

        val rvCampaigns = view.findViewById<RecyclerView>(R.id.rvCampaigns)
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyStateContainer)
        val emptyTitle = view.findViewById<TextView>(R.id.tvEmptyStateTitle)
        val emptySubtitle = view.findViewById<TextView>(R.id.tvEmptyStateSubtitle)

        rvCampaigns.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyTitle.text = "Choose a Campaign"
        emptySubtitle.text = "Campaign selection will be enabled here later."

        return view
    }
}
