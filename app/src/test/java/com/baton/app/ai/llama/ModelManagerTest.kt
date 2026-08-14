package com.baton.app.ai.llama

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
 *
 * v1.4.3 (F-10): the class is now annotated with
 * `@RunWith(RobolectricTestRunner::class)` so the new
 * model-picker tests can resolve a real [Context] from
 * `ApplicationProvider` and exercise the
 * `SharedPreferences`-backed persistence. The pre-existing
 * download-flow tests still use `mockk<Context>(relaxed = true)`
 * and pass it directly to the [ModelManager] constructor, so
 * they don't touch Robolectric's Application context — the
 * runner change is transparent to them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModelManagerTest {

    /**
     * v1.4.3 (F-10): clear the `model_prefs` SharedPreferences
     * before each test so the persisted model id from a previous
     * test doesn't leak into the next one. The pre-existing
     * download-flow tests don't read this preference, so the
     * `@Before` is a safe no-op for them.
     */
    @Before
    fun clearModelPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun cleanupModelPrefs() {
        // Belt-and-braces: also clear after the test so a later
        // test class that doesn't @Before-clear doesn't see
        // stale state.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

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
    // v1.4.3 (F-10): model-picker tests.
    //
    // These tests exercise [ModelManager.availableModels],
    // [ModelManager.currentModel], and [ModelManager.selectModel].
    // They use a real [Context] from `ApplicationProvider` (via
    // Robolectric) so the `SharedPreferences`-backed persistence
    // path is real. The pre-existing download-flow tests above
    // keep using `mockk<Context>(relaxed = true)` and are
    // unaffected — the [ModelManager]'s `prefs` field is
    // `by lazy` and the relaxed mock's `getSharedPreferences`
    // returns a relaxed mock that yields `null` for
    // `getString`, so the default model is selected without
    // throwing.
    // ---------------------------------------------------------------

    /**
     * v1.4.3 (F-10): [ModelManager.availableModels] must be
     * non-empty and must contain the default model (the first
     * entry, Qwen 3 1.7B Q4_K_M). The current model on a fresh
     * install must equal the default.
     */
    @Test
    fun `availableModels is non-empty and contains the current default`() {
        val models = ModelManager.availableModels
        assertTrue(
            "availableModels must not be empty; was $models",
            models.isNotEmpty(),
        )
        val default = models.first()
        assertEquals(
            "first model must be the Qwen 3 1.7B default",
            "qwen3-1.7b-q4_k_m",
            default.id,
        )
        // Every model must have a non-blank id, a non-blank
        // displayName, a non-blank description, a URL that looks
        // like HTTPS, and a positive sizeBytes.
        models.forEach { m ->
            assertTrue("id must be non-blank for $m", m.id.isNotBlank())
            assertTrue("displayName must be non-blank for $m", m.displayName.isNotBlank())
            assertTrue("description must be non-blank for $m", m.description.isNotBlank())
            assertTrue(
                "url must be HTTPS for $m; was ${m.url}",
                m.url.startsWith("https://"),
            )
            assertTrue("sizeBytes must be positive for $m", m.sizeBytes > 0)
        }
    }

    /**
     * v1.4.3 (F-10): [ModelManager.selectModel] must update
     * [ModelManager.currentModel] and delete the on-disk file
     * of the previously-selected model so the next
     * `ensureModel()` will re-download.
     */
    @Test
    fun `selectModel updates currentModel and clears the on-disk file`() = runBlocking<Unit> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Use a dedicated filesDir for this test so we don't
        // collide with other tests. Robolectric's Application
        // provides a real filesDir; we write into a sub-dir
        // under it.
        val tempDir = newTempFilesDir()
        // Point the manager at our temp dir by overriding
        // filesDir on a thin wrapper Context. Robolectric's
        // Application doesn't let us redirect filesDir directly,
        // so we work in the manager's own filesDir/models/
        // subdirectory instead and clean up after.
        val mm = ModelManager(
            context = ctx,
            httpClient = OkHttpClient(),
        )

        // Touch the lazy initialisation so currentModel is
        // resolved before we read it below.
        val initial = mm.currentModel.value
        assertEquals(
            "default model must be Qwen 3 1.7B on a fresh install",
            "qwen3-1.7b-q4_k_m",
            initial.id,
        )

        // Pre-populate the on-disk file for the initial model
        // (as if it had been previously downloaded). This is
        // what `selectModel` is expected to delete.
        val modelsDir = File(ctx.filesDir, "models")
        modelsDir.mkdirs()
        val oldFile = File(modelsDir, "${initial.id}.gguf")
        oldFile.writeBytes("fake-payload".toByteArray())
        assertTrue("old model file must exist before selectModel", oldFile.exists())

        // Pick a different model from the catalogue.
        val newModel = ModelManager.availableModels
            .first { it.id != initial.id }

        mm.selectModel(newModel)

        assertEquals(
            "currentModel must be the newly-selected model",
            newModel,
            mm.currentModel.value,
        )
        assertFalse(
            "old model file must be deleted after selectModel",
            oldFile.exists(),
        )
        // The new model's file must NOT exist (it hasn't been
        // downloaded yet).
        val newFile = File(modelsDir, "${newModel.id}.gguf")
        assertFalse(
            "new model file must not exist yet (only re-downloaded on next ensureModel)",
            newFile.exists(),
        )

        tempDir.deleteRecursively()
        // Clean up any files we wrote into the real filesDir.
        oldFile.delete()
        newFile.delete()
    }

    /**
     * v1.4.3 (F-10): [ModelManager.selectModel] must persist
     * the choice to `SharedPreferences` so a freshly-
     * instantiated [ModelManager] reads back the same model.
     * This is the "survives process restarts" contract.
     */
    @Test
    fun `selectModel persists the choice across ModelManager re-instantiation`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val first = ModelManager(
            context = ctx,
            httpClient = OkHttpClient(),
        )
        // Touch the lazy init so the default is resolved and
        // the prefs are read.
        assertEquals(
            "first manager must start at the default",
            "qwen3-1.7b-q4_k_m",
            first.currentModel.value.id,
        )

        val pick = ModelManager.availableModels
            .first { it.id != "qwen3-1.7b-q4_k_m" }
        first.selectModel(pick)
        assertEquals(
            "first manager must reflect the new selection",
            pick.id,
            first.currentModel.value.id,
        )

        // A second manager against the same Context must read
        // the persisted choice back from SharedPreferences.
        val second = ModelManager(
            context = ctx,
            httpClient = OkHttpClient(),
        )
        assertEquals(
            "second manager must pick up the persisted selection",
            pick.id,
            second.currentModel.value.id,
        )
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

