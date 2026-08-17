package com.baton.app.data.instructions

import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.SyncEngine
import com.baton.app.data.local.SyncQueueDao
import com.baton.app.data.local.TouchPersonOnActivity
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 */
@Singleton
open class RoomInstructionRepository @Inject constructor(
    private val dao: InstructionDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncEngine: SyncEngine,
    private val touchOnActivity: TouchPersonOnActivity,
    @ApplicationScope private val appScope: CoroutineScope,
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
        // We don't have a Supabase client wired in here — pass it via
        // a wrapper. Simpler: have the caller pass it in. The single
        // caller (HomeViewModel) already injects both DAOs and the
        // Supabase repo via Hilt. We expose the network read as an
        // extension function instead of pulling another Hilt graph
        // dependency in here.
        throw UnsupportedOperationException(
            "Use the suspend overload that takes a SupabaseInstructionRepository",
        )
    }

    /**
     * Hilt-injected overload that does the actual pull + upsert.
     * Kept separate so the network dependency stays explicit at the
     * call site (HomeViewModel.refreshInstructionsFromNetwork) and
     * this class remains trivially unit-testable with a fake DAO.
     */
    suspend fun refreshFromNetwork(remote: SupabaseInstructionRepository) {
        val remoteRows = remote.fetchAll()
        val entities = remoteRows.map { it.toEntity() }
        // v1.0: filter out any is_sensitive rows the server may have
        // somehow returned. By spec these are local-only and should
        // not be in the network response, but defensive filtering
        // keeps the local mirror clean if a future migration drops
        // the row-level gate.
        val nonSensitive = entities.filter { !it.isSensitive }
        dao.upsertAll(nonSensitive)
    }

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
    ): Instruction {
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        val entity = InstructionEntity(
            id = id,
            personId = personId,
            direction = Direction.OUTGOING.name,
            status = Status.OPEN.name,
            source = source.name,
            priority = priority.name,
            title = title,
            rawText = rawText,
            dueAt = dueAt,
            capturedAt = now,
            createdAt = now,
            updatedAt = now,
            isSensitive = false,
            syncStatus = SyncStatus.PENDING_INSERT,
        )
        dao.upsert(entity)
        enqueueInsert(id)
        // v2.0 Tier 2 (§2.3): auto-snooze. A new instruction
        // counts as activity for the person; bump their
        // lastInteractionAt so the decay view drops them out of
        // the "haven't touched" list. No-op on free-floating
        // instructions (personId == null).
        touchOnActivity.touch(personId)
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
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        dao.upsert(updated)
        enqueueUpdate(id)
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
    }

    /**
     * v1.5.1 (VAULT-005): the [InstructionRepository] wrapper for
     * [markDropped]. The [at] timestamp is used as the
     * `updatedAt` value.
     */
    override suspend fun markDropped(id: String, reason: String?, at: String) {
        dao.updateStatus(
            id = id,
            status = Status.DROPPED.name,
            updatedAt = at,
            completedAt = null,
            droppedReason = reason,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
    }

    // ---- existing direct-call helpers (unchanged) ----

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
    }

    /**
     * v1.1: mark an instruction DROPPED. Sets `status = DROPPED`,
     * `droppedReason = reason`, refreshes `updatedAt`. The row stays
     * in Room (the spec's silent drop is the carriedOver > 30 days
     * rule, not a user-initiated drop).
     */
    suspend fun markDropped(id: String, reason: String?) {
        val now = Instant.now().toString()
        dao.updateStatus(
            id = id,
            status = Status.DROPPED.name,
            updatedAt = now,
            completedAt = null,
            droppedReason = reason,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
    }

    /**
     * v1.1: re-open a closed instruction. Used when the user
     * "un-done"s a row or restores a dropped one. Status flips back
     * to OPEN; completedAt/droppedReason are cleared; updatedAt
     * refreshes so the 7-day brief window restarts.
     */
    suspend fun reopen(id: String) {
        val now = Instant.now().toString()
        dao.updateStatus(
            id = id,
            status = Status.OPEN.name,
            updatedAt = now,
            completedAt = null,
            droppedReason = null,
            syncStatus = SyncStatus.PENDING_UPDATE,
        )
        enqueueUpdate(id)
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
        dao.upsert(
            row.copy(
                isSensitive = sensitive,
                updatedAt = Instant.now().toString(),
                syncStatus = SyncStatus.PENDING_UPDATE,
            )
        )
        enqueueUpdate(id)
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
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "instructions",
                rowId = id,
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = "{}",
                createdAt = System.currentTimeMillis(),
            )
        )
        appScope.launch {
            syncEngine.drainOne(id, "instructions", SyncQueueEntity.OP_UPDATE)
        }
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
    status = Status.valueOf(status),
    source = Source.valueOf(source),
    priority = Priority.valueOf(priority),
    title = title,
    rawText = rawText,
    dueAt = dueAt,
    capturedAt = capturedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSensitive = isSensitive,
    completedAt = completedAt,
    droppedReason = droppedReason,
)
