package com.baton.app.features.capture

import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * M1 wrapper around `CalendarContract.Events` for the
 * "Add to calendar" toggle on the confirmation card.
 *
 * Two halves:
 *  - [buildEventData] is pure-JVM: it parses the LLM's `due_at` and
 *    returns a [CalendarEventData] (or `null` if the date is
 *    missing, unparseable, or in the past). The ViewModel calls
 *    this and emits the data over a Channel.
 *  - [toIntent] converts the data to an Android [Intent]. The
 *    Composable calls this with `LocalContext.current` and launches
 *    the intent; this is the only place Android's `Intent` class
 *    is touched on the capture-flow hot path.
 *
 * Splitting them keeps the ViewModel and its unit tests free of
 * Android framework calls (which throw "method not mocked" in
 * plain-JVM tests).
 *
 * The M1 manifest declares `WRITE_CALENDAR` with `maxSdkVersion=32`
 * — on API 33+ the system ACTION_INSERT flow does not need the
 * permission, the user picks the calendar in the Calendar app.
 *
 * No third-party calendar SDK. No background sync. The calendar
 * event is a copy of the instruction; the `instructions` row in
 * Supabase remains the source of truth.
 */
object CalendarGate {

    /**
     * Parse the proposal and produce the event data. Returns `null`
     * only if [dueAt] is provided, parseable, and in the past.
     *
     * v1.6.1: with no LLM there is no `due_at` to extract. When
     * [dueAt] is null or unparseable, the calendar event still
     * fires — the user toggled "Add to calendar" explicitly, so
     * we honor it. The begin time defaults to "now" so the event
     * shows up in the user's "Today" calendar list with a
     * sensible default duration. The user can move the time in
     * the system's calendar pick UI.
     */
    fun buildEventData(
        title: String,
        description: String,
        dueAt: String?,
        durationMinutes: Long = DEFAULT_DURATION_MIN,
    ): CalendarEventData? {
        val now = System.currentTimeMillis()
        val begin = parseDueAt(dueAt) ?: now
        // If the LLM (or any future due-date source) gave us a
        // past timestamp, skip the event — the user is not going
        // to want a 1-hour event for "last Tuesday".
        if (begin < now) return null
        val end = begin + durationMinutes * 60_000L
        return CalendarEventData(
            title = title,
            description = description,
            beginMillis = begin,
            endMillis = end,
        )
    }

    /**
     * Parse the LLM's `due_at` ISO 8601 timestamp into epoch millis.
     * Returns `null` on a blank or unparseable string.
     */
    fun parseDueAt(dueAt: String?): Long? {
        if (dueAt.isNullOrBlank()) return null
        return try {
            Instant.parse(dueAt).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Build the ACTION_INSERT intent from pre-parsed event data.
     * Call this on the Android thread (or Robolectric) only — the
     * [Intent] constructor is not safe to call in plain JVM tests.
     */
    fun toIntent(event: CalendarEventData): Intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, event.title)
        putExtra(CalendarContract.Events.DESCRIPTION, event.description)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        // FLAG_ACTIVITY_NEW_TASK so a non-Activity Context can launch it.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Default event duration when the LLM doesn't supply an end. */
    const val DEFAULT_DURATION_MIN: Long = 15L
}

/**
 * Pre-parsed calendar event data, ready to hand to
 * [CalendarGate.toIntent]. The pure-JVM form is testable without
 * Android.
 */
data class CalendarEventData(
    val title: String,
    val description: String,
    val beginMillis: Long,
    val endMillis: Long,
)
