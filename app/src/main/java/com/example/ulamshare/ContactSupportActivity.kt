package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
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

class ContactSupportActivity : AppCompatActivity() {

    private data class LegacyConversationConfig(
        val key: String,
        val channel: String,
        val rootName: String,
        val chatType: String,
        val title: String,
        val typeLabel: String,
        val emptyPreview: String,
        val welcomeRole: String,
        val welcomeName: String,
        val welcomeId: String,
        val welcomeText: String
    )

    companion object {
        private const val DATABASE_URL =
            "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"

        private const val SUPPORT_ROOT = "supportChats"
        private const val ADMIN_ROOT = "adminTeamChats"
        private const val DIRECT_ROOT = "directChats"

        private const val SUPPORT_CHANNEL = "support"
        private const val ADMIN_CHANNEL = "admin"
        private const val DIRECT_CHANNEL = "direct"
    }

    private lateinit var recyclerConversations: RecyclerView
    private lateinit var etConversationSearch: EditText
    private lateinit var btnCompose: ImageButton
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var tvConversationEmpty: TextView

    private lateinit var conversationAdapter: ConversationListAdapter

    private val directConversationMap = linkedMapOf<String, MessengerConversation>()
    private val sourceListeners = mutableListOf<Pair<Query, ValueEventListener>>()

    private var supportConversation: MessengerConversation? = null
    private var adminConversation: MessengerConversation? = null

    private lateinit var currentUserId: String
    private var currentUserEmail: String = ""
    private var currentUserLabel: String = "User"

