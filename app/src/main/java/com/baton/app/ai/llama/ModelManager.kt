package com.baton.app.ai.llama

import android.content.Context
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
 * Downloads the Qwen 3 1.7B Q4_K_M GGUF model on first run. The
 * download URL and SHA-256 are read from `assets/` at build time.
 *
 * The model lives in `filesDir/models/`, NOT in the repo
 * (gitignored). M3 will revisit if we want to ship the model
 * pre-installed via Play asset delivery.
 */
@Singleton
open class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {

    /**
     * Emits progress updates (0..100). The model file is downloaded
     * to a `.part` file first, verified against the expected SHA-256,
     * then atomically renamed. On verification failure the partial
     * download is deleted and the error is thrown.
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
}

sealed class DownloadProgress {
    data class InProgress(val percent: Int, val readBytes: Long, val totalBytes: Long) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
}
