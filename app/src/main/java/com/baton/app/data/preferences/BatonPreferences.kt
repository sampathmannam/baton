package com.baton.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1.2 (v2.0) + Tier 1.4 (v2.0): the per-device
 * DataStore for the onboarding-done flag and the theme
 * preference. Stored in the app's private DataStore
 * (`baton-prefs.preferences_pb` in `filesDir/datastore/`).
 *
 * The DataStore is intentionally tiny — only two keys for
 * now. `themeMode` is the ordinal of the [ThemeMode] enum
 * (`0 = System, 1 = Light, 2 = Dark`); the user-facing
 * default is `System` (the device setting), not `Light` or
 * `Dark`. `hasSeenOnboarding` defaults to `false` so the
 * first-run sheet shows on a fresh install.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "baton-prefs",
)

enum class ThemeMode { System, Light, Dark }

@Singleton
class BatonPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeKey = intPreferencesKey("theme_mode")
    private val onboardingKey = booleanPreferencesKey("has_seen_onboarding")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val ord = prefs[themeKey] ?: ThemeMode.System.ordinal
        ThemeMode.entries.getOrElse(ord) { ThemeMode.System }
    }

    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardingKey] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.ordinal }
    }

    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[onboardingKey] = true }
    }
}
