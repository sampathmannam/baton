package com.kaavalan.note.features.changelog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaavalan.note.BuildConfig
import com.kaavalan.note.data.preferences.KaavalanPreferences
import com.kaavalan.note.ui.theme.BatonTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * v1.9.11 (A9 audit fix): the in-app "What's new" screen.
 *
 * **What this is.** A simple bottom-sheet-equivalent that
 * shows the user the highlights of the current version
 * (and the last few versions) on the first launch after a
 * version bump. The screen reads from a bundled
 * `assets/changelog.json` (see the file for the format) and
 * uses a tiny `JSONObject` parse — no extra dep on
 * kotlinx.serialization.
 *
 * **When it shows.** `MainActivity` checks
 * `BuildConfig.VERSION_CODE > preferences.lastSeenChangelogVersion`
 * on every launch. If true (and the user has completed
 * onboarding), the screen shows. The user dismisses by
 * tapping "Got it" which writes the current version code to
 * preferences; next launch the check is `false` and the
 * screen does not show.
 *
 * **What it is NOT.** This is not an onboarding flow —
 * onboarding is a separate, one-time screen for first install.
 * The "What's new" screen is per-version: the user sees the
 * v1.9.11 highlights on first launch of v1.9.11, the v1.9.12
 * highlights on first launch of v1.9.12, and so on.
 *
 * **Manual access.** A "What's new" item in Settings → About
 * (added separately in v1.9.12) lets the user re-open this
 * screen at any time. The screen is self-contained: it has no
 * close affordance other than the "Got it" button.
 */
@Composable
fun ChangelogScreen(
    onDismiss: () -> Unit,
    viewModel: ChangelogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Mark the current version as "seen" when the user
    // dismisses. We do this in the composable (rather than
    // in the ViewModel) so the side effect is tied to the
    // explicit dismiss gesture, not to data-loading.
    val handleDismiss: () -> Unit = {
        viewModel.markSeen(onDismiss)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(context, com.kaavalan.note.R.string.changelog_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "v${state.currentVersion} (build ${state.currentVersionCode})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    state.entries.forEach { entry ->
                        ChangelogEntryCard(entry = entry)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Button(onClick = handleDismiss) {
                    Text(stringResource(context, com.kaavalan.note.R.string.changelog_got_it))
                }
            }
        }
    }
}

@Composable
private fun ChangelogEntryCard(entry: ChangelogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "v${entry.version}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entry.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            entry.highlights.forEach { highlight ->
                Text(
                    text = "• $highlight",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun stringResource(context: Context, resId: Int): String =
    context.getString(resId)

/**
 * ViewModel for [ChangelogScreen]. Reads `assets/changelog.json`
 * on init, exposes the parsed entries. The "seen" flag is
 * stored in [KaavalanPreferences.lastSeenChangelogVersion].
 */
@HiltViewModel
class ChangelogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: KaavalanPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangelogState())
    val state: StateFlow<ChangelogState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Read the last-seen version BEFORE we read the
            // changelog — the latter is independent of the
            // former.
            val lastSeen = preferences.lastSeenChangelogVersion.first()
            _state.value = loadChangelog(lastSeen = lastSeen)
        }
    }

    /**
     * Mark the current version as seen and invoke [onDismiss]
     * (which the activity uses to navigate away).
     */
    fun markSeen(onDismiss: () -> Unit) {
        viewModelScope.launch {
            preferences.setChangelogSeenAtVersion(BuildConfig.VERSION_CODE)
            onDismiss()
        }
    }

    /**
     * Read the changelog from `assets/changelog.json` and
     * build the state. Filters out versions the user has
     * already seen (capped at 5 entries to keep the screen
     * short; older versions are reachable from the Settings →
     * About "What's new" entry once that lands in v1.9.12).
     */
    private fun loadChangelog(lastSeen: Int): ChangelogState {
        val (allEntries, currentCode) = readChangelogFromAssets()
        val relevant = allEntries
            .filter { it.code > lastSeen }
            .take(MAX_ENTRIES_TO_SHOW)
        return ChangelogState(
            currentVersion = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
            entries = if (relevant.isEmpty()) allEntries.take(1) else relevant,
        )
    }

    /**
     * Parse `assets/changelog.json` into typed entries.
     * Returns `(entries, currentMaxCode)`. The function is
     * defensive: a missing or malformed file yields an empty
     * list (the screen will show "no entries" rather than
     * crash).
     */
    private fun readChangelogFromAssets(): Pair<List<ChangelogEntry>, Int> {
        val text = runCatching {
            context.assets.open("changelog.json").use { input ->
                input.bufferedReader().readText()
            }
        }.getOrNull() ?: return emptyList<ChangelogEntry>() to 0
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return emptyList<ChangelogEntry>() to 0
        val array = root.optJSONArray("changelog") ?: return emptyList<ChangelogEntry>() to 0
        val entries = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            ChangelogEntry.fromJson(obj)
        }
        return entries to (entries.maxOfOrNull { it.code } ?: 0)
    }

    companion object {
        private const val MAX_ENTRIES_TO_SHOW = 5
    }
}

data class ChangelogState(
    val currentVersion: String = "1.0.0",
    val currentVersionCode: Int = 0,
    val entries: List<ChangelogEntry> = emptyList(),
) {
    companion object {
        fun empty() = ChangelogState()
    }
}

/**
 * A single changelog entry parsed from `assets/changelog.json`.
 */
data class ChangelogEntry(
    val version: String,
    val code: Int,
    val date: String,
    val highlights: List<String>,
) {
    companion object {
        /**
         * Parse a single entry from a `JSONObject`. Returns
         * null on missing fields (defensive — a malformed
         * entry should not crash the screen).
         */
        fun fromJson(obj: JSONObject): ChangelogEntry? {
            return runCatching {
                val version = obj.optString("version").takeIf { it.isNotBlank() } ?: return null
                val code = obj.optInt("code", -1).takeIf { it >= 0 } ?: return null
                val date = obj.optString("date", "unknown")
                val highlights = obj.optJSONArray("highlights")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: return null
                ChangelogEntry(version, code, date, highlights)
            }.getOrNull()
        }
    }
}
