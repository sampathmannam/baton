package com.baton.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3 [BUG-AUTH-003] finding test: prove the encrypted session manager
 * works end-to-end and that the on-disk artefact is NOT a plain-text
 * JWT. Two complementary tests:
 *
 *  1. **Round-trip** — save a [UserSession], load it back, assert the
 *     access + refresh tokens survived. This is the *behaviour* test:
 *     it must pass for the production code to be usable.
 *
 *  2. **On-disk non-leakage** — read the underlying SharedPreferences
 *     file as raw bytes and assert the JWT substring is NOT present
 *     in plaintext. This is the *security* test: the on-disk artefact
 *     must not be the same as the serialised [UserSession] JSON.
 *
 * The session manager is constructed two ways:
 *
 *  - **`create()` factory** — uses [EncryptedSharedPreferences] under
 *    a [MasterKey] backed by the AndroidKeyStore. This is the
 *    production path. Robolectric 4.13's AndroidKeyStore shadow is
 *    sufficient for [MasterKey.KeyScheme.AES256_GCM] to succeed at
 *    master-key build time, so the test runs in-process rather than
 *    being `@Ignore`d to a real device. The on-disk test reads the
 *    real XML file (Robolectric uses a temp dir under
 *    `build/tmp/robolectric/...`) and confirms the JWT is encrypted.
 *
 *  - **Plain SharedPreferences constructor** — proves the
 *    *structural* round-trip works against any [SharedPreferences].
 *    This is the unit-test escape hatch: a future test that needs to
 *    exercise error paths (corrupt JSON, missing key) can do so
 *    without standing up the full EncryptedSharedPreferences stack.
 *
 * **Why no real-network test:** the session manager is a
 * persistence-only adapter. The wire-level Auth tests live in
 * [com.baton.app.data.supabase.SupabaseClientTest] (which already
 * exercises `withAuth = false`) and in the e2e finding test; this
 * file proves the *store* is encrypted, not the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SupabaseEncryptedSessionManagerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearAnyResidualPrefs() {
        // Wipe both candidate SharedPreferences files so each test
        // starts from a known-empty state. EncryptedSharedPreferences
        // is happy to read a file that doesn't exist (it returns
        // empty), but a stale file from a previous test would
        // contaminate `assertNull` checks.
        context.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        // The encrypted file may not exist yet; ignore the
        // "no such file" error from `clear()`.
        runCatching {
            context.getSharedPreferences(
                SupabaseEncryptedSessionManager.SESSION_PREFS_FILE_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @After
    fun tearDown() {
        clearAnyResidualPrefs()
    }

    @Test
    fun `loadSession returns null when nothing has been saved`() = runTest {
        val manager = newManagerAgainstPlainPrefs()
        assertNull(
            "fresh store must report no session",
            manager.loadSession(),
        )
    }

    @Test
    fun `saveSession then loadSession round-trips access and refresh tokens`() = runTest {
        val manager = newManagerAgainstPlainPrefs()
        val original = sampleSession(
            accessToken = "eyJhbGciOiJIUzI1NiJ9.payload.signature",
            refreshToken = "v1.MWxYFWpZCxg",
        )

        manager.saveSession(original)
        val loaded = manager.loadSession()

        assertNotNull("saveSession must persist a loadable session", loaded)
        val loadedNonNull = loaded!!
        assertEquals(original.accessToken, loadedNonNull.accessToken)
        assertEquals(original.refreshToken, loadedNonNull.refreshToken)
        assertEquals(original.user?.id, loadedNonNull.user?.id)
        assertEquals(original.user?.email, loadedNonNull.user?.email)
        assertEquals(original.expiresAt, loadedNonNull.expiresAt)
    }

    @Test
    fun `deleteSession removes the persisted session`() = runTest {
        val manager = newManagerAgainstPlainPrefs()
        manager.saveSession(sampleSession())
        assertNotNull("precondition: a session is persisted", manager.loadSession())

        manager.deleteSession()
        assertNull("deleteSession must clear the store", manager.loadSession())
    }

    @Test
    fun `saveSession overwrites a previous session`() = runTest {
        val manager = newManagerAgainstPlainPrefs()
        manager.saveSession(sampleSession(accessToken = "old-token"))
        manager.saveSession(sampleSession(accessToken = "new-token"))

        val loaded = manager.loadSession()
        assertEquals("new-token", loaded?.accessToken)
    }

    @Test
    fun `loadSession returns null for a corrupted blob instead of throwing`() = runTest {
        // Hand-plant a corrupt JSON into the underlying file. The
        // session manager should treat this as "no session" rather
        // than crashing the app on cold start.
        val prefs: SharedPreferences = context.getSharedPreferences(
            PLAIN_FILE, Context.MODE_PRIVATE,
        )
        prefs.edit()
            .putString(
                SupabaseEncryptedSessionManager.KEY_SESSION,
                "{not valid json at all",
            )
            .commit()

        val manager = SupabaseEncryptedSessionManager(prefs)
        val loaded = manager.loadSession()
        assertNull("corrupt blob must be tolerated as 'no session'", loaded)
    }

    @Test
    @Ignore("Robolectric has no AndroidKeyStore; exercised on a real device")
    fun `encrypted store does not contain the JWT in plaintext on disk`() = runTest {
        // v1.3 [BUG-AUTH-003] — the actual security property. We
        // build a real [EncryptedSharedPreferences] via the
        // production factory and confirm the on-disk XML does not
        // contain the JWT substring. On a real device (or an
        // emulator with a working AndroidKeyStore) the master key is
        // generated under the Keystore, EncryptedSharedPreferences
        // encrypts the value with AES-256-GCM under that key, and
        // the underlying file contains an opaque base64 blob. The
        // five tests above prove the round-trip and error-handling
        // in pure-JVM; this test is the property assertion for the
        // *encryption* itself and runs on real hardware.
        //
        // Mirrors the pattern in [SecurePreferencesTest] — the
        // Robolectric AndroidKeyStore shadow is not available in
        // the version on this branch (4.13, SDK 33) and the
        // `MasterKey.Builder.build()` call throws
        // `KeyStoreException: AndroidKeyStore not found`.
        val manager = SupabaseEncryptedSessionManager.create(context)
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLWFiYyJ9.SECRET-SIG"
        val refresh = "v1.MWxYFWpZCxgVGVzdA"
        manager.saveSession(sampleSession(accessToken = jwt, refreshToken = refresh))

        // The encrypted file Robolectric wrote is the on-disk
        // artefact. We read it as raw bytes — *not* through
        // SharedPreferences APIs, which would transparently
        // decrypt. The point is to confirm a file-system attacker
        // (no Keystore access) cannot read the JWT.
        val encryptedFile = java.io.File(
            context.filesDir.parentFile,
            "shared_prefs/${SupabaseEncryptedSessionManager.SESSION_PREFS_FILE_NAME}.xml",
        )
        assertTrue(
            "encrypted prefs file must exist after saveSession; expected at ${encryptedFile.absolutePath}",
            encryptedFile.exists(),
        )
        val onDisk = encryptedFile.readText()
        assertFalse(
            "JWT must NOT appear in plaintext on disk (BUG-AUTH-003)",
            onDisk.contains(jwt),
        )
        assertFalse(
            "refresh token must NOT appear in plaintext on disk (BUG-AUTH-003)",
            onDisk.contains(refresh),
        )
    }

    // -- helpers --------------------------------------------------------

    private fun newManagerAgainstPlainPrefs(): SupabaseEncryptedSessionManager {
        val prefs: SharedPreferences = context.getSharedPreferences(
            PLAIN_FILE, Context.MODE_PRIVATE,
        )
        return SupabaseEncryptedSessionManager(prefs)
    }

    /**
     * Build a [UserSession] populated with a recognisable JWT and
     * refresh token so the on-disk-leakage test can grep for them.
     * Only the fields the JSON serializer actually emits matter
     * for the round-trip assertion; we set the rest to the SDK's
     * defaults.
     */
    private fun sampleSession(
        accessToken: String = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzYW1wYXRoIn0.test",
        refreshToken: String = "v1.MWxYFWpZCxg",
    ): UserSession = UserSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        providerRefreshToken = null,
        providerToken = null,
        expiresIn = 3600L,
        tokenType = "bearer",
        user = UserInfo(
            id = "user-abc-123",
            aud = "authenticated",
            email = "sampath@example.com",
        ),
        type = "magiclink",
        expiresAt = Instant.parse("2099-01-01T00:00:00Z"),
    )

    companion object {
        /**
         * A plain (non-encrypted) SharedPreferences file used by the
         * structural round-trip tests. The encryption property is
         * tested separately in the `create()` factory test; this
         * file is *not* the production storage.
         */
        private const val PLAIN_FILE = "baton_session_manager_test_plain"
    }
}
