package com.baton.app.features.capture

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.instructions.InstructionRepository
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.person.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the note bar + capture sheet.
 *
 *  - M1-T1: the no-op state machine + capture sheet UI.
 *  - M1-T2: inserts a `captures` row before the LLM runs.
 *  - M1-T4: wires the on-device LLM; produces the `ExtractedInstruction`
 *    proposal.
 *  - M1-T5: on Confirm, `findOrCreate` the named person and
 *    `create` the instruction row, then dismiss.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val processor: CaptureProcessor,
    private val captureRepository: CaptureRepository,
    private val personRepository: PersonRepository,
    private val instructionRepository: InstructionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /**
     * M1-T6: one-shot side effects. The ViewModel emits an [Intent]
     * here when the user confirms with "Add to Calendar" on and the
     * LLM extracted a `due_at`. The Composable collects this and
     * launches via [android.content.Context.startActivity]. The
     * [Channel] buffers the event so a config change (rotation)
     * doesn't drop the intent.
     */
    internal val calendarIntentsChannel: Channel<CalendarEventData> = Channel(capacity = Channel.BUFFERED)
    val calendarIntents: Flow<CalendarEventData> = calendarIntentsChannel.receiveAsFlow()

    fun openSheet() {
        _state.update { it.copy(isVisible = true) }
    }

    fun dismissSheet() {
        _state.value = CaptureUiState()
    }

    fun onTextChanged(text: String) {
        _state.update { it.copy(text = text, error = null) }
    }

    fun onAddToCalendarChanged(checked: Boolean) {
        _state.update { it.copy(addToCalendar = checked) }
    }

    fun onProposalPersonChange(value: String) {
        _state.update {
            val proposal = it.proposal ?: return@update it
            it.copy(proposal = proposal.copy(person = value.ifBlank { null }))
        }
    }

    fun onProposalActionChange(value: String) {
        _state.update {
            val proposal = it.proposal ?: return@update it
            it.copy(proposal = proposal.copy(action = value))
        }
    }

    fun onProposalTextChange(value: String) {
        _state.update {
            val proposal = it.proposal ?: return@update it
            it.copy(proposal = proposal.copy(instructionText = value))
        }
    }

    /**
     * Run the LLM extraction on the current [CaptureUiState.text].
     *
     * Sequence:
     *  1. Insert a `captures` row (`mode=TEXT, raw_text=text,
     *     processed=false`). The row id is logged but not currently
     *     surfaced in the UI.
     *  2. Hand the text to the [CaptureProcessor]. The M1 default
     *     returns `null`; M1-T4 wires the on-device LLM.
     *  3. On success, mark the capture `processed=true` (M1-T5 will
     *     also write the linked `instructions` row in the same
     *     operation). On failure, the capture stays `processed=false`
     *     and the user can retry.
     */
    fun onExtract() {
        val current = _state.value
        if (!current.canExtract) return
        val text = current.text
        _state.update { it.copy(isExtracting = true, error = null) }
        viewModelScope.launch {
            val capture = runCatching {
                captureRepository.create(rawText = text, mode = CaptureMode.TEXT)
            }.getOrNull()
            if (capture == null) {
                _state.update {
                    it.copy(
                        isExtracting = false,
                        error = "Could not save note. Try again.",
                    )
                }
                return@launch
            }
            runCatching { processor.process(text) }
                .onSuccess { proposal ->
                    if (proposal != null) {
                        // M1-T5 will replace this with the full save flow;
                        // for now, marking processed is the best we can do.
                        runCatching { captureRepository.markProcessed(capture.id) }
                    }
                    _state.update {
                        if (proposal == null) {
                            it.copy(
                                isExtracting = false,
                                error = "No instruction found. Try rephrasing.",
                            )
                        } else {
                            it.copy(
                                isExtracting = false,
                                proposal = proposal,
                                error = null,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isExtracting = false,
                            error = e.message ?: "Could not extract instruction.",
                        )
                    }
                }
        }
    }

    /**
     * Confirm the proposal and save. Sequence:
     *  1. `personRepository.findOrCreate(name)` if the proposal named a
     *     person. If `proposal.person` is `null`, the instruction is
     *     stored with `person_id = null` (a free-floating note).
     *  2. `instructionRepository.create(...)` writes the row.
     *  3. Dismiss the sheet. On error, surface a user-readable message
     *     and keep the sheet open so the user can retry.
     *
     * M1 only saves; the M2 nudge flow will move the instruction
     * through `ACK_PENDING` → `DONE`. The M1-T6 calendar toggle, when
     * on, also fires a `CalendarContract.Events.Insert` intent in
     * parallel (added in T6).
     */
    fun onConfirm() {
        val current = _state.value
        val proposal = current.proposal ?: return
        if (!current.canConfirm) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            println("DEBUG: onConfirm coroutine started, current.addToCalendar=${current.addToCalendar}")
            val personId: String? = proposal.person?.let { name ->
                runCatching { personRepository.findOrCreate(name = name) }
                    .onFailure {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                error = "Could not save person. Try again.",
                            )
                        }
                    }
                    .getOrNull()
                    ?.id
            }
            if (_state.value.error != null) return@launch
            val title = buildTitle(proposal)
            val priority = parsePriority(proposal.priority)
            val result = runCatching {
                instructionRepository.create(
                    personId = personId,
                    source = Source.TEXT,
                    priority = priority,
                    title = title,
                    rawText = proposal.instructionText,
                    dueAt = proposal.dueAt,
                )
            }
            result.onSuccess {
                // M1-T6: emit a calendar event data *after* the
                // instruction lands. The instruction is the source
                // of truth; the calendar event is a copy. The
                // Composable converts the data to an Intent and
                // launches it via the Activity context.
                if (current.addToCalendar) {
                    val event = CalendarGate.buildEventData(
                        title = title,
                        description = proposal.instructionText,
                        dueAt = proposal.dueAt,
                    )
                    if (event != null) {
                        calendarIntentsChannel.trySend(event)
                    }
                }
                dismissSheet()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Could not save instruction.",
                    )
                }
            }
        }
    }

    private fun buildTitle(proposal: ExtractedInstruction): String {
        val action = proposal.action.trim().ifBlank { proposal.instructionText.take(40) }
        val person = proposal.person?.takeIf { it.isNotBlank() }
        return if (person != null) "$action — $person" else action
    }

    private fun parsePriority(raw: String): Priority = when (raw.trim().uppercase()) {
        "HIGH", "URGENT" -> Priority.HIGH
        "LOW" -> Priority.LOW
        else -> Priority.NORMAL
    }
}
