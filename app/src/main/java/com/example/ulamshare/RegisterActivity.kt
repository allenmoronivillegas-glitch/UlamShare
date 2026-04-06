package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log // Import Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val btnSendOtp = findViewById<Button>(R.id.btnSendOtp)
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etMobileNumber = findViewById<EditText>(R.id.etMobileNumber)

        initCallbacks()

        btnSendOtp.setOnClickListener {
            Log.d("RegisterActivity", "btnSendOtp clicked!") // Added this line
            val name = etFullName.text.toString()
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            var phone = etMobileNumber.text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Convert to +63
            if (phone.startsWith("0")) {
                phone = "+63" + phone.substring(1)
            }
            Log.d("RegisterActivity", "Attempting to send OTP to: $phone")
            sendOtp(phone, name, email, password)
        }
    }

    private fun initCallbacks() {
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d("RegisterActivity", "Verification completed: ${credential.signInMethod}")
                FirebaseAuth.getInstance().signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("RegisterActivity", "Verification failed: ${e.message}", e)
                Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                Log.d("RegisterActivity", "OTP code sent. Verification ID: $verificationId")
                val intent = Intent(this@RegisterActivity, OtpActivity::class.java)
                intent.putExtra("verificationId", verificationId)
                intent.putExtra("name", findViewById<EditText>(R.id.etFullName).text.toString())
                intent.putExtra("email", findViewById<EditText>(R.id.etEmail).text.toString())
                intent.putExtra("password", findViewById<EditText>(R.id.etPassword).text.toString())
                intent.putExtra("phone", findViewById<EditText>(R.id.etMobileNumber).text.toString())
                startActivity(intent)
            }
        }
    }

    private fun sendOtp(phone: String, name: String, email: String, password: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }
}