package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseException // Ensure this is imported
import java.util.concurrent.TimeUnit

class OtpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var verificationId: String? = null
    private var phoneNumber: String? = null // To store the phone number for resending
    private lateinit var resendCallbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    override fun onCreate(savedInstanceState: Bundle?) { // Corrected line here
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        verificationId = intent.getStringExtra("verificationId")
        phoneNumber = intent.getStringExtra("phone") // Get phone number from intent
        val name = intent.getStringExtra("name")
        val email = intent.getStringExtra("email")
        val password = intent.getStringExtra("password")

        Log.d("OtpActivity", "Received verificationId: $verificationId, phoneNumber: $phoneNumber")

        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val tvResend = findViewById<TextView>(R.id.tvResend) // Get the resend TextView

        initResendCallbacks()

        btnVerify.setOnClickListener {
            val code = findViewById<EditText>(R.id.etOtp1).text.toString() +
                    findViewById<EditText>(R.id.etOtp2).text.toString() +
                    findViewById<EditText>(R.id.etOtp3).text.toString() +
                    findViewById<EditText>(R.id.etOtp4).text.toString() +
                    findViewById<EditText>(R.id.etOtp5).text.toString() +
                    findViewById<EditText>(R.id.etOtp6).text.toString()

            if (verificationId != null) {
                Log.d("OtpActivity", "Attempting to verify OTP with code: $code")
                val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
                verifyOtp(credential)
            } else {
                Toast.makeText(this, "Verification ID is missing. Cannot verify OTP.", Toast.LENGTH_LONG).show()
                Log.e("OtpActivity", "Verification ID is null when trying to verify OTP.")
            }
        }

        tvResend.setOnClickListener { // Set click listener for resend
            Log.d("OtpActivity", "Resend OTP clicked. Phone number: $phoneNumber")
            if (phoneNumber != null) {
                resendOtp(phoneNumber!!)
                Toast.makeText(this, "Resending OTP...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Phone number not available to resend OTP.", Toast.LENGTH_LONG).show()
                Log.e("OtpActivity", "Phone number is null when trying to resend OTP.")
            }
        }

        // You can also add a click listener for btnBack here if you want it to navigate back
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressed() // Or navigate to RegisterActivity directly if needed
        }
    }

    private fun initResendCallbacks() {
        resendCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d("OtpActivity", "Resend verification completed automatically: ${credential.signInMethod}")
                // This callback might be invoked if the SMS is automatically verified.
                // In a resend scenario, we usually just care about onCodeSent.
                // We can choose to verify the OTP directly here or let the user enter it.
                // For now, let's keep the user entering it.
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e("OtpActivity", "Resend verification failed: ${e.message}", e)
                Toast.makeText(this@OtpActivity, "Error resending OTP: ${e.message}", Toast.LENGTH_LONG).show()
            }

            override fun onCodeSent(newVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                Log.d("OtpActivity", "New OTP code sent. New Verification ID: $newVerificationId")
                verificationId = newVerificationId // Update verificationId for the new OTP
                Toast.makeText(this@OtpActivity, "New OTP sent!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resendOtp(phone: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(resendCallbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyOtp(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("OtpActivity", "OTP verification successful.")
                    linkEmail()
                } else {
                    Log.e("OtpActivity", "OTP verification failed: ${task.exception?.message}", task.exception)
                    Toast.makeText(this, "OTP Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun linkEmail() {
        val email = intent.getStringExtra("email")!!
        val password = intent.getStringExtra("password")!!
        val name = intent.getStringExtra("name")!!
        val phone = intent.getStringExtra("phone")!!

        val credential = EmailAuthProvider.getCredential(email, password)

        auth.currentUser?.linkWithCredential(credential)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("OtpActivity", "Email linked successfully.")
                    val uid = auth.currentUser!!.uid

                    val user = hashMapOf(
                        "uid" to uid,
                        "name" to name,
                        "email" to email,
                        "phone" to phone
                    )

                    db.collection("users").document(uid).set(user)
                        .addOnSuccessListener { Log.d("OtpActivity", "User data saved to Firestore.") }
                        .addOnFailureListener { e -> Log.e("OtpActivity", "Error saving user data to Firestore: ${e.message}", e) }

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.e("OtpActivity", "Email linking failed: ${task.exception?.message}", task.exception)
                    Toast.makeText(this, "Link failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}