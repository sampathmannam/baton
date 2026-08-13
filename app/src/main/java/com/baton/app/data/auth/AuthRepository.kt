package com.baton.app.data.auth

import com.baton.app.BuildConfig
import com.baton.app.data.supabase.buildSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Auth repository wrapping Supabase Auth. The [SupabaseClient] is built
 * inside the constructor body (rather than bound through Hilt) for the
 * same reason as [com.baton.app.data.person.SupabasePersonRepository]:
 * Hilt's KSP1 processor cannot resolve KMP AAR types as binding-parameter
 * types. The result is a fully-wired singleton per consumer.
 */
class AuthRepository(httpClient: HttpClient) {

    private val client: SupabaseClient = buildSupabaseClient(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
        httpClient = httpClient,
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
