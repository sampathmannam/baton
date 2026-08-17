package com.baton.app.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.local.InstructionFtsDao
import com.baton.app.data.search.SearchQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@dagger.hilt.android.lifecycle.HiltViewModel
class SearchViewModel @Inject constructor(
    private val ftsDao: InstructionFtsDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<com.baton.app.data.local.entities.InstructionEntity>> =
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

    fun setQuery(value: String) {
        _query.value = value
    }

    fun clear() {
        _query.value = ""
    }
}
