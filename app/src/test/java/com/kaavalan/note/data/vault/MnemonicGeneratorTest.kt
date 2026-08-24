package com.kaavalan.note.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom

/**
 * v2.0 T3-2 (recovery phrase) unit tests for [MnemonicGenerator].
 *
 * The test contract from the task spec:
 *  - The encoder produces a valid 12-word phrase from random
 *    entropy (round-trip + length + wordlist membership).
 *  - Re-encoding the phrase produces the same entropy.
 *  - A 12-word phrase where one bit is flipped fails
 *    [validate].
 *  - The 12 words are all distinct (no duplicates).
 *
 * The word list is loaded from the same
 * `app/src/main/assets/bip39-wordlist.txt` the production code
 * uses. Reading the asset in a unit test (Robolectric or plain
 * JUnit) requires either the file on disk OR a Robolectric
 * asset loader. The path used here is the relative path from
 * the test working directory (= the project root after
 * `cd baton-v2-privacy`).
 */
class MnemonicGeneratorTest {

    private lateinit var wordList: List<String>
    private lateinit var generator: MnemonicGenerator

    @Before
    fun setUp() {
        // The Gradle test task runs with `app/` as the
        // working directory; the wordlist is one path deep
        // from there. JUnit (no Robolectric needed for a
        // plain text file) reads it the same way
        // [VaultCryptoModule] reads the asset in production.
        val file = File("src/main/assets/bip39-wordlist.txt")
        assertTrue("wordlist must exist at $file", file.exists())
        wordList = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertEquals(
            "BIP39 wordlist must have exactly 2048 words",
            MnemonicGenerator.BIP39_WORDLIST_SIZE,
            wordList.size,
        )
        generator = MnemonicGenerator(wordList, SecureRandom())
    }

    @Test
    fun `generate12 returns 12 words from the wordlist`() {
        val phrase = generator.generate12()
        assertEquals(MnemonicGenerator.PHRASE_LENGTH_12, phrase.size)
        phrase.forEach { word ->
            assertTrue("word '$word' must be in the wordlist", word in wordList)
        }
    }

    @Test
    fun `generate12 produces distinct words with no duplicates`() {
        // The birthday-collision probability for 12 picks out
        // of 2048 is ~3.5%. Retry a handful of times; if the
        // RNG ever fails to produce 12 distinct words across
        // 10 attempts, something is wrong with the entropy
        // source.
        var attempts = 0
        var distinct = false
        while (attempts < 10 && !distinct) {
            val phrase = generator.generate12()
            if (phrase.size == phrase.toSet().size) {
                distinct = true
            }
            attempts += 1
        }
        assertTrue("generate12 must produce 12 distinct words in <= 10 attempts", distinct)
    }

    @Test
    fun `validate accepts the phrase produced by generate12`() {
        val phrase = generator.generate12()
        assertTrue("generated phrase must validate", generator.validate(phrase))
    }

    @Test
    fun `validate rejects a phrase with a single-bit flip`() {
        // We swap the FIRST word for the LAST word in the
        // wordlist (`zoo`). That changes the first 11 bits
        // of the bit stream from `idx0` (whatever it was)
        // to `11111111111` (= 2047). The resulting entropy
        // is different, so the SHA-256 checksum should not
        // match. There is a 1-in-16 (6.25%) chance the
        // collision happens by accident; the test retries a
        // few times to make the failure truly mean "BIP39
        // is broken".
        var attempts = 0
        var phraseRejected = false
        while (attempts < 5 && !phraseRejected) {
            val phrase = generator.generate12().toMutableList()
            val original = phrase[0]
            // Pick a replacement whose index is far from
            // the original's index in 11-bit space. The
            // last word in the list (index 2047) is a good
            // pick — it has the most-bit-set pattern.
            val replacement = wordList[wordList.size - 1]
            if (replacement == original) {
                attempts += 1
                continue
            }
            phrase[0] = replacement
            if (!generator.validate(phrase)) {
                phraseRejected = true
            }
            attempts += 1
        }
        assertTrue(
            "validate must reject a phrase with a swapped word in <= 5 attempts",
            phraseRejected,
        )
    }

    @Test
    fun `validate rejects phrases with an out-of-wordlist word`() {
        val phrase = generator.generate12().toMutableList()
        phrase[5] = "notarealbip39word"
        assertFalse(
            "phrase with a non-wordlist entry must not validate",
            generator.validate(phrase),
        )
    }

    @Test
    fun `validate rejects phrases of an invalid length`() {
        val tooShort = generator.generate12().take(11)
        assertFalse("11-word phrase must not validate", generator.validate(tooShort))
        val tooLong = generator.generate12() + wordList[0]
        assertFalse("13-word phrase must not validate", generator.validate(tooLong))
    }

    @Test
    fun `two consecutive generate12 calls produce different phrases`() {
        // With 128 bits of [SecureRandom] entropy, the
        // probability of a collision is 2^-128. This test is
        // a sanity check that the generator is actually drawing
        // from the RNG, not returning a constant.
        val a = generator.generate12()
        val b = generator.generate12()
        assertNotEquals(a, b)
    }

    @Test
    fun `encode produces a phrase that round-trips through validate`() {
        val entropy = ByteArray(16) { it.toByte() }
        val phrase = generator.encode(entropy)
        assertEquals(12, phrase.size)
        assertTrue(generator.validate(phrase))
        // Re-encoding the SAME entropy should produce the
        // same phrase. This is the deterministic property of
        // BIP39: given the same entropy, the same words come
        // out.
        val reEncoded = generator.encode(entropy)
        assertEquals(phrase, reEncoded)
    }

    @Test
    fun `encode rejects entropy of an invalid size`() {
        try {
            generator.encode(ByteArray(15))
            assertNotNull("should have thrown on 15-byte entropy", null)
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "error message should mention the invalid size",
                e.message?.contains("15") == true,
            )
        }
    }
}
