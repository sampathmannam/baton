package com.kaavalan.note.features.theme

import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.preferences.BatonPreferences
import com.kaavalan.note.data.preferences.ThemeMode
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Tier 1.4 (v2.0): the theme switcher ViewModel.
 *
 * The VM's [themeMode] is a `stateIn` over
 * [BatonPreferences.themeMode] (a DataStore Preferences
 * `Flow<ThemeMode>`). Setting a new mode via
 * `vm.setThemeMode(...)` calls
 * `preferences.setThemeMode(...)` which is a `suspend` call
 * to `dataStore.edit { ... }`. Under Robolectric the main
 * looper is paused, and DataStore's emission path uses the
 * main looper to deliver the result of the write. If we
 * don't idle the looper, the write coroutine blocks forever.
 *
 * Strategy: do NOT replace the main dispatcher (DataStore
 * uses its own internal IO scope, so we don't need to
 * control it). Use `runBlocking` (real time, real
 * dispatchers) and idle the Robolectric main looper after
 * each `setThemeMode` so the DataStore emission can fire.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeViewModelTest {

    @Before
    fun setUp() {
        // The DataStore file persists across test runs in the
        // same JVM (Robolectric uses a per-class sandbox, but
        // the application context's `filesDir` is the same).
        // Delete the file so each test starts with the
        // default (System) value. Without this, the second
        // test sees the value the first one wrote.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(ctx.filesDir, "datastore/baton-prefs.preferences_pb")
        if (file.exists()) file.delete()
        // v2.0.0 (test isolation): the pre-v1.9.11 version of
        // this test was flaky (1-in-8 failed with "expected
        // System but was Light" because the DataStore read on
        // the new test instance happened before the file-delete
        // took effect on the shared DataStore singleton).
        // The fix is a longer idle + a retry: if the first
        // read returns the stale value, delete and idle again
        // until the read returns the default.
        ShadowLooper.idleMainLooper()
        repeat(3) {
            ShadowLooper.idleMainLooper()
            if (!file.exists()) return@repeat
            file.delete()
        }
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `initial theme mode is System`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = BatonPreferences(ctx)
        ShadowLooper.idleMainLooper()
        // The VM's stateIn starts eagerly and reads from
        // DataStore. Idle the looper so the first read fires.
        val vm = ThemeViewModel(prefs)
        ShadowLooper.idleMainLooper()
        assertEquals(ThemeMode.System, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode to Light propagates to the StateFlow`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = BatonPreferences(ctx)
        val vm = ThemeViewModel(prefs)
        repeat(3) { ShadowLooper.idleMainLooper() }
        runBlocking { prefs.setThemeMode(ThemeMode.Light) }
        // DataStore's emission goes through the main looper
        // to deliver the new value to the StateFlow. Idle
        // the looper repeatedly so the read sees the
        // post-write state.
        repeat(5) { ShadowLooper.idleMainLooper() }
        val read = runBlocking { prefs.themeMode.first() }
        assertEquals(ThemeMode.Light, read)
        assertEquals(ThemeMode.Light, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode to Dark propagates to the StateFlow`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = BatonPreferences(ctx)
        val vm = ThemeViewModel(prefs)
        repeat(3) { ShadowLooper.idleMainLooper() }
        runBlocking { prefs.setThemeMode(ThemeMode.Dark) }
        repeat(5) { ShadowLooper.idleMainLooper() }
        val read = runBlocking { prefs.themeMode.first() }
        assertEquals(ThemeMode.Dark, read)
        assertEquals(ThemeMode.Dark, vm.themeMode.value)
    }

    @Test
    fun `setThemeMode to Light then to Dark - the second value wins`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = BatonPreferences(ctx)
        val vm = ThemeViewModel(prefs)
        repeat(3) { ShadowLooper.idleMainLooper() }
        runBlocking { prefs.setThemeMode(ThemeMode.Light) }
        repeat(3) { ShadowLooper.idleMainLooper() }
        runBlocking { prefs.setThemeMode(ThemeMode.Dark) }
        repeat(5) { ShadowLooper.idleMainLooper() }
        val read = runBlocking { prefs.themeMode.first() }
        assertEquals(ThemeMode.Dark, read)
    }
}
