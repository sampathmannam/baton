package com.kaavalan.note.features.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.preferences.BatonPreferences
import com.kaavalan.note.data.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tier 1.4 (v2.0): theme switcher ViewModel.
 *
 * The `themeMode` flow is the single source of truth. The
 * [BatonPreferences] DataStore is the persistent backing
 * store. The actual theme is applied at the root composable
 * in [com.kaavalan.note.MainActivity.setContent] (we read the
 * same flow and pick `BatonLightScheme` / `BatonDarkScheme`
 * accordingly).
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferences: BatonPreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.System,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }
}
