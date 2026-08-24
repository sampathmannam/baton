package com.baton.app.data.supabase

import android.content.Context
import com.baton.app.BuildConfig
import com.baton.app.data.auth.SupabaseEncryptedSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import javax.inject.Singleton

/**
 * Hilt module that provides the singleton [BatonSupabase] wrapper
 * (and the underlying [HttpClient] it uses).
 *
 * **Why OkHttp and not the Android engine:** the M2-T7 Realtime
 * plugin needs WebSockets; the Android engine does not advertise
 * the `WebSocketCapability` and throws at install time. OkHttp
 * supports WebSockets out of the box and is already a transitive
 * dep of the Supabase client. The [WebSockets] plugin is
 * installed explicitly so the Ktor client reports the
 * capability regardless of which engine is underneath.
 *
 * **Why the [BatonSupabase] wrapper, not a raw [SupabaseClient].**
 * v1.9.10 (Obs-1 fix): the pre-v1.9.10 design had each
 * repository build its own [SupabaseClient] in a field
 * initializer, producing 4 parallel Realtime WebSocket
 * connections at cold start. The fix is a single Hilt-provided
 * [BatonSupabase] singleton. The `io.github.jan.supabase.SupabaseClient`
 * AAR type is hidden inside the data class — KSP1 only needs
 * to see `BatonSupabase` (a plain Kotlin data class) at Hilt
 * graph time. See the [BatonSupabase] class docstring for the
 * KSP `error.NonExistentClass` failure mode that motivated the
 * wrapper.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }

    /**
     * Single [BatonSupabase] for the whole app. All four
     * repositories (`SupabasePersonRepository`,
     * `SupabaseInstructionRepository`, `SupabaseCaptureRepository`,
     * `AuthRepository`) inject this; the underlying
     * [SupabaseClient] is created exactly once on first access.
     *
     * The [SupabaseEncryptedSessionManager] is built with
     * [ApplicationContext] so the JWT + refresh tokens land in
     * Keystore-backed encrypted storage instead of plain-text
     * `supabase_auth.xml` SharedPreferences. (v1.3 BUG-AUTH-003)
     */
    @Provides
    @Singleton
    fun provideBatonSupabase(
        httpClient: HttpClient,
        @ApplicationContext context: Context,
    ): BatonSupabase = BatonSupabase.create {
        buildSupabaseClient(
            url = BuildConfig.SUPABASE_URL,
            key = BuildConfig.SUPABASE_ANON_KEY,
            httpClient = httpClient,
            sessionManager = SupabaseEncryptedSessionManager.create(context),
        )
    }
}
