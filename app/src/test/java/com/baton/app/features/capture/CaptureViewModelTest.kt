package com.baton.app.features.capture

import app.cash.turbine.test
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
 * Unit tests for the M1-T1 capture state machine. The [CaptureProcessor]
 * is a no-op by default; the test wires a fake that returns a
 * deterministic proposal so we can assert the state transitions.
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

    @Test
    fun `openSheet makes the sheet visible`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null })
        vm.state.test {
            assertEquals(CaptureUiState(), awaitItem())
            vm.openSheet()
            assertTrue(awaitItem().isVisible)
        }
    }

    @Test
    fun `dismissSheet resets to idle`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null })
        vm.openSheet()
        vm.onTextChanged("hello")
        vm.dismissSheet()
        assertEquals(CaptureUiState(), vm.state.value)
    }

    @Test
    fun `onTextChanged updates text and clears any prior error`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null })
        vm.openSheet()
        vm.onTextChanged("first")
        assertEquals("first", vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `canExtract is false when text is blank`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null })
        vm.openSheet()
        assertFalse(vm.state.value.canExtract)
        vm.onTextChanged("")
        assertFalse(vm.state.value.canExtract)
        vm.onTextChanged("hi")
        assertTrue(vm.state.value.canExtract)
    }

    @Test
    fun `onExtract with no-op processor surfaces error and leaves sheet open`() = runTest(testDispatcher) {
        val vm = CaptureViewModel(processor = CaptureProcessor { null })
        vm.openSheet()
        vm.onTextChanged("Tell SHO Ramu to file FIR 47 by Friday")
        vm.onExtract()
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue("Sheet should stay open", s.isVisible)
        assertFalse("Should not be extracting anymore", s.isExtracting)
        assertNull(s.proposal)
        assertEquals("No instruction found. Try rephrasing.", s.error)
    }

    @Test
    fun `onExtract with a working processor sets proposal`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SHO Ramu",
            action = "file FIR 47",
            dueAt = "2026-08-15T17:00:00+05:30",
            priority = "NORMAL",
            instructionText = "Tell SHO Ramu to file FIR 47 by Friday",
            confidence = 0.92,
        )
        val vm = CaptureViewModel(processor = CaptureProcessor { proposal })
        vm.openSheet()
        vm.onTextChanged(proposal.instructionText)
        vm.onExtract()
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(proposal, s.proposal)
        assertFalse(s.isExtracting)
        assertNull(s.error)
        assertTrue(s.canConfirm)
    }

    @Test
    fun `onConfirm with a proposal dismisses the sheet`() = runTest(testDispatcher) {
        val proposal = ExtractedInstruction(
            person = "SHO Ramu",
            action = "file FIR 47",
            instructionText = "Tell SHO Ramu to file FIR 47 by Friday",
            confidence = 0.92,
        )
        val vm = CaptureViewModel(processor = CaptureProcessor { proposal })
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
        val vm = CaptureViewModel(processor = CaptureProcessor { null })
        vm.openSheet()
        vm.onTextChanged("hello")
        // No extract, no proposal
        vm.onConfirm()
        assertTrue(vm.state.value.isVisible)  // sheet still open
    }
}
