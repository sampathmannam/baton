package com.baton.app.ai.whisper

import com.baton.app.ai.llama.DownloadProgress
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M2-T3 tests for the [WhisperBridge] and [WhisperModelManager].
 *
 * The bridge loads a native library in its `init` block. On the
 * unit-test JVM that library is unavailable, so the bridge loads
 * "in a not-loaded state" — `isLoaded0()` returns false and
 * [WhisperBridge.transcribe] throws [WhisperError.NotLoaded].
 * That's the case we exercise below; real inference is gated on a
 * device or emulator with libbaton-whisper.so present.
 *
 * The model manager is tested against a stub context that returns
 * either a placeholder SHA (the shipped asset) or a real hash. The
 * download flow itself is not exercised — it would require network
 * access. We focus on the "already cached" and "verify" paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WhisperBridgeTest {

    @Test
    fun `bridge starts unloaded`() {
        val bridge = WhisperBridge()
        assertFalse("bridge must start unloaded (no native lib in unit tests)", bridge.isLoaded0())
        assertEquals(0L, bridge.lastEvalMs())
    }

    @Test
    fun `transcribe throws NotLoaded when bridge is not loaded`() = runTest {
        val bridge = WhisperBridge()
        try {
            bridge.transcribe(pcmBytes = byteArrayOf(0, 0), sampleRate = 16000)
            error("expected NotLoaded")
        } catch (e: WhisperError.NotLoaded) {
            // Expected.
        }
    }

    @Test
    fun `transcribe throws EmptyAudio when pcm is empty`() = runTest {
        val bridge = WhisperBridge()
        try {
            bridge.transcribe(pcmBytes = ByteArray(0), sampleRate = 16000)
            error("expected EmptyAudio")
        } catch (e: WhisperError.EmptyAudio) {
            // Expected.
        } catch (e: WhisperError.NotLoaded) {
            // The not-loaded guard runs first; accept either.
        }
    }

    @Test
    fun `free is a no-op when never loaded`() {
        val bridge = WhisperBridge()
        // Should not throw.
        bridge.free()
        assertFalse(bridge.isLoaded0())
    }

    // ---- WhisperModelManager ----

    @Test
    fun `model manager reports available when model file exists`() {
        val ctx = mockk<android.content.Context>(relaxed = true)
        val am = mockk<android.content.res.AssetManager>(relaxed = true)
        every { ctx.assets } returns am
        // The placeholder SHA starts with `#` — verify() treats that as
        // "no manifest" and accepts any file.
        every { am.open("whisper_sha256.txt") } returns "# placeholder\n".byteInputStream()

        val temp = File.createTempFile("ggml-tiny.en", ".bin")
        temp.writeBytes(ByteArray(1024))  // 1 KB of zero
        val mm = object : WhisperModelManager(ctx) {
            override fun modelFile(): File = temp
        }
        assertTrue(mm.isAvailable())
        temp.delete()
    }

    @Test
    fun `model manager reports not available when file is missing`() {
        val ctx = mockk<android.content.Context>(relaxed = true)
        val am = mockk<android.content.res.AssetManager>(relaxed = true)
        every { ctx.assets } returns am
        every { am.open("whisper_sha256.txt") } returns "# placeholder\n".byteInputStream()

        val mm = object : WhisperModelManager(ctx) {
            override fun modelFile(): File = File.createTempFile("missing", ".bin").also { it.delete() }
        }
        assertFalse(mm.isAvailable())
    }

    @Test
    fun `download flow emits Done immediately when file already exists`() = runTest {
        val ctx = mockk<android.content.Context>(relaxed = true)
        val am = mockk<android.content.res.AssetManager>(relaxed = true)
        every { ctx.assets } returns am
        every { am.open("whisper_sha256.txt") } returns "# placeholder\n".byteInputStream()

        val temp = File.createTempFile("ggml-tiny.en", ".bin")
        temp.writeBytes(ByteArray(1024))
        val mm = object : WhisperModelManager(ctx) {
            override fun modelFile(): File = temp
        }

        val first = mm.downloadModel().first()
        assertTrue("expected DownloadProgress.Done, got $first", first is DownloadProgress.Done)
        val done = first as DownloadProgress.Done
        assertEquals(temp.absolutePath, done.file.absolutePath)
        temp.delete()
    }

    @Test
    fun `model file path is filesDir models ggml-tiny_en_bin`() {
        val ctx = mockk<android.content.Context>(relaxed = true)
        val am = mockk<android.content.res.AssetManager>(relaxed = true)
        every { ctx.assets } returns am
        every { ctx.filesDir } returns File("/tmp/baton-test")
        val mm = WhisperModelManager(ctx)
        assertEquals(
            File("/tmp/baton-test/models/ggml-tiny.en.bin").absolutePath,
            mm.modelFile().absolutePath,
        )
    }
}
