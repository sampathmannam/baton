package com.baton.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.BuildConfig
import com.baton.app.ai.llama.ModelManager
import com.baton.app.ai.llama.ModelState
import com.baton.app.ai.whisper.WhisperModelManager
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.local.AppDatabase
import com.baton.app.data.export.PlainExporter
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.SyncEngine
import com.baton.app.data.local.TagDao
import com.baton.app.data.preferences.BatonPreferences
import com.baton.app.data.preferences.ThemeMode
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
import com.baton.app.data.tags.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * M3-T4: Settings view-model. The single action for M3-T4 was sign-out.
 * M3-T7 adds a tag management surface: the sheet shows the user's tags
 * (a small chips list grouped by kind) plus a free-form entry to add
 * a new FREE tag.
 *
 * **Sign-out order.** [AppInitializer.runOnSignOut] MUST run before
 * [AuthRepository.signOut] returns, otherwise the in-flight Compose
 * tree (HomeScreen + SettingsSheet) still has Hilt references to
 * the database and would observe a "database is not a database"
 * error from SQLCipher. We run them in the right order here:
 * wipe first (synchronous, cheap), then drop the session (which
 * triggers the observer).
 *
 * v1.2 (BUG-AUTH-002): also close the Realtime WebSocket BEFORE
 * `authRepository.signOut()` so the previous user's JWT does not
 * stay on the wire after the local DB is wiped. The next
 * sign-in re-calls [RealtimeSync.start] with the new token.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appInitializer: AppInitializer,
    private val tagRepository: RoomTagRepository,
    private val realtimeSync: RealtimeSync,
    private val syncEngine: SyncEngine,
    private val personDao: PersonDao,
    private val instructionDao: InstructionDao,
    private val tagDao: TagDao,
    // v1.5.4: model download surfaces. The Settings → Models
    // section observes the LLM's [ModelManager.state] (the
    // hot `StateFlow` of [ModelState]) and the Whisper model's
    // simple `isAvailable()` boolean (downloaded + SHA-verified
    // against the bundled `whisper_sha256.txt`). Tapping a
    // download button in the section calls [downloadLlm] /
    // [downloadWhisper], which are thin wrappers around the
    // managers. Both flows live in the app process — no
    // WorkManager is needed; the user is staring at the
    // progress bar.
    private val modelManager: ModelManager,
    private val whisperModelManager: WhisperModelManager,
    // Tier 0.6: storage size in MB. The "On this phone"
    // row now shows "X.X MB on this phone" alongside the
    // existing people/instructions/tags counts. The size
    // is computed off the main thread (see
    // [computeStorageSizeBytes]) and refreshed whenever the user
    // adds a person, instruction, or capture.
    // v2.0 (Tier 1.1 + 1.4 + 1.7): theme + plain export
    // surfaces. The theme is a DataStore-backed flow exposed
    // by [BatonPreferences]. The plain export uses
    // [PlainExporter] to dump the DB to CSV / JSON.
    private val preferences: BatonPreferences,
    private val plainExporter: PlainExporter,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    /**
     * v1.2.4 (F-HIGH-08): number of outbox rows that have hit
     * [SyncEngine.MAX_ATTEMPTS] and are stuck
     * (`PERMANENT_FAILURE:*`). 0 means "no stuck rows" — the
     * UI hides the retry card. The flow re-emits on every
     * change (new stuck row, retry, or successful drain of a
     * previously-stuck row).
     */
    val stuckOutboxCount: StateFlow<Int> = syncEngine
        .observeStuckCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /**
     * M3-T7: the user's tag taxonomy, observed from the Room mirror.
     * Sorted by usageCount DESC then name ASC inside the DAO. The
     * sheet groups these by kind for the user.
     */
    val tags: StateFlow<List<Tag>> = tagRepository
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * v1.5.3 (VAULT-008): the "On this phone" storage card. Live
     * count of people + instructions + tags. The number is
     * recomputed on every local write (Room is reactive), so the
     * card updates as the user adds / removes rows.
     *
     * Tier 0.6: the same flow also recomputes the on-disk
     * size in bytes (DB file + WAL + SHM + photos dir). The
     * bytes are computed off the main thread; the
     * `WhileSubscribed(5_000)` policy keeps the upstream
     * active across config changes.
     */
    val storage: StateFlow<StorageInfo> = combine(
        personDao.observeAll(),
        instructionDao.observeAll(),
        tagDao.observeAll(),
    ) { persons, instructions, tags ->
        Triple(persons.size, instructions.size, tags.size)
    }.map { (people, instructions, tags) ->
        val bytes = computeStorageSizeBytes()
        StorageInfo(
            peopleCount = people,
            instructionCount = instructions,
            tagCount = tags,
            sizeBytes = bytes,
        )
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StorageInfo(),
    )

    /**
     * v1.5.3 (VAULT-008): the app version string. BuildConfig
     * has the versionName + versionCode; we expose them as a
     * tuple so the UI can render "{versionName} (build
     * {versionCode})" without re-reading BuildConfig.
     */
    val appVersion: AppVersion = AppVersion(
        name = BuildConfig.VERSION_NAME,
        code = BuildConfig.VERSION_CODE,
    )

    /**
     * v1.5.4: the on-device LLM model state. Surfaced in the
     * Settings → Models section. Re-exposes the manager's
     * process-wide hot flow so the UI can render the
     * "Download" / "Downloading 47%" / "Ready" / "Retry"
     * transitions.
     */
    val llmModelState: StateFlow<ModelState> = modelManager.state

    /**
     * Tier 0.5: the LLM download progress, exposed as a
     * `StateFlow<Float>` in the 0.0-1.0 range. The Settings
     * → Models row uses this to drive a real
     * `LinearProgressIndicator` (replacing the v1.5.7
     * "Downloading... 47%" text-only affordance).
     * Re-exposed from the manager's hot progress flow.
     */
    val llmDownloadProgress: StateFlow<Float> = modelManager.progress

    /**
     * v1.5.4: the Whisper model availability. The M2-T3
     * implementation doesn't yet have a stateful
     * `StateFlow<WhisperModelState>` analogue (it's a
     * one-shot `Flow<DownloadProgress>` consumed by the
     * service). The Settings UX only needs a boolean —
     * "is the file on disk and SHA-verified". We hold
     * that in a [MutableStateFlow] and bump it to `true`
     * when [downloadWhisper] finishes, and `false`
     * initially if the file is missing. A more robust
     * implementation would use a [FileObserver] or a
     * polling ticker; that's out of scope for v1.5.4
     * because the user is staring at the Settings sheet
     * when they tap "Download voice model" — they can
     * close + reopen to refresh if the spinner lingers.
     */
    private val _whisperAvailable = MutableStateFlow(whisperModelManager.isAvailable())
    val whisperAvailable: StateFlow<Boolean> = _whisperAvailable.asStateFlow()

    /**
     * v1.5.4: kick off the LLM download. Idempotent (the
     * underlying [ModelManager.download] returns early if the
     * model is already ready or a download is in flight).
     */
    fun downloadLlm() {
        modelManager.ensureModel()
        modelManager.download()
    }

    /**
     * v1.5.4: kick off the Whisper download. Same idempotent
     * contract as [downloadLlm]. The M2-T3 manager exposes
     * `downloadModel()` as a one-shot `Flow<DownloadProgress>`
     * consumed inside the manager's own scope — for Settings
     * UX we just need a single fire-and-forget invocation; the
     * UI shows a brief "Downloading…" line and the
     * [whisperAvailable] flow flips to `true` when the file
     * is on disk and verified.
     */
    fun downloadWhisper() {
        viewModelScope.launch {
            runCatching {
                whisperModelManager.downloadModel().collect { /* progress */ }
            }
            // Recompute after the flow terminates — the model
            // file is now either on disk (and SHA-verified) or
            // the flow threw. `isAvailable()` does the file
            // existence + SHA check, so the boolean accurately
            // reflects the post-download state.
            _whisperAvailable.value = whisperModelManager.isAvailable()
        }
    }

    fun signOut() {
        if (_signingOut.value) return
        _signingOut.value = true
        viewModelScope.launch {
            // Order matters:
            // 1. Close Realtime — the previous user's JWT must not
            //    stay on the WebSocket (BUG-AUTH-002 / BATON-WIRE-002).
            // 2. Wipe the local DB — the Compose tree still references
            //    SQLCipher via Hilt; the wipe must finish before the
            //    session observer tears the activity down.
            // 3. Sign out of Supabase — the local sign-out is
            //    effective even if the network call fails (the
            //    server-side refresh-token revocation is best-effort).
            realtimeSync.stop()
            runCatching { appInitializer.runOnSignOut() }
            authRepository.signOut()  // returns Result<Unit>; ignored on failure
            // The session observer in MainActivity will transition
            // to Unauthenticated and the Compose tree will tear
            // down the HomeScreen + SettingsSheet automatically.
            // We don't need to dismiss the sheet ourselves.
        }
    }

    /**
     * M3-T7: create a new FREE tag from the management surface. Same
     * path the LLM extractor will use when it surfaces a `#tag` it
     * hasn't seen before.
     */
    fun addFreeTag(name: String) {
        val clean = name.trim().trimStart('#').take(40)
        if (clean.isBlank()) return
        viewModelScope.launch {
            runCatching { tagRepository.findOrCreateFree(clean) }
        }
    }

    /**
     * v2.0 (Tier 1.4): expose the theme mode as a
     * [StateFlow]. The Settings sheet renders a segmented
     * button that mirrors the value. The
     * [BatonPreferences.setThemeMode] call persists to
     * DataStore; the root composable reads the same flow so
     * the swap is immediate.
     */
    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.System,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /**
     * v2.0 (Tier 1.7): export the DB as CSV or JSON to the
     * given SAF URI. The MIME type decides the format:
     *   - `text/csv`  → CSV with UTF-8 BOM
     *   - `application/json` → JSON
     * Other MIME types fall through to JSON. The call
     * returns `Result<Unit>` so the sheet can show a
     * user-friendly error if the write fails.
     */
    suspend fun exportPlain(uri: Uri, mime: String?): Result<Unit> = runCatching {
        val snap = plainExporter.snapshot()
        val bytes = when (mime) {
            "text/csv" -> plainExporter.toCsv(snap).toByteArray(Charsets.UTF_8)
            else -> plainExporter.toJson(snap).toByteArray(Charsets.UTF_8)
        }
        val out = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: error("Could not open output")
        out.use { it.write(bytes) }
    }

    /**
     * v1.2.4 (F-HIGH-08): reset all `PERMANENT_FAILURE:*` rows
     * in the outbox so the next drain retries them. Called from
     * the "Retry stuck entries" action in the Settings sheet.
     * The next periodic drain (15 minutes) or the next
     * per-write drain will process them with `attempts = 0`.
     *
     * No UI feedback is wired yet — the Settings sheet
     * observes [stuckOutboxCount] and the count drops to 0 on
     * success. A snackbar is a natural follow-up but it's
     * out of scope for this fix.
     */
    fun retryStuckOutbox() {
        viewModelScope.launch {
            runCatching { syncEngine.retryPermanentlyFailed() }
        }
    }

    /**
     * Tier 0.6: compute the on-disk size of the Baton's
     * local data. The calculation sums:
     *
     *  - the SQLCipher Room DB file (`baton.db`)
     *  - the WAL (`baton.db-wal`) and SHM (`baton.db-shm`)
     *    companions; the WAL is often the bulk of the size
     *    on a write-heavy vault
     *  - the `filesDir/captures/` directory of photo
     *    captures (M2-T2's ML Kit OCR pipeline writes the
     *    JPEG here)
     *
     * Runs on [Dispatchers.IO] (the call site maps the storage
     * flow onto IO via [kotlinx.coroutines.flow.flowOn]). The
     * function is `private` to the VM because the file layout
     * is an implementation detail. Tests for the size
     * computation live in
     * [com.baton.app.ui.settings.StorageSizeTest] and call an
     * equivalent helper directly via a Robolectric context.
     */
    private suspend fun computeStorageSizeBytes(): Long = withContext(Dispatchers.IO) {
        val db = appContext.getDatabasePath(AppDatabase.NAME)
        val wal = File(db.path + "-wal")
        val shm = File(db.path + "-shm")
        val captures = File(appContext.filesDir, "captures")
        var total = 0L
        listOf(db, wal, shm).forEach { f ->
            if (f.exists()) total += f.length()
        }
        if (captures.isDirectory) {
            captures.listFiles()?.forEach { f ->
                if (f.isFile) total += f.length()
            }
        }
        total
    }
}

