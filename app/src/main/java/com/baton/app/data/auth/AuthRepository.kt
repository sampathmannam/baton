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

    suspend fun signOut() {
        client.auth.signOut()
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
