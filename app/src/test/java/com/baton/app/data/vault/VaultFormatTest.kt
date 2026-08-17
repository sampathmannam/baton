package com.baton.app.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.1 (v2.0): the on-disk .baton-vault file format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultFormatTest {

    @Test
    fun `header is exactly 56 bytes`() {
        val header = VaultFormat.buildHeader(
            salt = ByteArray(16) { it.toByte() },
            iv = ByteArray(12) { it.toByte() },
            kdfM = 19_456,
            kdfT = 2,
            kdfP = 1,
            payloadLen = 4096,
        )
        assertEquals(56, header.size)
    }

    @Test
    fun `round-trip - write header, read header, all fields match`() {
        val salt = ByteArray(16) { (it + 100).toByte() }
        val iv = ByteArray(12) { (it + 50).toByte() }
        val header = VaultFormat.buildHeader(
            salt = salt,
            iv = iv,
            kdfM = 19_456,
            kdfT = 2,
            kdfP = 1,
            payloadLen = 4096,
        )
        val parsed = VaultFormat.parseHeader(header)
        assertEquals(1, parsed.version)
        assertEquals(1, parsed.kdfId)
        assertEquals(19_456, parsed.kdfM)
        assertEquals(2, parsed.kdfT)
        assertEquals(1, parsed.kdfP)
        assertArrayEquals(salt, parsed.salt)
        assertArrayEquals(iv, parsed.iv)
        assertEquals(4096, parsed.payloadLen)
    }

    @Test
    fun `bad magic -- NotAVault error`() {
        val header = VaultFormat.buildHeader(
            salt = ByteArray(16),
            iv = ByteArray(12),
            kdfM = 19_456, kdfT = 2, kdfP = 1,
            payloadLen = 100,
        )
        val bad = header.copyOf().also { it[0] = 'X'.code.toByte() }
        assertThrows(VaultError.NotAVault::class.java) {
            VaultFormat.parseHeader(bad)
        }
    }

    @Test
    fun `version greater than 1 -- UnsupportedVersion error`() {
        val header = VaultFormat.buildHeader(
            salt = ByteArray(16),
            iv = ByteArray(12),
            kdfM = 19_456, kdfT = 2, kdfP = 1,
            payloadLen = 100,
        )
        val future = header.copyOf().also { it[4] = 0x02 }
        assertThrows(VaultError.UnsupportedVersion::class.java) {
            VaultFormat.parseHeader(future)
        }
    }

    @Test
    fun `truncated file (less than 56 bytes) -- NotAVault error`() {
        val tooShort = ByteArray(20)
        assertThrows(VaultError.NotAVault::class.java) {
            VaultFormat.parseHeader(tooShort)
        }
    }

    @Test
    fun `reserved bytes are ignored on read`() {
        val header = VaultFormat.buildHeader(
            salt = ByteArray(16),
            iv = ByteArray(12),
            kdfM = 19_456, kdfT = 2, kdfP = 1,
            payloadLen = 100,
        )
        val mangled = header.copyOf().also { it[5] = 0xFF.toByte(); it[6] = 0xAA.toByte() }
        val parsed = VaultFormat.parseHeader(mangled)
        assertEquals(1, parsed.version)
        assertEquals(19_456, parsed.kdfM)
    }

    @Test
    fun `KDF id of 2 (PBKDF2) -- UnsupportedKdf error`() {
        val header = VaultFormat.buildHeader(
            salt = ByteArray(16),
            iv = ByteArray(12),
            kdfM = 19_456, kdfT = 2, kdfP = 1,
            payloadLen = 100,
        )
        // The kdf_id starts at offset 8. The buildHeader wrote
        // 1 (Argon2id). Patch it to 2 (PBKDF2 placeholder) to
        // assert the dispatcher.
        val bytes = header.copyOf()
        bytes[8] = 0x02; bytes[9] = 0; bytes[10] = 0; bytes[11] = 0
        assertThrows(VaultError.UnsupportedKdf::class.java) {
            VaultFormat.parseHeader(bytes)
        }
    }
}
