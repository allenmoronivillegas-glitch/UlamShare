package com.example.ulamshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions

class PaymentMethodActivity : AppCompatActivity() {

    private var selectedOptionId: Int = R.id.optionGCash
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var campaignsRef: DatabaseReference
    private lateinit var savedMethodsContainer: LinearLayout
    private lateinit var tvNoSavedMethods: TextView
    private lateinit var tvSavedMethodsTitle: TextView

    private var source: String = SOURCE_DONATION
    private var isManageMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_method)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        campaignsRef = FirebaseDatabase.getInstance().getReference("campaigns")

        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank {
            intent.getStringExtra(EXTRA_MODE).orEmpty()
        }.ifBlank {
            if (hasDonationExtras()) SOURCE_DONATION else SOURCE_PROFILE
        }
        isManageMode = source == SOURCE_PROFILE || source == MODE_MANAGE
        Log.d(TAG, "PaymentMethodActivity opened. source=$source, isManageMode=$isManageMode")

        val campaignId = intent.getStringExtra("campaignId")
        val title = intent.getStringExtra("title")
        val amount = intent.getStringExtra("amount")

        val tvSubTitle = findViewById<TextView>(R.id.tvSubTitle)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnConfirm = findViewById<Button>(R.id.btnConfirmPayment)
        savedMethodsContainer = findViewById(R.id.savedMethodsContainer)
        tvNoSavedMethods = findViewById(R.id.tvNoSavedMethods)
        tvSavedMethodsTitle = findViewById(R.id.tvSavedMethodsTitle)

        val optionGCash = findViewById<ConstraintLayout>(R.id.optionGCash)
        val optionMaya = findViewById<ConstraintLayout>(R.id.optionMaya)
        val optionCard = findViewById<ConstraintLayout>(R.id.optionCard)

        val rbGCash = findViewById<RadioButton>(R.id.rbGCash)
        val rbMaya = findViewById<RadioButton>(R.id.rbMaya)
        val rbCard = findViewById<RadioButton>(R.id.rbCard)

        tvSubTitle.text = if (isManageMode) {
            "Add or manage saved payment methods"
        } else {
            "Choose how you'd like to donate"
        }
        btnConfirm.text = if (isManageMode) "Save Payment Method" else "Confirm Payment Method"
        tvSavedMethodsTitle.visibility = if (isManageMode) View.VISIBLE else View.GONE
        savedMethodsContainer.visibility = if (isManageMode) View.VISIBLE else View.GONE
        tvNoSavedMethods.visibility = if (isManageMode) View.VISIBLE else View.GONE

        btnBack.setOnClickListener { finish() }

        fun updateSelection(selectedId: Int) {
            selectedOptionId = selectedId

            optionGCash.setBackgroundResource(
                if (selectedId == R.id.optionGCash) R.drawable.bg_card_selected
                else R.drawable.rounded_input_border
            )
            rbGCash.isChecked = selectedId == R.id.optionGCash

            optionMaya.setBackgroundResource(
                if (selectedId == R.id.optionMaya) R.drawable.bg_card_selected
                else R.drawable.rounded_input_border
            )
            rbMaya.isChecked = selectedId == R.id.optionMaya

            optionCard.setBackgroundResource(
                if (selectedId == R.id.optionCard) R.drawable.bg_card_selected
                else R.drawable.rounded_input_border
            )
            rbCard.isChecked = selectedId == R.id.optionCard
        }

        optionGCash.setOnClickListener { updateSelection(R.id.optionGCash) }
        optionMaya.setOnClickListener { updateSelection(R.id.optionMaya) }
        optionCard.setOnClickListener {
            updateSelection(R.id.optionCard)
            if (!isManageMode) {
                validateDonationCampaign(campaignId) { isAvailable ->
                    if (!isAvailable) {
                        showExpiredCampaignMessage()
                        return@validateDonationCampaign
                    }

                    Log.d(TAG, "Opening card checkout from donation flow")
                    startActivity(Intent(this, PaymentActivity::class.java).apply {
                        putExtra(EXTRA_SOURCE, SOURCE_DONATION)
                        putExtra("campaignId", campaignId)
                        putExtra("title", title)
                        putExtra("amount", amount)
                    })
                }
            }
        }

        btnConfirm.setOnClickListener {
            if (isManageMode) {
                saveSelectedPaymentMethod()
            } else {
                continueDonationFlow(campaignId, title, amount)
            }
        }

        if (isManageMode) {
            loadSavedPaymentMethods()
        }
    }

    private fun continueDonationFlow(campaignId: String?, title: String?, amount: String?) {
        validateDonationCampaign(campaignId) { isAvailable ->
            if (!isAvailable) {
                showExpiredCampaignMessage()
                return@validateDonationCampaign
            }

            val method = selectedMethodDisplayName()
            Log.d(TAG, "Continuing donation flow to payment review. method=$method")

            if (selectedOptionId == R.id.optionCard) {
                startActivity(Intent(this, PaymentActivity::class.java).apply {
                    putExtra(EXTRA_SOURCE, SOURCE_DONATION)
                    putExtra("campaignId", campaignId)
                    putExtra("title", title)
                    putExtra("amount", amount)
                })
                return@validateDonationCampaign
            }

            startActivity(Intent(this, ReviewDonationActivity::class.java).apply {
                putExtra("campaignId", campaignId)
                putExtra("title", title)
                putExtra("amount", amount)
                putExtra("paymentMethod", method)
                putExtra("donateType", "One-Time")
            })
        }
    }

    private fun validateDonationCampaign(campaignId: String?, onResult: (Boolean) -> Unit) {
        if (isManageMode) {
            onResult(true)
            return
        }

        val id = campaignId.orEmpty()
        if (id.isBlank()) {
            Log.e(TAG, "Missing campaignId while validating donation flow")
            Toast.makeText(this, "This campaign is unavailable right now.", Toast.LENGTH_SHORT).show()
            onResult(false)
            return
        }

        campaignsRef.child(id).get()
            .addOnSuccessListener { snapshot ->
                val campaign = CampaignDisplayHelper.parseCampaign(snapshot)
                val canDonate = campaign != null && CampaignDisplayHelper.canDonate(campaign)
                Log.d(
                    TAG,
                    "Donation campaign validation. campaignId=$id canDonate=$canDonate status=${campaign?.status} date=${campaign?.date}"
                )
                onResult(canDonate)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to validate campaign before payment step", error)
                Toast.makeText(this, "Unable to verify this campaign right now.", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
    }

    private fun saveSelectedPaymentMethod() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to save payment methods.", Toast.LENGTH_SHORT).show()
            return
        }

        val methodId = selectedMethodType()
        val methodsRef = paymentMethodsRef(user.uid)
        val methodRef = methodsRef.document(methodId)

        Log.d(TAG, "Saving payment method. uid=${user.uid}, type=$methodId, source=$source")

        methodsRef.get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                val selectedExists = snapshot.documents.any { it.id == methodId }

                snapshot.documents.forEach { doc ->
                    batch.set(doc.reference, mapOf("isDefault" to false), SetOptions.merge())
                }

                val payload = hashMapOf<String, Any>(
                    "id" to methodId,
                    "type" to methodId,
                    "displayName" to selectedMethodDisplayName(),
                    "maskedNumber" to maskedMethodInfo(methodId),
                    "isDefault" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!selectedExists) {
                    payload["createdAt"] = FieldValue.serverTimestamp()
                }

                batch.set(methodRef, payload, SetOptions.merge())
                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "Saved payment method. uid=${user.uid}, type=$methodId")
                        Toast.makeText(this, "Payment method saved.", Toast.LENGTH_SHORT).show()
                        loadSavedPaymentMethods()
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to save payment method", error)
                        Toast.makeText(this, paymentMethodSaveErrorMessage(error), Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to load methods before save", error)
                Toast.makeText(this, paymentMethodSaveErrorMessage(error), Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadSavedPaymentMethods() {
        val user = auth.currentUser
        if (user == null) {
            savedMethodsContainer.removeAllViews()
            tvNoSavedMethods.text = "Please log in to save payment methods."
            tvNoSavedMethods.visibility = View.VISIBLE
            return
        }

        Log.d(TAG, "Loading methods for uid=${user.uid}")

        paymentMethodsRef(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                savedMethodsContainer.removeAllViews()
                tvNoSavedMethods.text = "No saved payment methods yet."
                tvNoSavedMethods.visibility = if (snapshot.isEmpty) View.VISIBLE else View.GONE

                snapshot.documents.sortedByDescending { it.getBoolean("isDefault") == true }
                    .forEach { doc ->
                        val methodId = doc.id
                        val displayName = doc.getString("displayName").orEmpty().ifBlank {
                            selectedDisplayNameForMethod(methodId)
                        }
                        val maskedNumber = doc.getString("maskedNumber").orEmpty()
                        val isDefault = doc.getBoolean("isDefault") == true
                        val subtitleParts = mutableListOf<String>()
                        if (maskedNumber.isNotBlank()) subtitleParts += maskedNumber
                        if (isDefault) subtitleParts += "Default"

                        savedMethodsContainer.addView(
                            buildSavedMethodRow(
                                methodId = methodId,
                                displayName = displayName,
                                subtitle = subtitleParts.joinToString(" | ")
                            )
                        )
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to load saved methods", error)
                tvNoSavedMethods.text = paymentMethodLoadErrorMessage(error)
                tvNoSavedMethods.visibility = View.VISIBLE
            }
    }

    private fun buildSavedMethodRow(methodId: String, displayName: String, subtitle: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = getDrawable(R.drawable.rounded_input_border)
            setOnClickListener { setDefaultMethod(methodId) }
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(TextView(this).apply {
            text = displayName
            textSize = 14f
            setTextColor(0xFF1A1C1E.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        copy.addView(TextView(this).apply {
            text = subtitle.ifBlank { "Saved for future donations" }
            textSize = 12f
            setTextColor(0xFF6C757D.toInt())
        })

        row.addView(
            copy,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(TextView(this).apply {
            text = "Delete"
            textSize = 12f
            setTextColor(0xFFE24B4A.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { deleteMethod(methodId) }
        })
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        }
        return row
    }

    private fun setDefaultMethod(methodId: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to save payment methods.", Toast.LENGTH_SHORT).show()
            return
        }

        paymentMethodsRef(user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                snapshot.documents.forEach { doc ->
                    batch.set(
                        doc.reference,
                        mapOf("isDefault" to (doc.id == methodId)),
                        SetOptions.merge()
                    )
                }
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Default payment method updated.", Toast.LENGTH_SHORT).show()
                        loadSavedPaymentMethods()
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to set default payment method", error)
                        Toast.makeText(this, paymentMethodSaveErrorMessage(error), Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to load methods before default update", error)
                Toast.makeText(this, paymentMethodSaveErrorMessage(error), Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteMethod(methodId: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in to save payment methods.", Toast.LENGTH_SHORT).show()
            return
        }

        paymentMethodsRef(user.uid)
            .document(methodId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Payment method deleted.", Toast.LENGTH_SHORT).show()
                loadSavedPaymentMethods()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to delete payment method", error)
                Toast.makeText(this, paymentMethodSaveErrorMessage(error), Toast.LENGTH_SHORT).show()
            }
    }

    private fun paymentMethodsRef(uid: String) =
        firestore.collection("users").document(uid).collection("payment_methods")

    private fun paymentMethodLoadErrorMessage(error: Exception): String {
        return when ((error as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permission denied. Please check Firestore rules."
            else -> "Unable to load saved methods."
        }
    }

    private fun paymentMethodSaveErrorMessage(error: Exception): String {
        return when ((error as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permission denied. Please check Firestore rules."
            else -> "Unable to save payment method."
        }
    }

    private fun selectedMethodType(): String {
        return when (selectedOptionId) {
            R.id.optionMaya -> "maya"
            R.id.optionCard -> "card"
            else -> "gcash"
        }
    }

    private fun selectedMethodDisplayName(): String {
        return selectedDisplayNameForMethod(selectedMethodType())
    }

    private fun selectedDisplayNameForMethod(methodId: String): String {
        return when (methodId) {
            "maya" -> "Maya"
            "card" -> "Credit / Debit Card"
            else -> "GCash"
        }
    }

    private fun maskedMethodInfo(methodId: String): String {
        return when (methodId) {
            "gcash" -> ""
            "maya" -> ""
            "card" -> ""
            else -> ""
        }
    }

    private fun hasDonationExtras(): Boolean {
        return !intent.getStringExtra("campaignId").isNullOrBlank() ||
            !intent.getStringExtra("amount").isNullOrBlank() ||
            !intent.getStringExtra("title").isNullOrBlank()
    }

    private fun showExpiredCampaignMessage() {
        Toast.makeText(
            this,
            "This campaign has expired and is no longer accepting donations.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_MODE = "mode"
        const val SOURCE_PROFILE = "profile"
        const val SOURCE_DONATION = "donation"
        const val MODE_MANAGE = "manage_payment_methods"
        private const val TAG = "PaymentMethods"
    }
}
