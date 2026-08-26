package com.kaavalan.note.data.vault

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1.1 (v2.0): passphrase strength meter.
 *
 * We do NOT block export on a low score — the user's choice
 * matters. We surface a 0-4 score + a label so the dialog can
 * warn at score 0/1 but allow the export to proceed.
 *
 * For v1 we ship a small, dependency-free scorer (~150 lines
 * below). It covers the same high-signal cases the canonical
 * `zxcvbn` library covers for short passphrases:
 *
 *  - empty / very short
 *  - exact common passwords (a small built-in list of 40)
 *  - all-digits, all-same-character
 *  - keyboard-row walks (`qwerty`, `asdf`, `1234`)
 *  - length-based "OK / Strong / Very strong" tiers
 *
 * A v1.7 may swap in `com.nulabinc.zxcvbn:zxcvbn4j` if APK
 * size pressure ever eases (zxcvbn4j adds ~700 KB of
 * dictionaries). The public surface here is `score(s)` and
 * `labelFor(score)`; the scorer is private.
 */
@Singleton
class PassphraseStrength @Inject constructor() {

    /** Returns a 0-4 score. 0 = trivially guessable, 4 = very strong. */
    fun score(passphrase: String): Int {
        if (passphrase.isEmpty()) return 0
        val trimmed = passphrase.trim()
        if (trimmed.isEmpty()) return 0
        if (trimmed.length < 4) return 0

        // Common password list — covers the top of the worst-passwords rankings.
        if (trimmed.lowercase() in COMMON_PASSWORDS) return 0

        if (isAllSame(trimmed)) return 0
        if (isKeyboardWalk(trimmed)) return 0
        if (isAllDigits(trimmed) && trimmed.length < 10) return 0

        // Length tiers + character-class variety.
        val classes = listOf(
            trimmed.any { it.isLowerCase() },
            trimmed.any { it.isUpperCase() },
            trimmed.any { it.isDigit() },
            trimmed.any { !it.isLetterOrDigit() },
        ).count { it }
        val length = trimmed.length
        return when {
            length >= 20 && classes >= 2 -> 4
            length >= 14 && classes >= 2 -> 3
            length >= 10 && classes >= 2 -> 2
            length >= 8 -> 1
            else -> 0
        }
    }

    /** Resolves a 0-4 score to a human-friendly label resource id. */
    fun labelFor(score: Int): String = when (score) {
        0 -> "too_weak"
        1 -> "weak"
        2 -> "ok"
        3 -> "strong"
        4 -> "very_strong"
        else -> "too_weak"
    }

    // ---- internals ----

    private fun isAllSame(s: String): Boolean = s.toSet().size == 1

    private fun isAllDigits(s: String): Boolean = s.all { it.isDigit() }

    /**
     * Detects the common keyboard walks (`qwerty`, `asdf`,
     * `1234`, `qaz`, etc.). True if the string is a prefix of
     * one of the walked sequences below.
     */
    private fun isKeyboardWalk(s: String): Boolean {
        val lower = s.lowercase()
        if (lower.length < 4) return false
        return WALKS.any { walk ->
            // Compare only the prefix length to avoid
            // `StringIndexOutOfBoundsException` when the
            // input is longer than the walk.
            lower == walk.substring(0, minOf(lower.length, walk.length))
        }
    }

    private companion object {
        // The 40 most common passwords (top of the "rockyou.txt" tail).
        // Kept here, not in assets/, to keep the unit tests hermetic.
        val COMMON_PASSWORDS = setOf(
            "password", "password1", "password123", "123456", "1234567",
            "12345678", "123456789", "1234567890", "qwerty", "qwertyuiop",
            "abc123", "111111", "iloveyou", "admin", "welcome", "monkey",
            "letmein", "dragon", "sunshine", "princess", "master", "shadow",
            "ashley", "football", "jesus", "michael", "ninja", "mustang",
            "solo", "batman", "access", "hello", "charlie", "donald",
            "trustno1", "1q2w3e4r", "zaq12wsx", "qazwsx", "passw0rd",
            "superman",
        )

        // Common keyboard walks.
        val WALKS = listOf(
            "qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890",
            "1qaz2wsx3edc4rfv5tgb6yhn7ujm", "qazwsxedc",
        )
    }
}
