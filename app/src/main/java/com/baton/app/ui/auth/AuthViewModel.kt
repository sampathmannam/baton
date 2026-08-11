package com.baton.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Sign in failed") }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Submitting
            authRepository.signUp(email, password)
                .onSuccess { _state.value = AuthUiState.Idle }
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Sign up failed") }
        }
    }
}
