package com.kaavalan.note.ui.home

import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.person.Person

sealed interface HomeUiState {
    data object Empty : HomeUiState
    data object Loading : HomeUiState

    data class Loaded(
        val persons: List<Person>,
        val openCountByPersonId: Map<String, Int> = emptyMap(),
        val stalePersonIds: Set<String> = emptySet(),
        val outgoingOpen: List<Instruction> = emptyList(),
        val incomingOpen: List<Instruction> = emptyList(),
        val popularTags: List<TagCount> = emptyList(),
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

data class TagCount(
    val tagId: String,
    val name: String,
    val count: Int,
)
