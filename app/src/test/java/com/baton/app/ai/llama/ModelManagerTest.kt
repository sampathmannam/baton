package com.baton.app.ai.llama

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * v1.4.2 (F-10): the new stateful [ModelManager.ensureModel] /
 * [ModelManager.download] entry points must:
 *
 *  1. Start in [ModelState.NotStarted] on a fresh `Context`.
 *  2. Transition through [ModelState.Downloading] to
 *     [ModelState.Ready] when the download succeeds.
 *  3. Transition to [ModelState.Failed] when the server returns a
 *     non-2xx status (404 in this test).
 *
 * The tests use a fake `okhttp3.Interceptor` that synthesises the
 * response in-memory — no network, no `okhttp-mockwebserver`
 * dependency. The [ModelManager] is constructed directly with the
 * mocked `Context` and the fake client, bypassing Hilt.
 *
 * The download is fire-and-forget on `Dispatchers.IO`. We use
 * [runBlocking] for the test driver because the IO work is on a
 * real thread pool (the [ModelManager]'s work scope is not
 * test-injectable without a Hilt binding change, which is out of
 * scope for this task). With a fake interceptor the IO work
 * finishes within a few milliseconds; the test is hermetic.
 */
class ModelManagerTest {

    /**
     * Spec test (1): a fresh [ModelManager] whose target `filesDir`
     * is an empty temp directory must report
     * [ModelState.NotStarted] before any user action.
     */
    @Test
    fun `initial state is NotStarted`() = runBlocking<Unit> {
        val tempDir = newTempFilesDir()
        val mm = newManager(tempDir, successInterceptor(content = ByteArray(0)))

        assertEquals(
            "fresh manager must report NotStarted",
            ModelState.NotStarted,
            mm.state.value,
        )

        // ensureModel() is idempotent and only promotes NotStarted
        // to Ready when the file is present. With an empty filesDir
        // the state must stay at NotStarted.
        mm.ensureModel()
        assertEquals(
            "ensureModel() on an empty filesDir must stay at NotStarted",
            ModelState.NotStarted,
            mm.state.value,
        )

        tempDir.deleteRecursively()
    }

    /**
     * Spec test (2): after [ModelManager.download] is called, the
     * state must transition through [ModelState.Downloading] (at
     * least one emission) and terminate at [ModelState.Ready].
     *
     * `StateFlow` is conflated — it only carries the latest value,
     * so a fast download can collapse every `Downloading` emission
     * into the final `Ready` before any collector can see it. The
     * end-to-end contract this test pins is therefore:
     *
     *  (a) The final state is `Ready` (the terminal value).
     *  (b) The on-disk file matches the payload we asked the fake
     *      interceptor to return (i.e. the download actually wrote
     *      the bytes, the implementation did not skip straight to
     *      `Ready`).
     *
     * The "transitions through `Downloading`" contract is verified
     * separately by [mid-download state is observed as Downloading],
     * which polls [ModelManager.state] while the download is in
     * flight. (Polling is the only race-free way to catch a
     * conflated intermediate value without depending on a slow
     * interceptor or a replay-buffer `SharedFlow`.)
     */
    @Test
    fun `download transitions through Downloading to Ready`() = runBlocking<Unit> {
        val tempDir = newTempFilesDir()
        val payload = "fake-gguf-payload".toByteArray()
        val mm = newManager(tempDir, successInterceptor(content = payload))

        // Background collector (on a real dispatcher) records
        // emissions. `runBlocking` is on BlockingEventLoop, so a
        // plain `launch` inside this block would not run until
        // the test thread yields — which it doesn't until the
        // download has already finished. We use Dispatchers.Default
        // so the collector is on a real thread pool.
        val emissions = java.util.Collections.synchronizedList(mutableListOf<ModelState>())
        val collectorJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            mm.state.collect { emissions.add(it) }
        }

