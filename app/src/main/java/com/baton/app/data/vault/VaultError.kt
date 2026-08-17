package com.baton.app.data.vault

/**
 * Tier 1.1 (v2.0): clean error surface for the .baton-vault file
 * format. Sealed class so the Compose dialog can `when`-match and
 * surface the right string. The importer does NOT distinguish
 * "wrong passphrase" from "tampered header" / "tampered payload"
 * to avoid giving an attacker an oracle — both surfaces
 * `IncorrectPassphrase`.
 */
sealed class VaultError(message: String) : Exception(message) {

    /** The file is not a Baton vault (bad magic, truncated, etc.). */
    class NotAVault(reason: String) : VaultError(reason)

    /** The file's version byte is higher than this app supports. */
    class UnsupportedVersion(version: Int) :
        VaultError("Unsupported vault version: $version")

    /** The file's kdf_id is not implemented in this app. */
    class UnsupportedKdf(kdfId: Int) :
        VaultError("Unsupported KDF: $kdfId")

    /**
     * The passphrase is wrong, OR the file is corrupt, OR the file
     * was tampered with. We do NOT distinguish these three to
     * avoid giving an attacker an oracle. Maps to the user string
     * "Incorrect passphrase".
     */
    class IncorrectPassphrase : VaultError("Incorrect passphrase")

    /** I/O error reading or writing. */
    class IoError(reason: String) : VaultError(reason)

    /** Disk full writing the imported DB. */
    class DiskFull : VaultError("Not enough space to save the vault")
}
