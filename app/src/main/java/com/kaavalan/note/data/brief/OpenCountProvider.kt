package com.kaavalan.note.data.brief

import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.entities.InstructionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.2: small one-shot read that returns the count of open
 * instructions for the user. Used by [BriefNotifierWorker] to
 * produce a non-zero count in the morning-brief notification
 * (the previous version passed an empty list to the brief
 * generator and always surfaced "Nothing on your plate.").
 *
 * The Today screen's reactive [BriefGenerator.observeDailyBrief]
 * remains the source of truth for the in-app brief — this is
 * only the head-count for the push notification's body text.
 *
 * "Open" means status in the three "needs attention" buckets
 * the spec calls out: OPEN, ACK_PENDING, IN_PROGRESS.
 * CARRIED_OVER is counted separately when the brief is built
 * (it has its own section).
 */
@Singleton
class OpenCountProvider @Inject constructor(
    private val instructionDao: InstructionDao,
) {
    suspend fun todayOpenCount(): Int {
        val open = listOf(
            com.kaavalan.note.data.instructions.Status.OPEN.name,
            com.kaavalan.note.data.instructions.Status.ACK_PENDING.name,
            com.kaavalan.note.data.instructions.Status.IN_PROGRESS.name,
        )
        return instructionDao.snapshot().count { it.status in open }
    }
}
