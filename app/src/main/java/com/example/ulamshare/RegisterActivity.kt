package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var storedVerificationId: String? = null
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSendOtp = findViewById<Button>(R.id.btnSendOtp)
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etMobileNumber = findViewById<EditText>(R.id.etMobileNumber)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)

        initCallbacks()

        btnBack.setOnClickListener {
            finish()
        }

        tvLoginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnSendOtp.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            var phone = etMobileNumber.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Ensure phone is in E.164 format
            if (!phone.startsWith("+")) {
                if (phone.startsWith("0")) {
                    phone = "+63" + phone.substring(1)
                } else if (!phone.startsWith("63")) {
                    phone = "+63$phone"
                } else {
                    phone = "+$phone"
                }
            }

            // For testing: Attempt real send but allow bypass
            sendVerificationCode(phone)
            
            // BYPASS FOR TESTING: If you want to go to OTP screen even if Firebase fails
            // navigateToOtp(phone, "MOCK_ID") 
        }
    }

    private fun initCallbacks() {
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d("RegisterActivity", "onVerificationCompleted:$credential")
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w("RegisterActivity", "onVerificationFailed", e)
                Toast.makeText(this@RegisterActivity, "Firebase Error: ${e.message}. Bypassing for UI testing...", Toast.LENGTH_LONG).show()
                
                // BYPASS: Navigate even on failure so you can test the UI
                val phone = findViewById<EditText>(R.id.etMobileNumber).text.toString()
                Toast.makeText(this@RegisterActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d("RegisterActivity", "onCodeSent:$verificationId")
                storedVerificationId = verificationId
                resendToken = token

                val phone = findViewById<EditText>(R.id.etMobileNumber).text.toString()
                navigateToOtp(phone, verificationId)
            }
        }
    }

    private fun navigateToOtp(phone: String, verificationId: String) {
        val intent = Intent(this, OtpActivity::class.java)
        intent.putExtra("verificationId", verificationId)
        intent.putExtra("fullName", findViewById<EditText>(R.id.etFullName).text.toString())
        intent.putExtra("email", findViewById<EditText>(R.id.etEmail).text.toString())
        intent.putExtra("password", findViewById<EditText>(R.id.etPassword).text.toString())
        intent.putExtra("phone", phone)
        startActivity(intent)
    }

    private fun sendVerificationCode(number: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
}