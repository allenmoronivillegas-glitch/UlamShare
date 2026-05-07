package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit

class OtpActivity : AppCompatActivity() {

    private lateinit var otpBoxes: List<EditText>
    private lateinit var tvEmail: TextView
    private lateinit var tvError: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvResend: TextView
    private lateinit var tvSupport: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnVerify: Button
    private lateinit var errorContainer: View
    private lateinit var auth: FirebaseAuth

    private var userId: String = ""
    private var fullName: String = ""
    private var email: String = ""
    private var mobile: String = ""
    private var password: String = ""
    private var channel: String = "email"
    private var selectedMethod: String = METHOD_SMS
    private var authProvider: String = "password"
    private var googleIdToken: String = ""
    private var requestId: String = ""
    private var phoneVerificationId: String = ""
    private var formattedPhoneNumber: String = ""
    private var smsResendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var timer: CountDownTimer? = null
    private var isSendingOtp = false
    private var isVerifyingOtp = false
    private var isFinalizingRegistration = false
    private var isErrorState = false
    private var lastResendClickAt = 0L
    private var lastVerifyClickAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        bindViews()

        auth = FirebaseAuth.getInstance()

        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        fullName = intent.getStringExtra(EXTRA_FULL_NAME).orEmpty()
        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        mobile = intent.getStringExtra(EXTRA_MOBILE).orEmpty()
        password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        selectedMethod = intent.getStringExtra(EXTRA_VERIFICATION_METHOD)
            .orEmpty()
            .ifBlank { intent.getStringExtra(EXTRA_CHANNEL).orEmpty() }
            .ifBlank { METHOD_SMS }
            .lowercase(Locale.US)
        if (selectedMethod !in setOf(METHOD_SMS, METHOD_EMAIL)) {
            selectedMethod = METHOD_SMS
        }
        channel = selectedMethod
        authProvider = intent.getStringExtra(EXTRA_AUTH_PROVIDER).orEmpty().ifBlank { "password" }
        googleIdToken = intent.getStringExtra(EXTRA_GOOGLE_ID_TOKEN).orEmpty()
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()

        if (email.isBlank() && selectedMethod == METHOD_EMAIL) {
            showError("Email address is missing. Please go back and try again.")
        }

        setupOtpInputs()
        setupClickListeners()
        updateVerificationCopy()

