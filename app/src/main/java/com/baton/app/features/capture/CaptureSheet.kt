package com.baton.app.features.capture

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.features.tags.TagPicker

/**
 * Modal bottom sheet shown when the user taps the note bar.
 *
 * v1.6.1: the on-device LLM is gone. The sheet is now a
 * single-purpose "type or speak a note, save it" surface. The
 * state machine collapses to:
 *
 *   - open sheet -> text field is empty
 *   - user types (or speaks via [VoiceCaptureService] which
 *     uses the system `SpeechRecognizer`)
 *   - user taps Save -> note persists, sheet closes
 *
 * There is no Extract button, no Confirmation card, no
 * model-download card, no LLM-unavailable card. The
 * [NoPeopleCard] is the only state card (preserved from
 * v1.4 PHONE-FINDING-8) because the user still needs a
 * person to attribute the note to.
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
    val hasPeople by viewModel.hasPeople.collectAsStateWithLifecycle()
    // Tier 0.4: collect the process-wide voice-recording
    // state. When `isRecording == true` the sheet renders an
    // in-app "Stop" button above the primary action; tapping
    // it calls `context.stopService(...)` (the same end
    // state as tapping the notification's Stop action).
    val isVoiceRecording by VoiceCaptureState.isRecording.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // M1-T6: collect calendar events from the VM and launch
    // them via the Activity context. The Channel is buffered
    // so a config change between save + launch doesn't drop
    // the event.
    LaunchedEffect(viewModel) {
        viewModel.calendarIntents.collect { event ->
            context.startActivity(CalendarGate.toIntent(event))
        }
    }

    LaunchedEffect(state.isVisible) {
        if (!state.isVisible) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.dismissSheet()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        CaptureSheetContent(
            state = state,
            hasPeople = hasPeople,
            isVoiceRecording = isVoiceRecording,
            onStopVoice = {
                val svc = Intent(context, VoiceCaptureService::class.java).apply {
                    action = VoiceCaptureService.ACTION_STOP
                }
                context.startService(svc)
            },
            onTextChanged = viewModel::onTextChanged,
            onClose = { viewModel.dismissSheet() },
            onAddToCalendarChange = viewModel::onAddToCalendarChanged,
            onTagToggled = viewModel::onTagToggled,
            onAddFreeTag = viewModel::onAddFreeTag,
            onSaveRaw = viewModel::onSaveRaw,
            onOpenAddPerson = onOpenAddPerson,
        )
    }
}

@Composable
private fun CaptureSheetContent(
    state: CaptureUiState,
    hasPeople: Boolean,
    // Tier 0.4: the in-app voice stop button. Rendered
    // above the Save button when `isVoiceRecording == true`.
    isVoiceRecording: Boolean = false,
    onStopVoice: () -> Unit = {},
    onTextChanged: (String) -> Unit,
    onClose: () -> Unit,
    onAddToCalendarChange: (Boolean) -> Unit = { },
    onTagToggled: (String) -> Unit = { },
    onAddFreeTag: (String) -> Unit = { },
    onSaveRaw: () -> Unit = { },
    onOpenAddPerson: () -> Unit = {},
) {
    // v1.5.5 (QA): the sheet content can overflow the
    // visible height when the NoPeopleCard + a long
    // TextField + the TagPicker + a Save button are all
    // visible at the same time. Without `verticalScroll`
    // the buttons at the bottom are pushed below the
    // screen edge. The scroll keeps the existing top-down
    // layout. The keyboard-aware `imePadding` on the
    // primary-action column still keeps the buttons
    // above the soft keyboard.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(onClose = onClose)
        // v1.4 (PHONE-FINDING-8): brand-new users have no
        // people, so the capture sheet is unusable. The
        // inline surfaceVariant card sits at the top with
        // the exact next action ("Add person"). The card
        // is non-dismissive (X / scrim / BACK still close
        // the sheet as normal) -- the user can keep typing,
        // just not save, until they've added a person. The
        // "Add person" button on the card calls
        // [onOpenAddPerson], the same entry point the Home
        // screen uses. The surfaceVariant colour is
        // neutral grey (per the no-red rule).
        if (!hasPeople) {
            NoPeopleCard(onOpenAddPerson = onOpenAddPerson)
        }
        CaptureTextField(
            text = state.text,
            isSaving = state.isSaving,
            onTextChanged = onTextChanged,
        )
        if (state.error != null) {
            // v1.4 (PHONE-FINDING-7): the error is rendered
            // in `onSurfaceVariant` (a neutral grey) -- NEVER
            // `colorScheme.error` (bright red), which would
            // be a spec §1 violation. The icon is
            // `Icons.Outlined.Info` for the standard "I have
            // something to tell you" cue.
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
        // M3-T7: tag picker sits below the text field. The
        // user picks from the existing taxonomy or authors a
        // free-form `#tag` on the fly. The state
        // `availableTags` is observed from the VM's collect;
        // `selectedTagIds` is the user's pre-save selection.
        TagPicker(
            available = state.availableTags,
            selected = state.selectedTagIds,
            onToggle = onTagToggled,
            onAddFree = onAddFreeTag,
        )
        // M1-T6: the "Add to calendar" toggle. Lives next
        // to the primary action so the user sees it before
        // tapping Save. Defaults to off. The intent fires
        // on Save (not on extract) so the user can attach
        // a calendar event to any free-form note.
        AddToCalendarRow(
            addToCalendar = state.addToCalendar,
            onAddToCalendarChange = onAddToCalendarChange,
        )
        PrimaryAction(
            isSaving = state.isSaving,
            canSaveRaw = state.canSaveRaw,
            hasPeople = hasPeople,
            isVoiceRecording = isVoiceRecording,
            onStopVoice = onStopVoice,
            onSaveRaw = onSaveRaw,
        )
    }
}

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    Row(
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
    isSaving: Boolean,
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
        enabled = !isSaving,
    )
}

@Composable
private fun AddToCalendarRow(
    addToCalendar: Boolean,
    onAddToCalendarChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Add to calendar" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.capture_sheet_add_to_calendar),
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.material3.Switch(
            checked = addToCalendar,
            onCheckedChange = onAddToCalendarChange,
        )
    }
}

@Composable
private fun PrimaryAction(
    isSaving: Boolean,
    canSaveRaw: Boolean,
    hasPeople: Boolean,
    // Tier 0.4: the in-app stop-voice affordance. When
    // `isVoiceRecording == true` the action column renders
    // a "Stop voice" button above the Save button.
    isVoiceRecording: Boolean = false,
    onStopVoice: () -> Unit = {},
    onSaveRaw: () -> Unit,
) {
    // v1.4 (PHONE-FINDING-9): respect both the soft
    // keyboard (ime) and the system navigation/gesture bar
    // (navigationBars). On 1264x2780 devices the Save
    // button was being clipped by the gesture bar. The
    // `union` covers the case where only the gesture bar
    // is present (no keyboard) and the case where the
    // keyboard is up.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Tier 0.4: in-app stop-voice button. Renders
        // above the Save button so the user can reach it
        // without scrolling. The button is a `Button`
        // (not `OutlinedButton`) so it reads as an active
        // affordance; the colour is `primary` /
        // `onPrimary` (no red, per the no-shame spec
        // rule). When the recording is not in progress,
        // the entire row is hidden.
        if (isVoiceRecording) {
            Button(
                onClick = onStopVoice,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.tier0_voice_in_app_stop),
                )
            }
        }
        // v1.6.1: the single Save button. The previous
        // dual-button (Extract + Save as text) is gone --
        // with no LLM there is no extraction step, so a
        // single primary action is the right shape. The
        // button is hard-disabled when:
        //   - the text is blank
        //   - the user has no people (the inline
        //     NoPeopleCard above is the visible reason)
        //   - a save is already in flight
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = onSaveRaw,
                enabled = canSaveRaw && hasPeople && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.capture_sheet_save))
            }
        }
    }
}

/**
 * v1.4 (PHONE-FINDING-8): the inline "you need a person
 * first" card. Renders at the top of the capture sheet when
 * [CaptureViewModel.hasPeople] is `false`. The card uses
 * `surfaceVariant` (a neutral grey, not the red
 * `errorContainer`) and carries a single primary-coloured
 * "Add person" button that fires [onOpenAddPerson]. The
 * text is short and action-oriented per the no-shame spec
 * rule.
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
