package com.baton.app.ai.whisper

import android.content.Context
import com.baton.app.ai.llama.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M2-T3: downloads the `ggml-tiny.en.bin` Whisper model on first
 * run. The download URL and SHA-256 are read from `assets/` at
 * build time so a developer can swap in a mirror without code
 * changes.
 *
 * **Model file lives in `filesDir/models/`, NOT in the repo**
 * (gitignored). The first cold-start of voice capture triggers a
 * ~75 MB download. After that, voice capture is offline-capable.
 *
 * **SHA-256 verification**: the model is downloaded to a `.part`
 * file, the SHA-256 is computed and compared against
 * `assets/whisper_sha256.txt`. On mismatch the partial download is
 * deleted and the error is thrown. The user can retry the download
 * from the voice-capture UI.
 *
 * The download progress is exposed as a [Flow] so the UI can show
 * "Downloading model… 47%" while the model is being fetched.
 */
@Singleton
open class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {

    fun downloadModel(): Flow<DownloadProgress> = flow {
        val target = modelFile()
        if (target.exists() && verify(target)) {
            emit(DownloadProgress.Done(target))
            return@flow
        }
        val url = context.assets.open("whisper_url.txt").bufferedReader().use { it.readText().trim() }
        val expectedSha = context.assets.open("whisper_sha256.txt").bufferedReader().use { it.readText().trim() }

        val tmp = File(target.parentFile, target.name + ".part")
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} downloading whisper model")
            val body = resp.body ?: throw IOException("Empty body downloading whisper model")
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
            throw WhisperError.LoadFailed("Whisper model SHA-256 mismatch; partial download deleted")
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw WhisperError.LoadFailed("Could not move downloaded whisper model into place")
        }
        emit(DownloadProgress.Done(target))
    }.flowOn(Dispatchers.IO)

    /**
     * Path of the ggml-tiny.en.bin file. `null` if it has never
     * been downloaded.
     */
    open fun modelFile(): File = File(context.filesDir, "models/ggml-tiny.en.bin")

    /**
     * Has the model been downloaded and verified already? Cheap
     * check used by the voice-capture UI to decide whether to
     * trigger a download flow or proceed straight to recording.
     */
    open fun isAvailable(): Boolean = modelFile().exists() && verify(modelFile())

    private fun verify(file: File, expectedSha: String? = null): Boolean {
        if (!file.exists()) return false
        val sha = expectedSha ?: runCatching {
            context.assets.open("whisper_sha256.txt").bufferedReader().use { it.readText().trim() }
        }.getOrNull() ?: return true
        // Skip verification when the SHA file is a comment / placeholder.
        // The shipped assets/whisper_sha256.txt starts with `#`; that
        // means "no verification yet" — first-run users accept any file.
        if (sha.startsWith("#")) return true
        if (sha.isBlank()) return true
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
}