/**
 * v1.5.3 (VAULT-008): the storage card payload. Counts only —
 * no per-row detail on this card (the home tab is the right
 * place to see individual rows).
 *
 * Tier 0.6: also carries the on-disk size in bytes (DB file
 * + WAL + SHM + photos dir). The Settings row formats this
 * as "X.X MB on this phone". The field is `0L` on a fresh
 * install before the user has added any data.
 */
data class StorageInfo(
    val peopleCount: Int = 0,
    val instructionCount: Int = 0,
    val tagCount: Int = 0,
    val sizeBytes: Long = 0L,
)

/**
 * v1.5.3 (VAULT-008): the version card payload. Read once at
 * VM construction from BuildConfig (the value doesn't change
 * at runtime, so a one-shot read is fine).
 */
data class AppVersion(
    val name: String = "",
    val code: Int = 0,
)
package com.baton.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.BuildConfig
import com.baton.app.ai.llama.ModelManager
import com.baton.app.ai.llama.ModelState
import com.baton.app.ai.whisper.WhisperModelManager
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.auth.SecurePreferences
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.local.InstructionDao
import com.baton.app.data.local.PersonDao
import com.baton.app.data.local.SyncEngine
import com.baton.app.data.local.TagDao
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
import com.baton.app.data.tags.Tag
import com.baton.app.data.vault.IdentityCrypto
import com.baton.app.data.vault.VaultMode
import com.baton.app.data.vault.VaultModeHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M3-T4: Settings view-model. The single action for M3-T4 was sign-out.
 * M3-T7 adds a tag management surface: the sheet shows the user's tags
 * (a small chips list grouped by kind) plus a free-form entry to add
 * a new FREE tag.
 *
 * **Sign-out order.** [AppInitializer.runOnSignOut] MUST run before
 * [AuthRepository.signOut] returns, otherwise the in-flight Compose
 * tree (HomeScreen + SettingsSheet) still has Hilt references to
 * the database and would observe a "database is not a database"
 * error from SQLCipher. We run them in the right order here:
 * wipe first (synchronous, cheap), then drop the session (which
 * triggers the observer).
 *
 * v1.2 (BUG-AUTH-002): also close the Realtime WebSocket BEFORE
 * `authRepository.signOut()` so the previous user's JWT does not
 * stay on the wire after the local DB is wiped. The next
 * sign-in re-calls [RealtimeSync.start] with the new token.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appInitializer: AppInitializer,
    private val tagRepository: RoomTagRepository,
    private val realtimeSync: RealtimeSync,
    private val syncEngine: SyncEngine,
    private val personDao: PersonDao,
    private val instructionDao: InstructionDao,
    private val tagDao: TagDao,
    // v1.5.4: model download surfaces. The Settings → Models
    // section observes the LLM's [ModelManager.state] (the
    // hot `StateFlow` of [ModelState]) and the Whisper model's
    // simple `isAvailable()` boolean (downloaded + SHA-verified
    // against the bundled `whisper_sha256.txt`). Tapping a
    // download button in the section calls [downloadLlm] /
    // [downloadWhisper], which are thin wrappers around the
    // managers. Both flows live in the app process — no
    // WorkManager is needed; the user is staring at the
    // progress bar.
    private val modelManager: ModelManager,
    private val whisperModelManager: WhisperModelManager,
    // v2.0 T3-1: deniable vault. The holder is a process
    // singleton; the ViewModel reads its current mode and
    // exposes a [setVaultMode] entry point for the Settings
    // sheet. The vault PIN (the auth gate on the
    // hidden -> visible transition) is stored in
    // [securePreferences] as a SHA-256 hash.
    private val vaultModeHolder: VaultModeHolder,
    private val securePreferences: SecurePreferences,
) : ViewModel() {

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    /**
     * v1.2.4 (F-HIGH-08): number of outbox rows that have hit
     * [SyncEngine.MAX_ATTEMPTS] and are stuck
     * (`PERMANENT_FAILURE:*`). 0 means "no stuck rows" — the
     * UI hides the retry card. The flow re-emits on every
     * change (new stuck row, retry, or successful drain of a
     * previously-stuck row).
     */
    val stuckOutboxCount: StateFlow<Int> = syncEngine
        .observeStuckCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /**
     * M3-T7: the user's tag taxonomy, observed from the Room mirror.
     * Sorted by usageCount DESC then name ASC inside the DAO. The
     * sheet groups these by kind for the user.
     */
    val tags: StateFlow<List<Tag>> = tagRepository
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * v1.5.3 (VAULT-008): the "On this phone" storage card. Live
     * count of people + instructions + tags. The number is
     * recomputed on every local write (Room is reactive), so the
     * card updates as the user adds / removes rows.
     */
    val storage: StateFlow<StorageInfo> = combine(
        personDao.observeAll(),
        instructionDao.observeAll(),
        tagDao.observeAll(),
    ) { persons, instructions, tags ->
        StorageInfo(
            peopleCount = persons.size,
            instructionCount = instructions.size,
            tagCount = tags.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StorageInfo(),
    )

    /**
     * v1.5.3 (VAULT-008): the app version string. BuildConfig
     * has the versionName + versionCode; we expose them as a
     * tuple so the UI can render "{versionName} (build
     * {versionCode})" without re-reading BuildConfig.
     */
    val appVersion: AppVersion = AppVersion(
        name = BuildConfig.VERSION_NAME,
        code = BuildConfig.VERSION_CODE,
    )

    /**
     * v1.5.4: the on-device LLM model state. Surfaced in the
     * Settings → Models section. Re-exposes the manager's
     * process-wide hot flow so the UI can render the
     * "Download" / "Downloading 47%" / "Ready" / "Retry"
     * transitions.
     */
    val llmModelState: StateFlow<ModelState> = modelManager.state

    /**
     * v1.5.4: the Whisper model availability. The M2-T3
     * implementation doesn't yet have a stateful
     * `StateFlow<WhisperModelState>` analogue (it's a
     * one-shot `Flow<DownloadProgress>` consumed by the
     * service). The Settings UX only needs a boolean —
     * "is the file on disk and SHA-verified". We hold
     * that in a [MutableStateFlow] and bump it to `true`
     * when [downloadWhisper] finishes, and `false`
     * initially if the file is missing. A more robust
     * implementation would use a [FileObserver] or a
     * polling ticker; that's out of scope for v1.5.4
     * because the user is staring at the Settings sheet
     * when they tap "Download voice model" — they can
     * close + reopen to refresh if the spinner lingers.
     */
    private val _whisperAvailable = MutableStateFlow(whisperModelManager.isAvailable())
    val whisperAvailable: StateFlow<Boolean> = _whisperAvailable.asStateFlow()

    /**
     * v1.5.4: kick off the LLM download. Idempotent (the
     * underlying [ModelManager.download] returns early if the
     * model is already ready or a download is in flight).
     */
    fun downloadLlm() {
        modelManager.ensureModel()
        modelManager.download()
    }

    /**
     * v1.5.4: kick off the Whisper download. Same idempotent
     * contract as [downloadLlm]. The M2-T3 manager exposes
     * `downloadModel()` as a one-shot `Flow<DownloadProgress>`
     * consumed inside the manager's own scope — for Settings
     * UX we just need a single fire-and-forget invocation; the
     * UI shows a brief "Downloading…" line and the
     * [whisperAvailable] flow flips to `true` when the file
     * is on disk and verified.
     */
    fun downloadWhisper() {
        viewModelScope.launch {
            runCatching {
                whisperModelManager.downloadModel().collect { /* progress */ }
            }
            // Recompute after the flow terminates — the model
            // file is now either on disk (and SHA-verified) or
            // the flow threw. `isAvailable()` does the file
            // existence + SHA check, so the boolean accurately
            // reflects the post-download state.
            _whisperAvailable.value = whisperModelManager.isAvailable()
        }
    }

    fun signOut() {
        if (_signingOut.value) return
        _signingOut.value = true
        viewModelScope.launch {
            // Order matters:
            // 1. Close Realtime — the previous user's JWT must not
            //    stay on the WebSocket (BUG-AUTH-002 / BATON-WIRE-002).
            // 2. Wipe the local DB — the Compose tree still references
            //    SQLCipher via Hilt; the wipe must finish before the
            //    session observer tears the activity down.
            // 3. Sign out of Supabase — the local sign-out is
            //    effective even if the network call fails (the
            //    server-side refresh-token revocation is best-effort).
            realtimeSync.stop()
            runCatching { appInitializer.runOnSignOut() }
            authRepository.signOut()  // returns Result<Unit>; ignored on failure
            // The session observer in MainActivity will transition
            // to Unauthenticated and the Compose tree will tear
            // down the HomeScreen + SettingsSheet automatically.
            // We don't need to dismiss the sheet ourselves.
        }
    }

    /**
     * M3-T7: create a new FREE tag from the management surface. Same
     * path the LLM extractor will use when it surfaces a `#tag` it
     * hasn't seen before.
     */
    fun addFreeTag(name: String) {
        val clean = name.trim().trimStart('#').take(40)
        if (clean.isBlank()) return
        viewModelScope.launch {
            runCatching { tagRepository.findOrCreateFree(clean) }
        }
    }

    /**
     * v1.2.4 (F-HIGH-08): reset all `PERMANENT_FAILURE:*` rows
     * in the outbox so the next drain retries them. Called from
     * the "Retry stuck entries" action in the Settings sheet.
     * The next periodic drain (15 minutes) or the next
     * per-write drain will process them with `attempts = 0`.
     *
     * No UI feedback is wired yet — the Settings sheet
     * observes [stuckOutboxCount] and the count drops to 0 on
     * success. A snackbar is a natural follow-up but it's
     * out of scope for this fix.
     */
    fun retryStuckOutbox() {
        viewModelScope.launch {
            runCatching { syncEngine.retryPermanentlyFailed() }
        }
    }

    /**
     * v2.0 T3-1: the active vault mode, observed from the
     * process-singleton [VaultModeHolder]. The Settings sheet
     * renders this value as the "Vault mode" row.
     */
    val vaultMode: StateFlow<VaultMode> = vaultModeHolder.mode

    /**
     * v2.0 T3-1: `true` once the user has set a vault PIN.
     * The Settings sheet gates the "switch to visible" flow
     * on this — a user without a PIN is prompted to set one
     * first. The underlying MutableStateFlow is re-emitted
     * by [setVaultPin] / [clearVaultPin] so the UI updates
     * without a process restart.
     */
    private val _hasVaultPin = MutableStateFlow(
        securePreferences.vaultPinHash() != null,
    )
    val hasVaultPin: StateFlow<Boolean> = _hasVaultPin.asStateFlow()

    /**
     * v2.0 T3-2: `true` once the user has generated a recovery
     * phrase. The Settings sheet renders a "Set up recovery
     * phrase" affordance when this is `false`; once it's
     * `true`, the affordance becomes "View recovery phrase"
     * (which still requires the PIN to re-display).
     */
    private val _hasRecoveryPhrase = MutableStateFlow(
        securePreferences.recoveryPhraseHash() != null,
    )
    val hasRecoveryPhrase: StateFlow<Boolean> = _hasRecoveryPhrase.asStateFlow()

    /**
     * v2.0 T3-1: set the vault PIN. The user supplies a 4-6
     * digit PIN; we SHA-256 it and store the hash. The PIN
     * itself is never persisted.
     *
     * @return `true` if the PIN was set, `false` if the input
     *   failed validation (empty or not digits).
     */
    fun setVaultPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        securePreferences.setVaultPinHash(IdentityCrypto.sha256Hex(pin))
        // Re-emit the `hasVaultPin` flow so the Settings sheet
        // updates without a process restart.
        _hasVaultPin.value = securePreferences.vaultPinHash() != null
        return true
    }

    /**
     * v2.0 T3-1: switch the active vault mode. The caller is
     * responsible for the PIN gate when the target mode is
     * [VaultMode.Visible] AND the user has a PIN set —
     * [pinMatches] should be checked first.
     *
     * The PIN is not an argument here because we want the
     * UI to drive the flow: it asks the user for the PIN,
     * checks it, then calls [setVaultMode] with the result.
     */
    fun setVaultMode(mode: VaultMode) {
        vaultModeHolder.setMode(mode)
    }

    /**
     * v2.0 T3-1: validate a candidate PIN against the stored
     * hash. Returns `true` iff the user has a PIN set AND
     * [pin]'s SHA-256 matches. Returns `false` on no-PIN-set
     * (caller should call [setVaultPin] first).
     */
    fun pinMatches(pin: String): Boolean {
        val stored = securePreferences.vaultPinHash() ?: return false
        return IdentityCrypto.sha256Hex(pin) == stored
    }

    /**
     * v2.0 T3-1: clear the vault PIN hash. Used by the
     * "Erase all data" path (in addition to [AppInitializer]
     * wiping the DB). The vault mode is reset to
     * [VaultMode.Visible] by the holder on the next
     * sign-in.
     */
    fun clearVaultPin() {
        securePreferences.clearVaultPinHash()
        _hasVaultPin.value = false
    }

    /**
     * v2.0 T3-2: the recovery phrase hash was just set by the
     * [com.baton.app.ui.privacy.RecoveryPhraseViewModel]. We
     * re-emit [hasRecoveryPhrase] so the Settings sheet's
     * "View recovery phrase" affordance appears without a
     * process restart.
     */
    fun onRecoveryPhraseChanged() {
        _hasRecoveryPhrase.value = securePreferences.recoveryPhraseHash() != null
    }

    private fun isValidPin(pin: String): Boolean {
        if (pin.length !in 4..6) return false
        return pin.all { it.isDigit() }
    }
}

/**
 * v1.5.3 (VAULT-008): the storage card payload. Counts only —
 * no per-row detail on this card (the home tab is the right
 * place to see individual rows).
 */
data class StorageInfo(
    val peopleCount: Int = 0,
    val instructionCount: Int = 0,
    val tagCount: Int = 0,
)

/**
 * v1.5.3 (VAULT-008): the version card payload. Read once at
 * VM construction from BuildConfig (the value doesn't change
 * at runtime, so a one-shot read is fine).
 */
data class AppVersion(
    val name: String = "",
    val code: Int = 0,
)
