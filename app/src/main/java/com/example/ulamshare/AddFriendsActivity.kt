package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

class AddFriendsActivity : AppCompatActivity() {

    companion object {
        private const val DATABASE_URL =
            "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    private lateinit var etSearchFriends: EditText
    private lateinit var recyclerAddFriends: RecyclerView
    private lateinit var tvAddFriendsEmpty: TextView
    private lateinit var tvSectionTitle: TextView

    private lateinit var addFriendsAdapter: AddFriendsAdapter

    private val allUsers = mutableListOf<DiscoverUser>()
    private val followingIds = mutableSetOf<String>()

    private lateinit var currentUserId: String
    private var currentUserEmail: String = ""
    private var currentUserLabel: String = "User"

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseDb: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DATABASE_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_friends)

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
        setupRecycler()
        setupActions()
        loadCurrentUserProfile()
    }

    private fun bindViews() {
        etSearchFriends = findViewById(R.id.etSearchFriends)
        recyclerAddFriends = findViewById(R.id.recyclerAddFriends)
        tvAddFriendsEmpty = findViewById(R.id.tvAddFriendsEmpty)
        tvSectionTitle = findViewById(R.id.tvSectionTitle)
    }

    private fun setupRecycler() {
        addFriendsAdapter = AddFriendsAdapter(
            onPrimaryAction = { user ->
                if (user.isFollowing) {
                    openChat(user)
                } else {
                    addFriend(user)
                }
            },
            onSecondaryAction = { user ->
                confirmRemoveFriend(user)
            }
        )
        recyclerAddFriends.layoutManager = LinearLayoutManager(this)
        recyclerAddFriends.adapter = addFriendsAdapter
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        etSearchFriends.doAfterTextChanged {
            applyFilter()
        }
    }

    private fun loadCurrentUserProfile() {
        firestore.collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                val fullName = document.getString("fullName").orEmpty()
                val email = document.getString("email").orEmpty()
                val following = document.get("following") as? List<*>

                if (fullName.isNotBlank()) {
                    currentUserLabel = fullName
                }
                if (email.isNotBlank()) {
                    currentUserEmail = email
                }

                followingIds.clear()
                following?.mapNotNullTo(followingIds) { it as? String }
            }
            .addOnCompleteListener {
                loadUsers()
            }
    }

    private fun loadUsers() {
        firestore.collection("users")
            .get()
            .addOnSuccessListener { result ->
                allUsers.clear()

                result.documents.forEach { document ->
                    val uid = document.getString("uid").orEmpty().ifBlank { document.id }
                    if (uid == currentUserId) return@forEach

                    val fullName = document.getString("fullName").orEmpty()
                    val email = document.getString("email").orEmpty()
                    val displayName = fullName.ifBlank {
                        email.substringBefore("@").ifBlank { getString(R.string.friend_label) }
                    }

                    allUsers += DiscoverUser(
                        uid = uid,
                        displayName = displayName,
                        email = email,
                        isFollowing = followingIds.contains(uid)
                    )
                }

                allUsers.sortBy { it.displayName.lowercase(Locale.getDefault()) }
                applyFilter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter() {
        val query = etSearchFriends.text?.toString()
            .orEmpty()
            .trim()
            .lowercase(Locale.getDefault())

        val filtered = if (query.isBlank()) {
            allUsers
        } else {
            allUsers.filter { user ->
                user.displayName.lowercase(Locale.getDefault()).contains(query) ||
                    user.email.lowercase(Locale.getDefault()).contains(query)
            }
        }

        tvSectionTitle.text = if (query.isBlank()) {
            getString(R.string.people_you_may_know)
        } else {
            getString(R.string.add_friends_title)
        }

        addFriendsAdapter.submitList(filtered)
        tvAddFriendsEmpty.visibility =
            if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun addFriend(user: DiscoverUser) {
        val conversationId = buildDirectConversationId(currentUserId, user.uid)
        val rootRef = firebaseDb.getReference("directChats").child(conversationId)
        val updates = hashMapOf<String, Any>(
            "chatType" to "direct",
            "updatedAt" to ServerValue.TIMESTAMP,
            "participants/$currentUserId" to true,
            "participants/${user.uid}" to true,
            "participantProfiles/$currentUserId/displayName" to currentUserLabel,
            "participantProfiles/$currentUserId/email" to currentUserEmail,
            "participantProfiles/${user.uid}/displayName" to user.displayName,
            "participantProfiles/${user.uid}/email" to user.email
        )

        val chatTask = rootRef.updateChildren(updates)
        val followingTask = firestore.collection("users")
            .document(currentUserId)
            .set(mapOf("following" to FieldValue.arrayUnion(user.uid)), SetOptions.merge())
        val followerTask = firestore.collection("users")
            .document(user.uid)
            .set(mapOf("followers" to FieldValue.arrayUnion(currentUserId)), SetOptions.merge())

        Tasks.whenAll(chatTask, followingTask, followerTask)
            .addOnSuccessListener {
                followingIds += user.uid
                allUsers.find { it.uid == user.uid }?.isFollowing = true
                applyFilter()
                AppNotificationManager.notifyFriendAdded(this, user.displayName)
                Toast.makeText(this, getString(R.string.friend_added), Toast.LENGTH_SHORT).show()
                openChat(user.copy(isFollowing = true))
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.friend_request_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmRemoveFriend(user: DiscoverUser) {
        AlertDialog.Builder(this)
            .setTitle(R.string.unfriend_confirm_title)
            .setMessage(getString(R.string.unfriend_confirm_message, user.displayName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.unfriend_action) { _, _ ->
                removeFriend(user)
            }
            .show()
    }

    private fun removeFriend(user: DiscoverUser) {
        val followingTask = firestore.collection("users")
            .document(currentUserId)
            .set(mapOf("following" to FieldValue.arrayRemove(user.uid)), SetOptions.merge())
        val followerTask = firestore.collection("users")
            .document(user.uid)
            .set(mapOf("followers" to FieldValue.arrayRemove(currentUserId)), SetOptions.merge())

        Tasks.whenAll(followingTask, followerTask)
            .addOnSuccessListener {
                followingIds -= user.uid
                allUsers.find { it.uid == user.uid }?.isFollowing = false
                applyFilter()
                AppNotificationManager.notifyFriendRemoved(this, user.displayName)
                Toast.makeText(this, getString(R.string.friend_removed), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.unfriend_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun openChat(user: DiscoverUser) {
        val conversationId = buildDirectConversationId(currentUserId, user.uid)
        val conversation = MessengerConversation(
            key = "direct:$conversationId",
            channel = "direct",
            rootPath = "directChats/$conversationId",
            title = user.displayName,
            typeLabel = getString(R.string.messenger_contact_type_friend),
            preview = getString(R.string.messenger_preview_start),
            updatedAt = System.currentTimeMillis(),
            chatType = "direct",
            participantUserId = user.uid,
            participantEmail = user.email
        )

        startActivity(MessengerChatActivity.createIntent(this, conversation))
    }

    private fun buildDirectConversationId(firstUserId: String, secondUserId: String): String {
        return listOf(firstUserId, secondUserId)
            .sorted()
            .joinToString("_")
    }
}
