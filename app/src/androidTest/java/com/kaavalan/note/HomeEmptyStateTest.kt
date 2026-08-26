package com.kaavalan.note

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v2.1.1 smoke test for the Home empty state.
 *
 * Flow:
 *  1. Land on Home. The DataStore `hasSeenOnboarding` flag
 *     is shared across tests in this package, so the first
 *     test to run sees the OnboardingScreen and the rest
 *     land on Home. We tolerate both: if "Skip" is present,
 *     tap it; otherwise continue.
 *  2. The Quick note bar and "Import from contacts" button
 *     are visible.
 *  3. Tapping the Quick note bar opens the capture sheet,
 *     which renders a TextField for note input.
 */
@RunWith(AndroidJUnit4::class)
class HomeEmptyStateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun home_quickNoteBar_opensCaptureSheet() {
        // Tolerate either entry point: fresh install (Skip
        // visible) or post-onboarding (Skip gone).
        runCatching { composeRule.onNodeWithText("Skip").performClick() }
        composeRule.waitForIdle()

        // The Quick note bar is the persistent bottom-of-Home
        // Surface (R.string.note_bar_hint = "Quick note").
        composeRule.onNodeWithText("Quick note").assertIsDisplayed()
        composeRule.onNodeWithText("Import from contacts").assertIsDisplayed()

        // Open the capture sheet.
        composeRule.onNodeWithText("Quick note").performClick()
        composeRule.waitForIdle()

        // The capture sheet's primary input is an
        // OutlinedTextField labelled "Note"
        // (R.string.capture_sheet_text_label). The label is
        // the strongest match because it is the TextField's
        // label slot (not a placeholder, not a heading).
        composeRule.onNodeWithText("Note").assertIsDisplayed()
    }
}
