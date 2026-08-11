package com.baton.app.features.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the note bar + capture sheet. In M1, the [CaptureProcessor] is a
 * no-op by default (M1-T2 wires a real insert-capture call; M1-T4 wires
 * the on-device LLM).
 *
 * The state class is in [CaptureUiState]. The four events a user can fire
 * are [openSheet], [dismissSheet], [onTextChanged], [onExtract] and
 * [onConfirm] (latter is a no-op until M1-T5).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val processor: CaptureProcessor,
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
     * Run the LLM extraction on the current [CaptureUiState.text]. Stays
     * open on success (showing the proposal) or failure (showing the
     * error). M1-T2 inserts a row in the `captures` table before this
     * call; M1-T4 wires the real LLM.
     */
    fun onExtract() {
        val current = _state.value
        if (!current.canExtract) return
        val text = current.text
        _state.update { it.copy(isExtracting = true, error = null) }
        viewModelScope.launch {
            runCatching { processor.process(text) }
                .onSuccess { proposal ->
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
