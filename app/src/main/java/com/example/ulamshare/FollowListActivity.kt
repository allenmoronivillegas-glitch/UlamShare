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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class FollowListActivity : AppCompatActivity() {

    private lateinit var titleView: TextView
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
        stateView = findViewById(R.id.tvFollowListState)
        progressView = findViewById(R.id.progressFollowList)
        recyclerView = findViewById(R.id.rvFollowList)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        titleView.text = if (mode == MODE_FOLLOWERS) {
            getString(R.string.followers_title)
        } else {
            getString(R.string.following_title)
        }

        adapter = FollowListAdapter(
            onUserClick = { user ->
                Toast.makeText(this, user.fullName, Toast.LENGTH_SHORT).show()
            },
            onUnfollowClick = { user ->
                confirmUnfollow(user)
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
        val collectionName = if (mode == MODE_FOLLOWERS) FOLLOWERS_COLLECTION else FOLLOWING_COLLECTION
        firestore.collection(USERS_COLLECTION)
            .document(ownerUserId)
            .collection(collectionName)
            .orderBy("followedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.map { document ->
                    val userId = document.getString("userId").orEmpty().ifBlank { document.id }
                    val fullName = document.getString("fullName").orEmpty()
                        .ifBlank { document.getString("displayName").orEmpty() }
                        .ifBlank { getString(R.string.friend_label) }
                    FollowListUser(
                        userId = userId,
                        fullName = fullName,
                        email = document.getString("email").orEmpty(),
                        profilePhotoUrl = document.getString("profilePhotoUrl").orEmpty(),
                        profilePhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty(),
                        role = document.getString("role").orEmpty(),
                        status = document.getString("status").orEmpty()
                    )
                }

                progressView.visibility = View.GONE
                adapter.submitList(users, canUnfollowFromThisList())
                if (users.isEmpty()) {
                    showEmptyState()
                } else {
                    stateView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
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

    private fun canUnfollowFromThisList(): Boolean {
        return mode == MODE_FOLLOWING && auth.currentUser?.uid == ownerUserId
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

    private fun showLoading() {
        progressView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        stateView.visibility = View.VISIBLE
        stateView.text = getString(R.string.follow_list_loading)
    }

    private fun showEmptyState() {
        progressView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        stateView.visibility = View.VISIBLE
        stateView.text = if (mode == MODE_FOLLOWERS) {
            getString(R.string.no_followers_yet)
        } else {
            getString(R.string.no_following_yet)
        }
    }

    companion object {
        const val MODE_FOLLOWING = "following"
        const val MODE_FOLLOWERS = "followers"

        private const val EXTRA_USER_ID = "extra_user_id"
        private const val EXTRA_MODE = "extra_mode"
        private const val USERS_COLLECTION = "users"
        private const val FOLLOWING_COLLECTION = "following"
        private const val FOLLOWERS_COLLECTION = "followers"

        fun start(context: Context, userId: String, mode: String) {
            val intent = Intent(context, FollowListActivity::class.java)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_MODE, mode)
            context.startActivity(intent)
        }
    }
}
