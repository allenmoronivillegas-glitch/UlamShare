package com.example.ulamshare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MessengerChatActivity : AppCompatActivity() {

    companion object {
        private const val DATABASE_URL =
            "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"

        private const val EXTRA_ROOT_PATH = "extra_root_path"
        private const val EXTRA_CHANNEL = "extra_channel"
        private const val EXTRA_CHAT_TYPE = "extra_chat_type"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TYPE_LABEL = "extra_type_label"
        private const val EXTRA_PARTICIPANT_USER_ID = "extra_participant_user_id"
        private const val EXTRA_PARTICIPANT_EMAIL = "extra_participant_email"

        private const val SUPPORT_CHANNEL = "support"
        private const val ADMIN_CHANNEL = "admin"
        private const val DIRECT_CHANNEL = "direct"

        fun createIntent(context: Context, conversation: MessengerConversation): Intent {
            return Intent(context, MessengerChatActivity::class.java).apply {
                putExtra(EXTRA_ROOT_PATH, conversation.rootPath)
                putExtra(EXTRA_CHANNEL, conversation.channel)
                putExtra(EXTRA_CHAT_TYPE, conversation.chatType)
                putExtra(EXTRA_TITLE, conversation.title)
                putExtra(EXTRA_TYPE_LABEL, conversation.typeLabel)
                putExtra(EXTRA_PARTICIPANT_USER_ID, conversation.participantUserId)
                putExtra(EXTRA_PARTICIPANT_EMAIL, conversation.participantEmail)
            }
        }
    }

    private lateinit var recyclerChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvChatAvatar: TextView
    private lateinit var tvChatTitle: TextView
    private lateinit var tvChatSubtitle: TextView
    private lateinit var tvMessagesEmpty: TextView

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private var activeMessagesQuery: Query? = null
    private var activeMessagesListener: ValueEventListener? = null

    private lateinit var currentUserId: String
    private var currentUserEmail: String = ""
    private var currentUserLabel: String = "User"

    private var conversationRootPath: String = ""
    private var conversationChannel: String = ""
    private var conversationChatType: String = ""
    private var conversationTitle: String = ""
    private var conversationTypeLabel: String = ""
    private var participantUserId: String = ""
    private var participantEmail: String = ""

    private val firebaseDb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL)
    }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messenger_chat)

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

        if (!readConversationExtras()) {
            Toast.makeText(this, "Unable to open this chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupRecyclerView()
        setupActions()
        loadCurrentUserProfile()
    }

    override fun onDestroy() {
        detachMessagesListener()
        super.onDestroy()
    }

    private fun readConversationExtras(): Boolean {
        conversationRootPath = intent.getStringExtra(EXTRA_ROOT_PATH).orEmpty()
        conversationChannel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        conversationChatType = intent.getStringExtra(EXTRA_CHAT_TYPE).orEmpty()
        conversationTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        conversationTypeLabel = intent.getStringExtra(EXTRA_TYPE_LABEL).orEmpty()
        participantUserId = intent.getStringExtra(EXTRA_PARTICIPANT_USER_ID).orEmpty()
        participantEmail = intent.getStringExtra(EXTRA_PARTICIPANT_EMAIL).orEmpty()

        return conversationRootPath.isNotBlank() && conversationTitle.isNotBlank()
    }

    private fun bindViews() {
        recyclerChat = findViewById(R.id.recyclerChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        tvChatAvatar = findViewById(R.id.tvChatAvatar)
        tvChatTitle = findViewById(R.id.tvChatTitle)
        tvChatSubtitle = findViewById(R.id.tvChatSubtitle)
        tvMessagesEmpty = findViewById(R.id.tvMessagesEmpty)
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatAdapter = ChatAdapter(messages, currentUserId)
        recyclerChat.layoutManager = layoutManager
        recyclerChat.adapter = chatAdapter
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnSend.setOnClickListener { sendMessage() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    private fun loadCurrentUserProfile() {
        firestore.collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                val fullName = document.getString("fullName").orEmpty()
                val email = document.getString("email").orEmpty()

                if (fullName.isNotBlank()) {
                    currentUserLabel = fullName
                }
                if (email.isNotBlank()) {
                    currentUserEmail = email
                }
            }
            .addOnCompleteListener {
                bindHeader()
                ensureConversationReady()
                bindMessages()
            }
    }

    private fun bindHeader() {
        tvChatTitle.text = conversationTitle
        tvChatSubtitle.text = conversationTypeLabel
        tvChatAvatar.text = initials(conversationTitle)
        etMessage.hint = getString(R.string.message_contact_hint, conversationTitle)
    }

    private fun ensureConversationReady() {
        when (conversationChannel) {
            SUPPORT_CHANNEL -> ensureLegacyConversationMetadata(
                welcomeRole = "support",
                welcomeName = getString(R.string.support_team_name),
                welcomeId = "support-team",
                welcomeText = "Hi! Welcome to HopeGive Support. Tell us how we can help today."
            )

            ADMIN_CHANNEL -> ensureLegacyConversationMetadata(
                welcomeRole = "admin",
                welcomeName = getString(R.string.admin_team_name),
                welcomeId = "admin-team",
                welcomeText = "You are now connected to the admin team. Send a message anytime."
            )

            DIRECT_CHANNEL -> ensureDirectConversationMetadata()
        }
    }

    private fun ensureDirectConversationMetadata() {
        if (participantUserId.isBlank()) return

        val rootRef = firebaseDb.getReference(conversationRootPath)
        val metadata = hashMapOf<String, Any>(
            "chatType" to conversationChatType,
            "participants/$currentUserId" to true,
            "participants/$participantUserId" to true,
            "participantProfiles/$currentUserId/displayName" to currentUserLabel,
            "participantProfiles/$currentUserId/email" to currentUserEmail,
            "participantProfiles/$participantUserId/displayName" to conversationTitle,
            "participantProfiles/$participantUserId/email" to participantEmail
        )
        rootRef.updateChildren(metadata)
    }

    private fun ensureLegacyConversationMetadata(
        welcomeRole: String,
        welcomeName: String,
        welcomeId: String,
        welcomeText: String
    ) {
        val rootRef = firebaseDb.getReference(conversationRootPath)
        val metadata = hashMapOf<String, Any>(
            "email" to currentUserEmail,
            "userId" to currentUserId,
            "displayName" to currentUserLabel,
            "chatType" to conversationChatType
        )
        rootRef.updateChildren(metadata)

        rootRef.child("messages").limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) return

                    val welcomeMessage = hashMapOf<String, Any>(
                        "text" to welcomeText,
                        "sender" to welcomeRole,
                        "senderRole" to welcomeRole,
                        "senderName" to welcomeName,
                        "senderId" to welcomeId,
                        "chatType" to conversationChatType,
                        "time" to ServerValue.TIMESTAMP
                    )

                    rootRef.child("messages").push().setValue(welcomeMessage)
                    val rootUpdates: HashMap<String, Any> = hashMapOf(
                        "updatedAt" to ServerValue.TIMESTAMP,
                        "lastMessage" to welcomeMessage as Any
                    )
                    rootRef.updateChildren(rootUpdates)
                }

                override fun onCancelled(error: DatabaseError) {
                    // No-op
                }
            })
    }

    private fun bindMessages() {
        detachMessagesListener()

        val query = firebaseDb.getReference(conversationRootPath)
            .child("messages")
            .orderByChild("time")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messages.clear()
                snapshot.children.forEach { child ->
                    parseMessage(child)?.let { messages.add(it) }
                }

                chatAdapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) {
                    recyclerChat.scrollToPosition(messages.size - 1)
                }
                updateMessageEmptyState()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MessengerChatActivity,
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

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val rootRef = firebaseDb.getReference(conversationRootPath)
        val messageRef = rootRef.child("messages").push()
        val timestamp = System.currentTimeMillis()

        val message = hashMapOf<String, Any>(
            "text" to messageText,
            "sender" to "user",
            "senderRole" to "user",
            "senderName" to currentUserLabel,
            "senderId" to currentUserId,
            "chatType" to conversationChatType,
            "time" to timestamp
        )

        messageRef.setValue(message)
            .addOnSuccessListener {
                etMessage.setText("")

                val rootUpdates = hashMapOf<String, Any>(
                    "updatedAt" to ServerValue.TIMESTAMP,
                    "lastMessage" to message
                )

                if (conversationChannel == DIRECT_CHANNEL) {
                    rootUpdates["chatType"] = conversationChatType
                    rootUpdates["participants/$currentUserId"] = true
                    rootUpdates["participantProfiles/$currentUserId/displayName"] = currentUserLabel
                    rootUpdates["participantProfiles/$currentUserId/email"] = currentUserEmail

                    if (participantUserId.isNotBlank()) {
                        rootUpdates["participants/$participantUserId"] = true
                        rootUpdates["participantProfiles/$participantUserId/displayName"] =
                            conversationTitle
                        rootUpdates["participantProfiles/$participantUserId/email"] =
                            participantEmail
                    }
                } else {
                    rootUpdates["email"] = currentUserEmail
                    rootUpdates["userId"] = currentUserId
                    rootUpdates["displayName"] = currentUserLabel
                    rootUpdates["chatType"] = conversationChatType
                }

                rootRef.updateChildren(rootUpdates)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateMessageEmptyState() {
        if (messages.isEmpty()) {
            tvMessagesEmpty.text =
                getString(R.string.messenger_no_messages_with_name, conversationTitle)
            tvMessagesEmpty.visibility = View.VISIBLE
        } else {
            tvMessagesEmpty.visibility = View.GONE
        }
    }

    private fun parseMessage(snapshot: DataSnapshot): ChatMessage? {
        if (!snapshot.exists()) return null

        val text = snapshot.child("text").getValue(String::class.java).orEmpty()
        val sender = snapshot.child("sender").getValue(String::class.java).orEmpty()
        val senderRoleRaw = snapshot.child("senderRole").getValue(String::class.java).orEmpty()
        val senderRole = if (senderRoleRaw.isNotBlank()) senderRoleRaw else fallbackRole(sender)
        val senderNameRaw = snapshot.child("senderName").getValue(String::class.java).orEmpty()
        val senderName = if (senderNameRaw.isNotBlank()) senderNameRaw else fallbackSenderName(senderRole)
        val senderId = snapshot.child("senderId").getValue(String::class.java).orEmpty()
        val time = snapshot.child("time").getValue(Long::class.java) ?: 0L

        return ChatMessage(
            text = text,
            sender = sender,
            time = time,
            senderRole = senderRole,
            senderName = senderName,
            senderId = senderId
        )
    }

    private fun initials(value: String): String {
        val words = value.trim().split(" ").filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase(Locale.getDefault())
            words.isNotEmpty() -> words[0].take(2).uppercase(Locale.getDefault())
            else -> "CH"
        }
    }

    private fun fallbackRole(sender: String): String {
        return when (sender.trim().lowercase(Locale.getDefault())) {
            "admin" -> "admin"
            "moderator", "mod" -> "moderator"
            "superadmin", "super admin" -> "superadmin"
            "support" -> "support"
            else -> "user"
        }
    }

    private fun fallbackSenderName(role: String): String {
        return when (role.trim().lowercase(Locale.getDefault())) {
            "admin" -> "Admin"
            "moderator" -> "Moderator"
            "superadmin" -> "Super Admin"
            "support" -> getString(R.string.support_team_name)
            else -> getString(R.string.friend_label)
        }
    }
}
