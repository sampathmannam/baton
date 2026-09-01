package com.kaavalan.note.data.instructions

import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.InstructionFtsDao
import com.kaavalan.note.data.local.SyncQueueDao
import com.kaavalan.note.data.local.TouchPersonOnActivity
import com.kaavalan.note.data.local.entities.InstructionEntity
import com.kaavalan.note.data.local.entities.InstructionFtsEntity
import com.kaavalan.note.data.local.entities.SyncQueueEntity
import com.kaavalan.note.data.local.entities.SyncStatus
import com.kaavalan.note.di.ApplicationScope
import com.kaavalan.note.data.audit.AuditChainWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M3-T5 + v1.1: local mirror for the [InstructionRepository] contract.
 * The DAO is the source of truth for the People-list badge
 * (`observeOpenCountByPerson`) and the PersonDetailScreen timeline
 * (`observeForPerson`).
 *
 * **v1.5.1 (VAULT-005 fix):** this class now implements
 * [InstructionRepository] end-to-end. v1.5.0 vault mode binds the
 * `InstructionRepository` interface to this class (not the
 * Supabase one), so every capture-and-save flow lands in the
 * local SQLCipher Room DB. The `SupabaseInstructionRepository` is
 * still wired in (as a constructor dep of the sync engine and of
 * the optional `refreshFromNetwork` call) so a future Settings
 * toggle can flip the binding back to cloud sync without a
 * refactor.
 *
 * Each write goes through Room (PENDING_INSERT / PENDING_UPDATE)
 * + the [SyncEngine] outbox. In vault mode the outbox is never
 * drained (the periodic workers are not scheduled), so PENDING
 * rows sit forever — that's fine, they're the durable source of
 * truth locally.
 *
 * `fetchAll()` returns the local mirror. In vault mode this IS the
 * full dataset. In a future cloud mode it would be a snapshot of
 * the locally-cached rows (the post-`refreshFromNetwork` state).
 *
 * **v2.0 (Tier 1.3):** every write also upserts the FTS4 row
 * (free FTS4 entity, no `contentEntity` link).
 *
 * **v1.9.8 (PROD-READINESS-P0-#2 — production blocker fix):**
 * the [create] path now wraps all four writes (main row, FTS row,
 * sync outbox entry, last-interaction touch) in a single Room
 * `withTransaction { ... }` block. The pre-fix version wrote them
 * sequentially across four coroutine steps; a process death or
 * JVM crash between the main-table insert and the FTS upsert left
 * the FTS index pointing at a non-existent rowid (search broken
 * until the next reseed). A crash between the FTS upsert and the
 * sync-queue enqueue left a row that the user could see in the
 * app but that would never reach Supabase. A crash between the
 * sync-queue enqueue and the last-interaction touch left the
 * decay view showing the user as "haven't touched in 30+ days"
 * for a person they had just saved an instruction about.
 *
 * Trade-off: wrapping in a transaction means the FTS row is
 * written AFTER the main row inside the same atomic block. The
 * pre-fix comment worried about "the FTS row never references a
 * missing main row" — that property is still guaranteed by
 * Room: the FTS DAO reads the main row's rowid via
 * [InstructionFtsDao.maxInstructionRowid] inside the same
 * transaction, so a partial read is impossible. Crash recovery
 * is now atomic: either all four writes land, or none do, and
 * the user sees a consistent local state.
 */
@Singleton
open class RoomInstructionRepository @Inject constructor(
    private val db: com.kaavalan.note.data.local.AppDatabase,
    private val dao: InstructionDao,
    private val ftsDao: InstructionFtsDao,
    private val syncQueueDao: SyncQueueDao,
    private val touchOnActivity: TouchPersonOnActivity,
    @ApplicationScope private val appScope: CoroutineScope,
    private val auditChainWriter: AuditChainWriter? = null,
) : InstructionRepository {

    /**
     * Pull every row from the network and upsert into Room with
     * [SyncStatus.SYNCED]. Called once on app start (after auth) and
     * on every Realtime `Change.Instructions` event so the local
     * open-instruction count reflects the full user dataset.
     *
     * Replaces all locally-tracked rows that came from the server
     * (status = SYNCED) and inserts any new ones. Locally-created
     * rows with status = PENDING_INSERT are preserved (they haven't
     * been sent to the server yet, so the network doesn't know about
     * them and we don't want a refresh to clobber them).
     */
    suspend fun refreshFromNetwork() {
        // v2.0.0: no remote to refresh from. The function
        // shape is preserved for any future change. Local Room
        // is the source of truth.
    }

    override fun observeTimeline(): Flow<List<Instruction>> =
        dao.observeTimeline().map { rows -> rows.map(InstructionEntity::toDomain) }

    override fun observeForPerson(personId: String): Flow<List<Instruction>> =
        dao.observeForPerson(personId).map { rows -> rows.map(InstructionEntity::toDomain) }

    private fun Instruction.toEntity(): InstructionEntity = InstructionEntity(
        id = id,
        personId = personId,
        direction = direction.name,
        status = status.name,
        source = source.name,
        priority = priority.name,
        title = title,
        rawText = rawText,
        dueAt = dueAt,
        capturedAt = capturedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSensitive = isSensitive,
        syncStatus = SyncStatus.SYNCED,
        // v2.0 (Hierarchy): audience + due chip + channel.
        audienceKind = audience?.kind,
        audienceTarget = audience?.target,
        audienceLabel = audience?.label,
        audienceIsBroadcast = audience?.isBroadcast ?: false,
        dueAtMs = dueAtMs,
        channel = channel,
        actionSummary = actionSummary,
        hardDeadlineAtEpochMs = hardDeadlineAtEpochMs,
        followUpAtEpochMs = followUpAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
        responsiblePersonId = responsiblePersonId,
        groupLabel = groupLabel,
        localRevision = localRevision,
        migrationReviewRequired = migrationReviewRequired,
        migrationMetadata = migrationMetadata,
    )

    override suspend fun create(draft: InstructionDraft): Instruction = createInternal(
        draft = draft,
        dueAt = draft.hardDeadlineAtEpochMs?.let { Instant.ofEpochMilli(it).toString() },
    )

    /**
     * v1.5.1 (VAULT-005): the capture-and-save path now writes
     * locally + enqueues a sync_queue row. In vault mode the
     * outbox is never drained, so the row sits in PENDING_INSERT
     * — the durable source of truth. In a future cloud mode the
     * periodic sync worker (or the per-write fire-and-forget
     * drain) would PUSH this row to Supabase and flip the status
     * to SYNCED.
     *
     * The id is a client-generated UUID (Supabase's `id` column
     * defaults to `gen_random_uuid()`; we send the value in the
     * INSERT so the local + server rows match).
     */
    override suspend fun create(
        personId: String?,
        source: Source,
        priority: Priority,
        title: String,
        rawText: String,
        dueAt: String?,
    ): Instruction = createInternal(
        draft = InstructionDraft(
            rawText = rawText,
            actionSummary = title,
            personId = personId,
            priority = priority,
            hardDeadlineAtEpochMs = dueAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            source = source,
        ),
        dueAt = dueAt,
    )

    private suspend fun createInternal(
        draft: InstructionDraft,
        dueAt: String?,
        audience: AudienceRef? = null,
        dueAtMs: Long? = draft.hardDeadlineAtEpochMs,
        channel: String? = null,
    ): Instruction {
        val now = Instant.now().toString()
        val entity = InstructionEntity(
            id = UUID.randomUUID().toString(),
            personId = draft.personId,
            direction = Direction.OUTGOING.name,
            status = draft.status.name,
            source = draft.source.name,
            priority = draft.priority.name,
            title = draft.actionSummary,
            rawText = draft.rawText,
            dueAt = dueAt,
            capturedAt = now,
            createdAt = now,
            updatedAt = now,
            isSensitive = false,
            syncStatus = SyncStatus.PENDING_INSERT,
            nextActionAt = draft.followUpAtEpochMs,
            audienceKind = audience?.kind,
            audienceTarget = audience?.target,
            audienceLabel = audience?.label,
            audienceIsBroadcast = audience?.isBroadcast ?: false,
            dueAtMs = dueAtMs,
            channel = channel,
            actionSummary = draft.actionSummary,
            hardDeadlineAtEpochMs = draft.hardDeadlineAtEpochMs,
            followUpAtEpochMs = draft.followUpAtEpochMs,
            responsiblePersonId = draft.responsiblePersonId,
            groupLabel = draft.groupLabel,
            localRevision = 1,
        )
        db.withTransaction {
            dao.upsert(entity)
            upsertFts(entity)
            enqueueInsert(entity.id)
            touchOnActivity.touch(draft.personId)
            appendAudit(entity.id, "CREATED", "{\"revision\":1}")
            if (draft.confirmedAiProposal) {
                appendAudit(entity.id, "AI_PROPOSAL_CONFIRMED", "{\"revision\":1}")
            }
        }
        return entity.toDomain()
    }

    /**
     * v1.5.1 (VAULT-005): local snapshot of the mirror. Used by
     * callers that previously relied on a Supabase round-trip.
     * `is_sensitive` rows are filtered out for the same defensive
     * reason as the network path.
     */
    override suspend fun fetchAll(): List<Instruction> =
        dao.snapshot()
            .filter { !it.isSensitive }
            .map { it.toDomain() }

    override suspend fun update(
        id: String,
        expectedUpdatedAt: String,
        patch: InstructionPatch,
    ): UpdateResult = db.withTransaction {
        val current = dao.getById(id) ?: return@withTransaction UpdateResult.NotFound
        if (current.updatedAt != expectedUpdatedAt) {
            return@withTransaction UpdateResult.Conflict(current.toDomain())
        }
        val statusChanged = parseStatus(current.status) != patch.status
        val fieldChanged = current.actionSummary != patch.actionSummary ||
            parsePriority(current.priority) != patch.priority ||
            current.hardDeadlineAtEpochMs != patch.hardDeadlineAtEpochMs ||
            current.followUpAtEpochMs != patch.followUpAtEpochMs ||
            current.personId != patch.personId ||
            current.responsiblePersonId != patch.responsiblePersonId ||
            current.groupLabel != patch.groupLabel
        val now = Instant.now().toString()
        val updated = current.copy(
            personId = patch.personId,
            status = patch.status.name,
            priority = patch.priority.name,
            title = patch.actionSummary,
            dueAt = patch.hardDeadlineAtEpochMs?.let { Instant.ofEpochMilli(it).toString() },
            updatedAt = now,
            nextActionAt = patch.followUpAtEpochMs,
            dueAtMs = patch.hardDeadlineAtEpochMs,
            actionSummary = patch.actionSummary,
            hardDeadlineAtEpochMs = patch.hardDeadlineAtEpochMs,
            followUpAtEpochMs = patch.followUpAtEpochMs,
            responsiblePersonId = patch.responsiblePersonId,
            groupLabel = patch.groupLabel,
            localRevision = current.localRevision + 1,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        dao.updateExisting(updated)
        upsertFts(updated)
        enqueueUpdate(id)
        if (fieldChanged) appendAudit(id, "FIELD_CHANGED", "{\"revision\":${updated.localRevision}}")
        if (statusChanged) appendAudit(id, "STATUS_CHANGED", "{\"status\":\"${updated.status}\",\"revision\":${updated.localRevision}}")
        if (patch.confirmedAiProposal) {
            appendAudit(id, "AI_PROPOSAL_CONFIRMED", "{\"revision\":${updated.localRevision}}")
        }
        UpdateResult.Updated(updated.toDomain())
    }

    override suspend fun markDone(id: String, completedAtEpochMs: Long) {
        mutateLifecycle(id, completedAtEpochMs, "STATUS_CHANGED") { current, at ->
            current.copy(status = Status.DONE.name, completedAt = at)
        }
    }

    override suspend fun archive(id: String, archivedAtEpochMs: Long) {
        mutateLifecycle(id, archivedAtEpochMs, "ARCHIVED") { current, _ ->
            current.copy(archivedAtEpochMs = archivedAtEpochMs)
        }
    }

    suspend fun restore(id: String, restoredAtEpochMs: Long) {
        mutateLifecycle(id, restoredAtEpochMs, "RESTORED") { current, _ ->
            current.copy(archivedAtEpochMs = null)
        }
    }

    override suspend fun deletePermanently(id: String) {
        db.withTransaction {
            dao.getRowId(id)?.let { ftsDao.deleteByRowId(it) }
            dao.deleteById(id)
        }
    }

    private suspend fun mutateLifecycle(
        id: String,
        atEpochMs: Long,
        auditKind: String,
        mutate: (InstructionEntity, String) -> InstructionEntity,
    ) {
        db.withTransaction {
            val current = dao.getById(id) ?: return@withTransaction
            val at = Instant.ofEpochMilli(atEpochMs).toString()
            val updated = mutate(current, at).copy(
                updatedAt = at,
                localRevision = current.localRevision + 1,
                syncStatus = SyncStatus.PENDING_UPDATE,
            )
            dao.updateExisting(updated)
            enqueueUpdate(id)
            appendAudit(id, auditKind, "{\"revision\":${updated.localRevision}}")
        }
    }

    /**
     * v1.5.1 (VAULT-005): the interface-level PATCH. Reads the
     * current row, applies the status / completedAt /
     * droppedReason / isSensitive changes, refreshes updatedAt,
     * writes PENDING_UPDATE, and enqueues a sync_queue row.
     */
    override suspend fun update(
        id: String,
        status: Status,
        completedAt: String?,
        droppedReason: String?,
        isSensitive: Boolean,
    ): Instruction {
        val current = dao.getById(id) ?: error("Instruction $id not found in local mirror")
        val now = Instant.now().toString()
        val updated = current.copy(
            status = status.name,
            completedAt = completedAt,
            droppedReason = droppedReason,
            isSensitive = isSensitive,
            updatedAt = now,
            localRevision = current.localRevision + 1,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        dao.updateExisting(updated)
        upsertFts(updated)
        enqueueUpdate(id)
        appendAudit(id, "FIELD_CHANGED", "{\"revision\":${updated.localRevision}}")
        if (parseStatus(current.status) != status) {
            appendAudit(id, "STATUS_CHANGED", "{\"status\":\"${status.name}\",\"revision\":${updated.localRevision}}")
        }
        return updated.toDomain()
    }

    /**
     * v1.5.1 (VAULT-005): the [InstructionRepository] wrapper for
     * [markDone]. Aligns with the Supabase signature: the caller
     * provides the [completedAt] timestamp (the original
     * Room-side `markDone(id)` used `Instant.now()` internally;
     * we honour the explicit value here).
     */
    override suspend fun markDone(id: String, completedAt: String) {
        dao.updateStatus(
            id = id,
            status = Status.DONE.name,
            updatedAt = completedAt,
            completedAt = completedAt,
            droppedReason = null,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
        appendAudit(id, "STATUS_CHANGED", "{\"status\":\"DONE\"}")
    }

    /**
     * v1.5.1 (VAULT-005): the [InstructionRepository] wrapper for
     * [markDropped]. The [at] timestamp is used as the
     * `updatedAt` value.
     */
    override suspend fun markDropped(id: String, reason: String?, at: String) {
        archiveCompatibility(id, reason, at)
    }

    // ---- v2.0 (Hierarchy): audience + due chip + channel ----

    override suspend fun createWithAudience(
        personId: String?,
        audience: AudienceRef?,
        source: Source,
        priority: Priority,
        title: String,
        rawText: String,
        dueAt: String?,
        dueAtMs: Long?,
        channel: String?,
    ): Instruction = createInternal(
        draft = InstructionDraft(
            rawText = rawText,
            actionSummary = title,
            personId = personId,
            responsiblePersonId = audience?.takeIf { it.kind == "PERSON" }?.target,
            groupLabel = audience?.takeIf { it.isBroadcast }?.label,
            priority = priority,
            hardDeadlineAtEpochMs = dueAtMs
                ?: dueAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            source = source,
        ),
        dueAt = dueAt,
        audience = audience,
        dueAtMs = dueAtMs,
        channel = channel,
    )

    override suspend fun setAudience(id: String, audience: AudienceRef?) {
        val now = Instant.now().toString()
        dao.setAudience(
            id = id,
            audienceKind = audience?.kind,
            audienceTarget = audience?.target,
            audienceLabel = audience?.label,
            audienceIsBroadcast = audience?.isBroadcast ?: false,
            now = now,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
        appendAudit(id, "FIELD_CHANGED", "{}")
    }

    override suspend fun setDueChip(id: String, dueAtMs: Long?) {
        val now = Instant.now().toString()
        dao.setDueChip(
            id = id,
            dueAtMs = dueAtMs,
            now = now,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
        appendAudit(id, "FIELD_CHANGED", "{}")
    }

    override suspend fun setChannel(id: String, channel: String?) {
        val now = Instant.now().toString()
        dao.setChannel(
            id = id,
            channel = channel,
            now = now,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
        appendAudit(id, "FIELD_CHANGED", "{}")
    }

    // ---- compatibility helpers for callers migrated in later stages ----

    /**
     * v1.1: mark an instruction DONE. Sets `status = DONE`,
     * `completedAt = now()`, refreshes `updatedAt` (so the brief
     * "needs you today" 7-day window resets — a re-opened row
     * is not "carried over"), and queues a PENDING_UPDATE for the
     * sync outbox.
     *
     * The wire side ([SupabaseInstructionRepository.markDone]) is a
     * thin PATCH that hits PostgREST with the same id; the sync
     * engine drains the outbox when the network is available.
     */
    suspend fun markDone(id: String) {
        val now = Instant.now().toString()
        dao.updateStatus(
            id = id,
            status = Status.DONE.name,
            updatedAt = now,
            completedAt = now,
            droppedReason = null,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
        appendAudit(id, "STATUS_CHANGED", "{\"status\":\"DONE\"}")
    }

    /**
     * Archives an instruction without changing the simplified lifecycle status.
     */
    suspend fun markDropped(id: String, reason: String?) {
        val now = Instant.now().toString()
        archiveCompatibility(id, reason, now)
    }

    /**
     * Restores a compatibility-archived instruction without changing its status.
     */
    suspend fun reopen(id: String) {
        val now = Instant.now().toString()
        db.withTransaction {
            val changed = dao.updateArchiveState(
                id = id,
                archivedAtEpochMs = null,
                droppedReason = null,
                updatedAt = now,
            )
            if (changed == 0) return@withTransaction
            enqueueUpdate(id)
            appendAudit(id, "RESTORED", "{}")
        }
    }

    private suspend fun archiveCompatibility(id: String, reason: String?, at: String) {
        db.withTransaction {
            val changed = dao.updateArchiveState(
                id = id,
                archivedAtEpochMs = runCatching { Instant.parse(at).toEpochMilli() }.getOrDefault(0L),
                droppedReason = reason,
                updatedAt = at,
            )
            if (changed == 0) return@withTransaction
            enqueueUpdate(id)
            appendAudit(id, "ARCHIVED", "{}")
        }
    }

    /**
     * v1.1: update a row's [is_sensitive] flag. The sync engine
     * filters sensitive rows on the way out, so flipping this on
     * for an already-synced row needs a PATCH to the server too
     * (the server should drop the row from its own copy — defensive
     * even though spec §13 says sensitive rows never hit the server).
     */
    suspend fun setSensitive(id: String, sensitive: Boolean) {
        val row = dao.getById(id) ?: return
        dao.updateExisting(
            row.copy(
                isSensitive = sensitive,
                updatedAt = Instant.now().toString(),
                localRevision = row.localRevision + 1,
                syncStatus = SyncStatus.PENDING_UPDATE,
            )
        )
        enqueueUpdate(id)
        appendAudit(id, "FIELD_CHANGED", "{\"revision\":${row.localRevision + 1}}")
    }

    private suspend fun upsertFts(entity: InstructionEntity) {
        val rowid = dao.getRowId(entity.id) ?: ftsDao.maxInstructionRowid() ?: return
        ftsDao.upsert(
            InstructionFtsEntity(
                rowid = rowid,
                title = entity.actionSummary,
                rawText = entity.rawText,
                personId = entity.personId,
                capturedAt = entity.capturedAt,
            ),
        )
    }

    private suspend fun appendAudit(id: String, kind: String, payload: String) {
        auditChainWriter?.append(
            tableName = "instructions",
            rowId = id,
            kind = kind,
            payload = payload,
        )
    }

    /**
     * v1.1: enqueue a single UPDATE row to the sync outbox and
     * fire-and-forget drain. The drain reads the local Room row
     * (it has the canonical lifecycle fields) and PATCHes the
     * server. On success the local row's `syncStatus` flips to
     * `SYNCED`. On failure the entry stays in the outbox and is
     * retried on the next drain (or app start).
     *
     * The payload is empty because the drain reads the row from
     * Room directly (the canonical source of truth) — the
     * sync_queue only carries `(table, rowId, op)`.
     */
    private suspend fun enqueueUpdate(id: String) {
        // v2.0.0: no remote to drain to. The sync_queue row is
        // still enqueued (forward-compat) but no drain will
        // ever run. A future v2.x pass that adds cloud sync
        // would re-insert the drain call here.
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = id,
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = "{}",
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * v1.5.1 (VAULT-005): enqueue an INSERT row to the sync
     * outbox. Same shape as [enqueueUpdate] but with
     * `OP_INSERT`. In vault mode the drain is a no-op (the
     * periodic worker is not scheduled) so the row sits in
     * PENDING_INSERT forever — that's the intended state.
     */
    private suspend fun enqueueInsert(id: String) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = id,
                op = SyncQueueEntity.OP_INSERT,
                payloadJson = "{}",
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}

/**
 * v1.5.1 (VAULT-005): reverse of [RoomInstructionRepository.toEntity].
 * Maps the Room row (string enums) to the domain [Instruction]
 * (typed enums).
 */
internal fun InstructionEntity.toDomain(): Instruction = Instruction(
    id = id,
    personId = personId,
    direction = Direction.valueOf(direction),
    status = parseStatus(status),
    source = Source.valueOf(source),
    priority = parsePriority(priority),
    title = title,
    rawText = rawText,
    dueAt = dueAt,
    capturedAt = capturedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSensitive = isSensitive,
    completedAt = completedAt,
    droppedReason = droppedReason,
    // v2.0 (Hierarchy): audience + due chip + channel.
    audience = audienceFromColumns(audienceKind, audienceTarget, audienceLabel),
    dueAtMs = dueAtMs,
    channel = channel,
    actionSummary = actionSummary,
    hardDeadlineAtEpochMs = hardDeadlineAtEpochMs,
    followUpAtEpochMs = followUpAtEpochMs,
    archivedAtEpochMs = archivedAtEpochMs,
    responsiblePersonId = responsiblePersonId,
    groupLabel = groupLabel,
    localRevision = localRevision,
    migrationReviewRequired = migrationReviewRequired,
    migrationMetadata = migrationMetadata,
)
