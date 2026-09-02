package com.kaavalan.note.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaavalan.note.R
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.person.PersonProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddInstruction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text((state as? PersonDetailUiState.Loaded)?.person?.name.orEmpty())
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.person_detail_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            PersonDetailUiState.Loading -> DetailCentered(padding) {
                Text(stringResource(R.string.loading))
            }
            PersonDetailUiState.NotFound -> DetailCentered(padding) {
                Text(stringResource(R.string.person_detail_not_found))
            }
            is PersonDetailUiState.Error -> DetailCentered(padding) { Text(current.message) }
            is PersonDetailUiState.Loaded -> PersonInstructionList(
                person = current.person,
                activeInstructions = current.activeInstructions,
                completedInstructions = current.completedInstructions,
                padding = padding,
                onAddInstruction = { showAddInstruction = true },
                onMarkDone = viewModel::markDone,
                onReopen = viewModel::reopen,
            )
        }
    }

    if (showAddInstruction) {
        AddInstructionForPersonSheet(
            onSave = { text ->
                viewModel.createInstructionForThisPerson(text)
                showAddInstruction = false
            },
            onDismiss = { showAddInstruction = false },
        )
    }
}

@Composable
private fun PersonInstructionList(
    person: PersonProfile,
    activeInstructions: List<Instruction>,
    completedInstructions: List<Instruction>,
    padding: PaddingValues,
    onAddInstruction: () -> Unit,
    onMarkDone: (String) -> Unit,
    onReopen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "person-profile") {
            PersonProfileHeader(person, onAddInstruction)
        }
        item(key = "active-heading") {
            SectionTitle(stringResource(R.string.person_active_instructions))
        }
        if (activeInstructions.isEmpty()) {
            item(key = "active-empty") { EmptyInstructionSection() }
        } else {
            items(activeInstructions, key = { "active-${it.id}" }) { instruction ->
                PersonInstructionRow(
                    instruction = instruction,
                    actionLabel = stringResource(R.string.timeline_filter_done),
                    onAction = { onMarkDone(instruction.id) },
                )
            }
        }
        item(key = "completed-heading") {
            SectionTitle(stringResource(R.string.person_completed_instructions))
        }
        if (completedInstructions.isEmpty()) {
            item(key = "completed-empty") { EmptyInstructionSection() }
        } else {
            items(completedInstructions, key = { "completed-${it.id}" }) { instruction ->
                PersonInstructionRow(
                    instruction = instruction,
                    actionLabel = stringResource(R.string.person_reopen_instruction),
                    onAction = { onReopen(instruction.id) },
                )
            }
        }
    }
}

@Composable
private fun PersonProfileHeader(person: PersonProfile, onAddInstruction: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(person.name, style = MaterialTheme.typography.headlineSmall)
        val roleAndUnit = listOfNotNull(person.rankOrRole, person.unit).joinToString(" • ")
        if (roleAndUnit.isNotEmpty()) {
            Text(roleAndUnit, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        person.phone?.let { phone ->
            Text(phone, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAddInstruction, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.person_add_instruction_for, person.name))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyInstructionSection() {
    Text(
        stringResource(R.string.person_instruction_section_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun PersonInstructionRow(
    instruction: Instruction,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    instruction.actionSummary.ifBlank { instruction.title },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (instruction.priority == Priority.URGENT) {
                    Text(
                        stringResource(R.string.timeline_urgent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (instruction.rawText != instruction.actionSummary) {
                Text(
                    instruction.rawText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                when (instruction.status) {
                    Status.TO_DO -> stringResource(R.string.timeline_filter_to_do)
                    Status.WAITING -> stringResource(R.string.timeline_filter_waiting)
                    Status.DONE -> stringResource(R.string.timeline_filter_done)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInstructionForPersonSheet(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.capture_sheet_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.capture_sheet_text_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.capture_sheet_save))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun DetailCentered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
