package com.example.ulamshare

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import android.view.animation.LinearInterpolator
import kotlin.math.roundToInt

class MessengerChatActivity : AppCompatActivity(), ChatAdapter.MessageInteractionListener {

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
        private const val SUPPORT_ROOT = "supportChats"
        private const val TYPING_IDLE_MS = 2500L
        private const val TYPING_STALE_MS = 5000L
        private const val TYPING_REFRESH_MS = 1000L

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
    private lateinit var replyPreviewContainer: View
    private lateinit var tvReplyPreviewSender: TextView
    private lateinit var tvReplyPreviewText: TextView
    private lateinit var btnReplyPreviewClose: ImageButton
    private lateinit var faqQuickActionsScroll: HorizontalScrollView
    private lateinit var faqQuickActionsContainer: LinearLayout
    private lateinit var typingIndicatorContainer: View
    private lateinit var typingDots: List<View>

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var replyTargetMessage: ChatMessage? = null

    private var activeMessagesQuery: Query? = null
    private var activeMessagesListener: ValueEventListener? = null
    private var activeTypingRef: DatabaseReference? = null
    private var activeTypingListener: ValueEventListener? = null

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

    private val isBotConversation: Boolean
        get() = conversationChannel == HopeGiveAssistantBot.CHANNEL ||
            conversationChatType == HopeGiveAssistantBot.CHANNEL

