package com.kaavalan.note.data.undo

import com.kaavalan.note.data.local.CaptureDao
import com.kaavalan.note.data.local.InstructionDao
import com.kaavalan.note.data.local.PersonDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tier 1.6 (v2.0): the undo controller.
 *
 * Process-singleton (not VM-scoped) so the snackbar is
 * shown even if the user navigates away between the
 * destructive action and the tap on "Undo". The flow is
 * a hot [StateFlow] of the last [UndoableAction]; `null`
 * means "nothing pending".
 *
 * `undoLast()` runs the inverse action and emits `null`.
 * `clear()` is called by the snackbar timeout (5 s) so
 * the next action can replace the last.
 */
@Singleton
class UndoController @Inject constructor(
    private val personDao: PersonDao,
    private val instructionDao: InstructionDao,
    private val captureDao: CaptureDao,
) {

    private val _last = MutableStateFlow<UndoableAction?>(null)
    val last: StateFlow<UndoableAction?> = _last.asStateFlow()

    fun push(action: UndoableAction) {
        _last.value = action
    }

    fun clear() {
        _last.value = null
    }

    suspend fun undoLast() {
        val action = _last.value ?: return
        when (action) {
            is UndoableAction.DeletePerson -> {
                personDao.upsert(action.row)
            }
            is UndoableAction.DeleteInstruction -> {
                instructionDao.upsert(action.row)
            }
            is UndoableAction.DeleteCapture -> {
                captureDao.upsert(action.row)
            }
            is UndoableAction.MarkPersonRecent -> {
                // v1.8.0 (PROD-READINESS-P1-#6): restore the
                // person's prior lastInteractionAt. The
                // previousLastInteractionAt is nullable
                // because the user may have marked a
                // never-touched person as recent; undoing
                // must restore the null state (not a
                // sentinel), so they go back to the
                // "never touched" group on Today.
                personDao.restoreLastInteraction(
                    personId = action.id,
                    lastInteractionAtMs = action.previousLastInteractionAt,
                    updatedAt = action.previousUpdatedAt,
                )
            }
        }
        _last.value = null
    }
}
