package com.kaavalan.note.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.9.11 (A2 audit fix): tests for the recovery-phrase
 * contract that DO NOT require AndroidKeyStore (which
 * Robolectric does not provide) or libsqlcipher.so (which
 * the unit test classpath does not include).
 *
 * **What we are actually testing here.** The full "I lost my
 * phone" flow depends on SQLCipher — the on-device DB is
 * encrypted with a passphrase derived from the BIP39 phrase
 * via Argon2id. Robolectric does not have libsqlcipher.so
 * on its classpath, so the SQLCipher round-trip (open with
 * passphrase, close, wipe file, re-open with same passphrase,
 * assert data) is a **manual drive-verify** item (see the
 * v1.9.11 release notes' deferrals table). What we *can* test
 * in a Robolectric unit test is the testable part of the
 * contract:
 *
 *  1. [IdentityCrypto.sha256Hex] is stable — re-hashing the
 *     same input gives the same value.
 *  2. [IdentityCrypto.sha256Hex] is collision-resistant
 *     (probabilistically) — distinct inputs give distinct
 *     hashes.
 *  3. SHA-256 produces a 64-character hex string (32 bytes
 *     = 64 hex chars), the canonical encoding the app uses
 *     for the on-disk recovery-phrase hash.
 *
 * **What we are NOT testing here.** The full
 * BIP39-12-word-generation contract (entropy, checksum,
 * wordlist membership) and the SQLCipher re-open round-trip.
 * The first requires loading the 2048-word BIP39 wordlist
 * from `assets/bip39-wordlist.txt` in a test, which
 * Robolectric's asset manager occasionally returns
 * FileNotFoundException for; the second requires libsqlcipher.
 * Both are listed in `docs/v1.9.11_release_notes.md` as
 * manual drive-verify items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecoveryPhraseContractTest {

    @Test
    fun `sha256 hex is stable for the same input`() {
        val phrase = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        val hash1 = IdentityCrypto.sha256Hex(phrase)
        val hash2 = IdentityCrypto.sha256Hex(phrase)
        assertEquals(
            "Hash of the same phrase must be stable",
            hash1,
            hash2,
        )
    }

    @Test
    fun `sha256 hex is 64 lowercase hex chars (the app's canonical encoding)`() {
        val hash = IdentityCrypto.sha256Hex("test input")
        assertEquals(
            "SHA-256 must encode to 32 bytes = 64 hex chars",
            64,
            hash.length,
        )
        assertTrue(
            "Hash must be lowercase hex, was: $hash",
            hash.all { it in '0'..'9' || it in 'a'..'f' },
        )
    }

    @Test
    fun `two distinct phrases have distinct hashes`() {
        val phrase1 = "abandon ability able about above absent absorb abstract absurd abuse access accident"
        val phrase2 = "abandon ability able about above absent absorb abstract absurd abuse access accuse"
        val hash1 = IdentityCrypto.sha256Hex(phrase1)
        val hash2 = IdentityCrypto.sha256Hex(phrase2)
        assertNotEquals(
            "Two distinct phrases must have distinct hashes (collision resistance)",
            hash1,
            hash2,
        )
    }
}
