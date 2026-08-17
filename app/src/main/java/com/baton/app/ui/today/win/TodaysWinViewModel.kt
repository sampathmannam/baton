package com.baton.app.ui.today.win

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.entities.CaptureEntity
import com.baton.app.data.local.entities.InstructionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * v2.0 Tier 2 (§2.11): "Today's win" summary. Pure
 * client-side query + template, no LLM. Counts the user's
 * captures and instructions created in the rolling 24 h,
 * groups by person, and surfaces sensitive rows separately.
 *
 * The card is rendered on the Today tab; when there's nothing
 * to report, it shows an inviting line ("No captures today yet.
 * Tap the note bar to start.").
 */
@HiltViewModel
class TodaysWinViewModel @Inject constructor(
    private val captureDao: CaptureDao,
    private val instructionDao: InstructionDao,
) : ViewModel() {

    val state: StateFlow<TodaysWinState> = combine(
        captureDao.observeAll(),
        instructionDao.observeAll(),
    ) { caps, ins ->
        val sinceMsIso = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        val recentCaps = caps.filter { it.createdAt >= sinceMsIso }
        val recentIns = ins.filter { it.createdAt >= sinceMsIso }
        val personIds = recentIns.mapNotNull { it.personId }.toSet()
        val carried = recentIns.count {
            it.status == "CARRIED_OVER"
        }
        // Captures don't have an isSensitive column; only
        // instructions do.
        val sensitive = recentIns.count { it.isSensitive }
        TodaysWinState(
            captureCount = recentCaps.size,
            peopleCount = personIds.size,
            carriedOverCount = carried,
            sensitiveCount = sensitive,
            isEmpty = recentCaps.isEmpty() && recentIns.isEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodaysWinState(isEmpty = true),
    )
}

data class TodaysWinState(
    val captureCount: Int = 0,
    val peopleCount: Int = 0,
    val carriedOverCount: Int = 0,
    val sensitiveCount: Int = 0,
    val isEmpty: Boolean = true,
)
