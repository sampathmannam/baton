package com.baton.app.data.undo

import com.baton.app.data.local.entities.CaptureEntity
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.PersonEntity
import com.baton.app.data.local.entities.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.9.6 (drive-verify polish #6): the snackbar UUID bug
 * regression suite. v1.9.5 shipped
 * `MainActivity.kt:285 = "${action.label} ${action.id.take(6)}"`
 * which exposed a 6-char UUID prefix to the user on every
 * destructive action ("Mark recent 96ldae" instead of "Mark
 * recent B. Ramesh Naidu"). The fix moves the snackbar's
 * subject off `action.id` and onto a new `action.displayName`
 * property, which every variant now overrides with the
 * user-visible name.
 *
 * These 4 tests lock the per-variant contract. Any future
 * refactor that drops an override (or accidentally wires
 * `displayName` to `id`) fails the build before the APK
 * ships.
 */
class UndoableActionTest {

    private fun person(id: String, name: String) = PersonEntity(
        id = id, name = name, designation = null, station = null, phone = null,
        userId = "u", createdAt = "2026-08-12T00:00:00+00:00",
        updatedAt = "2026-08-12T00:00:00+00:00", isSensitive = false,
        syncStatus = SyncStatus.SYNCED,
    )

    private fun instruction(id: String) = InstructionEntity(
        id = id, personId = null, direction = "OUTGOING", status = "OPEN",
        source = "TEXT", priority = "NORMAL", title = "title", rawText = "raw",
        dueAt = null, capturedAt = "2026-08-12T00:00:00+00:00",
        createdAt = "2026-08-12T00:00:00+00:00",
        updatedAt = "2026-08-12T00:00:00+00:00", isSensitive = false,
        syncStatus = SyncStatus.SYNCED, completedAt = null, droppedReason = null,
        nextActionAt = null,
    )

    private fun capture(id: String) = CaptureEntity(
        id = id, mode = "TEXT", rawText = "raw", audioUri = null,
        imageUri = null, processed = false,
        createdAt = "2026-08-12T00:00:00+00:00",
        syncStatus = SyncStatus.SYNCED,
    )

    @Test
    fun `displayName for DeletePerson returns name`() {
        val action = UndoableAction.DeletePerson(
            id = "96ldae11-0000-0000-0000-000000000000",
            name = "B. Ramesh Naidu",
            row = person("96ldae11-0000-0000-0000-000000000000", "B. Ramesh Naidu"),
        )
        assertEquals("B. Ramesh Naidu", action.displayName)
    }

    @Test
    fun `displayName for DeleteInstruction returns title`() {
        val action = UndoableAction.DeleteInstruction(
            id = "a1b2c3d4-0000-0000-0000-000000000000",
            title = "Temple land inquiry — follow up by Friday",
            row = instruction("a1b2c3d4-0000-0000-0000-000000000000"),
        )
        assertEquals(
            "Temple land inquiry — follow up by Friday",
            action.displayName,
        )
    }

    @Test
    fun `displayName for DeleteCapture returns preview`() {
        val action = UndoableAction.DeleteCapture(
            id = "deadbeef-0000-0000-0000-000000000000",
            preview = "Briefing for the next SP-level meeting",
            row = capture("deadbeef-0000-0000-0000-000000000000"),
        )
        assertEquals(
            "Briefing for the next SP-level meeting",
            action.displayName,
        )
    }

    @Test
    fun `displayName for MarkPersonRecent returns name`() {
        // v1.9.6 regression: the v1.9.5 snackbar read
        // "Mark recent 96ldae" because `action.id.take(6)`
        // took the first 6 chars of the contact's UUID. The
        // fix is `displayName` -> the person's full name.
        val action = UndoableAction.MarkPersonRecent(
            id = "96ldae11-0000-0000-0000-000000000000",
            name = "B. Ramesh Naidu",
            previousLastInteractionAt = 1_700_000_000_000L,
            previousUpdatedAt = "2026-08-12T00:00:00+00:00",
        )
        assertEquals("B. Ramesh Naidu", action.displayName)
    }
}
