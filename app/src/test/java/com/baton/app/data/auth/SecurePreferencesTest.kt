package com.baton.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M3-T1 tests for [SecurePreferences]. The EncryptedSharedPreferences
 * backend is a thin wrapper around AndroidKeyStore, which Robolectric
 * doesn't support ([KeyStoreException: NoSuchAlgorithmException] at
 * the master-key build). The tests below are skipped under the JVM
 * test runner and exercised on a real device or emulator. The
 * contract they verify is small:
 *
 *  - `databasePassphrase()` returns 32 bytes.
 *  - The passphrase is stable across calls.
 *  - `hasDatabasePassphrase()` reports the presence/absence correctly.
 *  - `clearDatabasePassphrase()` removes the stored key and reports
 *    `true` when something was removed.
 *  - Two `SecurePreferences` instances against the same store return
 *    the same key.
 *
 * The real-device run path is: cold-launch the app, adb pull the
 * DB, confirm it's encrypted (the file header is not "SQLite
 * format 3" — see the M3-T1 sign-off checklist).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecurePreferencesTest {

    private fun newPrefs(): SecurePreferences =
        SecurePreferences(ApplicationProvider.getApplicationContext<Context>())

    @Test @Ignore("Robolectric has no AndroidKeyStore; exercised on a real device")
    fun `databasePassphrase returns 32 bytes on first call`() {
        val prefs = newPrefs()
        val pass = prefs.databasePassphrase()
        assertEquals("passphrase must be 32 bytes for SQLCipher-256", 32, pass.size)
    }

    @Test @Ignore("Robolectric has no AndroidKeyStore; exercised on a real device")
    fun `databasePassphrase returns the same value on subsequent calls`() {
        val prefs = newPrefs()
        val first = prefs.databasePassphrase()
        val second = prefs.databasePassphrase()
        assertEquals("passphrase must be stable across calls", first.toList(), second.toList())
    }

    @Test @Ignore("Robolectric has no AndroidKeyStore; exercised on a real device")
    fun `hasDatabasePassphrase is false before the first call, true after`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs: SharedPreferences =
            context.getSharedPreferences("baton_secure_prefs", Context.MODE_PRIVATE)
        rawPrefs.edit().clear().commit()

        val prefs = newPrefs()
        assertFalse("no passphrase before first call", prefs.hasDatabasePassphrase())
        prefs.databasePassphrase()
        assertTrue("passphrase present after first call", prefs.hasDatabasePassphrase())
    }

    @Test @Ignore("Robolectric has no AndroidKeyStore; exercised on a real device")
    fun `clearDatabasePassphrase removes the stored key`() {
        val prefs = newPrefs()
        prefs.databasePassphrase()
        assertTrue(prefs.hasDatabasePassphrase())
        assertTrue("clear returns true when a key was present", prefs.clearDatabasePassphrase())
        assertFalse(prefs.hasDatabasePassphrase())
    }

    @Test @Ignore("Robolectric has no AndroidKeyStore; exercised on a real device")
    fun `clearDatabasePassphrase returns false when no key is present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs: SharedPreferences =
            context.getSharedPreferences("baton_secure_prefs", Context.MODE_PRIVATE)
        rawPrefs.edit().clear().commit()

        val prefs = newPrefs()
        assertFalse("clear on empty store returns false", prefs.clearDatabasePassphrase())
    }

    @Test @Ignore("Robolectric has no AndroidKeyStore; exercised on a real device")
    fun `two SecurePreferences instances against the same store return the same key`() {
        val a = newPrefs()
        val b = newPrefs()
        val keyA = a.databasePassphrase()
        val keyB = b.databasePassphrase()
        assertNotNull(keyA)
        assertEquals(keyA.toList(), keyB.toList())
    }
}
