package com.baton.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.captures.SupabaseCaptureRepository
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncQueueEntity
import com.baton.app.data.local.entities.SyncStatus
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonInsert
import com.baton.app.data.person.SupabasePersonRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [SyncEngine] — the outbox drain that pushes local
 * writes to Supabase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var personRemote: SupabasePersonRepository
    private lateinit var captureRemote: SupabaseCaptureRepository
    private lateinit var instructionRemote: SupabaseInstructionRepository
    private lateinit var engine: SyncEngine
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        personDao = db.personDao()
        syncQueueDao = db.syncQueueDao()
        personRemote = mockk(relaxed = true)
        captureRemote = mockk(relaxed = true)
        instructionRemote = mockk(relaxed = true)
        engine = SyncEngine(
            syncQueueDao = syncQueueDao,
            personDao = personDao,
            captureDao = db.captureDao(),
            instructionDao = db.instructionDao(),
            personRemote = personRemote,
            captureRemote = captureRemote,
            instructionRemote = instructionRemote,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun enqueuePerson(
        id: String,
        name: String,
    ) {
        personDao.upsert(
            PersonEntity(
                id = id,
                name = name,
                designation = "SHO",
                station = "Bandipora",
                phone = null,
                userId = "u-1",
                createdAt = "2026-08-12T00:00:00Z",
                updatedAt = "2026-08-12T00:00:00Z",
                syncStatus = SyncStatus.PENDING_INSERT,
            )
        )
        syncQueueDao.enqueue(
            SyncQueueEntity(
                table = "persons",
                rowId = id,
                op = SyncQueueEntity.OP_INSERT,
                payloadJson = json.encodeToString(
                    PersonInsert.serializer(),
                    PersonInsert(id = id, name = name, designation = "SHO", station = "Bandipora"),
                ),
                createdAt = 1L,
            )
        )
    }

    /**
     * Helper: every drain test sets up the same mockk answer.
     * The remote.create(...) is the [SupabasePersonRepository.create]
     * suspend function with signature
     * `(name, designation, station, clientId)`. Mockk's `args[n]`
     * is the n-th positional argument; we use the index to read
     * the clientId.
     */
    private fun stubRemoteEcho() {
        coEvery { personRemote.create(any(), any(), any(), any()) } answers {
            // args: [name: String, designation: String?, station: String?, clientId: String?]
            val name = args[0] as String
            val designation = args[1] as String?
            val station = args[2] as String?
            val clientId = args[3] as String?
            Person(
                id = clientId ?: "fallback",
                name = name,
                designation = designation,
                station = station,
                phone = null,
            )
        }
    }

    @Test
    fun `drainAll processes person inserts and marks SYNCED`() = runTest {
        enqueuePerson(id = "client-1", name = "Ramu")
        enqueuePerson(id = "client-2", name = "Priya")
        stubRemoteEcho()

        engine.drainAll()
        advanceUntilIdle()

        // Both rows are SYNCED.
        val rows = personDao.snapshot()
        assertEquals(2, rows.size)
        rows.forEach { assertEquals(SyncStatus.SYNCED, it.syncStatus) }
        // Queue is empty.
        assertEquals(0, syncQueueDao.snapshot().size)
    }

    @Test
    fun `drainAll records failure and keeps the entry on error`() = runTest {
        enqueuePerson(id = "client-1", name = "Ramu")
        coEvery { personRemote.create(any(), any(), any(), any()) } throws RuntimeException("offline")

        engine.drainAll()
        advanceUntilIdle()

        // Row stays PENDING_INSERT (drain failed, no update).
        val row = personDao.getById("client-1")
        assertNotNull(row)
        assertEquals(SyncStatus.PENDING_INSERT, row!!.syncStatus)
        // The queue entry remains with attempts++ and lastError set.
        val queue = syncQueueDao.snapshot()
        assertEquals(1, queue.size)
        assertEquals(1, queue[0].attempts)
        assertEquals("offline", queue[0].lastError)
    }

    @Test
    fun `drainOne processes a single entry`() = runTest {
        enqueuePerson(id = "client-1", name = "Ramu")
        stubRemoteEcho()

        engine.drainOne(rowId = "client-1", table = "persons", op = SyncQueueEntity.OP_INSERT)
        advanceUntilIdle()

        val row = personDao.getById("client-1")
        assertNotNull(row)
        assertEquals(SyncStatus.SYNCED, row!!.syncStatus)
        assertEquals(0, syncQueueDao.snapshot().size)
    }

    @Test
    fun `drainOne is a no-op when no entry matches`() = runTest {
        // No entry in the queue.
        engine.drainOne(rowId = "ghost", table = "persons", op = SyncQueueEntity.OP_INSERT)
        advanceUntilIdle()
        // No remote call.
        coVerify(exactly = 0) { personRemote.create(any(), any(), any(), any()) }
    }
}
