package com.baton.app.data.instructions

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-T5 unit test for [SupabaseInstructionRepository]. Mocks the
 * Postgrest backend with Ktor's [MockEngine] and asserts the wire
 * format end-to-end:
 *
 *  1. `create()` POSTs to `/rest/v1/instructions` with the expected
 *     JSON body (direction=OUTGOING, status=OPEN, source=TEXT, the
 *     `due_at`, `raw_text`, `person_id` all preserved).
 *  2. The returned row is decoded into a domain [Instruction] with
 *     server-generated `id` + timestamps.
 *  3. A 409 (race condition) propagates to the caller; the save flow
 *     surfaces it to the user.
 *
 * The real Supabase round-trip is covered by the M1 e2e finding
 * test (FT-1.2) which writes via the real backend and queries back.
 */
class SupabaseInstructionRepositoryTest {

    @Test
    fun `create posts to instructions with direction OUTGOING and status OPEN`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedMethod = request.method
            capturedPath = request.url.fullPath
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = ByteReadChannel(
                    """
                    [{
                      "id":"ins-uuid-1",
                      "person_id":"person-uuid-1",
                      "direction":"OUTGOING",
                      "status":"OPEN",
                      "source":"TEXT",
                      "priority":"NORMAL",
                      "title":"send FIR 47 — SHO Ramu",
                      "raw_text":"Tell SHO Ramu to send FIR 47 by Friday",
                      "due_at":"2026-08-15T17:00:00+05:30",
                      "captured_at":"2026-08-11T12:00:00+00:00",
                      "created_at":"2026-08-11T12:00:00+00:00",
                      "updated_at":"2026-08-11T12:00:00+00:00"
                    }]
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val httpClient = HttpClient(engine)
        val repo = SupabaseInstructionRepository(
            httpClient = httpClient,
            url = "https://test.supabase.co",
            key = "sb_publishable_test",
            withAuth = false,
        )

        val saved = repo.create(
            personId = "person-uuid-1",
            source = Source.TEXT,
            priority = Priority.NORMAL,
            title = "send FIR 47 — SHO Ramu",
            rawText = "Tell SHO Ramu to send FIR 47 by Friday",
            dueAt = "2026-08-15T17:00:00+05:30",
        )

        // 1. Wire shape
        assertEquals(HttpMethod.Post, capturedMethod)
        assertTrue(
            "path must start with /rest/v1/instructions, was: $capturedPath",
            capturedPath.orEmpty().startsWith("/rest/v1/instructions"),
        )
        assertNotNull(capturedBody)
        val body = capturedBody!!
        assertTrue("body must contain direction=OUTGOING: $body", body.contains("\"direction\":\"OUTGOING\""))
        assertTrue("body must contain status=OPEN: $body", body.contains("\"status\":\"OPEN\""))
        assertTrue("body must contain source=TEXT: $body", body.contains("\"source\":\"TEXT\""))
        assertTrue("body must contain priority=NORMAL: $body", body.contains("\"priority\":\"NORMAL\""))
        assertTrue("body must contain due_at: $body", body.contains("\"due_at\":\"2026-08-15T17:00:00+05:30\""))
        assertTrue("body must contain person_id: $body", body.contains("\"person_id\":\"person-uuid-1\""))
        assertTrue("body must contain captured_at: $body", body.contains("\"captured_at\":"))

        // 2. Decoded domain
        assertEquals("ins-uuid-1", saved.id)
        assertEquals("person-uuid-1", saved.personId)
        assertEquals(Direction.OUTGOING, saved.direction)
        assertEquals(Status.OPEN, saved.status)
        assertEquals(Source.TEXT, saved.source)
        assertEquals(Priority.NORMAL, saved.priority)
        assertEquals("send FIR 47 — SHO Ramu", saved.title)
        assertEquals("Tell SHO Ramu to send FIR 47 by Friday", saved.rawText)
        assertEquals("2026-08-15T17:00:00+05:30", saved.dueAt)
    }

    @Test
    fun `create with a null person_id sends person_id null in the body`() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = ByteReadChannel(
                    """
                    [{"id":"ins-2","direction":"OUTGOING","status":"OPEN",
                      "source":"TEXT","priority":"LOW",
                      "title":"review pending cases","raw_text":"Review pending cases on Sunday",
                      "captured_at":"2026-08-11T12:00:00+00:00",
                      "created_at":"2026-08-11T12:00:00+00:00",
                      "updated_at":"2026-08-11T12:00:00+00:00"}]
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf("Content-Type", "application/json"),
            )
        }
        val httpClient = HttpClient(engine)
        val repo = SupabaseInstructionRepository(
            httpClient = httpClient,
            url = "https://test.supabase.co",
            key = "sb_publishable_test",
            withAuth = false,
        )

        val saved = repo.create(
            personId = null,
            source = Source.TEXT,
            priority = Priority.LOW,
            title = "review pending cases",
            rawText = "Review pending cases on Sunday",
            dueAt = null,
        )

        val body = capturedBody!!
        assertTrue("body must contain person_id:null: $body", body.contains("\"person_id\":null"))
        // The body has `"due_at":null` (the key is always serialised),
        // but the value should be the JSON null literal.
        assertTrue("body must contain due_at:null: $body", body.contains("\"due_at\":null"))

        assertNull(saved.personId)
        assertNull(saved.dueAt)
    }

    @Test(expected = io.github.jan.supabase.postgrest.exception.PostgrestRestException::class)
    fun `create surfaces a 409 conflict as a PostgrestRestException`() = runTest {
        val engine = MockEngine { _ ->
            respondError(HttpStatusCode.Conflict)
        }
        val httpClient = HttpClient(engine)
        val repo = SupabaseInstructionRepository(
            httpClient = httpClient,
            url = "https://test.supabase.co",
            key = "sb_publishable_test",
            withAuth = false,
        )

        repo.create(
            personId = "person-x",
            source = Source.TEXT,
            priority = Priority.NORMAL,
            title = "x",
            rawText = "x",
            dueAt = null,
        )
    }
}
