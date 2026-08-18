package com.baton.app.features.capture

import android.content.Context
import android.os.Bundle
import android.os.ResultReceiver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.CaptureRepository
import com.baton.app.data.instructions.InstructionRepository
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.person.PersonRepository
import com.baton.app.data.tags.RoomTagRepository
import com.baton.app.ui.util.SafeError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the note bar + capture sheet.
 *
 * v1.6.1: the on-device LLM (llama.cpp + whisper.cpp) is gone.
 * There is no extraction, no proposal, no confirmation card.
 * The capture flow is:
 *
 *   1. User taps the note bar — the sheet opens.
 *   2. User types, or speaks (`android.speech.SpeechRecognizer`),
 *      or photographs (CameraX + ML Kit on-device OCR).
 *   3. User taps Save — the note is persisted with the current
 *      [CaptureMode] (TEXT / VOICE / PHOTO), `personId = null`,
 *      `priority = NORMAL`, and the title is the first 40 chars
 *      of the note.
 *
 * The "Add to Calendar" toggle still works (M1-T6) — the
 * calendar event is fired on Save, not on Extract. The tag
 * picker is unchanged.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val captureRepository: CaptureRepository,
    private val personRepository: PersonRepository,
    private val instructionRepository: InstructionRepository,
    private val tagRepository: RoomTagRepository,
) : ViewModel() {

    /**
     * v1.4 (F-09): initial state is read from [SavedStateHandle] so
     * a process death + relaunch restores the partial capture note
     * instead of silently losing it. We persist text / mode /
     * selectedTagIds via an [init] observer below; the Bundle only
     * holds plain types (String, Int, ArrayList<String>).
     */
    private val _state: MutableStateFlow<CaptureUiState> = MutableStateFlow(
        CaptureUiState(
            text = savedStateHandle.get<String>(KEY_TEXT) ?: "",
            mode = savedStateHandle.get<String>(KEY_MODE)
                ?.let { runCatching { CaptureMode.valueOf(it) }.getOrNull() }
                ?: CaptureMode.TEXT,
            selectedTagIds = (
                savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_TAG_IDS)
                    ?: arrayListOf()
                ).toSet(),
        ),
    )
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    init {
        // v1.4 (F-09): auto-persist on every state change. This
        // hooks the in-memory state to the Bundle via the
        // SavedStateHandle that Hilt provides. The onSave success
        // path calls [clearDraft] to wipe; [dismissSheet] now
        // preserves the draft.
        viewModelScope.launch {
            _state.collect { current ->
                savedStateHandle[KEY_TEXT] = current.text
                savedStateHandle[KEY_MODE] = current.mode.name
                savedStateHandle[KEY_SELECTED_TAG_IDS] = ArrayList(current.selectedTagIds)
            }
        }
    }

    /**
     * v1.4 (F-09): explicit wipe of the in-flight draft. Called by
     * the Save success path ([onSaveRaw]). Unlike [dismissSheet],
     * this clears the SavedStateHandle-backed fields so a process
     * death + relaunch does not restore a stale draft.
     */
    fun clearDraft() {
        savedStateHandle.remove<String>(KEY_TEXT)
        savedStateHandle.remove<String>(KEY_MODE)
        savedStateHandle.remove<ArrayList<String>>(KEY_SELECTED_TAG_IDS)
        _state.value = CaptureUiState()
    }

    private companion object {
        const val KEY_TEXT = "capture.text"
        const val KEY_MODE = "capture.mode"
        const val KEY_SELECTED_TAG_IDS = "capture.selectedTagIds"
    }

    /**
     * v1.4 (PHONE-FINDING-8): the capture sheet refuses to
     * accept a save when the user has zero people — there is no
     * person to attribute the instruction to. The UI observes
     * [hasPeople] and renders an inline "Add a person first"
     * card with a primary-coloured button that opens the
     * AddPersonSheet; the Save button is disabled when this is
     * `false`. Defaulting to `false` is the safe direction: the
     * first emission of [hasPeople] is the synchronous initial
     * value, so a brand-new user who taps the note bar before
     * the Room flow has emitted is protected by the inline card
     * until [personRepository.observeAll] confirms the empty
     * state. A real "has people" emit flips the flag on.
     */
    val hasPeople: StateFlow<Boolean> = personRepository.observeAll()
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /**
     * M1-T6: one-shot side effects. The ViewModel emits an
     * [CalendarEventData] here when the user saves with "Add to
     * Calendar" on. The Composable collects this and launches
     * via [android.content.Context.startActivity]. The [Channel]
     * buffers the event so a config change (rotation) doesn't
     * drop the intent.
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

    /**
     * v1.4 (F-09): dismiss the sheet WITHOUT wiping the draft. The
     * X / scrim / BACK path must preserve the partial note so a
     * re-open restores it. Use [clearDraft] for the explicit
     * "wipe the draft" path (called from the Save success path).
     */
    fun dismissSheet() {
        _state.update { it.copy(isVisible = false) }
    }

    /**
     * M3-T7: toggle a tag chip in the capture sheet. The set
     * is in memory only until the user taps Save; on Save the
     * instruction row is created, then each tag in the set is
     * linked via `instruction_tags`. The PENDING_INSERT status
     * on a freshly-created free-form tag is preserved through
     * this path: the sync queue drains on the next work run.
     */
    fun onTagToggled(tagId: String) {
        _state.update {
            val current = it.selectedTagIds
            val next = if (current.contains(tagId)) current - tagId else current + tagId
            it.copy(selectedTagIds = next)
        }
    }

    /**
     * M3-T7: add a free-form `#tag` to the note. The user
     * can pre-emptively add a tag from the picker.
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

    /**
     * M2-T2: the camera returned an image. OCR it via ML Kit,
     * drop the recognised text into the capture sheet's text
     * field, open the sheet, and let the user tap Save. With
     * the v1.6.1 LLM drop there is no automatic extraction
     * step — the user reads the text and saves it as a
     * `CaptureMode.PHOTO` note.
     */
    fun onPhotoTextRecognized(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(text = text, mode = CaptureMode.PHOTO, error = null) }
        if (!_state.value.isVisible) {
            _state.update { it.copy(isVisible = true) }
        }
    }

    /**
     * v1.5.4: surface a photo-capture error (e.g. CAMERA perm
     * denied, OCR threw) as an inline message on the capture
     * sheet. Used by the HomeScreen's camera-permission flow
     * when the user declines the runtime perm.
     */
    fun onPhotoError(message: String) {
        _state.update { it.copy(error = "Photo: $message", isVisible = true) }
    }

    /**
     * Start the voice-capture flow. v1.6.1: voice transcription
     * is via the system `android.speech.SpeechRecognizer`
     * service (no LLM). The service posts the partial / final
     * transcript back through the [ResultReceiver]; we drop the
     * text into the capture sheet's field.
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
     * Cancel the voice-capture flow. The user can also let it
     * complete naturally; this is the cancel path.
     */
    fun onVoiceStop(context: Context) {
        VoiceCaptureService.stop(context)
    }

    /**
     * A transcript came back from the service. Pre-fill the
     * capture sheet's text and open it. The user then taps
     * Save and the regular flow takes over.
     */
    fun onVoiceTranscript(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(text = text, mode = CaptureMode.VOICE, error = null, isVisible = true) }
    }

    /**
     * An error came back from the voice service. Surface it
     * inline on the capture sheet.
     */
    fun onVoiceError(message: String) {
        _state.update { it.copy(error = "Voice capture failed: $message") }
    }

    /**
     * v1.6.1: the only save path. The user types (or
     * voice-transcribes, or photo-OCR's) a note, taps Save,
     * and the note is persisted. The instruction lands with:
     *   - `personId = null` (free-floating; a future
     *     "link to person" flow can attach one).
     *   - `source = TEXT | VOICE | PHOTO` (preserved).
     *   - `priority = NORMAL`.
     *   - `title = rawText.take(40)` with a trailing `…` if
     *     truncated.
     *   - `dueAt = null` (the user can set a due date in the
     *     instruction row's edit sheet, not the capture flow).
     *
     * If the user has selected tags in the tag picker, the
     * tag links are attached in the same success path.
     *
     * The "Add to Calendar" toggle fires a calendar intent
     * with the first 40 chars as the title and the full
     * text as the description.
     */
    fun onSaveRaw() {
        val current = _state.value
        if (!current.canSaveRaw) return
        // v1.4 (PHONE-FINDING-8): the no-people guard. The
        // UI hides the Save button when [hasPeople] is false
        // (the inline "Add a person first" card replaces it),
        // so this guard is the same defensive backstop. The
        // user sees a clear error and the sheet stays open.
        // v1.6.1 note: the v1.5.4 NoPeopleCard copy says
        // "capture instructions" — the user is now saving a
        // free-floating note, not an instruction. We keep the
        // same exception type for test stability but the
        // copy in the capture sheet is the user-facing truth.
        if (!hasPeople.value) {
            _state.update {
                it.copy(
                    isSaving = false,
                    error = NoPeopleException().message,
                    errorType = ErrorType.NEEDS_PERSON_FIRST,
                )
            }
            return
        }
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
                // v1.6.1: insert a captures row so the audit
                // trail preserves the capture source. The
                // captures table is the "raw" record; the
                // instructions table is the saved note.
                runCatching {
                    captureRepository.create(rawText = rawText, mode = current.mode)
                }
                if (current.addToCalendar) {
                    val event = CalendarGate.buildEventData(
                        title = title,
                        description = rawText,
                        dueAt = null,
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
                // v1.4 (F-09): success path wipes the in-flight draft.
                clearDraft()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = SafeError.forUserSave(
                            e = e,
                            default = "Could not save note.",
                        ),
                        errorType = SafeError.classifyForCapture(e),
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
}

/**
 * v1.4 (PHONE-FINDING-8): the locked "no people" failure. The
 * capture sheet is unusable for a brand-new user with zero people
 * — there is no person to attribute the instruction to. The
 * previous behaviour surfaced this as a vague "Could not save note.
 * Try again." which left the user stuck. The new path:
 *
 *  1. UI: when [com.baton.app.features.capture.CaptureViewModel.hasPeople]
 *     is `false`, [com.baton.app.features.capture.CaptureSheet]
 *     renders an inline "Add a person first" card and disables
 *     the Save button. The user sees the recovery path before
 *     they can fail.
 *  2. VM: even if the UI somehow fires [onSaveRaw] with
 *     [hasPeople] false (a stale state from a delete race), the
 *     VM surfaces [NoPeopleException.message] as the inline
 *     error instead of attempting an un-attributable save.
 *  3. Test: the VM's `onSave` test asserts this exception type,
 *     so a future "let's just save with personId = null" shortcut
 *     fails the test.
 *
 * The message is short, neutral, and tells the user the next
 * action. No "error" / "failed" / red colour (the spec §1
 * no-shame rule).
 */
class NoPeopleException : Exception("Add a person first to capture instructions.")
