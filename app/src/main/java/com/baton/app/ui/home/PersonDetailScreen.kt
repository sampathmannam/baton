package com.baton.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.Status
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * M3-T6: Person detail screen. Shows the timeline of all
 * instructions for one person — both incoming (they gave it to
 * the user) and outgoing (the user gave it to them). Sorted by
 * `capturedAt DESC` (most recent first) so the most recent
 * exchange is at the top.
 *
 * **No navigation graph yet.** For M3 the detail screen is
 * reached by tapping a row on the Home tab; the
 * [com.baton.app.ui.home.HomeScreen] composable manages a
 * `selectedPersonId` state that flips when a row is tapped and
 * triggers this composable. When M3.5 lands a real nav graph
 * (Today tab, deep links) the wiring moves into the
 * `composable("person/{id}")` entry — the ViewModel contract
 * here (`observeForPerson(id)`) is the same.
 *
 * **Status chip.** Each instruction row carries a small label
 * for its status (`OPEN`, `DONE`, `CARRIED_OVER`, etc.). The
 * design rule (spec §3.3) is "no shame language": `CARRIED_OVER`
 * is used in place of `OVERDUE`; the chip colour is the
 * `secondaryContainer` (calm) when open, `surfaceVariant` (grey)
 * when closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    personId: String,
    onBack: () -> Unit,
    onOpenLinkedPerson: (String) -> Unit = {},
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    // Hilt's SavedStateHandle lets the VM pick up the `personId`
    // nav arg without us passing it manually. The VM exposes
    // `state` as a Flow that the composable collects.
    val state by viewModel.state.collectAsStateWithLifecycle()
    var nudgeTarget by remember { mutableStateOf<Instruction?>(null) }
    var dropTarget by remember { mutableStateOf<Instruction?>(null) }
    var sensitiveToggleId by remember { mutableStateOf<String?>(null) }
    var showPersonSensitive by remember { mutableStateOf(false) }
    // v1.5.3 (VAULT-003): local "Add instruction" sheet, pre-attributed
    // to this person. Avoids making the user back out to the home
    // tab to capture an instruction for the person they're looking at.
    var showAddInstruction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? PersonDetailUiState.Loaded)?.person?.name
                            ?: stringResource(R.string.person_detail_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.person_detail_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            PersonDetailUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is PersonDetailUiState.Loaded -> PersonTimeline(
                person = s.person,
                instructions = s.instructions,
                padding = padding,
                onNudge = { ins -> nudgeTarget = ins },
                onMarkDone = { ins -> viewModel.markDone(ins.id) },
                onReopen = { ins -> viewModel.reopen(ins.id) },
                onRequestDrop = { ins -> dropTarget = ins },
                onRequestInstructionSensitive = { ins -> sensitiveToggleId = ins.id },
                onOpenPersonSensitive = { showPersonSensitive = true },
                onAddInstruction = { showAddInstruction = true },
                onOpenLinkedPerson = onOpenLinkedPerson,
            )
        }
    }

    val target = nudgeTarget
    val loaded = (state as? PersonDetailUiState.Loaded)
    if (target != null && loaded != null) {
        NudgeSheet(
            instruction = target,
            person = loaded.person,
            onDismiss = { nudgeTarget = null },
        )
    }

    val dropIns = dropTarget
    if (dropIns != null) {
        DropDialog(
            instructionTitle = dropIns.title,
            onConfirm = { reason ->
                viewModel.markDropped(dropIns.id, reason)
                dropTarget = null
            },
            onDismiss = { dropTarget = null },
        )
    }

    val insId = sensitiveToggleId
    val insForSensitive = loaded?.instructions?.firstOrNull { it.id == insId }
    if (insForSensitive != null) {
        InstructionSensitiveDialog(
            instruction = insForSensitive,
            onConfirm = { newValue ->
                viewModel.setInstructionSensitive(insForSensitive.id, newValue)
                sensitiveToggleId = null
            },
            onDismiss = { sensitiveToggleId = null },
        )
    }

    if (showPersonSensitive && loaded != null) {
        PersonSensitiveDialog(
            person = loaded.person,
            onConfirm = { newValue ->
                viewModel.setPersonSensitive(loaded.person.id, newValue)
                showPersonSensitive = false
            },
            onDismiss = { showPersonSensitive = false },
        )
    }

    if (showAddInstruction && loaded != null) {
        AddInstructionForPersonSheet(
            personName = loaded.person.name,
            onSave = { text ->
                viewModel.createInstructionForThisPerson(text)
                showAddInstruction = false
            },
            onDismiss = { showAddInstruction = false },
        )
    }
}

