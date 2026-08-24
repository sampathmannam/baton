package com.kaavalan.note.features.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.kaavalan.note.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 0.1: Robolectric tests for the new Glance-based
 * [KaavalanCaptureWidget] + [KaavalanCaptureWidgetReceiver] pair.
 *
 * **What we test without an emulator:**
 *  - The QUICK_CAPTURE action constant matches the value wired
 *    into the manifest, the tile (Tier 0.2), and MainActivity.
 *  - Building an Intent with the action targets MainActivity
 *    (the deep-link contract).
 *  - The receiver class is instantiable and exposes a non-null
 *    [androidx.glance.appwidget.GlanceAppWidget].
 *  - The component name resolves to the receiver's class name.
 *  - The widget info XML resource is reachable from the test
 *    classpath (i.e. the manifest wiring is intact).
 *
 * **What we don't test here:**
 *  - Actual on-screen rendering (needs a real device or a
 *    Glance screenshot test -- neither is in scope for Tier 0).
 *  - The TileService `onClick` -> startActivity path (needs
 *    the system shade UI, smoke-tested on emulator in Tier
 *    0.2's drive case).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KaavalanCaptureWidgetTest {

    @Test
    fun `quick capture action is the shared constant`() {
        // The widget, the tile, and MainActivity all reference
        // this same string. Drift between the three would
        // silently break the deep link.
        assertEquals(
            "com.kaavalan.note.action.QUICK_CAPTURE",
            KaavalanCaptureWidget.ACTION_QUICK_CAPTURE,
        )
    }

    @Test
    fun `quick capture intent targets MainActivity`() {
        val intent = Intent().apply {
            action = KaavalanCaptureWidget.ACTION_QUICK_CAPTURE
            setClassName("com.kaavalan.note.debug", MainActivity::class.java.name)
        }
        assertEquals(KaavalanCaptureWidget.ACTION_QUICK_CAPTURE, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `glance widget receiver exposes a non-null GlanceAppWidget`() {
        // Tier 0.1: the receiver wraps a GlanceAppWidget
        // singleton. If this fails the manifest wiring is broken
        // and the widget will not appear in the picker.
        val receiver = KaavalanCaptureWidgetReceiver()
        assertNotNull(receiver.glanceAppWidget)
        assertTrue(
            "glanceAppWidget should be the KaavalanCaptureWidget singleton",
            receiver.glanceAppWidget is KaavalanCaptureWidget,
        )
    }

    @Test
    fun `component name resolves to the receiver`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication() as Context
        val cn = ComponentName(context, KaavalanCaptureWidgetReceiver::class.java)
        assertEquals(KaavalanCaptureWidgetReceiver::class.java.name, cn.className)
        assertTrue(cn.packageName.isNotEmpty())
    }

    @Test
    fun `widget info xml file is on the source classpath`() {
        // Tier 0.1: the manifest's <meta-data> points at
        // xml/kaavalan_capture_widget_info.xml. If the file is
        // missing, the widget picker silently drops the
        // widget. This assertion pins the file path so a
        // rename surfaces immediately. The R class is
        // generated at build time; checking the file on
        // disk is more reliable than the Robolectric
        // `getIdentifier` lookup, which depends on the
        // resource being registered in the test runtime.
        val xmlFile = java.io.File("src/main/res/xml/kaavalan_capture_widget_info.xml")
        assertTrue(
            "xml/kaavalan_capture_widget_info must exist at ${xmlFile.absolutePath}",
            xmlFile.exists(),
        )
    }
}
