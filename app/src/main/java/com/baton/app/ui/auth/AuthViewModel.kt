package com.baton.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.auth.AuthRepository
import com.baton.app.ui.util.SafeError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sign-in / sign-up state machine for the [AuthScreen].
 *
 * **v1.2 root-cause fix (BUG-AUTH-001):** the v1.1 path used
 * `e.message` directly, which leaks the supabase-kt exception
 * message (full request URL, `X-Client-Info: supabase-kt/3.1.1`,
 * and other headers) onto the sign-in screen — a security
 * smell on a shared device and a phishing surface (a real
 * supabase URL on a fake sign-in page is convincing). We now
 * map exceptions to a fixed set of user-safe strings via
 * [SafeError] (shared in `ui/util`).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.signIn(email, password)
                .onSuccess { _state.value = AuthUiState.Idle }
                .onFailure { e -> _state.value = AuthUiState.Error(SafeError.forUser(e, "Sign in failed.")) }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.signUp(email, password)
                .onSuccess { _state.value = AuthUiState.Idle }
                .onFailure { e -> _state.value = AuthUiState.Error(SafeError.forUser(e, "Sign up failed.")) }
        }
    }
}
