package com.kaavalan.note.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.4 (PHONE-FINDING-2) + BUG-AUDIT-2: the erase-all-data button
 * must not use the red `errorContainer` colour.
 *
 * v1.5.1: the button no longer calls `viewModel.signOut()`
 * directly — it now opens a confirmation dialog first (VAULT-007).
 * The test finds the button by the destructive trigger
 * `showEraseConfirmation = true` instead of the old direct call.
 */
class SettingsSheetTest {

    private val settingsSheetFile: File =
        File("src/main/java/com/kaavalan/note/ui/settings/SettingsSheet.kt").absoluteFile

    private fun source(): String = settingsSheetFile.readText(Charsets.UTF_8)

    private fun signOutButtonCallSite(text: String): String? {
        val regex = Regex("""\bButton\s*\(""")
        regex.findAll(text).forEach { m ->
            val (body, _) = extractCallAndTrailingLambda(text, m.range.last) ?: return@forEach
            // v1.5.1: the destructive button either calls
            // viewModel.signOut() directly (legacy v1.4 shape) OR
            // sets showEraseConfirmation = true (v1.5.1 shape with
            // a confirmation dialog). Match either.
            if (body.contains("viewModel.signOut") || body.contains("showEraseConfirmation")) {
                return body
            }
        }
        return null
    }

    /**
     * BUG-AUDIT-2: the Sign out button is not red.
     */
    @Test
    fun `BUG-AUDIT-2 sign out button is not red`() {
        val text = source()
        val body = signOutButtonCallSite(text)
        assertTrue(
            "SettingsSheet.kt must contain a Button(...) that calls viewModel.signOut().",
            body != null,
        )

        assertFalse(
            "BUG-AUDIT-2: the Sign out button must NOT use errorContainer. Found: $body",
            body!!.contains("errorContainer"),
        )
        assertFalse(
            "BUG-AUDIT-2: the Sign out button must NOT use onErrorContainer. Found: $body",
            body.contains("onErrorContainer"),
        )
        assertFalse(
            "BUG-AUDIT-2: the Sign out button must NOT use colorScheme.error. Found: $body",
            body.contains("colorScheme.error"),
        )

        assertTrue(
            "BUG-AUDIT-2: the Sign out button must use surfaceVariant. Found: $body",
            body.contains("surfaceVariant"),
        )
        assertTrue(
            "BUG-AUDIT-2: the Sign out button must use onSurfaceVariant. Found: $body",
            body.contains("onSurfaceVariant"),
        )
    }

    @Test
    fun `sign out button has a visual differentiator beyond colour - either border or icon prefix`() {
        val text = source()
        val body = signOutButtonCallSite(text)
        assertTrue("Sign out button must exist", body != null)
        val hasBorder = body!!.contains("BorderStroke") || body.contains("border =")
        val hasIconPrefix = body.contains("Icons.Default.Lock") ||
            body.contains("Icons.Outlined.Lock") ||
            body.contains("Icons.Filled.Lock")
        assertTrue(
            "BUG-AUDIT-2: the Sign out button must carry a non-colour " +
                "differentiator (border or icon prefix). Found: $body",
            hasBorder || hasIconPrefix,
        )
    }

    @Test
    fun `sign out button label resolves from a string resource (a11y)`() {
        val text = source()
        val body = signOutButtonCallSite(text)
        assertTrue("Sign out button must exist", body != null)
        assertTrue(
            "BUG-AUDIT-2: the Sign out button's text must come from " +
                "stringResource. Found: $body",
            body!!.contains("stringResource"),
        )
    }

    @Test
    fun `SettingsSheet kt source file is at the expected path`() {
        assertTrue(
            "SettingsSheet.kt must exist at ${settingsSheetFile.absolutePath}.",
            settingsSheetFile.exists(),
        )
    }

    private fun extractCallAndTrailingLambda(text: String, openIndex: Int): Pair<String, Int>? {
        var depth = 0
        var i = openIndex
        var closeParenIndex = -1
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        closeParenIndex = i
                        i++
                        break
                    }
                }
            }
            i++
        }
        if (closeParenIndex < 0) return null

        while (i < text.length && text[i].isWhitespace()) i++
        if (i < text.length && text[i] == '{') {
            var braceDepth = 0
            while (i < text.length) {
                when (text[i]) {
                    '{' -> braceDepth++
                    '}' -> {
                        braceDepth--
                        if (braceDepth == 0) {
                            return text.substring(openIndex, i + 1) to (i + 1)
                        }
                    }
                }
                i++
            }
            return null
        }
        return text.substring(openIndex, closeParenIndex + 1) to (closeParenIndex + 1)
    }
}
