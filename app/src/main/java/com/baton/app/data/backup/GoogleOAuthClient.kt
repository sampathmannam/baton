package com.baton.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.baton.app.BuildConfig
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
 * to `baton://oauth-callback?code=AUTH_CODE&state=STATE`.
 * The [com.baton.app.features.auth.OAuthCallbackActivity]
 * catches the redirect, validates the `state` against
 * the persisted value, extracts the code, and calls
 * [completeSignIn].
 *
 * [completeSignIn] exchanges the auth code + PKCE
 * `code_verifier` for an access + refresh token via
 * Google's token endpoint (using a Ktor [HttpClient] —
 * no Play Services Auth needed). The refresh token is
 * stored in [SecurePreferences]; the access token is
 * held in memory and refreshed on demand by
 * [getAccessToken].
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
 * `code` + `state` parameters, validates `state`
 * against the persisted value, calls [completeSignIn],
 * and finishes.
 *
 * **v2.1.1 (security): `state` + PKCE.** The v2.1.0
 * OAuth flow was missing two standard defences against
 * authorization-code injection (the
 * "any-installed-app-can-fire-baton://oauth-callback
 * with-an-attacker's-code" attack):
 *
 *  1. **`state` (RFC 6749 §10.12).** [signIn] generates
 *     a 32-byte random secret, base64url-encodes it,
 *     sends it as `state=...` in the auth URL, and
 *     persists it to a private file in `filesDir`. The
 *     callback activity reads the same file and rejects
 *     the redirect if the inbound `state` doesn't match
 *     (or if the file is missing — which means the
 *     legitimate flow already consumed it). A
 *     malicious `am start -a android.intent.action.VIEW
 *     -d "baton://oauth-callback?code=ATTACKER_CODE"`
 *     fires the activity but the state file is absent
 *     (or already consumed) so the exchange is
 *     rejected.
 *
 *  2. **PKCE (RFC 7636, S256).** [signIn] generates a
 *     32-byte random `code_verifier`, base64url-encodes
 *     it, sends `code_challenge = base64url(SHA256(verifier))`
 *     and `code_challenge_method=S256` in the auth
 *     URL, and persists the verifier alongside the
 *     state. [completeSignIn] sends the verifier in
 *     the token POST. Google hashes it and compares to
 *     the `code_challenge` from the auth URL; if they
 *     don't match, the exchange is rejected. This
 *     binds the auth code to this device — a stolen
 *     code redeemed from a different device would fail
 *     the challenge comparison.
 *
 * **v2.1.1 (security): token-exchange response body
 * is no longer logged.** Google's error responses can
 * echo the offending `client_id` and the malformed
 * `code` value; the v2.1.0 `require(...)` message
 * dumped the full body to logcat. The v2.1.1 message
 * only includes the HTTP status code.
 *
 * **v2.1.1 (security): CustomTabs `FLAG_ACTIVITY_NEW_TASK`.**
 * [signIn] is called from a Hilt-injected
 * [GoogleOAuthClient] (which holds an `@ApplicationContext`),
 * not from an Activity. Without `FLAG_ACTIVITY_NEW_TASK`,
 * `CustomTabsIntent.launchUrl` throws
 * `AndroidRuntimeException: Calling startActivity() from
 * outside of an Activity context requires the
 * FLAG_ACTIVITY_NEW_TASK flag`. The flag is added to the
 * Custom Tabs intent so the OAuth flow can be initiated
 * from any context.
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
     * `baton://oauth-callback?code=...&state=...`. The
     * [com.baton.app.features.auth.OAuthCallbackActivity]
     * catches the redirect, validates the `state`, and
     * calls [completeSignIn].
     */
    fun signIn() {
        // v2.1.1 (security): the OAuth flow now uses the
        // `state` parameter + PKCE (S256). Without these,
        // any installed app on the device can fire
        // `baton://oauth-callback?code=ATTACKER_CODE` and
        // have Baton exchange the attacker's auth code
        // for a real access + refresh token (the
        // settings sheet will then show "Signed in as
        // attacker@evil.com" and every Drive backup
        // lands in the attacker's appDataFolder).
        val state = generateState()
        val verifier = generatePkceVerifier()
        val challenge = sha256Base64Url(verifier)
        persistOAuthState(state, verifier)

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
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val intent = CustomTabsIntent.Builder().build()
        // v2.1.1 (security): CustomTabs requires an
        // Activity context. The injected @ApplicationContext
        // is the application context, which means
        // `launchUrl` throws `AndroidRuntimeException:
        // Calling startActivity() from outside of an
        // Activity context requires the
        // FLAG_ACTIVITY_NEW_TASK flag`. Set the flag
        // here so the OAuth flow can be initiated from
        // any context (e.g. the Hilt-injected
        // GoogleOAuthClient inside a ViewModel).
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, authUrl)
    }

    private fun generateState(): String {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    private fun generatePkceVerifier(): String {
        // RFC 7636: 43-128 chars, [A-Z][a-z][0-9]-._~
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    private fun sha256Base64Url(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    /**
     * Persist the OAuth `state` and `code_verifier` to a
     * private file in the app's filesDir so the
     * OAuthCallbackActivity (a separate Activity) can
     * read them. The file is deleted on
     * [completeSignIn] success.
     */
    private fun persistOAuthState(state: String, verifier: String) {
        val file = java.io.File(context.filesDir, "oauth_state.tmp")
        file.writeText("$state\n$verifier")
    }

    /**
     * Read + consume the persisted OAuth state. Returns
     * (state, verifier) on hit, or null if the file
     * doesn't exist / is malformed. The caller is
     * expected to [deleteOAuthStateFile] on success.
     */
    fun consumeOAuthState(): Pair<String, String>? {
        val file = java.io.File(context.filesDir, "oauth_state.tmp")
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val parts = text.split("\n", limit = 2)
        if (parts.size != 2) return null
        return parts[0] to parts[1]
    }

    fun deleteOAuthStateFile() {
        java.io.File(context.filesDir, "oauth_state.tmp").delete()
    }

    /**
     * Called by [com.baton.app.features.auth.OAuthCallbackActivity]
     * once the user has been redirected back with
     * `?code=...&state=...`. The activity has already
     * validated `state` against the persisted value
     * (see [consumeOAuthState]); this function
     * exchanges the code + PKCE verifier for an
     * access + refresh token. The refresh token is
     * stored in [SecurePreferences] for next-time
     * silent sign-in.
     */
    suspend fun completeSignIn(authCode: String) {
        // v2.1.1 (security): PKCE. The token endpoint
        // receives the `code_verifier` we generated in
        // [signIn] and persisted. Google hashes it and
        // compares to the `code_challenge` from the auth
        // URL; if they don't match, the exchange is
        // rejected. This binds the code to this device —
        // a stolen code redeemed from a different device
        // would fail the challenge comparison.
        val (_, verifier) = consumeOAuthState()
            ?: error("OAuth state file missing — sign-in flow was not initiated on this device")
        // v2.1.1 (security): never log the full response
        // body. Google's error responses can echo the
        // offending `client_id` and the malformed
        // `code` value, which are tokens we don't want in
        // logcat.
        val response = httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("code", authCode)
                append("client_id", CLIENT_ID)
                append("redirect_uri", REDIRECT_URI)
                append("grant_type", "authorization_code")
                append("code_verifier", verifier)
            },
        )
        require(response.status.value in 200..299) {
            "Token exchange failed: ${response.status}"
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
        // v2.1.1: delete the state file once the exchange
        // is done. The file contains the `code_verifier`
        // (a one-shot secret) and should not persist.
        deleteOAuthStateFile()
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
        // v2.1.1 (security): the client ID + redirect URI
        // are read from `BuildConfig` (which is populated
        // by `app/build.gradle.kts` from `local.properties`
        // or a Gradle project property). The v2.1.0
        // hard-coded placeholders failed the token
        // exchange with `400 invalid_client` because
        // Google rejects unknown client IDs. The user
        // sets `BATON_GOOGLE_OAUTH_CLIENT_ID` in
        // `local.properties` before shipping to the
        // Play Store.
        //
        // The redirect scheme is `baton` and the host
        // is `oauth-callback` — both are declared in
        // the AndroidManifest as an intent filter on
        // [com.baton.app.features.auth.OAuthCallbackActivity].
        val CLIENT_ID: String = BuildConfig.BATON_GOOGLE_OAUTH_CLIENT_ID
        val REDIRECT_URI: String = BuildConfig.BATON_GOOGLE_OAUTH_REDIRECT_URI
    }
}
