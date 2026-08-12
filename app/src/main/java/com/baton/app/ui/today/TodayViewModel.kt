package com.baton.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.brief.BriefGenerator
import com.baton.app.data.brief.BriefType
import com.baton.app.data.brief.DailyBrief
import com.baton.app.data.instructions.Instruction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * M4-T1: Today screen view-model.
 *
 * M4-T5: also exposes the evening review. Computed from the same
 * brief; the stillOpen list is the still-pending section. The
 * review surface is a single dismiss tap, no punishment.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val briefGenerator: BriefGenerator,
) : ViewModel() {

    val brief: StateFlow<DailyBrief> = briefGenerator
        .observeDailyBrief(
            type = BriefType.MORNING,
            date = LocalDate.now(ZoneId.systemDefault()),
        )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DailyBrief(
                date = LocalDate.now(ZoneId.systemDefault()).toString(),
                type = BriefType.MORNING,
                needsYouToday = emptyList(),
                waitingOnOthers = emptyList(),
                carriedOver = emptyList(),
            ),
        )

    val review: StateFlow<EveningReview> = briefGenerator
        .observeDailyBrief(
            type = BriefType.EVENING,
            date = LocalDate.now(ZoneId.systemDefault()),
        )
        .map { brief ->
            EveningReview(
                date = LocalDate.now(ZoneId.systemDefault()).toString(),
                stillOpen = brief.needsYouToday + brief.waitingOnOthers,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EveningReview(),
        )
}

data class EveningReview(
    val date: String = "",
    val gotDoneToday: List<Instruction> = emptyList(),
    val stillOpen: List<Instruction> = emptyList(),
)
