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
 * v2.1.1 smoke test for the AddPerson flow.
 *
 * Flow:
 *  1. Open the AddPersonSheet via the Home FAB.
 *  2. Type a name into the first TextField (R.string.person_name).
 *  3. Tap Save. The Home screen re-renders with the new row.
 */
@RunWith(AndroidJUnit4::class)
class AddPersonFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun home_fab_openAddPerson_saveNewPerson() {
        // Tolerate either entry point: fresh install (Skip
        // visible) or post-onboarding (Skip gone). The
        // DataStore flag is shared across tests in this
        // package; once OnboardingFlowTest has run, the
        // Skip button disappears.
        runCatching { composeRule.onNodeWithText("Skip").performClick() }
        composeRule.waitForIdle()

        // The Home FAB has contentDescription="Add person"
        // (R.string.home_add_person). On the empty state, the
        // same text is also rendered as a Button label, so we
        // target the FAB by its icon contentDescription to
        // disambiguate.
        composeRule.onNodeWithContentDescription("Add person")
            .performClick()
        composeRule.waitForIdle()

        // The first OutlinedTextField is the Name field.
        // Save is disabled until Name is non-blank.
        composeRule.onNodeWithText("Name").performTextInput("QA Person")
        composeRule.waitForIdle()

        // The AddPersonSheet's primary Button reads "Save"
        // (R.string.person_save). The capture sheet also has
        // a "Save" button; here we are in the AddPersonSheet,
        // so the only "Save" on screen is the one we want.
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        // Home re-renders with the new person row.
        composeRule.onNodeWithText("QA Person").assertIsDisplayed()
    }
}
