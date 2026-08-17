package com.baton.app.features.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
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
import com.baton.app.ai.llama.ModelState
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
    onOpenAddPerson: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // v1.4 (PHONE-FINDING-8): the no-people state is rendered as
    // an inline card inside the sheet, with a primary-coloured
    // "Add person" button. The VM's [hasPeople] StateFlow is the
    // source of truth — the UI does not duplicate the empty-state
    // check, it just collects the flow.
    val hasPeople by viewModel.hasPeople.collectAsStateWithLifecycle()
    // v1.5.4: the model lifecycle state drives the
    // `ModelNotReadyCard` below. Sharing the same StateFlow
    // means the card flips between "Download model" /
    // "Downloading… 47%" / "Retry" / hidden as the download
    // progresses, without the VM having to mirror the flow.
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
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
            hasPeople = hasPeople,
            modelState = modelState,
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
            onSaveRaw = viewModel::onSaveRaw,
            onDownloadModel = viewModel::downloadModel,
            onOpenAddPerson = onOpenAddPerson,
        )
    }
}

@Composable
private fun CaptureSheetContent(
    state: CaptureUiState,
    hasPeople: Boolean,
    modelState: ModelState,
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
    onSaveRaw: () -> Unit = { },
    onDownloadModel: () -> Unit = { },
    onOpenAddPerson: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(onClose = onClose)
        // v1.4 (PHONE-FINDING-8): brand-new users have no people,
        // so the capture sheet is unusable. The previous behaviour
        // (a vague "Could not save note. Try again." on the rare
        // save that actually fired) was a dead end. The new flow:
        // an inline surfaceVariant card sits at the top with the
        // exact next action ("Add person"). The card is non-
        // dismissive (X / scrim / BACK still close the sheet as
        // normal) — the user can keep typing, just not save, until
        // they've added a person. The "Add person" button on the
        // card calls [onOpenAddPerson], the same entry point the
        // Home screen uses, so the user lands in the same AddPerson
        // form. The surfaceVariant colour is neutral grey (per the
        // no-red rule); no shame framing.
        if (!hasPeople) {
            NoPeopleCard(onOpenAddPerson = onOpenAddPerson)
        }
        // v1.5.4: the model lifecycle card. The user opens the
        // capture sheet on a fresh install (no model file on
        // disk) and the v1.3 path would show "No connection"
        // because the legacy `downloadModel()` flow fails the
        // SHA-256 check (the bundled manifest's hash doesn't
        // match the upstream mirror's file). The new path:
        // explicit `ModelNotReadyCard` with a "Download model"
        // button that the user taps to start the download
        // progress flow. The card re-renders as
        // `DownloadingSection` while bytes flow, hides on
        // `Ready`, and shows a "Retry" button on `Failed`. The
        // card sits BELOW the no-people card (people is the
        // first blocker) but ABOVE the text field so the user
        // sees the action before they start typing.
        if (modelState !is ModelState.Ready) {
            ModelNotReadyCard(
                modelState = modelState,
                onDownload = onDownloadModel,
            )
        }
        CaptureTextField(
            text = state.text,
            isExtracting = state.isExtracting,
            onTextChanged = onTextChanged,
        )
        if (state.error != null) {
            // v1.4 (PHONE-FINDING-7): the error is rendered in
            // `onSurfaceVariant` (a neutral grey) — NEVER
            // `colorScheme.error` (bright red), which would be a
            // spec §1 violation. The icon is `Icons.Outlined.Info`
            // for the standard "I have something to tell you" cue.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            // v1.4 (PHONE-FINDING-8): hard-disable the Extract
            // (and the "Save as text" fallback below) when the
            // user has no people. The button is still rendered —
            // hiding it entirely would be confusing — but tapping
            // it is a no-op. The inline NoPeopleCard above is the
            // visible cue.
            // v1.5.4: same hard-disable applies when the model
            // is not yet downloaded — Extract requires the LLM
            // and would no-op silently. The inline
            // `ModelNotReadyCard` above is the visible cue.
            hasPeople = hasPeople,
            modelReady = modelState is ModelState.Ready,
            onExtract = onExtract,
            onConfirm = onConfirm,
            onSaveRaw = onSaveRaw,
        )
    }
}

