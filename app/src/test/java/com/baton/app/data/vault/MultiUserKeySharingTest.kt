package com.baton.app.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.8.0 (PROD-READINESS-P2-#1): the
 * multi-user key-sharing round-trip test.
 *
 * The v1.5.0 vault has a single passphrase.
 * A pilot deployment with 2-5 officers in
 * one station needs per-officer passphrases
 * that all unlock the same SQLCipher DB.
 *
 * The pattern is "station master key + per-
 * user key encryption key + per-user share":
 *  - SMK = 256-bit AES key (encrypts the
 *    SQLCipher DB)
 *  - KEK_u = PBKDF2(passphrase_u, salt_u)
 *  - Share_u = AES-GCM( SMK, KEK_u, nonce_u )
 *
 * The class is pure-JVM (no Android crypto)
 * and the tests are Robolectric only because
 * the build infrastructure wires `@RunWith`
 * for everything; the tests themselves
 * exercise JCE + AES-GCM which are standard
 * JVM APIs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MultiUserKeySharingTest {

    @Test
    fun `wrap then unwrap returns the original master key`() {
        val masterKey = MultiUserKeySharing.newMasterKey()
        val share = MultiUserKeySharing.wrap(
            masterKey = masterKey,
            userId = "officer-1",
            passphrase = "correct horse battery staple".toCharArray(),
        )
        val unwrapped = MultiUserKeySharing.unwrap(share, "correct horse battery staple".toCharArray())
        assertArrayEquals(masterKey, unwrapped.masterKey)
    }

    @Test
    fun `each wrap call produces a different share for the same inputs`() {
        // The salt + nonce are freshly generated
        // every call, so the same master + the
        // same passphrase produces different
        // bytes. This is correct: a share is
        // a one-time, per-user-derivation artifact.
        val masterKey = MultiUserKeySharing.newMasterKey()
        val first = MultiUserKeySharing.wrap(
            masterKey = masterKey,
            userId = "officer-1",
            passphrase = "same passphrase".toCharArray(),
        )
        val second = MultiUserKeySharing.wrap(
            masterKey = masterKey,
            userId = "officer-1",
            passphrase = "same passphrase".toCharArray(),
        )
        // The salts differ.
        assertNotEquals(first.salt.toList(), second.salt.toList())
        // The nonces differ.
        assertNotEquals(first.nonce.toList(), second.nonce.toList())
        // The ciphertexts differ.
        assertNotEquals(first.ciphertext.toList(), second.ciphertext.toList())
        // Both still unwrap to the same master.
        assertArrayEquals(
            masterKey,
            MultiUserKeySharing.unwrap(first, "same passphrase".toCharArray()).masterKey,
        )
        assertArrayEquals(
            masterKey,
            MultiUserKeySharing.unwrap(second, "same passphrase".toCharArray()).masterKey,
        )
    }

    @Test
    fun `wrong passphrase fails to unwrap the share`() {
        val masterKey = MultiUserKeySharing.newMasterKey()
        val share = MultiUserKeySharing.wrap(
            masterKey = masterKey,
            userId = "officer-2",
            passphrase = "the right one".toCharArray(),
        )
        val error = assertThrows(VaultError.MasterKeyUnwrap::class.java) {
            MultiUserKeySharing.unwrap(share, "the wrong one".toCharArray())
        }
        // The error message must not leak the
        // AEADBadTagException detail; it must
        // surface only the "passphrase did not
        // unwrap" reason. (See the class-level
        // KDoc for the threat-model rationale.)
        assertEquals(
            "passphrase did not unwrap the share for user officer-2",
            error.message,
        )
    }

    @Test
    fun `two officers have independent passphrases for the same master key`() {
        val masterKey = MultiUserKeySharing.newMasterKey()
        val shareA = MultiUserKeySharing.wrap(
            masterKey = masterKey,
            userId = "officer-A",
            passphrase = "alpha-only".toCharArray(),
        )
        val shareB = MultiUserKeySharing.wrap(
            masterKey = masterKey,
            userId = "officer-B",
            passphrase = "bravo-only".toCharArray(),
        )
        // A's passphrase unwraps A's share.
        assertArrayEquals(
            masterKey,
            MultiUserKeySharing.unwrap(shareA, "alpha-only".toCharArray()).masterKey,
        )
        // B's passphrase unwraps B's share.
        assertArrayEquals(
            masterKey,
            MultiUserKeySharing.unwrap(shareB, "bravo-only".toCharArray()).masterKey,
        )
        // A's passphrase does NOT unwrap B's share.
        assertThrows(VaultError.MasterKeyUnwrap::class.java) {
            MultiUserKeySharing.unwrap(shareB, "alpha-only".toCharArray())
        }
        // B's passphrase does NOT unwrap A's share.
        assertThrows(VaultError.MasterKeyUnwrap::class.java) {
            MultiUserKeySharing.unwrap(shareA, "bravo-only".toCharArray())
        }
    }

    @Test
    fun `rewrap with a new master key invalidates the old share's master but reuses the passphrase`() {
        // The "rotate master key" path: the admin
        // generates a fresh SMK, then re-wraps
        // it for every officer using each
        // officer's existing passphrase.
        val oldMaster = MultiUserKeySharing.newMasterKey()
        val newMaster = MultiUserKeySharing.newMasterKey()
        val officerPassphrase = "officer-3's passphrase".toCharArray()
        val oldShare = MultiUserKeySharing.wrap(oldMaster, "officer-3", officerPassphrase)
        val newShare = MultiUserKeySharing.rewrap(newMaster, "officer-3", officerPassphrase)
        // The new share unwraps to the new master.
        assertArrayEquals(
            newMaster,
            MultiUserKeySharing.unwrap(newShare, officerPassphrase).masterKey,
        )
        // The old share still unwraps to the old master (no silent rotation).
        assertArrayEquals(
            oldMaster,
            MultiUserKeySharing.unwrap(oldShare, officerPassphrase).masterKey,
        )
        // A "wrong passphrase" + the new share is still rejected.
        assertThrows(VaultError.MasterKeyUnwrap::class.java) {
            MultiUserKeySharing.unwrap(newShare, "wrong".toCharArray())
        }
    }

    @Test
    fun `share version mismatch is rejected`() {
        // The class currently emits version 1.
        // A hand-built version-2 share must be
        // rejected by this v1.8.0 build (the
        // "downgrade" defense).
        val masterKey = MultiUserKeySharing.newMasterKey()
        val realShare = MultiUserKeySharing.wrap(masterKey, "officer-4", "good")
        val futureShare = realShare.copy(version = 2)
        val error = assertThrows(VaultError.MasterKeyUnwrap::class.java) {
            MultiUserKeySharing.unwrap(futureShare, "good".toCharArray())
        }
        assertEquals(
            "share version 2 is not supported by this build",
            error.message,
        )
    }

    @Test
    fun `blank passphrase is rejected at the API boundary`() {
        val masterKey = MultiUserKeySharing.newMasterKey()
        assertThrows(IllegalArgumentException::class.java) {
            MultiUserKeySharing.wrap(masterKey, "officer-5", "")
        }
    }

    @Test
    fun `master key length is enforced`() {
        // A 31-byte or 33-byte master is a programming error.
        val tooShort = ByteArray(31)
        assertThrows(IllegalArgumentException::class.java) {
            MultiUserKeySharing.wrap(tooShort, "officer-6", "pass")
        }
    }
}
