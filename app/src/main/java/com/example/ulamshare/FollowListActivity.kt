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
    private var mode: String = MODE_FOLLOWING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)

        ownerUserId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        mode = intent.getStringExtra(EXTRA_MODE).orEmpty().ifBlank { MODE_FOLLOWING }

        titleView = findViewById(R.id.tvFollowListTitle)
        subtitleView = findViewById(R.id.tvFollowListSubtitle)
        stateView = findViewById(R.id.tvFollowListState)
        progressView = findViewById(R.id.progressFollowList)
        recyclerView = findViewById(R.id.rvFollowList)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        titleView.text = if (mode == MODE_FOLLOWERS) {
            getString(R.string.followers_title)
        } else if (mode == MODE_FRIENDS) {
            getString(R.string.friends_title)
        } else {
            getString(R.string.following_title)
        }
        subtitleView.text = getString(R.string.follow_list_subtitle)

        adapter = FollowListAdapter(
            onUserClick = { user ->
                PublicProfileActivity.start(this, user.userId)
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

        loadFollowList()
    }

    private fun loadFollowList() {
        if (ownerUserId.isBlank()) {
            showEmptyState()
            return
        }

        showLoading()
        val collectionName = when (mode) {
            MODE_FOLLOWERS -> FOLLOWERS_COLLECTION
            MODE_FRIENDS -> FRIENDS_COLLECTION
            else -> FOLLOWING_COLLECTION
        }
        firestore.collection(USERS_COLLECTION)
            .document(ownerUserId)
            .collection(collectionName)
            .get()
            .addOnSuccessListener { snapshot ->
                val relationshipDocs = snapshot.documents
                if (relationshipDocs.isEmpty()) {
                    loadDiscoverableUsersIfNeeded()
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
                        showUsers(users)
                    }
                    .addOnFailureListener { error ->
                        Log.w("FollowListActivity", "Unable to hydrate $collectionName profiles", error)
                        val orderField = if (mode == MODE_FRIENDS) "friendedAt" else "followedAt"
                        val fallbackUsers = relationshipDocs
                            .sortedByDescending { relationshipTimeMillis(it, orderField) }
                            .mapNotNull { relationship ->
                                val userId = relationshipUserId(relationship)
                                if (userId.isBlank()) null else mapRelationshipUser(relationship, null)
                            }
                            .distinctBy { it.userId }
                        showUsers(fallbackUsers)
                    }
            }
            .addOnFailureListener { error ->
                Log.e("FollowListActivity", "Unable to load $collectionName list", error)
                progressView.visibility = View.GONE
                recyclerView.visibility = View.GONE
                stateView.visibility = View.VISIBLE
                stateView.text = getString(R.string.follow_list_load_failed)
            }
    }

    private fun loadDiscoverableUsersIfNeeded() {
        if (mode != MODE_FRIENDS || auth.currentUser?.uid != ownerUserId) {
            showEmptyState()
            return
        }

        firestore.collection(USERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = dedupeProfileDocuments(snapshot.documents)
                    .map(::mapProfileUser)
                    .sortedBy { it.fullName.lowercase() }

                progressView.visibility = View.GONE
                if (users.isEmpty()) {
                    showEmptyState()
                } else {
                    subtitleView.text = getString(R.string.people_you_may_know)
                    stateView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(users, canUnfollow = false)
                }
            }
            .addOnFailureListener { error ->
                Log.e("FollowListActivity", "Unable to load discoverable users", error)
                showEmptyState()
            }
    }

    private fun showUsers(users: List<FollowListUser>) {
        progressView.visibility = View.GONE
        adapter.submitList(users, canUnfollowFromThisList())
        if (users.isEmpty()) {
            showEmptyState()
        } else {
            subtitleView.text = getString(R.string.follow_list_subtitle)
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
                            loadFollowList()
                        }
                        .onFailure { error ->
                            Log.e("FollowListActivity", "Unable to unfollow from list", error)
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
                            loadFollowList()
                        }
                        .onFailure { error ->
                            Log.e("FollowListActivity", "Unable to unfriend from list", error)
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
        subtitleView.text = getString(R.string.follow_list_subtitle)
    }

    private fun showEmptyState() {
        progressView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        stateView.visibility = View.VISIBLE
        stateView.text = when (mode) {
            MODE_FOLLOWERS -> getString(R.string.no_followers_yet)
            MODE_FRIENDS -> getString(R.string.no_friends_yet)
            else -> getString(R.string.no_following_yet)
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
        val userId = document.getString("uid").orEmpty().ifBlank { document.id }
        val fullName = listOf(
            document.getString("fullName").orEmpty(),
            document.getString("displayName").orEmpty(),
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
            .filterNot { it.getBoolean("isDuplicate") == true }
            .sortedWith(
                compareByDescending<DocumentSnapshot> { profileScore(it) }
                    .thenBy { publicNameFromDocument(it).lowercase(Locale.getDefault()) }
            )
            .mapNotNull { document ->
                val userId = document.getString("uid").orEmpty().ifBlank { document.id }
                val emailKey = document.getString("email").orEmpty().trim().lowercase(Locale.US)
                if (userId.isBlank() || userId == ownerUserId) return@mapNotNull null
                if (seenUids.contains(userId) || (emailKey.isNotBlank() && seenEmails.contains(emailKey))) {
                    Log.d("FollowListActivity", "Skipping duplicate discoverable user document id=${document.id} uid=$userId")
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
            document.getString("name").orEmpty(),
            getString(R.string.hopegive_user)
        ).firstNotNullOf { candidate ->
            PrivacyDisplayHelper.publicName(candidate, "").takeIf { it.isNotBlank() }
        }
    }

    private fun profileScore(document: DocumentSnapshot): Int {
        val userId = document.getString("uid").orEmpty().ifBlank { document.id }
        val hasName = publicNameFromDocument(document) != getString(R.string.hopegive_user)
        val hasPhoto = document.getString("profilePhotoUrl").orEmpty().isNotBlank() ||
            document.getString("profilePhotoLocalUri").orEmpty().isNotBlank()
        return listOf(
            if (document.id == userId) 100 else 0,
            if (hasName) 40 else 0,
            if (hasPhoto) 10 else 0,
            if (document.getTimestamp("updatedAt") != null) 1 else 0
        ).sum()
    }

    private fun relationshipTimeMillis(document: DocumentSnapshot, field: String): Long {
        return document.getTimestamp(field)?.toDate()?.time ?: 0L
    }

    companion object {
        const val MODE_FOLLOWING = "following"
        const val MODE_FOLLOWERS = "followers"
        const val MODE_FRIENDS = "friends"

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
