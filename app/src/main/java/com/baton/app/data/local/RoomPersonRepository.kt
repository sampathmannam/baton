package com.baton.app.data.local

import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonInsert
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.person.SupabasePersonRepository
import com.baton.app.data.person.toDomain
import com.baton.app.data.person.toEntity
import com.baton.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M2-T6: Room-backed [PersonRepository]. The UI reads from Room
 * (reactive Flow), writes go through Room + the [SyncEngine]
 * outbox.
 *
 * **Read path** is a `Flow<List<Person>>` mapped from
 * [PersonDao.observeAll]. The HomeViewModel collects this; Room
 * emits a new list on every insert / update / delete. The
 * Realtime subscription's re-fetch on `Change.Persons` is no
 * longer needed for the user's own writes (Room is reactive
 * for those); it stays useful for *other* devices' changes,
 * which the realtime handler dispatches to [refreshFromNetwork].
 *
 * **Write path** is optimistic: insert into Room with
 * `syncStatus = PENDING_INSERT` and a client-generated UUID,
 * enqueue the network call in the sync outbox, and return the
 * local row to the caller. The user sees the new person
 * immediately. The SyncEngine drains the outbox in the
 * background; on success the local row is updated with the
 * server's response and `syncStatus` flips to `SYNCED`.
 *
 * **Offline tolerance:** if the network call fails (timeout,
 * DNS, server 5xx), the entry stays in the outbox and is
 * retried on the next drain. The user keeps their locally
 * created person across app restarts.
 *
 * **Threading:** all Room calls run on `Dispatchers.IO`; the
 * public suspend functions are safe to call from any
 * dispatcher. The background drain uses the
 * [ApplicationScope]-qualified CoroutineScope from `SyncModule`.
 */
@Singleton
class RoomPersonRepository @Inject constructor(
    private val dao: PersonDao,
    private val syncQueueDao: SyncQueueDao,
    private val remote: SupabasePersonRepository,
    private val syncEngine: SyncEngine,
    @ApplicationScope private val appScope: CoroutineScope,
) : PersonRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val now: () -> Long = { System.currentTimeMillis() }

    override fun observeAll(): Flow<List<Person>> = dao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(
        name: String,
        designation: String?,
        station: String?,
        clientId: String?,
    ): Person {
        val nowIso = nowIso()
        val id = clientId ?: UUID.randomUUID().toString()
        val local = PersonEntity(
            id = id,
            name = name,
            designation = designation,
            station = station,
            phone = null,
            userId = "",
            createdAt = nowIso,
            updatedAt = nowIso,
            syncStatus = SyncStatus.PENDING_INSERT,
        )
        dao.upsert(local)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "persons",
                rowId = id,
                op = SyncQueueEntity.OP_INSERT,
                payloadJson = json.encodeToString(
                    PersonInsert.serializer(),
                    PersonInsert(id = id, name = name, designation = designation, station = station),
                ),
                createdAt = now(),
            )
        )
        // Fire-and-forget drain. On success, the SyncEngine
        // updates the row with the server's response and flips
        // `syncStatus` to `SYNCED`. The user keeps seeing the
        // local row in the meantime.
        //
        // The [appScope] is the application-scoped CoroutineScope
        // from [com.baton.app.di.SyncModule] — its dispatcher is
        // `Dispatchers.IO` in production and `TestDispatcher` in
        // unit tests, so the launch inherits the right context
        // without us hardcoding IO here. We don't wrap the body
        // in `withContext(Dispatchers.IO)` either: Room's suspend
        // functions already dispatch internally, and an extra
        // withContext would hop off the testDispatcher and break
        // unit tests that rely on the runTest dispatcher.
        appScope.launch {
            syncEngine.drainOne(id, "persons", SyncQueueEntity.OP_INSERT)
        }
        return local.toDomain()
    }

    override suspend fun findByName(name: String): Person? {
        return dao.findByName(name)?.toDomain()
            ?: remote.findByName(name)?.also { upsertFromNetwork(it) }
    }

    override suspend fun findOrCreate(
        name: String,
        designation: String?,
        station: String?,
    ): Person {
        findByName(name)?.let { return it }
        return create(name = name, designation = designation, station = station)
    }

    /**
     * M2-T6: pull all rows from Supabase and upsert into Room.
     * Called on app start (after auth) and on every Realtime
     * `Change.Persons` event.
     */
    suspend fun refreshFromNetwork() {
        val remoteRows = remote.fetchAll()
        // v1.0: spec §13. is_sensitive rows are local-only; filter
        // them out of the network pull so the local mirror never
        // claims a server row that doesn't exist there.
        val nonSensitive = remoteRows.filter { !it.isSensitive }
        dao.upsertAll(nonSensitive.map { it.toEntity(SyncStatus.SYNCED) })
    }

    /**
     * v1.1.1: spec §13 — flip the local-only flag and push the
     * change to the server. The row stays in Room (the user is
     * still tracking the person) but the server's `is_sensitive`
     * column must reflect the new value so the row is filtered
     * on the way out.
     *
     * **v1.1.1 root-cause fix:** v1.1 only updated local Room and
     * never enqueued a sync-queue entry, so the server's
     * `is_sensitive` stayed `false` after a toggle. We now enqueue
     * an `OP_UPDATE` (the same op the instruction-set uses) and
     * fire-and-forget drain. The drain's `processPersonEntry`
     * PATCHes the server's `is_sensitive` to match the local row.
     * On failure the entry stays in the outbox and is retried on
     * the next drain / app start.
     *
     * Both ON and OFF flips are enqueued; the SyncEngine
     * `processPersonEntry` PATCHes the column to the local value
     * regardless of direction.
     */
    override suspend fun setSensitive(id: String, sensitive: Boolean) {
        val now = java.time.Instant.now().toString()
        // Always PENDING_UPDATE until the wire side confirms.
        // The drain flips it to SYNCED on success. We don't try
        // to be clever with "the row was already SYNCED so skip
        // the wire call" — toggling is_sensitive is a wire-level
        // change and must reach the server.
        dao.setSensitive(id, sensitive, now, SyncStatus.PENDING_UPDATE)
        // Read the row back so the payload carries the canonical
        // name/designation/station (the SyncEngine decodes it as
        // a PersonInsert to keep the LWW conflict check happy).
        val row = dao.getById(id) ?: return
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "persons",
                rowId = id,
                op = SyncQueueEntity.OP_UPDATE,
                payloadJson = json.encodeToString(
                    PersonInsert.serializer(),
                    PersonInsert(
                        id = row.id,
                        name = row.name,
                        designation = row.designation,
                        station = row.station,
                    ),
                ),
                createdAt = now(),
            )
        )
        appScope.launch {
            syncEngine.drainOne(id, "persons", SyncQueueEntity.OP_UPDATE)
        }
    }

    private suspend fun upsertFromNetwork(person: Person) {
        dao.upsert(person.toEntity(SyncStatus.SYNCED))
    }

    private fun nowIso(): String =
        java.time.Instant.now().toString()
}
