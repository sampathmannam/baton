package com.baton.app.data.brief

import com.baton.app.data.instructions.Direction
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.instructions.Status
import com.baton.app.data.local.InstructionDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * v1.8.0 (PROD-READINESS-P1-#4): the privacy-gate test.
 * Spec §13: rows with `is_sensitive = true` MUST NOT appear in
 * the daily brief. The filter lives in
 * [BriefGenerator.build] and is the UI-side backstop to the
 * sync-engine filter (which prevents the row from leaving the
 * device in the first place). A row that is "open" + "needs
 * you" but is_sensitive is intentionally not surfaced — the
 * user's most sensitive cases are not what they want to see
 * at 8am.
 */
class BriefGeneratorPrivacyTest {

    private val dao: InstructionDao = mockk(relaxed = true)
    private val gen = BriefGenerator(dao)
    private val date: LocalDate = LocalDate.parse("2026-08-21")
    private val now: Instant = date.atTime(8, 0).atZone(ZoneId.of("UTC")).toInstant()

    private fun instr(
        id: String,
        isSensitive: Boolean,
        updatedAtDaysAgo: Long = 0L,
        dueAt: String? = null,
    ): Instruction = Instruction(
        id = id,
        personId = "p1",
        direction = Direction.INCOMING,
        status = Status.OPEN,
        source = Source.TEXT,
        priority = Priority.NORMAL,
        title = "Test $id",
        rawText = "Test $id",
        dueAt = dueAt,
        capturedAt = now.minus(updatedAtDaysAgo, ChronoUnit.DAYS).toString(),
        createdAt = now.minus(updatedAtDaysAgo, ChronoUnit.DAYS).toString(),
        updatedAt = now.minus(updatedAtDaysAgo, ChronoUnit.DAYS).toString(),
        isSensitive = isSensitive,
    )

    @Test
    fun `sensitive instruction is excluded from needsYouToday`() {
        // Use priority=HIGH (matches the second branch of
        // needsYouToday: "priority = HIGH AND status = OPEN").
        // 0-day-old rows avoid the carriedOver 8-30d dedup
        // window. DueAt is null so the date-equality branch
        // is also skipped. Both rows match needsYouToday on
        // the priority rule; the privacy gate then drops the
        // sensitive one.
        val public = instr(
            id = "pub", isSensitive = false,
            updatedAtDaysAgo = 0L,
        ).copy(priority = Priority.HIGH)
        val sensitive = instr(
            id = "sec", isSensitive = true,
            updatedAtDaysAgo = 0L,
        ).copy(priority = Priority.HIGH)
        val brief = gen.build(BriefType.MORNING, date, listOf(public, sensitive), now)
        val ids = brief.needsYouToday.map { it.id }
        assertTrue("public instruction must be in needsYouToday, got $ids", "pub" in ids)
        assertTrue("sensitive instruction must NOT be in needsYouToday, got $ids", "sec" !in ids)
    }

    @Test
    fun `sensitive instruction is excluded from carriedOver`() {
        val sensitive = instr(id = "sec", isSensitive = true, updatedAtDaysAgo = 10L)
        val brief = gen.build(BriefType.MORNING, date, listOf(sensitive), now)
        assertTrue(
            "sensitive instruction must NOT be in carriedOver",
            brief.carriedOver.none { it.id == "sec" },
        )
    }

    @Test
    fun `sensitive instruction is excluded from waitingOnOthers`() {
        val sensitive = instr(
            id = "sec",
            isSensitive = true,
            dueAt = null,
            updatedAtDaysAgo = 3L,
        )
        val brief = gen.build(BriefType.MORNING, date, listOf(sensitive), now)
        assertTrue(
            "sensitive instruction must NOT be in waitingOnOthers",
            brief.waitingOnOthers.none { it.id == "sec" },
        )
    }

    @Test
    fun `brief with only sensitive instructions is empty across all sections`() {
        // Mix the three trigger paths so each section is
        // exercised: HIGH-priority 0d for needsYouToday,
        // 10d for carriedOver, OUTGOING for waitingOnOthers.
        val high0d = instr(id = "s1", isSensitive = true, updatedAtDaysAgo = 0L)
            .copy(priority = Priority.HIGH)
        val carried = instr(id = "s2", isSensitive = true, updatedAtDaysAgo = 10L)
        val outgoing = instr(
            id = "s3", isSensitive = true, updatedAtDaysAgo = 3L,
        ).copy(direction = Direction.OUTGOING)
        val brief = gen.build(BriefType.MORNING, date, listOf(high0d, carried, outgoing), now)
        assertEquals("needsYouToday must be empty for all-sensitive brief", 0, brief.needsYouToday.size)
        assertEquals("carriedOver must be empty for all-sensitive brief", 0, brief.carriedOver.size)
        assertEquals("waitingOnOthers must be empty for all-sensitive brief", 0, brief.waitingOnOthers.size)
    }
}
