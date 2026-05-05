package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var callbackManager: CallbackManager
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etMobile: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var verificationMethodGroup: RadioGroup

    private var isCheckingEmail = false
    private var lastSubmitAt = 0L
    private var selectedGoogleIdToken = ""
    private var selectedGoogleEmail = ""
    private var selectedVerificationMethod = OtpActivity.METHOD_SMS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        callbackManager = CallbackManager.Factory.create()
        setupGoogleSignUp()
        setupFacebookSignUp()

        applyBottomSafeArea()

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvLogin).setOnClickListener { finish() }

        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etMobile = findViewById(R.id.etMobile)
        etPassword = findViewById(R.id.etPassword)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        verificationMethodGroup = findViewById(R.id.verificationMethodGroup)
        updateSendButtonText()

        verificationMethodGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedVerificationMethod = if (checkedId == R.id.rbEmail) {
                OtpActivity.METHOD_EMAIL
            } else {
                OtpActivity.METHOD_SMS
            }
            updateSendButtonText()
        }

        findViewById<View>(R.id.btnGoogleSignUp).setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        findViewById<View>(R.id.btnFacebookSignUp).setOnClickListener {
            Log.d(FACEBOOK_TAG, "Facebook login started from register")
            LoginManager.getInstance().logInWithReadPermissions(
                this,
                listOf("public_profile")
            )
        }

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.toString().trim().equals(selectedGoogleEmail, ignoreCase = true)) {
                    selectedGoogleEmail = ""
                    selectedGoogleIdToken = ""
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        btnSendOtp.setOnClickListener {
            if (SystemClock.elapsedRealtime() - lastSubmitAt < CLICK_GUARD_MS) return@setOnClickListener
            lastSubmitAt = SystemClock.elapsedRealtime()
            validateAndContinue()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun setupGoogleSignUp() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, options)
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account.email.orEmpty().trim().lowercase(Locale.getDefault())
                val token = account.idToken.orEmpty()
                if (email.isBlank() || token.isBlank()) {
                    Toast.makeText(this, "Google sign up failed: missing account email", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }

                selectedGoogleEmail = email
                selectedGoogleIdToken = token
                etEmail.setText(email)
                if (etFullName.text.isBlank()) {
                    etFullName.setText(account.displayName.orEmpty().ifBlank { getString(R.string.hopegive_user) })
                }
                Log.d(TAG, "Google sign up account selected. email=$email, hasIdToken=${token.isNotBlank()}")
                Toast.makeText(this, "Google account selected. Sending email OTP to $email.", Toast.LENGTH_LONG).show()
                checkDuplicateEmail(
                    fullName = etFullName.text.toString().trim().ifBlank { getString(R.string.hopegive_user) },
                    email = email,
                    mobile = etMobile.text.toString().trim(),
                    password = "",
                    googleIdToken = token,
                    verificationMethod = OtpActivity.METHOD_EMAIL
                )
            } catch (error: ApiException) {
                Log.e(TAG, "Google sign up failed", error)
                Toast.makeText(
                    this,
                    "Google sign up failed: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupFacebookSignUp() {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(loginResult: LoginResult) {
                    Log.d(FACEBOOK_TAG, "Facebook login success from register")
                    handleFacebookAccessToken(loginResult.accessToken)
                }

                override fun onCancel() {
                    Log.d(FACEBOOK_TAG, "Facebook login cancelled from register")
                    Toast.makeText(
                        this@RegisterActivity,
                        getString(R.string.facebook_sign_in_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(error: FacebookException) {
                    Log.e(FACEBOOK_TAG, "Facebook sign-in failed", error)
                    Toast.makeText(
                        this@RegisterActivity,
                        FacebookAuthSupport.userFriendlyError(this@RegisterActivity, error, "Facebook"),
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
                Log.d(FACEBOOK_TAG, "Firebase Facebook sign-in success")
                FacebookAuthSupport.saveOrMergeSocialUserProfile(
                    context = this,
                    user = user,
                    provider = "facebook",
                    onComplete = {
                        Toast.makeText(
                            this,
                            getString(R.string.facebook_sign_up_success),
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
                Toast.makeText(
                    this,
                    FacebookAuthSupport.userFriendlyError(this, error, "Facebook"),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    private fun validateAndContinue() {
        if (isCheckingEmail) return

        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim().lowercase(Locale.getDefault())
        val mobile = etMobile.text.toString().trim()
        val password = etPassword.text.toString()
        val isGoogleRegistration = isSelectedGoogleEmail(email)

        when {
            fullName.isBlank() -> {
                etFullName.error = "Full name is required"
                etFullName.requestFocus()
                return
            }
            email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.error = "Enter a valid email address"
                etEmail.requestFocus()
                return
            }
            mobile.isBlank() -> {
                etMobile.error = "Mobile number is required"
                etMobile.requestFocus()
                return
            }
            selectedVerificationMethod == OtpActivity.METHOD_SMS &&
                formatPhilippinePhoneNumber(mobile) == null -> {
                etMobile.error = "Use a valid PH number, for example 09123456789 or +639123456789"
                etMobile.requestFocus()
                return
            }
            !isGoogleRegistration && password.length < MIN_PASSWORD_LENGTH -> {
                etPassword.error = "Password must be at least 6 characters"
                etPassword.requestFocus()
                return
            }
        }

        checkDuplicateEmail(
            fullName = fullName,
            email = email,
            mobile = mobile,
            password = if (isGoogleRegistration) "" else password,
            googleIdToken = if (isGoogleRegistration) selectedGoogleIdToken else "",
            verificationMethod = selectedVerificationMethod
        )
    }

    private fun checkDuplicateEmail(
        fullName: String,
        email: String,
        mobile: String,
        password: String,
        googleIdToken: String,
        verificationMethod: String
    ) {
        isCheckingEmail = true
        setLoading(true)

        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener { result ->
                val signInMethods = result.signInMethods.orEmpty()
                if (signInMethods.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "This email is already registered. Please log in instead.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    navigateToOtp(
                        fullName = fullName,
                        email = email,
                        mobile = mobile,
                        password = password,
                        googleIdToken = googleIdToken,
                        verificationMethod = verificationMethod
                    )
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Duplicate email check failed for $email", error)
                Toast.makeText(
                    this,
                    error.localizedMessage ?: "Unable to check this email. Please try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnCompleteListener {
                isCheckingEmail = false
                setLoading(false)
            }
    }

    private fun navigateToOtp(
        fullName: String,
        email: String,
        mobile: String,
        password: String,
        googleIdToken: String,
        verificationMethod: String
    ) {
        val intent = Intent(this, OtpActivity::class.java).apply {
            putExtra(OtpActivity.EXTRA_FULL_NAME, fullName)
            putExtra(OtpActivity.EXTRA_EMAIL, email)
            putExtra(OtpActivity.EXTRA_MOBILE, mobile)
            putExtra(OtpActivity.EXTRA_PASSWORD, password)
            putExtra(OtpActivity.EXTRA_CHANNEL, verificationMethod)
            putExtra(OtpActivity.EXTRA_VERIFICATION_METHOD, verificationMethod)
            putExtra(OtpActivity.EXTRA_AUTH_PROVIDER, if (googleIdToken.isBlank()) "password" else "google")
            putExtra(OtpActivity.EXTRA_GOOGLE_ID_TOKEN, googleIdToken)
        }
        startActivity(intent)
    }

    private fun setLoading(loading: Boolean) {
        btnSendOtp.isEnabled = !loading
        btnSendOtp.text = if (loading) "Checking..." else sendButtonText()
    }

    private fun updateSendButtonText() {
        if (::btnSendOtp.isInitialized && !isCheckingEmail) {
            btnSendOtp.text = sendButtonText()
        }
    }

    private fun sendButtonText(): String {
        return if (selectedVerificationMethod == OtpActivity.METHOD_EMAIL) {
            "Send OTP via Email"
        } else {
            "Send OTP via SMS"
        }
    }

    private fun applyBottomSafeArea() {
        val root = findViewById<android.view.View>(R.id.registerRoot)
        val baseBottomPadding = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = baseBottomPadding + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun isSelectedGoogleEmail(email: String): Boolean {
        return selectedGoogleIdToken.isNotBlank() &&
            selectedGoogleEmail.isNotBlank() &&
            email.equals(selectedGoogleEmail, ignoreCase = true)
    }

    private fun formatPhilippinePhoneNumber(rawPhone: String): String? {
        val compact = rawPhone.trim().replace(Regex("[\\s\\-()]"), "")
        return when {
            compact.matches(Regex("^09\\d{9}$")) -> "+63${compact.drop(1)}"
            compact.matches(Regex("^9\\d{9}$")) -> "+63$compact"
            compact.matches(Regex("^639\\d{9}$")) -> "+$compact"
            compact.matches(Regex("^\\+639\\d{9}$")) -> compact
            else -> null
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
        private const val CLICK_GUARD_MS = 1000L
        private const val TAG = "RegisterActivity"
        private const val FACEBOOK_TAG = "FacebookAuth"
    }
}
