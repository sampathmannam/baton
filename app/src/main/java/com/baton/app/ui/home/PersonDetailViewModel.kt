package com.baton.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.entities.PersonEntity
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * **Reactive.** The timeline updates on every local write AND on
 * every Realtime-driven refresh; the screen never has to call a
 * `refresh()` itself. The same Room-mirror contract as the
 * HomeScreen applies.
 *
 * **One-shot load.** The person entity is loaded once via
 * [PersonDao.getById] (a `suspend fun`, not a Flow — the person
 * row only changes via the existing Persons Flow, and a reload
 * here would re-trigger the same animation on every edit). The
 * instructions are observed as a Flow.
 */
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    personDao: PersonDao,
    private val instructionDao: InstructionDao,
) : ViewModel() {

    /**
     * M3-T6: read the `personId` nav arg. The [PersonDetailScreen]
     * composable doesn't pass it explicitly; Hilt's
     * `SavedStateHandle` carries the nav-arg through the ViewModel
     * constructor.
     */
    private val personId: String = savedStateHandle.get<String>(ARG_PERSON_ID)
        ?: error("$ARG_PERSON_ID missing from nav args")

    private val _person = MutableStateFlow<PersonEntity?>(null)
    private val _personState = _person.asStateFlow()

    init {
        viewModelScope.launch {
            _person.value = personDao.getById(personId)
        }
    }

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
        )

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
