package com.kaavalan.note.ui.today.decay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1.9.5 (Compact DecayRow): the per-row "Mark recent" TextButton
 * is gone. The affordance moved to:
 *  1. Swipe-right past a 96dp threshold (Material 3 standard
 *     list-item side-effect action).
 *  2. Long-press → ModalBottomSheet with "Mark as recent" +
 *     "Cancel".
 *
 * This test is a **static scan** of `DecaySection.kt`. The pattern
 * is the same as
 * [com.kaavalan.note.ui.home.HomeScreenTest]: Robolectric 4.13's
 * launcher-activity resolution (PR #4736) makes
 * `createComposeRule()` / `createAndroidComposeRule<ComponentActivity>()`
 * fail in the unit-test classpath regardless of how the test
 * manifest is shaped. The static-scan approach is more durable
 * and catches the same class of regression (any refactor that
 * drops the swipe gesture wiring or the long-press sheet fails
 * the build).
 *
 * The 8 assertions below cover the structural contract that
 * `DecayRow` now satisfies:
 *  - The Mark recent TextButton is gone.
 *  - The ReachOutPill is the only right-side control.
 *  - The Card is `combinedClickable` with both `onClick` and
 *    `onLongClick`.
 *  - The long-press opens a ModalBottomSheet (state-gated).
 *  - The swipe gesture is wired via `pointerInput` +
 *    `detectHorizontalDragGestures`.
 *  - The swipe threshold is 96dp.
 *  - The swipe past the threshold fires `onMarkRecent`.
 *  - The "Mark as recent" text appears in the action sheet
 *    body (drives the action button).
 */
class DecayRowSwipeTest {

    private val decaySectionFile: File =
        File("src/main/java/com/kaavalan/note/ui/today/decay/DecaySection.kt").absoluteFile

    private fun source(): String = decaySectionFile.readText(Charsets.UTF_8)

    /**
     * Locate the body of the private `DecayRow` composable so the
     * assertions check the per-row rendering specifically, not
     * the whole `DecaySection.kt` (which includes the
     * `RedistributeDialog`'s TextButtons for "Confirm"/"Cancel" —
     * the latter would false-positive a TextButton scan if we
     * used file-level matching).
     */
    private fun decayRowBody(text: String): String? {
        val sig = "private fun DecayRow("
        val start = text.indexOf(sig)
        if (start < 0) return null
        // Walk forward through `{` braces to find the matching
        // close. This is a deliberately-simple scanner because
        // Kotlin's grammar doesn't allow `{` inside the parameter
        // list for a top-level `fun`.
        var depth = 0
        var i = start
        while (i < text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
            i++
        }
        return null
    }

    /**
     * v1.9.5 (1/8): the per-row `Mark recent` TextButton is gone.
     * The v1.9.4 layout rendered a `TextButton(onClick = onMarkRecent, ...)`
     * next to the ReachOutPill; the v1.9.5 layout moves the
     * affordance to swipe-right + long-press. The button text
     * "Mark recent" still appears (in the long-press action sheet
     * and the swipe background), so the test checks the
     * TextButton wiring, not the string.
     */
    @Test
    fun `v1_9_5 mark recent TextButton is removed from DecayRow`() {
        val text = source()
        val body = decayRowBody(text)
        assertNotNull(
            "Could not locate private fun DecayRow(...) in DecaySection.kt",
            body,
        )
        assertFalse(
            "DecayRow must NOT contain a `TextButton(onClick = onMarkRecent, ...)` " +
                "call site. The Mark recent affordance moved to swipe-right + long-press.",
            body!!.contains(Regex("""TextButton\s*\(\s*[^)]*onClick\s*=\s*onMarkRecent""")) ||
                body.contains("TextButton(\n") && body.contains("onClick = onMarkRecent"),
        )
    }

    /**
     * v1.9.5 (2/8): the Card is `combinedClickable` (replaces
     * the v1.9.4 `clickable` so we can wire `onLongClick` in
     * addition to `onClick`).
     */
    @Test
    fun `v1_9_5 DecayRow Card is combinedClickable with onLongClick`() {
        val text = source()
        val body = decayRowBody(text)!!
        assertTrue(
            "DecayRow Card must use combinedClickable (not clickable) so the " +
                "long-press can open the action sheet.",
            body.contains(".combinedClickable("),
        )
        assertTrue(
            "DecayRow combinedClickable must wire onLongClick = { showActionSheet = true }",
            Regex("""onLongClick\s*=\s*\{\s*showActionSheet\s*=\s*true\s*\}""").containsMatchIn(body),
        )
    }

    /**
     * v1.9.5 (3/8): the long-press opens a ModalBottomSheet.
     * The sheet is rendered when `showActionSheet == true`
     * (state-gated, not always-on).
     */
    @Test
    fun `v1_9_5 long-press opens a state-gated ModalBottomSheet`() {
        val text = source()
        val body = decayRowBody(text)!!
        assertTrue(
            "DecayRow must render a ModalBottomSheet for the long-press action.",
            body.contains("ModalBottomSheet("),
        )
        assertTrue(
            "The ModalBottomSheet must be gated on the showActionSheet state.",
            body.contains("if (showActionSheet) {"),
        )
        assertTrue(
            "The ModalBottomSheet must wire onDismissRequest = { showActionSheet = false }",
            body.contains("onDismissRequest = { showActionSheet = false }"),
        )
    }

    /**
     * v1.9.5 (4/8): the swipe-right gesture is wired through
     * `pointerInput` + `detectHorizontalDragGestures`. The
     * `onDragEnd` branch fires `onMarkRecent()` when the
     * offset exceeds the threshold.
     */
    @Test
    fun `v1_9_5 DecayRow has a swipe-right detectHorizontalDragGestures gesture`() {
        val text = source()
        val body = decayRowBody(text)!!
        assertTrue(
            "DecayRow must host a pointerInput { detectHorizontalDragGestures } block " +
                "for the swipe-right gesture.",
            body.contains(".pointerInput(") &&
                body.contains("detectHorizontalDragGestures("),
        )
        assertTrue(
            "The onDragEnd branch of detectHorizontalDragGestures must fire " +
                "onMarkRecent() once the offset exceeds the threshold.",
            Regex(
                """onDragEnd\s*=\s*\{[\s\S]{0,200}onMarkRecent\s*\(\s*\)""",
            ).containsMatchIn(body),
        )
    }

    /**
     * v1.9.5 (5/8): the swipe threshold is 96dp. Material 3
     * standard threshold for list-item side-effect actions.
     */
    @Test
    fun `v1_9_5 swipe threshold is 96dp`() {
        val text = source()
        val body = decayRowBody(text)!!
        // The plan spec'd the threshold as 96dp. The
        // implementation reads it from `SWIPE_THRESHOLD_DP` and
        // applies `toPx()` via LocalDensity. The constant must
        // be 96.
        assertTrue(
            "DecayRow must reference a 96dp swipe threshold (via SWIPE_THRESHOLD_DP or " +
                "an inline literal).",
            body.contains("SWIPE_THRESHOLD_DP") || body.contains("96.dp"),
        )
        // And the file-level constant must equal 96.
        assertTrue(
            "DecaySection.kt must declare `SWIPE_THRESHOLD_DP = 96` (or the inline " +
                "threshold must be 96dp).",
            text.contains("SWIPE_THRESHOLD_DP = 96"),
        )
    }

    /**
     * v1.9.5 (6/8): the `ReachOutPill(row.status)` is still the
     * only right-side control. The v1.9.4 `TextButton` +
     * `Spacer` siblings are gone. (The TextButton scan in test
     * #1 covers the same surface from the other side; this one
     * asserts the pill is still present.)
     */
    @Test
    fun `v1_9_5 ReachOutPill is the only right-side control in DecayRow`() {
        val text = source()
        val body = decayRowBody(text)!!
        assertTrue(
            "DecayRow must still render the ReachOutPill(row.status) as the right-side " +
                "status indicator.",
            body.contains("ReachOutPill(row.status)"),
        )
    }

    /**
     * v1.9.5 (7/8): the swipe background label is the
     * "Mark recent" text. The user sees this label as they drag
     * a card right; releasing past the 96dp threshold fires the
     * action.
     */
    @Test
    fun `v1_9_5 swipe background shows Mark recent label`() {
        val text = source()
        val body = decayRowBody(text)!!
        // The background Box with the tertiaryContainer colour
        // and the `decay_mark_recent` text. The string resource
        // is the same one the v1.9.4 TextButton used; we just
        // check the resource reference is in the swipe
        // background area, not the per-row TextButton.
        assertTrue(
            "DecayRow swipe background must reference R.string.decay_mark_recent " +
                "for the visible label.",
            body.contains("R.string.decay_mark_recent"),
        )
    }

    /**
     * v1.9.5 (8/8): the days-quiet text cap stays as a safety
     * net (`maxLines = 1` + ellipsis) so a 365d count doesn't
     * wrap and push the card back to v1.9.3 height. The
     * `maxLines = 1` is what makes the layout deterministic
     * regardless of the day count; the removal of the
     * TextButton gives the column the width to render
     * "haven't touched in 93 days" without ellipsis on a
     * 1080px device.
     */
    @Test
    fun `v1_9_5 days-quiet text keeps maxLines equals 1 with ellipsis safety net`() {
        val text = source()
        val body = decayRowBody(text)!!
        // The pluralStringResource(R.plurals.decay_days_quiet, ...) Text must
        // still have maxLines = 1 + TextOverflow.Ellipsis.
        // Find the text block by anchoring on the plurals
        // resource call and walking forward to the next
        // `Text(` call.
        val pluralsIdx = body.indexOf("R.plurals.decay_days_quiet")
        assertTrue(
            "DecayRow must render the days-quiet pluralised text.",
            pluralsIdx >= 0,
        )
        val tail = body.substring(pluralsIdx, minOf(pluralsIdx + 2000, body.length))
        assertTrue(
            "The days-quiet Text must keep maxLines = 1 as a safety net for very long " +
                "day counts (e.g. 365d).",
            Regex("""maxLines\s*=\s*1""").containsMatchIn(tail),
        )
        assertTrue(
            "The days-quiet Text must keep TextOverflow.Ellipsis as a safety net.",
            tail.contains("TextOverflow.Ellipsis"),
        )
    }
}
