package com.baton.app.ui.auth

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Submitting : AuthUiState
    data class Error(val message: String) : AuthUiState
}
