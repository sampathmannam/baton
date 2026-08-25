package com.kaavalan.note.ui.today.win

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.local.AppDatabase
import com.kaavalan.note.data.local.CaptureDao
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.entities.CaptureEntity
import com.kaavalan.note.data.local.entities.InstructionEntity
import com.kaavalan.note.data.local.entities.SyncStatus
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * v2.0 Tier 2 (§2.11): the [TodaysWinViewModel] rolls up the
 * user's day. The test seeds captures + instructions at
 * various timestamps and asserts the counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TodaysWinViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var captureDao: CaptureDao
    private lateinit var instructionDao: InstructionDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        captureDao = db.captureDao()
        instructionDao = db.instructionDao()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `empty state when no captures or instructions`() = runTest {
        val vm = TodaysWinViewModel(captureDao, instructionDao)
        // Subscribe so the stateIn upstream starts collecting.
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertEquals(0, s.captureCount)
        assertEquals(0, s.peopleCount)
        assertEquals(0, s.carriedOverCount)
        assertEquals(0, s.sensitiveCount)
        assertTrue(s.isEmpty)
    }

    @Test
    fun `recent captures and instructions are counted`() = runTest {
        seedCapture(minutesAgo = 10)
        seedCapture(minutesAgo = 30)
        seedInstruction(personId = "p1", minutesAgo = 5)
        seedInstruction(personId = "p2", minutesAgo = 15)
        val vm = TodaysWinViewModel(captureDao, instructionDao)
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertEquals(2, s.captureCount)
        assertEquals(2, s.peopleCount)
        assertFalse(s.isEmpty)
    }

    @Test
    fun `older than 24h rows are not counted`() = runTest {
        seedCapture(minutesAgo = 30)
        seedCapture(daysAgo = 2)  // outside the window
        val vm = TodaysWinViewModel(captureDao, instructionDao)
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertEquals(1, s.captureCount)
    }

    @Test
    fun `carried over count only includes CARRIED_OVER status`() = runTest {
        seedInstruction(personId = "p1", minutesAgo = 30, status = "CARRIED_OVER")
        seedInstruction(personId = "p2", minutesAgo = 30, status = "OPEN")
        val vm = TodaysWinViewModel(captureDao, instructionDao)
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertEquals(1, s.carriedOverCount)
    }

    @Test
    fun `sensitive count only includes isSensitive=true instructions`() = runTest {
        seedInstruction(personId = "p1", minutesAgo = 30, isSensitive = true)
        seedInstruction(personId = "p2", minutesAgo = 30, isSensitive = false)
        val vm = TodaysWinViewModel(captureDao, instructionDao)
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        assertEquals(1, s.sensitiveCount)
    }

    private fun seedCapture(
        minutesAgo: Long = 0,
        daysAgo: Long = 0,
    ) {
        val now = Instant.now()
        val ts = now.minus(minutesAgo, ChronoUnit.MINUTES)
            .minus(daysAgo, ChronoUnit.DAYS)
            .toString()
        kotlinx.coroutines.runBlocking {
            captureDao.upsert(CaptureEntity(
                id = UUID.randomUUID().toString(),
                mode = "TEXT",
                rawText = "Note $minutesAgo",
                audioUri = null,
                imageUri = null,
                processed = true,
                createdAt = ts,
                syncStatus = SyncStatus.SYNCED,
            ))
        }
    }

    private fun seedInstruction(
        personId: String,
        minutesAgo: Long = 0,
        daysAgo: Long = 0,
        status: String = "OPEN",
        isSensitive: Boolean = false,
    ) {
        val now = Instant.now()
        val ts = now.minus(minutesAgo, ChronoUnit.MINUTES)
            .minus(daysAgo, ChronoUnit.DAYS)
            .toString()
        kotlinx.coroutines.runBlocking {
            instructionDao.upsert(InstructionEntity(
                id = UUID.randomUUID().toString(),
                personId = personId,
                direction = "OUTGOING",
                status = status,
                source = "TEXT",
                priority = "NORMAL",
                title = "t",
                rawText = "r",
                dueAt = null,
                capturedAt = ts,
                createdAt = ts,
                updatedAt = ts,
                isSensitive = isSensitive,
                syncStatus = SyncStatus.SYNCED,
            ))
        }
    }

    private fun assertMyTrue(b: Boolean) = org.junit.Assert.assertTrue(b)
}

private fun assertTrue(b: Boolean) = org.junit.Assert.assertTrue(b)
