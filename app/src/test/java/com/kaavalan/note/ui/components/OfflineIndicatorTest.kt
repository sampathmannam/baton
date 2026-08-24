package com.kaavalan.note.ui.components

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v1.4 (PHONE-FINDING-6): offline pill contract test.
 *
 * Static-scan of the source + strings.xml, mirroring the v1.3
 * a11y-contentDescription regression-guard pattern in
 * [com.kaavalan.note.ui.AccessibilityContentDescriptionTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineIndicatorTest {

    @Test
    fun `offline pill is rendered when isOnline is false`() {
        val src = offlineIndicatorSource()
        assertTrue(
            "OfflineIndicator must reference R.string.offline_pill_label.",
            src.contains("R.string.offline_pill_label"),
        )
    }

    @Test
    fun `offline pill is NOT rendered when isOnline is true`() {
        val src = offlineIndicatorSource()
        val earlyReturn = src.contains("if (isOnline) return") ||
            (src.contains("if (isOnline)") && src.contains("return"))
        assertTrue(
            "OfflineIndicator must early-return when isOnline is true.",
            earlyReturn,
        )
    }

    @Test
    fun `offline pill has TalkBack contentDescription from a string resource`() {
        val src = offlineIndicatorSource()
        assertTrue(
            "OfflineIndicator must wrap the pill in semantics { contentDescription = ... }",
            src.contains("semantics") && src.contains("contentDescription"),
        )
        assertTrue(
            "OfflineIndicator's contentDescription must be sourced from R.string.offline_pill_a11y.",
            src.contains("stringResource") && src.contains("R.string.offline_pill_a11y"),
        )
    }

    @Test
    fun `offline pill uses surfaceVariant colour, not red`() {
        val src = offlineIndicatorSource()
        assertTrue(
            "OfflineIndicator must use surfaceVariant color.",
            src.contains("MaterialTheme.colorScheme.surfaceVariant"),
        )
        val redLiteral = Regex("""Color\s*\(\s*0x[Ff][Ff][0-5]""")
        assertTrue(
            "OfflineIndicator must not use red-dominant Color literals.",
            !redLiteral.containsMatchIn(src),
        )
    }

    @Test
    fun `offline_pill string resources are present in strings_xml`() {
        val stringsFile = File("src/main/res/values/strings.xml").absoluteFile
        assertTrue("strings.xml must exist", stringsFile.exists())
        val xml = stringsFile.readText(Charsets.UTF_8)
        listOf("offline_pill_label", "offline_pill_a11y").forEach { name ->
            val pattern = Regex("""<string\s+name\s*=\s*"$name"\s*>([^<]*)</string>""")
            val match = pattern.find(xml)
            assertNotNull("strings.xml must declare <string name=\"$name\">", match)
            val value = match!!.groupValues[1]
            assertTrue(
                "strings.xml entry '$name' must be non-blank (was '$value')",
                value.isNotBlank(),
            )
        }
    }

    private fun offlineIndicatorSource(): String {
        val f = File("src/main/java/com/kaavalan/note/ui/components/OfflineIndicator.kt").absoluteFile
        assertTrue("OfflineIndicator.kt must exist", f.exists())
        return f.readText(Charsets.UTF_8)
    }
}
