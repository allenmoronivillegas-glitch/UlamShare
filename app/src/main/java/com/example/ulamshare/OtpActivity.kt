package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class OtpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var etOtp1: EditText
    private lateinit var etOtp2: EditText
    private lateinit var etOtp3: EditText
    private lateinit var etOtp4: EditText
    private lateinit var etOtp5: EditText
    private lateinit var etOtp6: EditText
    private lateinit var checkboxAutoLogin: CheckBox
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        verificationId = intent.getStringExtra("verificationId")
        val phone = intent.getStringExtra("phone") ?: ""

        etOtp1 = findViewById(R.id.etOtp1)
        etOtp2 = findViewById(R.id.etOtp2)
        etOtp3 = findViewById(R.id.etOtp3)
        etOtp4 = findViewById(R.id.etOtp4)
        etOtp5 = findViewById(R.id.etOtp5)
        etOtp6 = findViewById(R.id.etOtp6)
        checkboxAutoLogin = findViewById(R.id.checkboxAutoLogin)
        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val layoutAutoLogin = findViewById<LinearLayout>(R.id.layoutAutoLogin)
        val tvOtpSubHeader = findViewById<TextView>(R.id.tvOtpSubHeader)

        if (phone.isNotEmpty()) {
            tvOtpSubHeader.text = "Enter the 6-digit code sent to $phone"
        }

        setupOtpInputs()

        btnBack.setOnClickListener { finish() }

        layoutAutoLogin.setOnClickListener {
            checkboxAutoLogin.isChecked = !checkboxAutoLogin.isChecked
        }

        btnVerify.setOnClickListener {
            verifyOtp()
        }
    }

    private fun setupOtpInputs() {
        val otpFields = arrayOf(etOtp1, etOtp2, etOtp3, etOtp4, etOtp5, etOtp6)
        
        for (i in 0 until otpFields.size) {
            otpFields[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1 && i < otpFields.size - 1) {
                        otpFields[i + 1].requestFocus()
                    }
                }
                override fun afterTextChanged(s: Editable?) {
                    if (s?.isEmpty() == true && i > 0) {
                        otpFields[i - 1].requestFocus()
                    }
                    
                    if (allFieldsFilled() && checkboxAutoLogin.isChecked) {
                        verifyOtp()
                    }
                }
            })
        }
    }

    private fun allFieldsFilled(): Boolean {
        return etOtp1.text.length == 1 && etOtp2.text.length == 1 && etOtp3.text.length == 1 &&
               etOtp4.text.length == 1 && etOtp5.text.length == 1 && etOtp6.text.length == 1
    }

    private fun verifyOtp() {
        val code = "${etOtp1.text}${etOtp2.text}${etOtp3.text}${etOtp4.text}${etOtp5.text}${etOtp6.text}"
        
        if (code.length < 6) {
            Toast.makeText(this, "Please enter the complete 6-digit code", Toast.LENGTH_SHORT).show()
            return
        }

        if (verificationId != null) {
            val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
            signInWithPhoneAuthCredential(credential)
        } else {
            Toast.makeText(this, "Error: Verification ID missing. Check Register screen setup.", Toast.LENGTH_LONG).show()
        }
    }

    private fun signInWithPhoneAuthCredential(credential: com.google.firebase.auth.PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    linkEmailToPhoneAccount()
                } else {
                    Log.e("OtpActivity", "Verification failed", task.exception)
                    Toast.makeText(this, "Verification failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun linkEmailToPhoneAccount() {
        val email = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val fullName = intent.getStringExtra("fullName") ?: ""
        val phone = intent.getStringExtra("phone") ?: ""

        val credential = com.google.firebase.auth.EmailAuthProvider
            .getCredential(email, password)

        auth.currentUser?.linkWithCredential(credential)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        saveUserToFirestore(userId, fullName, email, phone)
                    }
                } else {
                    Toast.makeText(this, "Link failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    navigateToMain()
                }
            }
    }

    private fun saveUserToFirestore(userId: String, fullName: String, email: String, phone: String) {
        val userMap = hashMapOf(
            "uid" to userId,
            "fullName" to fullName,
            "email" to email,
            "phone" to phone,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(userId)
            .set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
                navigateToMain()
            }
            .addOnFailureListener { e ->
                Log.e("OtpActivity", "Error saving user", e)
                navigateToMain() // Navigate anyway, user is authenticated
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
