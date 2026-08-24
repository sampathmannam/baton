package com.kaavalan.note.features.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaavalan.note.R
import com.kaavalan.note.data.vault.PassphraseStrength
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Tier 1.1 (v2.0): the vault export sheet.
 *
 * The sheet accepts the passphrase + a confirm field, shows
 * a [LinearProgressIndicator] of the strength (0-4 mapped to
 * 0-1f), then on "Save vault file" launches a SAF
 * [ActivityResultContracts.CreateDocument] and hands the
 * resulting [android.net.Uri] to the [VaultViewModel.export].
 *
 * **No-shame copy.** The strength label uses the calm palette
 * tokens (the progress bar's colour is the M3 primary — no
 * red even at score 0). The strength hint is informational,
 * not blocking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultExportSheet(
    onDismiss: () -> Unit,
    onExported: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var createdDocUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            createdDocUri = uri
            viewModel.export(uri, onExported)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.vault_export_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.vault_export_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.passphrase,
                onValueChange = viewModel::setPassphrase,
                label = { Text(stringResource(R.string.vault_export_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.working,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.passphrase.isNotEmpty()) {
                val score = viewModel.passphraseStrength.score(state.passphrase)
                val label = stringResource(
                    R.string.vault_strength_label,
                    stringResource(
                        when (score) {
                            0 -> R.string.vault_strength_0
                            1 -> R.string.vault_strength_1
                            2 -> R.string.vault_strength_2
                            3 -> R.string.vault_strength_3
                            else -> R.string.vault_strength_4
                        },
                    ),
                )
                LinearProgressIndicator(
                    progress = { (score + 1) / 5f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.confirm,
                onValueChange = viewModel::setConfirm,
                label = { Text(stringResource(R.string.vault_export_confirm)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !state.working,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { err ->
                Text(
                    text = stringResource(
                        when (err) {
                            VaultUiError.Mismatch -> R.string.vault_export_mismatch
                            VaultUiError.TooShort -> R.string.vault_export_too_short
                            else -> R.string.vault_export_failed
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val s = state
                    if (s.passphrase.length < VaultViewModel.MIN_PASSPHRASE_LEN) {
                        // Inline error shown by the state.error path
                        viewModel.setPassphrase(s.passphrase)
                        // Force the "too short" error by re-validating:
                        viewModel.setConfirm(s.confirm)
                        return@Button
                    }
                    val filename = "baton-vault-${ts()}.baton-vault"
                    createLauncher.launch(filename)
                },
                enabled = !state.working && state.passphrase.isNotEmpty() && state.confirm.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vault_export_button))
            }
            TextButton(
                onClick = onDismiss,
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun ts(): String =
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