        // Wait for the collector to actually subscribe before
        // triggering the download, otherwise the StateFlow's
        // conflation will collapse Downloading into Ready before
        // anyone is listening. We poll for the NotStarted seed
        // value (which is replayed on subscription).
        val waitDeadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < waitDeadline &&
            !emissions.contains(ModelState.NotStarted)
        ) {
            Thread.sleep(10)
        }
        assertTrue(
            "collector must have seen NotStarted before download starts; saw $emissions",
            emissions.contains(ModelState.NotStarted),
        )

        mm.download()

        // Wait for the terminal state via the StateFlow's first { }.
        val terminal = mm.state.first { it is ModelState.Ready || it is ModelState.Failed }

        assertTrue(
            "expected Ready, got $terminal",
            terminal is ModelState.Ready,
        )
        val ready = terminal as ModelState.Ready
        assertEquals(payload.size.toLong(), ready.sizeBytes)
        // Path is inside the tempDir we created.
        assertTrue(
            "Ready.path must live under the temp filesDir",
            ready.path.startsWith(tempDir.absolutePath),
        )
        // File on disk matches what we wrote.
        val onDisk = File(ready.path)
        assertTrue("model file must exist on disk", onDisk.exists())
        assertEquals(payload.size.toLong(), onDisk.length())

        collectorJob.cancel()
        tempDir.deleteRecursively()
    }

    /**
     * Companion test: while a download is in flight, polling the
     * state must eventually catch a [ModelState.Downloading]
     * emission. The download runs on `Dispatchers.IO`; the test
     * polls on the calling thread. The body is large enough that
     * the IO write loop takes multiple chunk reads, so the poll
     * has a window to observe `Downloading` before the state
     * collapses to `Ready`.
     */
    @Test
    fun `mid-download state is observed as Downloading`() = runBlocking<Unit> {
        val tempDir = newTempFilesDir()
        // 256 KB so the read loop iterates enough times that a
        // 1 ms-per-chunk rate of state changes is observable.
        val payload = ByteArray(256 * 1024) { (it and 0xFF).toByte() }
        val mm = newManager(tempDir, successInterceptor(content = payload))

        val collectorJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            mm.state.collect { /* swallow */ }
        }
        // Wait for the collector to subscribe.
        val waitDeadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < waitDeadline &&
            mm.state.value != ModelState.NotStarted
        ) {
            Thread.sleep(10)
        }

        mm.download()

        // Poll the state value until it becomes Ready (or we time
        // out). Assert that at least one of the polled snapshots
        // is a Downloading state.
        val deadline = System.currentTimeMillis() + 5_000
        var sawDownloading = false
        while (System.currentTimeMillis() < deadline) {
            val s = mm.state.value
            if (s is ModelState.Ready) break
            if (s is ModelState.Downloading) {
                sawDownloading = true
                assertTrue(
                    "Downloading.progress must be in [0, 1] (or -1 for unknown); was ${s.progress}",
                    s.progress in -1f..1f,
                )
            }
            Thread.sleep(1)
        }
        assertTrue(
            "must have polled a Downloading snapshot; final state was ${mm.state.value}",
            sawDownloading,
        )
        assertTrue(
            "download must have finished Ready; final state was ${mm.state.value}",
            mm.state.value is ModelState.Ready,
        )

        collectorJob.cancel()
        tempDir.deleteRecursively()
    }

    /**
     * Spec test (3): when the server returns a 404, [ModelManager.download]
     * must transition the state to [ModelState.Failed] with a
     * human-readable reason.
     */
    @Test
    fun `download transitions to Failed on 404`() = runBlocking<Unit> {
        val tempDir = newTempFilesDir()
        val mm = newManager(tempDir, httpFailureInterceptor(code = 404))

        mm.state.first { it == ModelState.NotStarted }

        mm.download()

        val terminal = mm.state.first { it is ModelState.Failed }

        val failed = terminal as ModelState.Failed
        assertNotNull("Failed.reason must be present", failed.reason)
        assertTrue(
            "Failed.reason should mention the 404; was '${failed.reason}'",
            failed.reason.contains("404"),
        )
        // No partial file should be left behind on failure.
        val leftover = File(tempDir, "models/qwen3-1.7b-q4_k_m.gguf")
        assertTrue(
            "no model file should exist on disk after a 404; saw ${leftover.absolutePath}",
            !leftover.exists(),
        )
        val part = File(tempDir, "models/qwen3-1.7b-q4_k_m.gguf.part")
        assertTrue("no .part file should remain", !part.exists())

        tempDir.deleteRecursively()
    }

    /**
     * Companion invariant: [ModelState.Downloading] with a
     * negative progress is the "server did not advertise
     * Content-Length" path. The state itself allows any `Float`
     * (so the implementation can use `-1f` as the unknown
     * sentinel) — this test pins the contract.
     */
    @Test
    fun `ModelState Downloading holds a progress Float`() {
        val d = ModelState.Downloading(0.42f)
        assertEquals(0.42f, d.progress, 0.0001f)
        val unknown = ModelState.Downloading(-1f)
        assertTrue(unknown.progress < 0f)
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private fun newTempFilesDir(): File {
        val dir = Files.createTempDirectory("baton-model-test").toFile()
        // ModelManager writes to filesDir/models/; pre-create the
        // models/ subdirectory to mirror a real install.
        File(dir, "models").mkdirs()
        return dir
    }

    private fun newManager(filesDir: File, interceptor: Interceptor): ModelManager {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns filesDir
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        return ModelManager(
            context = ctx,
            httpClient = client,
        )
    }

    /** 200 OK with a small in-memory body. */
    private fun successInterceptor(content: ByteArray): Interceptor = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                content.toResponseBody(
                    contentType = "application/octet-stream".toMediaType(),
                ),
            )
            .build()
    }

    /** Non-2xx response with an empty body. */
    private fun httpFailureInterceptor(code: Int): Interceptor = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Not Found")
            .body(ByteArray(0).toResponseBody(null))
            .build()
    }
}

