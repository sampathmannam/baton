package com.baton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.person.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: PersonRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // M0: read once from the repository. M3 will replace this with
            // a `stateIn`-backed Flow that observes Room + syncs with Supabase.
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
    }
}
