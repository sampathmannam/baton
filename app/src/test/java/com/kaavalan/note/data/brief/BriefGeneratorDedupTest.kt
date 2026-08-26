package com.kaavalan.note.data.brief

import com.kaavalan.note.data.instructions.Direction
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.Source
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.local.InstructionDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * v1.4.2: regression test for DATA-FINDING-03.
 *
 * `BriefGenerator.build()` used to put the same row in BOTH
 * `needsYouToday` and `carriedOver` for INCOMING OPEN rows between
 * 8 and 30 days old. The Today list showed them twice. The fix
 * drops any carriedOver row from needsYouToday before assembling
 * the brief.
 *
 * Predicate recap (see [DailyBrief.kt]):
 *  - needsYouToday: dir IN/INCOMING|SELF, status OPEN/ACK_PENDING/
 *    IN_PROGRESS, AND (dueAt == today OR (HIGH && OPEN) OR
 *    (now - updatedAt) > 7 days)
 *  - carriedOver:   dir IN/INCOMING|SELF, status == OPEN, AND
 *    8 <= (now - updatedAt).days <= 30
 *
 * Overlap window: 8-30 days, INCOMING OPEN, no dueAt, not HIGH
 * (without dedup, the row matches both predicates).
 */
class BriefGeneratorDedupTest {

    private val dao: InstructionDao = mockk(relaxed = true)
    private val gen = BriefGenerator(dao)

    private val today: LocalDate = LocalDate.of(2024, 6, 15)
    // Pin `now` to midday today in the device timezone so test
    // results are independent of when the suite runs.
    private val now: Instant = today
        .atStartOfDay(ZoneId.systemDefault())
        .plus(12, ChronoUnit.HOURS)
        .toInstant()

    private fun instruction(
        id: String,
        updatedAt: Instant,
        direction: Direction = Direction.INCOMING,
        status: Status = Status.OPEN,
        priority: Priority = Priority.NORMAL,
        dueAt: Instant? = null,
    ): Instruction = Instruction(
        id = id,
        personId = null,
        direction = direction,
        status = status,
        source = Source.TEXT,
        priority = priority,
        title = "row-$id",
        rawText = "raw-$id",
        dueAt = dueAt?.toString(),
        capturedAt = updatedAt.toString(),
        createdAt = updatedAt.toString(),
        updatedAt = updatedAt.toString(),
    )

    /**
     * The 8-30 day INCOMING OPEN row is the exact overlap case.
     * Before the fix it appeared in BOTH needsYouToday (via >7d
     * rule) and carriedOver (8..30d window). The fix moves it
     * out of needsYouToday and into carriedOver only.
     */
    @Test
    fun `12-day-old INCOMING OPEN row appears in carriedOver only`() {
        val row = instruction(id = "overlap-12d", updatedAt = now.minus(12, ChronoUnit.DAYS))
        val brief = gen.build(BriefType.MORNING, today, listOf(row), now)

        assertEquals(
            "12-day row must live in carriedOver",
            listOf("overlap-12d"),
            brief.carriedOver.map { it.id },
        )
        assertTrue(
            "DATA-FINDING-03: 12-day row must NOT also be in needsYouToday",
            brief.needsYouToday.none { it.id == "overlap-12d" },
        )
        // The total is still 1 — the row appears once, not twice.
        assertEquals(
            "row must render exactly once across the brief",
            1,
            brief.needsYouToday.size + brief.carriedOver.size,
        )
    }

    /**
     * A 3-day-old INCOMING OPEN row is below the carriedOver
     * floor (8d). It qualifies for needsYouToday via the HIGH
     * priority rule. The dedup must not touch it — it's not in
     * carriedOver, so it stays in needsYouToday only.
     */
    @Test
    fun `3-day-old INCOMING OPEN HIGH row appears in needsYouToday only`() {
        val row = instruction(
            id = "recent-3d",
            updatedAt = now.minus(3, ChronoUnit.DAYS),
            priority = Priority.HIGH,
        )
        val brief = gen.build(BriefType.MORNING, today, listOf(row), now)

        assertTrue(
            "3-day HIGH row must be in needsYouToday",
            brief.needsYouToday.any { it.id == "recent-3d" },
        )
        assertTrue(
            "3-day row is below carriedOver's 8-day floor",
            brief.carriedOver.none { it.id == "recent-3d" },
        )
    }

