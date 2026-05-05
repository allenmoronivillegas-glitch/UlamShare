package com.example.ulamshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var ivAvatar: TextView
    private lateinit var ivAvatarPhoto: ImageView
    private lateinit var tvTotalDonated: TextView
    private lateinit var tvDonationCount: TextView
    private lateinit var tvCampaignCount: TextView
    private lateinit var tvSubDon: TextView
    private lateinit var tvSubPay: TextView
    private lateinit var tvFriendsCount: TextView
    private lateinit var tvFollowingCount: TextView
    private lateinit var tvFollowersCount: TextView
    private lateinit var cardFriends: CardView
    private lateinit var cardFollowing: CardView
    private lateinit var cardFollowers: CardView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserEmail = view.findViewById(R.id.tvUserEmail)
        ivAvatar = view.findViewById(R.id.ivAvatar)
        ivAvatarPhoto = view.findViewById(R.id.ivAvatarPhoto)
        ivAvatarPhoto.clipToOutline = true
        tvTotalDonated = view.findViewById(R.id.tvTotalDonated)
        tvDonationCount = view.findViewById(R.id.tvDonationCount)
        tvCampaignCount = view.findViewById(R.id.tvCampaignCount)
        tvSubDon = view.findViewById(R.id.tvSubDon)
        tvSubPay = view.findViewById(R.id.tvSubPay)
        tvFriendsCount = view.findViewById(R.id.tvFriendsCount)
        tvFollowingCount = view.findViewById(R.id.tvFollowingCount)
        tvFollowersCount = view.findViewById(R.id.tvFollowersCount)
        cardFriends = view.findViewById(R.id.cardFriends)
        cardFollowing = view.findViewById(R.id.cardFollowing)
        cardFollowers = view.findViewById(R.id.cardFollowers)

        val optionLogout = view.findViewById<ConstraintLayout>(R.id.optionlogout)
        val optionPay = view.findViewById<ConstraintLayout>(R.id.optionPay)
        val optionImpact = view.findViewById<ConstraintLayout>(R.id.optionImpact)
        val myDonations = view.findViewById<ConstraintLayout>(R.id.mydonations)
        val optionFriends = view.findViewById<ConstraintLayout>(R.id.optionFriends)
        val optionSupport = view.findViewById<ConstraintLayout>(R.id.optionSupport)
        val optionNotifications = view.findViewById<ConstraintLayout>(R.id.optionDonations)
        val btnEditProfile = view.findViewById<ImageView>(R.id.btnEditProfile)

        loadUserData()

        btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        cardFriends.setOnClickListener {
            openFollowList(FollowListActivity.MODE_FRIENDS)
        }

        cardFollowing.setOnClickListener {
            openFollowList(FollowListActivity.MODE_FOLLOWING)
        }

        cardFollowers.setOnClickListener {
            openFollowList(FollowListActivity.MODE_FOLLOWERS)
        }

        optionLogout.setOnClickListener {
            if (auth.currentUser != null) {
                auth.signOut()
            }
            CampaignSessionManager.clear(requireContext())
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        optionPay.setOnClickListener {
            Log.d("ProfileFragment", "Opening PaymentMethodActivity source=profile")
            startActivity(Intent(requireContext(), PaymentMethodActivity::class.java).apply {
                putExtra(PaymentMethodActivity.EXTRA_SOURCE, PaymentMethodActivity.SOURCE_PROFILE)
            })
        }

        optionImpact.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(requireContext(), "Please log in to view your impact report.", Toast.LENGTH_SHORT).show()
            }
            startActivity(Intent(requireContext(), ImpactReportActivity::class.java))
        }

        myDonations.setOnClickListener {
            startActivity(Intent(requireContext(), ActivityHistory::class.java))
        }

        optionFriends.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(requireContext(), R.string.please_login_view_friends, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openFollowList(FollowListActivity.MODE_FRIENDS)
        }

        optionSupport.setOnClickListener {
            startActivity(Intent(requireContext(), ContactSupportActivity::class.java))
        }

        optionNotifications.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationActivity::class.java))
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            tvUserEmail.text = getString(R.string.profile_status_logged_in)
            tvTotalDonated.text = "\u20B10"
            loadUserDonationStats(user.uid)
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    val fullName = if (document != null && document.exists()) {
                        document.getString("fullName")
                            ?.takeIf { it.isNotBlank() }
                            ?: document.getString("displayName")
                                ?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.hopegive_user)
                    } else {
                        user.displayName?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.hopegive_user)
                    }
                    val profilePhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty()
                    val profilePhotoUrl = document.getString("profilePhotoUrl").orEmpty()

                    tvUserName.text = PrivacyDisplayHelper.publicName(fullName, getString(R.string.hopegive_user))
                    displayProfilePhoto(
                        profilePhotoLocalUri
                            .ifBlank { savedProfilePhotoUri(user.uid) }
                            .ifBlank { profilePhotoUrl }
                    )
                    refreshFollowCounts(user.uid)
                    refreshPaymentMethodSummary(user.uid)

                    val initials = fullName.split(" ")
                        .filter { it.isNotEmpty() }
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()

                    ivAvatar.text = if (initials.isNotEmpty()) initials else "?"
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileFragment", "Error fetching user data", e)
                    displayProfilePhoto(savedProfilePhotoUri(user.uid))
                    refreshFollowCounts(user.uid)
                    refreshPaymentMethodSummary(user.uid)
                }
        } else {
            tvUserName.text = getString(R.string.guest_user)
            tvUserEmail.text = getString(R.string.profile_status_guest)
            ivAvatar.text = "G"
            displayProfilePhoto(savedProfilePhotoUri(GUEST_PROFILE_KEY))
            tvTotalDonated.text = "\u20B10"
            tvDonationCount.text = "0"
            tvCampaignCount.text = "0"
            tvSubDon.text = "0 donations this year"
            tvSubPay.text = "No methods linked"
            tvFriendsCount.text = "0"
            tvFollowingCount.text = "0"
            tvFollowersCount.text = "0"
        }
    }

    private fun displayProfilePhoto(uriString: String) {
        if (uriString.isBlank()) {
            ivAvatarPhoto.setImageDrawable(null)
            ivAvatarPhoto.visibility = View.GONE
            ivAvatar.visibility = View.VISIBLE
            return
        }

        ivAvatarPhoto.visibility = View.VISIBLE
        ivAvatar.visibility = View.GONE
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            CampaignImageLoader.load(ivAvatarPhoto, uriString, R.drawable.plant)
        } else {
            runCatching {
                ivAvatarPhoto.setImageURI(Uri.parse(uriString))
            }.onFailure { error ->
                Log.w("ProfileFragment", "Unable to display saved profile photo", error)
                ivAvatarPhoto.setImageDrawable(null)
                ivAvatarPhoto.visibility = View.GONE
                ivAvatar.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshFollowCounts(userId: String) {
        val friendsTask = db.collection("users").document(userId)
            .collection("friends")
            .get()
        val followingTask = db.collection("users").document(userId)
            .collection("following")
            .get()
        val followersTask = db.collection("users").document(userId)
            .collection("followers")
            .get()

        Tasks.whenAll(friendsTask, followingTask, followersTask)
            .addOnSuccessListener {
                val friendsCount = friendsTask.result?.size() ?: 0
                val followingCount = followingTask.result?.size() ?: 0
                val followersCount = followersTask.result?.size() ?: 0
                tvFriendsCount.text = friendsCount.toString()
                tvFollowingCount.text = followingCount.toString()
                tvFollowersCount.text = followersCount.toString()
                db.collection("users").document(userId)
                    .set(
                        mapOf(
                            "friendsCount" to friendsCount,
                            "followingCount" to followingCount,
                            "followersCount" to followersCount
                        ),
                        SetOptions.merge()
                    )
            }
            .addOnFailureListener { error ->
                Log.e("ProfileFragment", "Unable to load follow counts", error)
            }
    }

    private fun refreshPaymentMethodSummary(userId: String) {
        db.collection("users").document(userId)
            .collection("payment_methods")
            .get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.size()
                tvSubPay.text = if (count == 0) {
                    "No saved payment methods yet."
                } else {
                    "$count saved method${if (count == 1) "" else "s"}"
                }
            }
            .addOnFailureListener { error ->
                Log.e("ProfileFragment", "Unable to load payment methods", error)
                tvSubPay.text = "Payment methods"
            }
    }

    private fun openFollowList(mode: String) {
        val user = auth.currentUser
        if (user == null) {
            if (mode == FollowListActivity.MODE_FRIENDS) {
                Toast.makeText(requireContext(), R.string.please_login_view_friends, Toast.LENGTH_SHORT).show()
                return
            }
            FollowListActivity.start(requireContext(), "", mode)
            return
        }

        FollowListActivity.start(requireContext(), user.uid, mode)
    }

    private fun profilePrefs() =
        requireContext().getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

    private fun savedProfilePhotoUri(key: String): String =
        profilePrefs().getString(profilePhotoPrefKey(key), "").orEmpty()

    private fun profilePhotoPrefKey(key: String): String = "profile_photo_uri_$key"

    private fun loadUserDonationStats(userId: String) {
        UserDonationStatsRepository.loadUserDonationStats(userId, db) { result ->
            if (!isAdded) return@loadUserDonationStats
            result
                .onSuccess { stats ->
                    tvTotalDonated.text = UserDonationStatsRepository.formatPeso(stats.totalDonated)
                    tvDonationCount.text = stats.donationsCount.toString()
                    tvCampaignCount.text = stats.campaignsDonatedCount.toString()
                    tvSubDon.text = "${stats.donationsThisYear} donations this year"
                }
                .onFailure { error ->
                    Log.e("ProfileFragment", "Donation stats load cancelled", error)
                    tvTotalDonated.text = UserDonationStatsRepository.formatPeso(0L)
                    tvDonationCount.text = "0"
                    tvCampaignCount.text = "0"
                    tvSubDon.text = "0 donations this year"
                }
        }
    }

    private companion object {
        const val PROFILE_PREFS = "profile_preferences"
        const val GUEST_PROFILE_KEY = "guest"
    }
}
