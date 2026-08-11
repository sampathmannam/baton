package com.baton.app.ui.home

import com.baton.app.data.person.Person

sealed interface HomeUiState {
    data object Empty : HomeUiState
    data object Loading : HomeUiState
    data class Loaded(val persons: List<Person>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
