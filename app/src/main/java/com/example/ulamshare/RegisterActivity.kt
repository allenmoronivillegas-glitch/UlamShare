package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private val GOOGLE_SIGN_IN_REQUEST = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnRegister = findViewById<Button>(R.id.btnSendOtp)
        val btnGoogleSignUp = findViewById<View>(R.id.btnGoogleSignUp)
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etMobileNumber = findViewById<EditText>(R.id.etMobileNumber)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)

        btnRegister.text = "Register"

        btnBack.setOnClickListener {
            finish()
        }

        tvLoginLink.setOnClickListener {
            finish() // Go back to Login
        }

        btnRegister.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val phone = etMobileNumber.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        saveUserToFirestore(fullName, email, phone)
                    } else {
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnGoogleSignUp.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GOOGLE_SIGN_IN_REQUEST) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign up failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Check if user already exists in Firestore to avoid overwriting
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    saveUserToFirestore(user.displayName ?: "User", user.email ?: "", "")
                                } else {
                                    CampaignAssignmentManager.syncForAuthenticatedUser(
                                        context = this,
                                        user = user,
                                        profileSeed = mapOf(
                                            "uid" to user.uid,
                                            "fullName" to (user.displayName ?: "User"),
                                            "email" to (user.email ?: "")
                                        ),
                                        onComplete = {
                                            Toast.makeText(this, "Google authentication successful", Toast.LENGTH_SHORT).show()
                                            navigateToMain()
                                        },
                                        onError = { e ->
                                            Toast.makeText(
                                                this,
                                                "Signed in, but campaign sync failed: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            navigateToMain()
                                        }
                                    )
                                }
                            }
                    }
                } else {
                    Toast.makeText(this, "Google authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore(fullName: String, email: String, phone: String) {
        val user = auth.currentUser ?: return
        val userMap = hashMapOf<String, Any>(
            "uid" to user.uid,
            "fullName" to fullName,
            "email" to email,
            "phone" to phone
        )

        CampaignAssignmentManager.syncForAuthenticatedUser(
            context = this,
            user = user,
            profileSeed = userMap,
            onComplete = {
                Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()
                navigateToMain()
            },
            onError = { e ->
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
