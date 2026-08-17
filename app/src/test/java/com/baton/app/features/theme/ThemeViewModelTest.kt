package com.baton.app.features.theme

import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.preferences.BatonPreferences
import com.baton.app.data.preferences.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1.4 (v2.0): the theme switcher ViewModel.
 *
 * DataStore writes happen on `Dispatchers.IO` (its own
 * internal scope), so `advanceUntilIdle()` does NOT drain
 * them — we use a small `delay` to let the write flush.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `initial theme mode is System`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = BatonPreferences(ctx)
        val vm = ThemeViewModel(prefs)
        advanceUntilIdle()
        assertEquals(ThemeMode.System, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode to Light persists and a fresh read returns Light`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = BatonPreferences(ctx)
        val vm = ThemeViewModel(prefs)
        advanceUntilIdle()
        vm.setThemeMode(ThemeMode.Light)
        advanceUntilIdle()
        // The DataStore write is on its own IO scope; give it
        // a moment to flush.
        runBlocking { delay(300) }
        val read = prefs.themeMode.first()
        assertEquals(ThemeMode.Light, read)
    }

    @Test
    fun `setThemeMode to Dark persists and a fresh read returns Dark`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = BatonPreferences(ctx)
        val vm = ThemeViewModel(prefs)
        advanceUntilIdle()
        vm.setThemeMode(ThemeMode.Dark)
        advanceUntilIdle()
        runBlocking { delay(300) }
        val read = prefs.themeMode.first()
        assertEquals(ThemeMode.Dark, read)
    }
}
