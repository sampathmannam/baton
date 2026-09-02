package com.kaavalan.note.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.InstructionDraft
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.RoomInstructionRepository
import com.kaavalan.note.data.instructions.Source
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.person.PersonProfile
import com.kaavalan.note.data.person.toDomain
import com.kaavalan.note.data.person.toProfile
import com.kaavalan.note.ui.util.SafeError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    personDao: PersonDao,
    private val instructionRepository: RoomInstructionRepository,
) : ViewModel() {

    private val personId: String = savedStateHandle.get<String>(ARG_PERSON_ID)
        ?: error("$ARG_PERSON_ID missing from nav args")

    val state: StateFlow<PersonDetailUiState> = personDao.observeById(personId)
        .flatMapLatest { personEntity ->
            if (personEntity == null) {
                flowOf(PersonDetailUiState.NotFound)
            } else {
                instructionRepository.observeForPerson(personId).map { instructions ->
                    val sections = partitionInstructions(instructions)
                    PersonDetailUiState.Loaded(
                        person = personEntity.toDomain().toProfile(),
                        activeInstructions = sections.active,
                        completedInstructions = sections.completed,
                    )
                }
            }
        }
        .catch { error ->
            emit(PersonDetailUiState.Error(SafeError.forUser(error, "Could not load this person.")))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PersonDetailUiState.Loading,
        )

    fun markDone(instructionId: String) {
        viewModelScope.launch {
            runCatching { instructionRepository.markDone(instructionId, Instant.now().toEpochMilli()) }
        }
    }

    fun reopen(instructionId: String) {
        viewModelScope.launch {
            runCatching { instructionRepository.reopen(instructionId) }
        }
    }

    fun createInstructionForThisPerson(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                instructionRepository.create(
                    InstructionDraft(
                        rawText = trimmed,
                        actionSummary = trimmed,
                        personId = personId,
                        responsiblePersonId = personId,
                        status = Status.TO_DO,
                        priority = Priority.NORMAL,
                        source = Source.TEXT,
                    ),
                )
            }
        }
    }

    companion object {
        const val ARG_PERSON_ID = "personId"
    }
}

data class PersonInstructionSections(
    val active: List<Instruction>,
    val completed: List<Instruction>,
)

internal fun partitionInstructions(instructions: List<Instruction>): PersonInstructionSections =
    PersonInstructionSections(
        active = instructions.filter { it.status != Status.DONE },
        completed = instructions.filter { it.status == Status.DONE },
    )

sealed interface PersonDetailUiState {
    data object Loading : PersonDetailUiState
    data object NotFound : PersonDetailUiState
    data class Loaded(
        val person: PersonProfile,
        val activeInstructions: List<Instruction>,
        val completedInstructions: List<Instruction>,
    ) : PersonDetailUiState
    data class Error(val message: String) : PersonDetailUiState
}
