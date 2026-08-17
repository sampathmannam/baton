package com.baton.app.data.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.entities.PersonEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v2.0 Tier 2 (§2.7, §2.9): thin ContentResolver wrapper over
 * `CalendarContract.Events`. Two methods:
 *  - [upcomingBatonEvents] — return events in the next 15 min
 *    whose title or description mentions a Baton's person name
 *    (case-insensitive). Used by the "Brief me before a meeting"
 *    card.
 *  - [findCandidateForPerson] — return the next event in the
 *    next 24 h whose title or description mentions a specific
 *    person. Used by the §2.9 "Attach to your next event" prompt.
 *
 * **Privacy boundary.** The cursor is `.use { }`'d so it's
 * closed on every code path. The list of matched events is held
 * only in memory (never persisted), and the list of Baton's
 * people is lowercased once per call and discarded.
 *
 * **Permission gate.** If [Manifest.permission.READ_CALENDAR]
 * is not held, both methods return an empty list. The "Brief me
 * before a meeting" UI checks [hasCalendarPermission] and shows
 * a "grant calendar access in Settings" hint when the permission
 * is missing. (Spec: "if the user has not granted READ_CALENDAR,
 * the Brief me before a meeting feature must gracefully NOT
 * appear".)
 */
@Singleton
class CalendarBriefSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personDao: PersonDao,
) {

    fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * List events in the next [windowMs] milliseconds that
     * reference any of the user's Baton's people. Returns
     * sorted by start time ASC. Returns an empty list if the
     * permission is not held.
     */
    suspend fun upcomingBatonEvents(
        now: Long = System.currentTimeMillis(),
        windowMs: Long = 15 * 60_000L,
    ): List<CalendarEvent> {
        if (!hasCalendarPermission()) return emptyList()
        val people = personDao.snapshot().map { it.name.lowercase() }
        if (people.isEmpty()) return emptyList()
        return queryEvents(now, now + windowMs, people)
    }

    /**
     * §2.9: the next event whose title/description mentions the
     * person (by name), in the next 24 h. Returns null if no
     * permission or no match.
     */
    suspend fun findCandidateForPerson(
        personId: String,
        now: Long = System.currentTimeMillis(),
        withinHours: Int = 24,
    ): CalendarEvent? {
        if (!hasCalendarPermission()) return null
        val person: PersonEntity = personDao.getById(personId) ?: return null
        val people = listOf(person.name.lowercase())
        return queryEvents(now, now + withinHours * 3_600_000L, people).firstOrNull()
    }

    private fun queryEvents(
        fromMs: Long,
        toMs: Long,
        peopleLower: List<String>,
    ): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION,
        )
        val sel = "${CalendarContract.Events.DTSTART} >= ? AND " +
            "${CalendarContract.Events.DTSTART} <= ?"
        val args = arrayOf(fromMs.toString(), toMs.toString())
        val out = mutableListOf<CalendarEvent>()
        val cr: ContentResolver = context.contentResolver
        val cursor = runCatching {
            cr.query(
                CalendarContract.Events.CONTENT_URI,
                projection, sel, args,
                "${CalendarContract.Events.DTSTART} ASC",
            )
        }.getOrNull() ?: return emptyList()
        cursor.use { c ->
            val idCol = c.getColumnIndex(CalendarContract.Events._ID)
            val titleCol = c.getColumnIndex(CalendarContract.Events.TITLE)
            val startCol = c.getColumnIndex(CalendarContract.Events.DTSTART)
            val endCol = c.getColumnIndex(CalendarContract.Events.DTEND)
            val descCol = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            while (c.moveToNext()) {
                val title = c.getString(titleCol).orEmpty().lowercase()
                val desc = c.getString(descCol).orEmpty().lowercase()
                val matched = peopleLower.firstOrNull { p ->
                    title.contains(p) || desc.contains(p)
                } ?: continue
                out += CalendarEvent(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol) ?: "",
                    startMs = c.getLong(startCol),
                    endMs = if (c.isNull(endCol)) null else c.getLong(endCol),
                    matchedPersonLower = matched,
                )
            }
        }
        return out
    }
}

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long?,
    /** Lowercased name of the Baton's person that matched. */
    val matchedPersonLower: String,
)
