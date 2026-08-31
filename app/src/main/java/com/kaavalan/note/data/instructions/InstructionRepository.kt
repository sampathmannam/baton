package com.kaavalan.note.data.instructions

import kotlinx.coroutines.flow.Flow

interface InstructionRepository {
    fun observeTimeline(): Flow<List<Instruction>> = stage1ImplementationRequired()
    fun observeForPerson(personId: String): Flow<List<Instruction>> = stage1ImplementationRequired()
    suspend fun create(draft: InstructionDraft): Instruction = stage1ImplementationRequired()
    suspend fun update(id: String, expectedUpdatedAt: String, patch: InstructionPatch): UpdateResult =
        stage1ImplementationRequired()
    suspend fun markDone(id: String, completedAtEpochMs: Long): Unit = stage1ImplementationRequired()
    suspend fun archive(id: String, archivedAtEpochMs: Long): Unit = stage1ImplementationRequired()
    suspend fun deletePermanently(id: String): Unit = stage1ImplementationRequired()

    // Stage 1 compatibility for callers migrated in later stages.
    suspend fun create(
        personId: String?,
        source: Source,
        priority: Priority,
        title: String,
        rawText: String,
        dueAt: String?,
    ): Instruction

    suspend fun fetchAll(): List<Instruction>

    suspend fun update(
        id: String,
        status: Status,
        completedAt: String?,
        droppedReason: String?,
        isSensitive: Boolean,
    ): Instruction

    suspend fun markDone(id: String, completedAt: String)
    suspend fun markDropped(id: String, reason: String?, at: String)

    suspend fun createWithAudience(
        personId: String?,
        audience: AudienceRef?,
        source: Source,
        priority: Priority,
        title: String,
        rawText: String,
        dueAt: String?,
        dueAtMs: Long?,
        channel: String?,
    ): Instruction

    suspend fun setAudience(id: String, audience: AudienceRef?)
    suspend fun setDueChip(id: String, dueAtMs: Long?)
    suspend fun setChannel(id: String, channel: String?)
}

private fun <T> stage1ImplementationRequired(): T =
    error("Legacy InstructionRepository test double does not implement the Stage 1 contract")
