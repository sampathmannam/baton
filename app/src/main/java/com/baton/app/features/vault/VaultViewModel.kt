package com.baton.app.features.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baton.app.data.vault.PassphraseStrength
import com.baton.app.data.vault.VaultError
import com.baton.app.data.vault.VaultExporter
import com.baton.app.data.vault.VaultImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tier 1.1 (v2.0): the ViewModel behind the vault export +
 * import sheets.
 *
 * The ViewModel is **stateful**: [passphrase] + [confirm] live
 * in a [MutableStateFlow] so the sheet survives process death
 * (the [androidx.lifecycle.SavedStateHandle] integration is
 * not needed here — the sheets are short-lived modal surfaces
 * and the user types into them once).
 *
 * Error mapping is centralised: [VaultError.IncorrectPassphrase]
 * is the universal "no" signal, so the import side does not
 * distinguish wrong-passphrase from tampered-file (consistent
 * with the design doc §6 T6).
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val exporter: VaultExporter,
    private val importer: VaultImporter,
    val passphraseStrength: PassphraseStrength,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    fun setPassphrase(value: String) {
        _state.value = _state.value.copy(passphrase = value, error = null)
    }

    fun setConfirm(value: String) {
        _state.value = _state.value.copy(confirm = value, error = null)
    }

    fun clear() {
        _state.value = VaultUiState()
    }

    /**
     * Run the export. Returns `true` on success (the sheet
     * should close), `false` on a validation or IO error
     * (the sheet surfaces the message in [state.error]).
     */
    fun export(uri: android.net.Uri, onSuccess: () -> Unit) {
        val s = _state.value
        if (s.passphrase.length < MIN_PASSPHRASE_LEN) {
            _state.value = s.copy(error = VaultUiError.TooShort)
            return
        }
        if (s.passphrase != s.confirm) {
            _state.value = s.copy(error = VaultUiError.Mismatch)
            return
        }
        _state.value = s.copy(working = true, error = null)
        viewModelScope.launch {
            val result = exporter.export(uri, s.passphrase.toCharArray())
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(working = false, finished = true)
                    onSuccess()
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        working = false,
                        error = mapError(e),
                    )
                },
            )
        }
    }

    /**
     * Run the import. Returns `true` on success (the sheet
     * should close), `false` on error.
     */
    fun import(uri: android.net.Uri, onSuccess: () -> Unit) {
        val s = _state.value
        _state.value = s.copy(working = true, error = null)
        viewModelScope.launch {
            val result = importer.import(uri, s.passphrase.toCharArray())
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(working = false, finished = true)
                    onSuccess()
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        working = false,
                        error = mapError(e),
                    )
                },
            )
        }
    }

    private fun mapError(e: Throwable): VaultUiError = when (e) {
        is VaultError.IncorrectPassphrase -> VaultUiError.IncorrectPassphrase
        is VaultError.NotAVault -> VaultUiError.NotAVault
        is VaultError.UnsupportedVersion -> VaultUiError.UnsupportedVersion
        is VaultError.UnsupportedKdf -> VaultUiError.NotAVault
        is VaultError.DiskFull -> VaultUiError.DiskFull
        is VaultError.IoError -> VaultUiError.IoError
        else -> VaultUiError.Other
    }

    companion object {
        const val MIN_PASSPHRASE_LEN = 8
    }
}

data class VaultUiState(
    val passphrase: String = "",
    val confirm: String = "",
    val working: Boolean = false,
    val finished: Boolean = false,
    val error: VaultUiError? = null,
)

/** UI-layer error categories — mapped 1:1 to user-facing strings. */
enum class VaultUiError {
    Mismatch,
    TooShort,
    IncorrectPassphrase,
    NotAVault,
    UnsupportedVersion,
    DiskFull,
    IoError,
    Other,
}
