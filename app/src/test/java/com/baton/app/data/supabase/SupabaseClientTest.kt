package com.baton.app.data.supabase

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * **Finding test for Task 6** — proves the Supabase client surface is wired
 * correctly: the factory returns a non-null client and the URL + key
 * round-trip, all in a pure-JVM test (no real network).
 *
 * Network behaviour is mocked via [MockEngine] and covered end-to-end by
 * the M0 e2e finding test (Task 9).
 *
 * **Why `withAuth = false`:** the [io.github.jan.supabase.auth.Auth] plugin's
 * default session manager needs Android `SharedPreferences` (via
 * `com.russhwolf:settings`). That requires Robolectric, not pure JVM. M0
 * doesn't need Auth — that's Task 7. The test passes `withAuth = false`
 * to install only the data-plane plugins (Postgrest, Functions, Realtime,
 * Storage) and stay in unit-test land.
 */
class SupabaseClientTest {

    @Test
    fun `buildSupabaseClient returns a client with the given URL and key`() {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val httpClient = HttpClient(engine)
        val testUrl = "https://test.supabase.co"
        val testKey = "sb_publishable_test"

        val client = buildSupabaseClient(
            url = testUrl,
            key = testKey,
            httpClient = httpClient,
            withAuth = false,  // Auth's default session manager needs Android SharedPreferences; not needed for M0.
        )

        // Conclusion 1: factory returns a non-null client
        assertNotNull("SupabaseClient factory returned null", client)

        // Conclusion 2: the URL and key round-trip — the factory used our values,
        // not defaults. This is the finding: a wrong wire-up would silently
        // talk to a different project. supabase-kt 3.x normalises the URL by
        // stripping the scheme, so we assert the host is preserved.
        assertEquals("test.supabase.co", client.supabaseUrl)
        assertEquals(testKey, client.supabaseKey)
    }
}
