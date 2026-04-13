package com.example.ulamshare

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CampaignAdapter(private var items: List<Campaign>) : RecyclerView.Adapter<CampaignAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.tvActiveCampTitle)
        private val subView: TextView = itemView.findViewById(R.id.tvActiveCampSub)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.pbMiniProgress)
        private val raisedView: TextView = itemView.findViewById(R.id.tvRaisedAmount)
        private val percentView: TextView = itemView.findViewById(R.id.tvProgressPercent)

        fun bind(campaign: Campaign) {
            titleView.text = campaign.title ?: "Untitled Campaign"
            subView.text = campaign.description ?: "No description yet."
            raisedView.text = "₱${campaign.raised} raised"
            
            val goal = campaign.goal ?: 0
            val raised = campaign.raised ?: 0
            val progress = if (goal > 0) (raised * 100 / goal) else 0
            progressBar.progress = progress
            percentView.text = "$progress%"

            // Make clickable and navigate to Select Amount
            itemView.setOnClickListener {
                val intent = Intent(itemView.context, ActivitySelectAmount::class.java).apply {
                    putExtra("campaignId", campaign.campaignId)
                    putExtra("title", campaign.title)
                    putExtra("goal", campaign.goal ?: 0)
                }
                itemView.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_campaign, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<Campaign>) {
        items = newList
        notifyDataSetChanged()
    }
}
