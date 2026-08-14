package com.baton.app.ui.llama

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.ai.llama.ModelManager
import com.baton.app.ai.llama.ModelOption
import com.baton.app.ai.llama.ModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * v1.4.2 (F-10): thin ViewModel that wires the
 * [com.baton.app.ui.llama.ModelDownloadScreen] to the singleton
 * [ModelManager]. The screen is a leaf — it does not own any UI
 * state beyond what the manager publishes — so the ViewModel is
 * deliberately just a passthrough with action entry points
 * ([startDownload], [retry], [selectModel]).
 *
 * v1.4.3 (F-10): extended with the model-picker surface
 * ([models], [currentModelId], [selectModel]) so the screen can
 * render the "Switch model" dialog.
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

    /**
     * v1.4.3 (F-10): the catalogue of pickable models. Sourced
     * directly from [ModelManager.availableModels] (a constant),
     * so this flow's value is stable across the process lifetime.
     */
    val models: StateFlow<List<ModelOption>> =
        MutableStateFlow(ModelManager.availableModels)

    /**
     * v1.4.3 (F-10): the `id` of the currently-selected model.
     * Derived from [ModelManager.currentModel] so the screen can
     * highlight the active option in the picker dialog.
     */
    val currentModelId: StateFlow<String> = modelManager
        .currentModel
        .map { it.id }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = modelManager.currentModel.value.id,
        )

    /** UI callback for the "Download model" button. */
    fun startDownload() {
        modelManager.download()
    }

    /** UI callback for the "Retry" button on a [ModelState.Failed] state. */
    fun retry() {
        modelManager.download()
    }

    /**
     * v1.4.3 (F-10): UI callback for the "Switch" button in the
     * model picker dialog. Delegates to [ModelManager.selectModel]
     * which persists the choice and resets the download state.
     */
    fun selectModel(option: ModelOption) {
        modelManager.selectModel(option)
    }
}
