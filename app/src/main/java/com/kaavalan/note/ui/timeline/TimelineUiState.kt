package com.kaavalan.note.ui.timeline

import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.instructions.TimelineBucket
import com.kaavalan.note.data.instructions.timelineBucket
import java.time.Instant
import java.time.ZoneId

enum class TimelineFilter {
    ALL,
    TO_DO,
    WAITING,
    DONE,
}

data class TimelineSection(
    val bucket: TimelineBucket,
    val instructions: List<Instruction>,
)

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data object Empty : TimelineUiState
    data class Content(val sections: List<TimelineSection>) : TimelineUiState
    data object Error : TimelineUiState
}

internal fun buildTimelineSections(
    instructions: List<Instruction>,
    filter: TimelineFilter,
    now: Instant,
    zoneId: ZoneId,
): List<TimelineSection> {
    val visible = instructions.filter { instruction ->
        when (filter) {
            TimelineFilter.ALL -> true
            TimelineFilter.TO_DO -> instruction.status == Status.TO_DO
            TimelineFilter.WAITING -> instruction.status == Status.WAITING
            TimelineFilter.DONE -> instruction.status == Status.DONE
        }
    }
    val grouped = visible.groupBy { it.timelineBucket(now, zoneId) }
    return TimelineBucket.values().mapNotNull { bucket ->
        grouped[bucket]?.takeIf(List<Instruction>::isNotEmpty)?.let { TimelineSection(bucket, it) }
    }
}
