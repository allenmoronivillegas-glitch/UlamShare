package com.example.ulamshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class PublicProfileActivity : AppCompatActivity() {
    private enum class FriendState {
        NOT_FRIENDS,
        REQUESTED,
        INCOMING_REQUEST,
        FRIENDS
    }

    private lateinit var avatarInitials: TextView
    private lateinit var avatarPhoto: ImageView
    private lateinit var nameView: TextView
    private lateinit var roleView: TextView
    private lateinit var friendsCountView: TextView
    private lateinit var followingCountView: TextView
    private lateinit var followersCountView: TextView
    private lateinit var totalDonatedView: TextView
    private lateinit var donationCountView: TextView
    private lateinit var campaignsDonatedView: TextView
    private lateinit var friendActionButton: MaterialButton
    private lateinit var followActionButton: MaterialButton
    private lateinit var menuButton: ImageButton
    private lateinit var friendsCard: CardView
    private lateinit var followingCard: CardView
    private lateinit var followersCard: CardView

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val firebaseDb: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DATABASE_URL) }

    private var currentProfile: FollowProfile? = null
    private var targetProfile: FollowProfile? = null
    private var targetUserId: String = ""
    private var friendState: FriendState = FriendState.NOT_FRIENDS
    private var isFollowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_public_profile)

        targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
        val currentUser = auth.currentUser
        if (targetUserId.isBlank()) {
            finish()
            return
        }
        if (currentUser?.uid == targetUserId) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_PROFILE, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
            return
        }

        bindViews()
        setupActions()
        loadProfiles()
    }

    private fun bindViews() {
        avatarInitials = findViewById(R.id.tvPublicAvatar)
        avatarPhoto = findViewById(R.id.ivPublicAvatarPhoto)
        nameView = findViewById(R.id.tvPublicName)
        roleView = findViewById(R.id.tvPublicRole)
        friendsCountView = findViewById(R.id.tvPublicFriendsCount)
        followingCountView = findViewById(R.id.tvPublicFollowingCount)
        followersCountView = findViewById(R.id.tvPublicFollowersCount)
        totalDonatedView = findViewById(R.id.tvPublicTotalDonated)
        donationCountView = findViewById(R.id.tvPublicDonationCount)
        campaignsDonatedView = findViewById(R.id.tvPublicCampaignsDonatedCount)
        friendActionButton = findViewById(R.id.btnFriendAction)
        followActionButton = findViewById(R.id.btnFollowAction)
        menuButton = findViewById(R.id.btnProfileMenu)
        friendsCard = findViewById(R.id.cardPublicFriends)
        followingCard = findViewById(R.id.cardPublicFollowing)
        followersCard = findViewById(R.id.cardPublicFollowers)
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        friendActionButton.setOnClickListener { handleFriendAction() }
        followActionButton.setOnClickListener { handleFollowAction() }
        menuButton.setOnClickListener { showProfileMenu(menuButton) }

        friendsCard.setOnClickListener {
            FollowListActivity.start(this, targetUserId, FollowListActivity.MODE_FRIENDS)
        }
        followingCard.setOnClickListener {
            FollowListActivity.start(this, targetUserId, FollowListActivity.MODE_FOLLOWING)
        }
        followersCard.setOnClickListener {
            FollowListActivity.start(this, targetUserId, FollowListActivity.MODE_FOLLOWERS)
        }
    }

    private fun loadProfiles() {
        firestore.collection(USERS_COLLECTION).document(targetUserId)
            .get()
            .addOnSuccessListener { targetDocument ->
                if (!targetDocument.exists()) {
                    Toast.makeText(this, "User profile not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                targetProfile = targetDocument.toFollowProfile()
                bindTargetProfile(targetDocument)
                loadDonationImpactStats()
                loadCurrentProfileAndRelationship()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to load public profile", error)
                Toast.makeText(this, "Unable to load this profile right now.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadCurrentProfileAndRelationship() {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.isAnonymous) {
            currentProfile = null
            friendState = FriendState.NOT_FRIENDS
            isFollowing = false
            syncActionButtons()
            return
        }

        firestore.collection(USERS_COLLECTION).document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                currentProfile = document.toFollowProfile(
                    fallbackUid = currentUser.uid,
                    fallbackName = currentUser.displayName
                        ?: getString(R.string.hopegive_user),
                    fallbackEmail = currentUser.email.orEmpty()
                )
                loadRelationshipState(currentUser.uid)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to load current profile relationship state", error)
                currentProfile = FollowProfile(
                    uid = currentUser.uid,
                    fullName = currentUser.displayName
                        ?: getString(R.string.hopegive_user),
                    email = currentUser.email.orEmpty()
                )
                loadRelationshipState(currentUser.uid)
            }
    }

    private fun loadRelationshipState(currentUserId: String) {
        val currentUserRef = firestore.collection(USERS_COLLECTION).document(currentUserId)
        currentUserRef.collection(FRIENDS_COLLECTION).document(targetUserId).get()
            .addOnSuccessListener { friendSnapshot ->
                if (friendSnapshot.exists()) {
                    friendState = FriendState.FRIENDS
                    loadFollowingState(currentUserId)
                    return@addOnSuccessListener
                }
                loadFriendRequestState(currentUserId)
            }
            .addOnFailureListener {
                loadFriendRequestState(currentUserId)
            }
    }

    private fun loadFriendRequestState(currentUserId: String) {
        val outgoingId = FollowRepository.friendRequestId(currentUserId, targetUserId)
        val incomingId = FollowRepository.friendRequestId(targetUserId, currentUserId)

        firestore.collection(FRIEND_REQUESTS_COLLECTION).document(outgoingId).get()
            .addOnSuccessListener { outgoing ->
                if (outgoing.getString("status") == FollowRepository.FRIEND_REQUEST_PENDING) {
                    friendState = FriendState.REQUESTED
                    loadFollowingState(currentUserId)
                    return@addOnSuccessListener
                }
                firestore.collection(FRIEND_REQUESTS_COLLECTION).document(incomingId).get()
                    .addOnSuccessListener { incoming ->
                        friendState = if (incoming.getString("status") == FollowRepository.FRIEND_REQUEST_PENDING) {
                            FriendState.INCOMING_REQUEST
                        } else {
                            FriendState.NOT_FRIENDS
                        }
                        loadFollowingState(currentUserId)
                    }
                    .addOnFailureListener {
                        friendState = FriendState.NOT_FRIENDS
                        loadFollowingState(currentUserId)
                    }
            }
            .addOnFailureListener {
                friendState = FriendState.NOT_FRIENDS
                loadFollowingState(currentUserId)
            }
    }

    private fun loadFollowingState(currentUserId: String) {
        firestore.collection(USERS_COLLECTION).document(currentUserId)
            .collection(FOLLOWING_COLLECTION).document(targetUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                isFollowing = snapshot.exists()
                syncActionButtons()
            }
            .addOnFailureListener {
                isFollowing = false
                syncActionButtons()
            }
    }

    private fun bindTargetProfile(document: DocumentSnapshot) {
        val profile = targetProfile ?: return
        nameView.text = PrivacyDisplayHelper.publicName(profile.fullName)
        roleView.text = roleLabel(profile.role)
        avatarInitials.text = initials(profile.fullName)
        friendsCountView.text = numberToLong(document.get("friendsCount")).toString()
        followingCountView.text = numberToLong(document.get("followingCount")).toString()
        followersCountView.text = numberToLong(document.get("followersCount")).toString()
        bindAggregateDonationImpact(document)
        displayProfilePhoto(profile.profilePhotoUrl.ifBlank { profile.profilePhotoLocalUri })
    }

    private fun bindAggregateDonationImpact(document: DocumentSnapshot) {
        val totalDonated = numberToLong(document.get("totalDonated"))
        val donationsCount = numberToLong(document.get("donationsCount"))
        val campaignsDonatedCount = numberToLong(document.get("campaignsDonatedCount"))

        totalDonatedView.text = UserDonationStatsRepository.formatPeso(totalDonated)
        donationCountView.text = donationsCount.toString()
        campaignsDonatedView.text = campaignsDonatedCount.toString()
    }

    private fun loadDonationImpactStats() {
        UserDonationStatsRepository.loadUserDonationStats(targetUserId, firestore) { result ->
            result
                .onSuccess { stats ->
                    totalDonatedView.text = UserDonationStatsRepository.formatPeso(stats.totalDonated)
                    donationCountView.text = stats.donationsCount.toString()
                    campaignsDonatedView.text = stats.campaignsDonatedCount.toString()
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to load public profile donation stats", error)
                    totalDonatedView.text = UserDonationStatsRepository.formatPeso(0L)
                    donationCountView.text = "0"
                    campaignsDonatedView.text = "0"
                }
        }
    }

    private fun syncActionButtons() {
        friendActionButton.text = when (friendState) {
            FriendState.FRIENDS -> getString(R.string.friends_action)
            FriendState.REQUESTED -> getString(R.string.friend_requested_action)
            FriendState.INCOMING_REQUEST -> getString(R.string.respond_action)
            FriendState.NOT_FRIENDS -> getString(R.string.add_friend_action)
        }
        followActionButton.text = if (isFollowing) {
            getString(R.string.following_action)
        } else {
            getString(R.string.follow_action)
        }
    }

    private fun handleFriendAction() {
        val current = currentProfile
        val target = targetProfile
        if (current == null || target == null || auth.currentUser?.isAnonymous == true) {
            Toast.makeText(this, R.string.please_login_connect_users, Toast.LENGTH_SHORT).show()
            return
        }

        when (friendState) {
            FriendState.NOT_FRIENDS -> sendFriendRequest(current, target)
            FriendState.REQUESTED -> showRequestedMenu(friendActionButton)
            FriendState.INCOMING_REQUEST -> showRespondMenu(friendActionButton)
            FriendState.FRIENDS -> showFriendsMenu(friendActionButton)
        }
    }

    private fun handleFollowAction() {
        val current = currentProfile
        val target = targetProfile
        if (current == null || target == null || auth.currentUser?.isAnonymous == true) {
            Toast.makeText(this, R.string.please_login_connect_users, Toast.LENGTH_SHORT).show()
            return
        }

        if (isFollowing) {
            PopupMenu(this, followActionButton).apply {
                menu.add("Unfollow")
                setOnMenuItemClickListener {
                    unfollowTarget()
                    true
                }
                show()
            }
        } else {
            FollowRepository.follow(
                firestore = firestore,
                currentUser = current,
                targetUser = target
            ) { result ->
                result
                    .onSuccess {
                        isFollowing = true
                        incrementDisplayedFollowers()
                        syncActionButtons()
                        Toast.makeText(this, R.string.following_action, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Unable to follow user", error)
                        Toast.makeText(this, R.string.friend_request_failed_generic, Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun sendFriendRequest(current: FollowProfile, target: FollowProfile) {
        FollowRepository.sendFriendRequest(
            firestore = firestore,
            currentUser = current,
            targetUser = target
        ) { result ->
            result
                .onSuccess {
                    friendState = FriendState.REQUESTED
                    syncActionButtons()
                    Toast.makeText(this, R.string.friend_request_sent, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to send friend request", error)
                    Toast.makeText(this, R.string.friend_request_failed_generic, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun acceptRequest() {
        val current = currentProfile ?: return
        val target = targetProfile ?: return
        FollowRepository.acceptFriendRequest(
            firestore = firestore,
            currentUser = current,
            requester = target
        ) { result ->
            result
                .onSuccess {
                    friendState = FriendState.FRIENDS
                    incrementDisplayedFriends()
                    syncActionButtons()
                    Toast.makeText(this, R.string.friend_request_accepted, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to accept friend request", error)
                    Toast.makeText(this, R.string.friend_request_failed_generic, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun declineRequest() {
        val current = currentProfile ?: return
        val target = targetProfile ?: return
        FollowRepository.declineFriendRequest(
            firestore = firestore,
            currentUserId = current.uid,
            targetUserId = target.uid
        ) { result ->
            result
                .onSuccess {
                    friendState = FriendState.NOT_FRIENDS
                    syncActionButtons()
                    Toast.makeText(this, R.string.friend_request_declined, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to decline friend request", error)
                    Toast.makeText(this, R.string.friend_request_failed_generic, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun cancelRequest() {
        val current = currentProfile ?: return
        val target = targetProfile ?: return
        FollowRepository.cancelFriendRequest(
            firestore = firestore,
            currentUserId = current.uid,
            targetUserId = target.uid
        ) { result ->
            result
                .onSuccess {
                    friendState = FriendState.NOT_FRIENDS
                    syncActionButtons()
                    Toast.makeText(this, R.string.friend_request_cancelled, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to cancel friend request", error)
                    Toast.makeText(this, R.string.friend_request_failed_generic, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun unfriendTarget() {
        val current = currentProfile ?: return
        val target = targetProfile ?: return
        FollowRepository.unfriend(
            firestore = firestore,
            currentUserId = current.uid,
            targetUserId = target.uid
        ) { result ->
            result
                .onSuccess {
                    friendState = FriendState.NOT_FRIENDS
                    decrementDisplayedFriends()
                    syncActionButtons()
                    Toast.makeText(this, R.string.friend_removed, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to unfriend user", error)
                    Toast.makeText(this, R.string.unfriend_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun unfollowTarget() {
        val current = currentProfile ?: return
        val target = targetProfile ?: return
        FollowRepository.unfollow(
            firestore = firestore,
            currentUserId = current.uid,
            targetUserId = target.uid
        ) { result ->
            result
                .onSuccess {
                    isFollowing = false
                    decrementDisplayedFollowers()
                    syncActionButtons()
                    Toast.makeText(this, "Unfollowed.", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Log.e(TAG, "Unable to unfollow user", error)
                    Toast.makeText(this, R.string.unfollow_failed, Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showRequestedMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.cancel_request_action)
            setOnMenuItemClickListener {
                cancelRequest()
                true
            }
            show()
        }
    }

    private fun showRespondMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.accept_action)
            menu.add(R.string.decline_action)
            setOnMenuItemClickListener { item ->
                if (item.title == getString(R.string.accept_action)) {
                    acceptRequest()
                } else {
                    declineRequest()
                }
                true
            }
            show()
        }
    }

    private fun showFriendsMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.view_friendship_action)
            menu.add(R.string.unfriend_action)
            setOnMenuItemClickListener { item ->
                if (item.title == getString(R.string.unfriend_action)) {
                    unfriendTarget()
                } else {
                    Toast.makeText(this@PublicProfileActivity, R.string.friends_action, Toast.LENGTH_SHORT).show()
                }
                true
            }
            show()
        }
    }

    private fun showProfileMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            when (friendState) {
                FriendState.FRIENDS -> menu.add(R.string.unfriend_action)
                FriendState.REQUESTED -> menu.add(R.string.cancel_request_action)
                FriendState.INCOMING_REQUEST -> {
                    menu.add(R.string.accept_action)
                    menu.add(R.string.decline_action)
                }
                FriendState.NOT_FRIENDS -> menu.add(R.string.add_friend_action)
            }
            menu.add(if (isFollowing) "Unfollow" else getString(R.string.follow_action))
            menu.add(R.string.message_user_action)
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    getString(R.string.add_friend_action) -> handleFriendAction()
                    getString(R.string.cancel_request_action) -> cancelRequest()
                    getString(R.string.accept_action) -> acceptRequest()
                    getString(R.string.decline_action) -> declineRequest()
                    getString(R.string.unfriend_action) -> unfriendTarget()
                    getString(R.string.follow_action) -> handleFollowAction()
                    "Unfollow" -> unfollowTarget()
                    getString(R.string.message_user_action) -> openMessage()
                }
                true
            }
            show()
        }
    }

    private fun openMessage() {
        val current = currentProfile
        val target = targetProfile ?: return
        if (current == null || auth.currentUser?.isAnonymous == true) {
            Toast.makeText(this, R.string.please_login_connect_users, Toast.LENGTH_SHORT).show()
            return
        }

        val conversationId = listOf(current.uid, target.uid).sorted().joinToString("_")
        firebaseDb.getReference("directChats").child(conversationId)
            .updateChildren(
                hashMapOf<String, Any>(
                    "chatType" to "direct",
                    "updatedAt" to ServerValue.TIMESTAMP,
                    "participants/${current.uid}" to true,
                    "participants/${target.uid}" to true,
                    "participantProfiles/${current.uid}/displayName" to current.fullName,
                    "participantProfiles/${current.uid}/email" to current.email,
                    "participantProfiles/${target.uid}/displayName" to target.fullName,
                    "participantProfiles/${target.uid}/email" to target.email
                )
            )

        val conversation = MessengerConversation(
            key = "direct:$conversationId",
            channel = "direct",
            rootPath = "directChats/$conversationId",
            title = target.fullName,
            typeLabel = getString(R.string.messenger_contact_type_friend),
            preview = getString(R.string.messenger_preview_start),
            updatedAt = System.currentTimeMillis(),
            chatType = "direct",
            participantUserId = target.uid,
            participantEmail = target.email
        )
        startActivity(MessengerChatActivity.createIntent(this, conversation))
    }

    private fun displayProfilePhoto(uriString: String) {
        if (uriString.isBlank()) {
            avatarPhoto.setImageDrawable(null)
            avatarPhoto.visibility = View.GONE
            avatarInitials.visibility = View.VISIBLE
            return
        }

        avatarPhoto.visibility = View.VISIBLE
        avatarInitials.visibility = View.GONE
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            CampaignImageLoader.load(avatarPhoto, uriString, R.drawable.plant)
        } else {
            runCatching {
                avatarPhoto.setImageURI(Uri.parse(uriString))
            }.onFailure {
                avatarPhoto.visibility = View.GONE
                avatarInitials.visibility = View.VISIBLE
            }
        }
    }

    private fun DocumentSnapshot.toFollowProfile(
        fallbackUid: String = id,
        fallbackName: String = "HopeGive User",
        fallbackEmail: String = ""
    ): FollowProfile {
        val email = getString("email").orEmpty().ifBlank { fallbackEmail }
        val fullName = listOf(
            getString("fullName").orEmpty(),
            getString("displayName").orEmpty(),
            fallbackName,
            getString(R.string.hopegive_user)
        ).firstNotNullOf { candidate ->
            PrivacyDisplayHelper.publicName(candidate, "").takeIf { it.isNotBlank() }
        }
        return FollowProfile(
            uid = getString("uid").orEmpty().ifBlank { fallbackUid },
            fullName = fullName,
            email = email,
            profilePhotoUrl = getString("profilePhotoUrl").orEmpty(),
            profilePhotoLocalUri = getString("profilePhotoLocalUri").orEmpty(),
            role = getString("role").orEmpty().ifBlank { getString("roleKey").orEmpty() },
            status = getString("status").orEmpty()
        )
    }

    private fun incrementDisplayedFriends() {
        friendsCountView.text = (friendsCountView.text.toString().toLongOrNull() ?: 0L)
            .plus(1L)
            .toString()
    }

    private fun decrementDisplayedFriends() {
        friendsCountView.text = (friendsCountView.text.toString().toLongOrNull() ?: 0L)
            .minus(1L)
            .coerceAtLeast(0L)
            .toString()
    }

    private fun incrementDisplayedFollowers() {
        followersCountView.text = (followersCountView.text.toString().toLongOrNull() ?: 0L)
            .plus(1L)
            .toString()
    }

    private fun decrementDisplayedFollowers() {
        followersCountView.text = (followersCountView.text.toString().toLongOrNull() ?: 0L)
            .minus(1L)
            .coerceAtLeast(0L)
            .toString()
    }

    private fun initials(value: String): String {
        val parts = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        return if (parts.isEmpty()) "HG" else parts.joinToString("") {
            it.first().uppercase(Locale.getDefault())
        }
    }

    private fun roleLabel(role: String): String {
        return when (role.trim().lowercase(Locale.getDefault())) {
            CampaignFeedPost.ROLE_SUPER_ADMIN -> "Super Admin"
            CampaignFeedPost.ROLE_ADMIN -> "Admin"
            CampaignFeedPost.ROLE_MODERATOR -> "Moderator"
            CampaignFeedPost.ROLE_GUEST -> "Guest"
            else -> "User"
        }
    }

    private fun numberToLong(value: Any?): Long {
        return when (value) {
            is Int -> value.toLong()
            is Long -> value
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    companion object {
        private const val TAG = "PublicProfile"
        private const val DATABASE_URL =
            "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"
        private const val USERS_COLLECTION = "users"
        private const val FRIENDS_COLLECTION = "friends"
        private const val FOLLOWING_COLLECTION = "following"
        private const val FRIEND_REQUESTS_COLLECTION = "friend_requests"
        private const val EXTRA_TARGET_USER_ID = "targetUserId"

        fun start(context: Context, targetUserId: String) {
            context.startActivity(Intent(context, PublicProfileActivity::class.java).apply {
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            })
        }
    }
}
