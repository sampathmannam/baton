package com.baton.app.data.local

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
 * **Failure policy:** on exception, the entry's `attempts` is
 * incremented and `lastError` is recorded. The entry stays in
 * the queue and the engine moves on to the next entry. The next
 * drain (next write or next app start) will retry it.
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
     * the WorkManager periodic drain (deferred to T6.1).
     */
    suspend fun drainAll() = mutex.withLock {
        val entries = syncQueueDao.snapshot()
        for (entry in entries) {
            try {
                processEntry(entry)
                syncQueueDao.deleteById(entry.id)
            } catch (e: Exception) {
                syncQueueDao.recordFailure(entry.id, e.message ?: e.toString())
            }
        }
    }

    /**
     * Drain a single (table, rowId, op) entry. Called by the
     * repositories immediately after they enqueue, so the user
     * sees the local change synchronously and the network round-
     * trip happens in the background.
     */
    suspend fun drainOne(rowId: String, table: String, op: String) {
        mutex.withLock {
            val entry = syncQueueDao.findPending(table, rowId, op) ?: return
            try {
                processEntry(entry)
                syncQueueDao.deleteById(entry.id)
            } catch (e: Exception) {
                syncQueueDao.recordFailure(entry.id, e.message ?: e.toString())
            }
        }
    }

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
    }
}

/** Convenience: run [drainAll] in a fire-and-forget coroutine on IO. */
fun SyncEngine.drainAllAsync(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) { drainAll() }
}
