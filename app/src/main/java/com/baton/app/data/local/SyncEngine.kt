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
     * v1.2.2: exponential backoff. `1s * 2^attempts`, capped at
     * 5 minutes. So the backoff schedule is:
     *  attempts=1 -> 2s, 2 -> 4s, 3 -> 8s, 4 -> 16s, 5 -> 32s,
     *  6 -> 64s, 7 -> 128s, 8 -> 256s, 9+ -> 300s (capped).
     * 9 attempts total worst-case before permanent failure, so
     * the user waits at most ~9 minutes before the entry is
     * flagged as stuck and surfaced in Settings.
     */
    private fun backoffMillis(attempts: Int): Long {
        val baseMs = 1_000L  // 1 second
        val shift = attempts.coerceIn(0, 30)  // guard against shift overflow
        val raw = baseMs shl shift
        return raw.coerceAtMost(MAX_BACKOFF_MS)
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
                // v1.1: read the local row's current state and PATCH
                // it on the server. The payload is the row's
                // lifecycle fields (status, completedAt, droppedReason,
                // isSensitive) — the server already knows the rest.
                val row = instructionDao.getById(entry.rowId)
                    ?: run {
                        // Row was deleted before the drain ran. Drop the
                        // entry quietly; nothing to push.
                        return
                    }
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
        private const val MAX_BACKOFF_MS = 5L * 60L * 1000L  // 5 minutes
        private const val TAG = "BatonSync"
    }
}

/** Convenience: run [drainAll] in a fire-and-forget coroutine on IO. */
fun SyncEngine.drainAllAsync(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) { drainAll() }
}
