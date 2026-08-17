package com.baton.app.data.vault

import java.security.MessageDigest

/**
 * v2.0 T3-1 + T3-2: a small set of pure helpers for the
 * identity / vault flows that need a one-way hash of a user-
 * supplied secret (the vault PIN, the recovery phrase). We do
 * NOT use these helpers for any cryptographic key derivation
 * (that's [deriveFeatureKey] below, which uses HMAC-SHA256).
 *
 * **Why SHA-256 (not Argon2id) for these checks?** The hash
 * is stored in EncryptedSharedPreferences (Keystore-backed
 * AES-256-GCM), so a forensic adversary would have to defeat
 * the Keystore to read the hash. Argon2id's purpose is to
 * rate-limit an offline attacker who has the hash file; here
 * the attacker is online (the device) and rate-limited by
 * the OS lock + the per-second EncryptedSharedPreferences
 * read cost. Argon2id would just add latency to a legitimate
 * user without buying meaningful additional security.
 */
object IdentityCrypto {

    /**
     * Compute the SHA-256 of [input] (UTF-8 encoded) and return
     * the result as a lowercase hex string of 64 characters.
     * The two callers — the vault PIN and the recovery phrase
     * — store the result via
     * [com.baton.app.data.auth.SecurePreferences.setVaultPinHash]
     * and ...setRecoveryPhraseHash respectively.
     */
    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}
