package com.kaavalan.note.ui.today.decay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.6 (drive-verify polish #6): the DecayRow gesture
 * discoverability hint, unit-tested at the pure-function
 * boundary.
 *
 * The v1.9.5 release added swipe-right + long-press on
 * `DecayRow` with no on-screen affordance to tell the user
 * the gestures exist. The v1.9.6 fix renders a small
 * `AssistChip` above the filter row the first time the
 * user opens Today with >= 3 quiet contacts; the chip
 * disappears forever when the user dismisses it (or
 * marks a row recent).
 *
 * The decision logic lives in
 * [DecayViewModel.shouldShowGestureHint] (a pure
 * top-level function on the companion) so the contract
 * is testable without standing up a Robolectric /
 * Compose runtime. The Composable reads the result via
 * the [DecayViewModel.gestureHintVisible] StateFlow;
 * the test calls the function directly with explicit
 * inputs.
 */
class DecayViewModelHintTest {

    /**
     * v1.9.6 (1/N): the hint is visible only when the
     * quiet-contact count meets the 3-row floor AND the
     * `decay_gesture_hint_shown_v1` preference is still
     * `false`.
     */
    @Test
    fun `hint is visible when rows is at or above 3 and pref is false`() {
        assertTrue(DecayViewModel.shouldShowGestureHint(rowCount = 3, prefShown = false))
        assertTrue(DecayViewModel.shouldShowGestureHint(rowCount = 4, prefShown = false))
        assertTrue(DecayViewModel.shouldShowGestureHint(rowCount = 10, prefShown = false))
    }

    /**
     * v1.9.6 (2/N): the hint is hidden when the user has
     * already dismissed it (re-install + same APK version
     * still re-reads `true` from DataStore).
     */
    @Test
    fun `hint is hidden when pref is true regardless of row count`() {
        assertFalse(DecayViewModel.shouldShowGestureHint(rowCount = 0, prefShown = true))
        assertFalse(DecayViewModel.shouldShowGestureHint(rowCount = 2, prefShown = true))
        assertFalse(DecayViewModel.shouldShowGestureHint(rowCount = 3, prefShown = true))
        assertFalse(DecayViewModel.shouldShowGestureHint(rowCount = 100, prefShown = true))
    }

    /**
     * v1.9.6 (3/N): the hint is hidden when the quiet
     * pile is below the 3-row floor. We don't want to
     * surface the swipe gesture on a near-empty list —
     * the user can't swipe what they can't see.
     */
    @Test
    fun `hint is hidden when row count is below 3 even if pref is false`() {
        assertFalse(DecayViewModel.shouldShowGestureHint(rowCount = 0, prefShown = false))
        assertFalse(DecayViewModel.shouldShowGestureHint(rowCount = 1, prefShown = false))
        assertFalse(DecayViewModel.shouldShowGestureHint(rowCount = 2, prefShown = false))
    }

    /**
     * v1.9.6 (4/N): the `HINT_MIN_ROWS` floor is the
     * public contract — `3`. Locking the constant here
     * means any future change to the floor (e.g. to 5)
     * must update both the production code and this
     * assertion, and reviewers will see the change
     * together.
     */
    @Test
    fun `HINT_MIN_ROWS is 3`() {
        assertTrue(
            "The DecayRow discoverability hint must surface when the quiet-contact " +
                "count is >= 3 (a 2-row or smaller pile is not worth pointing the user at).",
            DecayViewModel.HINT_MIN_ROWS == 3,
        )
    }
}