/**
 * v1.5.3 (VAULT-003): a small bottom sheet that captures an
 * instruction pre-attributed to the person the user is
 * looking at. The text field uses `capitalization = Words`
 * and no autocorrect so proper-noun content (names, places)
 * isn't mangled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInstructionForPersonSheet(
    personName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "New instruction for $personName",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Capture what you want $personName to do. The note will be attributed to them and show up on their timeline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                label = { Text("Note") },
                // VAULT-004: disable autocorrect + sentence caps
                // for proper-noun content (names, designations).
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(text) },
                    enabled = text.trim().isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PersonTimeline(
    person: com.baton.app.data.person.Person,
    instructions: List<Instruction>,
    padding: PaddingValues,
    onNudge: (Instruction) -> Unit = {},
    onMarkDone: (Instruction) -> Unit = {},
    onReopen: (Instruction) -> Unit = {},
    onRequestDrop: (Instruction) -> Unit = {},
    onRequestInstructionSensitive: (Instruction) -> Unit = {},
    onOpenPersonSensitive: () -> Unit = {},
    onAddInstruction: () -> Unit = {},
    onOpenLinkedPerson: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        // v1.6.3: 16dp horizontal contentPadding so the
        // cards no longer need their own horizontal padding
        // (full-width clickable hit targets).
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PersonHeader(
                person = person,
                openInstructionCount = instructions.count { it.status == com.baton.app.data.instructions.Status.OPEN },
                onAddInstruction = onAddInstruction,
                onOpenSensitive = onOpenPersonSensitive,
            )
        }
        // v2.0 Tier 2 (§2.12): person-to-person links.
        item { PersonLinksRow(onOpenPerson = onOpenLinkedPerson) }
        // v2.0 Tier 2 (§2.5): important dates per person.
        item { ImportantDatesRow() }
        if (instructions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.person_detail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(items = instructions, key = { it.id }) { ins ->
                InstructionRow(
                    instruction = ins,
                    onNudge = onNudge,
                    onMarkDone = { onMarkDone(ins) },
                    onReopen = { onReopen(ins) },
                    onRequestDrop = { onRequestDrop(ins) },
                    onRequestSensitive = { onRequestInstructionSensitive(ins) },
                )
            }
        }
        // v1.6.3: removed the trailing 80dp Spacer. The
        // LazyColumn's contentPadding(bottom) is the bottom
        // buffer; nothing else lives below the list on this
        // screen.
    }
}

/**
 * v1.1: the person header now includes a single "Sensitive" toggle
 * (spec §13). The row underneath is a quiet `TextButton` (not a
 * `Switch` — switches are too prominent for an ADHD-friendly design
 * and would suggest the row is currently editable). The button label
 * is "Mark as sensitive" / "Keep on this device" depending on state.
 */
