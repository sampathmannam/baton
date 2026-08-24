package com.baton.app.data.captures

import com.baton.app.data.supabase.BatonSupabase
import com.baton.app.data.supabase.buildSupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SupabaseCaptureRepository].
 *
 * BATON-WIRE-006 (v1.3): the wire call must be idempotent.
 *
 *  1. `create()` includes the `Prefer: resolution=ignore-duplicates`
 *     header so PostgREST treats the insert as idempotent on the
 *     `(table, id)` primary key. Without this header, a retry after
 *     a response loss would either 409 or create a duplicate row.
 *
 *  2. The body includes the client-generated UUID. The server uses
 *     this as the primary key; a re-POST with the same id is a
 *     unique-key conflict that the `Prefer` header resolves into a
 *     safe no-op.
 *
 *  3. Re-POSTing the same id is safe end-to-end: the second call
 *     returns the same `Capture` (the server returns the existing
 *     row) and the client experiences no error.
 *
 * The wire layer is mocked via Ktor's [MockEngine], so the test
 * runs in pure JVM with no real network — consistent with the
 * sibling [com.baton.app.data.instructions.SupabaseInstructionRepositoryTest].
 */
class SupabaseCaptureRepositoryTest {

    /**
     * v1.9.10 (Obs-1 fix): test helper that wraps a [MockEngine]-backed
     * [HttpClient] in the production [BatonSupabase] wrapper. Mirrors
     * the production wiring in
     * [com.baton.app.data.supabase.SupabaseModule].
     */
    private fun testBaton(
        engine: MockEngine,
        url: String = "https://test.supabase.co",
        key: String = "sb_publishable_test",
        withAuth: Boolean = false,
    ): BatonSupabase = BatonSupabase.create {
        val httpClient = HttpClient(engine)
        buildSupabaseClient(
            url = url,
            key = key,
            httpClient = httpClient,
            withAuth = withAuth,
        )
    }

