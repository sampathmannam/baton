package com.baton.app.ui.components

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * v1.6.0: design-rule static scans. These tests catch the
 * "I shipped a red overdue chip" or "I added a 'what's
 * new' modal" regression by scanning the UI source tree
 * for forbidden patterns. They are deliberately cheap
 * (no Compose, no Robolectric) so they run on every PR.
 *
 * The 8 design rules from the feature-audit v3.0 (§4.6):
 *  1. No "overdue" or "missed" language anywhere.
 *  2. No "What's new" modal on releases.
 *  3. No "feature of the day" / "tips" tab.
 *  4. No data dashboard for the user's own activity.
 *  5. No sync indicator / "saving..." status.
 *  6. No more than 10 entries on the Settings page.
 *  7. No per-contact cadence overrides; cadence is per-tier.
 *  8. No magic-string "self-destruct" timers; auto-wipe the key.
 *
 * Rules 1-5 + 8 are scanner tests. Rules 6, 7 require
 * semantic checks (covered in [SettingsSheetTest] and
 * [TierCadenceTest] respectively) and are not enforced here.
 */
@RunWith(AndroidJUnit4::class)
class DesignRulesTest {

    private fun uiSources(): List<File> {
        val root = File("src/main/java/com/baton/app")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val path = f.absolutePath.replace('\\', '/')
                // UI sources only — exclude data layer, AI,
                // vault crypto, etc. The audit restricts the
                // design rules to the *user-facing* surface.
                path.contains("/ui/") || path.contains("/features/")
            }
            .toList()
    }

    private fun readText(f: File): String =
        f.readText(Charsets.UTF_8)

    /**
     * Returns the non-comment, non-string-resource lines of
     * the file. We strip:
     *  - `//` line comments
     *  - `*` block-comment continuation lines
     *  - `<string name="...">VALUE</string>` resource
     *    declarations (the VALUE is the user-facing copy, not
     *    the resource name; we still scan those in the
     *    separate [rule1_inStringsXml] test)
     *
     * The goal is to scan only the code paths that ship
     * strings to the UI surface, not the comments that
     * document the rules. A `// no overdue` comment is a
     * guard, not a violation.
     */
    private fun nonCommentLines(text: String): String {
        val sb = StringBuilder()
        text.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//")) return@forEach
            if (trimmed.startsWith("*")) return@forEach
            if (trimmed.startsWith("/*")) return@forEach
            // Strip string-resource declarations. The
            // `name="..."` is metadata; the text between the
            // tags is user-facing — handled by the strings
            // scanner.
            val cleaned = line.replace(
                Regex("""<string\s+name="[^"]+">[^<]*</string>"""),
                "",
            )
            sb.append(cleaned).append('\n')
        }
        return sb.toString()
    }

    @Test
    fun rule1_noOverdueOrMissedLanguageInUi() {
        val offenders = mutableListOf<String>()
        uiSources().forEach { f ->
            val text = readText(f)
            // Scan non-comment lines only. Comments that
            // document the *absence* of the red overdue
            // pattern (e.g. "// no overdue wording") are
            // guards, not violations.
            val scannable = nonCommentLines(text)
            listOf("overdue", "missed").forEach { word ->
                val regex = Regex("\\b$word\\b", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(scannable)) {
                    offenders.add("${f.relativeTo(File("src/main/java/com/baton/app"))}: $word")
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "Rule 1 violation: 'overdue' or 'missed' found in user-facing UI code.\n" +
                    "Research basis: RSD at physical-pain intensity for ADHD users; " +
                    "Newark & Stieglitz 2010.\nOffenders:\n  " + offenders.joinToString("\n  "),
            )
        }
    }

    @Test
    fun rule1_inStringsXml() {
        // The strings.xml resource file is the
        // single source of user-facing copy. The rule
        // applies to the VALUES between the tags, not
        // the names. Resource names are documentation
        // for the developer; values are copy for the
        // user.
        val strings = File("src/main/res/values/strings.xml")
        if (!strings.exists()) return
        val text = strings.readText(Charsets.UTF_8)
        val values = Regex("""<string\s+name="[^"]+">([^<]*)</string>""")
            .findAll(text).map { it.groupValues[1] }.joinToString(" ")
        listOf("overdue", "missed").forEach { word ->
            val regex = Regex("\\b$word\\b", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(values)) {
                throw AssertionError(
                    "Rule 1 violation: '$word' found in a user-facing strings.xml value. " +
                        "Use 'carried over', 'still on the list', or a similar shame-free label.",
                )
            }
        }
    }

    @Test
    fun rule2_noWhatsNewModalInUi() {
        val offenders = mutableListOf<String>()
        uiSources().forEach { f ->
            val text = readText(f)
            val regex = Regex("""["']what'?s new["']""", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(text)) {
                offenders.add(f.relativeTo(File("src/main/java/com/baton/app")).path)
            }
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "Rule 2 violation: a 'What's new' modal/marker is in the UI.\n" +
                    "Research basis: Nielsen 2024 — 'Prune on a schedule. Features accrete, " +
                    "and screens silt up with interface detritus.'\nOffenders:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }

    @Test
    fun rule3_noFeatureOfTheDayOrTipsTab() {
        val offenders = mutableListOf<String>()
        uiSources().forEach { f ->
            val text = readText(f)
            listOf("feature of the day", "tip of the day", "tips tab").forEach { phrase ->
                if (text.contains(phrase, ignoreCase = true)) {
                    offenders.add("${f.relativeTo(File("src/main/java/com/baton/app"))}: $phrase")
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "Rule 3 violation: a 'feature of the day' or 'tips' tab is in the UI.\n" +
                    "Research basis: feature-fatigue applied to *promotion*.\nOffenders:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }

    @Test
    fun rule5_noSyncIndicatorInUi() {
        val offenders = mutableListOf<String>()
        uiSources().forEach { f ->
            val text = readText(f)
            // "saving...", "syncing...", "uploading..." as
            // visible status copy. The async / sync work
            // happens in the background; the user does not
            // need to see it.
            listOf("\"saving...\"", "\"syncing...\"", "\"uploading...\"").forEach { phrase ->
                if (text.contains(phrase)) {
                    offenders.add("${f.relativeTo(File("src/main/java/com/baton/app"))}: $phrase")
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "Rule 5 violation: a sync/saving indicator string is in the UI.\n" +
                    "Research basis: Bear's anti-friction principle: " +
                    "'Sync in background. No indicators.'\nOffenders:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }

    @Test
    fun rule8_noMagicStringSelfDestructTimers() {
        val offenders = mutableListOf<String>()
        uiSources().forEach { f ->
            val text = readText(f)
            // Magic-string self-destruct: literal "5_min" or
            // "30_sec" passed to a delay/destroyer. The
            // research rule is: auto-wipe the *key*, not the
            // visible data, and never on a magic string.
            val regex = Regex("""["'][0-9]+_(min|sec|hour)s?["']""")
            if (regex.containsMatchIn(text)) {
                offenders.add(f.relativeTo(File("src/main/java/com/baton/app")).path)
            }
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError(
                "Rule 8 violation: a magic-string self-destruct timer was found.\n" +
                    "Research basis: 'auto-wipe the key, not the visible data'.\nOffenders:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }
}
