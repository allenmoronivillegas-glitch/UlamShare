package com.example.ulamshare

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

data class DonationHistory(
    val id: String = "",
    val campaignTitle: String = "",
    val amount: Int = 0,
    val paymentMethod: String = "",
    val timestamp: Long = 0L,
    val dateString: String = "",
    val userId: String = ""
)

class DonationHistoryAdapter(private val donations: List<DonationHistory>) :
    RecyclerView.Adapter<DonationHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val campaignName: TextView = itemView.findViewById(R.id.tvCampaignName)
        private val donationDate: TextView = itemView.findViewById(R.id.tvDonationDate)
        private val donationAmount: TextView = itemView.findViewById(R.id.tvDonationAmount)
        private val paymentMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)

        fun bind(donation: DonationHistory) {
            campaignName.text = donation.campaignTitle
            donationDate.text = donation.dateString
            donationAmount.text = "₱${String.format("%,d", donation.amount)}"
            paymentMethod.text = donation.paymentMethod
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_donation_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(donations[position])
    }

    override fun getItemCount(): Int = donations.size
}

class ActivityHistory : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    private lateinit var rvDonationHistory: RecyclerView
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnStartDonating: Button

    private val donations = mutableListOf<DonationHistory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance("https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app")

        rvDonationHistory = findViewById(R.id.rvDonationHistory)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        btnBack = findViewById(R.id.btnBack)
        btnStartDonating = findViewById(R.id.btnStartDonating)

        // Setup RecyclerView with LinearLayoutManager
        rvDonationHistory.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener {
            finish()
        }

        btnStartDonating.setOnClickListener {
            finish()
        }

        Log.d("ActivityHistory", "Activity created - loading donations for user: ${auth.currentUser?.uid}")
        loadDonationHistory()
    }

    private fun loadDonationHistory() {
        val user = auth.currentUser
        if (user == null) {
            Log.w("ActivityHistory", "No user logged in")
            showEmptyState()
            return
        }

        val donationsRef = db.getReference("donations")
        donationsRef.get()
            .addOnSuccessListener { snapshot ->
                donations.clear()
                Log.d("ActivityHistory", "Firebase snapshot received with ${snapshot.childrenCount} total donations")

                snapshot.children.forEach { donationSnapshot ->
                    val id = donationSnapshot.key ?: ""
                    val campaignTitle = donationSnapshot.child("campaignTitle").value as? String ?: "Unknown"
                    val amount = (donationSnapshot.child("amount").value as? Number)?.toInt() ?: 0
                    val paymentMethod = donationSnapshot.child("paymentMethod").value as? String ?: "Unknown"
                    val timestamp = (donationSnapshot.child("timestamp").value as? Number)?.toLong() ?: 0L
                    val dateString = donationSnapshot.child("dateString").value as? String ?: ""
                    val userId = donationSnapshot.child("userId").value as? String ?: ""
                    
                    Log.d("ActivityHistory", "Checking donation: campaignTitle=$campaignTitle, userId=$userId, currentUserId=${user.uid}")
                    
                    if (userId == user.uid) {
                        Log.d("ActivityHistory", "Adding donation: $campaignTitle - ₱$amount")
                        donations.add(
                            DonationHistory(
                                id = id,
                                campaignTitle = campaignTitle,
                                amount = amount,
                                paymentMethod = paymentMethod,
                                timestamp = timestamp,
                                dateString = dateString,
                                userId = userId
                            )
                        )
                    }
                }

                donations.sortByDescending { it.timestamp }
                Log.d("ActivityHistory", "Total user donations found: ${donations.size}")

                if (donations.isEmpty()) {
                    Log.d("ActivityHistory", "No donations found - showing empty state")
                    showEmptyState()
                } else {
                    Log.d("ActivityHistory", "Donations found - showing donation list")
                    showDonationList()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ActivityHistory", "Error loading donations: ${e.message}", e)
                showEmptyState()
            }
    }

    private fun showEmptyState() {
        Log.d("ActivityHistory", "Showing empty state")
        emptyStateContainer.visibility = android.view.View.VISIBLE
        rvDonationHistory.visibility = android.view.View.GONE
    }

    private fun showDonationList() {
        Log.d("ActivityHistory", "Showing donation list with ${donations.size} items")
        emptyStateContainer.visibility = android.view.View.GONE
        rvDonationHistory.visibility = android.view.View.VISIBLE
        rvDonationHistory.adapter = DonationHistoryAdapter(donations)
        Log.d("ActivityHistory", "Adapter set successfully")
    }
}
