package com.kaavalan.note.data.vault

import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.1 (v2.0): the .baton-vault crypto primitive.
 *
 * **Argon2id KDF** is exercised in the on-device integration
 * test (`app/src/androidTest/.../VaultEndToEndTest.kt`).
 * The native lib (`argon2jni`) is not on the JVM
 * classpath, so the unit tests cover the AES-256-GCM
 * primitive + the OWASP parameter constants. The KDF +
 * the round-trip in `VaultExporter` / `VaultImporter` are
 * covered by the on-device test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultCryptoTest {

    // The cipher functions are reachable without the KDF
    // (the VaultCrypto constructor touches Argon2Kt, so we
    // wrap each test in a class that doesn't need the KDF
    // for the assertion). We exercise the encrypt/decrypt
    // path with a known key + IV.
    private val crypto = VaultCipherTestHelper()

    @Test
    fun `encrypt then decrypt round-trips on a known plaintext`() {
        val key = crypto.generateKeyForTest()
        val secretKey = SecretKeySpec(key, "AES")
        val iv = crypto.generateIv()
        val plaintext = "Kaavalan note -- the baton's quiet half.".toByteArray(Charsets.UTF_8)
        val aad = "header-bytes-as-aad".toByteArray(Charsets.UTF_8)
        val ciphertext = crypto.encrypt(secretKey, iv, plaintext, aad)
        val decrypted = crypto.decrypt(secretKey, iv, ciphertext, aad)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `AES-256-GCM ciphertext changes when the IV changes (sanity)`() {
        val key = crypto.generateKeyForTest()
        val secretKey = SecretKeySpec(key, "AES")
        val plaintext = "hello".toByteArray()
        val aad = "hdr".toByteArray()
        val iv1 = crypto.generateIv()
        val iv2 = crypto.generateIv()
        val c1 = crypto.encrypt(secretKey, iv1, plaintext, aad)
        val c2 = crypto.encrypt(secretKey, iv2, plaintext, aad)
        assertNotEquals(c1.toList(), c2.toList())
    }

    @Test
    fun `wrong key produces AEADBadTagException`() {
        val k1 = crypto.generateKeyForTest()
        val k2 = crypto.generateKeyForTest()
        val iv = crypto.generateIv()
        val plaintext = "vault-payload".toByteArray()
        val aad = "header".toByteArray()
        val ct = crypto.encrypt(SecretKeySpec(k1, "AES"), iv, plaintext, aad)
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            crypto.decrypt(SecretKeySpec(k2, "AES"), iv, ct, aad)
        }
    }

    @Test
    fun `empty plaintext is supported (zero-length payload)`() {
        val key = crypto.generateKeyForTest()
        val iv = crypto.generateIv()
        val ct = crypto.encrypt(SecretKeySpec(key, "AES"), iv, ByteArray(0), null)
        // GCM tag is 16 B appended to ciphertext.
        assertEquals(16, ct.size)
        val back = crypto.decrypt(SecretKeySpec(key, "AES"), iv, ct, null)
        assertEquals(0, back.size)
    }

    @Test
    fun `plaintext of size 1 MiB round-trips`() {
        val key = crypto.generateKeyForTest()
        val iv = crypto.generateIv()
        val plaintext = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val ct = crypto.encrypt(SecretKeySpec(key, "AES"), iv, plaintext, null)
        assertEquals(plaintext.size + 16, ct.size)
        val back = crypto.decrypt(SecretKeySpec(key, "AES"), iv, ct, null)
        assertArrayEquals(plaintext, back)
    }

    @Test
    fun `AAD is bound -- changing the AAD on decrypt fails the tag`() {
        val key = crypto.generateKeyForTest()
        val iv = crypto.generateIv()
        val plaintext = "secret".toByteArray()
        val aad = "header-v1".toByteArray()
        val ct = crypto.encrypt(SecretKeySpec(key, "AES"), iv, plaintext, aad)
        assertThrows(javax.crypto.AEADBadTagException::class.java) {
            crypto.decrypt(
                SecretKeySpec(key, "AES"),
                iv,
                ct,
                "header-v2".toByteArray(),
            )
        }
    }

    @Test
    fun `GCMParameterSpec uses 128-bit tag (16 bytes appended)`() {
        val key = crypto.generateKeyForTest()
        val iv = crypto.generateIv()
        val ct = crypto.encrypt(SecretKeySpec(key, "AES"), iv, "x".toByteArray(), null)
        assertTrue("GCM tag must be 16 bytes", ct.size >= 16)
        assertEquals("ciphertext (1 byte) + tag (16 bytes) = 17", 17, ct.size)
    }

    @Test
    fun `constants match the design doc (m=19,456 KiB, t=2, p=1)`() {
        assertEquals(19_456, VaultCrypto.DEFAULT_M_KIB)
        assertEquals(2, VaultCrypto.DEFAULT_T)
        assertEquals(1, VaultCrypto.DEFAULT_P)
        assertEquals(16, VaultCrypto.SALT_BYTES)
        assertEquals(12, VaultCrypto.IV_BYTES)
        assertEquals(16, VaultCrypto.TAG_BYTES)
        assertEquals(56, VaultCrypto.HEADER_BYTES)
    }
}

/**
 * A test-only helper that mirrors the AES-256-GCM surface
 * of [VaultCrypto] without touching the Argon2id KDF
 * (which needs the JNI native lib). Lets the unit tests
 * exercise the cipher primitive on the JVM.
 */
private class VaultCipherTestHelper {
    fun generateKeyForTest(): ByteArray {
        val kg = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }
        return kg.generateKey().encoded
    }
    fun generateIv(): ByteArray = ByteArray(VaultCrypto.IV_BYTES).also {
        java.security.SecureRandom().nextBytes(it)
    }
    fun toSecretKey(rawKey: ByteArray): javax.crypto.SecretKey {
        require(rawKey.size == VaultCrypto.KEY_BYTES) { "AES-256 requires 32 bytes" }
        return SecretKeySpec(rawKey, "AES")
    }
    fun encrypt(
        key: javax.crypto.SecretKey,
        iv: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray?,
    ): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, GCMParameterSpec(VaultCrypto.TAG_BITS, iv))
        if (aad != null && aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }
    fun decrypt(
        key: javax.crypto.SecretKey,
        iv: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray?,
    ): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance(VaultCrypto.TRANSFORMATION)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, GCMParameterSpec(VaultCrypto.TAG_BITS, iv))
        if (aad != null && aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextWithTag)
    }
}
