package com.baton.app.ai.extraction

import android.content.Context
import android.content.res.AssetManager
import com.baton.app.ai.llama.LlamaBridge
import com.baton.app.ai.llama.LlamaError
import com.baton.app.ai.llama.ModelManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Unit tests for the M1 [Extractor]. We mock the [Context] with
 * mockk so the asset read returns a deterministic prompt. The
 * [LlamaBridge] is subclassed with canned-JSON-returning fakes; the
 * [ModelManager] is subclassed to point at a temp file so
 * `ensureModelLoaded` is a no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExtractorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val prompt = "System. Input: {raw_text}"

    private fun mockContext(promptText: String): Context {
        val ctx = mockk<Context>(relaxed = true)
        val am = mockk<AssetManager>(relaxed = true)
        every { am.open(any<String>()) } returns ByteArrayInputStream(promptText.toByteArray())
        every { ctx.assets } returns am
        return ctx
    }

    private class FakeLlamaBridge(private val canned: () -> String) : LlamaBridge() {
        var loaded = false
        override suspend fun load(modelPath: File, nCtx: Int, nThreads: Int) {
            loaded = true
        }
        override suspend fun infer(prompt: String, maxTokens: Int): String = canned()
    }

    private class FakeModelManager(private val modelFile: File) : ModelManager(
        context = mockk<Context>(relaxed = true),
    ) {
        override fun modelFile(): File = modelFile
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeExtractor(cannedJson: String): Triple<Extractor, FakeLlamaBridge, File> {
        val tempFile = File.createTempFile("model", ".gguf").also { it.writeBytes(byteArrayOf(0)) }
        val llama = FakeLlamaBridge { cannedJson }
        val mm = FakeModelManager(tempFile)
        val ctx = mockContext(prompt)
        val extractor = Extractor(ctx, mm, llama)
        return Triple(extractor, llama, tempFile)
    }

    @Test
    fun `extracts a well-formed proposal`() = runTest(testDispatcher) {
        val canned = """
            {"person":"SHO Ramu","action":"send FIR 47","due_at":"2026-08-15T17:00:00+05:30",
             "priority":"NORMAL","instruction_text":"Tell SHO Ramu to send FIR 47 by Friday",
             "confidence":0.92}
        """.trimIndent()
        val (extractor, llama, _) = makeExtractor(canned)
        val proposal = extractor.process("Tell SHO Ramu to send FIR 47 by Friday")
        advanceUntilIdle()
        assertNotNull(proposal)
        assertEquals("SHO Ramu", proposal!!.person)
        assertEquals("send FIR 47", proposal.action)
        assertEquals(0.92, proposal.confidence, 0.0001)
        assertTrue(llama.loaded)
    }

    @Test
    fun `low confidence is dropped`() = runTest(testDispatcher) {
        val (extractor, _, _) = makeExtractor(
            """{"person":"x","action":"y","instruction_text":"z","confidence":0.3}"""
        )
        assertNull(extractor.process("y"))
    }

    @Test
    fun `extra prose around the JSON is tolerated`() = runTest(testDispatcher) {
        val (extractor, _, _) = makeExtractor("""
            Sure, here you go:
            {"person":"a","action":"b","instruction_text":"c","confidence":0.8}
            Hope that helps!
        """.trimIndent())
        val proposal = extractor.process("a b c")
        advanceUntilIdle()
        assertNotNull(proposal)
        assertEquals("a", proposal!!.person)
    }

    @Test
    fun `garbage output returns null`() = runTest(testDispatcher) {
        val (extractor, _, _) = makeExtractor("this is not json at all")
        assertNull(extractor.process("x"))
    }

    @Test
    fun `inference exception returns null`() = runTest(testDispatcher) {
        val tempFile = File.createTempFile("model", ".gguf").also { it.writeBytes(byteArrayOf(0)) }
        val llama = object : LlamaBridge() {
            override suspend fun load(modelPath: File, nCtx: Int, nThreads: Int) {}
            override suspend fun infer(prompt: String, maxTokens: Int): String =
                throw LlamaError.InferenceFailed("simulated")
        }
        val extractor = Extractor(
            context = mockContext(prompt),
            modelManager = FakeModelManager(tempFile),
            llama = llama,
        )
        assertNull(extractor.process("x"))
    }

    // ----- M3-T3: 8-shot prompt coverage -----

    @Test
    fun `DSP rank is preserved in the extracted person field`() = runTest(testDispatcher) {
        val canned = """
            {"person":"DSP Priya","action":"send the case diary","due_at":"2026-08-18T18:00:00+05:30",
             "priority":"NORMAL","instruction_text":"DSP Priya to send the case diary by Monday EOD",
             "confidence":0.92}
        """.trimIndent()
        val (extractor, _, _) = makeExtractor(canned)
        val proposal = extractor.process("DSP Priya should send the case diary by Monday EOD")
        advanceUntilIdle()
        assertNotNull(proposal)
        assertEquals("DSP Priya", proposal!!.person)
    }

    @Test
    fun `same-day time cue is preserved as a today ISO timestamp`() = runTest(testDispatcher) {
        val canned = """
            {"person":"SHO Triveni","action":"sign the seizure memo","due_at":"2026-08-12T17:00:00+05:30",
             "priority":"NORMAL","instruction_text":"Get SHO Triveni to sign the seizure memo before 5 PM",
             "confidence":0.9}
        """.trimIndent()
        val (extractor, _, _) = makeExtractor(canned)
        val proposal = extractor.process("Get SHO Triveni to sign the seizure memo before she leaves at 5")
        advanceUntilIdle()
        assertNotNull(proposal)
        assertEquals("SHO Triveni", proposal!!.person)
        assertNotNull("due_at must be set for a same-day cue", proposal.dueAt)
    }

    @Test
    fun `8th shot example with no instruction returns null`() = runTest(testDispatcher) {
        // Mirrors the 8th example: a meeting note, not an instruction.
        // The Extractor drops anything with confidence < 0.5.
        val (extractor, _, _) = makeExtractor(
            """{"person":null,"action":null,"due_at":null,"priority":"NORMAL","instruction_text":null,"confidence":0.2}"""
        )
        assertNull(extractor.process("meeting notes from bandobast review at 3pm"))
    }

    @Test
    fun `prompt is read from assets_prompts_extract_v1`() {
        // Sanity: the on-disk prompt must contain 8 examples (the
        // M3-T3 contract). This catches accidental regressions to
        // the 5-shot M1 prompt if a future change edits the file.
        // We read from the project source path (not via the
        // Application's assets/) so the test doesn't depend on
        // Robolectric bundling main-classpath resources (which
        // drags in security-crypto + its missing AndroidKeyStore).
        // user.dir is the module dir (app/) so the prompt is at
        // src/main/assets/prompts/extract_v1.txt.
        val src = java.io.File("src/main/assets/prompts/extract_v1.txt")
        val text = src.readText()
        val exampleCount = Regex("^Input:", RegexOption.MULTILINE).findAll(text).count()
        assertEquals("prompt must have 8 examples (M3-T3 contract)", 8, exampleCount)
    }
}
