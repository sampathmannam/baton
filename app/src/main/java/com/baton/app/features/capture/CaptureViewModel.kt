package com.baton.app.features.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.instructions.InstructionRepository
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.tags.RoomTagRepository
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
    private val tagRepository: RoomTagRepository,
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

    init {
        // M3-T7: keep `availableTags` warm. The user picks from
        // the full taxonomy; the Room cache is updated on every
        // Realtime tags event from HomeViewModel.
        viewModelScope.launch {
            tagRepository.observeAll().collect { tags ->
                _state.update { it.copy(availableTags = tags) }
            }
        }
    }

    fun openSheet() {
        _state.update { it.copy(isVisible = true) }
    }

    fun dismissSheet() {
        _state.value = CaptureUiState()
    }

    /**
     * M3-T7: toggle a tag chip in the capture sheet. The set
     * is in memory only until the user taps Save; on Save the
     * instruction row is created, then each tag in the set is
     * linked via `instruction_tags`. The PENDING_INSERT status on
     * a freshly-created free-form tag is preserved through this
     * path: the sync queue drains on the next work run.
     */
    fun onTagToggled(tagId: String) {
        _state.update {
            val current = it.selectedTagIds
            val next = if (current.contains(tagId)) current - tagId else current + tagId
            it.copy(selectedTagIds = next)
        }
    }

    /**
     * M3-T7: add a free-form `#tag` to the proposal. The LLM
     * extractor may surface `proposal.tags` in a future revision;
     * until then the user can pre-emptively add a tag from the
     * picker.
     */
    fun onAddFreeTag(name: String) {
        val clean = name.trim().trimStart('#').take(40)
        if (clean.isBlank()) return
        viewModelScope.launch {
            val tag = tagRepository.findOrCreateFree(clean) ?: return@launch
            _state.update {
                it.copy(selectedTagIds = it.selectedTagIds + tag.id)
            }
        }
    }

    fun onTextChanged(text: String) {
        _state.update { it.copy(text = text, mode = CaptureMode.TEXT, error = null) }
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
     * M2-T2: the camera returned an image. OCR it via ML Kit, drop
     * the recognised text into the capture sheet's text field,
     * open the sheet, and let the user tap Extract.
     *
     * The caller (HomeScreen) does the actual camera launch; this
     * method is invoked from the Composable's camera-result
     * callback once we have the content:// URI.
     */
    fun onPhotoTextRecognized(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(text = text, mode = CaptureMode.PHOTO, error = null) }
        if (!_state.value.isVisible) {
            _state.update { it.copy(isVisible = true) }
        }
    }

    /**
     * M2-T4: start the voice-capture service. The caller must hold
     * `RECORD_AUDIO` already. The service is responsible for
     * AudioRecord + WhisperBridge; this VM just hands the
     * [ResultReceiver] over and waits for the transcript (delivered
     * via [onVoiceTranscript]) or an error ([onVoiceError]).
     *
     * The receiver must be `Parcelable` because it crosses the
     * Intent extra boundary. The Activity creates the receiver and
     * passes it in; the VM owns the actual `send()` call.
     */
    fun onVoiceStart(context: Context) {
        val receiver = object : ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                when (resultCode) {
                    VoiceCaptureService.RESULT_OK -> {
                        val text = resultData?.getString(VoiceCaptureService.KEY_TEXT) ?: ""
                        onVoiceTranscript(text)
                    }
                    VoiceCaptureService.RESULT_ERROR -> {
                        val err = resultData?.getString(VoiceCaptureService.KEY_ERROR) ?: "Unknown"
                        onVoiceError(err)
                    }
                }
            }
        }
        VoiceCaptureService.start(context, receiver)
    }

    /**
     * M2-T4: stop the service early. The user can also let it
     * complete naturally; this is the cancel path.
     */
    fun onVoiceStop(context: Context) {
        VoiceCaptureService.stop(context)
    }

    /**
     * M2-T4: a transcript came back from the service. Pre-fill the
     * capture sheet's text and open it. The user then taps Extract
     * (or edits) and the regular flow takes over.
     */
    fun onVoiceTranscript(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(text = text, mode = CaptureMode.VOICE, error = null, isVisible = true) }
    }

    /**
     * M2-T4: an error came back from the service. Surface it
     * inline on the capture sheet.
     */
    fun onVoiceError(message: String) {
        _state.update { it.copy(error = "Voice capture failed: $message") }
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
     * M2-T2 photo path. Same as [onExtract] but writes the
     * capture with `mode=PHOTO` and a `image_uri` so the captures
     * table reflects the source. M1's `create()` signature doesn't
     * accept image_uri; M3's Room mirror will. For M2 we pass
     * mode=PHOTO with raw_text = OCR text (the same as the text
     * path); the image is held in cacheDir/captures/ until the
     * user closes the sheet, then uploaded as part of a future
     * M3 sync. M2 ships a capture row that points at the URI
     * indirectly via the row id mapping.
     */
    fun onPhotoExtract(ocrText: String, imageUriString: String) {
        if (ocrText.isBlank()) return
        viewModelScope.launch {
            val capture = runCatching {
                captureRepository.create(rawText = ocrText, mode = CaptureMode.PHOTO)
            }.getOrNull()
            if (capture == null) {
                _state.update {
                    it.copy(error = "Could not save photo. Try again.")
                }
                return@launch
            }
            runCatching { processor.process(ocrText) }
                .onSuccess { proposal ->
                    if (proposal != null) {
                        runCatching { captureRepository.markProcessed(capture.id) }
                    }
                    _state.update {
                        if (proposal == null) {
                            it.copy(
                                isExtracting = false,
                                error = "No instruction found in the photo. Try rephrasing.",
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
                    // v1.1: source reflects how the user actually
                    // captured the thought, not a hard-coded TEXT.
                    source = modeToSource(current.mode),
                    priority = priority,
                    title = title,
                    rawText = proposal.instructionText,
                    dueAt = proposal.dueAt,
                )
            }
            result.onSuccess { created ->
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
                if (current.selectedTagIds.isNotEmpty()) {
                    runCatching {
                        tagRepository.attachToInstruction(
                            instructionId = created.id,
                            tagIds = current.selectedTagIds.toList(),
                        )
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

    /**
     * v1.1: spec §12 — "LLM extraction fails → raw text saved as-is
     * with a `needs_review=true` flag; user can tag/edit later."
     *
     * The M1 UX surface for this is a "Save as text" button shown
     * when the LLM returned no proposal (or the user wants to skip
     * extraction entirely). The instruction lands with a generic
     * title (`rawText` truncated to 40 chars), no person, no due
     * date, and a `priority = NORMAL`. The audit trail preserves
     * the capture mode so a future review can show "you typed this
     * but didn't extract" vs "you spoke this and the LLM missed it".
     */
    fun onSaveRaw() {
        val current = _state.value
        if (!current.canSaveRaw) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val rawText = current.text.trim()
            val truncated = rawText.take(40)
            val title = if (rawText.length > 40) "$truncated…" else rawText
            val result = runCatching {
                instructionRepository.create(
                    personId = null,
                    source = modeToSource(current.mode),
                    priority = Priority.NORMAL,
                    title = title,
                    rawText = rawText,
                    dueAt = null,
                )
            }
            result.onSuccess { created ->
                if (current.selectedTagIds.isNotEmpty()) {
                    runCatching {
                        tagRepository.attachToInstruction(
                            instructionId = created.id,
                            tagIds = current.selectedTagIds.toList(),
                        )
                    }
                }
                dismissSheet()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Could not save raw note.",
                    )
                }
            }
        }
    }

    private fun modeToSource(mode: CaptureMode): Source = when (mode) {
        CaptureMode.TEXT -> Source.TEXT
        CaptureMode.VOICE -> Source.VOICE
        CaptureMode.PHOTO -> Source.PHOTO
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
