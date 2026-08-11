package com.baton.app.features.capture

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M1-T7 / M2-T1 unit test for [ShareIntake]. Robolectric is needed
 * because the Android [Intent] extras / data are stub-thrown in
 * plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareIntakeTest {

    private fun sendIntent(type: String, configure: Intent.() -> Unit = {}): Intent =
        Intent(Intent.ACTION_SEND).apply {
            this.type = type
            configure()
        }

    // --- text path ---

    @Test
    fun `inspect returns Text for a valid text plain SEND intent`() {
        val intent = sendIntent("text/plain") {
            putExtra(Intent.EXTRA_TEXT, "Tell SHO Ramu to send FIR 47")
        }
        val result = ShareIntake.inspect(intent)
        assertTrue(result is ShareIntake.Result.Text)
        assertEquals("Tell SHO Ramu to send FIR 47", (result as ShareIntake.Result.Text).text)
    }

    @Test
    fun `inspect returns null when intent is null`() {
        assertNull(ShareIntake.inspect(null))
    }

    @Test
    fun `inspect returns null when action is not SEND`() {
        val intent = sendIntent("text/plain") {
            action = Intent.ACTION_VIEW
            putExtra(Intent.EXTRA_TEXT, "x")
        }
        assertNull(ShareIntake.inspect(intent))
    }

    @Test
    fun `inspect returns null when text MIME has no EXTRA_TEXT`() {
        val intent = sendIntent("text/plain")
        assertNull(ShareIntake.inspect(intent))
    }

    @Test
    fun `inspect returns null when text MIME EXTRA_TEXT is blank`() {
        val intent = sendIntent("text/plain") {
            putExtra(Intent.EXTRA_TEXT, "   ")
        }
        assertNull(ShareIntake.inspect(intent))
    }

    @Test
    fun `inspect returns null when text MIME EXTRA_TEXT is missing entirely`() {
        val intent = sendIntent("text/plain")
        assertNull(ShareIntake.inspect(intent))
    }

    // --- image path (M2-T1) ---

    @Test
    fun `inspect returns Image for image png with EXTRA_STREAM`() {
        val uri = Uri.parse("content://media/external/images/42")
        val intent = sendIntent("image/png") {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val result = ShareIntake.inspect(intent)
        assertTrue(result is ShareIntake.Result.Image)
        assertEquals(uri, (result as ShareIntake.Result.Image).uri)
    }

    @Test
    fun `inspect returns Image for image jpeg with EXTRA_STREAM`() {
        val uri = Uri.parse("content://media/external/images/7")
        val intent = sendIntent("image/jpeg") {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val result = ShareIntake.inspect(intent)
        assertTrue(result is ShareIntake.Result.Image)
    }

    @Test
    fun `inspect returns Image for image slash star with EXTRA_STREAM`() {
        val uri = Uri.parse("content://media/external/images/9")
        val intent = sendIntent("image/*") {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val result = ShareIntake.inspect(intent)
        assertTrue(result is ShareIntake.Result.Image)
    }

    @Test
    fun `inspect returns null when image MIME has no EXTRA_STREAM`() {
        val intent = sendIntent("image/png")
        assertNull(ShareIntake.inspect(intent))
    }

    @Test
    fun `inspect returns null for unsupported MIME types`() {
        val intent = sendIntent("application/pdf") {
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://x"))
        }
        assertNull(ShareIntake.inspect(intent))
    }

    // --- common ---

    @Test
    fun `inspect returns null when MIME type is null`() {
        val intent = sendIntent("text/plain") {
            // wipe the type
            type = null
        }
        assertNull(ShareIntake.inspect(intent))
    }

    // --- forward intents ---

    @Test
    fun `buildForwardIntent carries the shared text in EXTRA_SHARED_TEXT`() {
        val intent = ShareIntake.buildForwardIntent(sharedText = "Tell SHO Ramu to send FIR 47")
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(
            "Tell SHO Ramu to send FIR 47",
            intent.getStringExtra(ShareIntake.EXTRA_SHARED_TEXT),
        )
        // The flags tell the system to land in the existing task
        // (or create one) without stacking share activities.
        assertNotNull(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertNotNull(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @Test
    fun `buildForwardFromImage carries the OCR text in EXTRA_SHARED_TEXT`() {
        val intent = ShareIntake.buildForwardFromImage(ocrText = "Tell SHO Ramu")
        assertEquals(
            "Tell SHO Ramu",
            intent.getStringExtra(ShareIntake.EXTRA_SHARED_TEXT),
        )
    }
}
