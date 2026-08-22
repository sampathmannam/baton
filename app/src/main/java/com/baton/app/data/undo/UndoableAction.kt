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

    /**
     * v1.9.6 (drive-verify polish #6): the human-readable
     * subject of the action, rendered in the snackbar message
     * ("<label> <displayName>"). Must be the user-visible name
     * / title / preview — never a UUID fragment.
     *
     * v1.9.5 shipped with `MainActivity.kt` reading
     * `action.id.take(6)`, which exposed a 6-char UUID prefix
     * to the user on every destructive action. The 6-char
     * prefix survives until the row is re-inserted (e.g. on
     * Undo), so the message read "Mark recent 96ldae" instead
     * of "Mark recent B. Ramesh Naidu". This property fixes
     * the source of the bug — every action variant now declares
     * its own display name, and the snackbar reads from
     * `displayName`, not from `id`.
     */
    val displayName: String

    data class DeletePerson(
        override val id: String,
        val name: String,
        val row: com.baton.app.data.local.entities.PersonEntity,
    ) : UndoableAction {
        override val label: String get() = "Person"
        override val displayName: String get() = name
    }

    data class DeleteInstruction(
        override val id: String,
        val title: String,
        val row: com.baton.app.data.local.entities.InstructionEntity,
    ) : UndoableAction {
        override val label: String get() = "Instruction"
        override val displayName: String get() = title
    }

    data class DeleteCapture(
        override val id: String,
        val preview: String,
        val row: com.baton.app.data.local.entities.CaptureEntity,
    ) : UndoableAction {
        override val label: String get() = "Capture"
        override val displayName: String get() = preview
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
        override val displayName: String get() = name
    }
}
