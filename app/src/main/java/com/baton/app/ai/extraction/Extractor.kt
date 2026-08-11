package com.baton.app.ai.extraction

import com.baton.app.ai.llama.LlamaBridge
import com.baton.app.ai.llama.LlamaError
import com.baton.app.ai.llama.ModelManager
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

    private val _state = MutableStateFlow<ExtractorState>(ExtractorState.Idle)
    val state: StateFlow<ExtractorState> = _state.asStateFlow()

    private val systemPrompt: String by lazy {
        context.assets.open("prompts/extract_v1.txt").bufferedReader().use { it.readText() }
    }

    override suspend fun process(rawText: String): ExtractedInstruction? {
        ensureModelLoaded()
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

    private suspend fun ensureModelLoaded() {
        if (llama.isLoaded0()) return
        val file = modelManager.modelFile()
        if (!file.exists()) {
            modelManager.downloadModel().collect { /* TODO: emit progress */ }
        }
        llama.load(file)
    }
}

sealed class ExtractorState {
    object Idle : ExtractorState()
    object Extracting : ExtractorState()
    data class Error(val message: String) : ExtractorState()
}
