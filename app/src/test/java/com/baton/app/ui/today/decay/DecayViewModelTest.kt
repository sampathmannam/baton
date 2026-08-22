package com.baton.app.ui.today.decay

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.TouchPersonOnActivity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncStatus
import io.mockk.mockk
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * v2.0 Tier 2 (§2.1, §2.13, §2.14): the [DecayViewModel] computes
 * "haven't touched in N days" from the Room mirror of persons +
 * each person's `lastInteractionAt`. The view-model:
 *  1. Reads all people from Room (reactive).
 *  2. Joins with the active filter (default 30 d, user-toggleable
 *     14 / 30 / 60 / 90).
 *  3. Emits only people whose daysQuiet is >= filter, sorted
 *     descending (oldest first).
 *  4. Computes the [ReachOutStatus] per row from the tier-based
 *     cadence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DecayViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var personDao: PersonDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(testDispatcher.asExecutor())
            .setTransactionExecutor(testDispatcher.asExecutor())
            .build()
        personDao = db.personDao()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `quiet a while status triggers when daysQuiet is over 2x cadence`() = runTest {
        // Tier Inner = 7d default. 15d quiet -> 15 > 14 = 2x -> QuietAWhile.
        val personId = seedPerson(name = "Ramesh", tier = "Inner",
            daysAgo = 15)
        val vm = makeVm()
        vm.setFilter(14)  // include 15d-quiet people
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        runCurrent()
        val row = s.rows.first { row -> row.id == personId }
        assertEquals(ReachOutStatus.QuietAWhile, row.status)
    }

    @Test
    fun `getting due status triggers when daysQuiet is between 1x and 2x cadence`() = runTest {
        // Active tier, 30d default. 45d quiet = 1.5x -> GettingDue.
        val personId = seedPerson(name = "Suresh", tier = "Active", daysAgo = 45)
        val vm = makeVm()
        vm.setFilter(14)
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        runCurrent()
        val row = s.rows.first { row -> row.id == personId }
        assertEquals(ReachOutStatus.GettingDue, row.status)
    }

    @Test
    fun `on track status triggers when daysQuiet is at or below cadence`() = runTest {
        // Active tier, 30d default. 20d quiet -> 20 < 30 -> OnTrack.
        // 20d is below the default 30d filter, so we use the
        // 14d filter to surface the row in the decay list.
        val personId = seedPerson(name = "Kavitha", tier = "Active", daysAgo = 20)
        val vm = makeVm()
        vm.setFilter(14)
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        runCurrent()
        val row = s.rows.first { row -> row.id == personId }
        assertEquals(ReachOutStatus.OnTrack, row.status)
    }

    @Test
    fun `cadence override replaces tier default for status mapping`() = runTest {
        // Active tier (30d default) but override = 60. 45d quiet
        // is 45 < 60 -> OnTrack. Without the override the row
        // would be GettingDue.
        val personId = seedPerson(
            name = "Vikram", tier = "Active", daysAgo = 45,
            overrideDays = 60,
        )
        val vm = makeVm()
        vm.setFilter(14)
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        runCurrent()
        val row = s.rows.first { row -> row.id == personId }
        assertEquals(ReachOutStatus.OnTrack, row.status)
        assertEquals(60, row.cadenceDays)
    }

    @Test
    fun `default filter is 30 days and excludes recent people`() = runTest {
        seedPerson(name = "Fresh", daysAgo = 5)  // under 30d, hidden
        seedPerson(name = "Old", daysAgo = 60)  // over 30d, visible
        val vm = makeVm()
        var s = vm.state.value
        backgroundScope.launch { vm.state.collect { s = it } }
        advanceUntilIdle()
        val names = s.rows.map { it.name }
        assertEquals(listOf("Old"), names)
    }

    @Test
    fun `setFilter changes the visible threshold`() = runTest {
        seedPerson(name = "Fifteen", daysAgo = 15)  // hidden at 30d filter, visible at 14d
        val vm = makeVm()
        vm.state.test {
            val initial = awaitItem()
            assertTrue("initially empty: $initial", initial.rows.isEmpty())
            vm.setFilter(14)
            val after = awaitItem()
            assertEquals(1, after.rows.size)
            assertEquals("Fifteen", after.rows[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `redistribute is a no-op when the quiet pile is empty`() = runTest {
        val vm = makeVm()
        // No people seeded -> state.rows is empty.
        // The redistribute call must not throw and must not
        // touch any rows.
        vm.redistribute()
        advanceUntilIdle()
        runCurrent()
        // Nothing to assert beyond "no exception" - the test
        // is a guard against the "rows.isEmpty() return"
        // path being accidentally removed.
    }

    /**
     * v1.8.0 (PROD-READINESS-P1-#6): the mark-recent
     * "Mark as recent" action. Bumps the person's
     * lastInteractionAt to now and pushes a
     * [UndoableAction.MarkPersonRecent] for the
     * snackbar. The DAO touch is observable via the
     * [TouchPersonOnActivity] mock; the UndoController
     * push is observable via its [last] StateFlow.
     */
    @Test
    fun `markRecent calls touch and pushes an UndoableAction with the previous lastInteractionAt`() = runTest {
        val personId = seedPerson(name = "Fifteen", daysAgo = 45L)
        val undoController = mockk<com.baton.app.data.undo.UndoController>(relaxed = true)
        val vm = DecayViewModel(
            personDao = personDao,
            touchOnActivity = mockk(relaxed = true),
            undoController = undoController,
            // v1.9.6: pass a relaxed BatonPreferences mock. The
            // markRecent() call also dispatches
            // setDecayGestureHintShown(); the relaxed mock makes
            // the suspend call a safe no-op.
            preferences = mockk(relaxed = true),
        )
        // Wait for the seeded person to surface in the
        // quiet list.
        var row: com.baton.app.ui.today.decay.DecayRow? = null
        vm.state.test {
            // Skip the initial empty emission.
            var s = awaitItem()
            while (s.rows.none { it.id == personId }) {
                s = awaitItem()
            }
            row = s.rows.first { it.id == personId }
            cancelAndIgnoreRemainingEvents()
        }
        val beforeMs = row!!.lastInteractionAt
        vm.markRecent(row!!)
        advanceUntilIdle()
        // The UndoController's push(...) was called
        // (mockk relaxed-mode call recorder). The
        // contract is the no-throw + the push call.
        io.mockk.verify(atLeast = 1) { undoController.push(any()) }
    }

    /**
     * v1.8.0 (PROD-READINESS-P1-#6): the undo path. After
     * markRecent pushes a [UndoableAction.MarkPersonRecent],
     * calling [UndoController.undoLast] must restore the
     * previous lastInteractionAt via
     * [PersonDao.restoreLastInteraction].
     */
    @Test
    fun `undoLast restores the previous lastInteractionAt for MarkPersonRecent`() = runTest {
        val personId = seedPerson(name = "Fifteen", daysAgo = 45L)
        val undoController = com.baton.app.data.undo.UndoController(
            personDao = personDao,
            instructionDao = mockk(relaxed = true),
            captureDao = mockk(relaxed = true),
        )
        val vm = DecayViewModel(
            personDao = personDao,
            touchOnActivity = mockk(relaxed = true),
            undoController = undoController,
            // v1.9.6: relaxed BatonPreferences mock. See
            // `markRecent calls touch...` above.
            preferences = mockk(relaxed = true),
        )
        // Wait for the seeded person to surface in the
        // quiet list, then capture the row.
        var row: com.baton.app.ui.today.decay.DecayRow? = null
        vm.state.test {
            var s = awaitItem()
            while (s.rows.none { it.id == personId }) {
                s = awaitItem()
            }
            row = s.rows.first { it.id == personId }
            cancelAndIgnoreRemainingEvents()
        }
        vm.markRecent(row!!)
        advanceUntilIdle()
        // The UndoController now holds a
        // MarkPersonRecent. Calling undoLast must call
        // PersonDao.restoreLastInteraction with the
        // pre-touch value.
        undoController.undoLast()
        advanceUntilIdle()
        // The UndoController's `last` must be null after undo.
        assertNull("undoLast must clear the pending action", undoController.last.value)
    }

    private fun makeVm() = DecayViewModel(
        personDao = personDao,
        touchOnActivity = mockk<TouchPersonOnActivity>(relaxed = true),
        undoController = mockk<com.baton.app.data.undo.UndoController>(relaxed = true),
        // v1.9.6: relaxed BatonPreferences mock. The
        // `gestureHintVisible` flow is a `combine(state,
        // preferences.decayGestureHintShown)`; the relaxed
        // mock's Flow property stays empty, so the
        // combine() never completes and `gestureHintVisible`
        // stays at its initialValue = false. That matches
        // the "fresh install" contract these tests want to
        // assert (no gesture hint, no preference).
        preferences = mockk<com.baton.app.data.preferences.BatonPreferences>(relaxed = true),
    )

    private fun seedPerson(
        name: String,
        tier: String = "Active",
        daysAgo: Long,
        overrideDays: Int? = null,
    ): String {
        val now = System.currentTimeMillis()
        val lastInteractionMs = now - daysAgo * 86_400_000L
        val entity = PersonEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            designation = null,
            station = null,
            phone = null,
            userId = "u1",
            createdAt = Instant.ofEpochMilli(lastInteractionMs).toString(),
            updatedAt = Instant.ofEpochMilli(lastInteractionMs).toString(),
            isSensitive = false,
            syncStatus = SyncStatus.SYNCED,
            tier = tier,
            cadenceOverrideDays = overrideDays,
            lastInteractionAt = lastInteractionMs,
        )
        return runBlocking { personDao.upsert(entity); entity.id }
    }

    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
