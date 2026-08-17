package com.baton.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.dates.ImportantDateRepository
import com.baton.app.data.local.entities.ImportantDateEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * v2.0 Tier 2 (§2.5): per-person important dates. Backed by
 * [ImportantDateRepository]. The default labels are
 * "Birthday" / "First met" / "Anniversary"; the user can add
 * custom labels.
 */
@HiltViewModel
class ImportantDatesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ImportantDateRepository,
) : ViewModel() {

    private val personId: String = savedStateHandle.get<String>(ARG_PERSON_ID) ?: ""

    val dates: StateFlow<List<ImportantDateEntity>> = repository
        .observeForPerson(personId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun add(label: String, date: LocalDate, recurring: Boolean) {
        if (personId.isEmpty()) return
        viewModelScope.launch {
            repository.add(
                personId = personId,
                label = label,
                dateEpochDay = date.toEpochDay(),
                recurring = recurring,
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        const val ARG_PERSON_ID = "personId"
        val DEFAULT_LABELS: List<String> = listOf(
            "Birthday", "First met", "Anniversary",
        )
    }
}
