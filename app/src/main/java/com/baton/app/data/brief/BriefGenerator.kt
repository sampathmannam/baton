package com.baton.app.data.brief

import com.baton.app.data.instructions.Instruction
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.entities.InstructionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M4-T1: brief generator. Reads the user's open instructions from
 * the local Room mirror (M2-T6) and groups them into the three
 * sections per spec §8.1.
 *
 * **Local generation.** The spec calls for a Supabase cron at 4 AM
 * user-local that writes a `daily_briefs` row. For v1 we generate
 * the brief client-side from the local mirror on demand — same
 * result, no round-trip, no cron to manage. The `daily_briefs`
 * table exists in the schema (for future server-side push) and
 * the spec still wins on content shape.
 *
 * **No counts in titles.** Per spec §3.3, the section titles are
 * the labels, not "3 things waiting on you".
 */
@Singleton
open class BriefGenerator @Inject constructor(
    private val instructionDao: InstructionDao,
) {

    /**
     * Build a brief for [date] (defaults to today in the device
     * timezone) from the open instructions in the local mirror.
     * Reactive: a Room update re-emits a new brief.
     */
    fun observeDailyBrief(
        type: BriefType = BriefType.MORNING,
        date: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    ): Flow<DailyBrief> =
        instructionDao.observeAll()
            .map { entities ->
                val instructions = entities.map { it.toDomain() }
                build(type = type, date = date, instructions = instructions, now = Instant.now())
            }

    /**
     * Synchronous build for tests and the review-screen on-demand
     * path. The reactive [observeDailyBrief] is the production path.
     */
    fun build(
        type: BriefType,
        date: LocalDate,
        instructions: List<Instruction>,
        now: Instant = Instant.now(),
    ): DailyBrief {
        val needs = instructions
            .filter { it.needsYouToday(date, now) }
            .sortedWith(needsYouComparator)
        val waiting = instructions
            .filter { it.waitingOnOthers() }
            .sortedBy { it.updatedAt }  // oldest first
        val carried = instructions
            .filter { it.carriedOver(now) }
            .sortedBy { it.updatedAt }  // oldest first
        return DailyBrief(
            date = date.toString(),
            type = type,
            needsYouToday = needs,
            waitingOnOthers = waiting,
            carriedOver = carried,
        )
    }

    // M4-T1: HIGH first, then by dueAt ASC NULLS LAST, then oldest.
    private val needsYouComparator: Comparator<Instruction> =
        compareByDescending<Instruction> { it.priority == com.baton.app.data.instructions.Priority.HIGH }
            .thenBy(nullsLast<String>()) { it.dueAt }
            .thenBy { it.updatedAt }
}

private fun InstructionEntity.toDomain(): Instruction = Instruction(
    id = id,
    personId = personId,
    direction = runCatching { com.baton.app.data.instructions.Direction.valueOf(direction) }
        .getOrDefault(com.baton.app.data.instructions.Direction.OUTGOING),
    status = runCatching { com.baton.app.data.instructions.Status.valueOf(status) }
        .getOrDefault(com.baton.app.data.instructions.Status.OPEN),
    source = runCatching { com.baton.app.data.instructions.Source.valueOf(source) }
        .getOrDefault(com.baton.app.data.instructions.Source.TEXT),
    priority = runCatching { com.baton.app.data.instructions.Priority.valueOf(priority) }
        .getOrDefault(com.baton.app.data.instructions.Priority.NORMAL),
    title = title,
    rawText = rawText,
    dueAt = dueAt,
    capturedAt = capturedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// helper for nullsLast on nullable String
private fun <T : Comparable<T>> nullsLast(): Comparator<in T?> = Comparator { a, b ->
    when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> a.compareTo(b)
    }
}
