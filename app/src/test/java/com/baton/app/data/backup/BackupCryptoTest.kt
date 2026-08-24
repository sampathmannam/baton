package com.baton.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.1.0 (PM rating): the [BackupCrypto] round-trip +
 * failure-mode tests. The crypto layer is the heart of
 * the Drive backup security model; the tests pin the
 * format, the wrong-passphrase failure, and the
 * salt/nonce uniqueness that makes every backup a
 * distinct ciphertext.
 */
class BackupCryptoTest {

    private val crypto = BackupCrypto()

    @Test
    fun `encrypt then decrypt returns the original plaintext`() {
        val plaintext = "the quick brown fox jumps over the lazy dog".toByteArray()
        val passphrase = "twelve words make a strong key".toCharArray()
        val blob = crypto.encrypt(plaintext, passphrase)
        val recovered = crypto.decrypt(blob, passphrase)
        assertArrayEquals(plaintext, recovered)
    }

    @Test
    fun `decrypt with the wrong passphrase throws (GCM tag mismatch)`() {
        val plaintext = "secret data".toByteArray()
        val right = "correct passphrase for testing".toCharArray()
        val wrong = "wrong passphrase for testing".toCharArray()
        val blob = crypto.encrypt(plaintext, right)
        assertThrows(Throwable::class.java) {
            crypto.decrypt(blob, wrong)
        }
    }

    @Test
    fun `encrypted blob starts with the BTV1 magic header`() {
        val blob = crypto.encrypt("hello".toByteArray(), "passphrase".toCharArray())
        // The magic is "BTV1" = 0x42 0x54 0x56 0x31.
        assertEquals(0x42, blob[0].toInt() and 0xFF)
        assertEquals(0x54, blob[1].toInt() and 0xFF)
        assertEquals(0x56, blob[2].toInt() and 0xFF)
        assertEquals(0x31, blob[3].toInt() and 0xFF)
    }

    @Test
    fun `encrypted blobs for the same plaintext are different (random salt and nonce)`() {
        // Two encryptions of the same plaintext with the
        // same passphrase must produce different blobs.
        // The salt + nonce are both random per encryption;
        // a deterministic encryption would be a
        // catastrophic flaw (it would leak which
        // backups share plaintext).
        val plaintext = "the same text".toByteArray()
        val passphrase = "a fixed passphrase".toCharArray()
        val a = crypto.encrypt(plaintext, passphrase)
        val b = crypto.encrypt(plaintext, passphrase)
        // The first 4 bytes are the fixed "BTV1" magic
        // header. Bytes 4-19 are the random salt; bytes
        // 20-31 are the random nonce. Bytes 32+ are the
        // ciphertext (with a 16-byte GCM tag at the end).
        // Two random encryptions of the same plaintext
        // differ in the salt + nonce; the ciphertext
        // also differs because the nonce differs.
        val headerLen = 4 // BTV1
        val saltLen = 16
        val nonceLen = 12
        val variableLen = saltLen + nonceLen
        for (i in headerLen until (headerLen + variableLen)) {
            assertNotEquals("byte $i (salt or nonce) should differ across encryptions", a[i], b[i])
        }
        // The blobs as a whole must differ.
        assertNotEquals(
            "two encryptions of the same plaintext with the same passphrase must produce different blobs",
            a.toList(),
            b.toList(),
        )
    }

    @Test
    fun `encrypted blob is larger than the plaintext (header + GCM tag)`() {
        // The on-wire format is: magic(4) + salt(16) +
        // nonce(12) + ciphertext(plaintext.size + 16).
        // 4 + 16 + 12 + 16 = 48 bytes of overhead, plus the
        // plaintext size.
        val plaintext = "x".repeat(100).toByteArray()
        val blob = crypto.encrypt(plaintext, "pass".toCharArray())
        assertEquals(
            "encrypted blob should be plaintext.size + 48 bytes (header + GCM tag)",
            plaintext.size + 48,
            blob.size,
        )
    }

    @Test
    fun `decrypt of a truncated blob throws`() {
        val blob = crypto.encrypt("hello".toByteArray(), "pass".toCharArray())
        // Strip everything past the magic header.
        val truncated = blob.copyOfRange(0, 4)
        assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(truncated, "pass".toCharArray())
        }
    }

    @Test
    fun `decrypt of a blob with the wrong magic throws`() {
        val good = crypto.encrypt("hello".toByteArray(), "pass".toCharArray())
        // Flip the first magic byte to 0xFF (still a
        // valid byte, but not 'B').
        val bad = good.copyOf()
        bad[0] = 0xFF.toByte()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            crypto.decrypt(bad, "pass".toCharArray())
        }
        assertTrue(
            "the exception should mention the magic mismatch",
            (ex.message ?: "").contains("magic"),
        )
    }

    @Test
    fun `empty passphrase is rejected at the encrypt boundary`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            crypto.encrypt("hello".toByteArray(), "".toCharArray())
        }
        assertTrue(
            "the exception should mention the empty passphrase",
            (ex.message ?: "").contains("passphrase"),
        )
    }
}
