package com.baton.app.ai.llama

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the Qwen 3 1.7B Q4_K_M GGUF model on first run.
 *
 * v1.4.2 (F-10): the original `downloadModel()` flow (read URL +
 * SHA-256 from `assets/`, stream to a `.part` file, verify, rename)
 * is preserved for the [com.baton.app.ai.extraction.Extractor] which
 * calls [modelFile] + `downloadModel().collect { }` during
 * `ensureModelLoaded`. The new [ensureModel] / [download] entry
 * points are the user-facing first-run UX: a [StateFlow] of
 * [ModelState] that the [com.baton.app.ui.llama.ModelDownloadScreen]
 * composable subscribes to and renders as a progress card.
 *
 * The stateful API uses a **hard-coded placeholder URL** (see
 * [DEFAULT_MODEL_URL]) instead of reading `assets/model_url.txt` —
 * the first-run screen is wired at code level so a fresh checkout
 * knows where the model is hosted without a build-time asset swap.
 * The real production URL still lives in `model_url.txt` and is
 * used by the legacy `downloadModel()` flow.
 *
 * **Model file location:** `filesDir/models/qwen3-1.7b-q4_k_m.gguf`
 * (gitignored). The file name follows the project's existing
 * convention used by the legacy `downloadModel()` flow and the
 * [Extractor]. The placeholder URL points at a different Qwen
 * release on purpose — it's a stand-in for the production URL.
 */
@Singleton
open class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {

    /**
     * Backing scope for the fire-and-forget download launched by
     * [download]. Uses `Dispatchers.IO` (not the test dispatcher)
     * because [OkHttpClient.newCall] is a blocking call; the
     * download is not on any caller-supplied scope.
     */
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ModelState>(ModelState.NotStarted)

    /**
     * Current download state. The flow is hot and conflated
     * (`StateFlow`); collectors see the latest value on subscribe
     * and every transition thereafter. The initial value is
     * [ModelState.NotStarted]; [ensureModel] promotes it to
     * [ModelState.Ready] if the model is already on disk.
     */
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /**
     * First-run entry point used by [com.baton.app.ui.llama.ModelDownloadScreen].
     *
     * Returns the same [state] flow the screen already collects.
     * Calling this method scans [modelFile]; if the file exists
     * and is non-empty, the state is promoted to [ModelState.Ready]
     * (idempotent). Otherwise the state stays at [ModelState.NotStarted]
     * and the screen renders the "Download model" button which
     * calls [download].
     */
    @Synchronized
    fun ensureModel(): StateFlow<ModelState> {
        if (_state.value !is ModelState.NotStarted) return _state
        val target = modelFile()
        if (target.exists() && target.length() > 0) {
            _state.value = ModelState.Ready(target.absolutePath, target.length())
        }
        return _state
    }

    /**
     * Kicks off a background download. Fire-and-forget — the call
     * returns immediately and the state is updated via [state] as
     * the download progresses.
     *
     * Idempotent: a second call while a download is in flight is a
     * no-op, and a call when the model is already [ModelState.Ready]
     * is also a no-op. A call after [ModelState.Failed] restarts the
     * download (the screen's "Retry" button uses this).
     */
    fun download() {
        // Snapshot the current state; if we're already in a
        // terminal/in-flight state, do nothing.
        when (_state.value) {
            is ModelState.Ready -> return
            is ModelState.Downloading -> return
            else -> {
                // NotStarted or Failed — proceed.
            }
        }
        workScope.launch { runDownload() }
    }

