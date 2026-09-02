package com.kaavalan.note.ui.timeline

import com.kaavalan.note.data.instructions.Direction
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.Source
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.instructions.TimelineBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimelineUiStateTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val zone = ZoneId.of("UTC")

    @Test
    fun `groups one ordered list into Late Today Next 7 days and Later`() {
        val sections = buildTimelineSections(
            instructions = listOf(
                instruction("later", Status.TO_DO, "2026-09-09T10:00:00Z"),
                instruction("today", Status.WAITING, "2026-09-01T12:00:00Z"),
                instruction("late", Status.TO_DO, "2026-08-31T12:00:00Z"),
                instruction("next", Status.DONE, "2026-09-08T12:00:00Z"),
                instruction("undated", Status.TO_DO, null),
            ),
            filter = TimelineFilter.ALL,
            now = now,
            zoneId = zone,
        )

        assertEquals(
            listOf(
                TimelineBucket.LATE,
                TimelineBucket.TODAY,
                TimelineBucket.NEXT_7_DAYS,
                TimelineBucket.LATER,
            ),
            sections.map { it.bucket },
        )
        assertEquals(listOf("later", "undated"), sections.last().instructions.map { it.id })
    }

    @Test
    fun `filters All To do Waiting and Done without changing urgency`() {
        val instructions = listOf(
            instruction("todo", Status.TO_DO, null, Priority.URGENT),
            instruction("waiting", Status.WAITING, null),
            instruction("done", Status.DONE, null),
        )

        assertEquals(3, buildTimelineSections(instructions, TimelineFilter.ALL, now, zone).sumOf { it.instructions.size })
        assertEquals(listOf("todo"), idsFor(instructions, TimelineFilter.TO_DO))
        assertEquals(listOf("waiting"), idsFor(instructions, TimelineFilter.WAITING))
        assertEquals(listOf("done"), idsFor(instructions, TimelineFilter.DONE))
        assertTrue(instructions.first().priority == Priority.URGENT)
    }

    private fun idsFor(instructions: List<Instruction>, filter: TimelineFilter) =
        buildTimelineSections(instructions, filter, now, zone)
            .flatMap { it.instructions }
            .map { it.id }

    private fun instruction(
        id: String,
        status: Status,
        actionAt: String?,
        priority: Priority = Priority.NORMAL,
    ) = Instruction(
        id = id,
        personId = null,
        direction = Direction.SELF,
        status = status,
        source = Source.TEXT,
        priority = priority,
        title = id,
        rawText = id,
        dueAt = null,
        capturedAt = "2026-09-01T00:00:00Z",
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
        actionSummary = id,
        followUpAtEpochMs = actionAt?.let(Instant::parse)?.toEpochMilli(),
    )
}
