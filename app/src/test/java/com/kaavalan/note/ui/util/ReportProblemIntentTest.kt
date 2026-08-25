package com.kaavalan.note.ui.util

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.ui.settings.AppVersion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLDecoder

/**
 * v1.9.9 (A10 audit fix): tests for
 * [com.kaavalan.note.ui.util.ReportProblemIntent.build].
 *
 * **What this test pins.**
 *
 *  1. The intent is an `ACTION_SENDTO` with a `mailto:` URI.
 *  2. The subject contains the app version name + "problem report".
 *  3. The body contains the app version name + code + device
 *     + Android version.
 *  4. When no crash log exists, the body uses the
 *     no-crash template (no "--- Crash log ---" section).
 *  5. When a crash log exists, the body uses the
 *     with-crash template AND contains the crash log's
 *     text.
 *
 * The strings are passed in as plain `String` parameters
 * rather than resolved via `context.getString(R.string.*)`
 * because the project's Robolectric tests run without
 * `includeAndroidResources = true` (see the helper's class
 * docstring for the rationale). The Settings sheet does the
 * resource lookup in a Compose composable scope (where
 * `stringResource` works) and passes the resolved strings
 * to the helper.
 *
 * The crash log side is still exercised against the real
 * `cacheDir/crashes/` so [CrashLog.mostRecent] returns
 * them; the test cleans up in [tearDown].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReportProblemIntentTest {

    private val appVersion = AppVersion(name = "1.9.9", code = 39)
    private val supportEmail = "kaavalan-note@protonmail.com"

    // Mirrors the production string templates. The exact
    // text doesn't matter for these tests; we only assert
    // on the placeholders the helper fills in.
    private val subjectTemplate = "Kaavalan %1\$s problem report"
    private val bodyTemplateNoCrash =
        "What happened? (steps to reproduce)\n\n" +
            "App version: %1\$s (build %2\$d)\n" +
            "Device: %3\$s\n" +
            "Android: %4\$s"
    private val bodyTemplateWithCrash =
        "What happened? (steps to reproduce)\n\n" +
            "App version: %1\$s (build %2\$d)\n" +
            "Device: %3\$s\n" +
            "Android: %4\$s\n\n" +
            "--- Crash log (most recent) ---\n" +
            "%5\$s"

    @Before
    fun setUp() {
        CrashLog.clear(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        CrashLog.clear(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `build without crash log uses no-crash body template`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = ReportProblemIntent.build(
            context = context,
            appVersion = appVersion,
            subjectTemplate = subjectTemplate,
            bodyTemplate = bodyTemplateNoCrash,
            supportEmail = supportEmail,
        )

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        val uri = intent.data
        assertNotNull("mailto: URI must be set", uri)
        val queryParams = parseQuery(uri.toString())

        val subject = URLDecoder.decode(queryParams["subject"] ?: "", "UTF-8")
        val body = URLDecoder.decode(queryParams["body"] ?: "", "UTF-8")

        assertTrue(
            "subject must contain the version name, was: '$subject'",
            subject.contains("1.9.9"),
        )
        assertTrue(
            "subject must say 'problem report', was: '$subject'",
            subject.contains("problem report"),
        )
        assertTrue(
            "body must contain the version name, was: '$body'",
            body.contains("1.9.9"),
        )
        assertTrue(
            "body must contain the build code, was: '$body'",
            body.contains("39"),
        )
        assertTrue(
            "body must contain device info (manufacturer + model), was: '$body'",
            body.contains(Build.MANUFACTURER) || body.contains(Build.MODEL),
        )
        assertTrue(
            "body must contain the Android release, was: '$body'",
            body.contains("Android " + Build.VERSION.RELEASE),
        )
        assertEquals(
            "no-crash body must not include the crash-log section header",
            false,
            body.contains("--- Crash log"),
        )
        assertTrue(
            "mailto: target must be the support email, was: '$uri'",
            uri.toString().startsWith("mailto:$supportEmail"),
        )
    }

    @Test
    fun `build with crash log embeds the log text in the body`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Write a fake crash log so [CrashLog.mostRecent]
        // returns it. The file name + format must match what
        // [CrashLog.write] produces (so the helper's lookup
        // works) — see the file-prefix + ext constants in
        // [CrashLog].
        val crashDir = File(context.cacheDir, "crashes").apply { mkdirs() }
        val logContent = """
            # Kaavalan crash log
            timestamp=2026-08-24T01:23:45+05:30
            app_version=1.9.9
            app_build=39
            device_manufacturer=Google
            device_model=Pixel 6
            android_sdk=33
            android_release=13

            # Stack trace
            java.lang.RuntimeException: simulated for test
                at com.kaavalan.note.SomeClass.crash(SomeClass.kt:42)
        """.trimIndent()
        val logFile = File(crashDir, "crash_20260824-012345.txt")
        logFile.writeText(logContent)

        try {
            val intent = ReportProblemIntent.build(
                context = context,
                appVersion = appVersion,
                subjectTemplate = subjectTemplate,
                bodyTemplate = bodyTemplateWithCrash,
                supportEmail = supportEmail,
            )
            val body = URLDecoder.decode(
                parseQuery(intent.data.toString())["body"] ?: "",
                "UTF-8",
            )

            assertTrue(
                "body must include a Crash log section header, was: '$body'",
                body.contains("--- Crash log"),
            )
            assertTrue(
                "body must include the crash log's app_version line, was: '$body'",
                body.contains("app_version=1.9.9"),
            )
            assertTrue(
                "body must include the simulated exception class, was: '$body'",
                body.contains("java.lang.RuntimeException: simulated for test"),
            )
            assertTrue(
                "body must include the simulated frame, was: '$body'",
                body.contains("SomeClass.kt:42"),
            )
            val subject = URLDecoder.decode(
                parseQuery(intent.data.toString())["subject"] ?: "",
                "UTF-8",
            )
            assertTrue(
                "subject must still say 'problem report', was: '$subject'",
                subject.contains("problem report"),
            )
        } finally {
            logFile.delete()
        }
    }

    @Test
    fun `build with empty crash log file and no-crash template does not produce a Crash log section`() {
        // The Settings sheet's call site picks the
        // [bodyTemplateNoCrash] when [CrashLog.mostRecent]
        // returns null OR an empty file (both are treated
        // as "no log" by the caller). The helper then
        // formats with 5 args; the 5th is an empty string
        // and is ignored by the 4-placeholder template.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val crashDir = File(context.cacheDir, "crashes").apply { mkdirs() }
        val logFile = File(crashDir, "crash_20990101-000000.txt")
        logFile.writeText("")

        try {
            val intent = ReportProblemIntent.build(
                context = context,
                appVersion = appVersion,
                subjectTemplate = subjectTemplate,
                bodyTemplate = bodyTemplateNoCrash,
                supportEmail = supportEmail,
            )
            val body = URLDecoder.decode(
                parseQuery(intent.data.toString())["body"] ?: "",
                "UTF-8",
            )
            assertEquals(
                "empty crash log + no-crash template must not produce a Crash log section",
                false,
                body.contains("--- Crash log"),
            )
        } finally {
            logFile.delete()
        }
    }

    /**
     * Parse a `mailto:` URI into a map of decoded query
     * parameters. We do this by hand because
     * `Uri.getQueryParameter` decodes for us but treats the
     * entire `body=...` value as opaque (which is what we
     * want anyway). The `mailto:user@host?subject=...&body=...`
     * form puts everything after `?` into the query.
     */
    private fun parseQuery(uri: String): Map<String, String> {
        val q = uri.substringAfter("?", "")
        if (q.isEmpty()) return emptyMap()
        return q.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else pair.substring(0, idx) to pair.substring(idx + 1)
        }.toMap()
    }
}
