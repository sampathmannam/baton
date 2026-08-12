package com.baton.app.ai.whisper

/**
 * Errors raised by the Whisper bridge. Mirrors [com.baton.app.ai.llama.LlamaError]
 * so callers can pattern-match both with a single `when`.
 */
sealed class WhisperError(message: String) : RuntimeException(message) {
    class LoadFailed(reason: String) : WhisperError("Whisper load failed: $reason")
    class NotLoaded : WhisperError("WhisperBridge is not loaded")
    class EmptyAudio : WhisperError("Audio buffer is empty")
    class TranscribeFailed(reason: String) : WhisperError("Whisper transcribe failed: $reason")
}
