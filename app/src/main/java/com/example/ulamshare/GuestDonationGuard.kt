package com.example.ulamshare

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.google.firebase.auth.FirebaseAuth

object GuestDonationGuard {
    private const val SESSION_PREFS = "ulamshare_session"
    private const val KEY_GUEST_MODE = "guestMode"
    private const val KEY_SESSION_TYPE = "sessionType"
    private const val KEY_USER_ROLE = "userRole"
    private const val ACCOUNT_REQUIRED_TITLE = "Account required"
    private const val ACCOUNT_REQUIRED_MESSAGE =
        "You can\u2019t donate in Guest Mode. Please sign up or log in to continue."

    const val EXTRA_RETURN_TO_DONATION = "returnToDonation"

    fun isGuestUser(context: Context): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) return true

        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val sessionType = prefs.getString(KEY_SESSION_TYPE, "").orEmpty()
        val role = prefs.getString(KEY_USER_ROLE, "").orEmpty()
        return prefs.getBoolean(KEY_GUEST_MODE, false) ||
            sessionType.equals("guest", ignoreCase = true) ||
            role.equals("guest", ignoreCase = true)
    }

    fun showLoginRequiredDialog(
        context: Context,
        campaignId: String? = null,
        campaignTitle: String? = null,
        finishAfterNavigation: (() -> Unit)? = null,
        finishOnCancel: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setTitle(ACCOUNT_REQUIRED_TITLE)
            .setMessage(ACCOUNT_REQUIRED_MESSAGE)
            .setPositiveButton("Sign Up") { _, _ ->
                context.startActivity(loginIntent(context, RegisterActivity::class.java, campaignId, campaignTitle))
                finishAfterNavigation?.invoke()
            }
            .setNegativeButton("Log In") { _, _ ->
                context.startActivity(loginIntent(context, LoginActivity::class.java, campaignId, campaignTitle))
                finishAfterNavigation?.invoke()
            }
            .setNeutralButton("Cancel") { _, _ -> finishOnCancel?.invoke() }
            .setOnCancelListener { finishOnCancel?.invoke() }
            .show()
    }

    fun blockIfGuest(
        context: Context,
        campaignId: String? = null,
        campaignTitle: String? = null,
        finishAfterNavigation: (() -> Unit)? = null,
        finishOnCancel: (() -> Unit)? = null
    ): Boolean {
        if (!isGuestUser(context)) return false
        showLoginRequiredDialog(
            context = context,
            campaignId = campaignId,
            campaignTitle = campaignTitle,
            finishAfterNavigation = finishAfterNavigation,
            finishOnCancel = finishOnCancel
        )
        return true
    }

    private fun loginIntent(
        context: Context,
        target: Class<*>,
        campaignId: String?,
        campaignTitle: String?
    ): Intent {
        return Intent(context, target).apply {
            putExtra(EXTRA_RETURN_TO_DONATION, true)
            putExtra("campaignId", campaignId.orEmpty())
            putExtra("campaignTitle", campaignTitle.orEmpty())
            putExtra("title", campaignTitle.orEmpty())
        }
    }
}
