package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class PaymentActivity : AppCompatActivity() {

    private lateinit var emailLayout: TextInputLayout
    private lateinit var cardNumberLayout: TextInputLayout
    private lateinit var expiryLayout: TextInputLayout
    private lateinit var cvcLayout: TextInputLayout
    private lateinit var nameLayout: TextInputLayout
    private lateinit var promoLayout: TextInputLayout

    private lateinit var etEmail: TextInputEditText
    private lateinit var etCardNumber: TextInputEditText
    private lateinit var etExpiry: TextInputEditText
    private lateinit var etCvc: TextInputEditText
    private lateinit var etName: TextInputEditText
    private lateinit var etPromo: TextInputEditText

    private lateinit var spinnerCountry: Spinner
    private lateinit var btnPay: Button
    private lateinit var btnApplyCode: Button
    private lateinit var tvPromoStatus: TextView
    private lateinit var campaignsRef: DatabaseReference

    // Valid promo codes map: code -> discount label
    private val validPromoCodes = mapOf(
        "SAVE10" to "10% discount applied!",
        "ROBLOX20" to "20% discount applied!",
        "WELCOME" to "Welcome discount applied!"
    )

    private val countries = listOf(
        "Philippines", "United States", "United Kingdom",
        "Australia", "Canada", "Singapore", "Japan", "South Korea"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_creditcard)
        campaignsRef = FirebaseDatabase.getInstance().getReference("campaigns")

        // Get campaign data from intent
        val campaignId = intent.getStringExtra("campaignId")
        val title = intent.getStringExtra("title")
        val amount = intent.getStringExtra("amount")

        initViews()
        setupCountrySpinner()
        setupCardNumberFormatter()
        setupExpiryFormatter()
        setupPromoCode()
        setupPayButton(campaignId, title, amount)
    }

    private fun initViews() {
        emailLayout = findViewById(R.id.emailLayout)
        cardNumberLayout = findViewById(R.id.cardNumberLayout)
        expiryLayout = findViewById(R.id.expiryLayout)
        cvcLayout = findViewById(R.id.cvcLayout)
        nameLayout = findViewById(R.id.nameLayout)
        promoLayout = findViewById(R.id.promoLayout)

        etEmail = findViewById(R.id.etEmail)
        etCardNumber = findViewById(R.id.etCardNumber)
        etExpiry = findViewById(R.id.etExpiry)
        etCvc = findViewById(R.id.etCvc)
        etName = findViewById(R.id.etName)
        etPromo = findViewById(R.id.etPromo)

        spinnerCountry = findViewById(R.id.spinnerCountry)
        btnPay = findViewById(R.id.btnPay)
        btnApplyCode = findViewById(R.id.btnApplyCode)
        tvPromoStatus = findViewById(R.id.tvPromoStatus)
    }

    private fun setupCountrySpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, countries)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCountry.adapter = adapter
    }

    // Auto-formats card number as: 1234 5678 9012 3456
    private fun setupCardNumberFormatter() {
        etCardNumber.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val digits = s.toString().replace(" ", "")
                val formatted = StringBuilder()
                for (i in digits.indices) {
                    if (i > 0 && i % 4 == 0) formatted.append(" ")
                    formatted.append(digits[i])
                }

                s.replace(0, s.length, formatted)
                isFormatting = false
            }
        })
    }

    // Auto-formats expiry as: MM / YY
    private fun setupExpiryFormatter() {
        etExpiry.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val digits = s.toString().replace(" / ", "").replace("/", "")
                val formatted = StringBuilder()
                for (i in digits.indices) {
                    if (i == 2) formatted.append(" / ")
                    formatted.append(digits[i])
                }

                s.replace(0, s.length, formatted)
                isFormatting = false
            }
        })
    }

    private fun setupPromoCode() {
        btnApplyCode.setOnClickListener {
            val code = etPromo.text.toString().trim().uppercase()

            if (code.isEmpty()) {
                showPromoStatus("Please enter a promo code.", isSuccess = false)
                return@setOnClickListener
            }

            val discount = validPromoCodes[code]
            if (discount != null) {
                showPromoStatus(discount, isSuccess = true)
                etPromo.isEnabled = false
                btnApplyCode.isEnabled = false
                btnApplyCode.text = "Applied"
            } else {
                showPromoStatus("Invalid promo code. Please try again.", isSuccess = false)
            }
        }
    }

    private fun showPromoStatus(message: String, isSuccess: Boolean) {
        tvPromoStatus.visibility = View.VISIBLE
        tvPromoStatus.text = message
        tvPromoStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSuccess) R.color.promo_success else R.color.promo_error
            )
        )
    }

    private fun setupPayButton(campaignId: String?, title: String?, amount: String?) {
        btnPay.setOnClickListener {
            if (!validateFields()) return@setOnClickListener

            validateDonationCampaign(campaignId) { isAvailable ->
                if (!isAvailable) {
                    showExpiredCampaignMessage()
                    return@validateDonationCampaign
                }

                btnPay.text = "Processing..."
                btnPay.isEnabled = false

                val intent = Intent(this, ReviewDonationActivity::class.java).apply {
                    putExtra("campaignId", campaignId)
                    putExtra("title", title)
                    putExtra("amount", amount)
                    putExtra("paymentMethod", "Credit / Debit Card")
                    putExtra("donateType", "One-Time")
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun validateDonationCampaign(campaignId: String?, onResult: (Boolean) -> Unit) {
        val id = campaignId.orEmpty()
        if (id.isBlank()) {
            Log.e("PaymentActivity", "Missing campaignId while validating card donation flow")
            Toast.makeText(this, "This campaign is unavailable right now.", Toast.LENGTH_SHORT).show()
            onResult(false)
            return
        }

        campaignsRef.child(id).get()
            .addOnSuccessListener { snapshot ->
                val campaign = CampaignDisplayHelper.parseCampaign(snapshot)
                val canDonate = campaign != null && CampaignDisplayHelper.canDonate(campaign)
                Log.d(
                    "PaymentActivity",
                    "Campaign validation before card review. campaignId=$id canDonate=$canDonate status=${campaign?.status} date=${campaign?.date}"
                )
                onResult(canDonate)
            }
            .addOnFailureListener { error ->
                Log.e("PaymentActivity", "Unable to validate campaign before card review", error)
                Toast.makeText(this, "Unable to verify this campaign right now.", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
    }

    private fun showExpiredCampaignMessage() {
        Toast.makeText(
            this,
            "This campaign has expired and is no longer accepting donations.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun validateFields(): Boolean {
        var isValid = true

        // Email
        val email = etEmail.text.toString().trim()
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter a valid email"
            isValid = false
        } else {
            emailLayout.error = null
        }

        // Card number (must be 16 digits)
        val cardDigits = etCardNumber.text.toString().replace(" ", "")
        if (cardDigits.length != 16) {
            cardNumberLayout.error = "Enter a valid 16-digit card number"
            isValid = false
        } else {
            cardNumberLayout.error = null
        }

        // Expiry (MM / YY format)
        val expiry = etExpiry.text.toString()
        if (!expiry.matches(Regex("\\d{2} / \\d{2}"))) {
            expiryLayout.error = "Enter valid expiry (MM / YY)"
            isValid = false
        } else {
            expiryLayout.error = null
        }

        // CVC (3-4 digits)
        val cvc = etCvc.text.toString()
        if (cvc.length < 3) {
            cvcLayout.error = "Enter valid CVC"
            isValid = false
        } else {
            cvcLayout.error = null
        }

        // Name
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            nameLayout.error = "Enter cardholder name"
            isValid = false
        } else {
            nameLayout.error = null
        }

        return isValid
    }
}
