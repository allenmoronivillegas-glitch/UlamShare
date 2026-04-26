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
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var realtimeDb: DatabaseReference
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var ivAvatar: TextView
    private lateinit var ivAvatarPhoto: ImageView
    private lateinit var tvTotalDonated: TextView
    private lateinit var tvDonationCount: TextView
    private lateinit var tvCampaignCount: TextView
    private lateinit var tvSubDon: TextView
    private lateinit var tvFollowingCount: TextView
    private lateinit var tvFollowersCount: TextView
    private lateinit var cardFollowing: CardView
    private lateinit var cardFollowers: CardView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_profile, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        realtimeDb = FirebaseDatabase
            .getInstance("https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference
            .child("donations")

        tvUserName = view.findViewById(R.id.tvUserName)
        tvUserEmail = view.findViewById(R.id.tvUserEmail)
        ivAvatar = view.findViewById(R.id.ivAvatar)
        ivAvatarPhoto = view.findViewById(R.id.ivAvatarPhoto)
        ivAvatarPhoto.clipToOutline = true
        tvTotalDonated = view.findViewById(R.id.tvTotalDonated)
        tvDonationCount = view.findViewById(R.id.tvDonationCount)
        tvCampaignCount = view.findViewById(R.id.tvCampaignCount)
        tvSubDon = view.findViewById(R.id.tvSubDon)
        tvFollowingCount = view.findViewById(R.id.tvFollowingCount)
        tvFollowersCount = view.findViewById(R.id.tvFollowersCount)
        cardFollowing = view.findViewById(R.id.cardFollowing)
        cardFollowers = view.findViewById(R.id.cardFollowers)

        val optionLogout = view.findViewById<ConstraintLayout>(R.id.optionlogout)
        val optionPay = view.findViewById<ConstraintLayout>(R.id.optionPay)
        val myDonations = view.findViewById<ConstraintLayout>(R.id.mydonations)
        val optionSupport = view.findViewById<ConstraintLayout>(R.id.optionSupport)
        val optionNotifications = view.findViewById<ConstraintLayout>(R.id.optionDonations)
        val btnEditProfile = view.findViewById<ImageView>(R.id.btnEditProfile)
        val btnHeaderAddFriends = view.findViewById<MaterialButton>(R.id.btnHeaderAddFriends)

        loadUserData()

        btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        btnHeaderAddFriends.setOnClickListener {
            if (auth.currentUser != null) {
                startActivity(Intent(requireContext(), AddFriendsActivity::class.java))
            } else {
                Toast.makeText(requireContext(), R.string.login_to_follow_users, Toast.LENGTH_SHORT).show()
            }
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
            startActivity(Intent(requireContext(), PaymentMethodActivity::class.java))
        }

        myDonations.setOnClickListener {
            startActivity(Intent(requireContext(), ActivityHistory::class.java))
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
            tvUserEmail.text = user.email
            tvTotalDonated.text = "\u20B10"
            loadUserDonationStats(user.uid)
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    val fullName = if (document != null && document.exists()) {
                        document.getString("fullName") ?: "User"
                    } else {
                        "User"
                    }
                    val profilePhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty()
                    val profilePhotoUrl = document.getString("profilePhotoUrl").orEmpty()

                    tvUserName.text = fullName
                    displayProfilePhoto(
                        profilePhotoLocalUri
                            .ifBlank { savedProfilePhotoUri(user.uid) }
                            .ifBlank { profilePhotoUrl }
                    )
                    refreshFollowCounts(user.uid)

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
                }
        } else {
            tvUserName.text = "Guest User"
            tvUserEmail.text = "Not logged in"
            ivAvatar.text = "G"
            displayProfilePhoto(savedProfilePhotoUri(GUEST_PROFILE_KEY))
            tvTotalDonated.text = "\u20B10"
            tvDonationCount.text = "0"
            tvCampaignCount.text = "0"
            tvSubDon.text = "0 donations this year"
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
        val followingTask = db.collection("users").document(userId)
            .collection("following")
            .get()
        val followersTask = db.collection("users").document(userId)
            .collection("followers")
            .get()

        Tasks.whenAll(followingTask, followersTask)
            .addOnSuccessListener {
                val followingCount = followingTask.result?.size() ?: 0
                val followersCount = followersTask.result?.size() ?: 0
                tvFollowingCount.text = followingCount.toString()
                tvFollowersCount.text = followersCount.toString()
                db.collection("users").document(userId)
                    .set(
                        mapOf(
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

    private fun openFollowList(mode: String) {
        val user = auth.currentUser
        if (user == null) {
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
        realtimeDb.orderByChild("userId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var totalDonated = 0
                    var donationCount = 0
                    val donatedCampaignIds = mutableSetOf<String>()
                    var donationsThisYear = 0
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                    for (donationSnapshot in snapshot.children) {
                        val amount = donationSnapshot.child("amount").getValue(Int::class.java)
                            ?: donationSnapshot.child("amount").getValue(Long::class.java)?.toInt()
                            ?: 0
                        val campaignId = donationSnapshot.child("campaignId").getValue(String::class.java) ?: ""
                        val timestamp = donationSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                        totalDonated += amount
                        donationCount += 1
                        if (campaignId.isNotBlank()) donatedCampaignIds.add(campaignId)

                        if (timestamp > 0) {
                            val donationYear = Calendar.getInstance().apply {
                                timeInMillis = timestamp
                            }.get(Calendar.YEAR)
                            if (donationYear == currentYear) {
                                donationsThisYear += 1
                            }
                        }
                    }

                    tvTotalDonated.text = String.format(Locale.US, "\u20B1%,d", totalDonated)
                    tvDonationCount.text = donationCount.toString()
                    tvCampaignCount.text = donatedCampaignIds.size.toString()
                    tvSubDon.text = "$donationsThisYear donations this year"
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "Donation stats load cancelled", error.toException())
                }
            })
    }

    private companion object {
        const val PROFILE_PREFS = "profile_preferences"
        const val GUEST_PROFILE_KEY = "guest"
    }
}
