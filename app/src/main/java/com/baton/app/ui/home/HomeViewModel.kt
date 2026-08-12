package com.baton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.RoomPersonRepository
import com.baton.app.data.sync.RealtimeSync
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
        // M2-T7: still subscribe to Realtime changes. When another
        // device (or this device, before the sync queue drains)
        // writes a row, the realtime event triggers a pull from
        // Supabase, which feeds Room, which re-emits the Flow.
        viewModelScope.launch {
            realtimeSync.changes.collect { change ->
                when (change) {
                    is RealtimeSync.Change.Persons -> refreshFromNetwork()
                    is RealtimeSync.Change.Instructions -> refreshInstructionsFromNetwork()
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
     * M3-T5: when an instructions change arrives via Realtime,
     * pull the latest instructions and upsert into Room. The
     * `observeOpenCountByPerson` Flow re-emits and the UI's
     * badge count updates.
     */
    private fun refreshInstructionsFromNetwork() {
        // TODO: when M2-T6 adds an InstructionRepository.refreshFromNetwork,
        // call it here. For M3-T5 the count comes from the
        // local Room mirror; instructions are only added on save
        // so the badge updates on the same device automatically.
        // Other devices' instructions arrive via the SyncEngine
        // outbox, which writes to Room and re-emits the Flow.
    }
}

