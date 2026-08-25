package com.kaavalan.note.data.vault

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * v2.0 T3-2 (recovery phrase): BIP39 12-word recovery phrase
 * generator + validator. v2 identity feature — no email, no
 * account, no server. The phrase IS the master secret; per-
 * feature keys are derived from it via [deriveFeatureKey].
 *
 * Reference: github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
 *
 * The implementation is ~80 lines of Kotlin by design — no
 * `bitcoinj` or `web3j` dependency, no transitive crypto deps.
 * The word list is loaded from
 * `app/src/main/assets/bip39-wordlist.txt` (2048 English words
 * from the canonical BIP39 list, MIT-licensed).
 *
 * **Algorithm (128-bit entropy = 12 words):**
 *  1. Draw 16 random bytes from [SecureRandom].
 *  2. SHA-256 the bytes; take the first 4 bits of the hash as
 *     a checksum. Append to entropy -> 132 bits.
 *  3. Split into 12 groups of 11 bits. Each 11-bit value
 *     (0..2047) is an index into the 2048-word list.
 *
 * **Validation** re-derives the entropy + checksum from the
 * 12 words and checks the checksum against SHA-256(entropy).
 * A 12-word phrase with a single bit flip fails validation.
 *
 * The phrase is **never** persisted. Only the SHA-256 of the
 * space-joined phrase is stored in
 * [com.kaavalan.note.data.auth.SecurePreferences.setRecoveryPhraseHash].
 */
class MnemonicGenerator(
    private val wordList: List<String>,
    private val rng: SecureRandom = SecureRandom(),
) {

    init {
        require(wordList.size == BIP39_WORDLIST_SIZE) {
            "BIP39 word list must have $BIP39_WORDLIST_SIZE words (got ${wordList.size})"
        }
    }

    /**
     * Generate a 12-word recovery phrase from 128 bits of fresh
     * [SecureRandom] entropy. The returned list is 12 distinct
     * words drawn from the wordlist.
     */
    fun generate12(): List<String> {
        val entropy = ByteArray(ENTROPY_BYTES_128) // 16 bytes = 128 bits
        rng.nextBytes(entropy)
        return encode(entropy)
    }

    /**
     * Encode an arbitrary 16/20/24/28/32-byte entropy buffer to
     * the corresponding 12/15/18/21/24-word phrase. v2.0 only
     * calls this with 16 bytes (12 words); the other sizes are
     * supported for forward-compat (e.g. a future "18-word"
     * stronger mode).
     */
    fun encode(entropy: ByteArray): List<String> {
        require(entropy.size in setOf(16, 20, 24, 28, 32)) {
            "Invalid entropy size: ${entropy.size} (expected 16, 20, 24, 28, or 32)"
        }
        // The BIP39 checksum is the first (CS = ENT / 32) bits
        // of SHA-256(entropy). 128-bit entropy -> 4-bit checksum.
        val checksumBits = entropy.size / 4
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val bits = (entropy.toBitString() + hash.toBitString().take(checksumBits))
        return bits.chunked(BITS_PER_WORD).map { wordList[it.toInt(2)] }
    }

    /**
     * Validate a candidate phrase. Returns `true` iff the
     * phrase has a legal length, every word is in the word
     * list, and the BIP39 checksum matches. A 12-word phrase
     * where any single bit has been flipped returns `false`.
     */
    fun validate(phrase: List<String>): Boolean {
        if (phrase.size !in VALID_PHRASE_LENGTHS) return false
        // Reject phrases with any word not in the wordlist.
        val indices = IntArray(phrase.size) { i ->
            val idx = wordList.indexOf(phrase[i])
            if (idx < 0) return false
            idx
        }
        // Reconstruct the bit stream: 11 bits per word.
        val bits = buildString(phrase.size * BITS_PER_WORD) {
            for (idx in indices) {
                append(idx.toString(2).padStart(BITS_PER_WORD, '0'))
            }
        }
        // Split into entropy + checksum. For a 12-word phrase
        // the bit stream is 132 bits = 128 entropy + 4 checksum.
        val totalBits = bits.length
        val checksumBitLen = totalBits / 33
        val entropyBitLen = totalBits - checksumBitLen
        val entropyBits = bits.substring(0, entropyBitLen)
        val checksumBits = bits.substring(entropyBitLen)
        // Re-derive the entropy bytes and re-hash; the first
        // `checksumBitLen` bits of the hash must match.
        val entropy = entropyBits.chunked(8).map { it.toInt(2).toByte() }.toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(entropy)
            .toBitString().take(checksumBitLen)
        return checksumBits == expected
    }

    companion object {
        const val BIP39_WORDLIST_SIZE = 2048
        const val ENTROPY_BYTES_128 = 16
        const val BITS_PER_WORD = 11
        const val PHRASE_LENGTH_12 = 12
        val VALID_PHRASE_LENGTHS = setOf(12, 15, 18, 21, 24)
    }
}

/**
 * Convert each byte of this [ByteArray] to its 8-bit big-endian
 * binary string and concatenate. Used by [MnemonicGenerator] to
 * turn entropy into the bit stream BIP39 expects.
 */
private fun ByteArray.toBitString(): String =
    joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(2).padStart(8, '0')
    }
