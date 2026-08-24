package com.baton.app.ui.home

import com.baton.app.data.instructions.Instruction
import com.baton.app.data.person.Person

sealed interface HomeUiState {
    data object Empty : HomeUiState
    data object Loading : HomeUiState

    /**
     * v2.0 (Hierarchy): the loaded state carries the "outbox" and
     * "inbox" sections and the popular #tag chips in addition to
     * the pre-v2.0 person list.
     */
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

/** v2.0 (Hierarchy): a single popular #tag chip. */
data class TagCount(
    val tagId: String,
    val name: String,
    val count: Int,
)
