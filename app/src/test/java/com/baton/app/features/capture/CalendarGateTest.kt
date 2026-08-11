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

    @Test
    fun `buildEventData returns null when dueAt is null or blank`() {
        assertNull(CalendarGate.buildEventData(title = "x", description = "y", dueAt = null))
        assertNull(CalendarGate.buildEventData(title = "x", description = "y", dueAt = ""))
        assertNull(CalendarGate.buildEventData(title = "x", description = "y", dueAt = "   "))
    }

    @Test
    fun `buildEventData returns null when dueAt is unparseable`() {
        assertNull(CalendarGate.buildEventData(title = "x", description = "y", dueAt = "not a date"))
        assertNull(CalendarGate.buildEventData(title = "x", description = "y", dueAt = "2026-13-99"))
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
