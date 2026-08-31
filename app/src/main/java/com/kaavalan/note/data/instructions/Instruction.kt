package com.kaavalan.note.data.instructions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/**
 * M1 domain model for a tracked instruction. Mirrors the `instructions`
 * table created in M0's `0001_init.sql`. See spec §4.2.
 *
 *  - [direction] is `OUTGOING` for the M1 capture-and-save flow
 *    (the user is delegating / tracking an instruction they gave).
 *  - [status] is `OPEN` on creation; the M2 nudge flow moves it through
 *    `ACK_PENDING` → `IN_PROGRESS` → `DONE`.
 *  - [source] is `TEXT` for M1 text captures. `VOICE` and `PHOTO` land
 *    in M2 alongside the Whisper / ML Kit OCR pipelines.
 *  - [title] is a 5-7 word human-readable label. M1 derives it from
 *    `action + " — " + person` (or just `action` if no person is named).
 *    M2 will have the LLM produce a cleaner title in the prompt.
 *  - [rawText] is the verbatim capture the user typed. The audit trail.
 *  - [dueAt] is the ISO 8601 timestamp from the LLM's `due_at`. `null`
 *    if the user didn't mention a time cue.
 *  - [capturedAt] is `now()` at create time (we set it explicitly because
 *    the column has no DB default).
 */
data class Instruction(
    val id: String,
    @SerialName("person_id") val personId: String?,
    val direction: Direction,
    val status: Status,
    val source: Source,
    val priority: Priority,
    val title: String,
    @SerialName("raw_text") val rawText: String,
    @SerialName("due_at") val dueAt: String?,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    // v1.0: spec §13. When true, this row never syncs to Supabase.
    @SerialName("is_sensitive") val isSensitive: Boolean = false,
    // v1.1: lifecycle fields. Set by markDone / markDropped.
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("dropped_reason") val droppedReason: String? = null,
    // v2.0 (Hierarchy): the audience pointer. See [AudienceRef].
    @SerialName("audience") val audience: AudienceRef? = null,
    // v2.0 (Hierarchy): manual due-chip (epoch millis).
    @SerialName("due_at_ms") val dueAtMs: Long? = null,
    // v2.0 (Hierarchy): outbound delivery channel.
    @SerialName("channel") val channel: String? = null,
    @SerialName("action_summary") val actionSummary: String = title,
    @SerialName("hard_deadline_at_epoch_ms") val hardDeadlineAtEpochMs: Long? = dueAtMs,
    @SerialName("follow_up_at_epoch_ms") val followUpAtEpochMs: Long? = null,
    @SerialName("archived_at_epoch_ms") val archivedAtEpochMs: Long? = null,
    @SerialName("responsible_person_id") val responsiblePersonId: String? = null,
    @SerialName("group_label") val groupLabel: String? = null,
    @SerialName("local_revision") val localRevision: Long = 1,
    @SerialName("migration_review_required") val migrationReviewRequired: Boolean = false,
    @SerialName("migration_metadata") val migrationMetadata: String? = null,
)

/** Wire values match the `instruction_direction` Postgres enum. */
enum class Direction { INCOMING, OUTGOING, SELF }

enum class Status {
    TO_DO,
    WAITING,
    DONE;

    companion object {
        @Deprecated("Stage 1 compatibility; use TO_DO") val OPEN = TO_DO
        @Deprecated("Stage 1 compatibility; use WAITING") val ACK_PENDING = WAITING
        @Deprecated("Stage 1 compatibility; use WAITING") val IN_PROGRESS = WAITING
        @Deprecated("Stage 1 compatibility; use WAITING") val WAITING_ON_OTHER = WAITING
        @Deprecated("Stage 1 compatibility; use TO_DO") val CARRIED_OVER = TO_DO
        @Deprecated("Stage 1 compatibility; archived records keep a current status") val DROPPED = DONE
    }
}

/** Wire values match the `instruction_source` Postgres enum. */
enum class Source { VOICE, TEXT, PHOTO, MCP }

enum class Priority {
    NORMAL,
    URGENT;

    companion object {
        @Deprecated("Stage 1 compatibility; use NORMAL") val LOW = NORMAL
        @Deprecated("Stage 1 compatibility; use URGENT") val HIGH = URGENT
    }
}

enum class TimelineBucket { LATE, TODAY, NEXT_7_DAYS, LATER }

val Instruction.timelineAtEpochMs: Long?
    get() = followUpAtEpochMs ?: hardDeadlineAtEpochMs

fun Instruction.timelineBucket(now: Instant, zoneId: ZoneId): TimelineBucket {
    val actionDate = timelineAtEpochMs
        ?.let(Instant::ofEpochMilli)
        ?.atZone(zoneId)
        ?.toLocalDate()
        ?: return TimelineBucket.LATER
    val today = now.atZone(zoneId).toLocalDate()
    return when {
        actionDate.isBefore(today) -> TimelineBucket.LATE
        actionDate == today -> TimelineBucket.TODAY
        !actionDate.isAfter(today.plusDays(7)) -> TimelineBucket.NEXT_7_DAYS
        else -> TimelineBucket.LATER
    }
}

data class InstructionDraft(
    val rawText: String,
    val actionSummary: String,
    val personId: String? = null,
    val responsiblePersonId: String? = null,
    val groupLabel: String? = null,
    val status: Status = Status.TO_DO,
    val priority: Priority = Priority.NORMAL,
    val hardDeadlineAtEpochMs: Long? = null,
    val followUpAtEpochMs: Long? = null,
    val source: Source = Source.TEXT,
    val confirmedAiProposal: Boolean = false,
)

data class InstructionPatch(
    val actionSummary: String,
    val status: Status,
    val priority: Priority,
    val hardDeadlineAtEpochMs: Long?,
    val followUpAtEpochMs: Long?,
    val personId: String?,
    val responsiblePersonId: String?,
    val groupLabel: String?,
    val confirmedAiProposal: Boolean = false,
)

sealed interface UpdateResult {
    data class Updated(val instruction: Instruction) : UpdateResult
    data class Conflict(val current: Instruction) : UpdateResult
    data object NotFound : UpdateResult
}

internal fun parseStatus(value: String): Status = when (value) {
    "TO_DO", "OPEN", "CARRIED_OVER" -> Status.TO_DO
    "WAITING", "ACK_PENDING", "IN_PROGRESS", "WAITING_ON_OTHER" -> Status.WAITING
    "DONE", "DROPPED" -> Status.DONE
    else -> Status.TO_DO
}

internal fun parsePriority(value: String): Priority = when (value) {
    "URGENT", "HIGH" -> Priority.URGENT
    else -> Priority.NORMAL
}
