package com.kaavalan.note.data.preferences

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
class KaavalanPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeKey = intPreferencesKey("theme_mode")
    private val onboardingKey = booleanPreferencesKey("has_seen_onboarding")
    // v1.9.6 (drive-verify polish #6): one-time
    // discoverability hint for the v1.9.5 swipe-right +
    // long-press gestures on the DecayRow. The chip renders
    // the first time the user opens Today with >= 3 quiet
    // contacts; tapping the chip (or marking a row recent)
    // flips this to true and the chip never appears again.
    // The `_v1` suffix is the standard re-key pattern so a
    // future copy / placement change can re-introduce the
    // hint by writing a new key.
    private val decayGestureHintShownKey = booleanPreferencesKey("decay_gesture_hint_shown_v1")
    // v1.9.11 (A9 audit fix): the version code of the last
    // changelog screen the user has dismissed. The screen
    // shows on the next launch if the current version code
    // (BuildConfig.VERSION_CODE) is greater than this value.
    // Default 0 — every v1.9.10- user sees the v1.9.11 screen
    // once.
    private val lastSeenChangelogVersionKey = intPreferencesKey("last_seen_changelog_version_v1")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val ord = prefs[themeKey] ?: ThemeMode.System.ordinal
        ThemeMode.entries.getOrElse(ord) { ThemeMode.System }
    }

    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardingKey] ?: false
    }

    /**
     * v1.9.6: one-time discoverability hint for the new
     * DecayRow gestures. `false` on a fresh install; the
     * DecaySection flips it to `true` when the user taps the
     * hint chip (or marks a row recent via swipe / long-
     * press). The flag survives process death and reinstall
     * of the same APK version.
     */
    val decayGestureHintShown: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[decayGestureHintShownKey] ?: false
    }

    /**
     * v1.9.11 (A9 audit fix): the version code of the last
     * changelog screen the user dismissed. Returns 0 on a
     * fresh install (no changelog shown yet).
     */
    val lastSeenChangelogVersion: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[lastSeenChangelogVersionKey] ?: 0
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.ordinal }
    }

    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[onboardingKey] = true }
    }

    suspend fun setDecayGestureHintShown() {
        context.dataStore.edit { it[decayGestureHintShownKey] = true }
    }

    /**
     * v1.9.11 (A9 audit fix): mark [versionCode] as the last
     * changelog version the user has seen. Next launch, if
     * `BuildConfig.VERSION_CODE > versionCode`, the
     * ChangelogScreen will show again.
     */
    suspend fun setChangelogSeenAtVersion(versionCode: Int) {
        context.dataStore.edit { it[lastSeenChangelogVersionKey] = versionCode }
    }
}
