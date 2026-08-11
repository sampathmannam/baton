package com.baton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.sync.RealtimeSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    realtimeSync: RealtimeSync,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // Initial fetch
        viewModelScope.launch {
            runCatching { personRepository.observeAll() }
                .onSuccess { persons ->
                    _state.value = if (persons.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Loaded(persons)
                    }
                }
                .onFailure { e ->
                    _state.value = HomeUiState.Error(e.message ?: "Unknown error")
                }
        }
        // M2-T7: subscribe to Realtime changes. When another device
        // (or this device) writes a row, refresh the list.
        viewModelScope.launch {
            realtimeSync.changes.collect { change ->
                when (change) {
                    is RealtimeSync.Change.Persons -> refreshList()
                    is RealtimeSync.Change.Instructions -> { /* TBD: instructions list lands in M3 */ }
                }
            }
        }
    }

    /**
     * Create a person via the repository, then refresh the list. M3 will
     * replace this with a Room Flow that re-emits automatically.
     */
    fun createPerson(name: String, designation: String?, station: String?) {
        viewModelScope.launch {
            runCatching { personRepository.create(name, designation, station) }
                .onSuccess { refreshList() }
                .onFailure { e ->
                    _state.value = HomeUiState.Error(e.message ?: "Could not create person")
                }
        }
    }

    private suspend fun refreshList() {
        runCatching { personRepository.observeAll() }
            .onSuccess { persons ->
                _state.value = if (persons.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Loaded(persons)
                }
            }
            .onFailure { e ->
                _state.value = HomeUiState.Error(e.message ?: "Could not refresh list")
            }
    }
}
