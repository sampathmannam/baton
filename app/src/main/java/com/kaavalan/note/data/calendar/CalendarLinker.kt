package com.kaavalan.note.data.calendar

import com.kaavalan.note.data.local.CaptureDao
import com.kaavalan.note.data.local.PersonDao
import com.kaavalan.note.data.local.TouchPersonOnActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0 Tier 2 (§2.9): when a capture is saved, look for the
 * next calendar event in the next 24 h whose title or
 * description mentions the capture's person (if any). If a
 * candidate exists, the UI shows a "Attach to your next event
 * with Ramesh?" prompt; on confirm we set
 * [com.kaavalan.note.data.local.entities.CaptureEntity.calendarEventId].
 *
 * **No auto-attach.** The user must accept the prompt
 * explicitly. We do not silently attach a capture to a calendar
 * event.
 *
 * **Permission gate.** [CalendarBriefSource.findCandidateForPerson]
 * returns null when `READ_CALENDAR` is not held, in which case
 * the UI shows nothing (no nudge).
 */
@Singleton
class CalendarLinker @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val calendarSource: CalendarBriefSource,
    private val personDao: PersonDao,
    private val captureDao: CaptureDao,
) {
    suspend fun candidateFor(personId: String): CalendarEvent? =
        calendarSource.findCandidateForPerson(personId)

    suspend fun attach(captureId: String, eventId: Long) {
        captureDao.setCalendarEventId(captureId, eventId.toString())
    }
}
