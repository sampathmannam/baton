package com.baton.app.ui.util

import com.baton.app.features.capture.ErrorType
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * v1.2 regression test (BEAU-NEW-01 / BUG-AUTH-008).
 *
 * Locks the security-relevant property of [SafeError.forUser]:
 * the returned string MUST NOT contain the URL, JWT, apikey,
 * X-Client-Info header, or any other PII / SDK identifier that
 * supabase-kt / Ktor attach to the raw exception's `message`.
 *
 * If a future change reverts to `e.message` (or fails to redact
 * a new exception type), this test fails the build.
 */
class SafeErrorTest {

    /** A canonical secret-looking URL + JWT we expect SafeError to NEVER surface. */
    private val secretUrl = "https://cfnmpqwfvhlnbblxqesm.supabase.co/rest/v1/instructions?select=%2A"
    private val secretJwt = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.dozjgNryXcZVm5lTHb8KDXGwY4H8Jz9w"
    private val secretApikey = "sb_publishable_ueOz-C6YKZM8CDPJSqsSgQ_UtYoPJVm"
    private val secretClientInfo = "supabase-kt/3.1.1"

    private fun assertSafe(s: String) {
        assertFalse("SafeError leaked URL: $s", s.contains("supabase.co", ignoreCase = true))
        assertFalse("SafeError leaked URL path: $s", s.contains("/rest/v1/", ignoreCase = true))
        assertFalse("SafeError leaked JWT: $s", s.contains("eyJ", ignoreCase = true))
        assertFalse("SafeError leaked Bearer: $s", s.contains("Bearer", ignoreCase = true))
        assertFalse("SafeError leaked apikey: $s", s.contains("sb_publishable", ignoreCase = true))
        assertFalse("SafeError leaked SDK name: $s", s.contains("supabase-kt", ignoreCase = true))
        assertFalse("SafeError leaked X-Client-Info: $s", s.contains("X-Client-Info", ignoreCase = true))
        assertFalse("SafeError leaked SDK version: $s", s.contains("/3.1.1"))
    }

    /**
     * Build a real [HttpResponse] with the desired status. Ktor's
     * [MockEngine] returns real responses with realistic status
     * codes that supabase-kt's [RestException] can consume.
     */
    private suspend fun fakeResponse(
        status: HttpStatusCode,
        withLeakyBody: String = "",
    ): HttpResponse {
        val engine = MockEngine {
            respond(
                content = withLeakyBody,
                status = status,
                headers = headersOf("X-Client-Info", secretClientInfo),
            )
        }
        return HttpClient(engine).get("https://example.invalid/")
    }

    @Test
    fun `default string is returned for unknown throwable`() {
        val msg = SafeError.forUser(IllegalStateException("boom"), "Custom default.")
        assertEquals("Custom default.", msg)
    }

    @Test
    fun `default string is returned for unknown throwable - never leaks e_message`() = runBlocking {
        val rawMsg = "something at $secretUrl with $secretJwt and $secretApikey and $secretClientInfo"
        val msg = SafeError.forUser(IllegalStateException(rawMsg), "Safe fallback.")
        assertEquals("Safe fallback.", msg)
        assertSafe(msg)
    }

