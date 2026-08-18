package com.baton.app.ai.llama

/**
 * Errors thrown by the on-device LLM. Mapped from native failures
 * (the native side logs the underlying cause via `__android_log_print`).
 */
sealed class LlamaError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotFound(path: String) : LlamaError("Model file not found: $path")
    class LoadFailed(reason: String) : LlamaError("Could not load model: $reason")
    class InferenceFailed(reason: String) : LlamaError("Inference failed: $reason")

    /**
     * v1.6.0: the JNI library (libllama.so) is not present in
     * the APK because [vendorLlamaCpp] was excluded from the
     * build. This is *not* a runtime failure of the model; it
     * is a build configuration. The Extractor catches this
     * specifically and falls back to text-only capture.
     */
    class ModelNotAvailable(reason: String) : LlamaError("LLM library not available: $reason")
}
