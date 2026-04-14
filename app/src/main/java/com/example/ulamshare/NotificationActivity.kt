package com.example.ulamshare

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(private val notifications: List<AppNotification>) :
    RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNotifTitle)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvNotifMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvNotifTime)

        fun bind(notification: AppNotification) {
            tvTitle.text = notification.title
            tvMessage.text = notification.message
            tvTime.text = notification.getTimeAgo()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size
}

class NotificationActivity : AppCompatActivity() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var emptyStateContainer: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var tvClearAll: TextView
    private val notifications = mutableListOf<AppNotification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        rvNotifications = findViewById(R.id.rvNotifications)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        btnBack = findViewById(R.id.btnBack)
        tvClearAll = findViewById(R.id.tvClearAll)

        rvNotifications.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener {
            finish()
        }

        tvClearAll.setOnClickListener {
            NotificationRepository.clearAll()
            loadNotifications()
        }

        loadNotifications()
    }

    private fun loadNotifications() {
        val allNotifications = NotificationRepository.getNotifications()
        notifications.clear()
        notifications.addAll(allNotifications.sortedByDescending { it.timestamp })
        
        if (notifications.isEmpty()) {
            showEmptyState()
        } else {
            showNotificationList()
        }
    }

    private fun showEmptyState() {
        emptyStateContainer.visibility = android.view.View.VISIBLE
        rvNotifications.visibility = android.view.View.GONE
        tvClearAll.visibility = android.view.View.GONE
    }

    private fun showNotificationList() {
        emptyStateContainer.visibility = android.view.View.GONE
        rvNotifications.visibility = android.view.View.VISIBLE
        tvClearAll.visibility = android.view.View.VISIBLE
        rvNotifications.adapter = NotificationAdapter(notifications)
    }
}
