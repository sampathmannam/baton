package com.baton.app.features.widget

import com.baton.app.data.captures.Capture
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.CaptureRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * v1.9.10: tests for [QuickNoteViewModel]. The view model is the
 * contract between the widget's [QuickNoteActivity] Compose UI
 * and the [CaptureRepository] write — the test pins the four
 * behaviours the activity relies on:
 *
 *  1. Blank text is a no-op (the activity disables the Save
 *     button too, but defense in depth).
 *  2. A successful save transitions Idle -> Saving -> Saved and
 *     calls [CaptureRepository.create] with [CaptureMode.TEXT].
 *  3. A failed save transitions to [SaveState.Error] with a
 *     user-facing message that does NOT leak the underlying
 *     exception text (BEAU-NEW-01 invariant).
 *  4. A second save on the same view model works — no shared
 *     state that would prevent re-use after a save.
 *
 * The view model is platform-agnostic; Robolectric is used
 * only to satisfy the project-wide Hilt @HiltViewModel pattern
 * (the tests themselves don't touch Android APIs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickNoteViewModelTest {

    private val captureRepository: CaptureRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun `blank text is a no-op and does not touch the repository`() = runTest(testDispatcher) {
        val vm = QuickNoteViewModel(captureRepository)

        vm.save("   ") // whitespace only
        vm.save("")    // empty
        vm.save("\n\t")

        // State stays at Idle (no save was attempted).
        assertEquals(SaveState.Idle, vm.state.value)
        coVerify(exactly = 0) { captureRepository.create(any(), any()) }
    }

    @Test
    fun `successful save transitions to Saved and calls create with TEXT mode`() = runTest(testDispatcher) {
        val captured = Capture(
            id = "cap-1",
            mode = CaptureMode.TEXT,
            rawText = "Saw B. Ramesh near Subedari PS at 14:30",
            audioUri = null,
            imageUri = null,
            processed = false,
            createdAt = "2026-08-24T14:30:00+05:30",
        )
        coEvery { captureRepository.create(any(), any()) } returns captured

        val vm = QuickNoteViewModel(captureRepository)
        vm.save("  Saw B. Ramesh near Subedari PS at 14:30  ")

        // State is Saved.
        assertEquals(SaveState.Saved, vm.state.value)
        // The repository was called with the trimmed text + TEXT mode.
        coVerify(exactly = 1) {
            captureRepository.create("Saw B. Ramesh near Subedari PS at 14:30", CaptureMode.TEXT)
        }
    }

    @Test
    fun `failed save transitions to Error with safe user-facing message`() = runTest(testDispatcher) {
        // The underlying exception contains a "leaky" message —
        // a hypothetical bad implementation that surfaced
        // SafeError.forUser(e, e.message) would leak it.
        val leakyException = IOException(
            "https://internal-restricted.supabase.example/rest/v1/captures: 500 db connection refused",
        )
        coEvery { captureRepository.create(any(), any()) } throws leakyException

        val vm = QuickNoteViewModel(captureRepository)
        vm.save("test note")

        // State is Error, not Saving.
        val state = vm.state.value
        assertTrue("Expected SaveState.Error, was: $state", state is SaveState.Error)
        // The user-facing message does NOT contain the leaky URL.
        val message = (state as SaveState.Error).message
        assertTrue(
            "Error message must not contain the URL fragment, was: $message",
            !message.contains("internal-restricted.supabase.example"),
        )
        assertTrue(
            "Error message must not contain '500', was: $message",
            !message.contains("500"),
        )
    }

    @Test
    fun `view model can be re-used after a save (no stuck Saving state)`() = runTest(testDispatcher) {
        coEvery { captureRepository.create(any(), any()) } returns mockk(relaxed = true)

        val vm = QuickNoteViewModel(captureRepository)
        vm.save("first note")
        assertEquals(SaveState.Saved, vm.state.value)
        // Second save must also work — no stuck Saving flag,
        // no broken state object.
        vm.save("second note")
        assertEquals(SaveState.Saved, vm.state.value)
        coVerify(exactly = 2) { captureRepository.create(any(), any()) }
    }
}
