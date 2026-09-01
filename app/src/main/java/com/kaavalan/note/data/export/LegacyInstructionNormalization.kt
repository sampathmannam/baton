package com.kaavalan.note.data.export

import java.time.Instant

internal data class LegacyInstructionNormalization(
    val status: String,
    val hardDeadlineAtEpochMs: Long?,
    val archivedAtEpochMs: Long?,
    val migrationReviewRequired: Boolean,
    val migrationMetadata: String?,
)

internal fun normalizeLegacyInstruction(
    status: String,
    direction: String,
    dueAt: String?,
    hardDeadlineAtEpochMs: Long?,
    archivedAtEpochMs: Long?,
    updatedAt: String,
    capturedAt: String,
    createdAt: String,
    migrationReviewRequired: Boolean,
    migrationMetadata: String?,
): LegacyInstructionNormalization {
    val transitional = status in setOf("ACK_PENDING", "IN_PROGRESS", "WAITING_ON_OTHER")
    val dropped = status == "DROPPED"
    return LegacyInstructionNormalization(
        status = when {
            status == "DONE" -> "DONE"
            transitional && direction == "OUTGOING" -> "WAITING"
            status == "WAITING" -> "WAITING"
            else -> "TO_DO"
        },
        hardDeadlineAtEpochMs = hardDeadlineAtEpochMs ?: dueAt.toEpochMillisOrNull(),
        archivedAtEpochMs = when {
            !dropped -> archivedAtEpochMs
            archivedAtEpochMs != null -> archivedAtEpochMs
            else -> updatedAt.toEpochMillisOrNull()
                ?: capturedAt.toEpochMillisOrNull()
                ?: createdAt.toEpochMillisOrNull()
                ?: 0L
        },
        migrationReviewRequired = migrationReviewRequired ||
            (transitional && direction !in setOf("OUTGOING", "INCOMING", "SELF")),
        migrationMetadata = if (dropped) "legacy_status=DROPPED" else migrationMetadata,
    )
}

private fun String?.toEpochMillisOrNull(): Long? =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
