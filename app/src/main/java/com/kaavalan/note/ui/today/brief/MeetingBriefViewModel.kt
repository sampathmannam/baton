package com.kaavalan.note.ui.today.brief

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.data.calendar.CalendarBriefSource
import com.kaavalan.note.data.calendar.CalendarEvent
import com.kaavalan.note.data.local.CaptureDao
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.entities.CaptureEntity
import com.kaavalan.note.data.local.entities.InstructionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * v2.0 Tier 2 (§2.7): the "Brief me before a meeting" card.
 * Combines the next 15 minutes of calendar events filtered to
 * events that mention a Baton's person with that person's last
 * 3 instructions, last 3 photos, and last 3 notes (a "note" is
 * a TEXT-mode capture).
 *
 * **Permission gate.** When `READ_CALENDAR` is not held,
 * [state] reports `isPermissionMissing = true` and the card
 * renders the "grant in Settings" hint instead of any event
 * details.
 *
 * **Refresh.** [refresh] re-runs the query. The screen calls
 * this when it becomes visible; subsequent updates to the
 * underlying captures / instructions re-trigger the state via
 * the in-memory refresh (no Flow combine — the calendar query
 * is one-shot).
 */
@HiltViewModel
class MeetingBriefViewModel @Inject constructor(
    private val calendarSource: CalendarBriefSource,
    private val personDao: PersonDao,
    private val instructionDao: InstructionDao,
    private val captureDao: CaptureDao,
) : ViewModel() {

    private val _state = MutableStateFlow<MeetingBriefState>(
        MeetingBriefState(isPermissionMissing = !calendarSource.hasCalendarPermission()),
    )
    val state: StateFlow<MeetingBriefState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (!calendarSource.hasCalendarPermission()) {
                _state.value = MeetingBriefState(isPermissionMissing = true)
                return@launch
            }
            val events = calendarSource.upcomingBatonEvents()
            if (events.isEmpty()) {
                _state.value = MeetingBriefState(
                    events = emptyList(),
                    isPermissionMissing = false,
                )
                return@launch
            }
            val enriched = events.map { ev ->
                val person = personDao.snapshot().firstOrNull {
                    it.name.equals(ev.matchedPersonLower, ignoreCase = true)
                }
                val personId = person?.id
                val instructions = if (personId != null) {
                    instructionDao.snapshot()
                        .filter { it.personId == personId }
                        .sortedByDescending { it.capturedAt }
                        .take(3)
                } else emptyList()
                // Photos + notes: a "photo" is a PHOTO-mode capture,
                // a "note" is a TEXT-mode capture. Captures don't
                // carry a personId today, so we show the 3 most
                // recent of each kind globally.
                val photos = captureDao.snapshot()
                    .filter { it.mode == "PHOTO" }
                    .sortedByDescending { it.createdAt }
                    .take(3)
                val notes = captureDao.snapshot()
                    .filter { it.mode == "TEXT" }
                    .sortedByDescending { it.createdAt }
                    .take(3)
                MeetingBriefEntry(
                    event = ev,
                    personId = personId,
                    personName = person?.name
                        ?: ev.matchedPersonLower.replaceFirstChar { it.titlecase() },
                    recentInstructions = instructions,
                    recentPhotos = photos,
                    recentNotes = notes,
                )
            }
            _state.value = MeetingBriefState(
                events = enriched,
                isPermissionMissing = false,
            )
        }
    }
}

data class MeetingBriefState(
    val events: List<MeetingBriefEntry> = emptyList(),
    val isPermissionMissing: Boolean = false,
)

data class MeetingBriefEntry(
    val event: CalendarEvent,
    val personId: String?,
    val personName: String,
    val recentInstructions: List<InstructionEntity>,
    val recentPhotos: List<CaptureEntity>,
    val recentNotes: List<CaptureEntity>,
)
