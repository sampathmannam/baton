package com.baton.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.baton.app.R
import java.time.LocalDate

/**
 * v2.0 Tier 2 (§2.5): a per-person important-dates row, rendered
 * inside [PersonDetailScreen]. Tapping "Add date" opens a dialog
 * with the 3 default labels as chips + a free-form text field, a
 * date picker shortcut (defaults to today), and a "Repeats every
 * year" checkbox.
 */
@Composable
fun ImportantDatesRow(
    viewModel: ImportantDatesViewModel = hiltViewModel(),
) {
    val dates by viewModel.dates.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.important_date_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.important_date_add))
            }
        }
        if (dates.isEmpty()) {
            Text(
                text = stringResource(R.string.important_date_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            dates.forEach { d ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = d.label,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val date = LocalDate.ofEpochDay(d.dateEpochDay)
                            Text(
                                text = if (d.recurring) "every year - ${date}" else date.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { viewModel.delete(d.id) }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        AddImportantDateDialog(
            onAdd = { label, date, recurring ->
                viewModel.add(label, date, recurring)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun AddImportantDateDialog(
    onAdd: (String, LocalDate, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(ImportantDatesViewModel.DEFAULT_LABELS.first()) }
    var customLabel by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var menuOpen by remember { mutableStateOf(false) }
    val resolvedLabel = if (customLabel.isNotBlank()) customLabel else label
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.important_date_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Default-label chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImportantDatesViewModel.DEFAULT_LABELS.forEach { def ->
                        AssistChip(
                            onClick = { label = def; customLabel = "" },
                            label = { Text(def) },
                        )
                    }
                }
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text(stringResource(R.string.important_date_custom_label_hint)) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Date: $date",
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { menuOpen = true }) {
                        Text("Pick")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Today") },
                            onClick = { date = LocalDate.now(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Tomorrow") },
                            onClick = { date = LocalDate.now().plusDays(1); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("In a week") },
                            onClick = { date = LocalDate.now().plusDays(7); menuOpen = false },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = recurring, onCheckedChange = { recurring = it })
                    Text(stringResource(R.string.important_date_recurring))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Will save as: $resolvedLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(resolvedLabel, date, recurring) },
                enabled = resolvedLabel.isNotBlank(),
            ) {
                Text(stringResource(R.string.person_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
