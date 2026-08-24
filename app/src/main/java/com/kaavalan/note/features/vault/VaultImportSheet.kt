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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaavalan.note.R

/**
 * Tier 1.1 (v2.0): the vault import sheet. The user picks
 * the .baton-vault file via SAF, types the passphrase, taps
 * "Import". On success the sheet closes and the home tab
 * re-renders with the restored data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultImportSheet(
    onDismiss: () -> Unit,
    onImported: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsStateWithLifecycle()

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.import(uri, onImported)
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
                text = stringResource(R.string.vault_import_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.vault_import_body),
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
            state.error?.let { err ->
                Text(
                    text = stringResource(
                        when (err) {
                            VaultUiError.IncorrectPassphrase -> R.string.vault_import_incorrect
                            VaultUiError.NotAVault -> R.string.vault_import_not_vault
                            VaultUiError.UnsupportedVersion -> R.string.vault_import_unsupported
                            VaultUiError.DiskFull -> R.string.vault_import_disk_full
                            VaultUiError.IoError -> R.string.vault_import_io
                            else -> R.string.vault_import_failed
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    openLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                },
                enabled = !state.working && state.passphrase.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.vault_import_button))
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
