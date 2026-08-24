package com.kaavalan.note.data.backup

import android.content.Context
import com.kaavalan.note.data.auth.SecurePreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * v2.1.1 (PM rating): the [GoogleOAuthClient.consumeOAuthState]
 * / [GoogleOAuthClient.deleteOAuthStateFile] round-trip
 * tests.
 *
 * The OAuth `state` + `code_verifier` are persisted to
 * `filesDir/oauth_state.tmp` in [GoogleOAuthClient.signIn] and
 * read in the [com.kaavalan.note.features.auth.OAuthCallbackActivity]
 * (which then forwards the values back to
 * [GoogleOAuthClient.completeSignIn]). The tests pin the
 * happy path (state + verifier round-trip) and the
 * failure modes (missing file / malformed file / delete).
 *
 * The `state` parameter is the OAuth 2.0
 * authorization-code-injection defence (RFC 6749 §10.12);
 * the `code_verifier` is the PKCE binding (RFC 7636).
 * Without these, any installed app on the device can fire
 * `baton://oauth-callback?code=ATTACKER_CODE` and have
 * Baton exchange the attacker's auth code.
 *
 * Robolectric is used for the [Context] (the real Android
 * `filesDir` is internal). [SecurePreferences] is mocked
 * (via [mockk]) because it depends on AndroidKeyStore,
 * which Robolectric cannot fully emulate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GoogleOAuthClientStateTest {

    private lateinit var context: Context
    private lateinit var client: GoogleOAuthClient
    private val securePreferences: SecurePreferences = mockk(relaxed = true)
    private val httpClient: HttpClient = HttpClient(
        MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"access_token":"x","refresh_token":"y","expires_in":3600}"""),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json"),
            )
        },
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Clean any leftover state file from a previous run.
        File(context.filesDir, "oauth_state.tmp").delete()
        client = GoogleOAuthClient(
            context = context,
            httpClient = httpClient,
            securePreferences = securePreferences,
        )
    }

    @After
    fun tearDown() {
        File(context.filesDir, "oauth_state.tmp").delete()
    }

    @Test
    fun `consumeOAuthState returns null when the state file is absent`() {
        // No signIn() called — the file should not exist.
        assertNull(client.consumeOAuthState())
    }

    @Test
    fun `consumeOAuthState returns null when the state file is malformed`() {
        val file = File(context.filesDir, "oauth_state.tmp")
        file.writeText("only-one-line-no-newline")
        // The file exists but has no `\n` separator, so the
        // split fails and we return null.
        assertNull(client.consumeOAuthState())
    }

    @Test
    fun `consumeOAuthState returns the state and verifier pair when the file is well formed`() {
        val expectedState = "abc123-base64url-state"
        val expectedVerifier = "verifier-base64url-pkce"
        val file = File(context.filesDir, "oauth_state.tmp")
        file.writeText("$expectedState\n$expectedVerifier")

        val pair = client.consumeOAuthState()
        assertNotNull(pair)
        assertEquals(expectedState, pair!!.first)
        assertEquals(expectedVerifier, pair.second)
    }

    @Test
    fun `deleteOAuthStateFile removes the file`() {
        val file = File(context.filesDir, "oauth_state.tmp")
        file.writeText("state\nverifier")
        assertTrue(file.exists())

        client.deleteOAuthStateFile()

        assertFalse(file.exists())
    }

    @Test
    fun `completeSignIn deletes the state file on success`() = runTest {
        // v2.1.1: completeSignIn must read the code_verifier
        // from the state file and include it in the token
        // POST (PKCE). The state file is deleted on success.
        val expectedState = "state-for-pkce"
        val expectedVerifier = "verifier-for-pkce"
        File(context.filesDir, "oauth_state.tmp")
            .writeText("$expectedState\n$expectedVerifier")

        // We can't easily inspect the body the MockEngine
        // receives, so we just verify that completeSignIn
        // doesn't throw and that the state file is deleted.
        client.completeSignIn("dummy-auth-code")
        assertFalse(File(context.filesDir, "oauth_state.tmp").exists())
    }
}
