package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var ivAvatar: TextView
    private lateinit var tvTotalDonated: TextView
    private lateinit var tvDonationCount: TextView
    private lateinit var tvCampaignCount: TextView
    private lateinit var tvSubDon: TextView

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
        tvTotalDonated = view.findViewById(R.id.tvTotalDonated)
        tvDonationCount = view.findViewById(R.id.tvDonationCount)
        tvCampaignCount = view.findViewById(R.id.tvCampaignCount)
        tvSubDon = view.findViewById(R.id.tvSubDon)
        
        val optionLogout = view.findViewById<ConstraintLayout>(R.id.optionlogout)

        loadUserData()

        optionLogout.setOnClickListener {
            if (auth.currentUser != null) {
                auth.signOut()
            }
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        return view
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            // Display email from Auth immediately
            tvUserEmail.text = user.email

            // Fetch extra details from Firestore
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val fullName = document.getString("fullName") ?: "User"
                        tvUserName.text = fullName
                        
                        // Set Avatar initials
                        val initials = fullName.split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .take(2)
                            .joinToString("")
                            .uppercase()
                        ivAvatar.text = initials

                        // Initialize with zero balance/stats as requested for fresh start
                        tvTotalDonated.text = "₱0"
                        tvDonationCount.text = "0"
                        tvCampaignCount.text = "0"
                        tvSubDon.text = "0 donations this year"
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileFragment", "Error fetching user data", e)
                }
        } else {
            // Guest mode
            tvUserName.text = "Guest User"
            tvUserEmail.text = "Not logged in"
            ivAvatar.text = "G"
            tvTotalDonated.text = "₱0"
            tvDonationCount.text = "0"
            tvCampaignCount.text = "0"
            tvSubDon.text = "0 donations this year"
        }
    }
}
