package com.baton.app.ui.home

import app.cash.turbine.test
import com.baton.app.data.local.RoomPersonRepository
import com.baton.app.data.person.Person
import com.baton.app.data.sync.RealtimeSync
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val repo: RoomPersonRepository = mockk(relaxed = true)
    private val realtime: RealtimeSync = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val personsFlow = MutableStateFlow<List<Person>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default: empty persons list. Individual tests push values
        // into the flow directly.
        every { repo.observeAll() } returns personsFlow.asStateFlow()
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
        personsFlow.value = emptyList()

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
        personsFlow.value = persons

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
        personsFlow.value = initial

        // A hot flow that the test can emit to.
        val changes = MutableSharedFlow<RealtimeSync.Change>(replay = 0, extraBufferCapacity = 4)
        every { realtime.changes } returns changes

        val vm = HomeViewModel(repo, realtime)
        advanceUntilIdle()

        // After init, state should be Loaded(initial). The init block
        // also fires refreshFromNetwork once (best-effort, ignored
        // in this test).
        assertEquals(HomeUiState.Loaded(initial), vm.state.value)

        // Now emit a Persons change. The VM should call
        // refreshFromNetwork (which fetches from Supabase, not
        // observed here, but the call itself is what we verify).
        changes.tryEmit(RealtimeSync.Change.Persons)
        advanceUntilIdle()

        coVerify(atLeast = 1) { repo.refreshFromNetwork() }
    }
}
