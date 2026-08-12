package com.baton.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.brief.BriefGenerator
import com.baton.app.data.brief.BriefType
import com.baton.app.data.brief.DailyBrief
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * M4-T1: Today screen view-model. Wraps the [BriefGenerator] in a
 * StateFlow so the composable can render reactively. The brief is
 * regenerated on every instruction Room update — no manual refresh.
 *
 * **No need for a server cron.** The local mirror (M2-T6) is the
 * source of truth; we compute the brief from it. When the user
 * closes an instruction, the badge count + brief update in the
 * same Room change.
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
}
