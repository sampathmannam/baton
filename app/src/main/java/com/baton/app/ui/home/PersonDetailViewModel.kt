package com.baton.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.RoomInstructionRepository
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.person.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M3-T6: ViewModel for [PersonDetailScreen]. Reads the
 * `personId` from the nav arg (`SavedStateHandle`) and combines
 * the person row with the per-person instruction timeline.
 *
 * **Reactive.** Both the person row and the instructions are
 * observed as Flows so the screen updates on every local write
 * (e.g. `setSensitive`) AND on every Realtime-driven refresh.
 * The screen never has to call a `refresh()` itself. The same
 * Room-mirror contract as the HomeScreen applies.
 *
 * **v1.1.1 root-cause fix:** the person row was previously a
 * one-shot `getById` read in `init` — the `MutableStateFlow`
 * never re-emitted when the local row changed, so the detail
 * screen's "Mark as sensitive" button stayed stale after a
 * tap. We now use [PersonDao.observeById] which emits on every
 * Room update to the row.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    personDao: PersonDao,
    private val instructionDao: InstructionDao,
    private val roomInstructionRepository: RoomInstructionRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {

    /**
     * M3-T6: read the `personId` nav arg. The [PersonDetailScreen]
     * composable doesn't pass it explicitly; Hilt's
     * `SavedStateHandle` carries the nav-arg through the ViewModel
     * constructor.
     */
    private val personId: String = savedStateHandle.get<String>(ARG_PERSON_ID)
        ?: error("$ARG_PERSON_ID missing from nav args")

    private val _personState = personDao.observeById(personId)

    /**
     * M3-T6: when the person is loaded, observe their instruction
     * timeline. flatMapLatest swaps the inner Flow when the
     * outer (person) changes — only one inner Flow is active at
     * a time, so we don't leak observers.
     */
    val state: StateFlow<PersonDetailUiState> = _personState
        .flatMapLatest { person ->
            if (person == null) {
                flowOf(PersonDetailUiState.Loading)
            } else {
                instructionDao.observeForPerson(person.id).combine(flowOf(person)) { ins, p ->
                    PersonDetailUiState.Loaded(
                        person = p.toDomain(),
                        instructions = ins.map { it.toDomain() },
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PersonDetailUiState.Loading,
        )

    private fun com.baton.app.data.local.entities.PersonEntity.toDomain() =
        com.baton.app.data.person.Person(
            id = id,
            name = name,
            designation = designation,
            station = station,
            phone = phone,
            updatedAt = updatedAt,
            isSensitive = isSensitive,
        )

    private fun com.baton.app.data.local.entities.InstructionEntity.toDomain(): Instruction =
        Instruction(
            id = id,
            personId = personId,
            direction = runCatching { com.baton.app.data.instructions.Direction.valueOf(direction) }
                .getOrDefault(com.baton.app.data.instructions.Direction.OUTGOING),
            status = runCatching { com.baton.app.data.instructions.Status.valueOf(status) }
                .getOrDefault(com.baton.app.data.instructions.Status.OPEN),
            source = runCatching { com.baton.app.data.instructions.Source.valueOf(source) }
                .getOrDefault(com.baton.app.data.instructions.Source.TEXT),
            priority = runCatching { com.baton.app.data.instructions.Priority.valueOf(priority) }
                .getOrDefault(com.baton.app.data.instructions.Priority.NORMAL),
            title = title,
            rawText = rawText,
            dueAt = dueAt,
            capturedAt = capturedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isSensitive = isSensitive,
            completedAt = completedAt,
            droppedReason = droppedReason,
        )

    /**
     * v1.1: state-transition handlers. Each one writes through the
     * Room repository (which also enqueues a PENDING_UPDATE for the
     * sync outbox) and Room re-emits the timeline Flow, so the UI
     * sees the change synchronously.
     */
    fun markDone(instructionId: String) {
        viewModelScope.launch {
            runCatching { roomInstructionRepository.markDone(instructionId) }
        }
    }

    fun markDropped(instructionId: String, reason: String?) {
        viewModelScope.launch {
            runCatching { roomInstructionRepository.markDropped(instructionId, reason) }
        }
    }

    fun reopen(instructionId: String) {
        viewModelScope.launch {
            runCatching { roomInstructionRepository.reopen(instructionId) }
        }
    }

    fun setInstructionSensitive(instructionId: String, sensitive: Boolean) {
        viewModelScope.launch {
            runCatching { roomInstructionRepository.setSensitive(instructionId, sensitive) }
        }
    }

    fun setPersonSensitive(personId: String, sensitive: Boolean) {
        viewModelScope.launch {
            runCatching { personRepository.setSensitive(personId, sensitive) }
        }
    }

    companion object {
        const val ARG_PERSON_ID = "personId"
    }
}

sealed interface PersonDetailUiState {
    data object Loading : PersonDetailUiState
    data class Loaded(
        val person: com.baton.app.data.person.Person,
        val instructions: List<com.baton.app.data.instructions.Instruction>,
    ) : PersonDetailUiState
}
