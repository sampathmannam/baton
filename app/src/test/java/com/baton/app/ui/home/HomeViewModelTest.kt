package com.baton.app.ui.home

import app.cash.turbine.test
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.sync.RealtimeSync
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val repo: PersonRepository = mockk()
    private val realtime: RealtimeSync = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default: no Realtime events. Individual tests override
        // to emit a value.
        every { realtime.changes } returns MutableSharedFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty state shown when repository returns no persons`() = runTest(testDispatcher) {
        coEvery { repo.observeAll() } returns emptyList()

        val vm = HomeViewModel(repo, realtime)
        advanceUntilIdle()

        vm.state.test {
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loaded state shown when repository returns persons`() = runTest(testDispatcher) {
        val persons = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
            Person(id = "p2", name = "Priya", designation = "DSP", station = "Srinagar", phone = null),
        )
        coEvery { repo.observeAll() } returns persons

        val vm = HomeViewModel(repo, realtime)
        advanceUntilIdle()

        vm.state.test {
            val state = awaitItem()
            assertEquals(HomeUiState.Loaded(persons), state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Realtime Persons change triggers a refresh`() = runTest(testDispatcher) {
        val initial = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
        )
        val afterInsert = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
            Person(id = "p2", name = "Priya", designation = "DSP", station = "Srinagar", phone = null),
        )
        // First call returns the initial set; subsequent calls
        // (the refresh after a Realtime event) return the updated
        // set.
        coEvery { repo.observeAll() } returnsMany listOf(initial, afterInsert)

        // A hot flow that the test can emit to.
        val changes = MutableSharedFlow<RealtimeSync.Change>(replay = 0, extraBufferCapacity = 4)
        every { realtime.changes } returns changes

        val vm = HomeViewModel(repo, realtime)
        advanceUntilIdle()

        // After init, state should be Loaded(initial).
        assertEquals(HomeUiState.Loaded(initial), vm.state.value)

        // Now emit a Persons change. The VM should re-fetch.
        changes.tryEmit(RealtimeSync.Change.Persons)
        advanceUntilIdle()

        // State should now reflect the inserted person.
        assertEquals(HomeUiState.Loaded(afterInsert), vm.state.value)
    }
}
