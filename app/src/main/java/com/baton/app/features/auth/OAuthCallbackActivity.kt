package com.baton.app.features.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.baton.app.data.backup.GoogleOAuthClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v2.1.0 (PM rating): the OAuth callback activity.
 * Google redirects the user to
 * `baton://oauth-callback?code=...` after they sign in
 * + grant the `drive.appdata` scope. The activity
 * (declared in the manifest with the matching intent
 * filter) catches the intent, extracts the auth code,
 * calls [GoogleOAuthClient.completeSignIn] to
 * exchange the code for an access + refresh token,
 * and finishes.
 *
 * **The flow is single-task.** The user is bounced
 * out of the Settings sheet to the Chrome Custom
 * Tab for the OAuth page, then back to this activity.
 * The activity is `singleTop` so a re-entry (e.g. a
 * double-tap on the sign-in button) doesn't stack two
 * copies.
 *
 * **The activity is a transparent overlay.** It
 * finishes immediately after the token exchange
 * completes; the user lands back in the Settings
 * sheet which polls the [GoogleOAuthClient.isSignedIn]
 * flag on resume and re-renders the "Signed in as
 * ..." row.
 */
@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {

    @Inject lateinit var oauth: GoogleOAuthClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data ?: run {
            Log.w(TAG, "OAuth callback launched without data URI")
            finish()
            return
        }
        val code = data.getQueryParameter("code")
        if (code == null) {
            val error = data.getQueryParameter("error") ?: "(no error parameter)"
            Log.w(TAG, "OAuth callback missing code parameter (error=$error)")
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                oauth.completeSignIn(code)
                Log.i(TAG, "OAuth sign-in complete")
            } catch (e: Throwable) {
                Log.e(TAG, "OAuth token exchange failed", e)
            } finally {
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "BatonOAuthCallback"
    }
}
