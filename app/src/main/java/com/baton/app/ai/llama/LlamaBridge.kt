package com.baton.app.ai.llama

import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kotlin facade over the native llama.cpp JNI bridge (see
 * `app/src/main/cpp/llama_jni.cpp`).
 *
 * The facade exposes two suspend entry points:
 *  - [load] initialises a single context from a GGUF model file.
 *  - [infer] runs a completion with optional GBNF grammar.
 *
 * A `Mutex` serialises calls because llama.cpp's `llama_context` is not
 * safe for concurrent decode. The native work is dispatched onto a
 * dedicated single-thread executor so the caller never blocks the
 * calling coroutine.
 *
 * M1-T4 wires this as the implementation of [com.baton.app.features.capture.CaptureProcessor].
 */
class LlamaBridge(
    private val nativeDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines
        .Dispatchers.Default.limitedParallelism(1),
) {

    @Volatile
    private var handle: Long = 0L

    suspend fun load(modelPath: File, nCtx: Int = 2048, nThreads: Int = 4) {
        withContext(nativeDispatcher) {
            check(handle == 0L) { "LlamaBridge already loaded; free before reloading" }
            val result = nativeLoad(modelPath.absolutePath, nCtx, nThreads)
            require(result != 0L) { "Could not load model: ${modelPath.absolutePath}" }
            handle = result
        }
    }

    suspend fun infer(
        prompt: String,
        maxTokens: Int = 256,
    ): String = withContext(nativeDispatcher) {
        check(handle != 0L) { "LlamaBridge is not loaded" }
        nativeInfer(handle, prompt, maxTokens)
    }

    fun lastEvalMs(): Long = if (handle == 0L) 0L else nativeGetLastEvalMs(handle)

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
}
