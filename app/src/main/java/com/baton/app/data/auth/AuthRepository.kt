package com.baton.app.data.auth

import com.baton.app.data.supabase.BatonSupabase
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Auth repository wrapping Supabase Auth. v1.9.10 (Obs-1 fix): the
 * repository now takes the shared [BatonSupabase] singleton
 * (which already has a [SupabaseEncryptedSessionManager]
 * installed) instead of building its own client + session
 * manager in the constructor body. This collapses the
 * previously-separate [com.baton.app.data.person.SupabasePersonRepository],
 * [com.baton.app.data.instructions.SupabaseInstructionRepository],
 * [com.baton.app.data.captures.SupabaseCaptureRepository], and
 * this class into one shared [io.github.jan.supabase.SupabaseClient]
 * — one Realtime WebSocket, one HTTP pool, one auth state observer.
 *
 * The [SupabaseEncryptedSessionManager] is still installed (in
 * [com.baton.app.data.supabase.SupabaseModule.provideBatonSupabase])
 * so the JWT + refresh token are persisted under the Keystore-backed
 * master key instead of `supabase_auth.xml` in plain text. v1.3
 * [BUG-AUTH-003].
 *
 * v1.4.5: added [sendOtp] / [verifyOtp] for passwordless email OTP
 * sign-in.
 */
class AuthRepository @Inject constructor(
    batonSupabase: BatonSupabase,
) {

    private val client = batonSupabase.client

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
     * the user's email.
     *
     * **Why the [OTP] provider, not the [Email] one.** v1.4.5
     * originally called `signInWith(Email) { email }` and shipped a
     * SafeError "Invalid email or password" on every tap. The
     * emulator smoke test caught it: the [Email] provider's
     * `grant_type=password` endpoint always 400s when the password
     * field is blank, regardless of what the server is configured to
     * do. The right path is `signInWith(OTP) { email, createUser }`
     * which hits Supabase's `/auth/v1/otp` endpoint and dispatches
     * the configured email template (6-digit code or magic link).
     *
     * [createUser] is set to `true` so a brand-new email is
     * auto-registered — matches the v1.1.1 passwordless
     * "magic-link-creates-account" UX (no separate sign-up step).
     */
    suspend fun sendOtp(email: String): Result<Unit> = runCatching {
        client.auth.signInWith(OTP) {
            this.email = email
            this.createUser = true
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
