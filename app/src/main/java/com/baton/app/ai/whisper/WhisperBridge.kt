package com.baton.app.ai.whisper

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin facade over the native whisper.cpp JNI bridge (see
 * `app/src/main/cpp/whisper_jni.cpp`).
 *
 * The facade exposes two suspend entry points:
 *  - [load] initialises a single context from a ggml-tiny.en.bin model
 *    file.
 *  - [transcribe] runs a single transcription over a PCM byte array
 *    (16-bit little-endian, mono, 16 kHz — the format the M2-T4
 *    `VoiceCaptureService` produces via `AudioRecord`).
 *
 * The native work is dispatched onto
 * `Dispatchers.Default.limitedParallelism(1)` so the calling
 * coroutine never blocks. The bridge is `@Singleton` and the
 * underlying context is single-use; concurrent calls queue via the
 * dispatcher.
 *
 * **Model file**: not bundled. The first call to [load] must point
 * at a file produced by [WhisperModelManager] (which downloads
 * ggml-tiny.en.bin to `filesDir/models/ggml-tiny.en.bin` on first
 * run and SHA-256-verifies it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
open class WhisperBridge @Inject constructor() {

    @Volatile
    private var handle: Long = 0L

    /**
     * Suspend load. `modelPath` should point at a ggml-tiny.en.bin
     * file. After this returns, [isLoaded] is true. Throws
     * [WhisperError.LoadFailed] on any failure.
     */
    open suspend fun load(modelPath: File, nThreads: Int = 4) {
        withContext(WHISPER_DISPATCHER) {
            check(handle == 0L) { "WhisperBridge already loaded; free before reloading" }
            val result = nativeLoad(modelPath.absolutePath, nThreads)
            require(result != 0L) { "Could not load whisper model: ${modelPath.absolutePath}" }
            handle = result
        }
    }

    /**
     * Transcribe PCM bytes (16-bit LE, mono, 16 kHz). Returns the
     * concatenated segment text. Empty string if the model produced
     * no output (e.g. silence). Throws [WhisperError.NotLoaded] if
     * [load] hasn't been called, [WhisperError.EmptyAudio] if the
     * buffer is empty.
     */
    open suspend fun transcribe(pcmBytes: ByteArray, sampleRate: Int = 16000): String {
        withContext(WHISPER_DISPATCHER) {
            if (handle == 0L) throw WhisperError.NotLoaded()
            require(pcmBytes.isNotEmpty()) { throw WhisperError.EmptyAudio() }
        }
        val text = withContext(WHISPER_DISPATCHER) {
            check(handle != 0L) { "WhisperBridge is not loaded" }
            nativeTranscribe(handle, pcmBytes, sampleRate)
        }
        return text
    }

    /**
     * Wall-clock ms of the last transcribe call. 0 if no call has
     * happened yet. The UI uses this to surface a "Transcribed in
     * 1.4s" affordance.
     */
    open fun lastEvalMs(): Long = if (handle == 0L) 0L else nativeGetLastEvalMs(handle)

    open fun isLoaded0(): Boolean = handle != 0L

    /** Free the underlying context. Idempotent. */
    open fun free() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    private external fun nativeLoad(modelPath: String, nThreads: Int): Long
    private external fun nativeTranscribe(handle: Long, pcmBytes: ByteArray, sampleRate: Int): String
    private external fun nativeGetLastEvalMs(handle: Long): Long
    private external fun nativeFree(handle: Long)

    companion object {
        private val WHISPER_DISPATCHER =
            kotlinx.coroutines.Dispatchers.Default.limitedParallelism(1)

        init {
            // The native lib is named libbaton-whisper.so (see CMakeLists.txt).
            // System.loadLibrary throws UnsatisfiedLinkError if whisper.cpp
            // wasn't vendored at build time — the Kotlin layer treats that
            // as "voice features unavailable" rather than a hard error.
            try {
                System.loadLibrary("baton-whisper")
            } catch (e: UnsatisfiedLinkError) {
                // Voice capture requires whisper.cpp to be vendored
                // (Gradle task `vendorWhisperCpp`). Until then, all
                // [transcribe] calls will throw [WhisperError.NotLoaded].
            }
        }
    }
}
