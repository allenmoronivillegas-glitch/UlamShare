package com.example.ulamshare

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class ContactSupportActivity : AppCompatActivity() {

    private enum class ChatTab(
        val rootPath: String,
        val chatType: String,
        val welcomeRole: String,
        val welcomeName: String,
        val welcomeId: String,
        val welcomeText: String
    ) {
        SUPPORT(
            rootPath = "supportChats",
            chatType = "support",
            welcomeRole = "support",
            welcomeName = "Support Team",
            welcomeId = "support-team",
            welcomeText = "Hi! Welcome to HopeGive Support. Tell us how we can help today."
        ),
        USER_USER(
            rootPath = "userUserChats",
            chatType = "user-user",
            welcomeRole = "user",
            welcomeName = "Community",
            welcomeId = "community-bot",
            welcomeText = "You are now in User-to-User chat. Start a conversation with another user."
        ),
        ADMIN_TEAM(
            rootPath = "adminTeamChats",
            chatType = "admin-team",
            welcomeRole = "admin",
            welcomeName = "Admin Team",
            welcomeId = "admin-team",
            welcomeText = "You are now connected to Admin/Moderator/Super Admin team chat."
        )
    }

    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var chipSupportNow: TextView
    private lateinit var chipTrackDonation: TextView
    private lateinit var chipReportIssue: TextView
    private lateinit var tabUserSupport: TextView
    private lateinit var tabUserUser: TextView
    private lateinit var tabAdminTeam: TextView

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var dbRef: DatabaseReference
    private val firebaseDb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app")
    }

    private lateinit var currentUserId: String
    private var currentUserEmail: String = ""
    private var currentUserLabel: String = "User"

    private var activeTab: ChatTab = ChatTab.SUPPORT
    private var activeMessagesQuery: Query? = null
    private var activeMessagesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_support)

        recycler = findViewById(R.id.recyclerChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        chipSupportNow = findViewById(R.id.chipSupportNow)
        chipTrackDonation = findViewById(R.id.chipTrackDonation)
        chipReportIssue = findViewById(R.id.chipReportIssue)
        tabUserSupport = findViewById(R.id.tabUserSupport)
        tabUserUser = findViewById(R.id.tabUserUser)
        tabAdminTeam = findViewById(R.id.tabAdminTeam)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentUserId = user.uid
        currentUserEmail = user.email.orEmpty()
        currentUserLabel = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: getString(R.string.you_label)

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        adapter = ChatAdapter(messages, currentUserId)
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter

        btnSend.setOnClickListener { sendMessage() }
        findViewById<ImageButton>(R.id.btnCompose).setOnClickListener { focusMessageInput() }
        findViewById<ImageButton>(R.id.btnMore)?.setOnClickListener { finish() }

        tabUserSupport.setOnClickListener { switchTab(ChatTab.SUPPORT) }
        tabUserUser.setOnClickListener { switchTab(ChatTab.USER_USER) }
        tabAdminTeam.setOnClickListener { switchTab(ChatTab.ADMIN_TEAM) }

        chipSupportNow.setOnClickListener { sendQuickMessage(resolveQuickMessage(0)) }
        chipTrackDonation.setOnClickListener { sendQuickMessage(resolveQuickMessage(1)) }
        chipReportIssue.setOnClickListener { sendQuickMessage(resolveQuickMessage(2)) }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        switchTab(ChatTab.SUPPORT, force = true)
    }

    override fun onDestroy() {
        detachMessagesListener()
        super.onDestroy()
    }

    private fun switchTab(tab: ChatTab, force: Boolean = false) {
        if (!force && tab == activeTab) return
        activeTab = tab
        applyTabStyles()
        updateQuickChipLabels()
        bindConversationSourceForActiveTab()
    }

    private fun applyTabStyles() {
        applyTabStyle(tabUserSupport, activeTab == ChatTab.SUPPORT)
        applyTabStyle(tabUserUser, activeTab == ChatTab.USER_USER)
        applyTabStyle(tabAdminTeam, activeTab == ChatTab.ADMIN_TEAM)
    }

    private fun applyTabStyle(tab: TextView, active: Boolean) {
        if (active) {
            tab.setBackgroundResource(R.drawable.bg_support_chip_active)
            tab.setTextColor(Color.WHITE)
            tab.setTypeface(tab.typeface, Typeface.BOLD)
        } else {
            tab.setBackgroundResource(R.drawable.bg_support_chip)
            tab.setTextColor(Color.parseColor("#1B4F9E"))
            tab.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    private fun updateQuickChipLabels() {
        when (activeTab) {
            ChatTab.SUPPORT -> {
                chipSupportNow.text = getString(R.string.chip_support_now)
                chipTrackDonation.text = getString(R.string.chip_track_donation)
                chipReportIssue.text = getString(R.string.chip_report_issue)
            }
            ChatTab.USER_USER -> {
                chipSupportNow.text = getString(R.string.chip_say_hello)
                chipTrackDonation.text = getString(R.string.chip_find_user)
                chipReportIssue.text = getString(R.string.chip_chat_request)
            }
            ChatTab.ADMIN_TEAM -> {
                chipSupportNow.text = getString(R.string.chip_request_admin)
                chipTrackDonation.text = getString(R.string.chip_report_user)
                chipReportIssue.text = getString(R.string.chip_follow_up)
            }
        }
    }

    private fun resolveQuickMessage(index: Int): String {
        return when (activeTab) {
            ChatTab.SUPPORT -> when (index) {
                0 -> getString(R.string.quick_message_support_now)
                1 -> getString(R.string.quick_message_track_donation)
                else -> getString(R.string.quick_message_report_issue)
            }
            ChatTab.USER_USER -> when (index) {
                0 -> getString(R.string.quick_message_user_user_intro)
                1 -> getString(R.string.quick_message_user_user_find)
                else -> getString(R.string.quick_message_user_user_collab)
            }
            ChatTab.ADMIN_TEAM -> when (index) {
                0 -> getString(R.string.quick_message_admin_team_help)
                1 -> getString(R.string.quick_message_admin_team_report)
                else -> getString(R.string.quick_message_admin_team_followup)
            }
        }
    }

    private fun bindConversationSourceForActiveTab() {
        detachMessagesListener()

        dbRef = firebaseDb
            .getReference(activeTab.rootPath)
            .child(currentUserId)

        val userInfo = mutableMapOf<String, Any?>()
        userInfo["email"] = currentUserEmail
        userInfo["userId"] = currentUserId
        userInfo["displayName"] = currentUserLabel
        userInfo["chatType"] = activeTab.chatType
        userInfo["updatedAt"] = ServerValue.TIMESTAMP
        dbRef.updateChildren(userInfo)

        ensureDefaultMessageForActiveTab()
        listenForMessages()
    }

    private fun ensureDefaultMessageForActiveTab() {
        val messagesRef = dbRef.child("messages")

        messagesRef.limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) return

                    val welcomeMessage = mapOf<String, Any>(
                        "text" to activeTab.welcomeText,
                        "sender" to activeTab.welcomeRole,
                        "senderRole" to activeTab.welcomeRole,
                        "senderName" to activeTab.welcomeName,
                        "senderId" to activeTab.welcomeId,
                        "chatType" to activeTab.chatType,
                        "time" to ServerValue.TIMESTAMP
                    )

                    messagesRef.push().setValue(welcomeMessage)
                }

                override fun onCancelled(error: DatabaseError) {
                    // No-op
                }
            })
    }

    private fun focusMessageInput() {
        etMessage.requestFocus()
        val currentLength = etMessage.text?.length ?: 0
        etMessage.setSelection(currentLength)
    }

    private fun sendQuickMessage(template: String) {
        etMessage.setText(template)
        etMessage.setSelection(template.length)
        sendMessage()
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val messageRef = dbRef.child("messages").push()

        val message = mapOf(
            "text" to messageText,
            "sender" to "user",
            "senderRole" to "user",
            "senderName" to currentUserLabel,
            "senderId" to currentUserId,
            "chatType" to activeTab.chatType,
            "time" to System.currentTimeMillis()
        )

        messageRef.setValue(message)
            .addOnSuccessListener {
                etMessage.setText("")
                dbRef.child("updatedAt").setValue(ServerValue.TIMESTAMP)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForMessages() {
        val query = dbRef.child("messages").orderByChild("time")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messages.clear()

                for (data in snapshot.children) {
                    val text = data.child("text").getValue(String::class.java) ?: ""
                    val sender = data.child("sender").getValue(String::class.java) ?: ""
                    val senderRoleRaw = data.child("senderRole").getValue(String::class.java).orEmpty()
                    val senderRole = if (senderRoleRaw.isNotBlank()) senderRoleRaw else fallbackRole(sender)
                    val senderNameRaw = data.child("senderName").getValue(String::class.java).orEmpty()
                    val senderName = if (senderNameRaw.isNotBlank()) senderNameRaw else fallbackSenderName(senderRole)
                    val senderId = data.child("senderId").getValue(String::class.java).orEmpty()
                    val time = data.child("time").getValue(Long::class.java) ?: 0L

                    messages.add(
                        ChatMessage(
                            text = text,
                            sender = sender,
                            time = time,
                            senderRole = senderRole,
                            senderName = senderName,
                            senderId = senderId
                        )
                    )
                }

                adapter.notifyDataSetChanged()

                if (messages.isNotEmpty()) {
                    recycler.scrollToPosition(messages.size - 1)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@ContactSupportActivity,
                    "Error loading messages: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        query.addValueEventListener(listener)
        activeMessagesQuery = query
        activeMessagesListener = listener
    }

    private fun detachMessagesListener() {
        val query = activeMessagesQuery
        val listener = activeMessagesListener
        if (query != null && listener != null) {
            query.removeEventListener(listener)
        }
        activeMessagesQuery = null
        activeMessagesListener = null
    }

    private fun fallbackRole(sender: String): String {
        return when (sender.trim().lowercase()) {
            "admin" -> "admin"
            "moderator", "mod" -> "moderator"
            "superadmin", "super admin" -> "superadmin"
            "support" -> "support"
            else -> "user"
        }
    }

    private fun fallbackSenderName(role: String): String {
        return when (role.trim().lowercase()) {
            "admin" -> "Admin"
            "moderator" -> "Moderator"
            "superadmin" -> "Super Admin"
            "support" -> getString(R.string.support_team_name)
            else -> getString(R.string.you_label)
        }
    }
}
