package com.baton.app.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient

/**
 * Builds a [SupabaseClient] for Baton. The factory takes URL + key + an
 * externally-built [HttpClient] so unit tests can substitute a
 * [io.ktor.client.engine.mock.MockEngine] instead of the Android engine,
 * keeping the test layer pure-JVM.
 *
 * In supabase-kt 3.x, [createSupabaseClient] takes only URL + key + a
 * builder lambda. The HTTP engine is set as a property of the builder
 * (`httpEngine = httpClient.engine`), not as a constructor parameter.
 *
 * The plugins installed:
 *   * [Postgrest]   — REST access to the `persons` / `instructions` / `events` tables
 *   * [Auth]        — Supabase Auth (email + password / magic link)
 *   * [Functions]   — the cloud MCP server (Deno Edge Function)
 *   * [Realtime]    — push-based invalidation when another device changes a row
 *   * [Storage]     — audio / photo captures (Spec §4.5)
 *
 * [withAuth] is a test-time escape hatch: the Auth plugin's default
 * session manager needs Android `SharedPreferences` (or a custom session
 * manager) and is not available in pure-JVM unit tests. Production code
 * uses the default `true`; tests pass `false` to install just the
 * data-plane plugins. M0 doesn't need Auth anyway — that's Task 7.
 *
 * [sessionManager] is the v1.3 [BUG-AUTH-003] wire-up: when `withAuth`
 * is `true` the caller passes an encrypted session manager (see
 * [com.baton.app.data.auth.SupabaseEncryptedSessionManager]) so the
 * JWT + refresh tokens are persisted under the Keystore-backed
 * master key instead of the device's plain-text
 * `supabase_auth.xml` SharedPreferences. Passing `null` falls back
 * to supabase-kt's default `SettingsSessionManager` — only intended
 * for unit tests that don't have an Android Context to build a
 * real [SessionManager]. Production callers always pass a real
 * instance built via [com.baton.app.data.auth.SupabaseEncryptedSessionManager.create].
 */
fun buildSupabaseClient(
    url: String,
    key: String,
    httpClient: HttpClient,
    withAuth: Boolean = true,
    sessionManager: SessionManager? = null,
): SupabaseClient = createSupabaseClient(
    supabaseUrl = url,
    supabaseKey = key,
) {
    httpEngine = httpClient.engine
    install(Postgrest)
    if (withAuth) {
        if (sessionManager != null) {
            install(Auth) {
                this.sessionManager = sessionManager
            }
        } else {
            install(Auth)
        }
    }
    install(Functions)
    install(Realtime)
    install(Storage)
}
