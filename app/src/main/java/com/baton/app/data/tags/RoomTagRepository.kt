package com.baton.app.data.tags

import com.baton.app.data.local.InstructionTagDao
import com.baton.app.data.local.TagDao
import com.baton.app.data.local.entities.InstructionTagCrossRef
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.local.entities.TagEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3-T7: local tag repository. UI reads from Room (Flow); writes go
 * through Room + the outbox (the tag is auto-created on first sight
 * of a person, designation, station, FIR number, or `#tag`).
 *
 * **Why a repository and not just a DAO:** the tag picker needs the
 * Cloud ↔ Room sync to keep the local list in lock-step with the
 * server's `usage_count`. The instruction_tags join table is a
 * write-through mirror too — the LLM extractor produces a list of
 * `(tagId, instructionId)` pairs that must land on the server.
 */
@Singleton
open class RoomTagRepository @Inject constructor(
    private val tagDao: TagDao,
    private val instructionTagDao: InstructionTagDao,
    private val supabase: SupabaseClient,
) {

    fun observeAll(): Flow<List<Tag>> = tagDao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }

    fun observeForInstruction(instructionId: String): Flow<List<Tag>> =
        instructionTagDao.observeForInstruction(instructionId)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * M3-T7: pull the user's tags + the tags for each open
     * instruction from Supabase and upsert into Room. Called on
     * app start (after auth) and on every Realtime `Change.Tags`
     * event so the local picker reflects the full user dataset.
     */
    suspend fun refreshFromNetwork() {
        val tagRows: List<TagRow> = supabase.postgrest
            .from("tags")
            .select(Columns.ALL)
            .decodeList()
        tagDao.upsertAll(tagRows.map { it.toEntity() })
    }

    /**
     * M3-T7: attach a list of tag IDs to an instruction locally.
     * The Cloud ↔ Room sync for this join is handled by the
     * `instruction_tags` RLS policy on the server; locally we just
     * need the local mirror so the picker can render.
     */
    suspend fun attachToInstruction(instructionId: String, tagIds: List<String>) {
        instructionTagDao.attachAll(
            tagIds.map { InstructionTagCrossRef(instructionId = instructionId, tagId = it) }
        )
    }

    suspend fun attachOneToInstruction(instructionId: String, tagId: String) {
        instructionTagDao.attach(InstructionTagCrossRef(instructionId = instructionId, tagId = tagId))
    }

    suspend fun detachAllFromInstruction(instructionId: String) {
        instructionTagDao.detachAllForInstruction(instructionId)
    }

    /** Find-or-create a FREE tag with the given name (used by the
     *  LLM extraction when a `#tag` surfaces). */
    suspend fun findOrCreateFree(name: String, now: () -> String = { java.time.Instant.now().toString() }): Tag? {
        val existing = tagDao.findByNameAndKind(name, TagKind.FREE.name)
        if (existing != null) return existing.toDomain()
        val newTag = Tag(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            kind = TagKind.FREE,
            color = null,
            usageCount = 0,
            lastUsedAt = null,
            createdAt = now(),
            updatedAt = now(),
        )
        tagDao.upsert(newTag.toEntity(SyncStatus.PENDING_INSERT))
        return newTag
    }
}

internal fun Tag.toEntity(syncStatus: String = SyncStatus.SYNCED): TagEntity = TagEntity(
    id = id,
    name = name,
    kind = kind.name,
    color = color,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
    userId = "",  // filled by the RLS-aware insert path on the server
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

internal fun TagRow.toEntity(syncStatus: String = SyncStatus.SYNCED): TagEntity = TagEntity(
    id = id,
    name = name,
    kind = kind.name,
    color = color,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
    userId = "",
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = syncStatus,
)

internal fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    kind = runCatching { TagKind.valueOf(kind) }.getOrDefault(TagKind.FREE),
    color = color,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
