package com.baton.app.features.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.features.tags.TagPicker

/**
 * Modal bottom sheet shown when the user taps the note bar. Hosts the
 * text field, the Extract button, and (once M1-T4 lands) the
 * confirmation card. M1 ships the text-field + Extract flow only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(
    viewModel: CaptureViewModel,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // M1-T6: collect calendar events from the VM and launch them
    // via the Activity context. The Channel is buffered so a config
    // change between save + launch doesn't drop the event.
    LaunchedEffect(viewModel) {
        viewModel.calendarIntents.collect { event ->
            context.startActivity(CalendarGate.toIntent(event))
        }
    }

    // When the sheet is hidden upstream, also clear VM state.
    LaunchedEffect(state.isVisible) {
        if (!state.isVisible) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = {
            // ModalBottomSheet dismisses via scrim tap, BACK key, or ESC.
            // Sync the VM so the next `openSheet()` call re-shows the sheet.
            viewModel.dismissSheet()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        CaptureSheetContent(
            state = state,
            onTextChanged = viewModel::onTextChanged,
            onExtract = viewModel::onExtract,
            onConfirm = viewModel::onConfirm,
            onClose = {
                viewModel.dismissSheet()
            },
            onAddToCalendarChange = viewModel::onAddToCalendarChanged,
            onProposalPersonChange = viewModel::onProposalPersonChange,
            onProposalActionChange = viewModel::onProposalActionChange,
            onProposalTextChange = viewModel::onProposalTextChange,
            onTagToggled = viewModel::onTagToggled,
            onAddFreeTag = viewModel::onAddFreeTag,
        )
    }
}

@Composable
private fun CaptureSheetContent(
    state: CaptureUiState,
    onTextChanged: (String) -> Unit,
    onExtract: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    onAddToCalendarChange: (Boolean) -> Unit = { },
    onProposalPersonChange: (String) -> Unit = { },
    onProposalActionChange: (String) -> Unit = { },
    onProposalTextChange: (String) -> Unit = { },
    onTagToggled: (String) -> Unit = { },
    onAddFreeTag: (String) -> Unit = { },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(onClose = onClose)
        CaptureTextField(
            text = state.text,
            isExtracting = state.isExtracting,
            onTextChanged = onTextChanged,
        )
        if (state.error != null) {
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.proposal != null) {
            ConfirmationCard(
                proposal = state.proposal!!,
                addToCalendar = state.addToCalendar,
                onPersonChange = onProposalPersonChange,
                onActionChange = onProposalActionChange,
                onInstructionTextChange = onProposalTextChange,
                onAddToCalendarChange = onAddToCalendarChange,
            )
        }
        // M3-T7: tag picker sits below the confirmation card. The
        // user picks from the existing taxonomy or authors a free-
        // form `#tag` on the fly. The state `availableTags` is
        // observed from the VM's collect; `selectedTagIds` is the
        // user's pre-save selection.
        TagPicker(
            available = state.availableTags,
            selected = state.selectedTagIds,
            onToggle = onTagToggled,
            onAddFree = onAddFreeTag,
        )
        PrimaryAction(
            isExtracting = state.isExtracting,
            canExtract = state.canExtract,
            canConfirm = state.canConfirm,
            onExtract = onExtract,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.capture_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.capture_sheet_close),
            )
        }
    }
}

@Composable
private fun CaptureTextField(
    text: String,
    isExtracting: Boolean,
    onTextChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChanged,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp, max = 200.dp)
            .semantics { contentDescription = "Capture note text" },
        label = { Text(stringResource(R.string.capture_sheet_text_label)) },
        placeholder = { Text(stringResource(R.string.capture_sheet_text_placeholder)) },
        shape = RoundedCornerShape(12.dp),
        enabled = !isExtracting,
    )
}

@Composable
private fun PrimaryAction(
    isExtracting: Boolean,
    canExtract: Boolean,
    canConfirm: Boolean,
    onExtract: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isExtracting -> CircularProgressIndicator()
            canConfirm -> Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.capture_sheet_confirm))
            }
            else -> Button(
                onClick = onExtract,
                enabled = canExtract,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.capture_sheet_extract))
            }
        }
    }
}
