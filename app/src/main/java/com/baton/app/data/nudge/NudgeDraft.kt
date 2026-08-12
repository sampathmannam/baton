package com.baton.app.data.nudge

import com.baton.app.data.instructions.Instruction
import com.baton.app.data.local.NudgeDraftDao
import com.baton.app.data.local.entities.NudgeDraftEntity
import com.baton.app.data.local.entities.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M4-T4: nudge draft generator. Template-based v1; the on-device
 * llama.cpp LLM is a refinement path that doesn't change the
 * [NudgeDraftEntity] shape.
 */
@Singleton
open class NudgeDraftGenerator @Inject constructor(
    private val dao: NudgeDraftDao,
) {

    fun observeFor(instructionId: String): Flow<List<NudgeDraft>> =
        dao.observeForInstruction(instructionId)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun generate(instruction: Instruction, personName: String?): NudgeDraft {
        val draftText = buildTemplate(instruction = instruction, personName = personName)
        val now = java.time.Instant.now().toString()
        val row = NudgeDraftEntity(
            id = java.util.UUID.randomUUID().toString(),
            instructionId = instruction.id,
            draftText = draftText,
            status = "DRAFT",
            sentVia = null,
            sentAt = null,
            createdAt = now,
            syncStatus = SyncStatus.SYNCED,
        )
        dao.upsert(row)
        return row.toDomain()
    }

    suspend fun updateText(id: String, text: String) {
        dao.updateText(id, text)
    }

    suspend fun markSent(id: String, sentVia: String) {
        val now = java.time.Instant.now().toString()
        dao.markSent(id, sentVia, now)
    }

    suspend fun cancel(id: String) {
        dao.cancel(id)
    }

    private fun buildTemplate(instruction: Instruction, personName: String?): String {
        val name = personName?.takeIf { it.isNotBlank() } ?: "there"
        val title = instruction.title.ifBlank { instruction.rawText.take(60) }
        return "Hi $name — following up on \"$title\". Let me know if you need anything from me."
    }
}

data class NudgeDraft(
    val id: String,
    val instructionId: String,
    val draftText: String,
    val status: String,
    val sentVia: String?,
    val sentAt: String?,
    val createdAt: String,
)

internal fun NudgeDraftEntity.toDomain(): NudgeDraft = NudgeDraft(
    id = id,
    instructionId = instructionId,
    draftText = draftText,
    status = status,
    sentVia = sentVia,
    sentAt = sentAt,
    createdAt = createdAt,
)
