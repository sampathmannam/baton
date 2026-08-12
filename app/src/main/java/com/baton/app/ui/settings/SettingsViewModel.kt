package com.baton.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.local.AppInitializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M3-T4: Settings view-model. The single action for now is sign-out.
 *
 * **Sign-out order.** [AppInitializer.runOnSignOut] MUST run before
 * [AuthRepository.signOut] returns, otherwise the in-flight Compose
 * tree (HomeScreen + SettingsSheet) still has Hilt references to
 * the database and would observe a "database is not a database"
 * error from SQLCipher. We run them in the right order here:
 * wipe first (synchronous, cheap), then drop the session (which
 * triggers the observer).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appInitializer: AppInitializer,
) : ViewModel() {

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    fun signOut() {
        if (_signingOut.value) return
        _signingOut.value = true
        viewModelScope.launch {
            runCatching { appInitializer.runOnSignOut() }
            runCatching { authRepository.signOut() }
            // The session observer in MainActivity will transition
            // to Unauthenticated and the Compose tree will tear
            // down the HomeScreen + SettingsSheet automatically.
            // We don't need to dismiss the sheet ourselves.
            _signingOut.value = false
        }
    }
}
