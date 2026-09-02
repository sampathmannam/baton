package com.kaavalan.note.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.groups.GroupLabelRepository
import com.kaavalan.note.data.person.ContactSyncService
import com.kaavalan.note.data.person.PersonRepository
import com.kaavalan.note.data.person.toProfile
import com.kaavalan.note.ui.util.SafeError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    private val groupLabelRepository: GroupLabelRepository,
    private val contactSyncService: ContactSyncService,
) : ViewModel() {

    private val retryNonce = MutableStateFlow(0L)
    private val mutableState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            retryNonce
                .flatMapLatest {
                    combine(
                        personRepository.observeAll(),
                        groupLabelRepository.observeAll(),
                    ) { people, groups ->
                        if (people.isEmpty() && groups.isEmpty()) {
                            HomeUiState.Empty
                        } else {
                            HomeUiState.Loaded(
                                persons = people.map { it.toProfile() },
                                groupLabels = groups,
                            )
                        }
                    }
                        .onStart { emit(HomeUiState.Loading) }
                        .catch { error ->
                            emit(HomeUiState.Error(SafeError.forUser(error, "Could not load people.")))
                        }
                }
                .collect(mutableState::emit)
        }
    }

    fun retry() {
        retryNonce.value += 1
    }

    fun createPerson(
        name: String,
        phone: String?,
        rankOrRole: String?,
        unit: String?,
    ) {
        viewModelScope.launch {
            runCatching {
                personRepository.create(
                    name = name,
                    designation = rankOrRole,
                    station = unit,
                    phone = phone,
                )
            }.onFailure { error ->
                mutableState.value = HomeUiState.Error(SafeError.forUser(error, "Could not create person."))
            }
        }
    }

    fun importContact(displayName: String, phone: String) {
        createPerson(
            name = displayName,
            phone = phone,
            rankOrRole = null,
            unit = null,
        )
    }

    fun createGroupLabel(name: String, responsiblePersonId: String?) {
        viewModelScope.launch {
            runCatching { groupLabelRepository.create(name, responsiblePersonId) }
                .onFailure { error ->
                    mutableState.value = HomeUiState.Error(
                        SafeError.forUser(error, "Could not create group label."),
                    )
                }
        }
    }

    fun deleteGroupLabel(id: String) {
        viewModelScope.launch {
            runCatching { groupLabelRepository.delete(id) }
                .onFailure { error ->
                    mutableState.value = HomeUiState.Error(
                        SafeError.forUser(error, "Could not delete group label."),
                    )
                }
        }
    }

    fun contactSyncService(): ContactSyncService = contactSyncService
}
