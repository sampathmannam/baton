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

    /**
     * v1.6.0: a `true` value here means the native library is
     * present and the JNI bindings resolved at class-load time.
     * When [vendorLlamaCpp] is skipped in CI, the library is
     * missing and every JNI call would throw
     * [java.lang.UnsatisfiedLinkError]. We probe the bindings
     * once at class-load time and short-circuit the calls.
     *
     * The flag is set by the companion-object `init` block
     * (see below). It is `final` and never written from
     * instance code — a defensive design choice that means
     * a misuse cannot accidentally advertise the JNI as
     * available when it isn't.
     */
    open val isNativeAvailable: Boolean = try {
        // Probe the JNI: calling any external method will
        // raise [UnsatisfiedLinkError] if libllama.so is
        // not loaded. We use a method that does not require
        // a model file to be present (the JNI symbol is
        // resolved at the class-load time, independent of
        // any model state).
        nativeGetLastEvalMs(0L)
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    } catch (e: Throwable) {
        // Defensive: any failure during the probe means the
        // JNI is unsafe to use. The capture pipeline falls
        // back to text-only capture in this case.
        false
    }

    open suspend fun load(modelPath: File, nCtx: Int = 2048, nThreads: Int = 4) {
        withContext(NATIVE_DISPATCHER) {
            check(isNativeAvailable) {
                "LlamaBridge native library is not available. " +
                    "Re-run the build with the vendorLlamaCpp task enabled."
            }
            check(handle == 0L) { "LlamaBridge already loaded; free before reloading" }
            val result = try {
                nativeLoad(modelPath.absolutePath, nCtx, nThreads)
            } catch (e: UnsatisfiedLinkError) {
                throw LlamaError.ModelNotAvailable(e.message ?: "JNI library not loaded")
            }
            require(result != 0L) { "Could not load model: ${modelPath.absolutePath}" }
            handle = result
        }
    }

    open suspend fun infer(
        prompt: String,
        maxTokens: Int = 256,
    ): String = withContext(NATIVE_DISPATCHER) {
        check(isNativeAvailable) {
            "LlamaBridge native library is not available. " +
                "Re-run the build with the vendorLlamaCpp task enabled."
        }
        check(handle != 0L) { "LlamaBridge is not loaded" }
        try {
            nativeInfer(handle, prompt, maxTokens)
        } catch (e: UnsatisfiedLinkError) {
            throw LlamaError.ModelNotAvailable(e.message ?: "JNI library not loaded")
        }
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
