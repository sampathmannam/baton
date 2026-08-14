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
 *
 * **v1.4.5:** added [sendOtp] / [verifyOtp] for passwordless
 * email OTP. The screen can now offer two paths: email+password
 * (the original flow) and email+OTP (the passwordless flow the
 * user picked to avoid typing a password). The user can flip
 * between the two via the "Use password instead" / "Use a code
 * instead" link at the bottom of the screen.
 *
 * Error states are mode-aware ([AuthUiState.OtpError] vs
 * [AuthUiState.PasswordError]) so the screen knows whether to
 * keep the user on the OTP panel or bounce back to the entry
 * panel — a flat Error would have lost the user's OTP context
 * on every code typo.
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
                .onSuccess { _state.value = AuthUiState.SignedIn }
                .onFailure { e -> _state.value = AuthUiState.PasswordError(SafeError.forUser(e, "Sign in failed.")) }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.signUp(email, password)
                .onSuccess { _state.value = AuthUiState.SignedIn }
                .onFailure { e -> _state.value = AuthUiState.PasswordError(SafeError.forUser(e, "Sign up failed.")) }
        }
    }

    /**
     * v1.4.5: send the OTP code to the user's email. On success,
     * the screen flips to [AuthUiState.CodeSent] and asks the user
     * to type the code. On failure, the screen shows an
     * [AuthUiState.OtpError] with the email preserved so the
     * "use password instead" link still works.
     */
    fun sendOtp(email: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.sendOtp(email)
                .onSuccess { _state.value = AuthUiState.CodeSent(codeEmail = email) }
                .onFailure { e ->
                    _state.value = AuthUiState.OtpError(
                        codeEmail = email,
                        message = SafeError.forUser(e, "Couldn't send the code. Check the email and try again."),
                    )
                }
        }
    }

    /**
     * v1.4.5: verify the OTP code the user typed. On success the
     * session is set by supabase-kt and persisted by the
     * [com.baton.app.data.auth.SupabaseEncryptedSessionManager];
     * the app's auth state observer (in MainActivity) navigates
     * the user out of the auth screen. On failure we stay on the
     * OTP panel via [AuthUiState.OtpError] so the user doesn't
     * have to re-send the code for a typo.
     */
    fun verifyOtp(email: String, token: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.verifyOtp(email, token)
                .onSuccess { _state.value = AuthUiState.SignedIn }
                .onFailure { e ->
                    _state.value = AuthUiState.OtpError(
                        codeEmail = email,
                        message = SafeError.forUser(e, "Code didn't work. Try again or resend."),
                    )
                }
        }
    }

    /**
     * v1.4.5: reset the state machine back to [AuthUiState.Idle].
     * Called from the screen when the user taps "Use password
     * instead" / "Use a code instead" to flip between the two
     * auth paths without losing any typed input.
     */
    fun reset() {
        _state.value = AuthUiState.Idle
    }
}
