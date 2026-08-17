package com.baton.app.ai.llama

import android.content.Context
import android.content.SharedPreferences
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
 * Downloads the on-device LLM model (GGUF) on first run and manages
 * which model variant is currently selected.
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
 * v1.4.3 (F-10): extended with a small model picker. [availableModels]
 * lists 3-4 GGUF options; [currentModel] is a [StateFlow] of the
 * user's selection; [selectModel] updates the choice, persists it to
 * `SharedPreferences` ("model_prefs"), and deletes the on-disk file
 * of the previously-selected model so the next [ensureModel] will
 * re-download the new one. The stateful download API now uses
 * `currentModel.value.url` and `currentModel.value.id` instead of the
 * hard-coded placeholder constants from v1.4.2.
 *
 * The hard-coded placeholder URL that previously lived as
 * [DEFAULT_MODEL_URL] is gone — the first model in [availableModels]
 * (Qwen 3 1.7B Q4_K_M) is the new default. Its URL points at a
 * Qwen release on Hugging Face as a stand-in for the production URL.
 * The real production URL still lives in `assets/model_url.txt` and
 * is used by the legacy `downloadModel()` flow (unchanged).
 *
 * **Model file location:** `filesDir/models/${currentModel.id}.gguf`
 * (gitignored). The file name follows the project's existing
 * convention used by the legacy `downloadModel()` flow and the
 * [Extractor].
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
     * Tier 0.5 (cleanup + ship-the-built): the download
     * progress, exposed as a `StateFlow<Float>` in the
     * 0.0-1.0 range. The Settings → Models row uses this
     * flow to drive a real [androidx.compose.material3.LinearProgressIndicator]
     * (instead of the v1.5.7 "Downloading... 47%" text
     * only). The flow is hot and mirrors
     * [state] -- when the model is [ModelState.Downloading]
     * the flow is the byte-level progress; when the model
     * is [ModelState.Ready] the flow is `1.0f`; otherwise
     * (NotStarted / Failed) the flow is `0.0f`.
     *
     * **Why a separate flow:** the v1.5.7
     * [ModelState.Downloading.progress] field is `Float` in
     * the `0.0-1.0` range (or `-1f` when the server did not
     * advertise a `Content-Length`). The v1.6.0 Settings
     * UI uses a `LinearProgressIndicator(progress = { ... })`
     * which takes a `Float`; the StateFlow<Float> shape is
     * the cleanest binding. The flow is kept in sync with
     * [state] by the [updateProgressFromState] helper below.
     */
    private val _progress = MutableStateFlow(0f)

    /**
     * Public read-only progress flow. The value is the
     * 0.0-1.0 fraction of the download (or `1.0f` if
     * the model is already [ModelState.Ready]).
     */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /**
     * Lazily-initialised [SharedPreferences] handle for the model
     * picker. The lookup is wrapped in [runCatching] so a test
     * that injects a mock [Context] (e.g. the existing
     * `ModelManagerTest` cases that use `mockk<Context>(relaxed = true)`)
     * can still construct the manager without throwing — the
     * preferences handle just resolves to `null` and we fall back
     * to the in-memory default. Tests that exercise persistence
     * use Robolectric to get a real [Context] (see
     * `ModelManagerTest`).
     */
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    private val _currentModel: MutableStateFlow<ModelOption> by lazy {
        val savedId = prefs?.getString(KEY_MODEL_ID, null)
        val initial = availableModels.firstOrNull { it.id == savedId }
            ?: availableModels.first()
        MutableStateFlow(initial)
    }

    /**
     * The model currently selected by the user. Persists across
     * process restarts via `SharedPreferences`. The initial value
     * is the first entry in [availableModels] (Qwen 3 1.7B Q4_K_M)
     * unless a previous selection was saved.
     */
    val currentModel: StateFlow<ModelOption> by lazy { _currentModel.asStateFlow() }

    /**
     * First-run entry point used by [com.baton.app.ui.llama.ModelDownloadScreen].
     *
     * Returns the same [state] flow the screen already collects.
     * Calling this method scans [modelFile] for the **current**
     * model; if the file exists and is non-empty, the state is
     * promoted to [ModelState.Ready] (idempotent). Otherwise the
     * state stays at [ModelState.NotStarted] and the screen renders
     * the "Download model" button which calls [download].
     */
    @Synchronized
    fun ensureModel(): StateFlow<ModelState> {
        if (_state.value !is ModelState.NotStarted) return _state
        val target = modelFile()
        if (target.exists() && target.length() > 0) {
            _state.value = ModelState.Ready(target.absolutePath, target.length())
            // Tier 0.5: an on-disk model is fully ready;
            // the progress bar is at 100%.
            _progress.value = 1f
        }
        return _state
    }

    /**
     * Kicks off a background download for the **current** model
     * ([currentModel.value]). Fire-and-forget — the call returns
     * immediately and the state is updated via [state] as the
     * download progresses.
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

    /**
     * Switch to a different model. Updates [currentModel], persists
     * the choice to `SharedPreferences`, and deletes the on-disk
     * file of the previously-selected model so the next
     * [ensureModel] call (triggered when the screen re-attaches)
     * will re-download the new one.
     *
     * The download state is reset to [ModelState.NotStarted] so the
     * screen shows the "Download model" button for the new model.
     * If a download was in flight for the old model, it will still
     * write to the old file (which we just deleted) and the
     * [runDownload] failure path will catch the error and surface
     * it as [ModelState.Failed] — the user can then retry with the
     * new model selected.
     *
     * No-op if [option] is already the current model.
     */
    @Synchronized
    fun selectModel(option: ModelOption) {
        if (option.id == _currentModel.value.id) return
        val previous = _currentModel.value
        prefs?.edit()?.putString(KEY_MODEL_ID, option.id)?.apply()
        val oldFile = File(context.filesDir, "models/${previous.id}.gguf")
        if (oldFile.exists()) {
            oldFile.delete()
        }
        val oldPart = File(context.filesDir, "models/${previous.id}.gguf.part")
        if (oldPart.exists()) {
            oldPart.delete()
        }
        _currentModel.value = option
        _state.value = ModelState.NotStarted
        // Tier 0.5: a model switch resets the progress
        // bar to 0. The new model has not been fetched.
        _progress.value = 0f
    }

    private suspend fun runDownload() {
        val current = _currentModel.value
        val target = File(context.filesDir, "models/${current.id}.gguf")
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        val request = Request.Builder().url(current.url).build()
        try {
            _state.value = ModelState.Downloading(0f)
            // Tier 0.5: reset the separate progress flow.
            _progress.value = 0f
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
                                // Tier 0.5: push the same value
                                // to the dedicated progress flow
                                // so the Settings UI's
                                // `LinearProgressIndicator` can
                                // bind to a plain `Float` without
                                // re-parsing the sealed state.
                                _progress.value = progress
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
            // Tier 0.5: a "ready" model is at 100% by
            // definition; the LinearProgressIndicator
            // then renders full.
            _progress.value = 1f
        } catch (e: Exception) {
            tmp.delete()
            _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            // Tier 0.5: a failure means the progress
            // bar should reset. The Settings UI hides
            // the indicator entirely on Failed, but
            // keeping the value at `0f` avoids a
            // stale-value flash if the user re-issues
            // the download.
            _progress.value = 0f
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

    /**
     * Resolves the on-disk path for the **current** model's GGUF
     * file. The path is `filesDir/models/${currentModel.id}.gguf`.
     * Used by both the new stateful download API and the legacy
     * `downloadModel()` flow consumed by [Extractor].
     */
    open fun modelFile(): File = File(context.filesDir, "models/${_currentModel.value.id}.gguf")

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
         * v1.4.3 (F-10): the catalogue of models the user can pick
         * from. The first entry is the default (used when no
         * preference has been persisted yet). All four are
         * GGUF Q4_K_M quantisations sized to fit comfortably on
         * modern mid-range devices; the URLs are placeholder
         * Hugging Face resolve links that point at the canonical
         * community quantisations. The real production URLs (and
         * their SHA-256 manifests, used by the legacy
         * `downloadModel()` flow) live in `assets/`.
         */
        val availableModels: List<ModelOption> = listOf(
            // v1.5.4: the upstream Qwen/Qwen3-1.7B-GGUF repo doesn't
            // ship a `qwen3-1.7b-q4_k_m.gguf` file at the canonical
            // path (HTTP 404). The `enacimie/Qwen3-1.7B-Q4_K_M-GGUF`
            // community mirror serves the same quantisation with
            // matching SHA. The catalog uses the mirror so a fresh
            // install + first-run download actually completes.
            ModelOption(
                id = "qwen3-1.7b-q4_k_m",
                displayName = "Qwen 3 1.7B (Q4_K_M)",
                description = "~1.1 GB, fast on most devices, good for short instructions",
                url = "https://huggingface.co/enacimie/Qwen3-1.7B-Q4_K_M-GGUF/resolve/main/qwen3-1.7b-q4_k_m.gguf",
                sizeBytes = 1_100_000_000L,
            ),
            ModelOption(
                id = "llama-3.2-3b-instruct-q4_k_m",
                displayName = "Llama 3.2 3B Instruct (Q4_K_M)",
                description = "~2.0 GB, stronger reasoning, slower on low-RAM devices",
                url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                sizeBytes = 2_000_000_000L,
            ),
            ModelOption(
                id = "gemma-2-2b-it-q4_k_m",
                displayName = "Gemma 2 2B IT (Q4_K_M)",
                description = "~1.6 GB, balanced quality, friendly safety profile",
                url = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                sizeBytes = 1_600_000_000L,
            ),
            ModelOption(
                id = "phi-3.5-mini-3.8b-q4_k_m",
                displayName = "Phi-3.5 mini 3.8B (Q4_K_M)",
                description = "~2.3 GB, strong on structured extraction, largest of the four",
                url = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                sizeBytes = 2_300_000_000L,
            ),
        )

        private const val PREFS_NAME = "model_prefs"
        private const val KEY_MODEL_ID = "selected_model_id"
    }
}

/**
 * v1.4.3 (F-10): a single pickable model option. The `id` is the
 * stable identifier persisted to `SharedPreferences` and used as
 * the on-disk file basename (`filesDir/models/${id}.gguf`). The
 * `url` is the download endpoint (a Hugging Face resolve link in
 * the v1.4.3 placeholder catalogue). The `sizeBytes` is the
 * approximate download size; the screen displays it as a
 * human-readable "~N GB" hint.
 */
data class ModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
)

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
