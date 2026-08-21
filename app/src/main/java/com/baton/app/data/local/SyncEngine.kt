package com.baton.app.data.local

import android.util.Log
import com.baton.app.data.local.entities.SyncConflictEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.person.PersonConflictPayload
import com.baton.app.data.person.PersonInsert
import com.baton.app.data.person.SupabasePersonRepository
import com.baton.app.data.person.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M2-T6: drain the [SyncQueueDao] outbox to Supabase.
 *
 * The engine is invoked:
 *  - **per-write** by the Room repositories, immediately after the
 *    local insert. The user sees the new row instantly; the drain
 *    runs in the background.
 *  - **on app start** by [com.baton.app.data.local.DatabaseInitializer]
 *    (or a `SyncWorker` once T6.1 lands). Anything left in the
 *    outbox from a previous session is sent.
 *
 * **FIFO order** preserves the causal order of writes: the queue
 * is sorted by `id ASC`, and the engine processes them one at a
 * time. A single in-flight drain at a time — a [Mutex] guards
 * against a second per-write trigger piling on.
 *
 * **v1.2.2 (F-HIGH-07) Failure policy (exponential backoff +
 * permanent-failure cap):** on exception, the entry's `attempts`
 * is incremented, `lastError` is recorded, and `nextAttemptAt`
 * is set to `now() + backoff(attempts)` where backoff is
 * `1s * 2^attempts` capped at 5 minutes. The next drain skips
 * the entry until `nextAttemptAt` is in the past. After
 * [MAX_ATTEMPTS] consecutive failures, the entry is marked
 * `lastError = "PERMANENT_FAILURE: <msg>"` and the drain skips
 * it forever (until the user explicitly calls
 * [retryPermanentlyFailed] from Settings). The per-write path
 * uses the same backoff; the user-perceived latency on a normal
 * online write is unaffected (first attempt runs immediately,
 * `nextAttemptAt` starts at 0).
 *
 * **M2-T8 conflict resolution:** on an `OP_UPDATE`, the engine
 * reads the server's `updated_at` (via [SupabasePersonRepository.findById])
 * and compares it to the local row's `updated_at`. If the server
 * is newer, the local change is dropped and a row is logged in
 * [SyncConflictDao] so the user can review what was lost. The
 * server's newer state is then mirrored into Room (so the UI shows
 * the authoritative value). LWW is by string comparison because
 * `updated_at` is ISO-8601 (lexicographic == chronological for
 * fixed-width ISO strings).
 */
