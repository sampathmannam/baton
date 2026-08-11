package com.baton.app.ai.llama

/**
 * Errors thrown by the on-device LLM. Mapped from native failures
 * (the native side logs the underlying cause via `__android_log_print`).
 */
sealed class LlamaError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ModelNotFound(path: String) : LlamaError("Model file not found: $path")
    class LoadFailed(reason: String) : LlamaError("Could not load model: $reason")
    class InferenceFailed(reason: String) : LlamaError("Inference failed: $reason")
}
