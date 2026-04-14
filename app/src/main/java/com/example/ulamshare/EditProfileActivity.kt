package com.example.ulamshare

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var ivEditAvatar: TextView
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etFullName = findViewById(R.id.etEditFullName)
        etEmail = findViewById(R.id.etEditEmail)
        etPhone = findViewById(R.id.etEditPhone)
        ivEditAvatar = findViewById(R.id.ivEditAvatar)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        loadUserData()

        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveUserData()
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            etEmail.setText(user.email)
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val fullName = document.getString("fullName") ?: ""
                        val phone = document.getString("mobileNumber") ?: ""
                        
                        etFullName.setText(fullName)
                        etPhone.setText(phone)
                        
                        updateInitials(fullName)
                    }
                }
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
        val user = auth.currentUser ?: return
        val fullName = etFullName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (fullName.isEmpty()) {
            etFullName.error = "Full name is required"
            return
        }

        val updates = hashMapOf<String, Any>(
            "fullName" to fullName,
            "mobileNumber" to phone
        )

        btnSave.isEnabled = false
        db.collection("users").document(user.uid)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnSave.isEnabled = true
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
