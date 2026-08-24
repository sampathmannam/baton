package com.baton.app.data.backup

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.1.0 (PM rating): the AES-256-GCM encryption used
 * for the Google Drive backup blob.
 *
 * **Why encrypt.** The Drive backup lands in the user's
 * `appDataFolder` (a hidden per-app storage). Google
 * never sees the bytes — but the bytes are also
 * accessible to any process that has read access to
 * the user's Google account (e.g. a future Drive API
 * client with the right scope, or Google's own
 * services that surface the file to the user on
 * demand). Encrypting client-side is the right
 * defence: the threat model says "no third party ever
 * sees the bytes in clear", and that includes the
 * cloud storage.
 *
 * **Why AES-256-GCM.** Industry-standard AEAD. 256-bit
 * key, 96-bit nonce, 128-bit auth tag. The nonce is
 * generated per encryption via [SecureRandom].
 *
 * **Why PBKDF2 from the recovery phrase.** The user
 * already has a 12-word recovery phrase (set up in
 * the v2.0+ recovery flow). Using the phrase as the
 * key source means:
 *
 *  1. The user has one secret to remember.
 *  2. The key is portable — on a new device, the user
 *     enters the phrase and can decrypt any past
 *     backup.
 *  3. The key never leaves the device. It is not
 *     uploaded to Drive.
 *
 * PBKDF2-HMAC-SHA256 with 100k iterations + a
 * per-backup random salt gives ~100ms of derivation
 * time on a Pixel 6, which is the right trade-off
 * (faster would weaken the offline dictionary-attack
 * surface; slower would noticeably block the user).
 *
 * **The format.** The on-disk blob is:
 *
 * ```
 *   magic (4 bytes "BTV1")
 *   salt (16 bytes)
 *   nonce (12 bytes)
 *   ciphertext (variable, includes the GCM tag)
 * ```
 *
 * The magic is a version sentinel: a future v2.x can
 * change the format and the loader can branch on
 * "BTV1" vs "BTV2".
 */
@Singleton
class BackupCrypto @Inject constructor() {

    /**
     * Encrypt [plaintext] using a key derived from
     * [passphrase]. Returns the format-encoded blob
     * (magic + salt + nonce + ciphertext).
     */
    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val key: SecretKey = deriveKey(passphrase, salt)
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + salt + nonce + ciphertext
    }

    /**
     * Decrypt [blob] (in the same format as
     * [encrypt]) using a key derived from [passphrase].
     * Throws on:
     *  - wrong magic (not a Baton backup)
     *  - truncated blob
     *  - wrong passphrase (GCM tag mismatch)
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size >= HEADER_LEN) { "blob is too short to be a Baton backup" }
        // Magic check.
        for (i in MAGIC.indices) {
            require(blob[i] == MAGIC[i]) {
                "blob is not a Baton backup (magic mismatch at byte $i)"
            }
        }
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val nonce = blob.copyOfRange(
            MAGIC.size + SALT_LEN,
            MAGIC.size + SALT_LEN + NONCE_LEN,
        )
        val ciphertext = blob.copyOfRange(MAGIC.size + SALT_LEN + NONCE_LEN, blob.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        }
        return cipher.doFinal(ciphertext)
    }

    /**
     * PBKDF2-HMAC-SHA256 with 100k iterations + a 256-bit
     * derived key. The salt is per-encryption (random 16
     * bytes) and embedded in the blob.
     */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        // "BTV1" — Baton encrypted-backup Version 1.
        // The 'B' = 0x42, 'T' = 0x54, 'V' = 0x56, '1' = 0x31.
        private val MAGIC = byteArrayOf(0x42, 0x54, 0x56, 0x31)
        private const val SALT_LEN = 16
        private const val NONCE_LEN = 12
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
        private const val PBKDF2_ITERATIONS = 100_000
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val HEADER_LEN = MAGIC.size + SALT_LEN + NONCE_LEN

        /**
         * Encode a passphrase-derived key as a
         * Base64 string for storage in SharedPreferences.
         * The salt is a separate random value embedded
         * with the ciphertext; this helper just returns
         * the key bytes for callers that want to use the
         * native Android Keystore for the salt.
         */
        fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

        fun fromBase64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    }
}
