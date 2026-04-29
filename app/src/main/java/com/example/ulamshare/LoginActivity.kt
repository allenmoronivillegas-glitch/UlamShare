package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager
    private val GOOGLE_SIGN_IN_REQUEST = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        callbackManager = CallbackManager.Factory.create()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnGoogleSignIn = findViewById<View>(R.id.btnGoogleSignIn)
        val btnFacebookSignIn = findViewById<View>(R.id.btnFacebookSignIn)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)

        setupFacebookLogin()

        btnBack.setOnClickListener {
            finish()
        }

        tvRegisterLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        handleAuthenticatedUser(
                            user = auth.currentUser,
                            successMessage = "Login Successful"
                        )
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST)
        }

        btnFacebookSignIn.setOnClickListener {
            Log.d(FACEBOOK_TAG, "Facebook login started")
            LoginManager.getInstance().logInWithReadPermissions(
                this,
                listOf("email", "public_profile")
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GOOGLE_SIGN_IN_REQUEST) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupFacebookLogin() {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(loginResult: LoginResult) {
                    Log.d(FACEBOOK_TAG, "Facebook login success")
                    handleFacebookAccessToken(loginResult.accessToken)
                }

                override fun onCancel() {
                    Log.d(FACEBOOK_TAG, "Facebook login cancelled")
                    Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.facebook_sign_in_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(error: FacebookException) {
                    Log.e(FACEBOOK_TAG, "Facebook login error", error)
                    Toast.makeText(
                        this@LoginActivity,
                        FacebookAuthSupport.userFriendlyError(this@LoginActivity, error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        FacebookAuthSupport.signInWithAccessToken(
            accessToken = token,
            onSuccess = { user ->
                FacebookAuthSupport.mergeFacebookUserProfile(
                    context = this,
                    user = user,
                    onComplete = {
                        Toast.makeText(
                            this,
                            getString(R.string.facebook_sign_in_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToHome()
                    },
                    onError = { error ->
                        Log.e(FACEBOOK_TAG, "Unable to merge Facebook user profile", error)
                        Toast.makeText(
                            this,
                            FacebookAuthSupport.userFriendlyError(this, error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            },
            onError = { error ->
                Toast.makeText(
                    this,
                    FacebookAuthSupport.userFriendlyError(this, error),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    handleAuthenticatedUser(
                        user = auth.currentUser,
                        successMessage = "Google Login Successful"
                    )
                } else {
                    Toast.makeText(this, "Firebase auth with Google failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun handleAuthenticatedUser(user: FirebaseUser?, successMessage: String) {
        if (user == null) {
            Toast.makeText(this, "Unable to load your account", Toast.LENGTH_LONG).show()
            return
        }

        val profileSeed = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "email" to (user.email ?: "")
        )
        user.displayName?.takeIf { it.isNotBlank() }?.let { profileSeed["fullName"] = it }

        CampaignAssignmentManager.syncForAuthenticatedUser(
            context = this,
            user = user,
            profileSeed = profileSeed,
            onComplete = {
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                navigateToHome()
            },
            onError = { error ->
                Toast.makeText(
                    this,
                    "Logged in, but campaign sync failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                navigateToHome()
            }
        )
    }

    private companion object {
        const val FACEBOOK_TAG = "FacebookAuth"
    }
}
