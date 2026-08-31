package com.kaavalan.note.data.timeline

import com.kaavalan.note.data.instructions.Direction
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.Source
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.instructions.TimelineBucket
import com.kaavalan.note.data.instructions.timelineBucket
import com.kaavalan.note.data.instructions.timelineAtEpochMs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TimelineGroupingTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-08-31T18:45:00Z") // 2026-09-01 00:15 local

    @Test
    fun `timeline boundaries use the supplied timezone and include the seventh day`() {
        assertEquals(TimelineBucket.LATE, instruction(followUp = atLocal(2026, 8, 31, 23, 59)).timelineBucket(now, zone))
        assertEquals(TimelineBucket.TODAY, instruction(deadline = atLocal(2026, 9, 1, 23, 59)).timelineBucket(now, zone))
        assertEquals(TimelineBucket.NEXT_7_DAYS, instruction(followUp = atLocal(2026, 9, 8, 23, 59)).timelineBucket(now, zone))
        assertEquals(TimelineBucket.LATER, instruction(followUp = atLocal(2026, 9, 9, 0, 0)).timelineBucket(now, zone))
        assertEquals(TimelineBucket.LATER, instruction().timelineBucket(now, zone))
    }

    @Test
    fun `follow-up controls grouping and ordering while deadline remains independent`() {
        val instruction = instruction(
            deadline = atLocal(2026, 8, 31, 12, 0),
            followUp = atLocal(2026, 9, 3, 9, 0),
        )

        assertEquals(TimelineBucket.NEXT_7_DAYS, instruction.timelineBucket(now, zone))
        assertEquals(atLocal(2026, 9, 3, 9, 0), instruction.timelineAtEpochMs)
        assertEquals(atLocal(2026, 8, 31, 12, 0), instruction.hardDeadlineAtEpochMs)
    }

    private fun atLocal(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun instruction(deadline: Long? = null, followUp: Long? = null) = Instruction(
        id = "id",
        personId = null,
        direction = Direction.SELF,
        status = Status.TO_DO,
        source = Source.TEXT,
        priority = Priority.NORMAL,
        title = "Legacy title",
        rawText = "Original capture",
        dueAt = null,
        capturedAt = "2026-08-30T00:00:00Z",
        createdAt = "2026-08-30T00:00:00Z",
        updatedAt = "2026-08-30T00:00:00Z",
        actionSummary = "Action",
        hardDeadlineAtEpochMs = deadline,
        followUpAtEpochMs = followUp,
    )
}
