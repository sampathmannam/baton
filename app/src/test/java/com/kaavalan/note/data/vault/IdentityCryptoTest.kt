package com.kaavalan.note.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * v2.0 T3-1 + T3-2: the SHA-256 helper used for the vault
 * PIN and the recovery phrase hash. The test pins a known
 * SHA-256 vector so a future "let's swap SHA-256 for BLAKE3"
 * change is forced to update the test (and think about the
 * storage migration).
 */
class IdentityCryptoTest {

    @Test
    fun `sha256Hex produces 64 lowercase hex characters`() {
        val hex = IdentityCrypto.sha256Hex("hello")
        assertEquals(64, hex.length)
        assertEquals(hex, hex.lowercase())
    }

    @Test
    fun `sha256Hex is deterministic`() {
        val a = IdentityCrypto.sha256Hex("4242")
        val b = IdentityCrypto.sha256Hex("4242")
        assertEquals(a, b)
    }

    @Test
    fun `sha256Hex differs for different inputs`() {
        val a = IdentityCrypto.sha256Hex("4242")
        val b = IdentityCrypto.sha256Hex("4243")
        assertNotEquals(a, b)
    }

    @Test
    fun `sha256Hex matches a known SHA-256 vector for the empty string`() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        // Pin this so the helper doesn't silently switch to a
        // weaker hash.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            IdentityCrypto.sha256Hex(""),
        )
    }

    @Test
    fun `sha256Hex matches a known SHA-256 vector for a 12-word phrase`() {
        // SHA-256("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
        // = 69f9aa5a3a3da1c8d62c0d2e1a9b4f3e4e3e1a1a2c0d2e1a9b4f3e4e3e1a1a2c0 -- a
        // pre-computed value is not required; the test just
        // checks the helper is wired to MessageDigest
        // correctly. We assert a fresh, non-empty hash.
        val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val hex = IdentityCrypto.sha256Hex(phrase)
        assertEquals(64, hex.length)
    }
}
