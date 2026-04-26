package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OtpActivity : AppCompatActivity() {

    private lateinit var otpBoxes: List<EditText>
    private lateinit var tvEmail: TextView
    private lateinit var tvError: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvResend: TextView
    private lateinit var tvSupport: TextView
    private lateinit var btnVerify: Button
    private lateinit var errorContainer: View

    private var userId: String = ""
    private var email: String = ""
    private var requestId: String = ""
    private var timer: CountDownTimer? = null
    private var isSendingOtp = false
    private var isVerifyingOtp = false
    private var isErrorState = false
    private var lastResendClickAt = 0L
    private var lastVerifyClickAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        bindViews()

        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()

        if (email.isBlank()) {
            showError("Email address is missing. Please go back and try again.")
        }

        tvEmail.text = maskEmail(email)
        setupOtpInputs()
        setupClickListeners()

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
            // TODO: Open support screen or contact support.
            Toast.makeText(this, "Support is coming soon.", Toast.LENGTH_SHORT).show()
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
        if (isSendingOtp || email.isBlank()) return

        isSendingOtp = true
        setLoadingState(sending = true)
        hideError()

        val request = SendOtpRequest(
            userId = userId,
            destination = email,
            channel = "email"
        )

        OtpApiClient.service.sendOtp(request).enqueue(object : Callback<SendOtpResponse> {
            override fun onResponse(call: Call<SendOtpResponse>, response: Response<SendOtpResponse>) {
                isSendingOtp = false
                setLoadingState(sending = false)

                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.requestId.isNotBlank()) {
                    requestId = body.requestId
                    clearOtpInputs()
                    startTimer()
                    Toast.makeText(
                        this@OtpActivity,
                        if (isResend) "New OTP sent." else body.message.ifBlank { "OTP sent." },
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showError(body?.message ?: backendErrorMessage(response, "Failed to send OTP"))
                }
            }

            override fun onFailure(call: Call<SendOtpResponse>, throwable: Throwable) {
                isSendingOtp = false
                setLoadingState(sending = false)
                showError(throwable.localizedMessage ?: "Failed to send OTP")
            }
        })
    }

    private fun verifyOtp() {
        if (isVerifyingOtp || isSendingOtp) return

        val otp = getOtpCode()
        if (otp.length != OTP_LENGTH) {
            showError("Please enter the 6-digit code.")
            return
        }

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
                        navigateToSuccess()
                    } else {
                        showError(body?.message ?: backendErrorMessage(response, "Invalid or expired OTP"))
                    }
                }

                override fun onFailure(call: Call<VerifyOtpResponse>, throwable: Throwable) {
                    isVerifyingOtp = false
                    setLoadingState(verifying = false)
                    showError(throwable.localizedMessage ?: "Unable to verify OTP. Please try again.")
                }
            })
    }

    private fun navigateToSuccess() {
        // TODO: Replace this with the real next screen for your email OTP flow.
        // Example: startActivity(Intent(this, NextActivity::class.java))
        val intent = Intent(this, SuccessActivity::class.java)
        intent.putExtra("message", "Registration Complete!")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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

    private fun setLoadingState(sending: Boolean = false, verifying: Boolean = false) {
        btnVerify.isEnabled = !sending && !verifying
        tvResend.isEnabled = !sending && !verifying
        tvResend.alpha = if (tvResend.isEnabled) 1f else 0.5f
        btnVerify.text = if (verifying) "Verifying..." else "Verify account"
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
                showError("This code has expired. Please resend a new code.")
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
        const val EXTRA_EMAIL = "email"
        const val EXTRA_REQUEST_ID = "requestId"

        private const val OTP_LENGTH = 6
        private const val OTP_EXPIRY_MS = 5 * 60 * 1000L
        private const val RESEND_COOLDOWN_MS = 30 * 1000L
        private const val VERIFY_CLICK_GUARD_MS = 1000L
    }
}
