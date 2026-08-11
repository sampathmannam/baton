package com.baton.app.features.capture

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M1-T7 unit test for [ShareIntake]. Robolectric is needed because
 * the Android [Intent] extras / data are stub-thrown in plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareIntakeTest {

    @Test
    fun `extractText returns the EXTRA_TEXT for a valid SEND intent`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Tell SHO Ramu to send FIR 47")
        }
        assertEquals("Tell SHO Ramu to send FIR 47", ShareIntake.extractText(intent))
    }

    @Test
    fun `extractText returns null for a null intent`() {
        assertNull(ShareIntake.extractText(null))
    }

    @Test
    fun `extractText returns null when action is not SEND`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "x")
        }
        assertNull(ShareIntake.extractText(intent))
    }

    @Test
    fun `extractText returns null when MIME type is not text slash plain`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, "x")
        }
        assertNull(ShareIntake.extractText(intent))
    }

    @Test
    fun `extractText returns null when MIME type is null`() {
        // The system share sheet usually sets type; but if a caller
        // sends an Intent with type=null we still want to fall through
        // cleanly (the receiver activity then forwards to MainActivity
        // without a pre-fill).
        val intent = Intent(Intent.ACTION_SEND).apply {
            // no type set
            putExtra(Intent.EXTRA_TEXT, "x")
        }
        assertNull(ShareIntake.extractText(intent))
    }

    @Test
    fun `extractText returns null when EXTRA_TEXT is blank`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "   ")
        }
        assertNull(ShareIntake.extractText(intent))
    }

    @Test
    fun `extractText returns null when EXTRA_TEXT is missing`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            // no EXTRA_TEXT
        }
        assertNull(ShareIntake.extractText(intent))
    }

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
}