    /**
     * 35-day-old INCOMING OPEN row is past the carriedOver cap
     * (30d). It still qualifies for needsYouToday (35d > 7d) but
     * NOT for carriedOver. The dedup must not affect it — it's
     * not in carriedOver.
     */
    @Test
    fun `35-day-old INCOMING OPEN row appears in needsYouToday only`() {
        val row = instruction(id = "stale-35d", updatedAt = now.minus(35, ChronoUnit.DAYS))
        val brief = gen.build(BriefType.MORNING, today, listOf(row), now)

        assertTrue(
            "35-day row is in needsYouToday (>7d rule)",
            brief.needsYouToday.any { it.id == "stale-35d" },
        )
        assertTrue(
            "35-day row is past carriedOver's 30-day cap and must be dropped silently",
            brief.carriedOver.none { it.id == "stale-35d" },
        )
    }

    /**
     * Two 12-day-old INCOMING OPEN rows would, before the fix,
     * render 4 times across needsYouToday + carriedOver (2 each).
     * After the fix they render 2 times (both in carriedOver,
     * neither in needsYouToday). No double-render.
     */
    @Test
    fun `two rows of the same age do not double-render`() {
        val rows = listOf(
            instruction(id = "twin-a", updatedAt = now.minus(12, ChronoUnit.DAYS)),
            instruction(id = "twin-b", updatedAt = now.minus(12, ChronoUnit.DAYS)),
        )
        val brief = gen.build(BriefType.MORNING, today, rows, now)

        assertEquals(
            "both 12-day rows in carriedOver",
            setOf("twin-a", "twin-b"),
            brief.carriedOver.map { it.id }.toSet(),
        )
        assertTrue(
            "neither 12-day row in needsYouToday",
            brief.needsYouToday.isEmpty(),
        )
        assertEquals(
            "two rows, two renders — no duplication",
            2,
            brief.carriedOver.size,
        )
    }

    /**
     * Sanity: when a row is in carriedOver, it must not also
     * appear in needsYouToday. The cross-section invariant.
     */
    @Test
    fun `no id appears in both needsYouToday and carriedOver`() {
        val rows = listOf(
            // overlap: in carriedOver
            instruction(id = "a-12d", updatedAt = now.minus(12, ChronoUnit.DAYS)),
            instruction(id = "b-20d", updatedAt = now.minus(20, ChronoUnit.DAYS)),
            // needsYouToday only (HIGH, recent)
            instruction(
                id = "c-high",
                updatedAt = now.minus(2, ChronoUnit.DAYS),
                priority = Priority.HIGH,
            ),
            // needsYouToday only (past cap, but still >7d)
            instruction(id = "d-35d", updatedAt = now.minus(35, ChronoUnit.DAYS)),
        )
        val brief = gen.build(BriefType.MORNING, today, rows, now)

        val needsIds = brief.needsYouToday.map { it.id }.toSet()
        val carriedIds = brief.carriedOver.map { it.id }.toSet()
        assertTrue(
            "needsYouToday ∩ carriedOver must be empty (was: $needsIds ∩ $carriedIds)",
            needsIds.intersect(carriedIds).isEmpty(),
        )
        assertFalse(
            "a-12d must not be in needsYouToday",
            "a-12d" in needsIds,
        )
        assertFalse(
            "b-20d must not be in needsYouToday",
            "b-20d" in needsIds,
        )
        assertTrue("a-12d must be in carriedOver", "a-12d" in carriedIds)
        assertTrue("b-20d must be in carriedOver", "b-20d" in carriedIds)
    }
}
