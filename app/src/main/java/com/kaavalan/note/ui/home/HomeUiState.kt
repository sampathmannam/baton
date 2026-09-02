package com.kaavalan.note.ui.home

import com.kaavalan.note.data.groups.GroupLabel
import com.kaavalan.note.data.person.PersonProfile

sealed interface HomeUiState {
    data object Empty : HomeUiState
    data object Loading : HomeUiState

    data class Loaded(
        val persons: List<PersonProfile>,
        val groupLabels: List<GroupLabel>,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}
