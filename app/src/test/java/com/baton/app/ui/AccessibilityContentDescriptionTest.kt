package com.baton.app.ui

import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v1.3: a11y contentDescription coverage (F-19 in the SOTA audit).
 *
 * The spec §3 calls the app ADHD-friendly *and* accessible; the
 * TalkBack checklist says every interactive element must announce
 * a useful description. This test does two things:
 *
 *  1. **Static scan** — every `Button`, `IconButton`, `AssistChip`,
 *     `FilterChip`, `InputChip` and `Card(onClick = …)` call in
 *     the UI sources must have a `contentDescription` /
 *     `onClickLabel` or render a `Text(...)` child for TalkBack
 *     to read. This is the "fail if any new IconButton or Button
 *     is added without a contentDescription" guard the task asks
 *     for, and it runs as a plain JUnit + Robolectric test (no
 *     Compose runtime required).
 *
 *  2. **Resource presence** — the new TalkBack `a11y_*` string
 *     resources must exist in strings.xml and resolve at runtime;
 *     otherwise the production code that references them would
 *     throw at first use.
 *
 * The task spec mentions `createComposeRule()`; we attempted that
 * first but Robolectric 4.13's launcher-activity resolution
 * (PR #4736) makes `createAndroidComposeRule<ComponentActivity>()`
 * fail in the unit-test classpath regardless of how the test
 * manifest is shaped. The static-scan approach is more durable
 * and catches the same class of regression (any new interactive
 * call without an a11y label fails the build), so we keep the
 * data-layer test in [com.baton.app.features.adhd] for the
 * semantic checks and rely on this scan for the regression
 * guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class AccessibilityContentDescriptionTest {

    // -----------------------------------------------------------------
    // (1) Static-scan guard
    // -----------------------------------------------------------------

    /**
     * Every `Button`, `IconButton`, `AssistChip`, `FilterChip`,
     * `InputChip`, `Card(onClick = …)` and `Surface(onClick = …)`
     * call in the UI sources must have a `contentDescription` /
     * `onClickLabel`, OR a non-empty `Text(...)` child for
     * TalkBack to read. The scan is brace-balanced per call site
     * (a regex would under-count multi-line trailing lambdas) and
     * includes any trailing `{ … }` lambda block so we can spot
     * `Button(onClick = …) { Text(stringResource(…)) }` as having
     * visible text.
     */
    @Test
    fun everyInteractiveElement_hasContentDescriptionOrVisibleText() {
        val sources = collectUiSources()
        assertTrue(
            "Expected to find at least 8 UI source files; found ${sources.size}. " +
                "If you added a new top-level ui/features directory, update this test.",
            sources.size >= 8,
        )

        val callSites = sources.flatMap { src -> findCallSites(src) }
        assertTrue(
            "Expected at least 15 interactive call sites " +
                "(Button/IconButton/Chip/Card(onClick=)/Surface(onClick=)); " +
                "found ${callSites.size}. If you removed a screen, update this test.",
            callSites.size >= 15,
        )

        val offenders = callSites.filter { site ->
            val body = site.body
            val hasContentDescription = body.contains("contentDescription")
            val hasOnClickLabel = body.contains("onClickLabel")
            // The body might cover a Button(...) { Text(text = if (cond) {
            // stringResource(R.string.x) } else { stringResource(R.string.y) }) }
            // chain. We accept any of these patterns:
            //   1. `stringResource(` anywhere — used in Text(...),
            //      `text = stringResource(...)`, or a label.
            //   2. A literal "..." string inside Text() (literal
            //      string in the child Text or a `Text(if (cond)
            //      "Yes" else "No")` ternary).
            //   3. An explicit `label = { ... }` (FilterChip, AssistChip)
            val hasVisibleText = body.contains("stringResource") ||
                body.contains(Regex("""Text\s*\(\s*"[^"]+"\s*\)""")) ||
                body.contains(Regex("""\bText\s*\(\s*if\s*\(""")) ||
                body.contains(Regex("""label\s*=\s*\{"""))
            !hasContentDescription && !hasOnClickLabel && !hasVisibleText
        }

        if (offenders.isNotEmpty()) {
            val msg = buildString {
                appendLine("Found ${offenders.size} interactive element(s) with no contentDescription,")
                appendLine("onClickLabel, or visible Text/label child. Add one of:")
                appendLine("  - contentDescription = stringResource(R.string.a11y_*) on the call")
                appendLine("  - contentDescription = \"...\"  on the inner Icon")
                appendLine("  - onClickLabel = \"...\"        on the clickable / Button")
                appendLine("  - A Text(stringResource(...)) or label = { Text(\"...\") } child")
                appendLine()
                appendLine("Offending sites:")
                offenders.forEach { site ->
                    appendLine("  ${site.file}:${site.line}  ${site.kind}")
                }
            }
            throw AssertionError(msg)
        }
    }

    // -----------------------------------------------------------------
    // (2) String-resource presence
    // -----------------------------------------------------------------

    /**
     * The new TalkBack content-description strings must exist in
     * strings.xml and resolve to a non-blank value. If anyone
     * renames or deletes them, the production code that
     * references them would crash on first use.
     *
     * We read strings.xml directly off disk instead of going
     * through `Context.getString()`: the latter requires Android
     * resources to be packaged into the test classpath
     * (`testOptions.unitTests.isIncludeAndroidResources = true`),
     * which in turn forces the production [com.baton.app.BatonApplication]
     * to be instantiated, and that fails the unrelated
     * SecurePreferences initialiser under Robolectric (no
     * AndroidKeyStore). Keeping the test pure-JVM means the
     * other test classes aren't disturbed.
     */
    @Test
    fun a11yStringResources_arePresent() {
        val stringsFile = File("src/main/res/values/strings.xml").absoluteFile
        assertTrue(
            "strings.xml must exist at ${stringsFile.absolutePath}",
            stringsFile.exists(),
        )
        val xml = stringsFile.readText(Charsets.UTF_8)
        val requiredNames = listOf(
            "a11y_person_row_open",
            "a11y_person_count_badge",
            "a11y_person_count_badge_one",
            "a11y_person_stale_indicator",
            "a11y_status_chip",
            "a11y_confidence_high",
            "a11y_confidence_medium",
            "a11y_confidence_low",
            // v1.6.0 (Tier 0.4): the in-app voice stop button on
            // the capture sheet must be readable by TalkBack.
            "a11y_voice_in_app_stop",
        )
        requiredNames.forEach { name ->
            val pattern = Regex("""<string\s+name\s*=\s*"$name"\s*>([^<]*)</string>""")
            val match = pattern.find(xml)
            assertNotNull(
                "strings.xml must declare <string name=\"$name\">",
                match,
            )
            val value = match!!.groupValues[1]
            assertTrue(
                "strings.xml entry '$name' must be non-blank (was '$value')",
                value.isNotBlank(),
            )
        }
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private data class CallSite(
        val file: String,
        val line: Int,
        val kind: String,
        val body: String,
    )

    private val callKinds = listOf(
        "Button", "IconButton", "AssistChip", "FilterChip", "InputChip",
    )

    private fun collectUiSources(): List<File> {
        val root = File("src/main/java/com/baton/app").absoluteFile
        val uiRoot = File(root, "ui")
        val featuresRoot = File(root, "features")
        val files = mutableListOf<File>()
        listOf(uiRoot, featuresRoot).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .forEach { files += it }
            }
        }
        return files
    }

    /**
     * For each UI source file, find every Button( / IconButton( /
     * AssistChip( / FilterChip( / InputChip( call site, plus every
     * `Card(` and `Surface(` call site, and extract the call body
     * up to the matching close paren AND the trailing lambda block
     * (if any). This is what makes the test able to detect
     * `Button(onClick = …) { Text(stringResource(…)) }` as having
     * visible text.
     */
    private fun findCallSites(file: File): List<CallSite> {
        val text = file.readText()
        val sites = mutableListOf<CallSite>()

        // Button / IconButton / *Chip
        val primaryRegex = Regex("""\b(${callKinds.joinToString("|")})\s*\(""")
        primaryRegex.findAll(text).forEach { m ->
            val (body, _) = extractCallAndTrailingLambda(text, m.range.last)
                ?: return@forEach
            sites += CallSite(
                file = file.path,
                line = lineNumberAt(text, m.range.first),
                kind = m.groupValues[1],
                body = body,
            )
        }

        // Card(onClick = …) — only count Cards that have an
        // onClick arg. The simple `Card(...) { … }` display card
        // is fine without a contentDescription.
        val cardRegex = Regex("""\bCard\s*\(""")
        cardRegex.findAll(text).forEach { m ->
            val (body, _) = extractCallAndTrailingLambda(text, m.range.last)
                ?: return@forEach
            if (body.contains(Regex("""onClick\s*="""))) {
                sites += CallSite(
                    file = file.path,
                    line = lineNumberAt(text, m.range.first),
                    kind = "Card(onClick=)",
                    body = body,
                )
            }
        }

        // Surface(onClick = …) — the NoteBar uses a clickable
        // Surface; flag any future ones that forget the
        // contentDescription.
        val surfaceRegex = Regex("""\bSurface\s*\(""")
        surfaceRegex.findAll(text).forEach { m ->
            val (body, _) = extractCallAndTrailingLambda(text, m.range.last)
                ?: return@forEach
            if (body.contains(Regex("""onClick\s*="""))) {
                sites += CallSite(
                    file = file.path,
                    line = lineNumberAt(text, m.range.first),
                    kind = "Surface(onClick=)",
                    body = body,
                )
            }
        }

        return sites
    }

    /**
     * Given the index of the opening `(` of a call, return the
     * substring from that `(` up to (and including) the matching
     * close paren AND any trailing `{ … }` lambda block that
     * follows it. Returns null on imbalance.
     */
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

        // Skip whitespace
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

    private fun lineNumberAt(text: String, index: Int): Int {
        var line = 1
        for (i in 0 until index) if (text[i] == '\n') line++
        return line
    }
}
