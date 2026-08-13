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
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    private val staleFlow = MutableStateFlow<List<com.baton.app.data.local.PersonStaleAge>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repo.observeAll() } returns personsFlow.asStateFlow()
        every { instructionDao.observeOpenCountByPerson() } returns countsFlow.asStateFlow()
        every { instructionDao.observeStaleByPerson() } returns staleFlow.asStateFlow()
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
            assertEquals(HomeUiState.Loaded(persons, openCountByPersonId = mapOf("p1" to 0, "p2" to 0), stalePersonIds = emptySet()), state)
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

        assertEquals(HomeUiState.Loaded(initial, openCountByPersonId = mapOf("p1" to 0), stalePersonIds = emptySet()), vm.state.value)

        changes.tryEmit(RealtimeSync.Change.Persons)
        advanceUntilIdle()

        coVerify(atLeast = 1) { repo.refreshFromNetwork() }
    }

    /**
     * v1.2 regression test (BEAU-NEW-01 / BUG-AUTH-008).
     *
     * The original `Flow.catch { e -> HomeUiState.Error(e.message) }`
     * surfaced the supabase-kt exception message verbatim, including
     * the full REST URL, the JWT, the apikey, and the
     * `X-Client-Info: supabase-kt/3.1.1` header. This test forces the
     * upstream Flow to throw a `RestException` carrying all of that
     * and asserts the surfaced [HomeUiState.Error] string contains
     * none of it.
     */
    @Test
    fun `BEAU-NEW-01 Flow catch does not leak URL, JWT, apikey, or SDK header`() = runTest(testDispatcher) {
        val secretUrl = "https://cfnmpqwfvhlnbblxqesm.supabase.co/rest/v1/instructions?select=%2A"
        val secretJwt = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.dozjgNryXcZVm5lTHb8KDXGwY4H8Jz9w"
        val secretApikey = "sb_publishable_ueOz-C6YKZM8CDPJSqsSgQ_UtYoPJVm"
        val secretClientInfo = "supabase-kt/3.1.1"

        // Build a real Ktor HttpResponse with a 500 status + leaky body
        // so that RestException carries realistic exception text.
        val leakyResponse: io.ktor.client.statement.HttpResponse =
            HttpClient(MockEngine {
                respond(
                    content = "leaked: $secretUrl $secretJwt $secretApikey $secretClientInfo",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf("X-Client-Info", secretClientInfo),
                )
            }).get("https://example.invalid/")

        val leakyError = BadRequestRestException(
            "GET $secretUrl: 500 $secretJwt $secretApikey $secretClientInfo",
            leakyResponse,
            "Server boom at $secretUrl",
        )

        // Make observeAll() throw our leaky exception.
        // Use a flow that emits once and then throws, so the combine
        // operator has at least one emission to react to before the
        // throw propagates to the .catch block.
        every { repo.observeAll() } returns flow<List<Person>> {
            emit(emptyList())  // initial empty state — triggers combine emission
            throw leakyError    // then throw — propagates to .catch
        }

        val vm = makeVm()

        // Use Turbine to wait until the VM settles into Error.
        vm.state.test {
            // Skip past Loading + Empty.
            var saw = awaitItem()
            while (saw !is HomeUiState.Error) {
                saw = awaitItem()
            }
            val msg = saw.message
            assertFalse("URL leaked: $msg", msg.contains("supabase.co", ignoreCase = true))
            assertFalse("/rest/v1/ leaked: $msg", msg.contains("/rest/v1/", ignoreCase = true))
            assertFalse("JWT leaked: $msg", msg.contains("eyJ", ignoreCase = true))
            assertFalse("Bearer leaked: $msg", msg.contains("Bearer", ignoreCase = true))
            assertFalse("apikey leaked: $msg", msg.contains("sb_publishable", ignoreCase = true))
            assertFalse("SDK name leaked: $msg", msg.contains("supabase-kt", ignoreCase = true))
            assertFalse("X-Client-Info leaked: $msg", msg.contains("X-Client-Info", ignoreCase = true))
            assertFalse("SDK version leaked: $msg", msg.contains("/3.1.1"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
