package com.baton.app.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.auth.SecurePreferences
import com.baton.app.data.vault.IdentityCrypto
import com.baton.app.data.vault.MnemonicGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * v2.0 T3-2 (recovery phrase): the ViewModel behind the
 * Settings -> Recovery phrase flow. Generates a fresh 12-word
 * BIP39 phrase from [SecureRandom] entropy, walks the user
 * through a 3-step verify flow, and persists a SHA-256 hash
 * of the phrase in [SecurePreferences].
 *
 * **State machine:**
 *  - `Idle` (initial) -> the user has not generated a phrase yet.
 *  - `Display(phrase)` -> the 12 words are shown on screen
 *    with FLAG_SECURE; the user is told to write them down.
 *  - `Verify(phrase, shuffled, picked)` -> the 12 words are
 *    re-rendered in a random order; the user taps them in
 *    the original order. Mismatches move the state back to
 *    `Display` with a "try again" message.
 *  - `Confirmed` -> the phrase hash is persisted and the
 *    flow closes.
 *
 * The phrase is held in memory only; the [IdentityCrypto.sha256Hex]
 * hash is the only on-disk artefact.
 *
 * **Settings notification.** Hilt prohibits injecting a
 * [androidx.lifecycle.ViewModel] into another ViewModel, so
 * the [SettingsViewModel] is NOT a constructor dep. The
 * caller (the hosting [RecoveryPhraseScreen] Composable)
 * supplies a [onPhraseHashChanged] lambda. We invoke it
 * once the user completes the verify step, so the Settings
 * sheet's "View recovery phrase" affordance appears without
 * a process restart.
 */
@HiltViewModel
class RecoveryPhraseViewModel @Inject constructor(
    private val mnemonicGenerator: MnemonicGenerator,
    private val securePreferences: SecurePreferences,
) : ViewModel() {

    /**
     * Lambda the caller provides. Fired exactly once when
     * the phrase hash is committed. The caller is the
     * Composable that hosts [RecoveryPhraseScreen]; it has
     * access to the [com.baton.app.ui.settings.SettingsViewModel]
     * via Hilt and calls its `onRecoveryPhraseChanged()`
     * method.
     */
    var onPhraseHashChanged: () -> Unit = {}

    private val _state = MutableStateFlow<RecoveryPhraseState>(RecoveryPhraseState.Idle)
    val state: StateFlow<RecoveryPhraseState> = _state.asStateFlow()

    /**
     * Start the flow. If a phrase already exists, regenerate
     * it (the user explicitly tapped "Regenerate recovery
     * phrase" in Settings). The old hash is overwritten only
     * after the user completes the verify step.
     */
    fun start() {
        val phrase = mnemonicGenerator.generate12()
        val shuffled = phrase.shuffled()
        _state.value = RecoveryPhraseState.Display(
            phrase = phrase,
            shuffled = shuffled,
        )
    }

    /**
     * User has read the displayed phrase and tapped
     * "I have written it down". Move to the verify step.
     */
    fun onWrittenDown() {
        val current = _state.value as? RecoveryPhraseState.Display ?: return
        _state.value = current.copy(writtenDownAcknowledged = true)
    }

    /**
     * User tapped a word during the verify step. We append
     * the word to the picked list; the caller (the Compose
     * UI) re-renders. The full "tap each in order" loop is
     * driven by the UI; we just hold the list.
     */
    fun pickWord(word: String) {
        val current = _state.value as? RecoveryPhraseState.Display ?: return
        if (!current.writtenDownAcknowledged) return
        // Don't allow duplicates: once a word is picked, hide
        // the chip. The state carries `picked` so the chip
        // is rendered dimmed + non-clickable.
        if (word in current.picked) return
        val newPicked = current.picked + word
        val state = if (newPicked.size == current.phrase.size) {
            // All 12 picked — verify.
            if (newPicked == current.phrase) {
                // Correct order. Persist the hash and move to
                // Confirmed. The phrase stays in memory until
                // the screen is dismissed; the screen handler
                // calls [onDismissed] which clears it.
                securePreferences.setRecoveryPhraseHash(
                    IdentityCrypto.sha256Hex(current.phrase.joinToString(" ")),
                )
                onPhraseHashChanged()
                RecoveryPhraseState.Confirmed
            } else {
                // Wrong order. Let the user try again.
                current.copy(
                    picked = emptyList(),
                    errorMessage = "Words aren't in the right order. Try again.",
                )
            }
        } else {
            current.copy(picked = newPicked)
        }
        _state.value = state
    }

    /**
     * User wants to restart the verify step. The phrase is
     * still in memory; the picked list is cleared.
     */
    fun retryVerify() {
        val current = _state.value as? RecoveryPhraseState.Display ?: return
        _state.value = current.copy(
            picked = emptyList(),
            errorMessage = null,
        )
    }

    /**
     * User finished (saw the "Done" screen) and is closing
     * the recovery phrase surface. We clear the in-memory
     * phrase; the hash in SecurePreferences is the only
     * on-disk artefact from now on.
     */
    fun onDismissed() {
        _state.value = RecoveryPhraseState.Idle
    }
}

/**
 * v2.0 T3-2: the recovery phrase UI state.
 *
 * The `Display` variant carries the canonical phrase (used
 * to verify the user's tap order), the shuffled list (used
 * to render the chip row), the picked list (used to dim
 * already-picked words), and an optional error message.
 */
sealed interface RecoveryPhraseState {
    data object Idle : RecoveryPhraseState

    data class Display(
        val phrase: List<String>,
        val shuffled: List<String>,
        val writtenDownAcknowledged: Boolean = false,
        val picked: List<String> = emptyList(),
        val errorMessage: String? = null,
    ) : RecoveryPhraseState

    data object Confirmed : RecoveryPhraseState
}
