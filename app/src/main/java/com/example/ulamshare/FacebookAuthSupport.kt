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
                Log.e(TAG, "Firebase auth with Facebook failed", error)
                onError(error)
            }
    }

    fun mergeFacebookUserProfile(
        context: Context,
        user: FirebaseUser,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val userRef = Firebase.firestore.collection(USERS_COLLECTION).document(user.uid)
        userRef.get()
            .addOnSuccessListener { document ->
                val profileSeed = mutableMapOf<String, Any>(
                    "uid" to user.uid,
                    "email" to (user.email ?: ""),
                    "authProvider" to "facebook",
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                user.displayName?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { profileSeed["fullName"] = it }

                user.photoUrl?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { profileSeed["profilePhotoUrl"] = it }

                val existingPhone = document.getString("phone").orEmpty()
                    .ifBlank { document.getString("mobile").orEmpty() }
                if (existingPhone.isNotBlank()) {
                    profileSeed["phone"] = existingPhone
                    profileSeed["mobile"] = existingPhone
                }

                val existingRole = document.getString("role").orEmpty()
                    .ifBlank { document.getString("roleKey").orEmpty() }
                if (existingRole.isBlank()) {
                    profileSeed["role"] = "user"
                }
                if (!document.exists()) {
                    profileSeed["createdAt"] = FieldValue.serverTimestamp()
                }

                userRef.set(profileSeed, SetOptions.merge())
                    .addOnSuccessListener {
                        CampaignAssignmentManager.syncForAuthenticatedUser(
                            context = context,
                            user = user,
                            profileSeed = profileSeed,
                            onComplete = onComplete,
                            onError = onError
                        )
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Unable to merge Facebook user profile", error)
                        onError(error)
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Unable to load existing user profile before merge", error)
                onError(error)
            }
    }

    fun userFriendlyError(context: Context, error: Exception): String {
        if (error is FirebaseAuthUserCollisionException ||
            error.message?.contains("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", ignoreCase = true) == true
        ) {
            return context.getString(R.string.facebook_existing_account_error)
        }

        val rawMessage = error.localizedMessage.orEmpty()
        val lowerMessage = rawMessage.lowercase(Locale.US)
        return when {
            "key hash" in lowerMessage -> context.getString(R.string.facebook_key_hash_error)
            "network" in lowerMessage -> "Network error. Please try again."
            "invalid" in lowerMessage && "credential" in lowerMessage ->
                "Facebook sign-in failed because the credential was invalid. Please try again."
            rawMessage.isNotBlank() -> rawMessage
            else -> context.getString(R.string.facebook_sign_in_failed)
        }
    }
}
