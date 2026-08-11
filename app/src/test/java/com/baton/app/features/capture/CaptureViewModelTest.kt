package com.baton.app.features.capture

import app.cash.turbine.test
import com.baton.app.data.captures.Capture
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.CaptureRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the M1 capture state machine. The [CaptureProcessor]
 * is a no-op by default; the test wires a fake that returns a
 * deterministic proposal so we can assert the state transitions.
 *
 * M1-T2 adds [CaptureRepository] to the ViewModel constructor; the
 * tests use a fake that records the captures and returns
 * deterministic ids.
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

    private fun fakeRepo() = FakeCaptureRepository()

    @Test
    fun `openSheet makes the sheet visible`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null }, captureRepository = fakeRepo())
        vm.state.test {
            assertEquals(CaptureUiState(), awaitItem())
            vm.openSheet()
            assertTrue(awaitItem().isVisible)
        }
    }

    @Test
    fun `dismissSheet resets to idle`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null }, captureRepository = fakeRepo())
        vm.openSheet()
        vm.onTextChanged("hello")
        vm.dismissSheet()
        assertEquals(CaptureUiState(), vm.state.value)
    }

    @Test
    fun `onTextChanged updates text and clears any prior error`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null }, captureRepository = fakeRepo())
        vm.openSheet()
        vm.onTextChanged("first")
        assertEquals("first", vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `canExtract is false when text is blank`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null }, captureRepository = fakeRepo())
        vm.openSheet()
        assertFalse(vm.state.value.canExtract)
        vm.onTextChanged("")
        assertFalse(vm.state.value.canExtract)
        vm.onTextChanged("hi")
        assertTrue(vm.state.value.canExtract)
    }

    @Test
    fun `onExtract with no-op processor surfaces error and leaves sheet open`() = runTest(testDispatcher) {
        val repo = fakeRepo()
        val vm = CaptureViewModel(processor = CaptureProcessor { null }, captureRepository = repo)
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
        val repo = fakeRepo()
        val vm = CaptureViewModel(processor = CaptureProcessor { proposal }, captureRepository = repo)
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
    fun `onConfirm with a proposal dismisses the sheet`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SHO Ramu",
            action = "file FIR 47",
            instructionText = "Tell SHO Ramu to file FIR 47 by Friday",
            confidence = 0.92,
        )
        val vm = CaptureViewModel(processor = CaptureProcessor { proposal }, captureRepository = fakeRepo())
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        vm.onConfirm()
        assertFalse(vm.state.value.isVisible)
        assertNull(vm.state.value.proposal)
    }

    @Test
    fun `onConfirm without a proposal is a no-op`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null }, captureRepository = fakeRepo())
        vm.openSheet()
        vm.onTextChanged("hello")
        vm.onConfirm()
        assertTrue(vm.state.value.isVisible)
    }
}
