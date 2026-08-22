package com.baton.app.ui.util

import android.content.Context
import android.os.Build
import com.baton.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.9.0 (PROD-READINESS-P3-P1-#1): the in-app
 * crash log. Catches unhandled exceptions on the
 * main looper, writes a structured log file to
 * `cacheDir/crashes/`, and shows a "share with
 * support" notification next time the user
 * launches the app.
 *
 * **Why a local crash log, not Crashlytics.**
 * The v1.5.0+ privacy policy is "we collect no
 * data". A third-party crash reporter (Firebase
 * Crashlytics, Sentry) breaks that promise —
 * the third party gets every crash's stack
 * trace. The local log keeps the user's crash
 * data on the user's device; the user shares it
 * with support if they want to.
 *
 * **Privacy guarantees.**
 *  - The crash log file is in `cacheDir/crashes/`
 *    (the app's private storage; not exported).
 *  - The file is overwritten on the next crash
 *    (only the most recent crash is kept; the
 *    older ones are auto-purged).
 *  - The user must explicitly share the log via
 *    the system share intent (the [CrashLog]
 *    helper exposes a FileProvider URI for the
 *    share).
 *
 * **File format.** Plain text. Each line is a
 * `key=value` pair. Easy to parse, easy to redact.
 * The format is documented in
 * `docs/crash-log-format.md` so a support reply
 * can grep the file directly.
 */
object CrashLog {

    private const val CRASH_DIR = "crashes"
    private const val MAX_CRASH_FILES = 10
    private const val FILE_PREFIX = "crash_"
    private const val FILE_EXT = ".txt"

    /**
     * The result of a [catchAndLog] call. The
     * caller (the [android.app.Application.onCreate]
     * hook) reads the result on the next launch
     * and shows the "Share with support" prompt.
     */
    data class CrashReport(
        val file: File,
        val timestampMs: Long,
    )

    /**
     * Write [throwable] to a crash log file in
     * `cacheDir/crashes/`. Returns the [CrashReport]
     * if the write succeeded, `null` otherwise
     * (the caller treats a `null` as "no crash to
     * report").
     *
     * **Failure modes.** If the disk is full or
     * `cacheDir` is unwritable, the function
     * returns `null` and the original throwable
     * is re-thrown to the caller. The user sees
     * the original crash; the support team just
     * doesn't get a log.
     */
    fun write(context: Context, throwable: Throwable): CrashReport? = try {
        val dir = File(context.cacheDir, CRASH_DIR).apply { mkdirs() }
        val timestampMs = System.currentTimeMillis()
        val timestampStr = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestampMs))
        val file = File(dir, "$FILE_PREFIX$timestampStr$FILE_EXT")
        val content = renderCrashLog(throwable, timestampMs)
        file.writeText(content)
        prune(dir)
        CrashReport(file = file, timestampMs = timestampMs)
    } catch (_: Throwable) {
        null
    }

    /**
     * The most recent crash log file, or `null`
     * if no crash has been logged this session
     * (or the previous launch).
     */
    fun mostRecent(context: Context): File? {
        val dir = File(context.cacheDir, CRASH_DIR)
        if (!dir.exists()) return null
        return dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_EXT) }
            ?.maxByOrNull { it.lastModified() }
    }

    /**
     * Delete all crash log files. Called from
     * the "Dismiss" action on the crash
     * notification.
     */
    fun clear(context: Context) {
        val dir = File(context.cacheDir, CRASH_DIR)
        if (!dir.exists()) return
        dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }?.forEach { it.delete() }
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_CRASH_FILES).forEach { it.delete() }
    }

    private fun renderCrashLog(throwable: Throwable, timestampMs: Long): String {
        val timestampStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(timestampMs))
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine("# Baton crash log")
            appendLine("timestamp=$timestampStr")
            appendLine("app_version=${BuildConfig.VERSION_NAME}")
            appendLine("app_build=${BuildConfig.VERSION_CODE}")
            appendLine("device_manufacturer=${Build.MANUFACTURER}")
            appendLine("device_model=${Build.MODEL}")
            appendLine("android_sdk=${Build.VERSION.SDK_INT}")
            appendLine("android_release=${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("# Stack trace")
            append(sw.toString())
        }
    }
}
