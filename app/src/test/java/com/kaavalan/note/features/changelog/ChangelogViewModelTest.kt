package com.kaavalan.note.features.changelog

import androidx.test.core.app.ApplicationProvider
import com.kaavalan.note.data.preferences.KaavalanPreferences
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
 * v1.9.11 (A9 audit fix): tests for [ChangelogViewModel] and
 * the bundled `changelog.json`.
 *
 * The view model uses a DataStore-backed preference, which
 * runs on its own coroutine scope (not the test scope). To
 * avoid the classic `UncompletedCoroutinesError` we test the
 * pieces in isolation:
 *
 *  1. [ChangelogEntry.fromJson] — the JSON parser
 *  2. `KaavalanPreferences.setChangelogSeenAtVersion` / read
 *     round-trip — the preference flow
 *  3. The bundled `changelog.json` has the current build's
 *     code (sanity check that the JSON is in sync with the
 *     build config)
 *
 * Full state-flow testing happens via the activity's
 * integration test (manual on-device drive-verify); the unit
 * tests pin the contract that the activity relies on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChangelogViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val preferences = KaavalanPreferences(context)

    @Before
    fun setUp() {
        // Reset the seen flag to 0 (fresh install) before
        // each test. DataStore is process-wide; the
        // before-each reset prevents state leaking.
        runBlocking { preferences.setChangelogSeenAtVersion(0) }
    }

    @Test
    fun `ChangelogEntry fromJson parses a valid entry`() {
        val obj = org.json.JSONObject("""
            {
              "version": "1.9.11",
              "code": 41,
              "date": "2026-08-24",
              "highlights": ["First", "Second", "Third"]
            }
        """.trimIndent())
        val entry = ChangelogEntry.fromJson(obj)
        assertNotNull("Valid JSON must parse to a non-null entry", entry)
        assertEquals("1.9.11", entry!!.version)
        assertEquals(41, entry.code)
        assertEquals("2026-08-24", entry.date)
        assertEquals(listOf("First", "Second", "Third"), entry.highlights)
    }

    @Test
    fun `ChangelogEntry fromJson returns null for missing required fields`() {
        // Missing `code` — should return null.
        val obj = org.json.JSONObject("""
            {"version": "1.0.0", "date": "2026-08-24", "highlights": ["x"]}
        """.trimIndent())
        val entry = ChangelogEntry.fromJson(obj)
        assertEquals("Missing `code` must yield null", null, entry)
    }

    @Test
    fun `ChangelogEntry fromJson returns null for missing highlights`() {
        // Missing `highlights` — should return null (an entry
        // with no highlights is not useful on the screen).
        val obj = org.json.JSONObject("""
            {"version": "1.0.0", "code": 1, "date": "2026-08-24"}
        """.trimIndent())
        val entry = ChangelogEntry.fromJson(obj)
        assertEquals("Missing `highlights` must yield null", null, entry)
    }

    @Test
    fun `bundled changelog has at least one entry with the current build code`() {
        // Sanity check: the changelog must include the current
        // build. If a build ships without a corresponding
        // changelog entry, the "What's new" screen will show
        // nothing — bad UX.
        // NOTE: this test reads the asset from the build
        // directory, not via the Android asset manager, because
        // Robolectric's asset manager occasionally returns
        // FileNotFoundException for new assets until the next
        // cold build. The host file is the source of truth.
        val text = java.io.File("src/main/assets/changelog.json").readText()
        val root = org.json.JSONObject(text)
        val array = root.optJSONArray("changelog")
        assertNotNull("changelog.json must have a 'changelog' array", array)
        assertTrue("changelog array must be non-empty", array!!.length() > 0)
        val currentCode = com.kaavalan.note.BuildConfig.VERSION_CODE
        val hasCurrent = (0 until array.length()).any { i ->
            array.optJSONObject(i).optInt("code", -1) == currentCode
        }
        assertTrue(
            "changelog.json must include an entry for the current build (code=$currentCode). " +
                "If you are building an unreleased version, add a changelog entry before shipping.",
            hasCurrent || currentCode == 0,
        )
    }

    @Test
    fun `setChangelogSeenAtVersion round-trips through the DataStore`() = runBlocking {
        preferences.setChangelogSeenAtVersion(41)
        val read = preferences.lastSeenChangelogVersion.first()
        assertEquals(
            "setChangelogSeenAtVersion should round-trip through the DataStore",
            41,
            read,
        )
    }
}
