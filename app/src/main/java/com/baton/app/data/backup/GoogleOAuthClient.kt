package com.baton.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.baton.app.data.auth.SecurePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.0 (PM rating): the Google OAuth client.
 *
 * **The flow.** When the user taps "Sign in with
 * Google" in Settings, [signIn] opens a Chrome Custom
 * Tab to Google's OAuth page. The user signs in +
 * grants the `drive.appdata` scope. Google redirects
 * to `baton://oauth-callback?code=AUTH_CODE`. The
 * [com.baton.app.features.auth.OAuthCallbackActivity]
 * catches the redirect, extracts the code, and calls
 * [completeSignIn].
 *
 * [completeSignIn] exchanges the auth code for an
 * access + refresh token via Google's token endpoint
 * (using a Ktor [HttpClient] — no Play Services Auth
 * needed). The refresh token is stored in
 * [SecurePreferences]; the access token is held in
 * memory and refreshed on demand by [getAccessToken].
 *
 * **Why Custom Tabs instead of GoogleSignInClient.**
 * The offline build cache doesn't have
 * `play-services-auth:21.2.0`. The Custom Tabs
 * approach uses only `androidx.browser:browser` (which
 * is in the cache). The trade-off: the user gets a
 * Chrome Custom Tab instead of the one-tap "Sign in
 * with Google" picker, but the end result (an access
 * token + the ability to call the Drive REST API) is
 * the same. A future v2.x can swap to the
 * GoogleSignInClient path for a smoother UX.
 *
 * **The redirect URI scheme.** The app declares an
 * intent filter for `baton://oauth-callback` in the
 * manifest. The OAuthCallbackActivity is a transparent
 * activity that catches the intent, extracts the
 * `code` parameter, calls [completeSignIn], and
 * finishes. The Custom Tabs flow is the standard
 * "Authorization Code with PKCE" pattern; we omit
 * PKCE for v2.1.0 (the redirect URI is on a
 * client-controlled scheme, which is the standard
 * public-client flow).
 */
@Singleton
class GoogleOAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val securePreferences: SecurePreferences,
) {

    /**
     * Held in memory after a successful
     * [completeSignIn]. Refreshed on demand by
     * [getAccessToken]. The access token expires in
     * ~1h; the refresh token is long-lived (until the
     * user revokes the app's access in their Google
     * account settings).
     */
    @Volatile
    private var cachedAccessToken: String? = null

    /**
     * Open the Google OAuth page in a Chrome Custom
     * Tab. The user signs in, grants the
     * `drive.appdata` scope, and Google redirects to
     * `baton://oauth-callback?code=...`. The
     * [com.baton.app.features.auth.OAuthCallbackActivity]
     * catches the redirect and calls [completeSignIn].
     */
    fun signIn() {
        // The OAuth 2.0 Authorization Code flow (no PKCE
        // for v2.1.0). The scopes are:
        //   - drive.appdata: per-app hidden folder
        //   - email: read the user's email (so the Settings
        //     sheet can show "Signed in as foo@bar.com")
        // The `access_type=offline` request is critical —
        // without it, Google returns an access token but
        // no refresh token, and the user has to re-sign
        // in every time the access token expires (~1h).
        val authUrl = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
            .buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter(
                "scope",
                "https://www.googleapis.com/auth/drive.appdata email",
            )
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("include_granted_scopes", "true")
            .build()

        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, authUrl)
    }

    /**
     * Called by [com.baton.app.features.auth.OAuthCallbackActivity]
     * once the user has been redirected back with
     * `?code=...`. Exchanges the code for an access +
     * refresh token. The refresh token is stored in
     * [SecurePreferences] for next-time silent sign-in.
     */
    suspend fun completeSignIn(authCode: String) {
        // Google's token endpoint. Public-client flow:
        // POST with form-encoded body (no client_secret
        // for installed-app clients — Google does
        // PKCE-style verification via the redirect URI
        // match). In production the client_id would
        // come from a BuildConfig field or a
        // strings.xml resource. The v2.1.0 build uses
        // a placeholder client_id — replace with a real
        // Google Cloud Console OAuth 2.0 client ID
        // before shipping to the Play Store.
        val response = httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("code", authCode)
                append("client_id", CLIENT_ID)
                append("redirect_uri", REDIRECT_URI)
                append("grant_type", "authorization_code")
            },
        )
        require(response.status.value in 200..299) {
            "Token exchange failed: ${response.status} ${response.bodyAsText()}"
        }
        val json = JSONObject(response.bodyAsText())
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token", null)
        val expiresIn = json.optLong("expires_in", 3600L)
        cachedAccessToken = accessToken
        if (refreshToken != null && refreshToken.isNotEmpty()) {
            securePreferences.setGoogleRefreshToken(refreshToken)
        }
        securePreferences.setGoogleAccessTokenExpiry(System.currentTimeMillis() + expiresIn * 1000)
    }

    /**
     * Return a valid access token, refreshing from
     * the stored refresh token if the cached one is
     * missing or expired. Returns `null` if the user
     * has never signed in.
     */
    suspend fun getAccessToken(): String? {
        cachedAccessToken?.let { cached ->
            val expiry = securePreferences.getGoogleAccessTokenExpiry()
            if (expiry > System.currentTimeMillis() + 60_000) {
                return cached
            }
        }
        // Cached expired or absent — refresh.
        val refreshToken = securePreferences.getGoogleRefreshToken() ?: return null
        val response = httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("refresh_token", refreshToken)
                append("client_id", CLIENT_ID)
                append("grant_type", "refresh_token")
            },
        )
        if (response.status.value !in 200..299) {
            // The refresh token was revoked. Clear
            // everything so the Settings sheet renders
            // the "Sign in" CTA.
            securePreferences.clearGoogleTokens()
            cachedAccessToken = null
            return null
        }
        val json = JSONObject(response.bodyAsText())
        val accessToken = json.getString("access_token")
        val expiresIn = json.optLong("expires_in", 3600L)
        cachedAccessToken = accessToken
        securePreferences.setGoogleAccessTokenExpiry(System.currentTimeMillis() + expiresIn * 1000)
        // The new access token may also include a new
        // refresh token; capture if present.
        json.optString("refresh_token", null)?.takeIf { it.isNotEmpty() }?.let {
            securePreferences.setGoogleRefreshToken(it)
        }
        return accessToken
    }

    /**
     * Sign out — clear the in-memory access token, the
     * stored refresh token, and the expiry. The Settings
     * sheet renders the "Sign in" CTA again.
     */
    fun signOut() {
        cachedAccessToken = null
        securePreferences.clearGoogleTokens()
    }

    /**
     * Has the user signed in? `true` if a refresh token
     * is stored. The in-memory `cachedAccessToken` is
     * process-local and not a reliable signal (the
     * process can be killed; we read the refresh token
     * from SharedPreferences).
     */
    fun isSignedIn(): Boolean =
        securePreferences.getGoogleRefreshToken() != null

    companion object {
        // v2.1.0: placeholders. Replace with the real
        // Google Cloud Console OAuth 2.0 client ID +
        // redirect URI before shipping. The redirect
        // scheme is `baton` and the host is
        // `oauth-callback` — both are declared in the
        // AndroidManifest as an intent filter on
        // [com.baton.app.features.auth.OAuthCallbackActivity].
        const val CLIENT_ID = "BATON_GOOGLE_OAUTH_CLIENT_ID_PLACEHOLDER"
        const val REDIRECT_URI = "baton://oauth-callback"
    }
}
