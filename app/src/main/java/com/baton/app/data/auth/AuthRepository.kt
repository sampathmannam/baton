package com.baton.app.data.auth

import android.content.Context
import com.baton.app.BuildConfig
import com.baton.app.data.supabase.buildSupabaseClient
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Auth repository wrapping Supabase Auth. The [SupabaseClient] is built
 * inside the constructor body (rather than bound through Hilt) for the
 * same reason as [com.baton.app.data.person.SupabasePersonRepository]:
 * Hilt's KSP1 processor cannot resolve KMP AAR types as binding-parameter
 * types. The result is a fully-wired singleton per consumer.
 *
 * v1.3 [BUG-AUTH-003]: the Supabase client is now configured with a
 * [SupabaseEncryptedSessionManager] so the JWT and refresh token are
 * persisted under the Keystore-backed master key instead of
 * `supabase_auth.xml` in plain text. The session manager is built
 * lazily from the application [Context] inside the constructor body —
 * the same place the client itself is built — to keep the KMP-AAR
 * out of the Hilt graph (see the note in
 * [com.baton.app.data.supabase.SupabaseClient]).
 *
 * v1.4.5: added [sendOtp] / [verifyOtp] for passwordless email OTP
 * sign-in (the alternative the user picked over Google OAuth). The
 * user types an email, gets a one-time code (or magic link) in their
 * inbox, types the code back, and the [SupabaseEncryptedSessionManager]
 * persists the session just like password sign-in.
 */
class AuthRepository @Inject constructor(
    httpClient: HttpClient,
    @ApplicationContext context: Context,
) {

    private val client: SupabaseClient = buildSupabaseClient(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
        httpClient = httpClient,
        sessionManager = SupabaseEncryptedSessionManager.create(context),
    )

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }

    /**
     * v1.4.5 passwordless sign-in step 1: send a one-time code (or
     * magic link, depending on the Supabase Auth provider config) to
     * the user's email. The user's note is sent as the OTP — if the
     * Supabase Auth provider has "Email OTP" or "Magic link" enabled,
     * Supabase dispatches it. If neither is enabled, the server
     * returns 422 and we surface a [SafeError] to the UI.
     *
     * Note: we call `signInWith(Email)` with **no password** — that's
     * the supabase-kt 3.x contract for "send OTP / magic link" via the
     * Email provider. Calling `signInWith(Email) { password = "..." }`
     * would silently attempt password auth and 401 on a fresh user.
     */
    suspend fun sendOtp(email: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            // No password = OTP / magic link dispatch.
        }
        Unit
    }

    /**
     * v1.4.5 passwordless sign-in step 2: verify the one-time code
     * the user typed from their email. On success supabase-kt sets
     * the session; the [SupabaseEncryptedSessionManager] persists it
     * under the Keystore-backed master key, and the app's
     * `observeSessionStatus()` flow flips to
     * [AuthSessionState.Authenticated] which navigates the user out
     * of the auth screen.
     *
     * [OtpType.Email.MAGIC_LINK] is the type the Supabase server
     * stamps on the email regardless of whether the delivery is a
     * 6-digit code or a tap-link — they're the same verification
     * path on the server side. (See Supabase docs:
     * "Email OTP / Magic link both verify with type=magiclink".)
     */
    suspend fun verifyOtp(email: String, token: String): Result<Unit> = runCatching {
        client.auth.verifyEmailOtp(OtpType.Email.MAGIC_LINK, email, token)
        Unit
    }

    /**
     * v1.2 root-cause fix (BATON-WIRE-008): signOut was a bare
     * suspend that propagated every exception. The SettingsViewModel
     * wrapped it in `runCatching`, but a future caller (deep link,
     * accessibility action, instrumentation test) would have crashed
     * the activity on a network failure during sign-out (e.g.
     * supabase-kt signOut requires a server round-trip to invalidate
     * the refresh token). We now wrap in `runCatching` and return
     * a `Result` so the caller decides what to do. The local DB wipe
     * (`AppInitializer.runOnSignOut()`) is already done by the caller
     * BEFORE this call, so the user is effectively signed out
     * client-side regardless of the network result.
     */
    suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
        Unit
    }

    /**
     * Observe the current auth state. Maps supabase-kt 3.x's [SessionStatus]
     * sealed values to a flat three-state enum ([AuthSessionState]) the UI
     * can render without coupling to the SDK.
     */
    fun observeSessionStatus(): Flow<AuthSessionState> =
        client.auth.sessionStatus.map { status ->
            when (status) {
                SessionStatus.Initializing -> AuthSessionState.Loading
                is SessionStatus.NotAuthenticated -> AuthSessionState.Unauthenticated
                is SessionStatus.Authenticated -> AuthSessionState.Authenticated
                is SessionStatus.RefreshFailure -> AuthSessionState.Unauthenticated
            }
        }
}

enum class AuthSessionState { Loading, Authenticated, Unauthenticated }
