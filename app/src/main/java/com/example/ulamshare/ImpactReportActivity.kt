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
    private var impactLoadRequestId = 0

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
        val requestId = ++impactLoadRequestId
        val user = auth.currentUser
        if (user == null) {
            showEmpty("Please log in to view your impact report.")
            return
        }

        showLoading()
        progressBar.postDelayed({
            if (requestId == impactLoadRequestId && progressBar.visibility == View.VISIBLE) {
                Log.w(TAG, "Impact report load timed out; showing empty state")
                showEmpty("No donations yet. Start donating to see your impact.")
            }
        }, IMPACT_LOAD_TIMEOUT_MS)

        UserDonationStatsRepository.loadUserDonationStats(user.uid) { result ->
            if (requestId != impactLoadRequestId) return@loadUserDonationStats
            result
                .onSuccess { stats ->
                    if (stats.donationsCount == 0) {
                        showEmpty("No donations yet. Start donating to see your impact.")
                        return@onSuccess
                    }

                    bindImpact(stats)
                }
                .onFailure { error ->
                    Log.e(TAG, "Impact report load failed", error)
                    showEmpty("No donations yet. Start donating to see your impact.")
                }
        }
    }

    private fun bindImpact(stats: UserDonationStats) {
        val donations = stats.donations
        val campaignCount = stats.campaignsDonatedCount
        val categoryCounts = stats.categoryCounts

        progressBar.visibility = View.GONE
        tvEmptyState.visibility = View.GONE
        contentContainer.visibility = View.VISIBLE

        tvTotalDonated.text = formatPeso(stats.totalDonated)
        tvDonationCount.text = stats.donationsCount.toString()
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

    private fun formatPeso(amount: Long): String {
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

    companion object {
        private const val TAG = "ImpactReport"
        private const val IMPACT_LOAD_TIMEOUT_MS = 12000L
    }
}
