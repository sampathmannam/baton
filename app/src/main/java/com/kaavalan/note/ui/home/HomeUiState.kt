package com.kaavalan.note.ui.home

import com.kaavalan.note.data.person.Person

sealed interface HomeUiState {
    data object Empty : HomeUiState
    data object Loading : HomeUiState

    /**
     * M3-T5: the loaded state now carries a per-person open
     * instruction count. The People list shows the count as a
     * badge to the right of the name. Counts are zero for persons
     * who have no open instructions (the map's `getOrDefault`).
     */
    data class Loaded(
        val persons: List<Person>,
        val openCountByPersonId: Map<String, Int> = emptyMap(),
        val stalePersonIds: Set<String> = emptySet(),
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}
