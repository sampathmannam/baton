package com.baton.app.data.undo

/**
 * Tier 1.6 (v2.0): the "last action only" undo buffer.
 *
 * Each destructive action (delete person / delete instruction
 * / delete capture) is wrapped in a small `UndoableAction`
 * that the [com.baton.app.data.undo.UndoController] pushes
 * into a `MutableStateFlow<UndoableAction?>`. The screen's
 * `SnackbarHostState` reads the value and shows the "Undo"
 * affordance.
 *
 * **Memory shape.** A new action replaces the old one
 * (this is "last action only" by spec). The old action is
 * dropped on the floor — we never expose a multi-step
 * history.
 */
sealed interface UndoableAction {
    val id: String

    /** "Undo" label shown in the snackbar. */
    val label: String

    data class DeletePerson(
        override val id: String,
        val name: String,
        val row: com.baton.app.data.local.entities.PersonEntity,
    ) : UndoableAction {
        override val label: String get() = "Person"
    }

    data class DeleteInstruction(
        override val id: String,
        val title: String,
        val row: com.baton.app.data.local.entities.InstructionEntity,
    ) : UndoableAction {
        override val label: String get() = "Instruction"
    }

    data class DeleteCapture(
        override val id: String,
        val preview: String,
        val row: com.baton.app.data.local.entities.CaptureEntity,
    ) : UndoableAction {
        override val label: String get() = "Capture"
    }

    /**
     * v1.8.0 (PROD-READINESS-P1-#6): the "Mark as recent" undo.
     * Carries the previous `lastInteractionAt` (ms-since-epoch)
     * and `updatedAt` (ISO string) so [com.baton.app.data.undo.UndoController.undoLast]
     * can restore the prior decay-state. `null` values mean the
     * person had never been touched; undoing must clear
     * `lastInteractionAt` back to null (not to a sentinel), so
     * the Quiet-a-while list re-includes them.
     */
    data class MarkPersonRecent(
        override val id: String,
        val name: String,
        val previousLastInteractionAt: Long?,
        val previousUpdatedAt: String,
    ) : UndoableAction {
        override val label: String get() = "Mark recent"
    }
}
