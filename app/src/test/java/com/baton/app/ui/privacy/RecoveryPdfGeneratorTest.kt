package com.baton.app.ui.privacy

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.8.0 (PROD-READINESS-P1-#2): the recovery-sheet PDF
 * generator test. The PDF render itself depends on
 * [android.graphics.pdf.PdfDocument] which is a thin
 * Robolectric shadow that does NOT actually start pages
 * in the unit-test sandbox (it throws "document is
 * closed!" on `startPage`); the real render path is
 * verified by the manual drive-verify on a real device.
 *
 * What we CAN test in the sandbox is the **input contract**:
 *  1. An empty phrase is rejected with a clear error
 *     (the user must not be able to write a blank PDF
 *     that produces a zero-page "file" and silently
 *     share it).
 *  2. The phrase is required to be non-empty at the
 *     Kotlin type level (no `null` allowed) via the
 *     `require()` guard.
 *
 * The full PDF render (file written, magic bytes, size
 * scaling) is a manual QA gate on a real device — the
 * unit-test sandbox cannot produce a valid PDF.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RecoveryPdfGeneratorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test(expected = IllegalArgumentException::class)
    fun `empty phrase is rejected`() {
        RecoveryPdfGenerator.generate(context, emptyList())
    }
}