    @Test
    fun `RestException 401 returns invalid email or password`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.Unauthorized, withLeakyBody = "x")
        val e = UnauthorizedRestException("invalid credentials", response, "ignored")
        val msg = SafeError.forUser(e, "ignored")
        assertEquals("Invalid email or password.", msg)
        assertSafe(msg)
    }

    @Test
    fun `RestException 400 returns invalid email or password`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.BadRequest, withLeakyBody = "bad")
        val e = BadRequestRestException("bad request with $secretUrl $secretJwt", response, "ignored")
        val msg = SafeError.forUser(e, "ignored")
        assertEquals("Invalid email or password.", msg)
        assertSafe(msg)
    }

    @Test
    fun `RestException 422 returns invalid email string`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.UnprocessableEntity, withLeakyBody = "x")
        val e = BadRequestRestException("validation failed: $secretJwt $secretApikey $secretClientInfo", response, "ignored")
        val msg = SafeError.forUser(e, "ignored")
        assertEquals("That email looks invalid.", msg)
        assertSafe(msg)
    }

    @Test
    fun `RestException 429 returns rate limit string`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.TooManyRequests, withLeakyBody = "x")
        val e = BadRequestRestException("rate limited $secretUrl", response, "ignored")
        val msg = SafeError.forUser(e, "ignored")
        assertEquals("Too many attempts. Try again in a minute.", msg)
        assertSafe(msg)
    }

    @Test
    fun `RestException 500 range returns unavailable string`() = runBlocking {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)) {
            val response = fakeResponse(status, withLeakyBody = "x")
            val e = BadRequestRestException("server boom $secretUrl $secretJwt", response, "ignored")
            val msg = SafeError.forUser(e, "ignored")
            assertEquals("Sign-in service unavailable. Try again later.", msg)
            assertSafe(msg)
        }
    }

    @Test
    fun `RestException unknown status returns default`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.fromValue(418), withLeakyBody = "x")
        val e = BadRequestRestException("weird status $secretUrl", response, "ignored")
        val msg = SafeError.forUser(e, "Custom.")
        assertEquals("Custom.", msg)
        assertSafe(msg)
    }

    @Test
    fun `HttpRequestException returns no-connection string`() {
        // HttpRequestException extends IOException and takes (message, HttpRequestBuilder).
        // We don't need a real request to exercise SafeError - any throwable of that type
        // is enough to lock the mapping.
        val builder = io.ktor.client.request.HttpRequestBuilder().apply {
            url.host = "example.invalid"
            url.protocol = io.ktor.http.URLProtocol.HTTPS
        }
        val e = HttpRequestException("Failed to connect to $secretUrl: $secretClientInfo", builder)
        val msg = SafeError.forUser(e, "ignored")
        assertEquals("No connection. Check your network.", msg)
        assertSafe(msg)
    }

    @Test
    fun `IOException returns no-connection string`() {
        val e = IOException("Connection timed out reaching $secretUrl")
        val msg = SafeError.forUser(e, "ignored")
        assertEquals("No connection. Check your network.", msg)
        assertSafe(msg)
    }

    @Test
    fun `null throwable message still returns default`() {
        val e = IllegalStateException() // null message
        val msg = SafeError.forUser(e, "Default works.")
        assertEquals("Default works.", msg)
    }

    // -----------------------------------------------------------------
    // v1.4 (PHONE-FINDING-7): save-context mapper.
    // -----------------------------------------------------------------

    @Test
    fun `v14 forUserSave - RestException 401 returns session expired string`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.Unauthorized, withLeakyBody = "x")
        val e = UnauthorizedRestException("jwt expired $secretUrl", response, "ignored")
        val msg = SafeError.forUserSave(e, "ignored")
        assertEquals("Your session expired. Please sign in again.", msg)
        assertSafe(msg)
    }

    @Test
    fun `v14 forUserSave - RestException 403 returns session expired string`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.Forbidden, withLeakyBody = "x")
        val e = BadRequestRestException("forbidden $secretUrl", response, "ignored")
        val msg = SafeError.forUserSave(e, "ignored")
        assertEquals("Your session expired. Please sign in again.", msg)
        assertSafe(msg)
    }

    @Test
    fun `v14 forUserSave - RestException 429 returns rate limit string`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.TooManyRequests, withLeakyBody = "x")
        val e = BadRequestRestException("rate limited $secretUrl", response, "ignored")
        val msg = SafeError.forUserSave(e, "ignored")
        assertEquals("Too many saves. Try again in a minute.", msg)
        assertSafe(msg)
    }

    @Test
    fun `v14 forUserSave - RestException 500 range returns unavailable string`() = runBlocking {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)) {
            val response = fakeResponse(status, withLeakyBody = "x")
            val e = BadRequestRestException("server boom $secretUrl $secretJwt", response, "ignored")
            val msg = SafeError.forUserSave(e, "ignored")
            assertEquals("Save service unavailable. Try again later.", msg)
            assertSafe(msg)
        }
    }

    @Test
    fun `v14 forUserSave - RestException unknown status returns default`() = runBlocking {
        val response = fakeResponse(HttpStatusCode.fromValue(418), withLeakyBody = "x")
        val e = BadRequestRestException("weird status $secretUrl", response, "ignored")
        val msg = SafeError.forUserSave(e, "Custom save default.")
        assertEquals("Custom save default.", msg)
        assertSafe(msg)
    }

    @Test
    fun `v14 forUserSave - HttpRequestException returns no-connection string`() {
        val builder = io.ktor.client.request.HttpRequestBuilder().apply {
            url.host = "example.invalid"
            url.protocol = io.ktor.http.URLProtocol.HTTPS
        }
        val e = HttpRequestException("Failed to connect to $secretUrl: $secretClientInfo", builder)
        val msg = SafeError.forUserSave(e, "ignored")
        assertEquals("No connection. Check your network.", msg)
        assertSafe(msg)
    }

    @Test
    fun `v14 forUserSave - IOException returns no-connection string`() {
        val e = IOException("Connection timed out reaching $secretUrl")
        val msg = SafeError.forUserSave(e, "ignored")
        assertEquals("No connection. Check your network.", msg)
        assertSafe(msg)
    }

    @Test
    fun `v14 forUserSave - unknown throwable returns default and never leaks e_message`() = runBlocking {
        val rawMsg = "something at $secretUrl with $secretJwt and $secretApikey and $secretClientInfo"
        val msg = SafeError.forUserSave(IllegalStateException(rawMsg), "Safe save fallback.")
        assertEquals("Safe save fallback.", msg)
        assertSafe(msg)
    }

    @Test
    fun `v14 forUserSave - null throwable message still returns default`() {
        val e = IllegalStateException() // null message
        val msg = SafeError.forUserSave(e, "Save default works.")
        assertEquals("Save default works.", msg)
    }

    /**
     * v1.4 (PHONE-FINDING-7): the user-facing string for the
     * NEEDS_PERSON_FIRST error type is locked here.
     */
    @Test
    fun `v14 forCaptureErrorType - NEEDS_PERSON_FIRST returns the locked fallback text`() {
        val s = SafeError.forCaptureErrorType(ErrorType.NEEDS_PERSON_FIRST)
        assertNotNull(s)
        assertTrue(
            "NEEDS_PERSON_FIRST message must start with 'Save failed.' (no 'Could not...')",
            s!!.startsWith("Save failed."),
        )
        assertTrue(
            "NEEDS_PERSON_FIRST message must guide the user ('Add a person first')",
            s.contains("Add a person first", ignoreCase = true),
        )
        assertSafe(s)
    }

    @Test
    fun `v14 forCaptureErrorType - NONE returns null so the sheet renders nothing`() {
        assertNull(SafeError.forCaptureErrorType(ErrorType.NONE))
    }

    @Test
    fun `v14 forCaptureErrorType - NETWORK and PERMISSION and UNKNOWN return null (VM owns the message)`() {
        assertNull(SafeError.forCaptureErrorType(ErrorType.NETWORK_UNAVAILABLE))
        assertNull(SafeError.forCaptureErrorType(ErrorType.PERMISSION_DENIED))
        assertNull(SafeError.forCaptureErrorType(ErrorType.UNKNOWN))
    }

    @Test
    fun `v14 classifyForCapture - HttpRequestException is NETWORK_UNAVAILABLE`() {
        val builder = io.ktor.client.request.HttpRequestBuilder().apply {
            url.host = "example.invalid"
            url.protocol = io.ktor.http.URLProtocol.HTTPS
        }
        val e = HttpRequestException("connect failed", builder)
        assertEquals(ErrorType.NETWORK_UNAVAILABLE, SafeError.classifyForCapture(e))
    }

    @Test
    fun `v14 classifyForCapture - IOException is NETWORK_UNAVAILABLE`() {
        assertEquals(ErrorType.NETWORK_UNAVAILABLE, SafeError.classifyForCapture(IOException("disk full")))
    }

    @Test
    fun `v14 classifyForCapture - RestException 5xx is NETWORK_UNAVAILABLE`() = runBlocking {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)) {
            val response = fakeResponse(status, withLeakyBody = "x")
            val e = BadRequestRestException("server boom", response, "ignored")
            assertEquals(
                "RestException $status should classify to NETWORK_UNAVAILABLE",
                ErrorType.NETWORK_UNAVAILABLE,
                SafeError.classifyForCapture(e),
            )
        }
    }

    @Test
    fun `v14 classifyForCapture - RestException 4xx is UNKNOWN`() = runBlocking {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests, HttpStatusCode.fromValue(422))) {
            val response = fakeResponse(status, withLeakyBody = "x")
            val e = BadRequestRestException("user error", response, "ignored")
            assertEquals(
                "RestException $status should classify to UNKNOWN",
                ErrorType.UNKNOWN,
                SafeError.classifyForCapture(e),
            )
        }
    }

    @Test
    fun `v14 classifyForCapture - any other throwable is UNKNOWN`() {
        assertEquals(ErrorType.UNKNOWN, SafeError.classifyForCapture(IllegalStateException("boom")))
        assertEquals(ErrorType.UNKNOWN, SafeError.classifyForCapture(RuntimeException()))
    }
}

