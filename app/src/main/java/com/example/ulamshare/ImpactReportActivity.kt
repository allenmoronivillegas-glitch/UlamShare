package com.example.ulamshare

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImpactReportActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var contentContainer: View
    private lateinit var tvEmptyState: TextView
    private lateinit var tvTotalDonated: TextView
    private lateinit var tvDonationCount: TextView
    private lateinit var tvCampaignCount: TextView
    private lateinit var tvEstimatedImpact: TextView
    private lateinit var recentImpactContainer: LinearLayout
    private lateinit var categoriesContainer: LinearLayout

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val donationsRef by lazy {
        FirebaseDatabase
            .getInstance("https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference
            .child("donations")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_impact_report)

        bindViews()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        loadImpactReport()
    }

    private fun bindViews() {
        progressBar = findViewById(R.id.progressBar)
        contentContainer = findViewById(R.id.contentContainer)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvTotalDonated = findViewById(R.id.tvTotalDonated)
        tvDonationCount = findViewById(R.id.tvDonationCount)
        tvCampaignCount = findViewById(R.id.tvCampaignCount)
        tvEstimatedImpact = findViewById(R.id.tvEstimatedImpact)
        recentImpactContainer = findViewById(R.id.recentImpactContainer)
        categoriesContainer = findViewById(R.id.categoriesContainer)
    }

    private fun loadImpactReport() {
        val user = auth.currentUser
        if (user == null) {
            showEmpty("Please log in to view your impact report.")
            return
        }

        showLoading()
        donationsRef.orderByChild("userId").equalTo(user.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val donations = snapshot.children.map { parseDonation(it) }
                        .filter { it.amount > 0 }
                        .sortedByDescending { it.timestamp }

                    if (donations.isEmpty()) {
                        showEmpty("No donations yet. Start donating to see your impact.")
                        return
                    }

                    bindImpact(donations)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Impact report load failed", error.toException())
                    showEmpty("Unable to load your impact report right now.")
                }
            })
    }

    private fun bindImpact(donations: List<ImpactDonation>) {
        val total = donations.sumOf { it.amount }
        val campaignCount = donations.map { it.campaignId.ifBlank { it.campaignTitle } }
            .filter { it.isNotBlank() }
            .toSet()
            .size
        val categoryCounts = donations.groupingBy {
            it.category.ifBlank { "General Campaigns" }
        }.eachCount()

        progressBar.visibility = View.GONE
        tvEmptyState.visibility = View.GONE
        contentContainer.visibility = View.VISIBLE

        tvTotalDonated.text = formatPeso(total)
        tvDonationCount.text = donations.size.toString()
        tvCampaignCount.text = campaignCount.toString()
        tvEstimatedImpact.text = "Your giving has supported $campaignCount campaign${if (campaignCount == 1) "" else "s"} across ${categoryCounts.size} impact area${if (categoryCounts.size == 1) "" else "s"}."

        recentImpactContainer.removeAllViews()
        donations.take(5).forEach { donation ->
            recentImpactContainer.addView(
                buildInfoRow(
                    title = donation.campaignTitle.ifBlank { "HopeGive Campaign" },
                    subtitle = listOf(
                        formatPeso(donation.amount),
                        donation.status.ifBlank { "Recorded" },
                        formatDate(donation.timestamp, donation.dateString)
                    ).filter { it.isNotBlank() }.joinToString(" • ")
                )
            )
        }

        categoriesContainer.removeAllViews()
        categoryCounts.entries.sortedByDescending { it.value }.forEach { entry ->
            categoriesContainer.addView(
                buildInfoRow(
                    title = entry.key,
                    subtitle = "${entry.value} donation${if (entry.value == 1) "" else "s"}"
                )
            )
        }
    }

    private fun buildInfoRow(title: String, subtitle: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = getDrawable(R.drawable.rounded_input_border)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(0xFF1E3A5F.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
        }

        row.addView(titleView)
        row.addView(subtitleView)
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        }
        return row
    }

    private fun parseDonation(snapshot: DataSnapshot): ImpactDonation {
        val amount = snapshot.child("amount").getValue(Int::class.java)
            ?: snapshot.child("amount").getValue(Long::class.java)?.toInt()
            ?: snapshot.child("amount").getValue(Double::class.java)?.toInt()
            ?: snapshot.child("amount").getValue(String::class.java)?.replace(",", "")?.toIntOrNull()
            ?: 0
        return ImpactDonation(
            amount = amount,
            campaignId = snapshot.child("campaignId").getValue(String::class.java).orEmpty(),
            campaignTitle = snapshot.child("campaignTitle").getValue(String::class.java).orEmpty(),
            category = snapshot.child("category").getValue(String::class.java)
                ?: snapshot.child("campaignCategory").getValue(String::class.java)
                ?: "",
            status = snapshot.child("status").getValue(String::class.java).orEmpty()
                .ifBlank { snapshot.child("verificationStatus").getValue(String::class.java).orEmpty() },
            timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L,
            dateString = snapshot.child("dateString").getValue(String::class.java).orEmpty()
        )
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        tvEmptyState.visibility = View.GONE
        contentContainer.visibility = View.GONE
    }

    private fun showEmpty(message: String) {
        progressBar.visibility = View.GONE
        contentContainer.visibility = View.GONE
        tvEmptyState.text = message
        tvEmptyState.visibility = View.VISIBLE
        if (message.startsWith("Please log in")) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatPeso(amount: Int): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return "\u20B1${formatter.format(amount)}"
    }

    private fun formatDate(timestamp: Long, fallback: String): String {
        if (timestamp <= 0L) return fallback
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class ImpactDonation(
        val amount: Int,
        val campaignId: String,
        val campaignTitle: String,
        val category: String,
        val status: String,
        val timestamp: Long,
        val dateString: String
    )

    companion object {
        private const val TAG = "ImpactReport"
    }
}
