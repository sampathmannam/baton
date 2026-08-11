package com.baton.app.features.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.captures.CaptureMode
import com.baton.app.data.captures.CaptureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the note bar + capture sheet. M1-T2 inserts a `captures` row
 * before the LLM runs; M1-T4 wires the on-device LLM; M1-T5 wires the
 * save flow.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val processor: CaptureProcessor,
    private val captureRepository: CaptureRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

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
     * Confirm the proposal. M1: no-op. M1-T5 wires the save flow
     * (instruction + auto-created person). The sheet closes regardless;
     * the caller decides what to do on success.
     */
    fun onConfirm() {
        val current = _state.value
        if (!current.canConfirm) return
        dismissSheet()
    }
}
