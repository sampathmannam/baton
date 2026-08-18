package com.baton.app.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.local.InstructionFtsDao
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.search.SearchQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tier 1.3 (v2.0): full-text search ViewModel.
 *
 * The query is held in a [MutableStateFlow]; a 150 ms debounce
 * prevents the FTS4 MATCH from re-firing on every keystroke.
 * The result flow is `flatMapLatest` over the FTS DAO so the
 * previous in-flight emission is cancelled when a new query
 * comes in. Empty queries return an empty list (the caller
 * shows the "no results" state).
 *
 * v1.6.2: also searches people. The Home / Today screen feeds
 * the currently-visible person list via [setVisiblePeople]; the
 * VM filters that list client-side by name + designation +
 * station (case-insensitive contains). The placeholder
 * "Search people and instructions" is now honest. We do this
 * client-side rather than via FTS because the people dataset
 * is small (tens of rows, not thousands) and we already have
 * them in memory — the FTS path is reserved for instructions
 * where the corpus is larger.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@dagger.hilt.android.lifecycle.HiltViewModel
class SearchViewModel @Inject constructor(
    private val ftsDao: InstructionFtsDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _visiblePeople = MutableStateFlow<List<PersonEntity>>(emptyList())

    val results: StateFlow<List<InstructionEntity>> =
        _query
            .debounce(150L)
            .flatMapLatest { raw ->
                val match = SearchQuery.build(raw)
                if (match.isBlank()) flowOf(emptyList()) else ftsDao.search(match)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * v1.6.2: client-side person filter. Combines the live
     * `_visiblePeople` snapshot with the debounced query, so
     * the list updates the moment either input changes.
     */
    val personResults: StateFlow<List<PersonEntity>> =
        combine(_query.debounce(150L), _visiblePeople) { raw, people ->
            if (raw.isBlank()) {
                emptyList()
            } else {
                val needle = raw.trim().lowercase()
                people.filter { p ->
                    p.name.lowercase().contains(needle) ||
                        (p.designation?.lowercase()?.contains(needle) == true) ||
                        (p.station?.lowercase()?.contains(needle) == true)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setVisiblePeople(people: List<PersonEntity>) {
        _visiblePeople.value = people
    }

    fun clear() {
        _query.value = ""
    }
}
