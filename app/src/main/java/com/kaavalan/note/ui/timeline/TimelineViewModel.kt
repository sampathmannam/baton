package com.kaavalan.note.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.InstructionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repository: InstructionRepository,
) : ViewModel() {

    private val selectedFilterState = MutableStateFlow(TimelineFilter.ALL)
    val selectedFilter: StateFlow<TimelineFilter> = selectedFilterState.asStateFlow()

    private val retryNonce = MutableStateFlow(0L)
    private val source: Flow<TimelineSource> = retryNonce.flatMapLatest {
        repository.observeTimeline()
            .map<List<Instruction>, TimelineSource>(TimelineSource::Data)
            .onStart { emit(TimelineSource.Loading) }
            .catch { emit(TimelineSource.Failed) }
    }

    val uiState: StateFlow<TimelineUiState> = combine(source, selectedFilterState) { result, filter ->
        when (result) {
            TimelineSource.Loading -> TimelineUiState.Loading
            TimelineSource.Failed -> TimelineUiState.Error
            is TimelineSource.Data -> {
                val sections = buildTimelineSections(
                    instructions = result.instructions,
                    filter = filter,
                    now = Instant.now(),
                    zoneId = ZoneId.systemDefault(),
                )
                if (sections.isEmpty()) TimelineUiState.Empty else TimelineUiState.Content(sections)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimelineUiState.Loading,
    )

    fun setFilter(filter: TimelineFilter) {
        selectedFilterState.value = filter
    }

    fun retry() {
        retryNonce.value += 1
    }

    private sealed interface TimelineSource {
        data object Loading : TimelineSource
        data object Failed : TimelineSource
        data class Data(val instructions: List<Instruction>) : TimelineSource
    }
}
