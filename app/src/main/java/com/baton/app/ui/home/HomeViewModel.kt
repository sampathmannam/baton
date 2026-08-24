package com.baton.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.instructions.RoomInstructionRepository
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonStaleAge
import com.baton.app.data.local.RoomPersonRepository
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
import com.baton.app.data.vault.VaultModeHolder
import com.baton.app.ui.util.SafeError
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val personRepository: RoomPersonRepository,
    private val instructionDao: InstructionDao,
    private val roomInstructionRepository: RoomInstructionRepository,
    private val supabaseInstructionRepository: SupabaseInstructionRepository,
    private val tagRepository: RoomTagRepository,
    realtimeSync: RealtimeSync,
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
                    _state.value = HomeUiState.Error(SafeError.forUser(e, "Could not create person."))
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
                    _state.value = HomeUiState.Error(SafeError.forUser(e, "Could not refresh people."))
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
                        SafeError.forUser(e, "Could not refresh instructions."),
                    )
                }
        }
    }

    /**
     * M3-T7: pull the user's tags from Supabase and upsert into
     * Room. Non-fatal on failure; the capture sheet tag picker
     * will just be empty.
     *
     * v1.9.10 (Obs-2 fix): v1.9.8 audit's refuter surfaced that
     * the empty [onFailure] block silently swallowed the error —
     * the user had no signal that the network call had failed.
     * The fix mirrors [refreshFromNetwork] / [refreshInstructionsFromNetwork]:
     * surface a [HomeUiState.Error] so the user sees a banner.
     * The capture sheet's tag picker stays empty (by design —
     * the local Room copy is the source of truth), but at least
     * the user knows the sync didn't run.
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

