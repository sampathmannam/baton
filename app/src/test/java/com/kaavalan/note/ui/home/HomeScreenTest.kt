package com.kaavalan.note.ui.home

import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v1.4 (PHONE-FINDING-1): the empty-state "Add person" Button must be
 * present and tappable when the Home tab has zero people.
 *
 * The previous v1.3 path relied on a [androidx.compose.material3.FloatingActionButton]
 * whose `primaryContainer` colour was too low-contrast against the
 * dark surface — new users missed the entry point. The v1.4 fix
 * adds a prominent primary-coloured [androidx.compose.material3.Button]
 * under the empty-state copy; the FAB is still rendered (for the
 * non-empty state) but the Button is the first-impression entry
 * point.
 *
 * This test is a **static scan** rather than a Compose UI test.
 * The pattern is the same as
 * [com.kaavalan.note.ui.AccessibilityContentDescriptionTest]: Robolectric
 * 4.13's launcher-activity resolution (PR #4736) makes
 * `createComposeRule()` / `createAndroidComposeRule<ComponentActivity>()`
 * fail in the unit-test classpath regardless of how the test manifest
 * is shaped. The static-scan approach is more durable and catches
 * the same class of regression (any refactor that drops the
 * `onAddPersonClick` wiring on the empty state fails the build).
 *
 * The scan asserts:
 *  1. `HomeScreen` renders the empty state with the wiring
 *     `EmptyState(onAddPersonClick = { showAddPerson = true })`.
 *  2. The `EmptyState` Composable's body contains a
 *     `Button(onClick = onAddPersonClick, ...)` block.
 *  3. That Button's body renders a `Text(text = stringResource(R.string.home_add_person), ...)`.
 *  4. `CaptureSheet(...)` is invoked with an `onOpenAddPerson = { ... }`
 *     callback so the inline "Add a person first" card on the
 *     capture sheet (v1.4 PHONE-FINDING-8) routes to the same
 *     entry point.
 *  5. The new `capture_needs_person_message` string resource exists
 *     in `strings.xml` and is non-blank.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class HomeScreenTest {

    /**
     * (1) HomeScreen must render the empty state with the
     * `onAddPersonClick = { showAddPerson = true }` wiring. This
     * is the production button the empty state renders; without
     * the wiring the user has no path to AddPerson from the
     * empty state.
     */
    @Test
    fun homeScreen_emptyStateRendersAddPersonButton() {
        val src = readHomeScreenSource()
        assertNotNull(
            "HomeScreen.kt must be readable off disk at $HOME_SCREEN_PATH",
            src,
        )
        val text = src!!
        assertTrue(
            "HomeScreen must render the empty state with onAddPersonClick wiring:\n" +
                "    HomeUiState.Empty -> EmptyState(padding, onAddPersonClick = { showAddPerson = true })\n" +
                "but the source did not contain that line.",
            text.contains(REGEX_EMPTY_STATE_WIRING),
        )
    }

    /**
     * (2) The `EmptyState` Composable's body must include a
     * `Button(onClick = onAddPersonClick, ...)`. The test scans for
     * the call site rather than parsing the AST — a refactor that
     * renames the parameter to a different onClick lambda breaks
     * the build before it ships.
     */
    @Test
    fun emptyState_callsButtonWithOnAddPersonClick() {
        val text = readHomeScreenSource()!!
        // Find the EmptyState Composable body (the private fun EmptyState
        // declaration through to the next top-level @Composable or `}`).
        val emptyStateRange = findComposableBodyRange(
            text = text,
            signature = "private fun EmptyState(",
        )
        assertTrue(
            "Could not locate the private EmptyState Composable in HomeScreen.kt",
            emptyStateRange != null,
        )
        val body = text.substring(emptyStateRange!!.first, emptyStateRange.second)
        assertTrue(
            "EmptyState body must contain Button(onClick = onAddPersonClick, ...):\n" +
                "    Button(\n" +
                "        onClick = onAddPersonClick,\n" +
                "        ...\n" +
                "    ) { ... }\n" +
                "but the body was:\n$body",
            body.contains(REGEX_BUTTON_ON_ADD_PERSON_CLICK),
        )
    }

    /**
     * (3) The Button's body must render
     * `Text(text = stringResource(R.string.home_add_person), ...)`
     * so TalkBack and the visual layout both surface the "Add
     * person" label. The same string is used on the FAB and on
     * the inline "Add a person first" card on the capture sheet
     * (v1.4 PHONE-FINDING-8) so the empty state, the FAB, and
     * the capture sheet card all read as the same action.
     */
    @Test
    fun emptyState_buttonRendersHomeAddPersonText() {
        val text = readHomeScreenSource()!!
        val emptyStateRange = findComposableBodyRange(
            text = text,
            signature = "private fun EmptyState(",
        )!!
        val body = text.substring(emptyStateRange.first, emptyStateRange.second)
        assertTrue(
            "EmptyState body must contain Text(text = stringResource(R.string.home_add_person), ...)",
            body.contains(REGEX_BUTTON_HOME_ADD_PERSON_TEXT),
        )
    }

    /**
     * (4) The capture sheet must be invoked with an
     * `onOpenAddPerson = { ... }` callback that the inline
     * "Add a person first" card (v1.4 PHONE-FINDING-8) uses
     * to open the AddPerson form. The callback must point at
     * the same `showAddPerson = true` state the empty-state
     * Button uses so the user lands in the same form regardless
     * of which entry point they came from.
     */
    @Test
    fun homeScreen_captureSheetHasOnOpenAddPersonCallback() {
        val text = readHomeScreenSource()!!
        assertTrue(
            "HomeScreen must invoke CaptureSheet(... onOpenAddPerson = { ... showAddPerson = true ... } ...)\n" +
                "so the inline no-people card on the capture sheet routes to AddPerson.",
            text.contains(REGEX_CAPTURE_SHEET_ON_OPEN_ADD_PERSON),
        )
    }

    /**
     * (5) The new v1.4 string `capture_needs_person_message` must
     * exist in `strings.xml` and resolve to a non-blank value.
     * The capture sheet's inline NoPeopleCard renders this string
     * verbatim; if it's missing, the sheet would crash on first
     * use by a brand-new user.
     */
    @Test
    fun captureNeedsPersonMessage_stringIsPresent() {
        val stringsFile = File(STRINGS_PATH).absoluteFile
        assertTrue(
            "strings.xml must exist at ${stringsFile.absolutePath}",
            stringsFile.exists(),
        )
        val xml = stringsFile.readText(Charsets.UTF_8)
        val pattern = Regex(
            """<string\s+name\s*=\s*"capture_needs_person_message"\s*>([^<]*)</string>""",
        )
        val match = pattern.find(xml)
        assertNotNull(
            "strings.xml must declare <string name=\"capture_needs_person_message\">",
            match,
        )
        val value = match!!.groupValues[1]
        assertTrue(
            "strings.xml entry 'capture_needs_person_message' must be non-blank (was '$value')",
            value.isNotBlank(),
        )
        // Spec §1: the message must be neutral, action-oriented, and
        // must not contain "error" / "failed" / red-tinted language.
        assertTrue(
            "capture_needs_person_message must not use the word 'error' (no-shame spec §1); was '$value'",
            !value.contains("error", ignoreCase = true),
        )
        assertTrue(
            "capture_needs_person_message must not use the word 'failed' (no-shame spec §1); was '$value'",
            !value.contains("failed", ignoreCase = true),
        )
        assertTrue(
            "capture_needs_person_message must guide the user to the next action; was '$value'",
            value.contains("Add a person", ignoreCase = true) ||
                value.contains("add a person", ignoreCase = true),
        )
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private fun readHomeScreenSource(): String? {
        val candidates = listOf(
            File(HOME_SCREEN_PATH),
            File("app/src/main/java/com/kaavalan/note/ui/home/HomeScreen.kt"),
        )
        for (f in candidates) {
            if (f.exists()) return f.readText(Charsets.UTF_8)
        }
        return null
    }

    /**
     * Find the byte range of a Composable's body — the opening
     * `{` after the function signature through the matching closing
     * `}`. A naive regex on the call sites is unreliable (the
     * trailing lambda body can span many lines). We do a manual
     * brace-count starting at the first `{` after the signature.
     */
    private fun findComposableBodyRange(
        text: String,
        signature: String,
    ): Pair<Int, Int>? {
        val sigIdx = text.indexOf(signature)
        if (sigIdx < 0) return null
        // Find the opening `{` of the function body.
        val openBrace = text.indexOf('{', sigIdx)
        if (openBrace < 0) return null
        var depth = 0
        var i = openBrace
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return openBrace to (i + 1)
                    }
                }
            }
            i += 1
        }
        return null
    }

    private companion object {
        // Tests run from the `app/` module directory under
        // gradle's working directory; the path is relative.
        const val HOME_SCREEN_PATH =
            "src/main/java/com/kaavalan/note/ui/home/HomeScreen.kt"
        const val STRINGS_PATH = "src/main/res/values/strings.xml"

        // Empty-state wiring: the `when` arm under `Scaffold`'s
        // content lambda that renders the empty state with the
        // production callback.
        val REGEX_EMPTY_STATE_WIRING = Regex(
            """HomeUiState\.Empty\s*->\s*EmptyState\s*\(\s*[^)]*onAddPersonClick\s*=\s*\{\s*showAddPerson\s*=\s*true\s*\}""",
        )

        // The Button in EmptyState must call `onClick = onAddPersonClick`.
        // We accept any number of parameters between `Button(` and
        // `onClick = onAddPersonClick`.
        val REGEX_BUTTON_ON_ADD_PERSON_CLICK = Regex(
            """Button\s*\([^)]*onClick\s*=\s*onAddPersonClick""",
        )

        // The Button body must render the home_add_person string
        // resource (so TalkBack reads "Add person" and the visible
        // text matches the FAB's label).
        val REGEX_BUTTON_HOME_ADD_PERSON_TEXT = Regex(
            """Text\s*\(\s*text\s*=\s*stringResource\s*\(\s*R\.string\.home_add_person""",
        )

        // CaptureSheet must receive an onOpenAddPerson callback that
        // closes the sheet and sets showAddPerson = true. The exact
        // shape can vary, so we accept any `onOpenAddPerson = {` with
        // `showAddPerson = true` in the body. We can't use
        // `[^)]*` for the inside of `CaptureSheet(...)` because
        // the callback body may contain nested parens (e.g.
        // `captureViewModel.dismissSheet()`); instead we use
        // `[\s\S]*?` (non-greedy any-char) bounded by the
        // onOpenAddPerson and showAddPerson tokens. The
        // non-greedy match means we read forward until the
        // first `showAddPerson = true` after the `onOpenAddPerson
        // = {` token, which is the production wiring.
        val REGEX_CAPTURE_SHEET_ON_OPEN_ADD_PERSON = Regex(
            """onOpenAddPerson\s*=\s*\{[\s\S]*?showAddPerson\s*=\s*true""",
        )
    }
}