    private val firebaseDb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL)
    }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val uiHandler = Handler(Looper.getMainLooper())
    private val remoteTypingEntries = linkedMapOf<String, TypingPresence>()
    private var typingAnimatorSet: AnimatorSet? = null
    private var hasPublishedTyping = false
    private val stopTypingRunnable = Runnable { publishTypingState(false) }
    private val staleTypingRefreshRunnable = object : Runnable {
        override fun run() {
            renderTypingIndicator()
            if (hasActiveRemoteTyping()) {
                uiHandler.postDelayed(this, TYPING_REFRESH_MS)
            }
        }
    }
    private val typingWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            handleTypingInputChanged(s?.toString().orEmpty())
        }
    }

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
        currentUserLabel = PrivacyDisplayHelper.publicName(
            user.displayName,
            getString(R.string.hopegive_user)
        )

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
        clearTypingPresence()
        detachMessagesListener()
        detachTypingListener()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        clearTypingPresence()
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
        replyPreviewContainer = findViewById(R.id.replyPreviewContainer)
        tvReplyPreviewSender = findViewById(R.id.tvReplyPreviewSender)
        tvReplyPreviewText = findViewById(R.id.tvReplyPreviewText)
        btnReplyPreviewClose = findViewById(R.id.btnReplyPreviewClose)
        faqQuickActionsScroll = findViewById(R.id.faqQuickActionsScroll)
        faqQuickActionsContainer = findViewById(R.id.faqQuickActionsContainer)
        typingIndicatorContainer = findViewById(R.id.typingIndicatorContainer)
        typingDots = listOf(
            findViewById(R.id.typingDot1),
            findViewById(R.id.typingDot2),
            findViewById(R.id.typingDot3)
        )
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatAdapter = ChatAdapter(
            messages = messages,
            currentUserId = currentUserId,
            interactionListener = this,
            actionsEnabled = !isBotConversation
        )
        recyclerChat.layoutManager = layoutManager
        recyclerChat.adapter = chatAdapter
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnSend.setOnClickListener { sendMessage() }
        btnReplyPreviewClose.setOnClickListener { clearReplyTarget() }

        etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
        etMessage.addTextChangedListener(typingWatcher)
    }

    private fun loadCurrentUserProfile() {
        firestore.collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                val fullName = PrivacyDisplayHelper.publicName(document.getString("fullName").orEmpty(), "")
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
        if (isBotConversation) {
            etMessage.hint = getString(R.string.messenger_bot_input_hint)
            bindBotQuickActions()
        } else {
            etMessage.hint = getString(R.string.message_contact_hint, conversationTitle)
            faqQuickActionsScroll.visibility = View.GONE
        }
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

            HopeGiveAssistantBot.CHANNEL -> {
                // Local FAQ assistant. Firestore/Realtime Database can be added later for editable FAQs.
            }
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
        detachTypingListener()

        if (isBotConversation) {
            updateTypingIndicatorVisibility(false)
            bindBotMessages()
            return
        }

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
        bindTypingPresence()
    }

    private fun bindBotMessages() {
        messages.clear()
        messages.add(
            buildBotMessage(
                text = getString(R.string.messenger_bot_welcome),
                key = "bot_welcome"
            )
        )
        chatAdapter.notifyDataSetChanged()
        recyclerChat.scrollToPosition(messages.size - 1)
        updateMessageEmptyState()
    }

    private fun bindBotQuickActions() {
        faqQuickActionsContainer.removeAllViews()
        HopeGiveAssistantBot.quickQuestions.forEach { question ->
            addBotQuickAction(question) {
                handleBotQuestion(question)
            }
        }
        addBotQuickAction(getString(R.string.messenger_bot_talk_to_support)) {
            openSupportConversation()
        }
        faqQuickActionsScroll.visibility = View.VISIBLE
    }

    private fun addBotQuickAction(label: String, onClick: () -> Unit) {
        val chip = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MessengerChatActivity, android.R.color.white))
            background = ContextCompat.getDrawable(this@MessengerChatActivity, R.drawable.bg_support_chip_active)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setOnClickListener { onClick() }
        }

        faqQuickActionsContainer.addView(
            chip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
        )
    }

    private fun handleBotQuestion(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) return

        appendLocalMessage(buildUserMessage(cleanQuestion))
        etMessage.setText("")
        clearReplyTarget()

        val answer = HopeGiveAssistantBot.answerFor(cleanQuestion)
            ?: getString(R.string.messenger_bot_unknown)
        appendLocalMessage(buildBotMessage(answer))
    }

    private fun appendLocalMessage(message: ChatMessage) {
        messages.add(message)
        chatAdapter.notifyItemInserted(messages.size - 1)
        recyclerChat.scrollToPosition(messages.size - 1)
        updateMessageEmptyState()
    }

    private fun buildUserMessage(text: String): ChatMessage {
        return ChatMessage(
            key = "local_user_${System.currentTimeMillis()}_${messages.size}",
            text = text,
            sender = "user",
            time = System.currentTimeMillis(),
            senderRole = "user",
            senderName = currentUserLabel,
            senderId = currentUserId
        )
    }

    private fun buildBotMessage(
        text: String,
        key: String = "local_bot_${System.currentTimeMillis()}_${messages.size}"
    ): ChatMessage {
        return ChatMessage(
            key = key,
            text = text,
            sender = "bot",
            time = System.currentTimeMillis(),
            senderRole = "support",
            senderName = getString(R.string.messenger_bot_name),
            senderId = HopeGiveAssistantBot.SENDER_ID
        )
    }

    private fun openSupportConversation() {
        val supportConversation = MessengerConversation(
            key = "support:$currentUserId",
            channel = SUPPORT_CHANNEL,
            rootPath = "$SUPPORT_ROOT/$currentUserId",
            title = getString(R.string.support_team_name),
            typeLabel = getString(R.string.messenger_contact_type_support),
            preview = getString(R.string.messenger_preview_support),
            updatedAt = 0L,
            chatType = SUPPORT_CHANNEL
        )
        startActivity(createIntent(this, supportConversation))
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

    private fun bindTypingPresence() {
        detachTypingListener()

        val ref = firebaseDb.getReference(conversationRootPath).child("typing")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                remoteTypingEntries.clear()
                snapshot.children.forEach { child ->
                    val userId = child.child("userId").getValue(String::class.java).orEmpty()
                        .ifBlank { child.key.orEmpty() }
                    if (userId.isBlank()) return@forEach

                    remoteTypingEntries[userId] = TypingPresence(
                        userId = userId,
                        userName = child.child("userName").getValue(String::class.java).orEmpty(),
                        isTyping = child.child("isTyping").getValue(Boolean::class.java) ?: false,
                        updatedAt = child.child("updatedAt").getValue(Long::class.java) ?: 0L
                    )
                }
                renderTypingIndicator()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("MessengerTyping", "Typing listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        activeTypingRef = ref
        activeTypingListener = listener
    }

    private fun detachTypingListener() {
        val ref = activeTypingRef
        val listener = activeTypingListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        activeTypingRef = null
        activeTypingListener = null
        remoteTypingEntries.clear()
        uiHandler.removeCallbacks(staleTypingRefreshRunnable)
        if (::typingIndicatorContainer.isInitialized) {
            updateTypingIndicatorVisibility(false)
        }
    }

    private fun handleTypingInputChanged(value: String) {
        if (isBotConversation || conversationRootPath.isBlank()) return

        val hasText = value.trim().isNotEmpty()
        if (!hasText) {
            clearTypingPresence()
            return
        }

        if (!hasPublishedTyping) {
            publishTypingState(true)
        }
        uiHandler.removeCallbacks(stopTypingRunnable)
        uiHandler.postDelayed(stopTypingRunnable, TYPING_IDLE_MS)
    }

    private fun publishTypingState(isTyping: Boolean) {
        if (isBotConversation || conversationRootPath.isBlank() || currentUserId.isBlank()) return
        if (hasPublishedTyping == isTyping && !isTyping) return

        val typingRef = firebaseDb.getReference(conversationRootPath)
            .child("typing")
            .child(currentUserId)
        val payload = hashMapOf<String, Any>(
            "userId" to currentUserId,
            "userName" to currentUserLabel,
            "isTyping" to isTyping,
            "updatedAt" to System.currentTimeMillis()
        )
        typingRef.setValue(payload)
            .addOnFailureListener { error ->
                Log.w("MessengerTyping", "Failed to update typing state", error)
            }
        hasPublishedTyping = isTyping
    }

    private fun clearTypingPresence() {
        uiHandler.removeCallbacks(stopTypingRunnable)
        publishTypingState(false)
    }

    private fun renderTypingIndicator() {
        val now = System.currentTimeMillis()
        val activeEntry = remoteTypingEntries.values
            .filter { entry ->
                entry.userId != currentUserId &&
                    entry.isTyping &&
                    now - entry.updatedAt <= TYPING_STALE_MS
            }
            .maxByOrNull { it.updatedAt }

        updateTypingIndicatorVisibility(activeEntry != null)

        uiHandler.removeCallbacks(staleTypingRefreshRunnable)
        if (activeEntry != null) {
            uiHandler.postDelayed(staleTypingRefreshRunnable, TYPING_REFRESH_MS)
        }
    }

    private fun hasActiveRemoteTyping(): Boolean {
        val now = System.currentTimeMillis()
        return remoteTypingEntries.values.any { entry ->
            entry.userId != currentUserId &&
                entry.isTyping &&
                now - entry.updatedAt <= TYPING_STALE_MS
        }
    }

    private fun updateTypingIndicatorVisibility(isVisible: Boolean) {
        if (!::typingIndicatorContainer.isInitialized) return
        typingIndicatorContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
        if (isVisible) {
            startTypingAnimation()
        } else {
            stopTypingAnimation()
        }
    }

    private fun startTypingAnimation() {
        if (typingAnimatorSet?.isRunning == true) return

        val animators = typingDots.flatMapIndexed { index, dot ->
            listOf(
                ObjectAnimator.ofFloat(dot, View.ALPHA, 0.35f, 1f, 0.35f).apply {
                    duration = 900L
                    startDelay = index * 160L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                },
                ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.9f, 1.15f, 0.9f).apply {
                    duration = 900L
                    startDelay = index * 160L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                },
                ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.9f, 1.15f, 0.9f).apply {
                    duration = 900L
                    startDelay = index * 160L
                    repeatCount = ObjectAnimator.INFINITE
                    interpolator = LinearInterpolator()
                }
            )
        }

        typingAnimatorSet = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    private fun stopTypingAnimation() {
        typingAnimatorSet?.cancel()
        typingAnimatorSet = null
        typingDots.forEach { dot ->
            dot.alpha = 0.65f
            dot.scaleX = 1f
            dot.scaleY = 1f
        }
    }

    private fun sendMessage() {
        val messageText = etMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (isBotConversation) {
            handleBotQuestion(messageText)
            return
        }

        clearTypingPresence()

        val rootRef = firebaseDb.getReference(conversationRootPath)
        val messageRef = rootRef.child("messages").push()
        val timestamp = System.currentTimeMillis()

        val replyTarget = replyTargetMessage
        val message = hashMapOf<String, Any>(
            "text" to messageText,
            "sender" to "user",
            "senderRole" to "user",
            "senderName" to currentUserLabel,
            "senderId" to currentUserId,
            "chatType" to conversationChatType,
            "time" to timestamp
        )
        if (replyTarget != null) {
            message["replyTo"] = replyTarget.key
            message["replyText"] = replyTarget.text.ifBlank {
                getString(R.string.deleted_message_label)
            }
            message["replySenderName"] = resolveReplySenderLabel(replyTarget)
            message["replySenderRole"] = replyTarget.senderRole.ifBlank { replyTarget.sender }
        }

        messageRef.setValue(message)
            .addOnSuccessListener {
                etMessage.setText("")
                clearReplyTarget()

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
        val senderName = PrivacyDisplayHelper.publicName(
            senderNameRaw,
            fallbackSenderName(senderRole)
        )
        val senderId = snapshot.child("senderId").getValue(String::class.java).orEmpty()
        val time = snapshot.child("time").getValue(Long::class.java) ?: 0L
        val deleted = snapshot.child("deleted").getValue(Boolean::class.java) ?: false
        val replyTo = snapshot.child("replyTo").getValue(String::class.java).orEmpty()
        val replyText = snapshot.child("replyText").getValue(String::class.java)
            ?: snapshot.child("replyToText").getValue(String::class.java).orEmpty()
        val replySenderNameRaw = snapshot.child("replySenderName").getValue(String::class.java)
            ?: snapshot.child("replyToSenderName").getValue(String::class.java).orEmpty()
        val replySenderName = PrivacyDisplayHelper.publicName(replySenderNameRaw, "")
        val replySenderRole = snapshot.child("replySenderRole").getValue(String::class.java)
            ?: snapshot.child("replyToSenderRole").getValue(String::class.java).orEmpty()
        val reactions = snapshot.child("reactions").children
            .mapNotNull { reactionSnapshot ->
                val actorKey = reactionSnapshot.key.orEmpty()
                if (actorKey.isBlank()) return@mapNotNull null

                val emoji = reactionSnapshot.child("emoji").getValue(String::class.java)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { reactionSnapshot.getValue(String::class.java).orEmpty().trim() }
                if (emoji.isBlank()) return@mapNotNull null

                actorKey to ChatReactionEntry(
                    emoji = emoji,
                    by = reactionSnapshot.child("by").getValue(String::class.java)
                        .orEmpty()
                        .ifBlank { actorKey },
                    role = reactionSnapshot.child("role").getValue(String::class.java).orEmpty(),
                    time = reactionSnapshot.child("time").getValue(Long::class.java) ?: 0L
                )
            }
            .toMap()

        return ChatMessage(
            key = snapshot.key.orEmpty(),
            text = text,
            sender = sender,
            time = time,
            senderRole = senderRole,
            senderName = senderName,
            senderId = senderId,
            deleted = deleted,
            replyTo = replyTo,
            replyText = replyText,
            replySenderName = replySenderName,
            replySenderRole = replySenderRole,
            reactions = reactions
        )
    }

    override fun onMessageActionsRequested(anchor: View, message: ChatMessage) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menu.add(0, 1, 0, getString(R.string.react_action))
        popupMenu.menu.add(0, 2, 1, getString(R.string.reply_action))
        popupMenu.menu.add(0, 3, 2, getString(R.string.delete_action))
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showReactionPicker(anchor, message)
                    true
                }
                2 -> {
                    setReplyTarget(message)
                    true
                }
                3 -> {
                    confirmDeleteMessage(message)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    override fun onReactionTapped(message: ChatMessage, reactionType: String) {
        toggleReaction(message, reactionType)
    }

    private fun showReactionPicker(anchor: View, message: ChatMessage) {
        val popupMenu = PopupMenu(this, anchor)
        MessageReactionUi.quickReactionOrder.forEachIndexed { index, reactionType ->
            popupMenu.menu.add(0, index, index, MessageReactionUi.displayLabel(reactionType))
        }
        popupMenu.setOnMenuItemClickListener { item ->
            val reactionType = MessageReactionUi.quickReactionOrder.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            toggleReaction(message, reactionType)
            true
        }
        popupMenu.show()
    }

    private fun toggleReaction(message: ChatMessage, reactionType: String) {
        if (message.key.isBlank()) return

        val actorKey = MessageReactionUi.actorKey(currentUserId)
        val messageRef = firebaseDb.getReference(conversationRootPath)
            .child("messages")
            .child(message.key)
        val reactionRef = messageRef.child("reactions").child(actorKey)
        val existingReaction = message.reactions[actorKey]?.emoji?.trim().orEmpty().ifBlank {
            message.reactions.values.firstOrNull { it.by == actorKey }?.emoji?.trim().orEmpty()
        }

        if (existingReaction == reactionType) {
            reactionRef.removeValue()
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update reaction", Toast.LENGTH_SHORT).show()
                }
            return
        }

        reactionRef.setValue(
            hashMapOf<String, Any>(
                "emoji" to reactionType,
                "by" to actorKey,
                "role" to "user",
                "time" to ServerValue.TIMESTAMP
            )
        ).addOnFailureListener {
            Toast.makeText(this, "Failed to update reaction", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setReplyTarget(message: ChatMessage) {
        replyTargetMessage = message
        replyPreviewContainer.visibility = View.VISIBLE
        tvReplyPreviewSender.text = resolveReplySenderLabel(message)
        tvReplyPreviewText.text = message.text.ifBlank { getString(R.string.deleted_message_label) }
        etMessage.requestFocus()
    }

    private fun clearReplyTarget() {
        replyTargetMessage = null
        replyPreviewContainer.visibility = View.GONE
        tvReplyPreviewSender.text = ""
        tvReplyPreviewText.text = ""
    }

    private fun resolveReplySenderLabel(message: ChatMessage): String {
        if (message.senderId == currentUserId) return getString(R.string.you_label)

        if (message.senderName.isNotBlank()) return message.senderName
        if (message.replySenderName.isNotBlank()) return message.replySenderName

        return fallbackSenderName(message.senderRole.ifBlank { message.sender })
    }

    private fun confirmDeleteMessage(message: ChatMessage) {
        if (message.senderId != currentUserId) {
            Toast.makeText(this, getString(R.string.delete_own_messages_only), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_message_title)
            .setMessage(R.string.delete_message_confirm)
            .setNegativeButton(R.string.cancel_action, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                deleteMessage(message)
            }
            .show()
    }

    private fun deleteMessage(message: ChatMessage) {
        if (message.key.isBlank()) return

        val rootRef = firebaseDb.getReference(conversationRootPath)
        val messageRef = rootRef.child("messages").child(message.key)
        val updates = hashMapOf<String, Any>(
            "deleted" to true,
            "deletedAt" to ServerValue.TIMESTAMP,
            "text" to ""
        )

        messageRef.updateChildren(updates)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete message", Toast.LENGTH_SHORT).show()
            }

        if (messages.lastOrNull()?.key == message.key) {
            rootRef.child("lastMessage")
                .updateChildren(updates)
        }

        if (replyTargetMessage?.key == message.key) {
            clearReplyTarget()
        }
    }

    private fun initials(value: String): String {
        if (value.equals(getString(R.string.messenger_bot_name), ignoreCase = true)) return "HG"

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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private data class TypingPresence(
        val userId: String,
        val userName: String,
        val isTyping: Boolean,
        val updatedAt: Long
    )
}
