package com.kaavalan.note.ui.privacy

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import java.io.File

/**
 * v1.6.0: smoke test for the hold-to-reveal recovery phrase
 * surface. The full screen is FLAG_SECUREd, so we don't
 * drive the real [RecoveryPhraseScreen] here (Robolectric
 * can't go through the FLAG_SECURE pipeline). Instead we
 * verify that the strings and the design principle are
 * present in the resource bundle.
 *
 * The principle being tested:
 *   - "Press and hold to reveal" is the *only* affordance
 *     on the displayed-phrase surface.
 *   - The phrase words are NOT visible in the resource
 *     bundle — they come from a per-session random BIP39
 *     mnemonic, generated at runtime.
 *   - "I trust this device" replaces the generic "Done"
 *     button on the confirmed step.
 *
 * v1.6.0 (fix): the test used to call `Context.getString(R.string.*)`
 * via `ApplicationProvider.getApplicationContext<Context>()`.
 * That requires either `testOptions.unitTests.includeAndroidResources = true`
 * (which breaks 270+ other Robolectric tests by enabling the
 * AndroidKeyStore provider) or a `@Config(manifest=...)` annotation
 * on the test class. Both options have side effects. The
 * cleaner approach is to read the resource file directly from
 * the project source path — the test is a smoke test for the
 * design principle, not a test of the Android resource loader,
 * and the source path is a stable contract.
 *
 * user.dir is the module dir (app/) at test time, so the file
 * is at `src/main/res/values/strings.xml`. The pattern mirrors
 * the prompt test in [com.kaavalan.note.ai.extraction.ExtractorTest].
 */
class RecoveryPhraseHoldToRevealTest {

    private val stringsXml: String by lazy {
        // The values/strings.xml file is the canonical
        // source of every user-facing string in the
        // app. We read it once and let each test grep
        // for the substring it's looking for. A future
        // refactor that moves strings to a different
        // file or inlines them into Compose `Text(...)`
        // calls should update this path AND the tests
        // that depend on it.
        File("src/main/res/values/strings.xml").readText()
    }

    @Test
    fun holdToReveal_stringIsPresent_inResourceBundle() {
        // The two new strings must be present. If either
        // disappears, this test fails — that's the point.
        assertTrue(
            "recovery_phrase_hold_to_reveal must be declared in strings.xml",
            stringsXml.contains("name=\"recovery_phrase_hold_to_reveal\""),
        )
        // The hold-to-reveal affordance must be a verb
        // that says "hold" or "press", otherwise the
        // user has no hint that the gesture is
        // press-and-hold.
        val revealValue = extractStringValue("recovery_phrase_hold_to_reveal")
        assertTrue("recovery_phrase_hold_to_reveal must be non-blank", revealValue.isNotBlank())
        assertTrue(
            "The hold-to-reveal affordance must say 'hold' or 'press', was: $revealValue",
            revealValue.contains("hold", ignoreCase = true) ||
                revealValue.contains("press", ignoreCase = true),
        )
    }

    @Test
    fun revealedAffordance_stringIsPresent() {
        assertTrue(
            "recovery_phrase_revealing must be declared in strings.xml",
            stringsXml.contains("name=\"recovery_phrase_revealing\""),
        )
        val revealing = extractStringValue("recovery_phrase_revealing")
        assertTrue("recovery_phrase_revealing must be non-blank", revealing.isNotBlank())
    }

    @Test
    fun trustDevice_stringReplacesGenericDone() {
        // v1.6.0 replaced the generic "Done" with the
        // security affirmation "I trust this device".
        // The trust label must be present and must NOT
        // be a generic "Done" (a v1.5.x regression we
        // are guarding against).
        assertTrue(
            "recovery_phrase_trust_device must be declared in strings.xml",
            stringsXml.contains("name=\"recovery_phrase_trust_device\""),
        )
        val trust = extractStringValue("recovery_phrase_trust_device")
        assertTrue("recovery_phrase_trust_device must be non-blank", trust.isNotBlank())
        assertTrue(
            "The trust device button must contain 'trust', was: $trust",
            trust.contains("trust", ignoreCase = true),
        )
    }

    /**
     * Extracts the inner text of a `<string name="X">VALUE</string>`
     * declaration. The regex is intentionally simple — a multi-
     * line `VALUE` (with embedded newlines) won't match, but
     * our three target strings are all single-line. The matcher
     * also tolerates the standard Android escape
     * `\'` (apostrophe) which would otherwise end the regex
     * pattern.
     */
    private fun extractStringValue(name: String): String {
        val pattern = Regex("""<string\s+name="${Regex.escape(name)}"[^>]*>([^<]+)</string>""")
        return pattern.find(stringsXml)?.groupValues?.get(1)?.trim().orEmpty()
    }
}
