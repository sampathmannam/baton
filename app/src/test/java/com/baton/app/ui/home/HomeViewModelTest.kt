package com.baton.app.ui.home

import app.cash.turbine.test
import com.baton.app.data.instructions.RoomInstructionRepository
import com.baton.app.data.instructions.SupabaseInstructionRepository
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonOpenCount
import com.baton.app.data.local.RoomPersonRepository
import com.baton.app.data.person.Person
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
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
    private val instructionDao: InstructionDao = mockk(relaxed = true)
    private val roomInstructionRepository: RoomInstructionRepository = mockk(relaxed = true)
    private val supabaseInstructionRepository: SupabaseInstructionRepository = mockk(relaxed = true)
    private val tagRepository: RoomTagRepository = mockk(relaxed = true)
    private val realtime: RealtimeSync = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val personsFlow = MutableStateFlow<List<Person>>(emptyList())
    private val countsFlow = MutableStateFlow<List<PersonOpenCount>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repo.observeAll() } returns personsFlow.asStateFlow()
        every { instructionDao.observeOpenCountByPerson() } returns countsFlow.asStateFlow()
        every { realtime.changes } returns MutableSharedFlow()
        // M3-T5: launch-time instruction refresh is wired but not
        // relevant to the badge-count assertions. Empty list is fine.
        coEvery { roomInstructionRepository.refreshFromNetwork(supabaseInstructionRepository) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm() = HomeViewModel(
        personRepository = repo,
        instructionDao = instructionDao,
        roomInstructionRepository = roomInstructionRepository,
        supabaseInstructionRepository = supabaseInstructionRepository,
        tagRepository = tagRepository,
        realtimeSync = realtime,
    )

    @Test
    fun `empty state shown when repository returns no persons`() = runTest(testDispatcher) {
        personsFlow.value = emptyList()

        val vm = makeVm()
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

        val vm = makeVm()
        advanceUntilIdle()

        vm.state.test {
            val state = awaitItem()
            // M3-T5: open count defaults to 0 for every person who
            // doesn't appear in the count Flow. The PersonRow uses
            // this to hide the badge entirely.
            assertEquals(HomeUiState.Loaded(persons, openCountByPersonId = mapOf("p1" to 0, "p2" to 0)), state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `M3-T5 loaded state includes the open count per person`() = runTest(testDispatcher) {
        val persons = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
            Person(id = "p2", name = "Priya", designation = "DSP", station = "Srinagar", phone = null),
        )
        personsFlow.value = persons
        countsFlow.value = listOf(
            PersonOpenCount(personId = "p1", cnt = 3),
            PersonOpenCount(personId = "p2", cnt = 0),
        )

        val vm = makeVm()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(HomeUiState.Loaded::class, state::class)
        val loaded = state as HomeUiState.Loaded
        assertEquals(persons, loaded.persons)
        assertEquals(3, loaded.openCountByPersonId["p1"])
        assertEquals(0, loaded.openCountByPersonId["p2"])
    }

    @Test
    fun `M3-T5 person with no count row defaults to 0 (badge hidden)`() = runTest(testDispatcher) {
        // The DAO only emits a row for persons with at least one
        // open instruction. A person who has no open work doesn't
        // show up in the count Flow. The VM should default to 0 so
        // the PersonRow composable can hide the badge entirely.
        val persons = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
        )
        personsFlow.value = persons
        countsFlow.value = emptyList()  // no rows — p1 has no open instructions

        val vm = makeVm()
        advanceUntilIdle()

        val loaded = vm.state.value as HomeUiState.Loaded
        assertEquals(0, loaded.openCountByPersonId["p1"])
    }

    @Test
    fun `Realtime Persons change triggers a refresh`() = runTest(testDispatcher) {
        val initial = listOf(
            Person(id = "p1", name = "Ramu", designation = "SHO", station = "Bandipora", phone = null),
        )
        personsFlow.value = initial

        val changes = MutableSharedFlow<RealtimeSync.Change>(replay = 0, extraBufferCapacity = 4)
        every { realtime.changes } returns changes

        val vm = makeVm()
        advanceUntilIdle()

        assertEquals(HomeUiState.Loaded(initial, openCountByPersonId = mapOf("p1" to 0)), vm.state.value)

        changes.tryEmit(RealtimeSync.Change.Persons)
        advanceUntilIdle()

        coVerify(atLeast = 1) { repo.refreshFromNetwork() }
    }
}
