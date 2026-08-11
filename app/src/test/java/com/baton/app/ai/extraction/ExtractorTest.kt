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
}