/**
 * v1.4 (PHONE-FINDING-8): the inline "you need a person first"
 * card. Renders at the top of the capture sheet when
 * [CaptureViewModel.hasPeople] is `false`. The card uses
 * `surfaceVariant` (a neutral grey, not the red `errorContainer`)
 * and carries a single primary-coloured "Add person" button that
 * fires [onOpenAddPerson]. The text is short and action-oriented
 * per the no-shame spec rule.
 */
@Composable
private fun NoPeopleCard(onOpenAddPerson: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.capture_needs_person_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onOpenAddPerson,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Add person" },
            ) {
                Text(stringResource(R.string.home_add_person))
            }
        }
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
    hasPeople: Boolean,
    modelReady: Boolean,
    onExtract: () -> Unit,
    onConfirm: () -> Unit,
    onSaveRaw: () -> Unit,
) {
    // v1.4 (PHONE-FINDING-9): respect both the soft keyboard (ime)
    // and the system navigation/gesture bar (navigationBars). On
    // 1264x2780 devices the secondary "Save as text (skip
    // extraction)" button was being clipped by the gesture bar. The
    // `union` covers the case where only the gesture bar is present
    // (no keyboard) and the case where the keyboard is up.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    // v1.4 (PHONE-FINDING-8): Extract is disabled when
                    // the user has no people. The inline NoPeopleCard
                    // above is the visible reason. Power users with
                    // people keep the v1.3 behaviour.
                    // v1.5.4: also disabled when the on-device model
                    // is not yet downloaded. The `ModelNotReadyCard`
                    // above is the visible cue; without this guard
                    // the user would tap a blue button that no-ops
                    // and the `onExtract` path would silently
                    // return null.
                    enabled = canExtract && hasPeople && modelReady,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.capture_sheet_extract))
                }
            }
        }
        // v1.1: spec §12 — "LLM extraction fails → raw text saved as-is".
        // The user can always skip extraction and save the text verbatim.
        // Shown only when the LLM hasn't produced a proposal yet, so it
        // doesn't compete with the primary "Save" button. v1.4
        // (PHONE-FINDING-8): hidden when [hasPeople] is false for the
        // same reason Extract is disabled — a "Save as text" without a
        // person would still fail the same way.
        if (!isExtracting && !canConfirm && canExtract && hasPeople) {
            androidx.compose.material3.OutlinedButton(
                onClick = onSaveRaw,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save as text (skip extraction)")
            }
        }
    }
}

/**
 * v1.5.4: the inline "model not ready" card. Renders above the
 * text field when [ModelManager.state] is anything other than
 * [ModelState.Ready]. Three affordances:
 *
 *  - [ModelState.NotStarted] → "Download model" button.
 *  - [ModelState.Downloading] → `LinearProgressIndicator` with
 *    the live `progress` value (0..1 or `-1f` if the server
 *    didn't advertise a `Content-Length`).
 *  - [ModelState.Failed] → a neutral "Reason" line + "Retry"
 *    button (re-invokes [onDownload]).
 *
 * Hidden entirely on `Ready` so the user sees a clean sheet
 * once the model is on disk. The card is the visible cue
 * backing the `enabled = canExtract && hasPeople && modelReady`
 * gate on the Extract button below; the user sees the
 * "what next" before they tap a dead button.
 *
 * Colours are `surfaceVariant` / `onSurfaceVariant` per the
 * no-red rule — same neutral grey as the `NoPeopleCard`. The
 * icon is `Icons.Outlined.Info` (matches the inline error
 * styling on the rest of the sheet).
 */
@Composable
private fun ModelNotReadyCard(
    modelState: ModelState,
    onDownload: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (modelState) {
                is ModelState.NotStarted -> {
                    Text(
                        text = "On-device AI is not downloaded yet. Tap below to fetch the model.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onDownload,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Download on-device model" },
                    ) {
                        Text(stringResource(R.string.model_download_button))
                    }
                }
                is ModelState.Downloading -> {
                    val displayProgress = if (modelState.progress < 0f) 0f
                        else modelState.progress.coerceIn(0f, 1f)
                    val percent = (displayProgress * 100).toInt()
                    Text(
                        text = "Downloading on-device model.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { displayProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is ModelState.Failed -> {
                    Text(
                        text = "Model download didn't finish: ${modelState.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onDownload,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Retry model download" },
                    ) {
                        Text(stringResource(R.string.model_download_retry))
                    }
                }
                is ModelState.Ready -> {
                    // Defensive: the parent composable already hides
                    // this card on Ready. Kept as a no-op branch
                    // so the `when` is exhaustive.
                }
            }
        }
    }
}
