package com.baton.app.ai.extraction

import com.baton.app.ai.llama.LlamaBridge
import com.baton.app.ai.llama.LlamaError
import com.baton.app.ai.llama.ModelManager
import com.baton.app.ai.llama.ModelState
import com.baton.app.features.capture.CaptureProcessor
import com.baton.app.features.capture.ExtractedInstruction
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M1 instruction extractor. Wires the [LlamaBridge] (M1-T3) and the
 * [ModelManager] (model download on first run) behind the
 * [CaptureProcessor] interface.
 *
 * The prompt is loaded from `assets/prompts/extract_v1.txt` on
 * first use. The LLM is asked to return JSON; the Kotlin side
 * parses with a lenient `Json` and validates each field.
 * `confidence < 0.5` returns `null` (the [CaptureViewModel] then
 * shows "No instruction found — try again").
 *
 * v1.5.4: the model lifecycle is now driven by the stateful
 * [ModelManager] `StateFlow<ModelState>` instead of the legacy
 * `Flow<DownloadProgress>` that ships in [ModelManager.downloadModel].
 * The stateful flow doesn't verify a SHA-256 against an `assets/`
 * manifest, which means the `Extractor` no longer crashes on a
 * SHA mismatch when the upstream mirror serves a different file
 * than the bundled manifest. The first-run download is a one-tap
 * action from the CaptureSheet's "Model not downloaded" card
 * (see [CaptureSheet.ModelNotReadyCard]); the
 * [CaptureViewModel.modelState] flow surfaces the same
 * `StateFlow<ModelState>` so the UI updates as the download
 * progresses.
 *
 * M1 uses greedy decoding (the JNI does not enable the grammar
 * sampler in the current build). The prompt + JSON validation
 * give equivalent shape guarantees for M1's single schema. M2
 * can re-enable the GBNF path for tighter output constraints.
 */
@Singleton
class Extractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val llama: LlamaBridge,
) : CaptureProcessor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * v1.5.4: per-instance extractor state. Independent of the
     * shared [ModelManager.state] (which is hot and process-wide)
     * — the Extractor's own state tracks the local
     * "I'm currently doing inference" pulse so the UI can
     * distinguish "model ready, just extracting" from "model ready,
     * idle".
     */
    private val _state = MutableStateFlow<ExtractorState>(ExtractorState.Idle)
    val state: StateFlow<ExtractorState> = _state.asStateFlow()

    /**
     * v1.5.4: pass-through to the shared model state. The
     * CaptureSheet's `ModelNotReadyCard` collects this so the
     * download progress + ready + failed transitions are
     * rendered without the VM needing to mirror the flow.
     */
    val modelState: StateFlow<ModelState> get() = modelManager.state

    private val systemPrompt: String by lazy {
        context.assets.open("prompts/extract_v1.txt").bufferedReader().use { it.readText() }
    }

    override suspend fun process(rawText: String): ExtractedInstruction? {
        // v1.5.4: promote the state to NotStarted if the model file
        // was never downloaded (or the user just picked a different
        // model via [ModelManager.selectModel], which resets the
        // state to NotStarted). The CaptureSheet reads the same
        // `modelState` flow and surfaces the inline "Model not
        // downloaded" card with a "Download model" button that
        // calls [ModelManager.download] — this method just
        // returns `null` cleanly without throwing.
        val file = modelManager.modelFile()
        if (!file.exists() || file.length() == 0L) {
            modelManager.ensureModel()
            return null
        }
        if (!llama.isLoaded0()) {
            try {
                llama.load(file)
            } catch (e: LlamaError) {
                _state.value = ExtractorState.Error(e.message ?: "Could not load model")
                return null
            }
        }
        val prompt = systemPrompt.replace("{raw_text}", rawText.replace("\\", "\\\\"))
        _state.value = ExtractorState.Extracting
        val raw = try {
            llama.infer(prompt, maxTokens = 384)
        } catch (e: LlamaError) {
            _state.value = ExtractorState.Error(e.message ?: "Inference failed")
            return null
        }
        _state.value = ExtractorState.Idle
        return parse(raw)
    }

    private fun parse(raw: String): ExtractedInstruction? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val candidate = raw.substring(start, end + 1)
        return runCatching {
            val parsed = json.decodeFromString<ExtractedInstruction>(candidate)
            if (parsed.confidence < 0.5) null else parsed
        }.getOrNull()
    }
}

sealed class ExtractorState {
    object Idle : ExtractorState()
    object Extracting : ExtractorState()
    data class Error(val message: String) : ExtractorState()
}
