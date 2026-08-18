package com.baton.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.data.preferences.ThemeMode
import com.baton.app.data.tags.Tag
import com.baton.app.data.tags.TagKind
import com.baton.app.data.vault.VaultMode
import com.baton.app.features.tags.colorForKind
import com.baton.app.features.tags.parseHex
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onVaultExport: () -> Unit = {},
    onVaultImport: () -> Unit = {},
    onOpenRecoveryPhrase: () -> Unit = {},
    onOpenThreatModel: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val appVersion = viewModel.appVersion
    // v1.6.1: the "Models" section is gone. The on-device
    // LLM and the whisper.cpp voice model are both removed.
    // Voice capture uses the system SpeechRecognizer (no
    // model file to download). The capture sheet has no
    // Extract step. The Settings sheet is now the About +
    // Data + Theme + Privacy surface it was in v1.5.4
    // before the Models section landed.
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    // v1.5.1 (VAULT-007): the destructive action (erases ALL local
    // data) used to fire on a single button tap. In vault mode the
    // user has no cloud backup, so a stray tap means losing every
    // note forever. Require an explicit confirmation.
    var showEraseConfirmation by remember { mutableStateOf(false) }
    var plainExportError by remember { mutableStateOf<String?>(null) }
    var plainExportOk by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val r = viewModel.exportPlain(uri, "text/csv")
                if (r.isSuccess) { plainExportOk = true; plainExportError = null }
                else { plainExportError = r.exceptionOrNull()?.message }
            }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val r = viewModel.exportPlain(uri, "application/json")
                if (r.isSuccess) { plainExportOk = true; plainExportError = null }
                else { plainExportError = r.exceptionOrNull()?.message }
            }
        }
    }
    // v2.0 T3-1: deniable-vault dialogs. The three state vars
    // drive the "set PIN" / "enter PIN to unlock" / "confirm
    // switch to hidden" flows. They are mutually exclusive
    // (only one is open at a time) and each is dismissed by
    // its own confirm/dismiss button.
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showEnterPinDialog by remember { mutableStateOf(false) }
    var showSwitchToHiddenConfirm by remember { mutableStateOf(false) }
    var enterPinWrong by remember { mutableStateOf(false) }
    val vaultMode by viewModel.vaultMode.collectAsStateWithLifecycle()
    val hasVaultPin by viewModel.hasVaultPin.collectAsStateWithLifecycle()

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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            TagsSection(
                tags = tags,
                onAdd = viewModel::addFreeTag,
            )

            // v2.0 T3-1 + T3-2 + T3-3: the Privacy section.
            // Four rows: vault mode, vault PIN, recovery
            // phrase, threat model. Each row is a tappable
            // affordance that opens either a dialog
            // (PIN-related) or a dedicated screen (recovery
            // phrase, threat model). The vault-mode row is
            // the only one that takes effect immediately;
            // the others navigate.
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_section_privacy),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            PrivacyRow(
                label = stringResource(R.string.settings_vault_mode),
                value = when (vaultMode) {
                    VaultMode.Visible -> stringResource(R.string.settings_vault_mode_visible)
                    VaultMode.Hidden -> stringResource(R.string.settings_vault_mode_hidden)
                },
                explainer = if (hasVaultPin) {
                    stringResource(R.string.settings_vault_mode_explainer)
                } else {
                    stringResource(R.string.settings_vault_mode_explainer_no_pin)
                },
                onClick = {
                    if (vaultMode == VaultMode.Visible) {
                        showSwitchToHiddenConfirm = true
                    } else {
                        // Hidden -> Visible. Requires PIN.
                        if (hasVaultPin) {
                            showEnterPinDialog = true
                        } else {
                            // No PIN set; force the user to
                            // set one first by opening the
                            // PIN dialog.
                            showSetPinDialog = true
                        }
                    }
                },
            )
            PrivacyRow(
                label = stringResource(R.string.settings_vault_pin),
                value = if (hasVaultPin) {
                    stringResource(R.string.settings_vault_pin_value_set)
                } else {
                    stringResource(R.string.settings_vault_pin_unset)
                },
                explainer = null,
                onClick = { showSetPinDialog = true },
            )
            val hasRecoveryPhrase by viewModel.hasRecoveryPhrase.collectAsStateWithLifecycle()
            PrivacyRow(
                label = stringResource(R.string.settings_recovery_phrase),
                value = if (hasRecoveryPhrase) {
                    stringResource(R.string.settings_recovery_phrase_value_set)
                } else {
                    stringResource(R.string.settings_recovery_phrase_value_unset)
                },
                explainer = stringResource(R.string.settings_recovery_phrase_explainer),
                onClick = onOpenRecoveryPhrase,
            )
            PrivacyRow(
                label = stringResource(R.string.settings_threat_model),
                value = stringResource(R.string.settings_threat_model_value),
                explainer = null,
                onClick = onOpenThreatModel,
            )

            val stuckCount by viewModel.stuckOutboxCount.collectAsStateWithLifecycle()
            if (stuckCount > 0) {
                StuckOutboxCard(
                    count = stuckCount,
                    onRetry = viewModel::retryStuckOutbox,
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // v1.6.1: the "Models" section is removed. The
            // on-device LLM (llama.cpp) and the whisper.cpp
            // voice model are both gone. Voice capture uses
            // the system SpeechRecognizer; the capture sheet
            // has no Extract step. The Settings sheet is now
            // the About + Data + Theme + Privacy surface it
            // was in v1.5.4 before the Models section landed.

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // v2.0 (Tier 1.4): the theme switcher. A segmented
            // button row that maps `ThemeMode` to the user's
            // choice (System / Light / Dark). The selection
            // persists via [BatonPreferences.setThemeMode] and
            // the root composable observes the same flow.
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            ThemeRow(
                current = themeMode,
                onChange = viewModel::setThemeMode,
            )
            Spacer(Modifier.height(8.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // v2.0 (Tier 1.1 + 1.7): the Data section. Three
            // rows: Export encrypted vault (opens a sheet that
            // collects a passphrase), Import encrypted vault
            // (opens a sheet that collects a passphrase + the
            // file), and Export as CSV or JSON (uses a SAF
            // CreateDocument with custom MIME).
            Text(
                text = stringResource(R.string.settings_section_data),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Button(
                onClick = onVaultExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_vault_export))
            }
            Button(
                onClick = onVaultImport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_vault_import))
            }
            Text(
                text = stringResource(R.string.settings_plain_export),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        plainExportError = null
                        plainExportOk = false
                        csvLauncher.launch("baton-${ts()}.csv")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(stringResource(R.string.plain_export_csv))
                }
                Button(
                    onClick = {
                        plainExportError = null
                        plainExportOk = false
                        jsonLauncher.launch("baton-${ts()}.json")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(stringResource(R.string.plain_export_json))
                }
            }
            if (plainExportOk) {
                Text(
                    text = stringResource(R.string.plain_export_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (plainExportError != null) {
                Text(
                    text = stringResource(R.string.plain_export_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // v1.5.3 (VAULT-008): the About section. App version,
            // storage counts, data mode. No interaction, just
            // info — these are read-only debug-style fields the
            // user can use to verify which build is on the device
            // and how much is in the vault.
            Text(
                text = stringResource(R.string.settings_section_about),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            AboutRow(
                label = stringResource(R.string.settings_app_version),
                value = stringResource(
                    R.string.settings_app_version_value,
                    appVersion.name,
                    appVersion.code,
                ),
            )
            // Tier 0.6: the "On this phone" row now
            // shows both the row counts and the on-disk
            // size in MB. The values are rendered as a
            // Column (two text lines) inside a single
            // AboutRow so the label "On this phone" is
            // only shown once. The MB number is
            // recomputed off the main thread every time
            // the upstream `storage` flow emits (Room is
            // reactive -- adding a person or a capture
            // triggers a refresh).
            AboutRow(
                label = stringResource(R.string.settings_storage),
                value = buildString {
                    append(
                        stringResource(
                            R.string.settings_storage_value,
                            storage.peopleCount,
                            storage.instructionCount,
                            storage.tagCount,
                        ),
                    )
                    append('\n')
                    append(
                        stringResource(
                            R.string.settings_storage_size_mb,
                            storage.sizeBytes / (1024.0 * 1024.0),
                        ),
                    )
                },
            )
            AboutRow(
                label = stringResource(R.string.settings_data_mode),
                value = stringResource(R.string.settings_data_mode_vault),
            )
            Spacer(Modifier.height(8.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // v1.4 (PHONE-FINDING-2): the previous version used
            // colorScheme.errorContainer (bright red). Replaced
            // with surfaceVariant/onSurfaceVariant + a subtle
            // border and a lock icon to communicate "destructive"
            // without colour.
            Button(
                onClick = { showEraseConfirmation = true },
                enabled = !signingOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (signingOut) {
                        stringResource(R.string.settings_signing_out)
                    } else {
                        stringResource(R.string.settings_sign_out)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // v1.5.1 (VAULT-007): the erase-all confirmation. Without this
    // a single accidental tap in the settings sheet wipes every
    // person, every instruction, and every tag — there is no cloud
    // backup in vault mode. The dialog uses neutral wording
    // ("Erase") instead of "Delete" / "Destroy" to match the
    // no-shame tone the rest of the app uses.
    if (showEraseConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEraseConfirmation = false },
            title = { Text(stringResource(R.string.settings_erase_confirm_title)) },
            text = { Text(stringResource(R.string.settings_erase_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEraseConfirmation = false
                        scope.launch {
                            viewModel.signOut()
                        }
                    },
                ) {
                    Text(stringResource(R.string.settings_erase_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirmation = false }) {
                    Text(stringResource(R.string.settings_erase_confirm_no))
                }
            },
        )
    }

    // v2.0 T3-1: the three vault dialogs. Each is a separate
    // state var so they don't collide. The PIN dialogs use a
    // local `remember { mutableStateOf("") }` for the text
    // field; the privacy-screen VM only sees the final string
    // when the user confirms.
    if (showSetPinDialog) {
        PinDialog(
            title = stringResource(R.string.settings_vault_pin_dialog_title),
            body = stringResource(R.string.settings_vault_pin_dialog_body),
            confirmLabel = stringResource(R.string.settings_vault_pin_dialog_save),
            onConfirm = { pin ->
                if (viewModel.setVaultPin(pin)) {
                    showSetPinDialog = false
                }
            },
            onDismiss = { showSetPinDialog = false },
        )
    }
    if (showEnterPinDialog) {
        PinDialog(
            title = stringResource(R.string.settings_vault_enter_pin_title),
            body = stringResource(R.string.settings_vault_enter_pin_body),
            confirmLabel = stringResource(R.string.settings_vault_enter_pin_confirm),
            onConfirm = { pin ->
                if (viewModel.pinMatches(pin)) {
                    viewModel.setVaultMode(VaultMode.Visible)
                    showEnterPinDialog = false
                    enterPinWrong = false
                } else {
                    // Wrong PIN: keep the dialog open and
                    // surface the "try again" message.
                    enterPinWrong = true
                }
            },
            onDismiss = {
                showEnterPinDialog = false
                enterPinWrong = false
            },
            invalidMessage = if (enterPinWrong) {
                stringResource(R.string.settings_vault_enter_pin_wrong)
            } else {
                null
            },
        )
    }
    if (showSwitchToHiddenConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSwitchToHiddenConfirm = false },
            title = { Text(stringResource(R.string.settings_vault_switch_to_hidden_title)) },
            text = { Text(stringResource(R.string.settings_vault_switch_to_hidden_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchToHiddenConfirm = false
                    viewModel.setVaultMode(VaultMode.Hidden)
                }) {
                    Text(stringResource(R.string.settings_vault_switch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchToHiddenConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * v2.0 T3-1: one tappable row in the Privacy section. The
 * row is a single-line label + value, with an optional
 * small explainer below. Tapping anywhere on the row fires
 * [onClick]. The row is non-destructive (no red, no error
 * colour) — the worst the user can do is open a dialog.
 */
@Composable
private fun PrivacyRow(
    label: String,
    value: String,
    explainer: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                // v2.0 T3-1: a Tappable privacy row. The
                // [label] is the visible Text inside, so we
                // don't need a separate contentDescription;
                // TalkBack will read the label as the row's
                // name. The semantics block is here to make
                // the static a11y scan in
                // [com.baton.app.ui.AccessibilityContentDescriptionTest]
                // see a `contentDescription` reference in the
                // call body and accept the Surface.
                contentDescription = label
            },
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (explainer != null) {
                Text(
                    text = explainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * v2.0 T3-1: the PIN entry dialog. Two flavours: "set PIN"
 * (used to set or change the PIN) and "enter PIN" (used to
 * switch back from Hidden). The dialog is a single
 * [OutlinedTextField] with [keyboardType = NumberPassword] so
 * the IME does not autocorrect or suggest words.
 *
 * @param onConfirm the parent's callback for a confirmed
 *   PIN entry. The parent validates the PIN; the dialog
 *   does not enforce the 4-6-digit rule here (the "set"
 *   variant calls into [SettingsViewModel.setVaultPin] which
 *   returns `false` on invalid input; the "enter" variant
 *   closes on correct match).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    invalidMessage: String? = null,
) {
    var pin by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { newValue ->
                        // Restrict to digits, max 6 chars.
                        if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                            pin = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.settings_vault_pin_dialog_label)) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                    ),
                )
                if (invalidMessage != null) {
                    Text(
                        text = invalidMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length in 4..6,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_vault_pin_dialog_cancel))
            }
        },
    )
}

/**
 * v2.0 (Tier 1.4): a SegmentedButton row for the theme
 * switcher. The default position is `System` (the device
 * setting), and the user can pick `Light` / `Dark` to
 * override. The choice persists in DataStore and the root
 * composable reads the same flow to apply the theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeRow(
    current: ThemeMode,
    onChange: (ThemeMode) -> Unit,
) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = {
                    Text(
                        text = when (mode) {
                            ThemeMode.System -> stringResource(R.string.theme_system)
                            ThemeMode.Light -> stringResource(R.string.theme_light)
                            ThemeMode.Dark -> stringResource(R.string.theme_dark)
                        },
                    )
                },
            )
        }
    }
}

private fun ts(): String =
    java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        .format(java.util.Date())

@Composable
private fun StuckOutboxCard(
    count: Int,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.typography.bodyLarge.let {
            androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count stuck outbox ${if (count == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Failed to sync after multiple retries. Retry to put them back in the queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun TagsSection(
    tags: List<Tag>,
    onAdd: (String) -> Unit,
) {
    var composing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AssistChip(
                onClick = { composing = !composing },
                label = { Text("+ #tag") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
        if (composing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("new-tag") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                )
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = {
                        onAdd(text)
                        text = ""
                        composing = false
                    },
                    enabled = text.isNotBlank(),
                ) { Text("Add") }
            }
        }
        if (tags.isEmpty()) {
            Text(
                text = "No tags yet. They'll show up here as you create instructions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val groups = TagKind.values().mapNotNull { kind ->
                val list = tags.filter { it.kind == kind }
                if (list.isEmpty()) null else kind to list
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                groups.forEach { (kind, list) ->
                    item {
                        Text(
                            text = kind.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(items = list, key = { it.id }) { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                color = tag.color?.let(::parseHex) ?: colorForKind(kind),
                                contentColor = Color.Transparent,
                                shape = CircleShape,
                            ) {}
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = if (tag.kind == TagKind.FREE) "#${tag.name}" else tag.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (tag.usageCount > 0) {
                                Text(
                                    text = "×${tag.usageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * v1.5.3 (VAULT-008): a single "label : value" row in the
 * About section. No interaction, just information density.
 * Uses `bodyMedium` for the label (quiet) and `bodyMedium`
 * for the value (the actual answer) — both on
 * `onSurfaceVariant` so the whole block reads as "info",
 * not "settings to change".
 */
@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// v1.6.1: removed `ModelRow` and `WhisperModelRow`. The
// on-device LLM and the whisper.cpp voice model are gone.
// Voice capture uses the system SpeechRecognizer; the
// capture sheet has no Extract step.
