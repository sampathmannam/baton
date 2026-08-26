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
 * v2.1.1 smoke test for bottom-nav tab switching.
 *
 * Flow:
 *  1. Home -> Today tab: Today screen renders one of its
 *     known section titles (Quiet a while / Worry box /
 *     Today's win / the empty state).
 *  2. Today -> Settings tab: Settings sheet opens
 *     (asserted by the "Settings" sheet title heading).
 *  3. Settings -> Home tab: Home re-renders ("People" title).
 */
@RunWith(AndroidJUnit4::class)
class BottomNavTabSwitchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun home_toToday_toSettings_toHome_roundTripsAllThreeTabs() {
        // Tolerate either entry point: fresh install (Skip
        // visible) or post-onboarding (Skip gone).
        runCatching { composeRule.onNodeWithText("Skip").performClick() }
        composeRule.waitForIdle()

        // Step 1: Home -> Today. The Today tab's icon has
        // contentDescription=R.string.tab_today="Today".
        // The Today screen TopAppBar title is the same
        // string, so the screen renders "Today" too.
        composeRule.onNodeWithContentDescription("Today").performClick()
        composeRule.waitForIdle()
        // The empty state is the strongest always-present
        // signal on a fresh install (R.string.today_empty_title).
        composeRule.onNodeWithText("Nothing on your plate.").assertIsDisplayed()

        // Step 2: Today -> Settings (the sheet opens).
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        // The Settings sheet renders a large "Settings"
        // header (R.string.settings_title) plus the section
        // labels. The header is unambiguous on this screen.
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        // Step 3: close the sheet, then Home -> Home.
        composeRule.onNodeWithContentDescription("Close settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("People").assertIsDisplayed()
    }
}