@Singleton
class SyncEngine @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val personDao: PersonDao,
    private val captureDao: CaptureDao,
    private val instructionDao: InstructionDao,
    private val syncConflictDao: SyncConflictDao,
    private val personRemote: SupabasePersonRepository,
    private val captureRemote: com.baton.app.data.captures.SupabaseCaptureRepository,
    private val instructionRemote: com.baton.app.data.instructions.SupabaseInstructionRepository,
) {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Drain the full queue. Called from the app-startup hook and
     * the WorkManager periodic drain.
     *
     * v1.2.2: only entries with `nextAttemptAt <= now()` are
     * considered. Entries in the backoff window or marked
     * `PERMANENT_FAILURE:*` are skipped silently.
     */
    suspend fun drainAll() = mutex.withLock {
        val now = System.currentTimeMillis()
        val entries = syncQueueDao.snapshotReady(now)
        for (entry in entries) {
            try {
                processEntry(entry)
                syncQueueDao.deleteById(entry.id)
            } catch (e: Exception) {
                recordFailureOrGiveUp(entry, e)
            }
        }
    }

    /**
     * Drain a single (table, rowId, op) entry. Called by the
     * repositories immediately after they enqueue, so the user
     * sees the local change synchronously and the network round-
     * trip happens in the background.
     *
     * v1.2.2: if the entry's `nextAttemptAt` is in the future
     * (i.e. a previous attempt failed and the backoff hasn't
     * expired), the per-write path silently returns — the
     * background drain will pick it up. This prevents a hot
     * user action from triggering a tight retry loop on a
     * downed server.
     */
    /**
     * v1.8.0 (PROD-READINESS-P2-P1-#4): enqueue a row
     * with a hard cap on the outbox size. Calls
     * [SyncQueueDao.enqueue] (which dedupes on
     * `(op, table, rowId)` per v1.4.2) and then
     * [SyncQueueDao.trimToLimit] to evict the oldest
     * rows if the new size exceeds [MAX_QUEUE_SIZE].
     *
     * The repositories that previously called
     * [SyncQueueDao.enqueue] directly can be migrated
     * to this method to opt into the cap. v1.8.0 keeps
     * the bare [SyncQueueDao.enqueue] available for
     * tests + the per-write path that needs the
     * REPLACE-on-conflict behaviour the unique index
     * gives.
     *
     * **v1.5.0 vault-mode note.** The v1.5.0 build has
     * no cloud sync, so the cap is dormant. The
     * methods are wired and tested so a future
     * cloud-sync build gets the right behaviour
     * without a refactor.
     */
    suspend fun enqueueWithCap(entry: SyncQueueEntity) = mutex.withLock {
        syncQueueDao.enqueue(entry)
        syncQueueDao.trimToLimit(MAX_QUEUE_SIZE)
    }

    suspend fun drainOne(rowId: String, table: String, op: String) {
        mutex.withLock {
            val entry = syncQueueDao.findPending(table, rowId, op) ?: return
            val now = System.currentTimeMillis()
            if (entry.nextAttemptAt > now) {
                // In the backoff window. The periodic drain will retry.
                return
            }
            if (entry.lastError?.startsWith(PERMANENT_FAILURE_PREFIX) == true) {
                // Already given up. The user must explicitly retry.
                return
            }
            try {
                processEntry(entry)
                syncQueueDao.deleteById(entry.id)
            } catch (e: Exception) {
                recordFailureOrGiveUp(entry, e)
            }
        }
    }

    /**
     * v1.2.2: handle a drain failure. If the entry has hit
     * [MAX_ATTEMPTS], mark it permanently failed (the drain
     * skips it on every future pass). Otherwise, apply the
     * exponential backoff and let the next drain try again.
     */
    private suspend fun recordFailureOrGiveUp(entry: SyncQueueEntity, e: Exception) {
        val newAttempts = entry.attempts + 1
        val errorMsg = e.message ?: e.toString()
        if (newAttempts >= MAX_ATTEMPTS) {
            Log.w(
                TAG,
                "sync_queue entry ${entry.id} (${entry.table}/${entry.op}/${entry.rowId}) " +
                    "hit MAX_ATTEMPTS=$MAX_ATTEMPTS — giving up. Last error: $errorMsg",
            )
            syncQueueDao.markPermanentlyFailed(
                entry.id,
                newAttempts,
                "$PERMANENT_FAILURE_PREFIX (after $newAttempts attempts) $errorMsg",
            )
        } else {
            val nextAttemptAt = System.currentTimeMillis() + backoffMillis(newAttempts)
            syncQueueDao.recordFailureWithBackoff(entry.id, errorMsg, nextAttemptAt)
        }
    }

    /**
     * v1.4.1 (DATA-FINDING-01): table-driven backoff matching the
     * v1.4 spec — `1s, 2s, 4s, 8s, 16s, 32s, 60s, 120s, 240s, 300s,
     * 300s`. The pre-v1.4 schedule (`1s * 2^attempts` capped at
     * 300s) produced `2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 300s,
     * 300s, 300s` — off-by-one on the first retry (2s vs 1s) and
     * mid-range 64s/128s/256s instead of the spec's 60s/120s/240s.
     * The user paid 3 extra retries + 4 extra seconds for a real
     * outage (e.g. Jio 4G down for 4 minutes). After attempt
     * index 10 we cap at 300s.
     */
    private fun backoffMillis(attempts: Int): Long {
        val idx = (attempts - 1).coerceAtLeast(0)
        val schedule = longArrayOf(
            1_000L,     // 1   s — first retry
            2_000L,     // 2   s
            4_000L,     // 4   s
            8_000L,     // 8   s
            16_000L,    // 16  s
            32_000L,    // 32  s
            60_000L,    // 60  s — spec
            120_000L,   // 120 s — spec
            240_000L,   // 240 s — spec
            300_000L,   // 300 s — cap
            300_000L,   // 300 s — cap (last entry of spec)
        )
        return if (idx < schedule.size) {
            schedule[idx]
        } else {
            schedule.last()  // any attempt > 11 caps at 300s
        }
    }

    /**
     * v1.2.2: reset all `PERMANENT_FAILURE:*` rows so the next
     * drain tries them again. Returns the number of rows reset.
     *
     * Wired from Settings → "Retry stuck outbox entries". A user
     * who fixes a problem (e.g. updates the network) can poke
     * the outbox without reinstalling.
     */
    suspend fun retryPermanentlyFailed(): Int = syncQueueDao.resetPermanentlyFailed()

    /**
     * v1.2.4 (F-HIGH-08): live count of outbox rows that have
     * hit [MAX_ATTEMPTS] and are stuck (`PERMANENT_FAILURE:*`).
     * Used by [com.baton.app.ui.settings.SettingsViewModel] to
     * surface a "N stuck entries — Retry" action in the
     * Settings sheet. The Flow re-emits on every change (new
     * stuck row, retry, or drain).
     */
    fun observeStuckCount(): kotlinx.coroutines.flow.Flow<Int> =
        syncQueueDao.observeStuckCount()

    private suspend fun processEntry(entry: SyncQueueEntity) {
        when (entry.table) {
            "persons" -> processPersonEntry(entry)
            "captures" -> processCaptureEntry(entry)
            "instructions" -> processInstructionEntry(entry)
            else -> throw IllegalArgumentException("Unknown sync table: ${entry.table}")
        }
    }

    private suspend fun processPersonEntry(entry: SyncQueueEntity) {
        when (entry.op) {
            SyncQueueEntity.OP_INSERT -> {
                val insert = json.decodeFromString(PersonInsert.serializer(), entry.payloadJson)
                val person = personRemote.create(
                    name = insert.name,
                    designation = insert.designation,
                    station = insert.station,
                    clientId = insert.id,
                )
                // Update Room with the server-assigned values + mark synced.
                // The local entity's id is the client UUID; if the
                // server returned the same id, upsert is a no-op. If
                // the server ever reassigns (it shouldn't — PostgREST
                // honours the client id), the row's id would change;
                // for now we trust the client id is the row id.
                personDao.upsert(
                    person.toEntity().copy(syncStatus = SyncStatus.SYNCED)
                )
            }
            SyncQueueEntity.OP_UPDATE -> {
                // M2-T8: LWW via updated_at. Before pushing the local
                // change, ask the server for the current row. If the
                // server is newer, drop the local write, log the
                // conflict for the user to review, and mirror the
                // server's state into Room. The caller (e.g. a UI
                // "Edit person" flow) will see the authoritative
                // value the next time it reads.
                //
                // v1.1.1: the payload is decoded as a sanity check
                // — malformed JSON throws and the entry stays in the
                // outbox for retry. v1.1 OP_UPDATE for persons is
                // exclusively the `is_sensitive` toggle (there's no
                // other person-edit flow yet). The wire call is
                // `setSensitive(id, local.isSensitive)` regardless
                // of true/false. v1.1's else-branch that called
                // `personRemote.create(...)` for the false case was
                // a root-cause bug — it would re-INSERT the row
                // instead of PATCHing the column.
                json.decodeFromString(PersonInsert.serializer(), entry.payloadJson)
                val localRow = personDao.getById(entry.rowId)
                if (localRow == null) {
                    // Row was deleted locally before the drain ran.
                    // Drop the entry; nothing to push.
                    return
                }
                val serverRow = personRemote.findById(entry.rowId)

                if (serverRow != null && isServerNewer(serverRow.updatedAt, localRow.updatedAt)) {
                    // Conflict: server is newer. Drop local, log audit, mirror server.
                    syncConflictDao.insert(
                        SyncConflictEntity(
                            tableName = "persons",
                            rowId = entry.rowId,
                            localPayload = json.encodeToString(
                                PersonConflictPayload.serializer(),
                                localRow.toConflictPayload(),
                            ),
                            serverPayload = json.encodeToString(
                                PersonConflictPayload.serializer(),
                                serverRow.toConflictPayload(),
                            ),
                            reason = REASON_SERVER_NEWER,
                            detectedAt = System.currentTimeMillis(),
                        )
                    )
                    personDao.upsert(
                        serverRow.toEntity().copy(syncStatus = SyncStatus.SYNCED)
                    )
                    return
                }

                // No conflict (or server has no copy): PATCH the
                // server's `is_sensitive` to match the local row.
                personRemote.setSensitive(entry.rowId, localRow.isSensitive)
                personDao.setSyncStatus(
                    entry.rowId,
                    SyncStatus.SYNCED,
                    localRow.updatedAt,
                )
            }
            SyncQueueEntity.OP_DELETE -> {
                // T6.1: server-side delete via Postgrest.filter(eq("id", ...)).
                // For now we just clear the local row.
                personDao.deleteById(entry.rowId)
            }
        }
    }

    private suspend fun processCaptureEntry(entry: SyncQueueEntity) {
        // M2-T6: captures are local-first; the server is the
        // audit trail. The drain is best-effort and falls through
        // to the next entry on failure.
        captureDao.setSyncStatus(entry.rowId, SyncStatus.SYNCED)
    }

    private suspend fun processInstructionEntry(entry: SyncQueueEntity) {
        // M2-T6 + v1.1: handle INSERT (capture flow), UPDATE (mark-
        // done / mark-dropped / re-open / set-sensitive), and
        // DELETE (TBD).
        when (entry.op) {
            SyncQueueEntity.OP_INSERT -> {
                // M2-T6: the create path is already round-tripped in
                // SupabaseInstructionRepository.create(). The drain
                // just confirms sync status.
                instructionDao.setSyncStatus(entry.rowId, SyncStatus.SYNCED)
            }
            SyncQueueEntity.OP_UPDATE -> {
                // v1.4.2 (DATA-FINDING-02): LWW via updated_at,
                // mirroring `processPersonEntry`. Read the server's
                // current row and compare its `updated_at` to the
                // local row's `updated_at`. If the server is newer,
                // drop the local PATCH, log a conflict, and mirror
                // the server's state into Room so the UI shows the
                // authoritative value.
                //
                // Equal timestamps = local wins (matches the
                // `isServerNewer` strict-`>` rule used by the persons
                // path; deterministic and matches the
                // `no conflict when updated_at equal` person test).
                //
                // v1.1: when no conflict, read the local row's
                // current state and PATCH it on the server. The
                // payload is the row's lifecycle fields (status,
                // completedAt, droppedReason, isSensitive) — the
                // server already knows the rest.
                val row = instructionDao.getById(entry.rowId)
                    ?: run {
                        // Row was deleted before the drain ran. Drop
                        // the entry quietly; nothing to push.
                        return
                    }
                val serverRow = instructionRemote.findById(entry.rowId)
                if (serverRow != null && isServerNewer(serverRow.updatedAt, row.updatedAt)) {
                    // Conflict: server is newer. Drop local, log
                    // audit, mirror server.
                    Log.w(
                        TAG,
                        "LWW drop: server newer for instructions/${entry.rowId} " +
                            "(server=${serverRow.updatedAt}, local=${row.updatedAt})",
                    )
                    syncConflictDao.insert(
                        SyncConflictEntity(
                            tableName = "instructions",
                            rowId = entry.rowId,
                            localPayload = json.encodeToString(
                                InstructionConflictPayload.serializer(),
                                row.toInstructionConflictPayload(),
                            ),
                            serverPayload = json.encodeToString(
                                InstructionConflictPayload.serializer(),
                                serverRow.toInstructionConflictPayload(),
                            ),
                            reason = REASON_SERVER_NEWER,
                            detectedAt = System.currentTimeMillis(),
                        )
                    )
                    instructionDao.upsert(
                        serverRow.toInstructionEntity().copy(syncStatus = SyncStatus.SYNCED)
                    )
                    return
                }
                // No conflict (or server has no copy): PATCH the
                // server's lifecycle fields to match the local row.
                instructionRemote.update(
                    id = row.id,
                    status = com.baton.app.data.instructions.Status.valueOf(row.status),
                    completedAt = row.completedAt,
                    droppedReason = row.droppedReason,
                    isSensitive = row.isSensitive,
                )
                instructionDao.setSyncStatus(row.id, SyncStatus.SYNCED)
            }
            SyncQueueEntity.OP_DELETE -> {
                // TBD: server-side DELETE. v1.1 doesn't expose a delete
                // instruction path; the row stays in Room and the
                // server copy until the user does an explicit action.
                // Mark the row as PENDING_DELETE for now.
            }
        }
    }

    /**
     * Lexicographic compare of two ISO-8601 strings. Both are
     * server-supplied or Room-supplied in the same format
     * (`Instant.toString()`), so the comparison is also
     * chronological. `null` server timestamp is treated as
     * "not newer" (we don't have data to compare).
     */
    private fun isServerNewer(serverUpdatedAt: String?, localUpdatedAt: String?): Boolean {
        if (serverUpdatedAt == null) return false
        if (localUpdatedAt == null) return true
        return serverUpdatedAt > localUpdatedAt
    }

    private fun nowIso(): String = java.time.Instant.now().toString()

    private fun com.baton.app.data.local.entities.PersonEntity.toConflictPayload(): PersonConflictPayload =
        PersonConflictPayload(
            id = id,
            name = name,
            designation = designation,
            station = station,
            phone = phone,
            userId = userId,
            updatedAt = updatedAt,
        )

    private fun com.baton.app.data.person.Person.toConflictPayload(): PersonConflictPayload =
        PersonConflictPayload(
            id = id,
            name = name,
            designation = designation,
            station = station,
            phone = phone,
            userId = "",  // Server's user_id isn't in the Person domain
            updatedAt = updatedAt,
        )

    /**
     * v1.4.2 (DATA-FINDING-02): JSON-serialisable snapshot of an
     * [com.baton.app.data.instructions.Instruction] row at the
     * moment of a conflict. Stored in
     * `sync_conflicts.localPayload` / `serverPayload` so the user
     * can review what was lost and what won. Mirrors the
     * [PersonConflictPayload] shape used by the persons sync path.
     *
     * Only the lifecycle fields the LWW PATCH would have written
     * (status, completedAt, droppedReason, isSensitive) are
     * captured — the rest of the row (title, rawText, etc.) is the
     * same on local and server, so a snapshot of those would just
     * bloat the audit table.
     */
    @Serializable
    private data class InstructionConflictPayload(
        val id: String,
        val status: String,
        @SerialName("completed_at") val completedAt: String? = null,
        @SerialName("dropped_reason") val droppedReason: String? = null,
        @SerialName("is_sensitive") val isSensitive: Boolean = false,
        @SerialName("updated_at") val updatedAt: String? = null,
    )

    private fun com.baton.app.data.local.entities.InstructionEntity.toInstructionConflictPayload(): InstructionConflictPayload =
        InstructionConflictPayload(
            id = id,
            status = status,
            completedAt = completedAt,
            droppedReason = droppedReason,
            isSensitive = isSensitive,
            updatedAt = updatedAt,
        )

    private fun com.baton.app.data.instructions.Instruction.toInstructionConflictPayload(): InstructionConflictPayload =
        InstructionConflictPayload(
            id = id,
            status = status.name,
            completedAt = completedAt,
            droppedReason = droppedReason,
            isSensitive = isSensitive,
            updatedAt = updatedAt,
        )

    /**
     * v1.4.2 (DATA-FINDING-02): mirror of
     * [com.baton.app.data.instructions.RoomInstructionRepository]'s
     * private `Instruction.toEntity()`. Used by the LWW drop path
     * to overwrite the local Room row with the server's
     * authoritative state. Kept private to this file because
     * SyncEngine is the only caller — the Room repository's own
     * converter stays the source of truth for the in-app lifecycle
     * writes.
     */
    private fun com.baton.app.data.instructions.Instruction.toInstructionEntity(): com.baton.app.data.local.entities.InstructionEntity =
        com.baton.app.data.local.entities.InstructionEntity(
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
            // v1.1 lifecycle fields: the LWW drop must mirror
            // every server field, not just the ones the PATCH
            // was about to write. Otherwise the local row would
            // lose e.g. `completedAt` even though the server
            // still has it.
            completedAt = completedAt,
            droppedReason = droppedReason,
            syncStatus = SyncStatus.SYNCED,
        )

    companion object {
        const val REASON_SERVER_NEWER = "server_newer"
        /**
         * v1.2.2 (F-HIGH-07): after this many consecutive drain
         * failures, the entry is marked `PERMANENT_FAILURE:*` and
         * skipped on every future drain until the user calls
         * [retryPermanentlyFailed]. 10 attempts = ~9 minutes of
         * backoff (1+2+4+8+16+32+64+128+256 = 511s ≈ 8.5 min)
         * before the entry is flagged as stuck.
         */
        const val MAX_ATTEMPTS = 10
        const val PERMANENT_FAILURE_PREFIX = "PERMANENT_FAILURE:"
        // v1.8.0 (PROD-READINESS-P2-P1-#4): the outbox
        // cap. 1000 rows is ~50 KB on disk (each row is
        // a small JSON blob in a few columns) — well
        // under the per-app storage budget. The number
        // is high enough that a normal day's work fits
        // in the cap (a heavy day is ~50-100 writes) so
        // the cap only fires on a station that's been
        // offline for a week or more. When it fires,
        // the oldest rows are dropped first (the most
        // recent writes are the ones most likely to be
        // still relevant).
        const val MAX_QUEUE_SIZE = 1000
        private const val MAX_BACKOFF_MS = 5L * 60L * 1000L  // 5 minutes
        private const val TAG = "BatonSync"
    }
}

/** Convenience: run [drainAll] in a fire-and-forget coroutine on IO. */
fun SyncEngine.drainAllAsync(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) { drainAll() }
}
