package com.baton.app.ui.today.decay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.TouchPersonOnActivity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.person.Person
import com.baton.app.data.person.TierCadence
import com.baton.app.data.person.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * v2.0 Tier 2 (§2.1, §2.13, §2.14): ViewModel for the
 * "Haven't touched in N days" section on the Today tab. Reads all
 * people from Room, computes days-since-last-interaction for each
 * (using the effective cadence from [TierCadence]), and surfaces
 * the ones that crossed the user's chosen threshold.
 *
 * The `filterDays` value is user-togglable (14 / 30 / 60 / 90)
 * via the chip row in the section header; the default is 30.
 *
 * `Quiet a while` is the user-facing label for anyone over 2x
 * the cadence; `Getting due` for 1x..2x; `On track` for < 1x.
 * (Spec §2.13.)
 */
@HiltViewModel
class DecayViewModel @Inject constructor(
    private val personDao: PersonDao,
    private val touchOnActivity: TouchPersonOnActivity,
) : ViewModel() {

    private val _filterDays = MutableStateFlow(DEFAULT_FILTER_DAYS)
    val filterDays: StateFlow<Int> = _filterDays

    /**
     * The visible list. Combines the people flow with the filter
     * days; the state-in collector runs on viewModelScope so it
     * stays alive while the user is on Today.
     */
    val state: StateFlow<DecayUiState> = combine(
        personDao.observeAll(),
        _filterDays,
    ) { people, filter ->
        val now = Instant.now().toEpochMilli()
        val quiet = people
            .map { it.toDecayRow(now) }
            // v1.6.1: exclude "never touched" people from the
            // Quiet a while list. A person the user just added
            // is not quiet — they're new. Without this filter
            // the row passes `daysQuiet = Long.MAX_VALUE >= filter`
            // and the UI renders "haven't touched in -1 days"
            // (Long.MAX_VALUE.toInt() is -1).
            .filter { it.lastInteractionAt != null && it.daysQuiet >= filter }
            .sortedByDescending { it.daysQuiet }
        DecayUiState(
            filterDays = filter,
            rows = quiet,
            quietCount = quiet.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DecayUiState(),
    )

    fun setFilter(days: Int) {
        if (_filterDays.value != days) _filterDays.value = days
    }

    /**
     * v2.0 Tier 2 (§2.14): when the user accepts the
     * "Reschedule the quiet pile" dialog, push the snooze out by
     * 14 days for the oldest half of the rows. Sets the new
     * `lastInteractionAt` to `now + 14 days` for the bottom
     * half of the visible list. The DAO update is non-destructive
     * (one row per UPDATE; no re-insert).
     */
    fun redistribute() {
        val now = System.currentTimeMillis()
        val cutoff = now + 14L * 86_400_000
        val rows = state.value.rows
        if (rows.isEmpty()) return
        val half = rows.size / 2
        // Oldest half (sorted by daysQuiet desc) get pushed out.
        viewModelScope.launch {
            rows.take(half).forEachIndexed { i, row ->
                val newTs = cutoff + (i.toLong() * 60_000L)  // space by 1 min each
                val updatedAt = Instant.ofEpochMilli(newTs).toString()
                personDao.touch(row.id, newTs, updatedAt)
            }
        }
    }

    private fun PersonEntity.toDecayRow(now: Long): DecayRow {
        val last = lastInteractionAt
        val daysQuiet = if (last == null) Long.MAX_VALUE
            else Duration.ofMillis(now - last).toDays().coerceAtLeast(0)
        val cadence = TierCadence.effectiveDays(tier, cadenceOverrideDays)
        val status = when {
            last == null -> ReachOutStatus.OnTrack  // "New" — never touched
            daysQuiet > 2 * cadence -> ReachOutStatus.QuietAWhile
            daysQuiet > cadence -> ReachOutStatus.GettingDue
            else -> ReachOutStatus.OnTrack
        }
        return DecayRow(
            id = id,
            name = name,
            designation = designation,
            station = station,
            daysQuiet = daysQuiet,
            lastInteractionAt = last,
            tier = tier,
            cadenceDays = cadence,
            status = status,
            person = toDomain(),
        )
    }

    companion object {
        const val DEFAULT_FILTER_DAYS = 30
        val FILTER_OPTIONS = listOf(14, 30, 60, 90)
    }
}

data class DecayUiState(
    val filterDays: Int = DecayViewModel.DEFAULT_FILTER_DAYS,
    val rows: List<DecayRow> = emptyList(),
    val quietCount: Int = 0,
)

data class DecayRow(
    val id: String,
    val name: String,
    val designation: String?,
    val station: String?,
    val daysQuiet: Long,
    val tier: String,
    val cadenceDays: Int,
    val status: ReachOutStatus,
    val person: Person,
    // v1.6.1: pass through the last interaction timestamp so the
    // VM can exclude never-touched people from the Quiet a while
    // list (otherwise the `Long.MAX_VALUE.toInt() = -1` overflow
    // renders "-1 days" for a person the user just added).
    val lastInteractionAt: Long?,
)

enum class ReachOutStatus(val label: String) {
    QuietAWhile("Quiet a while"),
    GettingDue("Getting due"),
    OnTrack("On track"),
}
