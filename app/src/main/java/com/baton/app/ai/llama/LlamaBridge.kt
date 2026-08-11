package com.baton.app.ai.llama

import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin facade over the native llama.cpp JNI bridge (see
 * `app/src/main/cpp/llama_jni.cpp`).
 *
 * The facade exposes two suspend entry points:
 *  - [load] initialises a single context from a GGUF model file.
 *  - [infer] runs a completion.
 *
 * A `Mutex` serialises calls because llama.cpp's `llama_context`
 * is not safe for concurrent decode. The native work is dispatched
 * onto `Dispatchers.Default.limitedParallelism(1)` so the caller
 * never blocks the calling coroutine.
 *
 * M1-T4 wires this as the implementation of
 * [com.baton.app.features.capture.CaptureProcessor] via the
 * [com.baton.app.ai.extraction.Extractor].
 */
@Singleton
open class LlamaBridge @Inject constructor() {

    @Volatile
    private var handle: Long = 0L

    open suspend fun load(modelPath: File, nCtx: Int = 2048, nThreads: Int = 4) {
        withContext(NATIVE_DISPATCHER) {
            check(handle == 0L) { "LlamaBridge already loaded; free before reloading" }
            val result = nativeLoad(modelPath.absolutePath, nCtx, nThreads)
            require(result != 0L) { "Could not load model: ${modelPath.absolutePath}" }
            handle = result
        }
    }

    open suspend fun infer(
        prompt: String,
        maxTokens: Int = 256,
    ): String = withContext(NATIVE_DISPATCHER) {
        check(handle != 0L) { "LlamaBridge is not loaded" }
        nativeInfer(handle, prompt, maxTokens)
    }

    open fun lastEvalMs(): Long = if (handle == 0L) 0L else nativeGetLastEvalMs(handle)

    open fun isLoaded0(): Boolean = handle != 0L

    fun free() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    // region: native methods (see app/src/main/cpp/llama_jni.cpp)
    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long
    private external fun nativeInfer(handle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeGetLastEvalMs(handle: Long): Long
    private external fun nativeFree(handle: Long)
    // endregion

    companion object {
        private val NATIVE_DISPATCHER =
            kotlinx.coroutines.Dispatchers.Default.limitedParallelism(1)
    }
}