    @Test
    fun `create sets Prefer resolution=ignore-duplicates header and includes id in body`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null
        var capturedPreferRaw: String? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method
            capturedPath = request.url.fullPath
            // supabase-kt 3.1.1 joins the `prefer` list with commas
            // into a single `Prefer` header value, e.g.
            // `return=representation,resolution=ignore-duplicates`.
            // We capture the raw header string and assert
            // containment rather than equality.
            capturedPreferRaw = request.headers["Prefer"]
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = ByteReadChannel(
                    """
                    [{
                      "id":"cap-uuid-1",
                      "mode":"TEXT",
                      "raw_text":"hello",
                      "audio_uri":null,
                      "image_uri":null,
                      "processed":false,
                      "created_at":"2026-08-14T00:00:00+00:00"
                    }]
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repo = SupabaseCaptureRepository(testBaton(engine))

        val capture = repo.create(rawText = "hello", mode = CaptureMode.TEXT)

        // 1. Wire shape — POST to the captures table.
        assertEquals(HttpMethod.Post, capturedMethod)
        assertTrue(
            "path must start with /rest/v1/captures, was: $capturedPath",
            capturedPath.orEmpty().startsWith("/rest/v1/captures"),
        )

        // 2. BATON-WIRE-006: the Prefer header must include
        // `resolution=ignore-duplicates` so PostgREST dedupes on
        // the (table, id) primary key. The header is a
        // comma-joined list of tokens; we assert containment of
        // the `resolution=ignore-duplicates` token.
        val prefer = capturedPreferRaw.orEmpty()
        assertTrue(
            "Prefer header must include resolution=ignore-duplicates; got: $prefer",
            prefer.contains("resolution=ignore-duplicates"),
        )

        // 3. The body must include the client-generated UUID, the
        // raw text, and the mode. The id is the idempotency key.
        assertNotNull(capturedBody)
        val body = capturedBody!!
        assertTrue("body must contain id: $body", body.contains("\"id\":"))
        assertTrue("body must contain raw_text: $body", body.contains("\"raw_text\":\"hello\""))
        assertTrue("body must contain mode: $body", body.contains("\"mode\":\"TEXT\""))

        // 4. The returned Capture is decoded from the server's row.
        assertEquals("cap-uuid-1", capture.id)
        assertEquals(CaptureMode.TEXT, capture.mode)
        assertEquals("hello", capture.rawText)
        assertEquals(false, capture.processed)
    }

    /**
     * BATON-WIRE-006 retry-safety test.
     *
     * Scenario: the client makes the first POST. The server writes
     * the row and sends a 201 back, but the response is lost in
     * transit (timeout, RST). The client retries with the same id.
     *
     * With `Prefer: resolution=ignore-duplicates`, the server sees
     * the duplicate id and returns the existing row (200) instead of
     * creating a phantom. The client gets the same `Capture` back
     * and experiences no error.
     *
     * This test asserts both the header is set on *both* calls and
     * the client-side state is identical (no thrown exception, same
     * id, same content). The "no-op" the audit refers to is
     * server-side (no second row is created); the client just
     * observes that the second call succeeded with the same result.
     */
    @Test
    fun `re-POSTing the same capture id is safe (no error, returns existing row)`() = runTest {
        val capturedPreferRaws = mutableListOf<String?>()
        val capturedBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            capturedPreferRaws.add(request.headers["Prefer"])
            capturedBodies.add(request.body.toByteArray().decodeToString())
            // Both calls return the same row, simulating the server
            // returning the existing row on the retry (the
            // `ignore-duplicates` semantics). The status is 200 on
            // the retry because PostgREST returns 200 for an
            // ignored-insert, not 201.
            respond(
                content = ByteReadChannel(
                    """
                    [{
                      "id":"cap-same-id",
                      "mode":"TEXT",
                      "raw_text":"hello",
                      "audio_uri":null,
                      "image_uri":null,
                      "processed":false,
                      "created_at":"2026-08-14T00:00:00+00:00"
                    }]
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val repo = SupabaseCaptureRepository(testBaton(engine))

        // First POST with the same id we'll use on the retry.
        val first = repo.insertCapture(
            id = "cap-same-id",
            rawText = "hello",
            mode = CaptureMode.TEXT,
        )
        // Second POST — the retry, with the SAME id.
        val second = repo.insertCapture(
            id = "cap-same-id",
            rawText = "hello",
            mode = CaptureMode.TEXT,
        )

        // 1. Both calls set the Prefer header to include
        // `resolution=ignore-duplicates`. The header is a
        // comma-joined list of tokens; we assert containment of
        // the `resolution=ignore-duplicates` token.
        assertEquals(2, capturedPreferRaws.size)
        val firstPrefer = capturedPreferRaws[0].orEmpty()
        val secondPrefer = capturedPreferRaws[1].orEmpty()
        assertTrue(
            "first call must include Prefer: resolution=ignore-duplicates; got: $firstPrefer",
            firstPrefer.contains("resolution=ignore-duplicates"),
        )
        assertTrue(
            "second (retry) call must also include Prefer: resolution=ignore-duplicates; got: $secondPrefer",
            secondPrefer.contains("resolution=ignore-duplicates"),
        )

        // 2. Both calls include the same id in the body. This is
        // what makes the retry a no-op on the server: the id is
        // the idempotency key.
        assertEquals(2, capturedBodies.size)
        assertTrue(
            "first body must contain id:cap-same-id: ${capturedBodies[0]}",
            capturedBodies[0].contains("\"id\":\"cap-same-id\""),
        )
        assertTrue(
            "second (retry) body must also contain id:cap-same-id: ${capturedBodies[1]}",
            capturedBodies[1].contains("\"id\":\"cap-same-id\""),
        )

        // 3. Both calls succeed and return the same Capture.
        // The server returned the existing row on the retry, so
        // the client observes no error and no client-side state
        // change — exactly the "no-op" the audit calls out.
        assertEquals("cap-same-id", first.id)
        assertEquals("cap-same-id", second.id)
        assertEquals(first.id, second.id)
        assertEquals(first.rawText, second.rawText)
        assertEquals(first.mode, second.mode)
    }
}
