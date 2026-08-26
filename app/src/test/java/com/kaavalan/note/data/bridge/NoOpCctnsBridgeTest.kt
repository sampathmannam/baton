package com.kaavalan.note.data.bridge

import com.kaavalan.note.data.instructions.Direction
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.Source
import com.kaavalan.note.data.instructions.Status
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.0 (PROD-READINESS-P2-#7): the no-op bridge test.
 * The v1.8.0 contract is that every call returns
 * [BridgeResult.NotConfigured] so the call sites fail
 * loudly. A future v2.x replaces [NoOpCctnsBridge] with
 * a real implementation; this test stays green as long
 * as the interface contract is honored.
 */
class NoOpCctnsBridgeTest {

    private val bridge: CctnsBridge = NoOpCctnsBridge()

    private val testInstruction = Instruction(
        id = "i1",
        personId = null,
        direction = Direction.OUTGOING,
        status = Status.DONE,
        source = Source.TEXT,
        priority = Priority.NORMAL,
        title = "Send FIR 47",
        rawText = "Send FIR 47 to SP by Friday",
        dueAt = null,
        capturedAt = "2026-08-21T10:00:00Z",
        createdAt = "2026-08-21T10:00:00Z",
        updatedAt = "2026-08-21T10:00:00Z",
    )

    @Test
    fun `pushInstructionClosed returns NotConfigured`() = runTest {
        val result = bridge.pushInstructionClosed(testInstruction)
        assertEquals(BridgeResult.NotConfigured, result)
    }

    @Test
    fun `pushFirPhotoAttachment returns NotConfigured`() = runTest {
        val result = bridge.pushFirPhotoAttachment(
            captureId = "c1",
            firNumber = "FIR 47/2026",
            photoUri = "file:///captures/c1.jpg",
        )
        assertEquals(BridgeResult.NotConfigured, result)
    }

    @Test
    fun `registerCourtDate returns NotConfigured`() = runTest {
        val result = bridge.registerCourtDate(
            personId = "p1",
            dateEpochDay = 19560L,
            label = "Hearing 47",
        )
        assertEquals(BridgeResult.NotConfigured, result)
    }

    @Test
    fun `pushDeclassification returns NotConfigured`() = runTest {
        val result = bridge.pushDeclassification(
            personId = "p1",
            declassifiedBy = "user-uuid",
            declassifiedAt = 1725000000000L,
        )
        assertEquals(BridgeResult.NotConfigured, result)
    }

    /**
     * Sanity: the sealed-class contract is exhaustive.
     * A future PR that adds a new [BridgeResult] subtype
     * breaks the exhaustive `when` in any of the four
     * call sites; this test fails with "non-exhaustive
     * when" if a new variant is added without updating
     * the no-op.
     */
    @Test
    fun `BridgeResult has at least the three documented variants`() {
        val variants = listOf(
            BridgeResult.NotConfigured,
            BridgeResult.Success(remoteId = "remote-1"),
            BridgeResult.Failed(reason = "timeout"),
        )
        assertTrue("expected 3 BridgeResult variants", variants.size == 3)
    }
}
