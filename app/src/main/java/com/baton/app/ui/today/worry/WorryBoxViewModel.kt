package com.baton.app.ui.today.worry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.instructions.RoomInstructionRepository
import com.baton.app.data.local.CaptureDao
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.entities.CaptureEntity
import com.baton.app.data.local.entities.InstructionEntity
import com.baton.app.data.local.entities.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * v2.0 Tier 2 (§2.10): ViewModel for the "Worry box" section on
 * the Today tab. Combines worry instructions (urgency IN
 * ('worry', 'worry_with_date')) and worry captures into a single
 * list, sorted by `reviewAtEpochDay` ASC (or `createdAt` DESC
 * for free worries), and exposes actions to "review and let go"
 * (resolve) or "keep" (clear the review date but stay in the box).
 *
 * **Status semantics for instructions.** Resolving a worry sets
 * `urgency = 'normal'`, `status = 'DONE'`, and stamps
 * `completedAt`. The instruction leaves the box and goes into
 * the "Got done today" column of the evening review. Keeping a
 * worry clears `reviewAtEpochDay` so the user sees it next time
 * they tap in, without an automatic review prompt.
 *
 * **Status semantics for captures.** Captures don't have a
 * `status` column; resolving flips `urgency` to 'normal' and the
 * row leaves the box. Keeping clears `reviewAtEpochDay`.
 */
@HiltViewModel
class WorryBoxViewModel @Inject constructor(
    private val instructionDao: InstructionDao,
    private val captureDao: CaptureDao,
    private val roomInstructionRepository: RoomInstructionRepository,
) : ViewModel() {

    val state: StateFlow<WorryBoxUiState> = combine(
        instructionDao.observeWorry(),
        captureDao.observeWorry(),
    ) { ins, cap ->
        val items = buildList {
            ins.forEach { add(WorryItem.Instruction(it.toItem())) }
            cap.forEach { add(WorryItem.Capture(it.toItem())) }
        }.sortedWith(
            compareBy<WorryItem> { it.data.reviewEpochDay == null }
                .thenBy { it.data.reviewEpochDay ?: 0L }
                .thenByDescending { it.data.createdEpochMs },
        )
        WorryBoxUiState(items = items, isEmpty = items.isEmpty())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorryBoxUiState(isEmpty = true),
    )

    fun resolveInstruction(id: String) {
        viewModelScope.launch {
            val now = Instant.now().toString()
            instructionDao.resolveWorry(id, now, SyncStatus.PENDING_UPDATE)
        }
    }

    fun keepInstruction(id: String) {
        viewModelScope.launch {
            val now = Instant.now().toString()
            instructionDao.keepWorry(id, now, SyncStatus.PENDING_UPDATE)
        }
    }

    fun resolveCapture(id: String) {
        viewModelScope.launch { captureDao.resolveWorry(id) }
    }

    fun keepCapture(id: String) {
        viewModelScope.launch { captureDao.keepWorry(id) }
    }

    private fun InstructionEntity.toItem(): WorryItemData = WorryItemData(
        id = id,
        title = title,
        rawText = rawText,
        reviewEpochDay = reviewAtEpochDay,
        createdEpochMs = Instant.parse(createdAt).toEpochMilli(),
    )

    private fun CaptureEntity.toItem(): WorryItemData = WorryItemData(
        id = id,
        title = rawText?.take(80) ?: "(photo)",
        rawText = rawText,
        reviewEpochDay = reviewAtEpochDay,
        createdEpochMs = Instant.parse(createdAt).toEpochMilli(),
    )
}

data class WorryBoxUiState(
    val items: List<WorryItem> = emptyList(),
    val isEmpty: Boolean = true,
)

data class WorryItemData(
    val id: String,
    val title: String,
    val rawText: String?,
    val reviewEpochDay: Long?,
    val createdEpochMs: Long,
)

sealed interface WorryItem {
    val data: WorryItemData
    data class Instruction(override val data: WorryItemData) : WorryItem
    data class Capture(override val data: WorryItemData) : WorryItem
}
