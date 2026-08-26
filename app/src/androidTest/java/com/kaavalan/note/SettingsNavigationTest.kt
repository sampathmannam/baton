package com.kaavalan.note

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v2.1.1 smoke test for the Settings bottom sheet.
 *
 * Flow:
 *  1. Open Settings via the bottom-nav Settings tab.
 *  2. The sheet renders the section labels
 *     (Privacy, Data, About) as section headers.
 *  3. Close the sheet via the Close icon button.
 *  4. Home is restored.
 */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNav_settings_opensSheet_showsSections_andCloseReturnsToHome() {
        // Tolerate either entry point: fresh install (Skip
        // visible) or post-onboarding (Skip gone).
        runCatching { composeRule.onNodeWithText("Skip").performClick() }
        composeRule.waitForIdle()

        // Step 1: tap the Settings tab in the bottom nav.
        // The tab's icon contentDescription is the tab label
        // (R.string.tab_settings = "Settings").
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        // Step 2: the sheet renders three section headers.
        // They are Text widgets so we assert on their text.
        composeRule.onNodeWithText("Privacy").assertIsDisplayed()
        composeRule.onNodeWithText("Data").assertIsDisplayed()
        composeRule.onNodeWithText("About").assertIsDisplayed()

        // Step 3: close via the X icon (contentDescription
        // is R.string.settings_close = "Close settings").
        composeRule.onNodeWithContentDescription("Close settings").performClick()
        composeRule.waitForIdle()

        // Step 4: Home is back; the TopAppBar title
        // R.string.home_title = "People" is visible.
        composeRule.onNodeWithText("People").assertIsDisplayed()
    }
}
