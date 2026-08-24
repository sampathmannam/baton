package com.kaavalan.note.data.vault

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tier 1.1 (v2.0): the on-disk .baton-vault file format.
 *
 * **Header (56 bytes, all integers LE):**
 * ```
 *   [ 0.. 3]  magic           = "BATO"  (0x42 0x41 0x54 0x4F)
 *   [ 4]      version         = 0x01
 *   [ 5.. 7]  reserved        = 0x00 0x00 0x00
 *   [ 8..11]  kdf_id          = 0x01  (1 = Argon2id)
 *   [12..15]  kdf_m           = LE u32 (KiB)        (19456)
 *   [16..19]  kdf_t           = LE u32 (iterations) (2)
 *   [20..23]  kdf_p           = LE u32 (parallelism)(1)
 *   [24..39]  kdf_salt        = 16 B random
 *   [40..51]  iv              = 12 B random
 *   [52..55]  payload_len     = LE u32 (size of [ciphertext||tag])
 * ```
 * After the header: [ciphertext || 16-byte GCM tag].
 *
 * The AAD bound to the GCM tag is exactly the 56-byte header,
 * so a single bit flip in any header field fails the tag check
 * (=> the importer returns `IncorrectPassphrase`).
 *
 * Total file = 56 + payload_len bytes.
 */
object VaultFormat {

    const val MAGIC = "BATO"
    const val VERSION = 0x01
    const val KDF_ID_ARGON2ID = 0x01

    /**
     * Build the 56-byte fixed header. The result is bound to the
     * AEAD as the AAD on encrypt and re-bound on decrypt.
     */
    fun buildHeader(
        salt: ByteArray,
        iv: ByteArray,
        kdfM: Int,
        kdfT: Int,
        kdfP: Int,
        payloadLen: Int,
    ): ByteArray {
        require(salt.size == VaultCrypto.SALT_BYTES) {
            "Salt must be ${VaultCrypto.SALT_BYTES} bytes (got ${salt.size})"
        }
        require(iv.size == VaultCrypto.IV_BYTES) {
            "IV must be ${VaultCrypto.IV_BYTES} bytes (got ${iv.size})"
        }
        require(payloadLen >= 0) { "payload_len must be non-negative" }
        val buf = ByteBuffer.allocate(VaultCrypto.HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buf.put(VERSION.toByte())
        buf.put(0); buf.put(0); buf.put(0)   // reserved
        buf.putInt(KDF_ID_ARGON2ID)
        buf.putInt(kdfM)
        buf.putInt(kdfT)
        buf.putInt(kdfP)
        buf.put(salt)
        buf.put(iv)
        buf.putInt(payloadLen)
        return buf.array()
    }

    /**
     * Parse the 56-byte header. Throws [VaultError.NotAVault] for
     * truncated input or bad magic, [VaultError.UnsupportedVersion]
     * for a future version byte, [VaultError.UnsupportedKdf] for
     * a KDF id we don't know.
     */
    fun parseHeader(fileBytes: ByteArray): ParsedHeader {
        require(fileBytes.size >= VaultCrypto.HEADER_BYTES) {
            throw VaultError.NotAVault("file < ${VaultCrypto.HEADER_BYTES} bytes")
        }
        val buf = ByteBuffer.wrap(fileBytes, 0, VaultCrypto.HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
            throw VaultError.NotAVault("bad magic")
        }
        val version = buf.get().toInt() and 0xFF
        if (version != VERSION) {
            throw VaultError.UnsupportedVersion(version)
        }
        // Skip 3 reserved bytes.
        buf.get(); buf.get(); buf.get()
        val kdfId = buf.int
        if (kdfId != KDF_ID_ARGON2ID) {
            throw VaultError.UnsupportedKdf(kdfId)
        }
        val kdfM = buf.int
        val kdfT = buf.int
        val kdfP = buf.int
        val salt = ByteArray(VaultCrypto.SALT_BYTES).also { buf.get(it) }
        val iv = ByteArray(VaultCrypto.IV_BYTES).also { buf.get(it) }
        val payloadLen = buf.int
        return ParsedHeader(
            version = version,
            kdfId = kdfId,
            kdfM = kdfM,
            kdfT = kdfT,
            kdfP = kdfP,
            salt = salt,
            iv = iv,
            payloadLen = payloadLen,
        )
    }

    data class ParsedHeader(
        val version: Int,
        val kdfId: Int,
        val kdfM: Int,
        val kdfT: Int,
        val kdfP: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val payloadLen: Int,
    ) {
        override fun equals(other: Any?): Boolean = other is ParsedHeader &&
            version == other.version &&
            kdfId == other.kdfId &&
            kdfM == other.kdfM &&
            kdfT == other.kdfT &&
            kdfP == other.kdfP &&
            salt.contentEquals(other.salt) &&
            iv.contentEquals(other.iv) &&
            payloadLen == other.payloadLen

        override fun hashCode(): Int = version * 31 + kdfM
    }
}