    private val firebaseDb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL)
    }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_support)

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

        bindViews()
        setupRecyclerViews()
        setupActions()
        loadCurrentUserProfile()
    }

    override fun onDestroy() {
        sourceListeners.forEach { (query, listener) -> query.removeEventListener(listener) }
        sourceListeners.clear()
        super.onDestroy()
    }

    private fun bindViews() {
        recyclerConversations = findViewById(R.id.recyclerConversations)
        etConversationSearch = findViewById(R.id.etConversationSearch)
        btnCompose = findViewById(R.id.btnCompose)
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle)
        tvConversationEmpty = findViewById(R.id.tvConversationEmpty)
    }

    private fun setupRecyclerViews() {
        conversationAdapter = ConversationListAdapter { openConversation(it) }
        recyclerConversations.layoutManager = LinearLayoutManager(this)
        recyclerConversations.adapter = conversationAdapter
    }

    private fun setupActions() {
        btnCompose.setOnClickListener { openAddFriendsScreen() }
        findViewById<ImageButton>(R.id.btnMore).setOnClickListener { finish() }

        etConversationSearch.doAfterTextChanged {
            rebuildConversationList()
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
                startConversationObservers()
            }
    }

    private fun startConversationObservers() {
        observeLegacyConversation(buildSupportConfig())
        observeLegacyConversation(buildAdminConfig())
        observeDirectConversations()
    }

    private fun observeLegacyConversation(config: LegacyConversationConfig) {
        ensureLegacyConversationMetadata(config)

        val ref = firebaseDb.getReference(config.rootName).child(currentUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val conversation = buildLegacyConversation(config, snapshot)
                if (config.channel == SUPPORT_CHANNEL) {
                    supportConversation = conversation
                } else {
                    adminConversation = conversation
                }
                rebuildConversationList()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@ContactSupportActivity,
                    "Failed to load chats: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        ref.addValueEventListener(listener)
        sourceListeners += ref to listener
    }

    private fun observeDirectConversations() {
        val query = firebaseDb.getReference(DIRECT_ROOT)
            .orderByChild("participants/$currentUserId")
            .equalTo(true)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                directConversationMap.clear()
                snapshot.children.forEach { child ->
                    val conversation = buildDirectConversation(child) ?: return@forEach
                    directConversationMap[conversation.key] = conversation
                }
                rebuildConversationList()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@ContactSupportActivity,
                    "Failed to load friends: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        query.addValueEventListener(listener)
        sourceListeners += query to listener
    }

    private fun rebuildConversationList() {
        val allConversations = buildList {
            add(supportConversation ?: defaultLegacyConversation(buildSupportConfig()))
            add(adminConversation ?: defaultLegacyConversation(buildAdminConfig()))
            addAll(directConversationMap.values)
        }.sortedByDescending { it.updatedAt }

        val searchQuery = etConversationSearch.text?.toString()
            .orEmpty()
            .trim()
            .lowercase(Locale.getDefault())

        val filtered = if (searchQuery.isBlank()) {
            allConversations
        } else {
            allConversations.filter { conversation ->
                conversation.title.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    conversation.preview.lowercase(Locale.getDefault()).contains(searchQuery) ||
                    conversation.participantEmail.lowercase(Locale.getDefault()).contains(searchQuery)
            }
        }

        conversationAdapter.submitList(filtered)
        tvConversationEmpty.visibility =
            if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        tvHeaderSubtitle.text = getString(R.string.messenger_header_subtitle)
    }

    private fun openConversation(conversation: MessengerConversation) {
        startActivity(MessengerChatActivity.createIntent(this, conversation))
    }

    private fun openAddFriendsScreen() {
        startActivity(Intent(this, AddFriendsActivity::class.java))
    }

    private fun ensureLegacyConversationMetadata(config: LegacyConversationConfig) {
        val rootRef = firebaseDb.getReference(config.rootName).child(currentUserId)
        val metadata = hashMapOf<String, Any>(
            "email" to currentUserEmail,
            "userId" to currentUserId,
            "displayName" to currentUserLabel,
            "chatType" to config.chatType
        )
        rootRef.updateChildren(metadata)

        rootRef.child("messages").limitToFirst(1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) return

                    val welcomeMessage = hashMapOf<String, Any>(
                        "text" to config.welcomeText,
                        "sender" to config.welcomeRole,
                        "senderRole" to config.welcomeRole,
                        "senderName" to config.welcomeName,
                        "senderId" to config.welcomeId,
                        "chatType" to config.chatType,
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

    private fun buildLegacyConversation(
        config: LegacyConversationConfig,
        snapshot: DataSnapshot
    ): MessengerConversation {
        val lastMessage = findLastMessage(snapshot)
        val updatedAt = snapshot.child("updatedAt").getValue(Long::class.java)
            ?: lastMessage?.time
            ?: 0L

        return MessengerConversation(
            key = config.key,
            channel = config.channel,
            rootPath = "${config.rootName}/$currentUserId",
            title = config.title,
            typeLabel = config.typeLabel,
            preview = buildPreview(lastMessage, config.emptyPreview),
            updatedAt = updatedAt,
            chatType = config.chatType
        )
    }

    private fun defaultLegacyConversation(config: LegacyConversationConfig): MessengerConversation {
        return MessengerConversation(
            key = config.key,
            channel = config.channel,
            rootPath = "${config.rootName}/$currentUserId",
            title = config.title,
            typeLabel = config.typeLabel,
            preview = config.emptyPreview,
            updatedAt = 0L,
            chatType = config.chatType
        )
    }

    private fun buildDirectConversation(snapshot: DataSnapshot): MessengerConversation? {
        val conversationId = snapshot.key.orEmpty()
        if (conversationId.isBlank()) return null

        val participantIds = snapshot.child("participants").children.mapNotNull { child ->
            val value = child.getValue(Boolean::class.java)
            if (value == true) child.key else null
        }
        if (!participantIds.contains(currentUserId)) return null

        val otherUserId = participantIds.firstOrNull { it != currentUserId }.orEmpty()
        if (otherUserId.isBlank()) return null

        val profileSnapshot = snapshot.child("participantProfiles").child(otherUserId)
        val otherEmail = profileSnapshot.child("email").getValue(String::class.java).orEmpty()
        val otherName = profileSnapshot.child("displayName").getValue(String::class.java)
            .orEmpty()
            .ifBlank {
                otherEmail.substringBefore("@").ifBlank { getString(R.string.friend_label) }
            }

        val lastMessage = findLastMessage(snapshot)
        val updatedAt = snapshot.child("updatedAt").getValue(Long::class.java)
            ?: lastMessage?.time
            ?: 0L

        return MessengerConversation(
            key = directConversationKey(conversationId),
            channel = DIRECT_CHANNEL,
            rootPath = "$DIRECT_ROOT/$conversationId",
            title = otherName,
            typeLabel = getString(R.string.messenger_contact_type_friend),
            preview = buildPreview(lastMessage, getString(R.string.messenger_preview_start)),
            updatedAt = updatedAt,
            chatType = DIRECT_CHANNEL,
            participantUserId = otherUserId,
            participantEmail = otherEmail
        )
    }

    private fun buildPreview(message: ChatMessage?, fallback: String): String {
        if (message == null) return fallback
        if (message.deleted) return getString(R.string.deleted_message_label)
        if (message.text.isBlank()) return fallback

        val senderName = if (message.senderId == currentUserId) {
            getString(R.string.you_label)
        } else {
            message.senderName.ifBlank {
                fallbackSenderName(message.senderRole.ifBlank { message.sender })
            }
        }

        val body = message.text.trim().let { text ->
            if (text.length > 42) "${text.take(42).trimEnd()}..." else text
        }

        return getString(R.string.messenger_preview_format, senderName, body)
    }

    private fun findLastMessage(snapshot: DataSnapshot): ChatMessage? {
        parseMessage(snapshot.child("lastMessage"))?.let { candidate ->
            if (candidate.time != 0L || candidate.text.isNotBlank()) {
                return candidate
            }
        }

        val latestSnapshot = snapshot.child("messages").children.maxByOrNull { child ->
            child.child("time").getValue(Long::class.java) ?: 0L
        } ?: return null

        return parseMessage(latestSnapshot)
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
        val deleted = snapshot.child("deleted").getValue(Boolean::class.java) ?: false

        return ChatMessage(
            key = snapshot.key.orEmpty(),
            text = text,
            sender = sender,
            time = time,
            senderRole = senderRole,
            senderName = senderName,
            senderId = senderId,
            deleted = deleted
        )
    }

    private fun buildSupportConfig(): LegacyConversationConfig {
        return LegacyConversationConfig(
            key = "support:$currentUserId",
            channel = SUPPORT_CHANNEL,
            rootName = SUPPORT_ROOT,
            chatType = SUPPORT_CHANNEL,
            title = getString(R.string.support_team_name),
            typeLabel = getString(R.string.messenger_contact_type_support),
            emptyPreview = getString(R.string.messenger_preview_support),
            welcomeRole = "support",
            welcomeName = getString(R.string.support_team_name),
            welcomeId = "support-team",
            welcomeText = "Hi! Welcome to HopeGive Support. Tell us how we can help today."
        )
    }

    private fun buildAdminConfig(): LegacyConversationConfig {
        return LegacyConversationConfig(
            key = "admin:$currentUserId",
            channel = ADMIN_CHANNEL,
            rootName = ADMIN_ROOT,
            chatType = "admin-team",
            title = getString(R.string.admin_team_name),
            typeLabel = getString(R.string.messenger_contact_type_admin),
            emptyPreview = getString(R.string.messenger_preview_admin),
            welcomeRole = "admin",
            welcomeName = getString(R.string.admin_team_name),
            welcomeId = "admin-team",
            welcomeText = "You are now connected to the admin team. Send a message anytime."
        )
    }

    private fun buildDirectConversationId(firstUserId: String, secondUserId: String): String {
        return listOf(firstUserId, secondUserId)
            .sorted()
            .joinToString("_")
    }

    private fun directConversationKey(conversationId: String): String {
        return "direct:$conversationId"
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
