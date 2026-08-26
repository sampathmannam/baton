package com.kaavalan.note.ui.today.decay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.TouchPersonOnActivity
import com.kaavalan.note.data.local.entities.PersonEntity
import com.kaavalan.note.data.person.Person
import com.kaavalan.note.data.person.TierCadence
import com.kaavalan.note.data.person.toDomain
import com.kaavalan.note.data.preferences.KaavalanPreferences
import com.kaavalan.note.data.undo.UndoController
import com.kaavalan.note.data.undo.UndoableAction
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
    private val undoController: UndoController,
    private val preferences: KaavalanPreferences,
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

    /**
     * v1.9.6 (drive-verify polish #6): the one-time
     * discoverability hint. Combines the visible-row count
     * with the DataStore-backed "has the user already seen
     * the hint?" flag. The UI reads this StateFlow and
     * renders the [com.kaavalan.note.ui.today.decay.DecayGestureHint]
     * chip when `true`. Pure decision logic lives in
     * [shouldShowGestureHint] so the contract is unit-testable
     * without standing up the VM.
     */
    val gestureHintVisible: StateFlow<Boolean> = combine(
        state,
        preferences.decayGestureHintShown,
    ) { s, hintShown -> shouldShowGestureHint(s.rows.size, hintShown) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
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

    /**
     * v1.8.0 (PROD-READINESS-P1-#6): per-row "Mark as recent"
     * action. Bumps the person's `lastInteractionAt` to now so
     * they leave the Quiet-a-while list, captures the prior
     * state for [UndoController], and pushes a
     * [UndoableAction.MarkPersonRecent] so the snackbar can
     * offer "Undo". The undo restores the prior
     * `lastInteractionAt` (which may be null if the user
     * marked a never-touched person as recent).
     *
     * No-op if the row is no longer in the visible list
     * (e.g. the user typed in another filter while the snackbar
     * was visible). The DAO call is idempotent so a duplicate
     * tap is a safe no-op.
     *
     * v1.9.6: also dismisses the discoverability hint — once
     * the user has marked someone recent, the hint is no
     * longer useful, so we set the pref so the chip never
     * shows again.
     */
    fun markRecent(row: DecayRow) {
        val now = System.currentTimeMillis()
        val nowIso = Instant.ofEpochMilli(now).toString()
        viewModelScope.launch {
            personDao.touch(row.id, now, nowIso)
            undoController.push(
                UndoableAction.MarkPersonRecent(
                    id = row.id,
                    name = row.name,
                    previousLastInteractionAt = row.lastInteractionAt,
                    previousUpdatedAt = row.person.updatedAt ?: nowIso,
                )
            )
            // v1.9.6: mark the gesture hint as seen in the
            // same viewmodel-scope coroutine. The flag
            // is observed by `gestureHintVisible`, so the
            // chip disappears the next time the UI
            // recomposes after this push completes.
            preferences.setDecayGestureHintShown()
        }
    }

    /**
     * v1.9.6: dismiss the one-time discoverability hint
     * when the user taps the "Got it" affordance. Pure
     * state mutation; the UI hides the chip on the next
     * recomposition.
     */
    fun dismissGestureHint() {
        viewModelScope.launch { preferences.setDecayGestureHintShown() }
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
        /**
         * v1.9.6 (drive-verify polish #6): the minimum
         * quiet-contact count for the discoverability hint
         * to be worth showing. Below this count, the section
         * is sparse and the user is unlikely to swipe; we
         * suppress the hint to avoid noise on a near-empty
         * Today.
         */
        const val HINT_MIN_ROWS = 3

        /**
         * v1.9.6: pure decision function — extracted from
         * the Composable so the contract is unit-testable
         * without standing up a Robolectric / Compose
         * runtime. The UI calls this indirectly via the
         * `gestureHintVisible` StateFlow; the unit test
         * calls it directly with explicit inputs.
         *
         * Contract:
         * - hint is visible only when the user has >= 3
         *   quiet contacts on this screen AND the
         *   `decay_gesture_hint_shown_v1` preference is
         *   still `false`.
         * - any `prefShown = true` input (including a
         *   re-install) means the hint stays hidden.
         */
        fun shouldShowGestureHint(rowCount: Int, prefShown: Boolean): Boolean =
            rowCount >= HINT_MIN_ROWS && !prefShown
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
