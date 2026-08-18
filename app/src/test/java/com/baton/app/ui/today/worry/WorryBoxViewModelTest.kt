package com.baton.app.ui.today.worry

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.instructions.RoomInstructionRepository
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.SyncEngine
import com.baton.app.data.local.SyncQueueDao
import com.baton.app.data.local.TouchPersonOnActivity
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncStatus
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * v2.0 Tier 2 (§2.10): the [WorryBoxViewModel] combines worry
 * instructions + worry captures into one list, sorted by
 * `reviewAtEpochDay` ASC (the soonest first), then by
 * `createdAt` DESC. The "resolve" and "keep" actions update
 * the underlying DAO rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorryBoxViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var instructionDao: InstructionDao
    private lateinit var captureDao: CaptureDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var instructionFtsDao: com.baton.app.data.local.InstructionFtsDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        instructionDao = db.instructionDao()
        captureDao = db.captureDao()
        syncQueueDao = db.syncQueueDao()
        instructionFtsDao = db.instructionFtsDao()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `state is empty when there are no worry rows`() = runTest {
        val vm = makeVm()
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertTrue(s.isEmpty)
    }

    @Test
    fun `worry instruction with review date appears in the box`() = runTest {
        val id = seedWorryInstruction(urgency = "worry_with_date",
            reviewEpochDay = 19600L)  // 2023-09-04 ish
        val vm = makeVm()
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertEquals(1, s.items.size)
        assertTrue(s.items[0] is WorryItem.Instruction)
        assertEquals(id, s.items[0].data.id)
    }

    @Test
    fun `sorted by reviewAtEpochDay ASC then createdAt DESC`() = runTest {
        // Two worries: A has a later review, B has an earlier
        // review. B should come first.
        val a = seedWorryInstruction(urgency = "worry_with_date", reviewEpochDay = 20000L)
        val b = seedWorryInstruction(urgency = "worry_with_date", reviewEpochDay = 19000L)
        val vm = makeVm()
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        val ids = s.items.map { it.data.id }
        assertEquals(listOf(b, a), ids)
    }

    @Test
    fun `resolveInstruction flips urgency to normal and status to DONE`() = runTest {
        val id = seedWorryInstruction(urgency = "worry_with_date", reviewEpochDay = 20000L)
        val vm = makeVm()
        advanceUntilIdle()
        vm.resolveInstruction(id)
        advanceUntilIdle()
        val row = instructionDao.getById(id)!!
        assertEquals("normal", row.urgency)
        assertEquals("DONE", row.status)
    }

    @Test
    fun `keepInstruction clears reviewAtEpochDay but keeps urgency`() = runTest {
        val id = seedWorryInstruction(urgency = "worry_with_date", reviewEpochDay = 20000L)
        val vm = makeVm()
        advanceUntilIdle()
        vm.keepInstruction(id)
        advanceUntilIdle()
        val row = instructionDao.getById(id)!!
        assertEquals("worry_with_date", row.urgency)
        assertTrue("reviewAtEpochDay should be null after keep", row.reviewAtEpochDay == null)
    }

    @Test
    fun `closed instructions (DONE DROPPED) are excluded from worry box`() = runTest {
        // Seed a worry that's already DONE: must not show in the
        // worry box.
        instructionDao.upsert(makeWorryInstruction(urgency = "worry", status = "DONE"))
        val vm = makeVm()
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertTrue(s.isEmpty)
    }

    private fun makeVm(): WorryBoxViewModel = WorryBoxViewModel(
        instructionDao = instructionDao,
        captureDao = captureDao,
        roomInstructionRepository = RoomInstructionRepository(
            dao = instructionDao,
            syncQueueDao = syncQueueDao,
            syncEngine = mockk<SyncEngine>(relaxed = true),
            touchOnActivity = mockk<TouchPersonOnActivity>(relaxed = true),
            ftsDao = instructionFtsDao,
            appScope = kotlinx.coroutines.GlobalScope,
        ),
    )

    private fun seedWorryInstruction(
        urgency: String = "worry_with_date",
        reviewEpochDay: Long? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val entity = InstructionEntity(
            id = id,
            personId = null,
            direction = "OUTGOING",
            status = "OPEN",
            source = "TEXT",
            priority = "NORMAL",
            title = "Worry",
            rawText = "Worry about something",
            dueAt = null,
            capturedAt = now,
            createdAt = now,
            updatedAt = now,
            isSensitive = false,
            syncStatus = SyncStatus.SYNCED,
            caseType = null,
            urgency = urgency,
            reviewAtEpochDay = reviewEpochDay,
        )
        kotlinx.coroutines.runBlocking { instructionDao.upsert(entity) }
        return id
    }

    private fun makeWorryInstruction(
        urgency: String,
        status: String,
    ): InstructionEntity = InstructionEntity(
        id = UUID.randomUUID().toString(),
        personId = null,
        direction = "OUTGOING",
        status = status,
        source = "TEXT",
        priority = "NORMAL",
        title = "Worry",
        rawText = "Worry about something",
        dueAt = null,
        capturedAt = Instant.now().toString(),
        createdAt = Instant.now().toString(),
        updatedAt = Instant.now().toString(),
        isSensitive = false,
        syncStatus = SyncStatus.SYNCED,
        caseType = null,
        urgency = urgency,
        reviewAtEpochDay = null,
    )
}
