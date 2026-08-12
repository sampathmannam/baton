package com.baton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.instructions.RoomInstructionRepository
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.RoomPersonRepository
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: RoomPersonRepository,
    private val instructionDao: InstructionDao,
    private val roomInstructionRepository: RoomInstructionRepository,
    private val supabaseInstructionRepository: SupabaseInstructionRepository,
    private val tagRepository: RoomTagRepository,
    realtimeSync: RealtimeSync,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // M3-T5: combine persons + open-instruction counts in a
        // single Flow so the UI sees a consistent snapshot. The
        // `combine` operator re-emits on either side's change, so
        // an instruction save or a person add both trigger a
        // re-render with the updated badge count.
        viewModelScope.launch {
            combine(
                personRepository.observeAll(),
                instructionDao.observeOpenCountByPerson(),
            ) { persons, counts ->
                // M3-T5: build a map of personId -> open count, defaulting
                // missing persons to 0. The DAO only emits rows for
                // persons with at least one open instruction; persons
                // with no open work don't show up in the count Flow.
                // Defaulting here means the PersonRow composable can
                // always call `map[id]` and never see null.
                val map = persons.associate { it.id to 0 } + counts.associate { it.personId to it.cnt }
                if (persons.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Loaded(persons = persons, openCountByPersonId = map)
                }
            }
                .catch { e -> _state.value = HomeUiState.Error(e.message ?: "Unknown error") }
                .collect { _state.value = it }
        }
        // M2-T6: kick off an initial pull on first VM creation.
        // On a cold start with an empty Room, the Flow above emits
        // `Empty` immediately; this refreshFromNetwork fills the
        // cache from Supabase. On subsequent launches Room is
        // already warm; the refresh is a no-op (upsertAll replaces
        // with the same rows).
        refreshFromNetwork()
        // M3-T5: also pull the user's instructions on launch so the
        // People-list badge reflects their real open-instruction
        // count, including rows that were captured on other devices.
        // Without this, the local Room mirror is empty on a fresh
        // install and the badge never appears until the user adds a
        // person via the NoteBar on this same device.
        refreshInstructionsFromNetwork()
        // M3-T7: pull the user's tags on launch so the tag picker
        // (in the capture sheet) is populated with the user's full
        // taxonomy. Without this, the picker is empty until the
        // user creates a tag on this device.
        refreshTagsFromNetwork()
        // M2-T7: still subscribe to Realtime changes. When another
        // device (or this device, before the sync queue drains)
        // writes a row, the realtime event triggers a pull from
        // Supabase, which feeds Room, which re-emits the Flow.
        viewModelScope.launch {
            realtimeSync.changes.collect { change ->
                when (change) {
                    is RealtimeSync.Change.Persons -> refreshFromNetwork()
                    is RealtimeSync.Change.Instructions -> refreshInstructionsFromNetwork()
                    is RealtimeSync.Change.Tags -> refreshTagsFromNetwork()
                }
            }
        }
    }

    /**
     * Create a person via the repository. M2-T6: this goes
     * through Room + the sync outbox; the user sees the new
     * person in the list immediately (Room is reactive).
     */
    fun createPerson(name: String, designation: String?, station: String?) {
        viewModelScope.launch {
            runCatching { personRepository.create(name, designation, station) }
                .onFailure { e ->
                    _state.value = HomeUiState.Error(e.message ?: "Could not create person")
                }
            // Success path: the Flow's `collect` block re-emits
            // when Room gets the new row, so no explicit refresh
            // is needed.
        }
    }

    private fun refreshFromNetwork() {
        viewModelScope.launch {
            runCatching { personRepository.refreshFromNetwork() }
                .onFailure { e ->
                    // Non-fatal: the local copy is still authoritative.
                    _state.value = HomeUiState.Error(e.message ?: "Could not refresh from network")
                }
        }
    }

    /**
     * M3-T5: pull the user's instructions from Supabase and upsert
     * into Room. Triggers the `observeOpenCountByPerson` Flow to
     * re-emit; the People-list badge re-renders with the new counts.
     *
     * Failure is logged but not surfaced: the local Room copy stays
     * empty, the badge stays at 0, the rest of the app still works.
     */
    private fun refreshInstructionsFromNetwork() {
        viewModelScope.launch {
            runCatching { roomInstructionRepository.refreshFromNetwork(supabaseInstructionRepository) }
                .onFailure { e ->
                    // Non-fatal: badge will undercount, no crash.
                    _state.value = HomeUiState.Error(
                        e.message ?: "Could not refresh instructions from network",
                    )
                }
        }
    }

    /**
     * M3-T7: pull the user's tags from Supabase and upsert into
     * Room. Non-fatal on failure; the capture sheet tag picker
     * will just be empty.
     */
    private fun refreshTagsFromNetwork() {
        viewModelScope.launch {
            runCatching { tagRepository.refreshFromNetwork() }
                .onFailure { e ->
                    // Non-fatal: tag picker will be empty.
                }
        }
    }
}

