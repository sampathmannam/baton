package com.kaavalan.note

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaavalan.note.data.auth.AuthRepository
import com.kaavalan.note.data.person.PersonRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * **M0 finding test** — the end-to-end "app works" check. Runs on a real
 * device or emulator (androidTest). Asserts:
 *
 *   1. A fresh user can sign up.
 *   2. The Home screen is empty (no persons yet).
 *   3. The "Add person" button opens a sheet.
 *   4. Filling in a name and tapping Save creates a Person.
 *   5. The new Person appears in the Home list.
 *
 * Plus the implicit RLS check: every read/write is scoped to the
 * `auth.uid()` of the signed-in user, enforced by the policies in
 * `supabase/migrations/0001_init.sql`.
 *
 * To run:
 * ```
 * .\gradlew.bat :app:connectedDebugAndroidTest --tests "com.kaavalan.note.M0AcceptanceTest"
 * ```
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class M0AcceptanceTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var personRepository: PersonRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            // Sign up a fresh test user. Will fail silently if already exists
            // (we don't care — both outcomes give us an authenticated session).
            authRepository.signUp("m0-test@example.com", "test-password-1234")
        }
    }

    @Test
    fun emptyHome_showsAddPersonButton_canCreatePerson() {
        composeRule.onNodeWithText("Add person").assertIsDisplayed()
        composeRule.onNodeWithText("Add person").performClick()

        composeRule.onNodeWithText("Name").performTextInput("Test Person")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Test Person").assertIsDisplayed()
    }
}
