package com.kaavalan.note.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tier 1.1 (v2.0): end-to-end test of the .baton-vault file
 * format. We build the 56-byte header, encrypt the
 * payload with AES-256-GCM under a known key (bypassing
 * Argon2id — the KDF is covered by the on-device
 * integration test in `app/src/androidTest/.../`), then
 * parse the file back and decrypt it. The test pins the
 * full file pipeline:
 *
 *   plaintext -> [KDF (skipped)] -> key + iv
 *            -> encrypt(key, iv, plaintext, aad=header)
 *            -> header || ciphertext || tag  (file)
 *            -> parseHeader(file)
 *            -> decrypt(key, iv, payload, aad=header)
 *            -> plaintext
 *
 * **Wrong-passphrase / wrong-key** is also covered here:
 * a one-bit flip in the tag fails the AEAD check, which
 * is what the [VaultError.IncorrectPassphrase] mapping in
 * [VaultImporter] relies on.
 *
 * The Argon2id KDF round-trip is covered by
 * `app/src/androidTest/.../VaultEndToEndTest.kt` (the
 * native lib is not on the JVM classpath).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultFileRoundTripTest {

    @Test
    fun `full file round-trip -- header + encrypt + parse + decrypt returns the original plaintext`() {
        val salt = ByteArray(VaultCrypto.SALT_BYTES) { (it * 7 + 3).toByte() }
        val iv = ByteArray(VaultCrypto.IV_BYTES) { (it * 11 + 5).toByte() }
        // A test-only "Argon2id output" (32 bytes). In
        // production this is the KDF output.
        val key = ByteArray(VaultCrypto.KEY_BYTES) { (it * 13 + 7).toByte() }
        val secretKey = SecretKeySpec(key, "AES")
        val plaintext = "Kaavalan note vault payload -- v2.0".toByteArray(Charsets.UTF_8)

        // Build the 56-byte header.
        val header = VaultFormat.buildHeader(
            salt = salt,
            iv = iv,
            kdfM = VaultCrypto.DEFAULT_M_KIB,
            kdfT = VaultCrypto.DEFAULT_T,
            kdfP = VaultCrypto.DEFAULT_P,
            payloadLen = plaintext.size + VaultCrypto.TAG_BYTES,
        )
        assertEquals(56, header.size)

        // Encrypt under AAD = header (the spec contract).
        val cipher = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, iv),
        )
        cipher.updateAAD(header)
        val payload = cipher.doFinal(plaintext) // ciphertext || tag
        assertEquals(plaintext.size + VaultCrypto.TAG_BYTES, payload.size)

        // The on-disk file is header || payload.
        val file = header + payload

        // Parse + decrypt.
        val parsed = VaultFormat.parseHeader(file)
        assertEquals(1, parsed.version)
        assertEquals(VaultCrypto.DEFAULT_M_KIB, parsed.kdfM)
        assertEquals(VaultCrypto.DEFAULT_T, parsed.kdfT)
        assertEquals(VaultCrypto.DEFAULT_P, parsed.kdfP)
        assertArrayEquals(salt, parsed.salt)
        assertArrayEquals(iv, parsed.iv)
        assertEquals(payload.size, parsed.payloadLen)

        val aad = file.copyOfRange(0, VaultCrypto.HEADER_BYTES)
        val payload2 = file.copyOfRange(
            VaultCrypto.HEADER_BYTES,
            VaultCrypto.HEADER_BYTES + parsed.payloadLen,
        )
        val cipher2 = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher2.init(
            javax.crypto.Cipher.DECRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, parsed.iv),
        )
        cipher2.updateAAD(aad)
        val decrypted = cipher2.doFinal(payload2)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `one-bit flip in the tag fails with AEADBadTagException (the oracle for IncorrectPassphrase)`() {
        val salt = ByteArray(VaultCrypto.SALT_BYTES)
        val iv = ByteArray(VaultCrypto.IV_BYTES)
        val key = ByteArray(VaultCrypto.KEY_BYTES)
        val secretKey = SecretKeySpec(key, "AES")
        val plaintext = "secret".toByteArray(Charsets.UTF_8)

        val header = VaultFormat.buildHeader(salt, iv, 19_456, 2, 1, plaintext.size + VaultCrypto.TAG_BYTES)
        val cipher = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, iv),
        )
        cipher.updateAAD(header)
        val payload = cipher.doFinal(plaintext)
        val file = header + payload

        // Flip the last bit of the tag.
        val tampered = file.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        val parsed = VaultFormat.parseHeader(tampered)
        val aad = tampered.copyOfRange(0, VaultCrypto.HEADER_BYTES)
        val payload2 = tampered.copyOfRange(
            VaultCrypto.HEADER_BYTES,
            VaultCrypto.HEADER_BYTES + parsed.payloadLen,
        )
        val cipher2 = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher2.init(
            javax.crypto.Cipher.DECRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, parsed.iv),
        )
        cipher2.updateAAD(aad)
        // The mapping in VaultImporter catches
        // AEADBadTagException and rethrows as
        // VaultError.IncorrectPassphrase -- we assert the
        // underlying AEAD failure here.
        assertThrows(AEADBadTagException::class.java) {
            cipher2.doFinal(payload2)
        }
    }

    @Test
    fun `one-bit flip in the header fails with AEADBadTagException (header is bound to AAD)`() {
        val salt = ByteArray(VaultCrypto.SALT_BYTES)
        val iv = ByteArray(VaultCrypto.IV_BYTES)
        val key = ByteArray(VaultCrypto.KEY_BYTES)
        val secretKey = SecretKeySpec(key, "AES")
        val plaintext = "secret".toByteArray(Charsets.UTF_8)

        val header = VaultFormat.buildHeader(salt, iv, 19_456, 2, 1, plaintext.size + VaultCrypto.TAG_BYTES)
        val cipher = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, iv),
        )
        cipher.updateAAD(header)
        val payload = cipher.doFinal(plaintext)
        val file = header + payload

        // Flip a single bit in the salt (offset 24-39).
        val tampered = file.copyOf()
        tampered[24] = (tampered[24].toInt() xor 0x01).toByte()

        val parsed = VaultFormat.parseHeader(tampered)
        val aad = tampered.copyOfRange(0, VaultCrypto.HEADER_BYTES)
        val payload2 = tampered.copyOfRange(
            VaultCrypto.HEADER_BYTES,
            VaultCrypto.HEADER_BYTES + parsed.payloadLen,
        )
        val cipher2 = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher2.init(
            javax.crypto.Cipher.DECRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, parsed.iv),
        )
        cipher2.updateAAD(aad)
        assertThrows(AEADBadTagException::class.java) {
            cipher2.doFinal(payload2)
        }
    }

    @Test
    fun `empty plaintext round-trips with the same key + AAD`() {
        val salt = ByteArray(VaultCrypto.SALT_BYTES)
        val iv = ByteArray(VaultCrypto.IV_BYTES)
        val key = ByteArray(VaultCrypto.KEY_BYTES)
        val secretKey = SecretKeySpec(key, "AES")
        val plaintext = ByteArray(0)

        val header = VaultFormat.buildHeader(salt, iv, 19_456, 2, 1, 0 + VaultCrypto.TAG_BYTES)
        val cipher = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, iv),
        )
        cipher.updateAAD(header)
        val payload = cipher.doFinal(plaintext)
        // 16 B tag, 0 B ciphertext.
        assertEquals(VaultCrypto.TAG_BYTES, payload.size)
        val file = header + payload
        val parsed = VaultFormat.parseHeader(file)
        assertEquals(parsed.payloadLen, payload.size)
        val aad = file.copyOfRange(0, VaultCrypto.HEADER_BYTES)
        val payload2 = file.copyOfRange(
            VaultCrypto.HEADER_BYTES,
            VaultCrypto.HEADER_BYTES + parsed.payloadLen,
        )
        val cipher2 = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher2.init(
            javax.crypto.Cipher.DECRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, parsed.iv),
        )
        cipher2.updateAAD(aad)
        val decrypted = cipher2.doFinal(payload2)
        assertEquals(0, decrypted.size)
    }

    @Test
    fun `large 1 MB plaintext round-trips (realistic DB size)`() {
        val salt = ByteArray(VaultCrypto.SALT_BYTES)
        val iv = ByteArray(VaultCrypto.IV_BYTES)
        val key = ByteArray(VaultCrypto.KEY_BYTES)
        val secretKey = SecretKeySpec(key, "AES")
        val plaintext = ByteArray(1024 * 1024) { (it * 37 % 251).toByte() }

        val header = VaultFormat.buildHeader(salt, iv, 19_456, 2, 1, plaintext.size + VaultCrypto.TAG_BYTES)
        val cipher = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, iv),
        )
        cipher.updateAAD(header)
        val payload = cipher.doFinal(plaintext)
        val file = header + payload
        assertEquals(56 + plaintext.size + VaultCrypto.TAG_BYTES, file.size)

        val parsed = VaultFormat.parseHeader(file)
        assertNotNull(parsed)
        val aad = file.copyOfRange(0, VaultCrypto.HEADER_BYTES)
        val payload2 = file.copyOfRange(
            VaultCrypto.HEADER_BYTES,
            VaultCrypto.HEADER_BYTES + parsed.payloadLen,
        )
        val cipher2 = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher2.init(
            javax.crypto.Cipher.DECRYPT_MODE, secretKey,
            GCMParameterSpec(VaultCrypto.TAG_BITS, parsed.iv),
        )
        cipher2.updateAAD(aad)
        val decrypted = cipher2.doFinal(payload2)
        assertArrayEquals(plaintext, decrypted)
    }
}