    private suspend fun runDownload() {
        val target = modelFile()
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        val request = Request.Builder().url(DEFAULT_MODEL_URL).build()
        try {
            _state.value = ModelState.Downloading(0f)
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code} downloading model")
                }
                val body = resp.body ?: throw IOException("Empty body downloading model")
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                var read = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            read += n
                            val progress = if (total > 0) {
                                (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            if (progress >= 0f) {
                                _state.value = ModelState.Downloading(progress)
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Could not move downloaded model into place")
            }
            _state.value = ModelState.Ready(target.absolutePath, target.length())
        } catch (e: Exception) {
            tmp.delete()
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Legacy entry point used by [com.baton.app.ai.extraction.Extractor].
     * Reads the URL + SHA-256 from `assets/` at build time, streams
     * to a `.part` file, verifies, and renames. Preserved verbatim
     * for the v1.4.x path; the v1.4.2 (F-10) stateful API in
     * [ensureModel] / [download] is the new user-facing surface.
     */
    fun downloadModel(): Flow<DownloadProgress> = flow {
        val target = modelFile()
        if (target.exists() && verify(target)) {
            emit(DownloadProgress.Done(target))
            return@flow
        }
        val url = context.assets.open("model_url.txt").bufferedReader().use { it.readText().trim() }
        val expectedSha = context.assets.open("model_sha256.txt").bufferedReader().use { it.readText().trim() }

        val tmp = File(target.parentFile, target.name + ".part")
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} downloading model")
            val body = resp.body ?: throw IOException("Empty body downloading model")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            var read = 0L
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        read += n
                        val percent = if (total > 0) ((read * 100) / total).toInt() else -1
                        emit(DownloadProgress.InProgress(percent, read, total))
                    }
                }
            }
        }
        if (!verify(tmp, expectedSha)) {
            tmp.delete()
            throw LlamaError.LoadFailed("Model SHA-256 mismatch; partial download deleted")
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw LlamaError.LoadFailed("Could not move downloaded model into place")
        }
        emit(DownloadProgress.Done(target))
    }.flowOn(Dispatchers.IO)

    open fun modelFile(): File = File(context.filesDir, "models/qwen3-1.7b-q4_k_m.gguf")

    private fun verify(file: File, expectedSha: String? = null): Boolean {
        if (!file.exists()) return false
        val sha = expectedSha ?: runCatching {
            context.assets.open("model_sha256.txt").bufferedReader().use { it.readText().trim() }
        }.getOrNull() ?: return true  // no manifest, accept any file
        val actual = sha256(file)
        return actual.equals(sha, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /**
         * v1.4.2 (F-10): placeholder URL for the first-run download.
         * Points at the Qwen2.5-1.5B-Instruct GGUF on Hugging Face.
         * The real production URL for the project's chosen Qwen3 1.7B
         * model lives in `assets/model_url.txt` and is used by the
         * legacy [downloadModel] flow. This placeholder exists so the
         * first-run UX has a real, reachable URL without depending
         * on a build-time asset swap.
         */
        const val DEFAULT_MODEL_URL: String =
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }
}

/**
 * v1.4.2 (F-10): sealed surface for the on-device model download
 * lifecycle. The first-run UI subscribes to a `StateFlow<ModelState>`
 * and renders the appropriate copy / progress / retry affordance for
 * each variant.
 *
 *  - [NotStarted] — the model file is absent from `filesDir/`. The
 *    screen shows a "Download model" button.
 *  - [Downloading] — a download is in flight. `progress` is `0f..1f`
 *    or `-1f` when the server did not advertise a `Content-Length`.
 *  - [Ready] — the model file is on disk and ready for the
 *    [com.baton.app.ai.llama.LlamaBridge] to load. `sizeBytes` is the
 *    on-disk size (from `File.length()`).
 *  - [Failed] — the download or the move-into-place step threw. The
 *    reason is human-readable (a substring of the exception's
 *    message). The screen shows a "Retry" button that re-invokes
 *    [ModelManager.download].
 */
sealed class ModelState {
    data object NotStarted : ModelState()
    data class Downloading(val progress: Float) : ModelState()
    data class Ready(val path: String, val sizeBytes: Long) : ModelState()
    data class Failed(val reason: String) : ModelState()
}

sealed class DownloadProgress {
    data class InProgress(val percent: Int, val readBytes: Long, val totalBytes: Long) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
}
