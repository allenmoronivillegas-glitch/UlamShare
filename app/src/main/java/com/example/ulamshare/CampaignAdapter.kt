package com.example.ulamshare

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class CampaignAdapter(private var items: List<Campaign>) : RecyclerView.Adapter<CampaignAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emojiView: TextView = itemView.findViewById(R.id.tvCampaignEmoji)
        private val titleView: TextView = itemView.findViewById(R.id.tvActiveCampTitle)
        private val subView: TextView = itemView.findViewById(R.id.tvActiveCampSub)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.pbMiniProgress)
        private val raisedView: TextView = itemView.findViewById(R.id.tvRaisedAmount)
        private val percentView: TextView = itemView.findViewById(R.id.tvProgressPercent)
        private val statusBadgeView: TextView = itemView.findViewById(R.id.tvCampaignStatusBadge)

        fun bind(campaign: Campaign) {
            val isExpired = CampaignDisplayHelper.isExpired(campaign)
            emojiView.text = CampaignDisplayHelper.campaignEmoji(campaign)
            titleView.text = campaign.title ?: "Untitled Campaign"
            subView.text = campaign.description?.ifBlank { "No description yet." } ?: "No description yet."
            raisedView.text = "${CampaignDisplayHelper.formatPeso(CampaignDisplayHelper.campaignRaised(campaign))} raised"

            val progress = CampaignDisplayHelper.progressPercent(campaign)
            progressBar.progress = progress
            percentView.text = "$progress%"

            statusBadgeView.visibility = if (isExpired) View.VISIBLE else View.GONE
            if (isExpired) {
                statusBadgeView.text = "Expired"
                itemView.alpha = 0.72f
            } else {
                itemView.alpha = 1f
            }

            itemView.setOnClickListener {
                if (GuestDonationGuard.blockIfGuest(
                        context = itemView.context,
                        campaignId = campaign.campaignId,
                        campaignTitle = campaign.title
                    )
                ) {
                    return@setOnClickListener
                }

                if (!CampaignDisplayHelper.canDonate(campaign)) {
                    Toast.makeText(
                        itemView.context,
                        "This campaign has expired and is no longer accepting donations.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val intent = Intent(itemView.context, ActivitySelectAmount::class.java).apply {
                    putExtra("campaignId", campaign.campaignId)
                    putExtra("title", campaign.title)
                    putExtra("goal", CampaignDisplayHelper.campaignGoal(campaign))
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
