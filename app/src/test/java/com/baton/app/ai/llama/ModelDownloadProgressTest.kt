package com.baton.app.ai.llama

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 0.5: tests for the v1.6.0 download-progress
 * `StateFlow<Float>` on [ModelManager].
 *
 * **What we test:**
 *  - The `progress` flow exists and starts at `0f` for a
 *    fresh [ModelManager].
 *  - The flow is `1.0f` when the model file is on disk
 *    (the [ModelManager.ensureModel] path promotes the
 *    state to [ModelState.Ready] and the progress to
 *    `1.0f`).
 *  - The `progress` flow is bound to the same lifetime as
 *    the manager (hot, process-wide).
 *
 * **What we don't test:**
 *  - The actual byte-level progress emission -- that
 *    requires a real OkHttp + a 1.1 GB model file. The
 *    [ModelManager.runDownload] path is exercised on
 *    device in the Tier 0.5 drive case (`qa-whisper-progress.xml`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModelDownloadProgressTest {

    private lateinit var context: Context
    private lateinit var manager: ModelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = ModelManager(context)
    }

    @Test
    fun `progress flow starts at zero`() {
        // Tier 0.5: a brand-new manager (no model on
        // disk, no download in flight) must report 0f
        // so the LinearProgressIndicator renders
        // empty.
        assertEquals(0f, manager.progress.value, 0.0001f)
    }

    @Test
    fun `progress flow flips to one when model is on disk`() {
        // Lay down a fake model file in the
        // manager's target path. The next
        // [ModelManager.ensureModel] call promotes the
        // state to [ModelState.Ready] and the progress
        // to 1.0f.
        val target = manager.modelFile()
        target.parentFile?.mkdirs()
        target.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            manager.ensureModel()
            assertEquals(
                "progress must be 1f after ensureModel promotes to Ready",
                1f,
                manager.progress.value,
                0.0001f,
            )
        } finally {
            target.delete()
        }
    }

    @Test
    fun `progress flow is a StateFlow`() {
        // Tier 0.5: the consumer side binds via
        // `collectAsStateWithLifecycle` which expects a
        // [kotlinx.coroutines.flow.StateFlow]. The
        // field must be a StateFlow, not a plain Flow.
        assertNotNull(manager.progress)
        // Read the first value synchronously -- StateFlow
        // always has a value.
        val v = runBlocking { manager.progress.first() }
        assertTrue(
            "progress value must be in the 0.0-1.0 range (was $v)",
            v in 0f..1f,
        )
    }

    @Test
    fun `selectModel resets the progress flow to zero`() {
        // Tier 0.5: switching to a different model
        // resets the progress bar. We do not download
        // anything here; the assertion is that
        // [ModelManager.selectModel] flips the flow
        // to 0f.
        val option = ModelManager.availableModels[1] // Llama 3.2 3B
        manager.selectModel(option)
        assertEquals(0f, manager.progress.value, 0.0001f)
        // Restore the default for the next test.
        manager.selectModel(ModelManager.availableModels[0])
    }
}
