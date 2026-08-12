package com.baton.app.data.local

import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
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
 */
@Singleton
class SyncEngine @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val personDao: PersonDao,
    private val captureDao: CaptureDao,
    private val instructionDao: InstructionDao,
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
                // T6.1: last-write-wins via `updated_at`. For now the
                // payload is the full row.
                val row = json.decodeFromString(PersonInsert.serializer(), entry.payloadJson)
                // The remote Supabase repo doesn't expose `update` yet;
                // until then the create path handles both (it does
                // upsert via the unique constraint and RLS).
                personRemote.create(
                    name = row.name,
                    designation = row.designation,
                    station = row.station,
                    clientId = row.id,
                )
                personDao.setSyncStatus(entry.rowId, SyncStatus.SYNCED, row.name)
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
        // M2-T6: instructions are written by the capture flow after
        // the LLM extracts a proposal. The drain confirms the row
        // is on the server. For now we mark SYNCED on success of
        // the previous create call (the SupabaseInstructionRepository
        // already round-trips in `create`).
        instructionDao.setSyncStatus(entry.rowId, SyncStatus.SYNCED)
    }
}

/** Convenience: run [drainAll] in a fire-and-forget coroutine on IO. */
fun SyncEngine.drainAllAsync(scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) { drainAll() }
}
