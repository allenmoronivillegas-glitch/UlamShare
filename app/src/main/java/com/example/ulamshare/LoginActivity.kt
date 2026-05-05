package com.example.ulamshare

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle
import android.util.Base64
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
import java.security.MessageDigest

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager
    private val GOOGLE_SIGN_IN_REQUEST = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        if (isDebugBuild()) {
            printFacebookKeyHash()
        }

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
            Log.d(GOOGLE_TAG, "Google sign-in started")
            googleSignInClient.signOut().addOnCompleteListener {
                Log.d(GOOGLE_TAG, "Showing Google account chooser")
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST)
            }
        }

        btnFacebookSignIn.setOnClickListener {
            Log.d(FACEBOOK_TAG, "Facebook login started")
            LoginManager.getInstance().logOut()
            LoginManager.getInstance().logInWithReadPermissions(
                this,
                listOf("public_profile")
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
                Log.d(GOOGLE_TAG, "Google account selected: ${account.email.orEmpty()}")
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.e(GOOGLE_TAG, "Google sign-in failed", e)
                Toast.makeText(
                    this,
                    FacebookAuthSupport.userFriendlyError(this, e, "Google"),
                    Toast.LENGTH_LONG
                ).show()
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
                    Log.e(FACEBOOK_TAG, "Facebook login failed", error)
                    Toast.makeText(
                        this@LoginActivity,
                        FacebookAuthSupport.userFriendlyError(this@LoginActivity, error, "Facebook"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun handleFacebookAccessToken(token: AccessToken) {
        Log.d(FACEBOOK_TAG, "Facebook access token received")
        FacebookAuthSupport.signInWithAccessToken(
            accessToken = token,
            onSuccess = { user ->
                Log.d(FACEBOOK_TAG, "Firebase Facebook sign-in success")
                FacebookAuthSupport.saveOrMergeSocialUserProfile(
                    context = this,
                    user = user,
                    provider = "facebook",
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
                            FacebookAuthSupport.userFriendlyError(this, error, "Facebook"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            },
            onError = { error ->
                Log.e(FACEBOOK_TAG, "Firebase Facebook sign-in failed", error)
                Toast.makeText(
                    this,
                    FacebookAuthSupport.userFriendlyError(this, error, "Facebook"),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun printFacebookKeyHash() {
        try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info: PackageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                val info: PackageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                info.signatures.orEmpty()
            }

            signatures.forEach { signature: Signature ->
                val messageDigest = MessageDigest.getInstance("SHA")
                messageDigest.update(signature.toByteArray())
                val keyHash = Base64.encodeToString(messageDigest.digest(), Base64.NO_WRAP)
                Log.d(KEY_HASH_TAG, "Key Hash: $keyHash")
            }
        } catch (error: Exception) {
            Log.e(KEY_HASH_TAG, "Unable to get key hash", error)
        }
    }

    private fun isDebugBuild(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(GOOGLE_TAG, "Firebase Google sign-in success")
                    val user = auth.currentUser
                    if (user == null) {
                        Toast.makeText(this, "Unable to load your Google account", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    FacebookAuthSupport.saveOrMergeSocialUserProfile(
                        context = this,
                        user = user,
                        provider = "google",
                        onComplete = {
                            Toast.makeText(this, "Google Login Successful", Toast.LENGTH_SHORT).show()
                            navigateToHome()
                        },
                        onError = { error ->
                            Log.e(GOOGLE_TAG, "Unable to merge Google user profile", error)
                            Toast.makeText(
                                this,
                                "Logged in, but profile sync failed: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            navigateToHome()
                        }
                    )
                } else {
                    val error = task.exception ?: IllegalStateException("Firebase Google sign-in failed.")
                    Log.e(GOOGLE_TAG, "Google sign-in failed", error)
                    Toast.makeText(
                        this,
                        FacebookAuthSupport.userFriendlyError(this, error, "Google"),
                        Toast.LENGTH_LONG
                    ).show()
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
        PrivacyDisplayHelper.publicName(user.displayName, "")
            .takeIf { it.isNotBlank() }
            ?.let { profileSeed["fullName"] = it }

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
        const val GOOGLE_TAG = "GoogleAuth"
        const val KEY_HASH_TAG = "FacebookKeyHash"
    }
}
