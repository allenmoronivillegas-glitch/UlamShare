package com.example.ulamshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var realtimeDb: FirebaseDatabase

    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etCountry: AutoCompleteTextView
    private lateinit var etProvince: AutoCompleteTextView
    private lateinit var etCityMunicipality: AutoCompleteTextView
    private lateinit var etBarangay: EditText
    private lateinit var ivEditAvatar: TextView
    private lateinit var ivEditAvatarPhoto: ImageView
    private lateinit var btnEditAvatarPhoto: ImageView
    private lateinit var btnSave: Button
    private lateinit var btnBack: ImageView

    private var selectedProfileImageUri: Uri? = null
    private var saveButtonDefaultText: CharSequence = ""
    private var bindingAddressFromProfile = false

    private val profilePhotoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedProfileImageUri = uri
            persistReadPermission(uri)
            displayProfilePhoto(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        realtimeDb = FirebaseDatabase.getInstance(DATABASE_URL)

        etFullName = findViewById(R.id.etEditFullName)
        etEmail = findViewById(R.id.etEditEmail)
        etPhone = findViewById(R.id.etEditPhone)
        etCountry = findViewById(R.id.etEditCountry)
        etProvince = findViewById(R.id.etEditProvince)
        etCityMunicipality = findViewById(R.id.etEditCityMunicipality)
        etBarangay = findViewById(R.id.etEditBarangay)
        ivEditAvatar = findViewById(R.id.ivEditAvatar)
        ivEditAvatarPhoto = findViewById(R.id.ivEditAvatarPhoto)
        ivEditAvatarPhoto.clipToOutline = true
        btnEditAvatarPhoto = findViewById(R.id.btnEditAvatarPhoto)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)
        saveButtonDefaultText = btnSave.text

        setupSystemBarSpacing()
        setupAddressFields()
        loadUserData()

        btnBack.setOnClickListener {
            finish()
        }

        btnEditAvatarPhoto.setOnClickListener {
            profilePhotoPicker.launch(arrayOf("image/*"))
        }

        btnSave.setOnClickListener {
            saveUserData()
        }
    }

    private fun setupSystemBarSpacing() {
        val root = findViewById<View>(R.id.editProfileRoot)
        val scroll = findViewById<View>(R.id.editProfileScroll)
        val saveParams = btnSave.layoutParams as ViewGroup.MarginLayoutParams
        val originalSaveBottomMargin = saveParams.bottomMargin
        val originalScrollBottomPadding = scroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val updatedParams = btnSave.layoutParams as ViewGroup.MarginLayoutParams
            updatedParams.bottomMargin = originalSaveBottomMargin + navigationBars.bottom
            btnSave.layoutParams = updatedParams

            scroll.setPadding(
                scroll.paddingLeft,
                scroll.paddingTop,
                scroll.paddingRight,
                originalScrollBottomPadding + dp(8)
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupAddressFields() {
        setDropdownOptions(etCountry, countryOptions())
        setDropdownOptions(etProvince, PHILIPPINE_PROVINCES)
        setDropdownOptions(etCityMunicipality, emptyList())

        etCountry.setOnItemClickListener { _, _, _, _ ->
            updateAddressMode(clearProvince = true)
        }
        etProvince.setOnItemClickListener { _, _, _, _ ->
            updateCityMunicipalityOptions(clearCity = true)
        }

        etCountry.doAfterTextChanged {
            if (!bindingAddressFromProfile) updateAddressMode(clearProvince = false)
        }
        etProvince.doAfterTextChanged {
            if (!bindingAddressFromProfile) updateCityMunicipalityOptions(clearCity = false)
        }
    }

    private fun setDropdownOptions(view: AutoCompleteTextView, options: List<String>) {
        view.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                options
            )
        )
        view.threshold = 0
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && view.adapter != null) view.showDropDown()
        }
        view.setOnClickListener {
            if (view.adapter != null) view.showDropDown()
        }
    }

    private fun updateAddressMode(clearProvince: Boolean) {
        val philippines = isPhilippines(etCountry.text.toString())
        if (philippines) {
            etProvince.hint = "Select province"
            etCityMunicipality.hint = "Select city or municipality"
            setDropdownOptions(etProvince, PHILIPPINE_PROVINCES)
        } else {
            etProvince.hint = "Enter state or province"
            etCityMunicipality.hint = "Enter city or municipality"
            setDropdownOptions(etProvince, emptyList())
            setDropdownOptions(etCityMunicipality, emptyList())
        }

        if (clearProvince) {
            etProvince.setText("", false)
            etCityMunicipality.setText("", false)
        }
        updateCityMunicipalityOptions(clearCity = clearProvince)
    }

    private fun updateCityMunicipalityOptions(clearCity: Boolean) {
        val province = etProvince.text.toString().trim()
        val cityOptions = if (isPhilippines(etCountry.text.toString())) {
            PHILIPPINE_CITY_MUNICIPALITY_OPTIONS[province].orEmpty()
        } else {
            emptyList()
        }
        setDropdownOptions(etCityMunicipality, cityOptions)
        if (clearCity) etCityMunicipality.setText("", false)
    }

    private fun loadUserData() {
        val user = auth.currentUser
        if (user != null) {
            etEmail.setText(user.email.orEmpty())
            val fallbackName = user.displayName?.takeIf { it.isNotBlank() } ?: "User"
            updateInitials(fallbackName)
            bindAddressFields(country = DEFAULT_COUNTRY)
            displayProfilePhoto(savedProfilePhotoUri(user.uid))
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        bindProfile(document, fallbackName)
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Unable to load profile", error)
                }
        } else {
            etFullName.setText("Guest User")
            etEmail.setText("Not logged in")
            etPhone.setText("")
            bindAddressFields(country = DEFAULT_COUNTRY)
            updateInitials("Guest User")
            displayProfilePhoto(savedProfilePhotoUri(GUEST_PROFILE_KEY))
        }
    }

    private fun bindProfile(document: DocumentSnapshot, fallbackName: String) {
        val fullName = document.getString("fullName") ?: fallbackName
        val email = document.getString("email").orEmpty()
            .ifBlank { auth.currentUser?.email.orEmpty() }
        val phone = listOf(
            document.getString("phone").orEmpty(),
            document.getString("mobileNumber").orEmpty(),
            document.getString("mobile").orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        val profilePhotoLocalUri = document.getString("profilePhotoLocalUri").orEmpty()
        val profilePhotoUrl = document.getString("profilePhotoUrl").orEmpty()

        etFullName.setText(fullName)
        etEmail.setText(email)
        etPhone.setText(phone)
        bindAddressFields(
            country = addressValue(document, "country"),
            province = addressValue(document, "province"),
            cityMunicipality = addressValue(document, "cityMunicipality"),
            barangay = addressValue(document, "barangay")
        )

        updateInitials(fullName)
        displayProfilePhoto(
            profilePhotoLocalUri
                .ifBlank { savedProfilePhotoUri(document.id) }
                .ifBlank { profilePhotoUrl }
        )
    }

    private fun addressValue(document: DocumentSnapshot, key: String): String {
        val flatValue = document.getString(key).orEmpty()
        if (flatValue.isNotBlank()) return flatValue

        val address = document.get("address") as? Map<*, *>
        return address?.get(key)?.toString().orEmpty()
    }

    private fun bindAddressFields(
        country: String = "",
        province: String = "",
        cityMunicipality: String = "",
        barangay: String = ""
    ) {
        bindingAddressFromProfile = true
        etCountry.setText(country.ifBlank { DEFAULT_COUNTRY }, false)
        updateAddressMode(clearProvince = false)
        etProvince.setText(province, false)
        updateCityMunicipalityOptions(clearCity = false)
        etCityMunicipality.setText(cityMunicipality, false)
        etBarangay.setText(barangay)
        bindingAddressFromProfile = false
    }

    private fun updateInitials(fullName: String) {
        val initials = fullName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
        ivEditAvatar.text = if (initials.isNotEmpty()) initials else "G"
    }

    private fun saveUserData() {
        val user = auth.currentUser
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val country = etCountry.text.toString().trim()
        val province = etProvince.text.toString().trim()
        val cityMunicipality = etCityMunicipality.text.toString().trim()
        val barangay = etBarangay.text.toString().trim()

        if (!validateProfileInput(fullName, email, country, province, cityMunicipality)) {
            return
        }

        if (user == null) {
            selectedProfileImageUri?.let { uri ->
                saveProfilePhotoUri(GUEST_PROFILE_KEY, uri.toString())
            }
            Toast.makeText(this, R.string.profile_photo_saved_local, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSaving(true)
        val emailChanged = email != user.email.orEmpty()
        if (emailChanged) {
            user.updateEmail(email)
                .addOnSuccessListener {
                    Log.d(TAG, "Firebase Auth email updated uid=${user.uid}")
                    saveProfileDocument(user.uid, fullName, email, phone, country, province, cityMunicipality, barangay)
                }
                .addOnFailureListener { error ->
                    setSaving(false)
                    showEmailUpdateError(error)
                }
        } else {
            saveProfileDocument(user.uid, fullName, email, phone, country, province, cityMunicipality, barangay)
        }
    }

    private fun validateProfileInput(
        fullName: String,
        email: String,
        country: String,
        province: String,
        cityMunicipality: String
    ): Boolean {
        if (fullName.isEmpty()) {
            etFullName.error = "Full name is required"
            etFullName.requestFocus()
            return false
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email."
            etEmail.requestFocus()
            return false
        }

        if (country.isEmpty()) {
            etCountry.error = "Please select your country."
            etCountry.requestFocus()
            return false
        }

        if (isPhilippines(country) && province.isEmpty()) {
            etProvince.error = "Please select your province."
            etProvince.requestFocus()
            return false
        }

        if (isPhilippines(country) && cityMunicipality.isEmpty()) {
            etCityMunicipality.error = "Please select your city or municipality."
            etCityMunicipality.requestFocus()
            return false
        }

        return true
    }

    private fun saveProfileDocument(
        uid: String,
        fullName: String,
        email: String,
        phone: String,
        country: String,
        province: String,
        cityMunicipality: String,
        barangay: String
    ) {
        val address = mapOf(
            "country" to country,
            "province" to province,
            "cityMunicipality" to cityMunicipality,
            "barangay" to barangay
        )
        val updates = hashMapOf<String, Any>(
            "uid" to uid,
            "fullName" to fullName,
            "email" to email,
            "phone" to phone,
            "mobileNumber" to phone,
            "country" to country,
            "province" to province,
            "cityMunicipality" to cityMunicipality,
            "barangay" to barangay,
            "address" to address,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        selectedProfileImageUri?.let { uri ->
            val uriString = uri.toString()
            saveProfilePhotoUri(uid, uriString)
            updates["profilePhotoLocalUri"] = uriString
            updates["profilePhotoUrl"] = ""
        }

        auth.currentUser?.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
        )

        db.collection("users").document(uid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updateRealtimeProfileIfPresent(uid, fullName, email, phone, country, province, cityMunicipality, barangay)
            }
            .addOnFailureListener { error ->
                setSaving(false)
                Log.e(TAG, "Failed to update Firestore profile", error)
                Toast.makeText(this, "Failed to update profile: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateRealtimeProfileIfPresent(
        uid: String,
        fullName: String,
        email: String,
        phone: String,
        country: String,
        province: String,
        cityMunicipality: String,
        barangay: String
    ) {
        val ref = realtimeDb.getReference("users").child(uid)
        ref.get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    finishSuccessfulSave()
                    return@addOnSuccessListener
                }

                val realtimeUpdates = mapOf<String, Any>(
                    "email" to email,
                    "fullName" to fullName,
                    "username" to fullName,
                    "phone" to phone,
                    "mobileNumber" to phone,
                    "country" to country,
                    "province" to province,
                    "cityMunicipality" to cityMunicipality,
                    "barangay" to barangay,
                    "updatedAt" to ServerValue.TIMESTAMP
                )

                ref.updateChildren(realtimeUpdates)
                    .addOnSuccessListener {
                        finishSuccessfulSave()
                    }
                    .addOnFailureListener { error ->
                        setSaving(false)
                        Log.e(TAG, "Failed to update Realtime Database profile", error)
                        Toast.makeText(
                            this,
                            "Profile saved, but Realtime Database profile could not update.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->
                setSaving(false)
                Log.e(TAG, "Failed to check Realtime Database user profile", error)
                Toast.makeText(
                    this,
                    "Profile saved, but Realtime Database profile could not update.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun finishSuccessfulSave() {
        setSaving(false)
        Toast.makeText(this, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showEmailUpdateError(error: Exception) {
        Log.e(TAG, "Firebase Auth email update failed", error)
        val message = when (error) {
            is FirebaseAuthRecentLoginRequiredException ->
                "For security, please log in again before changing your email."
            is FirebaseAuthUserCollisionException ->
                "This email is already used by another account."
            is FirebaseAuthInvalidCredentialsException ->
                "Please enter a valid email."
            else ->
                "Unable to update email: ${error.message ?: "Please try again."}"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setSaving(isSaving: Boolean) {
        btnSave.isEnabled = !isSaving
        btnSave.text = if (isSaving) "Saving..." else saveButtonDefaultText
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to persist profile photo URI permission", error)
        }
    }

    private fun displayProfilePhoto(uriString: String) {
        if (uriString.isBlank()) {
            ivEditAvatarPhoto.setImageDrawable(null)
            ivEditAvatarPhoto.visibility = View.GONE
            ivEditAvatar.visibility = View.VISIBLE
            return
        }

        ivEditAvatarPhoto.visibility = View.VISIBLE
        ivEditAvatar.visibility = View.GONE
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            CampaignImageLoader.load(ivEditAvatarPhoto, uriString, R.drawable.plant)
        } else {
            runCatching {
                ivEditAvatarPhoto.setImageURI(Uri.parse(uriString))
            }.onFailure { error ->
                Log.w(TAG, "Unable to display selected profile photo", error)
                ivEditAvatarPhoto.setImageDrawable(null)
                ivEditAvatarPhoto.visibility = View.GONE
                ivEditAvatar.visibility = View.VISIBLE
            }
        }
    }

    private fun profilePrefs() =
        getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

    private fun savedProfilePhotoUri(key: String): String =
        profilePrefs().getString(profilePhotoPrefKey(key), "").orEmpty()

    private fun saveProfilePhotoUri(key: String, uriString: String) {
        profilePrefs().edit()
            .putString(profilePhotoPrefKey(key), uriString)
            .apply()
    }

    private fun profilePhotoPrefKey(key: String): String = "profile_photo_uri_$key"

    private fun isPhilippines(country: String): Boolean =
        country.trim().equals(DEFAULT_COUNTRY, ignoreCase = true)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun countryOptions(): List<String> {
        val countries = Locale.getISOCountries()
            .map { countryCode -> Locale("", countryCode).displayCountry }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        return listOf(DEFAULT_COUNTRY) + countries.filterNot {
            it.equals(DEFAULT_COUNTRY, ignoreCase = true)
        }
    }

    private companion object {
        const val TAG = "EditProfileActivity"
        const val PROFILE_PREFS = "profile_preferences"
        const val GUEST_PROFILE_KEY = "guest"
        const val DEFAULT_COUNTRY = "Philippines"
        const val DATABASE_URL = "https://ulamshare-4f2b9-default-rtdb.asia-southeast1.firebasedatabase.app"

        val PHILIPPINE_PROVINCES = listOf(
            "Abra",
            "Agusan del Norte",
            "Agusan del Sur",
            "Aklan",
            "Albay",
            "Antique",
            "Apayao",
            "Aurora",
            "Basilan",
            "Bataan",
            "Batanes",
            "Batangas",
            "Benguet",
            "Biliran",
            "Bohol",
            "Bukidnon",
            "Bulacan",
            "Cagayan",
            "Camarines Norte",
            "Camarines Sur",
            "Camiguin",
            "Capiz",
            "Catanduanes",
            "Cavite",
            "Cebu",
            "Cotabato",
            "Davao de Oro",
            "Davao del Norte",
            "Davao del Sur",
            "Davao Occidental",
            "Davao Oriental",
            "Dinagat Islands",
            "Eastern Samar",
            "Guimaras",
            "Ifugao",
            "Ilocos Norte",
            "Ilocos Sur",
            "Iloilo",
            "Isabela",
            "Kalinga",
            "La Union",
            "Laguna",
            "Lanao del Norte",
            "Lanao del Sur",
            "Leyte",
            "Maguindanao del Norte",
            "Maguindanao del Sur",
            "Marinduque",
            "Masbate",
            "Metropolitan Manila",
            "Misamis Occidental",
            "Misamis Oriental",
            "Mountain Province",
            "Negros Occidental",
            "Negros Oriental",
            "Northern Samar",
            "Nueva Ecija",
            "Nueva Vizcaya",
            "Occidental Mindoro",
            "Oriental Mindoro",
            "Palawan",
            "Pampanga",
            "Pangasinan",
            "Quezon",
            "Quirino",
            "Rizal",
            "Romblon",
            "Samar",
            "Sarangani",
            "Siquijor",
            "Sorsogon",
            "South Cotabato",
            "Southern Leyte",
            "Sultan Kudarat",
            "Sulu",
            "Surigao del Norte",
            "Surigao del Sur",
            "Tarlac",
            "Tawi-Tawi",
            "Zambales",
            "Zamboanga del Norte",
            "Zamboanga del Sur",
            "Zamboanga Sibugay"
        )

        val PHILIPPINE_CITY_MUNICIPALITY_OPTIONS = mapOf(
            "Bulacan" to listOf(
                "Angat",
                "Balagtas",
                "Baliuag",
                "Bocaue",
                "Bulakan",
                "Bustos",
                "Calumpit",
                "Dona Remedios Trinidad",
                "Guiguinto",
                "Hagonoy",
                "Malolos City",
                "Marilao",
                "Meycauayan City",
                "Norzagaray",
                "Obando",
                "Pandi",
                "Paombong",
                "Plaridel",
                "Pulilan",
                "San Ildefonso",
                "San Jose del Monte City",
                "San Miguel",
                "San Rafael",
                "Santa Maria"
            ),
            "Davao del Sur" to listOf(
                "Bansalan",
                "Digos City",
                "Hagonoy",
                "Kiblawan",
                "Magsaysay",
                "Malalag",
                "Matanao",
                "Padada",
                "Santa Cruz",
                "Sulop"
            ),
            "Metropolitan Manila" to listOf(
                "Caloocan City",
                "Las Pinas City",
                "Makati City",
                "Malabon City",
                "Mandaluyong City",
                "Manila City",
                "Marikina City",
                "Muntinlupa City",
                "Navotas City",
                "Paranaque City",
                "Pasay City",
                "Pasig City",
                "Quezon City",
                "San Juan City",
                "Taguig City",
                "Valenzuela City"
            )
        )
    }
}
