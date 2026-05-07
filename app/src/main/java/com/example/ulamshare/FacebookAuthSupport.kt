package com.example.ulamshare

import android.content.Context
import android.util.Log
import com.facebook.AccessToken
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Locale

object FacebookAuthSupport {
    private const val TAG = "FacebookAuth"
    private const val SOCIAL_TAG = "SocialAuth"
    private const val USERS_COLLECTION = "users"

    fun signInWithAccessToken(
        accessToken: AccessToken,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val credential = FacebookAuthProvider.getCredential(accessToken.token)
        FirebaseAuth.getInstance()
            .signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    val error = IllegalStateException("Facebook sign-in succeeded but no Firebase user was returned.")
                    Log.e(TAG, "Firebase credential success, but user was null")
                    onError(error)
                    return@addOnSuccessListener
                }
                Log.d(TAG, "Firebase credential success")
                onSuccess(user)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Firebase Facebook sign-in failed", error)
                onError(error)
            }
    }

    fun saveOrMergeSocialUserProfile(
        context: Context,
        user: FirebaseUser,
        provider: String,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userRef = Firebase.firestore.collection(USERS_COLLECTION).document(user.uid)
        Log.d(TAG, "Creating/merging Firestore user profile uid=${user.uid}")
        Log.d(SOCIAL_TAG, "Checking users/{uid} for uid=${user.uid}")
        userRef.get()
            .addOnSuccessListener { document ->
                val profileSeed = mutableMapOf<String, Any>(
                    "uid" to user.uid,
                    "authProvider" to provider,
                    "authProviders" to FieldValue.arrayUnion(provider),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                user.email?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { profileSeed["email"] = it }

                val existingFullName = document.getString("fullName").orEmpty()
                if (existingFullName.isBlank()) {
                    PrivacyDisplayHelper.publicName(user.displayName, "")
                        .takeIf { it.isNotBlank() }
                        ?.let { profileSeed["fullName"] = it }
                }

                user.photoUrl?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { profileSeed["profilePhotoUrl"] = it }

                val existingRole = document.getString("role").orEmpty()
                    .ifBlank { document.getString("roleKey").orEmpty() }
                if (existingRole.isBlank()) {
                    profileSeed["role"] = "user"
                }

                if (!document.exists()) {
                    Log.d(SOCIAL_TAG, "Creating new social user profile for uid=${user.uid}")
                    profileSeed["createdAt"] = FieldValue.serverTimestamp()
                    profileSeed["phone"] = document.getString("phone").orEmpty()
                    profileSeed["mobile"] = document.getString("mobile").orEmpty()
                } else {
                    Log.d(SOCIAL_TAG, "Merging existing social user profile for uid=${user.uid}")
                }

                CampaignAssignmentManager.syncForAuthenticatedUser(
                    context = context,
                    user = user,
                    profileSeed = profileSeed,
                    onComplete = onComplete,
                    onError = onError
                )
            }
            .addOnFailureListener { error ->
                Log.e(SOCIAL_TAG, "Unable to load existing user profile before merge", error)
                Log.e(TAG, "Firestore profile merge failed", error)
                onError(error)
            }
    }

    fun userFriendlyError(context: Context, error: Exception, providerLabel: String): String {
        val normalizedProvider = providerLabel.lowercase(Locale.US)
        if (error is FirebaseAuthUserCollisionException ||
            error.message?.contains("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", ignoreCase = true) == true
        ) {
            return when (normalizedProvider) {
                "facebook" -> context.getString(R.string.facebook_existing_account_error)
                "google" -> context.getString(R.string.google_existing_account_error)
                else -> "An account already exists with this email. Please sign in using the original method, then link $providerLabel later."
            }
        }

        val rawMessage = error.localizedMessage.orEmpty()
        val lowerMessage = rawMessage.lowercase(Locale.US)
        return when {
            "invalid app id" in lowerMessage || "does not look like a valid app id" in lowerMessage ->
                "Facebook App ID is invalid. Please check strings.xml."
            "key hash" in lowerMessage -> context.getString(R.string.facebook_key_hash_error)
            "network" in lowerMessage -> "Network error. Please try again."
            "invalid" in lowerMessage && "credential" in lowerMessage ->
                "$providerLabel sign-in failed because the credential was invalid. Please try again."
            normalizedProvider == "facebook" && lowerMessage.contains("cancel") ->
                context.getString(R.string.facebook_sign_in_cancelled)
            normalizedProvider == "facebook" && lowerMessage.contains("login") ->
                "Please sign in to Facebook to continue."
            rawMessage.isNotBlank() -> rawMessage
            normalizedProvider == "google" -> context.getString(R.string.google_sign_in_failed)
            else -> context.getString(R.string.facebook_sign_in_failed)
        }
    }
}
