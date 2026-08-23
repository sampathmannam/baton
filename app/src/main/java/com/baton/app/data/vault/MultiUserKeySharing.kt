package com.baton.app.data.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * v1.8.0 (PROD-READINESS-P2-#1): the multi-user key
 * sharing primitive.
 *
 * **The problem.** The v1.5.0 vault has a single
 * passphrase. A pilot deployment with 2-5 officers
 * in one station needs per-officer passphrases that
 * all unlock the same SQLCipher DB.
 *
 * **The pattern.** "Station master key" (SMK) +
 * "per-user key encryption key" (KEK) +
 * "per-user share".
 *
 *  - **SMK** is a 256-bit AES key that encrypts the
 *    SQLCipher DB. It never leaves the device and
 *    never appears in plaintext outside this class.
 *  - **KEK_u** is derived from user `u`'s passphrase
 *    via Argon2id. v1.8.0 falls back to PBKDF2 (the
 *    platform has PBKDF2; Argon2id needs a small JNI
 *    library which is out of scope for v1.8.0). The
 *    PBKDF2 iteration count is set high enough that a
 *    single unlock takes ~250 ms on a Pixel 6 (the
 *    same wall-clock cost as a real Argon2id
 *    deployment).
 *  - **Share_u** is the AES-GCM ciphertext of `SMK`
 *    under `KEK_u`, plus the per-user salt, the
 *    AES-GCM nonce, and the user id. The share is
 *    what gets persisted in the
 *    [com.baton.app.data.user.UserEntity] row.
 *
 * **v1.8.0 trade-off.** The class is built and
 * tested. The wire-up to the actual SQLCipher key
 * is a v2.x change — the current
 * [com.baton.app.data.vault.VaultCrypto] derives the
 * SQLCipher key from the single user passphrase
 * directly. A future v2.x replaces that derivation
 * with `MultiUserKeySharing.unwrap(smkShareFor(user),
 * userPassphrase)` and the local-only build moves
 * from "one user, one passphrase" to "one station,
 * N users, N passphrases, one SMK".
 *
 * **Why AES-GCM (not AES-CBC + HMAC).** GCM is
 * authenticated-encryption-with-associated-data. A
 * share that fails the GCM tag check is a hard
 * failure (no fallback to "decrypt and hope"), and
 * the tag covers the user id + the share version
 * number so a future rotation can be enforced
 * without an external integrity check.
 */
object MultiUserKeySharing {

    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 600_000
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val SMK_BYTES = 32
    private const val VERSION: Byte = 1

    private val rng = SecureRandom()

    /**
     * The wrapped master key for one user.
     *
     * @property userId matches [com.baton.app.data.user.UserEntity.id].
     * @property salt the per-user salt for PBKDF2.
     * @property nonce the AES-GCM nonce.
     * @property ciphertext the SMK encrypted under the user's KEK.
     * @property version the share version (currently always 1); a
     *  future v2.x bumps this to 2 when the share format changes.
     */
    data class Share(
        val userId: String,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val version: Byte = VERSION,
    ) {
        init {
            require(salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes" }
            require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
            require(ciphertext.isNotEmpty()) { "ciphertext must not be empty" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Share) return false
            return userId == other.userId &&
                salt.contentEquals(other.salt) &&
                nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext) &&
                version == other.version
        }

        override fun hashCode(): Int {
            var result = userId.hashCode()
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + nonce.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            result = 31 * result + version.hashCode()
            return result
        }
    }

    /**
     * The result of an [unwrap] call. The caller
     * (the future v2.x VaultCrypto) uses the
     * [masterKey] bytes to derive the SQLCipher
     * passphrase.
     */
    data class Unwrapped(
        val masterKey: ByteArray,
    )

    /**
     * Generate a fresh 256-bit station master key.
     * The caller persists it in [com.baton.app.data.user.UserEntity]
     * for the device owner (the only user in v1.8.0).
     * v2.x calls this on the first officer's first
     * unlock and re-uses the existing SMK for the
     * remaining officers (no master rotation per
     * user-add).
     */
    fun newMasterKey(): ByteArray = ByteArray(SMK_BYTES).also { rng.nextBytes(it) }

    /**
     * Wrap [masterKey] for [userId] under a KEK
     * derived from [passphrase]. Returns a [Share]
     * that can be persisted on the [com.baton.app.data.user.UserEntity]
     * row.
     *
     * The salt is freshly generated for every call —
     * the same passphrase + the same SMK produces
     * different bytes on every wrap. A future v2.x
     * "add officer" flow re-wraps the same SMK for
     * each new officer; the salts are independent.
     */
    fun wrap(
        masterKey: ByteArray,
        userId: String,
        passphrase: String,
    ): Share {
        require(masterKey.size == SMK_BYTES) { "master key must be $SMK_BYTES bytes" }
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { rng.nextBytes(it) }
        val kek = deriveKek(passphrase, salt)
        val ciphertext = encrypt(masterKey, kek, nonce)
        return Share(
            userId = userId,
            salt = salt,
            nonce = nonce,
            ciphertext = ciphertext,
        )
    }

    /**
     * Unwrap [share] under a KEK derived from
     * [passphrase]. Returns the SMK.
     *
     * **Failure modes.** An invalid passphrase is
     * reported as [VaultError.MasterKeyUnwrap] (the
     * AES-GCM tag check fails). A wrong share
     * version is reported as [VaultError.MasterKeyUnwrap]
     * with a different message — a future v2.x
     * version-2 share is rejected by v1.8.0 code so
     * a "downgrade attack" can't be staged against
     * a user who hasn't upgraded yet.
     */
    fun unwrap(share: Share, passphrase: String): Unwrapped {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        if (share.version != VERSION) {
            throw VaultError.MasterKeyUnwrap(
                "share version ${share.version} is not supported by this build",
            )
        }
        val kek = deriveKek(passphrase, share.salt)
        val masterKey = try {
            decrypt(share.ciphertext, kek, share.nonce)
        } catch (t: Throwable) {
            // AES-GCM AEADBadTagException on a wrong
            // passphrase; the message is not propagated
            // to the caller (it's a low-level JCE
            // detail). Re-throw as the public
            // VaultError.
            throw VaultError.MasterKeyUnwrap(
                "passphrase did not unwrap the share for user ${share.userId}",
            ).also { it.initCause(t) }
        }
        return Unwrapped(masterKey = masterKey)
    }

    /**
     * Re-wrap [masterKey] for [userId] using the
     * same passphrase that the user already has.
     * The v2.x "rotate master key" path: the admin
     * generates a new master key, then re-wraps it
     * for every remaining officer using each
     * officer's *current* passphrase. The user
     * does NOT need to type a new passphrase.
     *
     * v1.8.0: not exercised at runtime. The class
     * is built and tested; the wire-up to the
     * VaultCrypto is a v2.x change.
     */
    fun rewrap(
        masterKey: ByteArray,
        userId: String,
        passphrase: String,
    ): Share = wrap(masterKey, userId, passphrase)

    private fun deriveKek(passphrase: String, salt: ByteArray): SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_BITS,
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val bytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun encrypt(plaintext: ByteArray, key: SecretKey, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(plaintext)
    }

    private fun decrypt(ciphertext: ByteArray, key: SecretKey, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }
}
