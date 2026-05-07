package com.example.ulamshare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class FollowListActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var stateView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FollowListAdapter

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var ownerUserId: String = ""
    private var mode: String = MODE_ALL_USERS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)

        ownerUserId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        mode = normalizeMode(intent.getStringExtra(EXTRA_MODE).orEmpty())
        Log.d(TAG, "mode=$mode")

        titleView = findViewById(R.id.tvFollowListTitle)
        subtitleView = findViewById(R.id.tvFollowListSubtitle)
        stateView = findViewById(R.id.tvFollowListState)
        progressView = findViewById(R.id.progressFollowList)
        recyclerView = findViewById(R.id.rvFollowList)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        titleView.text = titleForMode()
        subtitleView.text = subtitleForMode()

        adapter = FollowListAdapter(
            onUserClick = { user ->
                openUserProfile(user.userId)
            },
            onUnfollowClick = { user ->
                if (mode == MODE_FRIENDS) {
                    confirmUnfriend(user)
                } else {
                    confirmUnfollow(user)
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadUserList()
    }

    private fun loadUserList() {
        when (mode) {
            MODE_ALL_USERS -> loadAllUsers()
            MODE_FRIENDS -> loadRelationshipUsers(FRIENDS_COLLECTION)
            MODE_FOLLOWERS -> loadRelationshipUsers(FOLLOWERS_COLLECTION)
            MODE_FOLLOWING -> loadRelationshipUsers(FOLLOWING_COLLECTION)
            else -> loadAllUsers()
        }
    }

    private fun loadRelationshipUsers(collectionName: String) {
        if (ownerUserId.isBlank()) {
            showEmptyState()
            return
        }

        showLoading()
        val relationshipPath = "users/$ownerUserId/$collectionName"
        if (collectionName == FRIENDS_COLLECTION) {
            Log.d(TAG, "Loading friends from $relationshipPath")
        } else {
            Log.d(TAG, "Loading $collectionName from $relationshipPath")
        }

        firestore.collection(USERS_COLLECTION)
            .document(ownerUserId)
            .collection(collectionName)
            .get()
            .addOnSuccessListener { snapshot ->
                val relationshipDocs = snapshot.documents
                if (relationshipDocs.isEmpty()) {
                    if (collectionName == FRIENDS_COLLECTION) {
                        Log.d(TAG, "Friends shown count=0")
                    }
                    showEmptyState()
                    return@addOnSuccessListener
                }

                val profileTasks = relationshipDocs.mapNotNull { relationship ->
                    val userId = relationshipUserId(relationship)
                    if (userId.isBlank()) {
                        null
                    } else {
                        firestore.collection(USERS_COLLECTION).document(userId).get()
                    }
                }
                if (profileTasks.isEmpty()) {
                    showEmptyState()
                    return@addOnSuccessListener
                }

                Tasks.whenAllSuccess<DocumentSnapshot>(profileTasks)
                    .addOnSuccessListener { profileDocs ->
                        val profilesById = profileDocs.associateBy { document ->
                            document.getString("uid").orEmpty().ifBlank { document.id }
                        }
                        val orderField = if (mode == MODE_FRIENDS) "friendedAt" else "followedAt"
                        val users = relationshipDocs
                            .sortedByDescending { relationshipTimeMillis(it, orderField) }
                            .mapNotNull { relationship ->
                                val userId = relationshipUserId(relationship)
                                if (userId.isBlank()) {
                                    null
                                } else {
                                    mapRelationshipUser(relationship, profilesById[userId])
                                }
                            }
                            .distinctBy { it.userId }
                        if (collectionName == FRIENDS_COLLECTION) {
                            Log.d(TAG, "Friends shown count=${users.size}")
                        }
                        showUsers(users)
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "Unable to hydrate $collectionName profiles", error)
                        val orderField = if (mode == MODE_FRIENDS) "friendedAt" else "followedAt"
                        val fallbackUsers = relationshipDocs
                            .sortedByDescending { relationshipTimeMillis(it, orderField) }
                            .mapNotNull { relationship ->
                                val userId = relationshipUserId(relationship)
                                if (userId.isBlank()) null else mapRelationshipUser(relationship, null)
                            }
                            .distinctBy { it.userId }
                        if (collectionName == FRIENDS_COLLECTION) {
                            Log.d(TAG, "Friends shown count=${fallbackUsers.size}")
                        }
                        showUsers(fallbackUsers)
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to load $collectionName list", error)
                progressView.visibility = View.GONE
                recyclerView.visibility = View.GONE
                stateView.visibility = View.VISIBLE
                stateView.text = getString(R.string.follow_list_load_failed)
            }
    }

    private fun loadAllUsers() {
        showLoading()
        Log.d(TAG, "Loading all users from users collection")
        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "Raw users count=${snapshot.size()}")
                val users = dedupeProfileDocuments(snapshot.documents)
                    .map(::mapProfileUser)
                    .sortedBy { it.fullName.lowercase() }

                progressView.visibility = View.GONE
                Log.d(TAG, "Shown users count=${users.size}")
                if (users.isEmpty()) {
                    showEmptyState()
                } else {
                    subtitleView.text = subtitleForMode()
                    stateView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(users, canUnfollow = false)
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to load users", error)
                showEmptyState()
            }
    }

    private fun showUsers(users: List<FollowListUser>) {
        progressView.visibility = View.GONE
        adapter.submitList(users, canUnfollowFromThisList())
        if (users.isEmpty()) {
            showEmptyState()
        } else {
            subtitleView.text = subtitleForMode()
            stateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun canUnfollowFromThisList(): Boolean {
        return (mode == MODE_FOLLOWING || mode == MODE_FRIENDS) && auth.currentUser?.uid == ownerUserId
    }

    private fun confirmUnfollow(user: FollowListUser) {
        AlertDialog.Builder(this)
            .setTitle(R.string.unfriend_confirm_title)
            .setMessage(getString(R.string.unfriend_confirm_message, user.fullName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.unfriend_action) { _, _ ->
                FollowRepository.unfollow(
                    firestore = firestore,
                    currentUserId = ownerUserId,
                    targetUserId = user.userId
                ) { result ->
                    result
                        .onSuccess {
                            Toast.makeText(this, R.string.friend_removed, Toast.LENGTH_SHORT).show()
                            loadUserList()
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Unable to unfollow from list", error)
                            Toast.makeText(this, R.string.unfollow_failed, Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .show()
    }

    private fun confirmUnfriend(user: FollowListUser) {
        AlertDialog.Builder(this)
            .setTitle(R.string.unfriend_confirm_title)
            .setMessage(getString(R.string.unfriend_confirm_message, user.fullName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.unfriend_action) { _, _ ->
                FollowRepository.unfriend(
                    firestore = firestore,
                    currentUserId = ownerUserId,
                    targetUserId = user.userId
                ) { result ->
                    result
                        .onSuccess {
                            Toast.makeText(this, R.string.friend_removed, Toast.LENGTH_SHORT).show()
                            loadUserList()
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Unable to unfriend from list", error)
                            Toast.makeText(this, R.string.unfriend_failed, Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .show()
    }

    private fun showLoading() {
        progressView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        stateView.visibility = View.VISIBLE
        stateView.text = getString(R.string.follow_list_loading)
        subtitleView.text = subtitleForMode()
    }

    private fun showEmptyState() {
        progressView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        stateView.visibility = View.VISIBLE
        stateView.text = when (mode) {
            MODE_ALL_USERS -> getString(R.string.add_friends_empty_state)
            MODE_FOLLOWERS -> getString(R.string.no_followers_yet)
            MODE_FRIENDS -> getString(R.string.no_friends_yet)
            else -> getString(R.string.no_following_yet)
        }
    }

    private fun titleForMode(): String {
        return when (mode) {
            MODE_ALL_USERS -> getString(R.string.add_friends_title)
            MODE_FOLLOWERS -> getString(R.string.followers_title)
            MODE_FRIENDS -> getString(R.string.friends_title)
            else -> getString(R.string.following_title)
        }
    }

    private fun subtitleForMode(): String {
        return when (mode) {
            MODE_ALL_USERS -> getString(R.string.add_friends_subtitle)
            MODE_FRIENDS -> getString(R.string.friends_profile_subtitle)
            else -> getString(R.string.follow_list_subtitle)
        }
    }

    private fun relationshipUserId(document: DocumentSnapshot): String {
        return document.getString("userId").orEmpty().ifBlank { document.id }
    }

    private fun mapRelationshipUser(
        relationship: DocumentSnapshot,
        profile: DocumentSnapshot?
    ): FollowListUser {
        val userId = relationshipUserId(relationship)
        val fullName = listOf(
            relationship.getString("fullName").orEmpty(),
            relationship.getString("displayName").orEmpty(),
            profile?.getString("fullName").orEmpty(),
            profile?.getString("displayName").orEmpty(),
            getString(R.string.hopegive_user)
        ).firstNotNullOf { candidate ->
            PrivacyDisplayHelper.publicName(candidate, "").takeIf { it.isNotBlank() }
        }
        return FollowListUser(
            userId = userId,
            fullName = fullName,
            profilePhotoUrl = relationship.getString("profilePhotoUrl").orEmpty()
                .ifBlank { profile?.getString("profilePhotoUrl").orEmpty() },
            profilePhotoLocalUri = relationship.getString("profilePhotoLocalUri").orEmpty()
                .ifBlank { profile?.getString("profilePhotoLocalUri").orEmpty() },
            role = relationship.getString("role").orEmpty()
                .ifBlank { profile?.getString("role").orEmpty() }
                .ifBlank { profile?.getString("roleKey").orEmpty() },
            status = relationship.getString("status").orEmpty()
                .ifBlank { profile?.getString("status").orEmpty() }
        )
    }

    private fun mapProfileUser(document: DocumentSnapshot): FollowListUser {
        val userId = canonicalUserId(document)
        val fullName = listOf(
            document.getString("fullName").orEmpty(),
            document.getString("displayName").orEmpty(),
            document.getString("username").orEmpty(),
            getString(R.string.hopegive_user)
        ).firstNotNullOf { candidate ->
            PrivacyDisplayHelper.publicName(candidate, "").takeIf { it.isNotBlank() }
        }
        return FollowListUser(
            userId = userId,
            fullName = fullName,
            profilePhotoUrl = document.getString("profilePhotoUrl").orEmpty(),
            profilePhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty(),
            role = document.getString("role").orEmpty().ifBlank { document.getString("roleKey").orEmpty() },
            status = document.getString("status").orEmpty()
        )
    }

    private fun dedupeProfileDocuments(documents: List<DocumentSnapshot>): List<DocumentSnapshot> {
        val seenUids = mutableSetOf<String>()
        val seenEmails = mutableSetOf<String>()
        return documents
            .filter(::isRealUserDocument)
            .sortedWith(
                compareByDescending<DocumentSnapshot> { profileScore(it) }
                    .thenByDescending { it.getTimestamp("updatedAt")?.toDate()?.time ?: 0L }
                    .thenBy { publicNameFromDocument(it).lowercase(Locale.getDefault()) }
            )
            .mapNotNull { document ->
                val userId = canonicalUserId(document)
                val emailKey = document.getString("email").orEmpty().trim().lowercase(Locale.US)
                if (userId.isBlank()) return@mapNotNull null
                if (userId == currentViewerId()) {
                    Log.d(TAG, "Skipped current user uid=$userId")
                    return@mapNotNull null
                }
                if (seenUids.contains(userId) || (emailKey.isNotBlank() && seenEmails.contains(emailKey))) {
                    Log.d(TAG, "Skipped duplicate uid=$userId documentId=${document.id}")
                    return@mapNotNull null
                }
                seenUids += userId
                if (emailKey.isNotBlank()) seenEmails += emailKey
                document
            }
    }

    private fun publicNameFromDocument(document: DocumentSnapshot): String {
        return listOf(
            document.getString("fullName").orEmpty(),
            document.getString("displayName").orEmpty(),
            document.getString("username").orEmpty(),
            document.getString("name").orEmpty(),
            getString(R.string.hopegive_user)
        ).firstNotNullOf { candidate ->
            PrivacyDisplayHelper.publicName(candidate, "").takeIf { it.isNotBlank() }
        }
    }

    private fun profileScore(document: DocumentSnapshot): Int {
        val storedUserId = document.getString("uid").orEmpty()
        val hasName = publicNameFromDocument(document) != getString(R.string.hopegive_user)
        val hasPhoto = document.getString("profilePhotoUrl").orEmpty().isNotBlank() ||
            document.getString("profilePhotoLocalUri").orEmpty().isNotBlank()
        return listOf(
            if (storedUserId.isNotBlank() && document.id == storedUserId) 100 else 0,
            if (storedUserId.isNotBlank() && document.id != storedUserId) 70 else 0,
            if (document.getBoolean("isActiveUser") == true) 50 else 0,
            if (hasName) 40 else 0,
            if (hasPhoto) 10 else 0,
        ).sum()
    }

    private fun isRealUserDocument(document: DocumentSnapshot): Boolean {
        val userId = canonicalUserId(document)
        if (userId.isBlank()) return false
        if (userId == currentViewerId()) {
            Log.d(TAG, "Skipped current user uid=$userId")
            return false
        }
        if (document.getBoolean("isActiveUser") == false) {
            Log.d(TAG, "Skipped inactive uid=$userId")
            return false
        }
        if (document.getBoolean("isDuplicate") == true) {
            Log.d(TAG, "Skipped duplicate uid=$userId")
            return false
        }
        if (document.getBoolean("archived") == true || document.getBoolean("isArchived") == true || document.get("archivedAt") != null) {
            Log.d(TAG, "Skipped archived uid=$userId")
            return false
        }

        val recordType = document.getString("recordType").orEmpty()
        if (recordType == "legacy_or_invalid" || (recordType.isNotBlank() && recordType != "user")) {
            Log.d(TAG, "Skipped non-user record uid=$userId recordType=$recordType")
            return false
        }

        val hasStoredUid = document.getString("uid").orEmpty().isNotBlank()
        val hasIdentity = document.getString("fullName").orEmpty().isNotBlank() ||
            document.getString("displayName").orEmpty().isNotBlank() ||
            document.getString("username").orEmpty().isNotBlank() ||
            document.getString("email").orEmpty().isNotBlank() ||
            document.getString("authProvider").orEmpty().isNotBlank() ||
            document.get("authProviders") != null ||
            document.get("createdAt") != null

        if (!hasStoredUid && !hasIdentity) {
            Log.d(TAG, "Skipped incomplete record documentId=${document.id}")
            return false
        }
        return true
    }

    private fun canonicalUserId(document: DocumentSnapshot): String {
        return document.getString("uid").orEmpty().ifBlank { document.id }
    }

    private fun currentViewerId(): String {
        return auth.currentUser?.uid.orEmpty()
    }

    private fun openUserProfile(userId: String) {
        if (userId == currentViewerId()) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_PROFILE, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            return
        }
        PublicProfileActivity.start(this, userId)
    }

    private fun relationshipTimeMillis(document: DocumentSnapshot, field: String): Long {
        return document.getTimestamp(field)?.toDate()?.time ?: 0L
    }

    private fun normalizeMode(rawMode: String): String {
        return when (rawMode) {
            MODE_ALL_USERS, LEGACY_MODE_USERS -> MODE_ALL_USERS
            MODE_FRIENDS -> MODE_FRIENDS
            MODE_FOLLOWING -> MODE_FOLLOWING
            MODE_FOLLOWERS -> MODE_FOLLOWERS
            else -> MODE_ALL_USERS
        }
    }

    companion object {
        private const val TAG = "ViewUsers"

        const val MODE_ALL_USERS = "all_users"
        const val MODE_FOLLOWING = "following"
        const val MODE_FOLLOWERS = "followers"
        const val MODE_FRIENDS = "friends"
        const val MODE_USERS = MODE_ALL_USERS
        private const val LEGACY_MODE_USERS = "users"

        private const val EXTRA_USER_ID = "extra_user_id"
        private const val EXTRA_MODE = "extra_mode"
        private const val USERS_COLLECTION = "users"
        private const val FOLLOWING_COLLECTION = "following"
        private const val FOLLOWERS_COLLECTION = "followers"
        private const val FRIENDS_COLLECTION = "friends"

        fun start(context: Context, userId: String, mode: String) {
            val intent = Intent(context, FollowListActivity::class.java)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_MODE, mode)
            context.startActivity(intent)
        }
    }
}
