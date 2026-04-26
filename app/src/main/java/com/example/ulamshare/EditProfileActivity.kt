package com.example.ulamshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var ivEditAvatar: TextView
    private lateinit var ivEditAvatarPhoto: ImageView
    private lateinit var btnEditAvatarPhoto: ImageView
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageView
    private var selectedProfileImageUri: Uri? = null

    private val profilePhotoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedProfileImageUri = uri
            persistReadPermission(uri)
            displayProfilePhoto(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etFullName = findViewById(R.id.etEditFullName)
        etEmail = findViewById(R.id.etEditEmail)
        etPhone = findViewById(R.id.etEditPhone)
        ivEditAvatar = findViewById(R.id.ivEditAvatar)
        ivEditAvatarPhoto = findViewById(R.id.ivEditAvatarPhoto)
        ivEditAvatarPhoto.clipToOutline = true
        btnEditAvatarPhoto = findViewById(R.id.btnEditAvatarPhoto)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        loadUserData()

        btnBack.setOnClickListener {
            finish()
        }

        btnEditAvatarPhoto.setOnClickListener {
            profilePhotoPicker.launch(arrayOf("image/*"))
        }

        btnSave.setOnClickListener {
            saveUserData()
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            etEmail.setText(user.email)
            val fallbackName = user.displayName?.takeIf { it.isNotBlank() } ?: "User"
            updateInitials(fallbackName)
            displayProfilePhoto(savedProfilePhotoUri(user.uid))
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val fullName = document.getString("fullName") ?: fallbackName
                        val phone = document.getString("mobileNumber") ?: ""
                        val profilePhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty()
                        val profilePhotoUrl = document.getString("profilePhotoUrl").orEmpty()
                        
                        etFullName.setText(fullName)
                        etPhone.setText(phone)
                        
                        updateInitials(fullName)
                        displayProfilePhoto(
                            profilePhotoLocalUri
                                .ifBlank { savedProfilePhotoUri(user.uid) }
                                .ifBlank { profilePhotoUrl }
                        )
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("EditProfileActivity", "Unable to load profile", error)
                }
        } else {
            etFullName.setText("Guest User")
            etEmail.setText("Not logged in")
            etPhone.setText("")
            updateInitials("Guest User")
            displayProfilePhoto(savedProfilePhotoUri(GUEST_PROFILE_KEY))
        }
    }

    private fun updateInitials(fullName: String) {
        val initials = fullName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
        ivEditAvatar.text = if (initials.isNotEmpty()) initials else "G"
    }

    private fun saveUserData() {
        val user = auth.currentUser
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (fullName.isEmpty()) {
            etFullName.error = "Full name is required"
            return
        }

        if (user == null) {
            selectedProfileImageUri?.let { uri ->
                saveProfilePhotoUri(GUEST_PROFILE_KEY, uri.toString())
            }
            Toast.makeText(this, R.string.profile_photo_saved_local, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val updates = hashMapOf<String, Any>(
            "fullName" to fullName,
            "email" to email,
            "mobileNumber" to phone
        )
        selectedProfileImageUri?.let { uri ->
            val uriString = uri.toString()
            saveProfilePhotoUri(user.uid, uriString)
            updates["profilePhotoLocalUri"] = uriString
            updates["profilePhotoUrl"] = ""
        }

        btnSave.isEnabled = false
        db.collection("users").document(user.uid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (error: SecurityException) {
            Log.w("EditProfileActivity", "Unable to persist profile photo URI permission", error)
        }
    }

    private fun displayProfilePhoto(uriString: String) {
        if (uriString.isBlank()) {
            ivEditAvatarPhoto.setImageDrawable(null)
            ivEditAvatarPhoto.visibility = View.GONE
            ivEditAvatar.visibility = View.VISIBLE
            return
        }

        ivEditAvatarPhoto.visibility = View.VISIBLE
        ivEditAvatar.visibility = View.GONE
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            CampaignImageLoader.load(ivEditAvatarPhoto, uriString, R.drawable.plant)
        } else {
            runCatching {
                ivEditAvatarPhoto.setImageURI(Uri.parse(uriString))
            }.onFailure { error ->
                Log.w("EditProfileActivity", "Unable to display selected profile photo", error)
                ivEditAvatarPhoto.setImageDrawable(null)
                ivEditAvatarPhoto.visibility = View.GONE
                ivEditAvatar.visibility = View.VISIBLE
            }
        }
    }

    private fun profilePrefs() =
        getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

    private fun savedProfilePhotoUri(key: String): String =
        profilePrefs().getString(profilePhotoPrefKey(key), "").orEmpty()

    private fun saveProfilePhotoUri(key: String, uriString: String) {
        profilePrefs().edit()
            .putString(profilePhotoPrefKey(key), uriString)
            .apply()
    }

    private fun profilePhotoPrefKey(key: String): String = "profile_photo_uri_$key"

    private companion object {
        const val PROFILE_PREFS = "profile_preferences"
        const val GUEST_PROFILE_KEY = "guest"
    }
}
