package com.baton.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baton.app.R

/**
 * Bottom sheet to capture a new [com.baton.app.data.person.Person].
 * Three fields, all optional except Name. Save is disabled until Name
 * is non-blank — keeps the form ADHD-friendly (one obvious action).
 *
 * v1.5.3 fixes:
 *  - VAULT-002: Back key first hides the soft keyboard (if it's
 *    up), then closes the sheet. The previous behaviour was to
 *    skip the IME-close step and dismiss the whole sheet, which
 *    silently lost any typed data.
 *  - VAULT-004: Name / Designation / Station use
 *    `capitalization = Words` and `autoCorrect = false` so the
 *    soft keyboard doesn't insert stray characters into proper
 *    nouns (e.g. "Thanjavur" -> "Thanjavurv").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonSheet(
    onSave: (name: String, designation: String?, station: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        // v1.9.7 (UX-001): skip the partial-expanded state so the
        // sheet goes straight to fully expanded when the IME is
        // up. Without this, the sheet sits at ~50% height with
        // Save below the keyboard on 480 dpi Android 14+ devices.
        skipPartiallyExpanded = true,
    )
    var name by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    // VAULT-002: back press hides the soft keyboard first, then
    // closes the sheet. We track IME state with a flag that flips
    // when a text field receives / loses focus.
    var imeVisible by remember { mutableStateOf(false) }
    BackHandler(enabled = imeVisible) {
        keyboard?.hide()
        imeVisible = false
    }
    BackHandler(enabled = !imeVisible) {
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_add_person),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.person_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChangedCompat { imeVisible = it },
                // VAULT-004: Words capitalization (proper nouns),
                // autocorrect off.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = designation,
                onValueChange = { designation = it },
                label = { Text(stringResource(R.string.person_designation)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChangedCompat { imeVisible = it },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = station,
                onValueChange = { station = it },
                label = { Text(stringResource(R.string.person_station)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChangedCompat { imeVisible = it },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        designation.trim().ifEmpty { null },
                        station.trim().ifEmpty { null },
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.person_save))
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/**
 * v1.5.3 (VAULT-002 helper): a small wrapper around Modifier.onFocusChanged
 * that also flips the [imeVisible] flag on the calling
 * [AddPersonSheet]. Inline so the import surface stays local.
 */
@Composable
private fun Modifier.onFocusChangedCompat(onChange: (Boolean) -> Unit): Modifier =
    this.then(onFocusChanged { onChange(it.isFocused) })

// v1.9.7 (UX-001): added `.imePadding()` to the Column inside the
// ModalBottomSheet so the Save button floats above the soft keyboard
// on Android 14+ at 480 dpi (real-device test on Motorola signature
// showed Save at y=1892 hidden by IME with visible area ending at
// y=1646). Reproduces in API 34+ where the IME insets are stricter.
