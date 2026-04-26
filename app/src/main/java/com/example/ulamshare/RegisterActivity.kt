package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

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

        googleSignInClient = GoogleSignIn.getClient(this@RegisterActivity, gso)
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val accountTask = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = accountTask.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken.isNullOrBlank()) {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Google sign up failed: missing ID token",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@registerForActivityResult
                }
                firebaseAuthWithGoogle(idToken)
            } catch (e: ApiException) {
                Toast.makeText(
                    this@RegisterActivity,
                    "Google sign up failed: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnRegister = findViewById<Button>(R.id.btnSendOtp)
        val btnGoogleSignUp = findViewById<View>(R.id.btnGoogleSignUp)
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etMobileNumber = findViewById<EditText>(R.id.etMobileNumber)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)

        // Keep the button text as per the XML "Send OTP to Verify" or change it if you prefer
        btnRegister.text = "Send OTP to Verify"

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
                Toast.makeText(
                    this@RegisterActivity,
                    "Please fill all fields",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this@RegisterActivity) { task: Task<AuthResult> ->
                    if (task.isSuccessful) {
                        saveUserToFirestore(fullName, email, phone)
                    } else {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration failed: ${task.exception?.localizedMessage ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        btnGoogleSignUp.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this@RegisterActivity) { task: Task<AuthResult> ->
                if (task.isSuccessful) {
                    val user = auth.currentUser ?: return@addOnCompleteListener
                    // Check if user already exists in Firestore to avoid overwriting.
                    db.collection("users")
                        .document(user.uid)
                        .get()
                        .addOnSuccessListener { document: DocumentSnapshot ->
                            if (!document.exists()) {
                                saveUserToFirestore(
                                    fullName = user.displayName ?: "User",
                                    email = user.email ?: "",
                                    phone = ""
                                )
                            } else {
                                syncCampaignsAndProceed(user)
                            }
                        }
                } else {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Google authentication failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun syncCampaignsAndProceed(user: FirebaseUser) {
        CampaignAssignmentManager.syncForAuthenticatedUser(
            context = this@RegisterActivity,
            user = user,
            profileSeed = mapOf(
                "uid" to user.uid,
                "fullName" to (user.displayName ?: "User"),
                "email" to (user.email ?: "")
            ),
            onComplete = {
                Toast.makeText(
                    this@RegisterActivity,
                    "Google authentication successful",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMain()
            },
            onError = { error ->
                Toast.makeText(
                    this@RegisterActivity,
                    "Signed in, but campaign sync failed: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
                navigateToMain()
            }
        )
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
            context = this@RegisterActivity,
            user = user,
            profileSeed = userMap,
            onComplete = {
                Toast.makeText(
                    this@RegisterActivity,
                    "Step 1 Complete: Registration Successful",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToOtp(user.uid, email)
            },
            onError = { error ->
                Toast.makeText(
                    this@RegisterActivity,
                    "Error saving data: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun navigateToOtp(userId: String, email: String) {
        val intent = Intent(this@RegisterActivity, OtpActivity::class.java)
        intent.putExtra(OtpActivity.EXTRA_USER_ID, userId)
        intent.putExtra(OtpActivity.EXTRA_EMAIL, email)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this@RegisterActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
