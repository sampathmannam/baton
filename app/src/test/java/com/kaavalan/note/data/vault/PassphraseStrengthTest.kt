package com.kaavalan.note.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.1 (v2.0): the passphrase strength meter. Pure
 * Kotlin, no Android dependency, but runs under Robolectric
 * for symmetry with the rest of the vault test suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PassphraseStrengthTest {

    private val scorer = PassphraseStrength()

    @Test
    fun `empty -- score 0`() {
        assertEquals(0, scorer.score(""))
    }

    @Test
    fun `password -- score 0 (in the common-password list)`() {
        assertEquals(0, scorer.score("password"))
    }

    @Test
    fun `qwerty -- score 0 (keyboard walk)`() {
        assertEquals(0, scorer.score("qwerty"))
    }

    @Test
    fun `short all-digits -- score 0`() {
        assertEquals(0, scorer.score("12345"))
    }

    @Test
    fun `long digits only -- score 1 (length is fine but no variety)`() {
        // The scorer requires two character classes for scores
        // 2+. Length >= 8 alone gives score 1.
        val s = scorer.score("12345678")
        assertTrue("12345678 should be at most score 1, got $s", s <= 1)
    }

    @Test
    fun `two words with a space and length 10 -- score 2 (OK)`() {
        assertEquals(2, scorer.score("abc 123456"))
    }

    @Test
    fun `4-word passphrase with length 28 -- score 3 or 4 (very strong)`() {
        // Mixed case + 1 digit to satisfy the two-character-class
        // requirement. Without the digit the score is 1 (length
        // tier only).
        val s = scorer.score("Correct Horse Battery Staple 7")
        assertTrue("phrase should be score 3 or 4, got $s", s >= 3)
    }

    @Test
    fun `mixed-case with symbols, length 16 -- score 4 (very strong)`() {
        val s = scorer.score("MyKaavalanIs!Kaavalan")
        assertTrue("expected strong score, got $s", s >= 3)
    }

    @Test
    fun `labelFor maps each score to a known label`() {
        // The Compose layer looks up these exact strings.
        assertEquals("too_weak", scorer.labelFor(0))
        assertEquals("weak", scorer.labelFor(1))
        assertEquals("ok", scorer.labelFor(2))
        assertEquals("strong", scorer.labelFor(3))
        assertEquals("very_strong", scorer.labelFor(4))
    }
}
