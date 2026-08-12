package com.baton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.local.RoomPersonRepository
import com.baton.app.data.sync.RealtimeSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: RoomPersonRepository,
    realtimeSync: RealtimeSync,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // M2-T6: Room is the source of truth. The Flow re-emits on
        // every local write AND on every Realtime-driven refresh
        // (see [refreshFromNetwork] below). The Home tab never has
        // to call `observeAll()` itself.
        viewModelScope.launch {
            personRepository.observeAll()
                .catch { e -> _state.value = HomeUiState.Error(e.message ?: "Unknown error") }
                .collect { persons ->
                    _state.value = if (persons.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Loaded(persons)
                    }
                }
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
                    is RealtimeSync.Change.Instructions -> { /* TBD: instructions list lands in M3 */ }
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
}

