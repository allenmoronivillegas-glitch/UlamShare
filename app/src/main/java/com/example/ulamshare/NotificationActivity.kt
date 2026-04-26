package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class NotificationAdapter(
    private val notifications: List<AppNotification>,
    private val onNotificationClicked: (AppNotification) -> Unit
) :
    RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val unreadIndicator: View = itemView.findViewById(R.id.vUnreadIndicator)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNotifTitle)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvNotifMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvNotifTime)

        fun bind(notification: AppNotification) {
            unreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE
            tvTitle.text = notification.title
            tvMessage.text = notification.message
            tvTime.text = notification.getTimeAgo()
            itemView.setOnClickListener { onNotificationClicked(notification) }
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
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var notificationsRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        NotificationRepository.init(this)

        rvNotifications = findViewById(R.id.rvNotifications)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        btnBack = findViewById(R.id.btnBack)
        tvClearAll = findViewById(R.id.tvClearAll)

        rvNotifications.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener {
            finish()
        }

        tvClearAll.setOnClickListener {
            clearNotifications()
        }

        loadNotifications()
    }

    override fun onDestroy() {
        notificationsRegistration?.remove()
        notificationsRegistration = null
        super.onDestroy()
    }

    private fun loadNotifications() {
        val user = auth.currentUser
        if (user == null) {
            loadLocalNotifications()
            return
        }

        notificationsRegistration?.remove()
        notificationsRegistration = firestore.collection("notifications")
            .whereEqualTo("recipientId", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loadLocalNotifications()
                    return@addSnapshotListener
                }

                notifications.clear()
                notifications.addAll(
                    snapshot?.documents.orEmpty()
                        .map { document ->
                            AppNotification(
                                id = document.getString("id").orEmpty().ifBlank { document.id },
                                title = titleForType(document.getString("type").orEmpty()),
                                message = document.getString("message").orEmpty(),
                                timestamp = timestampToMillis(document.getTimestamp("createdAt")),
                                type = document.getString("type").orEmpty().ifBlank { "campaign" },
                                isRead = document.getBoolean("isRead") ?: false,
                                postId = document.getString("postId").orEmpty(),
                                commentId = document.getString("commentId").orEmpty(),
                                replyId = document.getString("replyId").orEmpty(),
                                replyingToReplyId = document.getString("replyingToReplyId").orEmpty()
                            )
                        }
                        .sortedByDescending { it.timestamp }
                )

                if (notifications.isEmpty()) {
                    showEmptyState()
                } else {
                    showNotificationList()
                }
            }
    }

    private fun loadLocalNotifications() {
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
        rvNotifications.adapter = NotificationAdapter(notifications) { notification ->
            markNotificationRead(notification)
            openNotificationTarget(notification)
        }
    }

    private fun markNotificationRead(notification: AppNotification) {
        if (notification.isRead) return
        val user = auth.currentUser ?: return
        firestore.collection("notifications")
            .document(notification.id)
            .update("isRead", true)
            .addOnFailureListener {
                loadLocalNotifications()
            }
    }

    private fun clearNotifications() {
        val user = auth.currentUser
        if (user == null) {
            NotificationRepository.clearAll()
            loadNotifications()
            return
        }

        val currentNotifications = notifications.toList()
        if (currentNotifications.isEmpty()) return
        val batch = firestore.batch()
        currentNotifications.forEach { notification ->
            batch.delete(firestore.collection("notifications").document(notification.id))
        }
        batch.commit()
    }

    private fun titleForType(type: String): String {
        return when (type) {
            "comment" -> "New comment"
            "comment_reply" -> "New reply"
            "reply_reply" -> "New reply"
            "post_reaction" -> "New reaction"
            "mention" -> "New mention"
            else -> "Campaign update"
        }
    }

    private fun timestampToMillis(timestamp: Timestamp?): Long {
        return timestamp?.toDate()?.time ?: System.currentTimeMillis()
    }

    private fun openNotificationTarget(notification: AppNotification) {
        if (notification.postId.isBlank()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_CAMPAIGNS, true)
            putExtra(MainActivity.EXTRA_POST_ID, notification.postId)
            putExtra(MainActivity.EXTRA_COMMENT_ID, notification.commentId)
            putExtra(MainActivity.EXTRA_REPLY_ID, notification.replyId)
            putExtra(MainActivity.EXTRA_NOTIFICATION_TYPE, notification.type)
        }
        startActivity(intent)
    }
}