        if (requestId.isBlank()) {
            requestOtp(isResend = false)
        } else {
            startTimer()
        }
    }

    private fun bindViews() {
        tvEmail = findViewById(R.id.tvEmail)
        tvError = findViewById(R.id.tvError)
        tvTimer = findViewById(R.id.tvTimer)
        tvResend = findViewById(R.id.tvResend)
        tvSupport = findViewById(R.id.tvSupport)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        btnVerify = findViewById(R.id.btnVerify)
        errorContainer = findViewById(R.id.errorContainer)
        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        otpBoxes = listOf(
            findViewById(R.id.otp1),
            findViewById(R.id.otp2),
            findViewById(R.id.otp3),
            findViewById(R.id.otp4),
            findViewById(R.id.otp5),
            findViewById(R.id.otp6)
        )
    }

    private fun setupClickListeners() {
        btnVerify.setOnClickListener {
            if (isRapidClick(lastVerifyClickAt, VERIFY_CLICK_GUARD_MS)) return@setOnClickListener
            lastVerifyClickAt = SystemClock.elapsedRealtime()
            verifyOtp()
        }

        tvResend.setOnClickListener {
            if (isRapidClick(lastResendClickAt, RESEND_COOLDOWN_MS)) {
                Toast.makeText(this, "Please wait before requesting another OTP.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastResendClickAt = SystemClock.elapsedRealtime()
            requestOtp(isResend = true)
        }

        tvSupport.setOnClickListener {
            switchVerificationMethod()
        }
    }

    private fun setupOtpInputs() {
        otpBoxes.forEachIndexed { index, editText ->
            editText.filters = arrayOf(InputFilter.LengthFilter(1))

            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val value = s?.toString().orEmpty()
                    val digitsOnly = value.filter(Char::isDigit).take(1)
                    if (value != digitsOnly) {
                        editText.setText(digitsOnly)
                        editText.setSelection(digitsOnly.length)
                        return
                    }

                    hideError()
                    updateOtpBoxBackgrounds()

                    if (digitsOnly.isNotEmpty() && index < otpBoxes.lastIndex) {
                        otpBoxes[index + 1].requestFocus()
                    } else if (index == otpBoxes.lastIndex && getOtpCode().length == OTP_LENGTH) {
                        hideKeyboard(editText)
                    }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })

            editText.setOnKeyListener { _, keyCode, event ->
                if (
                    keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    editText.text.isEmpty() &&
                    index > 0
                ) {
                    otpBoxes[index - 1].requestFocus()
                    otpBoxes[index - 1].setSelection(otpBoxes[index - 1].text.length)
                    return@setOnKeyListener true
                }
                false
            }

            editText.setOnFocusChangeListener { _, _ ->
                updateOtpBoxBackgrounds()
            }
        }
    }

    private fun requestOtp(isResend: Boolean) {
        if (DEBUG_OTP_MODE) {
            requestDebugOtp(isResend)
            return
        }

        if (selectedMethod == METHOD_SMS) {
            requestSmsOtp(isResend)
        } else {
            requestEmailOtp(isResend)
        }
    }

    private fun requestDebugOtp(isResend: Boolean) {
        if (isSendingOtp) return

        isSendingOtp = true
        setLoadingState(sending = true)
        hideError()

        val otp = String.format(Locale.US, "%06d", Random().nextInt(1_000_000))
        val expiresAt = System.currentTimeMillis() + OTP_EXPIRY_MS
        debugOtpPrefs()
            .edit()
            .putString(DEBUG_OTP_CODE_KEY, otp)
            .putLong(DEBUG_OTP_EXPIRES_AT_KEY, expiresAt)
            .putInt(DEBUG_OTP_ATTEMPTS_KEY, 0)
            .apply()

        isSendingOtp = false
        setLoadingState(sending = false)
        clearOtpInputs()
        updateVerificationCopy(otpSent = true)
        startTimer()

        Log.d(TAG, "Debug OTP generated for method=$selectedMethod. Code is shown only in debug mode.")
        Toast.makeText(this, "Test OTP: $otp", Toast.LENGTH_LONG).show()
        showDebugOtpDialog(otp, isResend)
    }

    private fun requestEmailOtp(isResend: Boolean) {
        if (isSendingOtp || email.isBlank()) return

        isSendingOtp = true
        setLoadingState(sending = true)
        hideError()

        val request = SendOtpRequest(
            userId = userId.ifBlank { email },
            destination = email,
            channel = METHOD_EMAIL
        )
        Log.d(TAG, "Selected method: $selectedMethod")
        Log.d(TAG, "Sending Email OTP to: $email")

        OtpApiClient.service.sendOtp(request).enqueue(object : Callback<SendOtpResponse> {
            override fun onResponse(call: Call<SendOtpResponse>, response: Response<SendOtpResponse>) {
                isSendingOtp = false
                setLoadingState(sending = false)

                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.requestId.isNotBlank()) {
                    requestId = body.requestId
                    clearOtpInputs()
                    updateVerificationCopy(otpSent = true)
                    startTimer()
                    Toast.makeText(
                        this@OtpActivity,
                        if (isResend) "New OTP sent." else body.message.ifBlank { "OTP sent." },
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val message = body?.message ?: backendErrorMessage(response, "Unable to send email OTP. Please try again.")
                    Log.e(TAG, "Email OTP failed. httpCode=${response.code()}, message=$message")
                    showError(message)
                }
            }

            override fun onFailure(call: Call<SendOtpResponse>, throwable: Throwable) {
                isSendingOtp = false
                setLoadingState(sending = false)
                Log.e(TAG, "Email OTP failed", throwable)
                showError(throwable.localizedMessage ?: "Unable to send email OTP. Please try again.")
            }
        })
    }

    private fun requestSmsOtp(isResend: Boolean) {
        if (isSendingOtp) return

        Log.d(TAG, "Raw phone: $mobile")
        val phone = formatPhilippinePhoneNumber(mobile)
        if (phone == null) {
            showError("Please use a valid +63 phone number format.")
            return
        }

        formattedPhoneNumber = phone
        isSendingOtp = true
        setLoadingState(sending = true)
        hideError()

        Log.d(TAG, "Selected method: $selectedMethod")
        Log.d(TAG, "Formatted phone: $formattedPhoneNumber")
        Log.d(TAG, "Sending SMS OTP to: $formattedPhoneNumber")
        Log.d(TAG, "Calling verifyPhoneNumber")

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "SMS verification completed automatically")
                isSendingOtp = false
                setLoadingState(sending = false)
                verifySmsCredential(credential, autoVerified = true)
            }

            override fun onVerificationFailed(error: FirebaseException) {
                isSendingOtp = false
                setLoadingState(sending = false)
                Log.e(TAG, "onVerificationFailed", error)
                showError(smsErrorMessage(error))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                isSendingOtp = false
                setLoadingState(sending = false)
                phoneVerificationId = verificationId
                smsResendToken = token
                Log.d(TAG, "onCodeSent verificationId received")
                clearOtpInputs()
                updateVerificationCopy(otpSent = true)
                startTimer()
                Toast.makeText(
                    this@OtpActivity,
                    firebaseTestNumberMessage(isResend),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhoneNumber)
            .setTimeout(SMS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)

        if (isResend && smsResendToken != null) {
            optionsBuilder.setForceResendingToken(smsResendToken!!)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    private fun verifyOtp() {
        if (isVerifyingOtp || isSendingOtp) return

        val otp = getOtpCode()
        if (otp.length != OTP_LENGTH) {
            showError("Please enter the 6-digit code.")
            return
        }

        if (DEBUG_OTP_MODE) {
            verifyDebugOtp(otp)
            return
        }

        if (selectedMethod == METHOD_SMS) {
            verifySmsCode(otp)
        } else {
            verifyEmailOtp(otp)
        }
    }

    private fun verifyDebugOtp(otp: String) {
        val prefs = debugOtpPrefs()
        val savedOtp = prefs.getString(DEBUG_OTP_CODE_KEY, "").orEmpty()
        val expiresAt = prefs.getLong(DEBUG_OTP_EXPIRES_AT_KEY, 0L)
        val attempts = prefs.getInt(DEBUG_OTP_ATTEMPTS_KEY, 0)

        if (savedOtp.isBlank()) {
            showError("Verification request is missing. Please resend the code.")
            return
        }

        if (System.currentTimeMillis() > expiresAt) {
            clearDebugOtp()
            showError("OTP expired. Please resend.")
            return
        }

        if (attempts >= DEBUG_OTP_MAX_ATTEMPTS) {
            showError("Too many attempts. Please request a new code.")
            return
        }

        if (otp != savedOtp) {
            prefs.edit().putInt(DEBUG_OTP_ATTEMPTS_KEY, attempts + 1).apply()
            showError("Invalid OTP.")
            return
        }

        clearDebugOtp()
        timer?.cancel()
        Toast.makeText(this, "OTP verified", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "OTP verified successfully using debug mode and method=$selectedMethod")
        completeVerifiedFlow(selectedMethod)
    }

    private fun verifyEmailOtp(otp: String) {
        if (requestId.isBlank()) {
            showError("Verification request is missing. Please resend the code.")
            return
        }

        isVerifyingOtp = true
        setLoadingState(verifying = true)
        hideError()

        OtpApiClient.service.verifyOtp(VerifyOtpRequest(requestId = requestId, otp = otp))
            .enqueue(object : Callback<VerifyOtpResponse> {
                override fun onResponse(
                    call: Call<VerifyOtpResponse>,
                    response: Response<VerifyOtpResponse>
                ) {
                    isVerifyingOtp = false
                    setLoadingState(verifying = false)

                    val body = response.body()
                    if (
                        response.isSuccessful &&
                        body?.success == true &&
                        body.status.equals("VERIFIED", ignoreCase = true)
                    ) {
                        timer?.cancel()
                        Toast.makeText(this@OtpActivity, "OTP verified", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "OTP verified successfully using: $selectedMethod")
                        completeVerifiedFlow(METHOD_EMAIL)
                    } else {
                        val message = body?.message ?: backendErrorMessage(response, "Invalid or expired OTP")
                        Log.e(TAG, "OTP verify failed. httpCode=${response.code()}, message=$message")
                        showError(message)
                    }
                }

                override fun onFailure(call: Call<VerifyOtpResponse>, throwable: Throwable) {
                    isVerifyingOtp = false
                    setLoadingState(verifying = false)
                    Log.e(TAG, "OTP verify network/backend failure", throwable)
                    showError(throwable.localizedMessage ?: "Unable to verify OTP. Please try again.")
                }
            })
    }

    private fun verifySmsCode(otp: String) {
        if (phoneVerificationId.isBlank()) {
            showError("SMS verification request is missing. Please resend the code.")
            return
        }

        val credential = PhoneAuthProvider.getCredential(phoneVerificationId, otp)
        verifySmsCredential(credential, autoVerified = false)
    }

    private fun verifySmsCredential(credential: PhoneAuthCredential, autoVerified: Boolean) {
        if (isVerifyingOtp || isFinalizingRegistration) return

        isVerifyingOtp = true
        setLoadingState(verifying = true)
        hideError()

        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                isVerifyingOtp = false
                setLoadingState(verifying = false)

                val signedInUser = result.user
                val existingEmail = signedInUser?.email.orEmpty()
                if (existingEmail.isNotBlank() && !existingEmail.equals(email, ignoreCase = true)) {
                    Log.e(TAG, "SMS credential belongs to a different existing Firebase user.")
                    auth.signOut()
                    showError("This phone number is already linked to another account.")
                    return@addOnSuccessListener
                }

                timer?.cancel()
                Toast.makeText(
                    this,
                    if (autoVerified) "Phone verified automatically." else "Phone verified.",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d(TAG, "OTP verified successfully using: $selectedMethod")
                completeVerifiedFlow(METHOD_SMS)
            }
            .addOnFailureListener { error ->
                isVerifyingOtp = false
                setLoadingState(verifying = false)
                Log.e(TAG, "SMS OTP failed", error)
                showError(smsErrorMessage(error))
            }
    }

    private fun completeVerifiedFlow(verifiedMethod: String) {
        if (!hasPendingRegistration()) {
            navigateToRegistrationStep3()
            return
        }

        finalizeRegistrationAfterOtp(verifiedMethod)
    }

    private fun hasPendingRegistration(): Boolean {
        val hasPasswordRegistration = password.isNotBlank()
        val hasGoogleRegistration = authProvider == "google" && googleIdToken.isNotBlank()
        return fullName.isNotBlank() &&
            email.isNotBlank() &&
            (hasPasswordRegistration || hasGoogleRegistration)
    }

    private fun finalizeRegistrationAfterOtp(verifiedMethod: String) {
        if (isFinalizingRegistration) return

        isFinalizingRegistration = true
        setLoadingState(finalizing = true)

        if (authProvider == "google" && googleIdToken.isNotBlank()) {
            finalizeGoogleRegistrationAfterOtp(verifiedMethod)
            return
        }

        if (!DEBUG_OTP_MODE && verifiedMethod == METHOD_SMS && auth.currentUser != null) {
            linkEmailPasswordToSmsUser(verifiedMethod)
        } else {
            createEmailPasswordAccount(verifiedMethod)
        }
    }

    private fun createEmailPasswordAccount(verifiedMethod: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    isFinalizingRegistration = false
                    setLoadingState()
                    showError("Account was verified, but user creation failed. Please try again.")
                    return@addOnSuccessListener
                }

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()

                user.updateProfile(profileUpdates)
                    .addOnCompleteListener {
                        saveVerifiedUserProfile(user, verifiedMethod)
                    }
            }
            .addOnFailureListener { error ->
                handleAccountFinalizeFailure(error, "Unable to create your account. Please try again.")
            }
    }

    private fun linkEmailPasswordToSmsUser(verifiedMethod: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            createEmailPasswordAccount(verifiedMethod)
            return
        }

        val credential = EmailAuthProvider.getCredential(email, password)
        currentUser.linkWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    isFinalizingRegistration = false
                    setLoadingState()
                    showError("Phone was verified, but account linking failed. Please try again.")
                    return@addOnSuccessListener
                }

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()

                user.updateProfile(profileUpdates)
                    .addOnCompleteListener {
                        saveVerifiedUserProfile(user, verifiedMethod)
                    }
            }
            .addOnFailureListener { error ->
                handleAccountFinalizeFailure(error, "Phone was verified, but account creation failed. Please try again.")
            }
    }

    private fun finalizeGoogleRegistrationAfterOtp(verifiedMethod: String) {
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)

        if (!DEBUG_OTP_MODE && verifiedMethod == METHOD_SMS && auth.currentUser != null) {
            auth.currentUser?.linkWithCredential(credential)
                ?.addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        isFinalizingRegistration = false
                        setLoadingState()
                        showError("Phone was verified, but Google sign up failed. Please try again.")
                        return@addOnSuccessListener
                    }

                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName.ifBlank { user.displayName.orEmpty() })
                        .build()

                    user.updateProfile(profileUpdates)
                        .addOnCompleteListener {
                            saveVerifiedUserProfile(user, verifiedMethod)
                        }
                }
                ?.addOnFailureListener { error ->
                    handleAccountFinalizeFailure(error, "Phone was verified, but Google sign up failed. Please try again.")
                }
            return
        }

        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    isFinalizingRegistration = false
                    setLoadingState()
                    showError("Account was verified, but Google sign up failed. Please try again.")
                    return@addOnSuccessListener
                }

                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName.ifBlank { user.displayName.orEmpty() })
                    .build()

                user.updateProfile(profileUpdates)
                    .addOnCompleteListener {
                        saveVerifiedUserProfile(user, verifiedMethod)
                    }
            }
            .addOnFailureListener { error ->
                handleAccountFinalizeFailure(error, "Unable to finish Google sign up. Please try again.")
            }
    }

    private fun handleAccountFinalizeFailure(error: Exception, fallback: String) {
        isFinalizingRegistration = false
        setLoadingState()
        val message = if (error is FirebaseAuthUserCollisionException) {
            "This email is already registered. Please log in instead."
        } else {
            error.localizedMessage ?: fallback
        }
        showError(message)
    }

    private fun saveVerifiedUserProfile(user: FirebaseUser, verifiedMethod: String) {
        val phoneForProfile = formatPhilippinePhoneNumber(mobile) ?: mobile
        val profile = mapOf(
            "uid" to user.uid,
            "fullName" to fullName,
            "email" to email,
            "mobile" to phoneForProfile,
            "phone" to phoneForProfile,
            "role" to "user",
            "authProvider" to "email",
            "isActiveUser" to true,
            "isDuplicate" to false,
            "recordType" to "user",
            "friendsCount" to 0,
            "followingCount" to 0,
            "followersCount" to 0,
            "totalDonated" to 0,
            "donationsCount" to 0,
            "campaignsDonatedCount" to 0,
            "emailVerifiedByOtp" to (verifiedMethod == METHOD_EMAIL),
            "phoneVerifiedByOtp" to (verifiedMethod == METHOD_SMS),
            "verificationMethodUsed" to verifiedMethod,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "verified" to true
        )

        Firebase.firestore.collection("users")
            .document(user.uid)
            .set(profile, SetOptions.merge())
            .addOnSuccessListener {
                val syncSeed = mapOf(
                    "uid" to user.uid,
                    "fullName" to fullName,
                    "email" to email,
                    "mobile" to phoneForProfile,
                    "phone" to phoneForProfile,
                    "authProvider" to "email",
                    "emailVerifiedByOtp" to (verifiedMethod == METHOD_EMAIL),
                    "phoneVerifiedByOtp" to (verifiedMethod == METHOD_SMS),
                    "verificationMethodUsed" to verifiedMethod,
                    "verified" to true
                )
                CampaignAssignmentManager.syncForAuthenticatedUser(
                    context = this,
                    user = user,
                    profileSeed = syncSeed,
                    onComplete = {
                        isFinalizingRegistration = false
                        setLoadingState()
                        Toast.makeText(this, "Registration Complete!", Toast.LENGTH_SHORT).show()
                        navigateToRegistrationStep3()
                    },
                    onError = { error ->
                        isFinalizingRegistration = false
                        setLoadingState()
                        Toast.makeText(
                            this,
                            "Account created, but campaign sync failed: ${error.localizedMessage ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateToRegistrationStep3()
                    }
                )
            }
            .addOnFailureListener { error ->
                isFinalizingRegistration = false
                setLoadingState()
                showError(error.localizedMessage ?: "Account created, but profile setup failed.")
            }
    }

    private fun navigateToRegistrationStep3() {
        val intent = Intent(this, RegistrationStep3Activity::class.java)
        intent.putExtra(RegistrationStep3Activity.EXTRA_FULL_NAME, fullName)
        intent.putExtra(RegistrationStep3Activity.EXTRA_EMAIL, email)
        intent.putExtra(RegistrationStep3Activity.EXTRA_MOBILE, formatPhilippinePhoneNumber(mobile) ?: mobile)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showDebugOtpDialog(otp: String, isResend: Boolean) {
        val title = if (isResend) "New Debug OTP" else "Debug OTP"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Test OTP: $otp\n\nThis code is shown only while DEBUG_OTP_MODE is true. Do not use this flow in production.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun debugOtpPrefs() =
        getSharedPreferences(DEBUG_OTP_PREFS, MODE_PRIVATE)

    private fun clearDebugOtp() {
        debugOtpPrefs()
            .edit()
            .remove(DEBUG_OTP_CODE_KEY)
            .remove(DEBUG_OTP_EXPIRES_AT_KEY)
            .remove(DEBUG_OTP_ATTEMPTS_KEY)
            .apply()
    }

    private fun switchVerificationMethod() {
        if (isSendingOtp || isVerifyingOtp || isFinalizingRegistration) return

        selectedMethod = if (selectedMethod == METHOD_SMS) METHOD_EMAIL else METHOD_SMS
        channel = selectedMethod
        requestId = ""
        phoneVerificationId = ""
        smsResendToken = null
        if (DEBUG_OTP_MODE) {
            clearDebugOtp()
        }
        timer?.cancel()
        clearOtpInputs()
        hideError()
        updateVerificationCopy(otpSent = false)
        requestOtp(isResend = false)
    }

    private fun updateVerificationCopy(otpSent: Boolean = false) {
        if (selectedMethod == METHOD_SMS) {
            formattedPhoneNumber = formatPhilippinePhoneNumber(mobile).orEmpty()
            tvSubtitle.text = if (otpSent) {
                "Enter the 6-digit verification code for"
            } else {
                "We'll verify this phone number"
            }
            tvEmail.text = maskPhone(formattedPhoneNumber.ifBlank { mobile })
            tvSupport.text = "Send via Email instead"
        } else {
            tvSubtitle.text = if (otpSent) {
                "We sent a 6-digit email code to"
            } else {
                "We'll send a 6-digit email code to"
            }
            tvEmail.text = maskEmail(email)
            tvSupport.text = "Send via SMS instead"
        }
    }

    private fun getOtpCode(): String {
        return otpBoxes.joinToString(separator = "") { it.text.toString().trim() }
    }

    private fun clearOtpInputs() {
        isErrorState = false
        otpBoxes.forEach { it.text.clear() }
        otpBoxes.firstOrNull()?.requestFocus()
        updateOtpBoxBackgrounds()
    }

    private fun showError(message: String) {
        isErrorState = true
        tvError.text = message
        errorContainer.visibility = View.VISIBLE
        otpBoxes.forEach { it.setBackgroundResource(R.drawable.bg_otp_error_blue) }
    }

    private fun hideError() {
        if (errorContainer.visibility == View.VISIBLE) {
            errorContainer.visibility = View.GONE
        }
        if (isErrorState) {
            isErrorState = false
            updateOtpBoxBackgrounds()
        }
    }

    private fun updateOtpBoxBackgrounds() {
        if (isErrorState) {
            otpBoxes.forEach { it.setBackgroundResource(R.drawable.bg_otp_error_blue) }
            return
        }

        otpBoxes.forEach { editText ->
            val background = when {
                editText.hasFocus() -> R.drawable.bg_otp_focused_blue
                editText.text.isNotEmpty() -> R.drawable.bg_otp_filled_blue
                else -> R.drawable.bg_otp_default_blue
            }
            editText.setBackgroundResource(background)
        }
    }

    private fun setLoadingState(
        sending: Boolean = false,
        verifying: Boolean = false,
        finalizing: Boolean = false
    ) {
        btnVerify.isEnabled = !sending && !verifying && !finalizing
        tvResend.isEnabled = !sending && !verifying && !finalizing
        tvResend.alpha = if (tvResend.isEnabled) 1f else 0.5f
        tvSupport.isEnabled = !sending && !verifying && !finalizing
        tvSupport.alpha = if (tvSupport.isEnabled) 1f else 0.5f
        btnVerify.text = when {
            finalizing -> "Creating account..."
            verifying -> "Verifying..."
            sending -> "Sending code..."
            else -> "Verify account"
        }
        if (sending) {
            tvTimer.text = "Sending code..."
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(OTP_EXPIRY_MS, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = millisUntilFinished / 1000 % 60
                tvTimer.text = "Code expires in %02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                tvTimer.text = "Code expired. Please resend."
                if (DEBUG_OTP_MODE) {
                    clearDebugOtp()
                }
                showError("OTP expired. Please resend.")
            }
        }.also { it.start() }
    }

    private fun maskEmail(rawEmail: String): String {
        val parts = rawEmail.trim().split("@", limit = 2)
        if (parts.size != 2) return rawEmail

        val name = parts[0]
        val domain = parts[1]
        val visiblePrefix = name.take(1)
        return "$visiblePrefix*****@$domain"
    }

    private fun maskPhone(rawPhone: String): String {
        val phone = rawPhone.ifBlank { return "Missing phone number" }
        return when {
            phone.startsWith("+63") && phone.length >= 7 -> "+63*****${phone.takeLast(4)}"
            phone.length >= 5 -> "*****${phone.takeLast(4)}"
            else -> phone
        }
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

    private fun firebaseTestNumberMessage(isResend: Boolean): String {
        val prefix = if (isResend) "New verification code requested." else "Verification code sent."
        return "$prefix If this is a Firebase test number, use the test code configured in Firebase Console."
    }

    private fun smsErrorMessage(error: Exception): String {
        val raw = error.localizedMessage.orEmpty()
        val lower = raw.lowercase(Locale.US)
        return when {
            "invalid" in lower && "phone" in lower -> "Invalid phone number. Please use +63 phone number format."
            "quota" in lower || "billing" in lower || "region" in lower ||
                "blocked" in lower || "not authorized" in lower ||
                "operation_not_allowed" in lower || "operation-not-allowed" in lower ->
                "SMS is not available in production mode yet. Use a Firebase test phone number and test code."
            "network" in lower -> "Network error. Please try again."
            raw.isNotBlank() -> raw
            else -> "SMS verification failed. Please try again."
        }
    }

    private fun hideKeyboard(view: View) {
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        inputManager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun backendErrorMessage(response: Response<*>, fallback: String): String {
        return try {
            val errorBody = response.errorBody()?.string().orEmpty()
            if (errorBody.isBlank()) return fallback
            JSONObject(errorBody).optString("message", fallback).ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun isRapidClick(lastClickAt: Long, guardMs: Long): Boolean {
        return SystemClock.elapsedRealtime() - lastClickAt < guardMs
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_FULL_NAME = "fullName"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_MOBILE = "mobile"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_VERIFICATION_METHOD = "verificationMethod"
        const val EXTRA_AUTH_PROVIDER = "authProvider"
        const val EXTRA_GOOGLE_ID_TOKEN = "googleIdToken"
        const val EXTRA_REQUEST_ID = "requestId"
        const val METHOD_SMS = "sms"
        const val METHOD_EMAIL = "email"

        private const val DEBUG_OTP_MODE = true
        private const val OTP_LENGTH = 6
        private const val OTP_EXPIRY_MS = 5 * 60 * 1000L
        private const val DEBUG_OTP_MAX_ATTEMPTS = 5
        private const val RESEND_COOLDOWN_MS = 30 * 1000L
        private const val VERIFY_CLICK_GUARD_MS = 1000L
        private const val SMS_TIMEOUT_SECONDS = 60L
        private const val DEBUG_OTP_PREFS = "debug_otp_prefs"
        private const val DEBUG_OTP_CODE_KEY = "debug_otp_code"
        private const val DEBUG_OTP_EXPIRES_AT_KEY = "debug_otp_expires_at"
        private const val DEBUG_OTP_ATTEMPTS_KEY = "debug_otp_attempts"
        private const val TAG = "OtpVerification"
    }
}
