package com.kaavalan.note.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.PersonStaleAge
import com.kaavalan.note.data.local.RoomPersonRepository
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v2.0.0 (drop Supabase): the HomeViewModel no longer needs
 * [RealtimeSync], [SupabaseInstructionRepository], or any
 * other cloud reference. The data path is now:
 *
 *   Room (local SQLCipher DB) → [HomeViewModel] state →
 *   Compose UI
 *
 * The [v1.9.10 Obs-2 fix] tag refresh surfacing an
 * [HomeUiState.Error] is preserved (the function still runs
 * a "refresh" but the implementation is now a no-op against
 * the local Room cache, kept to keep the function contract
 * identical for any future change). The launch-time
 * `refreshFromNetwork()` calls are gone — there is no remote
 * to refresh from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: RoomPersonRepository,
    private val instructionDao: InstructionDao,
    private val tagRepository: RoomTagRepository,
    // v2.0 T3-1: the deniable vault. The Home list filters
    // on the active mode; the holder is a process-singleton
    // so the filter survives the bottom-sheet toggle.
    private val vaultModeHolder: VaultModeHolder,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // M3-T5 + M4-T3 + v2.0 T3-1: combine persons (filtered
        // by the active vault mode) + open-instruction counts
        // + stale-outgoing ages into a single Flow. The
        // persons source is `flatMapLatest`'d off the mode so
        // a mode switch triggers a re-query of the DAO.
        viewModelScope.launch {
            vaultModeHolder.mode
                .flatMapLatest { mode ->
                    combine(
                        personRepository.observeAllInMode(mode.storageKey),
                        instructionDao.observeOpenCountByPerson(),
                        instructionDao.observeStaleByPerson(),
                    ) { persons, counts, stale ->
                        val openMap = persons.associate { it.id to 0 } +
                            counts.associate { it.personId to it.cnt }
                        val staleSet = stale.map { it.personId }.toSet()
                        if (persons.isEmpty()) {
                            HomeUiState.Empty
                        } else {
                            HomeUiState.Loaded(
                                persons = persons,
                                openCountByPersonId = openMap,
                                stalePersonIds = staleSet,
                            )
                        }
                    }
                }
                .catch { e -> _state.value = HomeUiState.Error(SafeError.forUser(e, "Could not load people.")) }
                .collect { _state.value = it }
        }
        // v1.9.10 (Obs-2 fix): the launch-time tag refresh still
        // fires from init so the user gets an Error surface if the
        // tag mirror is unreachable. v2.0.0: this is a no-op
        // against the local Room cache (see
        // [RoomTagRepository.refreshFromNetwork]) but the call +
        // error contract is preserved so a future change that
        // re-enables background sync reuses the same path.
        refreshTagsFromNetwork()
    }

    /**
     * Create a person via the repository. v2.0.0: this is
     * Room-only — no sync queue, no remote, no outbox. The
     * person appears in the list immediately (Room is
     * reactive). The v1.x sync queue is still in the DB
     * schema (v1.8.0) for forward-compat with a future
     * optional cloud sync, but no rows are written to it.
     */
    fun createPerson(name: String, designation: String?, station: String?) {
        viewModelScope.launch {
            runCatching { personRepository.create(name, designation, station) }
                .onFailure { e ->
                    _state.value = HomeUiState.Error(SafeError.forUser(e, "Could not create person."))
                }
            // Success path: the Flow's `collect` block re-emits
            // when Room gets the new row, so no explicit refresh
            // is needed.
        }
    }

    /**
     * v1.9.10 (Obs-2 fix): tag refresh surfaces a
     * [HomeUiState.Error] like the other two refreshes
     * (persons, instructions) used to. v2.0.0: there is no
     * remote to refresh from, but the function shape is
     * preserved (it now no-ops) so any future change that
     * adds a local background sync (e.g. import/import-from-
     * file) reuses the same error-surface contract.
     */
    private fun refreshTagsFromNetwork() {
        viewModelScope.launch {
            runCatching { tagRepository.refreshFromNetwork() }
                .onFailure { e ->
                    _state.value = HomeUiState.Error(
                        SafeError.forUser(e, "Could not refresh tags."),
                    )
                }
        }
    }
}