@Composable
private fun PersonHeader(
    person: com.baton.app.data.person.Person,
    openInstructionCount: Int,
    onOpenSensitive: () -> Unit,
    onAddInstruction: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // v1.6.3: no horizontal padding here; the
            // LazyColumn's contentPadding handles it.
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = person.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val subtitle = listOfNotNull(person.designation, person.station).joinToString(" • ")
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        // v1.5.3 (VAULT-003): primary "Add instruction" button —
        // the user is on this screen because they want to do
        // something involving this person. Don't make them
        // navigate away to capture.
        // v1.6.3: switched from filled Button to OutlinedButton
        // — the filled variant was the loudest element on the
        // screen and pulled attention away from the timeline.
        // OutlinedButton keeps the action discoverable but
        // lets the instructions read as the primary content.
        androidx.compose.material3.OutlinedButton(
            onClick = onAddInstruction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Add instruction for ${person.name}")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (person.isSensitive) {
                // v1.5.1: vault mode — there's no Supabase sync either
                // way. Sensitive = never leaves the device, even if
                // cloud sync is re-enabled later.
                "Stays on this phone, never backed up."
            } else {
                // v1.5.1: vault mode copy. Replaces the pre-vault
                // "Syncs to Supabase..." text that lied to the user
                // (VAULT-001).
                "Stays on this phone."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenSensitive) {
            Text(if (person.isSensitive) "Remove sensitive flag" else "Mark as sensitive")
        }
        Spacer(Modifier.height(8.dp))
        // v1.2 root-cause fix (F-03 in the UI audit): the previous
        // version hardcoded `/* placeholder */ 0` here, so the user
        // always saw "0 instructions" no matter how many were
        // actually open. We now take the live count from the
        // ViewModel and only render the line when the count is
        // non-zero — the spec says "no counts shown when 0".
        if (openInstructionCount > 0) {
            Text(
                text = stringResource(
                    R.string.person_detail_timeline_count,
                    openInstructionCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstructionRow(
    instruction: Instruction,
    onNudge: (Instruction) -> Unit = {},
    onMarkDone: () -> Unit = {},
    onReopen: () -> Unit = {},
    onRequestDrop: () -> Unit = {},
    onRequestSensitive: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        // v1.6.3: no horizontal padding here; the
        // LazyColumn's contentPadding handles it.
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = instruction.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(instruction.status)
            }
            Text(
                text = instruction.rawText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatCapturedAt(instruction.capturedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (instruction.completedAt != null) {
                Text(
                    text = "Done " + formatCapturedAt(instruction.completedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (instruction.droppedReason != null) {
                Text(
                    text = "Dropped: ${instruction.droppedReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InstructionActions(
                instruction = instruction,
                onNudge = { onNudge(instruction) },
                onMarkDone = onMarkDone,
                onReopen = onReopen,
                onRequestDrop = onRequestDrop,
                onRequestSensitive = onRequestSensitive,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InstructionActions(
    instruction: Instruction,
    onNudge: () -> Unit,
    onMarkDone: () -> Unit,
    onReopen: () -> Unit,
    onRequestDrop: () -> Unit,
    onRequestSensitive: () -> Unit,
) {
    val isClosed = instruction.status == Status.DONE || instruction.status == Status.DROPPED
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (isClosed) {
            TextButton(onClick = onReopen) { Text("Re-open") }
        } else {
            if (instruction.direction == com.baton.app.data.instructions.Direction.OUTGOING) {
                TextButton(onClick = onNudge) { Text("Draft nudge") }
            }
            TextButton(onClick = onMarkDone) { Text("Mark done") }
            TextButton(onClick = onRequestDrop) { Text("Drop") }
        }
        TextButton(onClick = onRequestSensitive) {
            Text(if (instruction.isSensitive) "Make syncable" else "Mark sensitive")
        }
    }
}

/**
 * v1.1: drop dialog. Captures an optional `reason` and confirms
 * before calling the VM. Reason is preserved on the row (server-side
 * too) so the user can review what was dropped in a future conflict
 * UI.
 */
@Composable
private fun DropDialog(
    instructionTitle: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Drop instruction") },
        text = {
            Column {
                Text("\"$instructionTitle\"")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = { Text("Reason (optional)") },
                    singleLine = false,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.ifBlank { null }) }) { Text("Drop") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * v1.1: confirm dialog before flipping the row's `is_sensitive`
 * flag. Toggling on is the more impactful action (the row never
 * reaches the server again) so it gets a confirmation; toggling
 * off is fine to do in one tap.
 */
@Composable
private fun InstructionSensitiveDialog(
    instruction: Instruction,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val newValue = !instruction.isSensitive
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (newValue) "Mark sensitive" else "Make syncable") },
        text = {
            Text(
                if (newValue) {
                    // v1.5.1: vault-mode copy. Sensitive = never leaves
                    // the device, even if cloud sync is re-enabled later.
                    "This instruction will stay on this phone only."
                } else {
                    // v1.5.1: vault-mode copy. The unsensitive path
                    // doesn't actually re-enable Supabase sync in
                    // vault mode — instructions still stay local.
                    "This instruction will start syncing to the cloud " +
                        "if cloud sync is on for this device."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newValue) }) {
                Text(if (newValue) "Mark sensitive" else "Make syncable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * v1.1: confirm dialog before flipping the person's `is_sensitive`
 * flag. Same semantics as [InstructionSensitiveDialog] but for the
 * whole person (which also affects all of their instructions on
 * the next sync).
 */
@Composable
private fun PersonSensitiveDialog(
    person: com.baton.app.data.person.Person,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val newValue = !person.isSensitive
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (newValue) "Mark sensitive" else "Remove sensitive flag") },
        text = {
            Text(
                if (newValue) {
                    // v1.5.1: vault-mode copy. Sensitive = never leaves
                    // the device, even if cloud sync is re-enabled later.
                    "${person.name} and their instructions will stay on " +
                        "this phone only."
                } else {
                    // v1.5.1: vault-mode copy.
                    "${person.name} will be available for cloud sync " +
                        "if cloud sync is on for this device."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newValue) }) {
                Text(if (newValue) "Mark sensitive" else "Remove flag")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun StatusChip(status: Status) {
    val (bg, fg) = when (status) {
        Status.OPEN, Status.IN_PROGRESS, Status.ACK_PENDING, Status.WAITING_ON_OTHER ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        Status.DONE ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        Status.CARRIED_OVER ->
            // "Carried over, not overdue" — uses the calm
            // tertiary tone, not error/red.
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Status.DROPPED ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = status.name.replace('_', ' ').lowercase()
        .replaceFirstChar { it.uppercase() }
    // v1.3 (F-19): the chip is decorative; the rendered text is
    // already the status name. We add a `Status: …` prefix via
    // semantics so TalkBack announces the role (this is a status
    // label) and not just the bare word "Open" or "Carried over".
    val statusDesc = stringResource(R.string.a11y_status_chip, statusLabel)
    androidx.compose.material3.Surface(
        color = bg,
        contentColor = fg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        modifier = Modifier.semantics { contentDescription = statusDesc },
    ) {
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun formatCapturedAt(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        val formatter = DateTimeFormatter
            .ofPattern("d MMM, HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        iso
    }
}
