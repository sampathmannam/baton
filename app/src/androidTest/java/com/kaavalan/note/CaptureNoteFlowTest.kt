package com.kaavalan.note

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v2.1.1 smoke test for the capture-note happy path.
 *
 * Flow:
 *  1. Add a person (the capture-sheet Save button is gated
 *     on hasPeople — see CaptureSheet.PrimaryAction).
 *  2. Open the capture sheet from the Quick note bar.
 *  3. Type a unique note title into the labelled TextField.
 *  4. Tap Save.
 *  5. The new instruction lands in the Outbox (the
 *     R.string.hierarchy_section_outbox section of the
 *     home list) — assert the title is visible.
 */
@RunWith(AndroidJUnit4::class)
class CaptureNoteFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun addPerson_openCapture_typeAndSave_persistsNoteInOutbox() {
        // Tolerate either entry point: fresh install (Skip
        // visible) or post-onboarding (Skip gone).
        runCatching { composeRule.onNodeWithText("Skip").performClick() }
        composeRule.waitForIdle()

        // Step 1: add a person so the capture sheet's Save
        // button is enabled (it is gated on hasPeople).
        composeRule.onNodeWithContentDescription("Add person").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Name").performTextInput("CaptureTest Person")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        // Step 2: open the capture sheet via the Quick note bar.
        composeRule.onNodeWithText("Quick note").assertIsDisplayed()
        composeRule.onNodeWithText("Quick note").performClick()
        composeRule.waitForIdle()

        // Step 3: type a unique title into the OutlinedTextField
        // labelled "Note" (R.string.capture_sheet_text_label).
        // The capture sheet auto-saves the first line as the
        // instruction title, the full text as rawText.
        val uniqueNote = "QA smoke note 1742"
        composeRule.onNodeWithText("Note").performTextInput(uniqueNote)
        composeRule.waitForIdle()

        // Step 4: tap the capture sheet's Save button.
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        // Step 5: the new instruction is rendered in the
        // Outbox section (R.string.hierarchy_section_outbox).
        // The Outbox only appears when there is at least one
        // outgoing instruction; the unique title is the
        // strongest signal that the save persisted.
        composeRule.onNodeWithText("Outbox").assertIsDisplayed()
        composeRule.onNodeWithText(uniqueNote).assertIsDisplayed()
    }
}
