package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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
    private val friendIds = mutableSetOf<String>()
    private val requestedIds = mutableSetOf<String>()

    private lateinit var currentUserId: String
    private var currentUserEmail: String = ""
    private var currentUserLabel: String = "User"
    private var currentUserPhotoUrl: String = ""
    private var currentUserPhotoLocalUri: String = ""
    private var currentUserRole: String = ""
    private var currentUserStatus: String = ""

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
        currentUserLabel = PrivacyDisplayHelper.publicName(
            user.displayName,
            getString(R.string.hopegive_user)
        )

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
            onUserClick = { user ->
                PublicProfileActivity.start(this, user.uid)
            },
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
                currentUserPhotoUrl = document.getString("profilePhotoUrl").orEmpty()
                currentUserPhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty()
                currentUserRole = document.getString("role").orEmpty()
                currentUserStatus = document.getString("status").orEmpty()

                val publicFullName = PrivacyDisplayHelper.publicName(fullName, "")
                if (publicFullName.isNotBlank()) {
                    currentUserLabel = publicFullName
                }
                if (email.isNotBlank()) {
                    currentUserEmail = email
                }
            }
            .addOnCompleteListener {
                loadFriendIds()
            }
    }

    private fun loadFriendIds() {
        firestore.collection("users")
            .document(currentUserId)
            .collection("friends")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    friendIds.clear()
                    snapshot.documents.mapNotNullTo(friendIds) { document ->
                        document.getString("userId").orEmpty().ifBlank { document.id }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.w("AddFriendsActivity", "Unable to load friends subcollection", error)
            }
            .addOnCompleteListener {
                loadPendingFriendRequests()
            }
    }

    private fun loadPendingFriendRequests() {
        firestore.collection("friend_requests")
            .whereEqualTo("fromUserId", currentUserId)
            .whereEqualTo("status", FollowRepository.FRIEND_REQUEST_PENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                requestedIds.clear()
                snapshot.documents.mapNotNullTo(requestedIds) { document ->
                    document.getString("toUserId").orEmpty()
                }
            }
            .addOnFailureListener { error ->
                Log.w("AddFriendsActivity", "Unable to load outgoing friend requests", error)
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

                allUsers += dedupeUserDocuments(result.documents).map { document ->
                    mapDiscoverUser(document)
                }

                allUsers.sortBy { it.displayName.lowercase(Locale.getDefault()) }
                applyFilter()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
    }

    private fun dedupeUserDocuments(documents: List<DocumentSnapshot>): List<DocumentSnapshot> {
        val seenUids = mutableSetOf<String>()
        val seenEmails = mutableSetOf<String>()
        return documents
            .filterNot { it.getBoolean("isDuplicate") == true }
            .sortedWith(
                compareByDescending<DocumentSnapshot> { profileScore(it) }
                    .thenBy { publicNameFromDocument(it).lowercase(Locale.getDefault()) }
            )
            .mapNotNull { document ->
                val uid = document.getString("uid").orEmpty().ifBlank { document.id }
                val emailKey = normalizeEmail(document.getString("email"))
                if (uid.isBlank() || uid == currentUserId) return@mapNotNull null
                if (seenUids.contains(uid) || (emailKey.isNotBlank() && seenEmails.contains(emailKey))) {
                    Log.d("AddFriendsActivity", "Skipping duplicate user document id=${document.id} uid=$uid")
                    return@mapNotNull null
                }
                seenUids += uid
                if (emailKey.isNotBlank()) seenEmails += emailKey
                document
            }
    }

    private fun mapDiscoverUser(document: DocumentSnapshot): DiscoverUser {
        val uid = document.getString("uid").orEmpty().ifBlank { document.id }
        return DiscoverUser(
            uid = uid,
            displayName = publicNameFromDocument(document),
            email = document.getString("email").orEmpty(),
            profilePhotoUrl = document.getString("profilePhotoUrl").orEmpty(),
            profilePhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty(),
            role = document.getString("role").orEmpty().ifBlank { document.getString("roleKey").orEmpty() },
            status = document.getString("status").orEmpty(),
            isFollowing = friendIds.contains(uid),
            isRequested = requestedIds.contains(uid)
        )
    }

    private fun publicNameFromDocument(document: DocumentSnapshot): String {
        return listOf(
            document.getString("fullName").orEmpty(),
            document.getString("displayName").orEmpty(),
            document.getString("name").orEmpty(),
            getString(R.string.hopegive_user)
        ).firstNotNullOf { candidate ->
            PrivacyDisplayHelper.publicName(candidate, "").takeIf { it.isNotBlank() }
        }
    }

    private fun profileScore(document: DocumentSnapshot): Int {
        val uid = document.getString("uid").orEmpty().ifBlank { document.id }
        val hasName = publicNameFromDocument(document) != getString(R.string.hopegive_user)
        val hasPhoto = document.getString("profilePhotoUrl").orEmpty().isNotBlank() ||
            document.getString("profilePhotoLocalUri").orEmpty().isNotBlank()
        return listOf(
            if (document.id == uid) 100 else 0,
            if (hasName) 40 else 0,
            if (hasPhoto) 10 else 0,
            document.getTimestamp("updatedAt")?.toDate()?.time?.coerceAtMost(99L)?.toInt() ?: 0
        ).sum()
    }

    private fun normalizeEmail(value: String?): String {
        return value.orEmpty().trim().lowercase(Locale.US)
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
                    user.role.lowercase(Locale.getDefault()).contains(query) ||
                    user.status.lowercase(Locale.getDefault()).contains(query)
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
        FollowRepository.sendFriendRequest(
            firestore = firestore,
            currentUser = FollowProfile(
                uid = currentUserId,
                fullName = currentUserLabel,
                email = currentUserEmail,
                profilePhotoUrl = currentUserPhotoUrl,
                profilePhotoLocalUri = currentUserPhotoLocalUri,
                role = currentUserRole,
                status = currentUserStatus
            ),
            targetUser = FollowProfile(
                uid = user.uid,
                fullName = user.displayName,
                email = user.email,
                profilePhotoUrl = user.profilePhotoUrl,
                profilePhotoLocalUri = user.profilePhotoLocalUri,
                role = user.role,
                status = user.status
            )
        ) { result ->
            result
                .onSuccess {
                    requestedIds += user.uid
                    allUsers.find { it.uid == user.uid }?.isRequested = true
                    applyFilter()
                    Toast.makeText(this, getString(R.string.friend_request_sent), Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e("AddFriendsActivity", "Unable to send friend request", error)
                    Toast.makeText(this, getString(R.string.friend_request_failed), Toast.LENGTH_SHORT).show()
                }
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
        FollowRepository.unfriend(
            firestore = firestore,
            currentUserId = currentUserId,
            targetUserId = user.uid
        ) { result ->
            result
                .onSuccess {
                    friendIds -= user.uid
                    allUsers.find { it.uid == user.uid }?.isFollowing = false
                    applyFilter()
                    AppNotificationManager.notifyFriendRemoved(this, user.displayName)
                    Toast.makeText(this, getString(R.string.friend_removed), Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e("AddFriendsActivity", "Unable to unfollow user", error)
                    Toast.makeText(this, getString(R.string.unfriend_failed), Toast.LENGTH_SHORT).show()
                }
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
