package com.baton.app.features.capture

import app.cash.turbine.test
import com.baton.app.data.captures.Capture
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.instructions.Direction
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.InstructionRepository
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.instructions.Status
import com.baton.app.data.person.Person
import com.baton.app.data.person.PersonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the M1 capture state machine. M1-T4 wired the
 * [CaptureProcessor] (LLM); M1-T5 wires the save flow.
 *
 * The test provides fakes for [CaptureRepository], [PersonRepository],
 * and [InstructionRepository] so the save flow can be asserted
 * without a real Supabase backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeCaptureRepository : CaptureRepository {
        var nextId = 0
        val created = mutableListOf<Pair<String, CaptureMode>>()
        val markedProcessed = mutableListOf<String>()
        override suspend fun create(rawText: String, mode: CaptureMode): Capture {
            nextId += 1
            val id = "cap-$nextId"
            created += id to mode
            return Capture(
                id = id,
                mode = mode,
                rawText = rawText,
                processed = false,
                createdAt = "2026-08-11T00:00:00+00:00",
            )
        }
        override suspend fun markProcessed(id: String) {
            markedProcessed += id
        }
    }

    private class FakePersonRepository : PersonRepository {
        var nextId = 0
        val findByNameCalls = mutableListOf<String>()
        val created = mutableListOf<Triple<String, String?, String?>>()
        val existing = mutableMapOf<String, Person>()
        override suspend fun observeAll(): List<Person> = existing.values.toList()
        override suspend fun create(name: String, designation: String?, station: String?): Person {
            created += Triple(name, designation, station)
            nextId += 1
            val person = Person(
                id = "person-$nextId",
                name = name,
                designation = designation,
                station = station,
                phone = null,
            )
            existing[name] = person
            return person
        }
        override suspend fun findByName(name: String): Person? {
            findByNameCalls += name
            return existing[name]
        }
        override suspend fun findOrCreate(
            name: String,
            designation: String?,
            station: String?,
        ): Person = findByName(name) ?: create(name, designation, station)
    }

    private class FakeInstructionRepository : InstructionRepository {
        var nextId = 0
        val created = mutableListOf<CreatedInstruction>()
        override suspend fun create(
            personId: String?,
            source: Source,
            priority: Priority,
            title: String,
            rawText: String,
            dueAt: String?,
        ): Instruction {
            nextId += 1
            val id = "ins-$nextId"
            created += CreatedInstruction(
                id = id,
                personId = personId,
                source = source,
                priority = priority,
                title = title,
                rawText = rawText,
                dueAt = dueAt,
            )
            return Instruction(
                id = id,
                personId = personId,
                direction = Direction.OUTGOING,
                status = Status.OPEN,
                source = source,
                priority = priority,
                title = title,
                rawText = rawText,
                dueAt = dueAt,
                capturedAt = "2026-08-11T00:00:00+00:00",
                createdAt = "2026-08-11T00:00:00+00:00",
                updatedAt = "2026-08-11T00:00:00+00:00",
            )
        }
    }

    private data class CreatedInstruction(
        val id: String,
        val personId: String?,
        val source: Source,
        val priority: Priority,
        val title: String,
        val rawText: String,
        val dueAt: String?,
    )

    private fun fakes(): Triple<FakeCaptureRepository, FakePersonRepository, FakeInstructionRepository> {
        return Triple(FakeCaptureRepository(), FakePersonRepository(), FakeInstructionRepository())
    }

    private fun makeVm(
        processor: CaptureProcessor,
        repo: FakeCaptureRepository,
        person: FakePersonRepository,
        ins: FakeInstructionRepository,
    ): CaptureViewModel = CaptureViewModel(
        processor = processor,
        captureRepository = repo,
        personRepository = person,
        instructionRepository = ins,
    )

    @Test
    fun `openSheet makes the sheet visible`() = runTest(testDispatcher) {
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { null }, repo, person, ins)
        vm.state.test {
            assertEquals(CaptureUiState(), awaitItem())
            vm.openSheet()
            assertTrue(awaitItem().isVisible)
        }
    }

    @Test
    fun `dismissSheet resets to idle`() = runTest(testDispatcher) {
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { null }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged("hello")
        vm.dismissSheet()
        assertEquals(CaptureUiState(), vm.state.value)
    }

    @Test
    fun `onTextChanged updates text and clears any prior error`() = runTest(testDispatcher) {
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { null }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged("first")
        assertEquals("first", vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `canExtract is false when text is blank`() = runTest(testDispatcher) {
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { null }, repo, person, ins)
        vm.openSheet()
        assertFalse(vm.state.value.canExtract)
        vm.onTextChanged("")
        assertFalse(vm.state.value.canExtract)
        vm.onTextChanged("hi")
        assertTrue(vm.state.value.canExtract)
    }

    @Test
    fun `onExtract with no-op processor surfaces error and leaves sheet open`() = runTest(testDispatcher) {
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { null }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged("Tell SHO Ramu to file FIR 47 by Friday")
        vm.onExtract()
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue("Sheet should stay open", s.isVisible)
        assertFalse("Should not be extracting anymore", s.isExtracting)
        assertNull(s.proposal)
        assertEquals("No instruction found. Try rephrasing.", s.error)
        // The capture was created, but NOT marked processed (no instruction
        // came out of the LLM).
        assertEquals(1, repo.created.size)
        assertTrue(repo.markedProcessed.isEmpty())
    }

    @Test
    fun `onExtract with a working processor sets proposal and marks capture processed`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SHO Ramu",
            action = "file FIR 47",
            dueAt = "2026-08-15T17:00:00+05:30",
            priority = "NORMAL",
            instructionText = "Tell SHO Ramu to file FIR 47 by Friday",
            confidence = 0.92,
        )
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(proposal, s.proposal)
        assertFalse(s.isExtracting)
        assertNull(s.error)
        assertTrue(s.canConfirm)
        assertEquals(1, repo.created.size)
        assertEquals(listOf("cap-1"), repo.markedProcessed)
    }

    @Test
    fun `onConfirm with a proposal saves an instruction and dismisses the sheet`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SHO Ramu",
            action = "send FIR 47",
            dueAt = "2026-08-15T17:00:00+05:30",
            priority = "HIGH",
            instructionText = "Tell SHO Ramu to send FIR 47 by Friday",
            confidence = 0.92,
        )
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        vm.onConfirm()
        advanceUntilIdle()

        // 1. The sheet is dismissed.
        val s = vm.state.value
        assertFalse("Sheet should be closed", s.isVisible)
        assertNull(s.proposal)
        assertFalse(s.isSaving)

        // 2. The person was auto-created (findOrCreate called once, inserted once).
        assertEquals(listOf("SHO Ramu"), person.findByNameCalls)
        assertEquals(1, person.created.size)
        assertEquals("SHO Ramu", person.created[0].first)

        // 3. The instruction was created with the right fields.
        assertEquals(1, ins.created.size)
        val saved = ins.created[0]
        assertEquals("person-1", saved.personId)
        assertEquals(Source.TEXT, saved.source)
        assertEquals(Priority.HIGH, saved.priority)
        assertEquals("send FIR 47 — SHO Ramu", saved.title)
        assertEquals(proposal.instructionText, saved.rawText)
        assertEquals(proposal.dueAt, saved.dueAt)
    }

    @Test
    fun `onConfirm with no person still saves a free-floating instruction`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = null,
            action = "review pending cases",
            dueAt = null,
            priority = "NORMAL",
            instructionText = "Review pending cases on Sunday",
            confidence = 0.7,
        )
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        vm.onConfirm()
        advanceUntilIdle()

        assertEquals("should not look up a person when proposal.person is null", 0, person.findByNameCalls.size)
        assertEquals(0, person.created.size)
        assertEquals(1, ins.created.size)
        val saved = ins.created[0]
        assertNull(saved.personId)
        assertEquals("review pending cases", saved.title)
    }

    @Test
    fun `onConfirm without a proposal is a no-op`() = runTest(testDispatcher) {
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { null }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged("hello")
        vm.onConfirm()
        assertTrue(vm.state.value.isVisible)
        assertEquals("should not save anything when there's no proposal", 0, ins.created.size)
    }

    @Test
    fun `onConfirm with an existing person reuses the row instead of creating a new one`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "DSP Srinagar",
            action = "send pending cases list",
            instructionText = "DSP Srinagar to send pending cases list",
            confidence = 0.9,
        )
        val (repo, person, ins) = fakes()
        // Pre-seed the person repo as if the user had added DSP Srinagar
        // earlier in the session.
        person.existing["DSP Srinagar"] = Person(
            id = "person-existing",
            name = "DSP Srinagar",
            designation = "DSP",
            station = "Srinagar Range",
            phone = null,
        )
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        vm.onConfirm()
        advanceUntilIdle()

        assertEquals(listOf("DSP Srinagar"), person.findByNameCalls)
        assertEquals("should not create a duplicate person", 0, person.created.size)
        assertEquals(1, ins.created.size)
        assertEquals("person-existing", ins.created[0].personId)
    }

    @Test
    fun `URGENT priority from LLM is mapped to HIGH`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SP Bandipora",
            action = "call",
            priority = "URGENT",  // legacy value the LLM might still emit
            instructionText = "Call SP Bandipora immediately",
            confidence = 0.95,
        )
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        vm.onConfirm()
        advanceUntilIdle()

        assertEquals(1, ins.created.size)
        assertEquals(Priority.HIGH, ins.created[0].priority)
    }

    @Test
    fun `onConfirm with Add-to-Calendar on and a dueAt emits a calendar event`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SHO Ramu",
            action = "send FIR 47",
            dueAt = "2099-08-15T17:00:00+05:30",  // far future so the test is stable
            priority = "NORMAL",
            instructionText = "Tell SHO Ramu to send FIR 47 by Friday",
            confidence = 0.92,
        )
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        vm.onAddToCalendarChanged(true)
        vm.onConfirm()
        advanceUntilIdle()

        // Read the channel directly. The channel is `internal` so the
        // production side never accesses it; the test gets a clean
        // synchronous poll without Flow collection overhead.
        val event = vm.calendarIntentsChannel.tryReceive().getOrNull()
        assertNotNull("calendar event should be emitted", event)
        assertEquals("send FIR 47 — SHO Ramu", event!!.title)
        assertEquals("Tell SHO Ramu to send FIR 47 by Friday", event.description)
    }

    @Test
    fun `onConfirm with Add-to-Calendar on but no dueAt does NOT emit a calendar event`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = null,
            action = "review pending cases",
            dueAt = null,
            priority = "NORMAL",
            instructionText = "Review pending cases on Sunday",
            confidence = 0.7,
        )
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        vm.onAddToCalendarChanged(true)
        vm.onConfirm()
        advanceUntilIdle()

        val event = vm.calendarIntentsChannel.tryReceive().getOrNull()
        assertNull("no calendar event should be emitted when there's no dueAt", event)
    }

    @Test
    fun `onConfirm without Add-to-Calendar does NOT emit a calendar event`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SHO Ramu",
            action = "send FIR 47",
            dueAt = "2099-08-15T17:00:00+05:30",
            priority = "NORMAL",
            instructionText = "Tell SHO Ramu to send FIR 47 by Friday",
            confidence = 0.92,
        )
        val (repo, person, ins) = fakes()
        val vm = makeVm(CaptureProcessor { proposal }, repo, person, ins)
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        // addToCalendar stays false
        vm.onConfirm()
        advanceUntilIdle()

        val event = vm.calendarIntentsChannel.tryReceive().getOrNull()
        assertNull(event)
    }
}
