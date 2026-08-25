package com.kaavalan.note.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.instructions.toDomain
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.PersonStaleAge
import com.kaavalan.note.data.local.RoomPersonRepository
import com.kaavalan.note.data.local.TagDao
import com.kaavalan.note.data.tags.RoomTagRepository
import com.kaavalan.note.data.vault.VaultModeHolder
import com.kaavalan.note.ui.util.SafeError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: RoomPersonRepository,
    private val instructionDao: InstructionDao,
    private val tagDao: TagDao,
    private val tagRepository: RoomTagRepository,
    private val vaultModeHolder: VaultModeHolder,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vaultModeHolder.mode
                .flatMapLatest { mode ->
                    combine(
                        personRepository.observeAllInMode(mode.storageKey),
                        instructionDao.observeOpenCountByPerson(),
                        instructionDao.observeStaleByPerson(),
                        instructionDao.observeOutgoingOpen(),
                        instructionDao.observeIncomingOpen(),
                        tagDao.observeTop(20).map { it.map { e -> TagCount(e.id, e.name, e.usageCount) } },
                    ) { values ->
                        val persons = values[0] as List<com.kaavalan.note.data.person.Person>
                        @Suppress("UNCHECKED_CAST")
                        val counts = values[1] as List<com.kaavalan.note.data.local.PersonOpenCount>
                        @Suppress("UNCHECKED_CAST")
                        val stale = values[2] as List<PersonStaleAge>
                        @Suppress("UNCHECKED_CAST")
                        val outgoing = (values[3] as List<com.kaavalan.note.data.local.entities.InstructionEntity>).map { it.toDomain() }
                        @Suppress("UNCHECKED_CAST")
                        val incoming = (values[4] as List<com.kaavalan.note.data.local.entities.InstructionEntity>).map { it.toDomain() }
                        @Suppress("UNCHECKED_CAST")
                        val popularTags = values[5] as List<TagCount>
                        val openMap = persons.associate { it.id to 0 } + counts.associate { it.personId to it.cnt }
                        val staleSet = stale.map { it.personId }.toSet()
                        if (persons.isEmpty() && outgoing.isEmpty() && incoming.isEmpty()) HomeUiState.Empty
                        else HomeUiState.Loaded(persons = persons, openCountByPersonId = openMap, stalePersonIds = staleSet, outgoingOpen = outgoing, incomingOpen = incoming, popularTags = popularTags)
                    }
                }
                .catch { e -> _state.value = HomeUiState.Error(SafeError.forUser(e, "Could not load people.")) }
                .collect { _state.value = it }
        }
        refreshTagsFromNetwork()
    }

    fun createPerson(name: String, designation: String?, station: String?) {
        viewModelScope.launch {
            runCatching { personRepository.create(name, designation, station) }
                .onFailure { e -> _state.value = HomeUiState.Error(SafeError.forUser(e, "Could not create person.")) }
        }
    }

    private fun refreshTagsFromNetwork() {
        viewModelScope.launch {
            runCatching { tagRepository.refreshFromNetwork() }
                .onFailure { e -> _state.value = HomeUiState.Error(SafeError.forUser(e, "Could not refresh tags.")) }
        }
    }
}
