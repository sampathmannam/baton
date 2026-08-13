package com.baton.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.local.AppInitializer
import com.baton.app.data.local.SyncEngine
import com.baton.app.data.sync.RealtimeSync
import com.baton.app.data.tags.RoomTagRepository
import com.baton.app.data.tags.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
