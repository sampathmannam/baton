package com.baton.app.data.vault

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1.1 (v2.0): AES-256-GCM primitive + Argon2id KDF for the
 * .baton-vault file format.
 *
 * **Design.** m=19,456 KiB, t=2, p=1 (OWASP minimum for mobile,
 * 2026 cheat sheet). Salt 16 B random. IV 12 B random (NIST SP
 * 800-38D). Tag 16 B. AAD = 56-byte fixed header.
 *
 * **Library.** `com.lambdapioneer.argon2kt:argon2kt:1.6.0` for
 * the KDF; `javax.crypto.Cipher` (no extra deps) for the cipher.
 * Both are kept as separate helpers so the cipher can be
 * unit-tested with a known KDF output and the KDF can be
 * benchmarked independently.
 *
 * The exporter and importer use the constants here — never
 * hard-code parameter values at the call site. This is the
 * single source of truth.
 */
@Singleton
class VaultCrypto @Inject constructor() {

    private val argon2 = Argon2Kt()

    private val rng = SecureRandom()

    /**
     * Derives a 32-byte AES-256 key from a passphrase + salt
     * using Argon2id. The default parameters are baked into
     * the header of every export (`m`/`t`/`p` in the 56-byte
     * file header) so the importer always uses the right
     * values — there is no library default to drift.
     */
    fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        m: Int = DEFAULT_M_KIB,
        t: Int = DEFAULT_T,
        p: Int = DEFAULT_P,
        outLen: Int = KEY_BYTES,
    ): ByteArray {
        require(salt.size == SALT_BYTES) { "Argon2id salt must be exactly $SALT_BYTES bytes" }
        val passBytes = String(passphrase).toByteArray(Charsets.UTF_8)
        try {
            return argon2.hash(
                Argon2Mode.ARGON2_ID,
                passBytes,
                salt,
                m,
                t,
                p,
                outLen,
                Argon2Version.V13,
            ).rawHashAsByteArray()
        } finally {
            passBytes.fill(0) // wipe after use
        }
    }

    /** Wraps a raw 32-byte key as a JCE [SecretKey]. */
    fun toSecretKey(rawKey: ByteArray): SecretKey {
        require(rawKey.size == KEY_BYTES) { "AES-256 requires exactly $KEY_BYTES bytes (got ${rawKey.size})" }
        return SecretKeySpec(rawKey, "AES")
    }

    /** 12-byte GCM nonce (NIST SP 800-38D §5.2.1.1). */
    fun generateIv(): ByteArray = ByteArray(IV_BYTES).also { rng.nextBytes(it) }

    /** 16-byte Argon2id salt. */
    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }

    /**
     * Encrypts [plaintext] under [key] with the given [iv]. The
     * returned bytes are `ciphertext || tag` concatenated (the
     * tag is the trailing 16 bytes, GCM convention). [aad] is
     * bound into the AEAD; the importer MUST pass the same
     * value on decrypt.
     */
    fun encrypt(key: SecretKey, iv: ByteArray, plaintext: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        if (aad != null && aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts [ciphertextWithTag] under [key] with [iv] and
     * the same [aad] used at encryption. Throws
     * [javax.crypto.AEADBadTagException] (subclass of
     * [javax.crypto.BadPaddingException]) on tag mismatch — the
     * caller MUST catch this and surface "Incorrect passphrase",
     * NOT the raw exception text.
     */
    fun decrypt(
        key: SecretKey,
        iv: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray?,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        if (aad != null && aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextWithTag)
    }

    /**
     * Generates a fresh random 32-byte AES key. For unit tests
     * only — the production key material is the Argon2id
     * output, never random.
     */
    @Suppress("unused")
    fun generateKeyForTest(): ByteArray {
        val kg = KeyGenerator.getInstance("AES").apply { init(KEY_BITS) }
        return kg.generateKey().encoded
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = 16
        const val SALT_BYTES = 16
        const val HEADER_BYTES = 56

        // OWASP minimum mobile profile (Password Storage Cheat Sheet,
        // 2026): m=19,456 KiB, t=2, p=1. These are also baked into
        // every export header so the importer is parameter-driven.
        const val DEFAULT_M_KIB = 19_456
        const val DEFAULT_T = 2
        const val DEFAULT_P = 1
    }
}
