package com.baton.app.features.capture

import android.content.Intent
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * M1-T6 unit test for [CalendarGate].
 *
 * The data half ([CalendarGate.buildEventData] + [parseDueAt]) is
 * pure-JVM and runs without Robolectric. The Intent half
 * ([CalendarGate.toIntent]) needs Android's Intent class to be
 * functional, so it runs under Robolectric.
 *
 * The acceptance test (FT-1.4) covers the "launches the intent"
 * half by querying CalendarContract.Events on the emulator.
 */
class CalendarGateTest {

    /**
     * v1.6.1: with the LLM gone there is no `due_at` to extract.
     * A null/blank/unparseable [dueAt] now produces a
     * [CalendarEventData] whose `beginMillis` is "now" so the
     * user's explicit "Add to calendar" tap still fires a
     * calendar event (the user can move the time in the
     * system's calendar pick UI). The pre-v1.6.1 contract of
     * "return null when there's no due date" was correct for an
     * LLM-driven capture flow but not for the free-form note
     * flow that v1.6.1 ships.
     */
    @Test
    fun `buildEventData falls back to now when dueAt is null or blank`() {
        val before = System.currentTimeMillis()
        val event1 = CalendarGate.buildEventData(title = "x", description = "y", dueAt = null)!!
        val event2 = CalendarGate.buildEventData(title = "x", description = "y", dueAt = "")!!
        val event3 = CalendarGate.buildEventData(title = "x", description = "y", dueAt = "   ")!!
        val after = System.currentTimeMillis()
        // begin falls in [before, after] — the test can be flaky
        // at the millisecond boundary, so allow a small slack.
        assertTrue("begin=$event1.beginMillis not in [$before, $after]",
            event1.beginMillis in before..after)
        assertEquals(event1.beginMillis, event2.beginMillis)
        assertEquals(event1.beginMillis, event3.beginMillis)
        // end is begin + default 15 minutes
        assertEquals(event1.beginMillis + 15L * 60_000L, event1.endMillis)
    }

    /**
     * v1.6.1: unparseable [dueAt] is treated the same as a
     * null/blank one (fall back to "now") rather than silently
     * dropping the calendar event. The user can correct the
     * time in the calendar pick UI.
     */
    @Test
    fun `buildEventData falls back to now when dueAt is unparseable`() {
        val before = System.currentTimeMillis()
        val event1 = CalendarGate.buildEventData(title = "x", description = "y", dueAt = "not a date")!!
        val event2 = CalendarGate.buildEventData(title = "x", description = "y", dueAt = "2026-13-99")!!
        val after = System.currentTimeMillis()
        assertTrue(event1.beginMillis in before..after)
        assertTrue(event2.beginMillis in before..after)
    }

    @Test
    fun `buildEventData returns null when dueAt is in the past`() {
        // An obviously-past timestamp.
        assertNull(CalendarGate.buildEventData(title = "x", description = "y", dueAt = "1999-01-01T00:00:00Z"))
    }

    @Test
    fun `buildEventData produces correct begin + end times`() {
        val dueAt = "2099-08-15T17:00:00+05:30"
        val event = CalendarGate.buildEventData(
            title = "send FIR 47 — SHO Ramu",
            description = "Tell SHO Ramu to send FIR 47 by Friday",
            dueAt = dueAt,
        )
        assertNotNull(event)
        val expectedBegin = Instant.parse(dueAt).toEpochMilli()
        assertEquals("send FIR 47 — SHO Ramu", event!!.title)
        assertEquals("Tell SHO Ramu to send FIR 47 by Friday", event.description)
        assertEquals(expectedBegin, event.beginMillis)
        assertEquals(expectedBegin + 15L * 60_000L, event.endMillis)
    }

    @Test
    fun `buildEventData honours a custom duration`() {
        val dueAt = "2099-08-15T17:00:00+05:30"
        val event = CalendarGate.buildEventData(
            title = "x",
            description = "y",
            dueAt = dueAt,
            durationMinutes = 45L,
        )!!
        val begin = Instant.parse(dueAt).toEpochMilli()
        assertEquals(begin + 45L * 60_000L, event.endMillis)
    }

    @Test
    fun `parseDueAt handles the LLM output format with timezone offset`() {
        val ms = CalendarGate.parseDueAt("2026-08-15T17:00:00+05:30")
        assertNotNull(ms)
        assertEquals(Instant.parse("2026-08-15T17:00:00+05:30").toEpochMilli(), ms)
    }

    @Test
    fun `parseDueAt handles the Z (UTC) suffix`() {
        val ms = CalendarGate.parseDueAt("2026-08-15T11:30:00Z")
        assertNotNull(ms)
        assertEquals(Instant.parse("2026-08-15T11:30:00Z").toEpochMilli(), ms)
    }

    @RunWith(RobolectricTestRunner::class)
    @Config(sdk = [33])
    class ToIntentTest {

        @Test
        fun `toIntent produces an ACTION_INSERT with the right extras`() {
            val event = CalendarEventData(
                title = "send FIR 47 — SHO Ramu",
                description = "Tell SHO Ramu to send FIR 47 by Friday",
                beginMillis = Instant.parse("2099-08-15T17:00:00+05:30").toEpochMilli(),
                endMillis = Instant.parse("2099-08-15T17:00:00+05:30").toEpochMilli() + 15L * 60_000L,
            )
            val intent = CalendarGate.toIntent(event)

            // Action + data URI
            assertEquals(Intent.ACTION_INSERT, intent.action)
            assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)

            // Title + description
            assertEquals("send FIR 47 — SHO Ramu", intent.getStringExtra(CalendarContract.Events.TITLE))
            assertEquals(
                "Tell SHO Ramu to send FIR 47 by Friday",
                intent.getStringExtra(CalendarContract.Events.DESCRIPTION),
            )

            // Begin + end times
            assertEquals(event.beginMillis, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L))
            assertEquals(event.endMillis, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L))

            // All-day is off
            assertEquals(false, intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true))

            // FLAG_ACTIVITY_NEW_TASK so a non-Activity Context can launch it.
            assertTrue("FLAG_ACTIVITY_NEW_TASK must be set", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
    }
}
