package com.baton.app.ui.llama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.ai.llama.ModelManager
import com.baton.app.ai.llama.ModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * v1.4.2 (F-10): thin ViewModel that wires the
 * [com.baton.app.ui.llama.ModelDownloadScreen] to the singleton
 * [ModelManager]. The screen is a leaf — it does not own any UI
 * state beyond what the manager publishes — so the ViewModel is
 * deliberately just a passthrough with two action entry points
 * ([startDownload], [retry]).
 *
 * `state` is hot (`StateFlow`) and is shared via `stateIn` so a
 * configuration change (rotation, dark/light toggle) does not
 * re-trigger `ensureModel` and the screen does not flash through
 * `NotStarted` on re-attach.
 */
@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val modelManager: ModelManager,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager
        .ensureModel()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = ModelState.NotStarted,
        )

    /** UI callback for the "Download model" button. */
    fun startDownload() {
        modelManager.download()
    }

    /** UI callback for the "Retry" button on a [ModelState.Failed] state. */
    fun retry() {
        modelManager.download()
    }
}
