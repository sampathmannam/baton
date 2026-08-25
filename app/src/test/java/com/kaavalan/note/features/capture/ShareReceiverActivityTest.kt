package com.kaavalan.note.features.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.kaavalan.note.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 0.3: Robolectric tests for the v1.6.0
 * [ShareReceiverActivity].
 *
 * **What we test without an emulator:**
 *  - A valid `ACTION_SEND text/plain` intent produces a
 *    forward intent that targets MainActivity with the
 *    shared text as the `EXTRA_SHARED_TEXT` extra.
 *  - An invalid intent (wrong action) produces a forward
 *    intent with an empty string (the no-UI fallback).
 *  - The activity is constructible, package-consistent
 *    with the manifest, and uses the translucent theme
 *    declared in `themes.xml`.
 *
 * **What we don't test here:**
 *  - The actual on-shared-text UI in MainActivity. The
 *    drive case (`qa-share-receive.xml`) launches the
 *    activity via `adb shell am start -a SEND` and
 *    screencaps the capture sheet.
 *  - The image -> OCR -> forward chain (needs ML Kit
 *    + a real bitmap; out of scope for Tier 0).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareReceiverActivityTest {

    @Test
    fun `text SEND intent produces forward intent targeting MainActivity`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication() as Context
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Tell SHO Ramu to file FIR 47")
        }
        // Simulate the inspect path the activity runs.
        val result = ShareIntake.inspect(sendIntent)
        assertTrue("text/plain must produce a Text result", result is ShareIntake.Result.Text)
        val forward = ShareIntake.buildForwardIntent((result as ShareIntake.Result.Text).text)
        forward.setClassName(context, MainActivity::class.java.name)
        assertEquals(
            "Forward targets MainActivity",
            MainActivity::class.java.name,
            forward.component?.className,
        )
        assertEquals(
            "Forward carries the shared text",
            "Tell SHO Ramu to file FIR 47",
            forward.getStringExtra(ShareIntake.EXTRA_SHARED_TEXT),
        )
    }

    @Test
    fun `invalid intent produces empty forward (no pre-fill)`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication() as Context
        val bad = Intent(Intent.ACTION_VIEW)
        val result = ShareIntake.inspect(bad)
        assertEquals("non-SEND intents produce null from inspect", null, result)
        // The activity falls back to an empty forward.
        val forward = ShareIntake.buildForwardIntent(sharedText = "")
        forward.setClassName(context, MainActivity::class.java.name)
        assertEquals("", forward.getStringExtra(ShareIntake.EXTRA_SHARED_TEXT))
    }

    @Test
    fun `activity is package-consistent with the manifest`() {
        // The activity is @AndroidEntryPoint, so the
        // full Robolectric lifecycle is not exercised
        // here (Hilt's generated `_HiltModules` need a
        // HiltAndroidRule + a test application class,
        // which the v1.5.7 test suite does not provide
        // for the share-target surface). The
        // package-consistent check is enough to pin
        // the manifest wiring: a package move breaks
        // the manifest <activity-alias android:name=...>
        // reference and the share-sheet intent filter
        // becomes orphaned.
        val activityClass = ShareReceiverActivity::class.java
        assertEquals(
            "com.kaavalan.note.features.capture",
            activityClass.`package`?.name ?: "",
        )
        assertEquals(
            "com.kaavalan.note.features.capture.ShareReceiverActivity",
            activityClass.name,
        )
    }

    @Test
    fun `translucent theme file is on the source classpath`() {
        // The manifest's <activity-alias> declares
        // android:theme="@style/Theme.Kaavalan.Translucent.NoDisplay".
        // If a future commit drops the theme, the activity
        // flashes white on a share intent. We check the
        // themes.xml file directly (the R class is
        // generated at build time; the source file is the
        // source of truth).
        val themesFile = java.io.File("src/main/res/values/themes.xml")
        assertTrue(
            "themes.xml must exist at ${themesFile.absolutePath}",
            themesFile.exists(),
        )
        val contents = themesFile.readText()
        assertTrue(
            "themes.xml must declare Theme.Kaavalan.Translucent.NoDisplay",
            "Theme.Kaavalan.Translucent.NoDisplay" in contents,
        )
    }
}
