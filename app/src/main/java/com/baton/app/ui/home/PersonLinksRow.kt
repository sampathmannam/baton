package com.baton.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
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

/**
 * v2.0 Tier 2 (§2.12): the person-links chip row in
 * [PersonDetailScreen]. Each chip is a directed edge; tapping
 * the X removes it. Tapping "Add link" opens a picker that
 * combines the 4 default relations with a free-form text field
 * and a chooser for the target person.
 */
@Composable
fun PersonLinksRow(
    onOpenPerson: (String) -> Unit,
    viewModel: PersonLinksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                text = stringResource(R.string.person_links_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.person_links_add))
            }
        }
        if (state.links.isEmpty()) {
            Text(
                text = stringResource(R.string.person_link_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.links.size) { idx ->
                    val link = state.links[idx]
                    AssistChip(
                        onClick = { onOpenPerson(link.targetId) },
                        label = {
                            val arrow = if (link.isOutgoing) "->" else "<-"
                            Text("$arrow ${link.targetName} - ${link.relation}")
                        },
                    )
                }
            }
        }
    }
    if (showAdd) {
        AddLinkDialog(
            people = state.people,
            onAdd = { target, relation ->
                viewModel.add(target, relation)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLinkDialog(
    people: List<com.baton.app.data.local.entities.PersonEntity>,
    onAdd: (targetId: String, relation: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var relation by remember { mutableStateOf(PersonLinksViewModel.DEFAULT_RELATIONS.first()) }
    var customRelation by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf(people.firstOrNull()?.id ?: "") }
    val resolvedRelation = if (customRelation.isNotBlank()) customRelation else relation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_links_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PersonLinksViewModel.DEFAULT_RELATIONS.forEach { rel ->
                        AssistChip(
                            onClick = { relation = rel; customRelation = "" },
                            label = { Text(rel) },
                        )
                    }
                }
                OutlinedTextField(
                    value = customRelation,
                    onValueChange = { customRelation = it },
                    label = { Text(stringResource(R.string.person_link_custom_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.padding(2.dp))
                Text(
                    text = stringResource(R.string.person_link_choose_person),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (people.isEmpty()) {
                    Text(
                        text = stringResource(R.string.links_add_person_first),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(people.size) { idx ->
                            val p = people[idx]
                            AssistChip(
                                onClick = { targetId = p.id },
                                label = { Text(p.name) },
                                leadingIcon = if (targetId == p.id) {
                                    { Text("-") }
                                } else null,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(targetId, resolvedRelation) },
                enabled = resolvedRelation.isNotBlank() && targetId.isNotBlank(),
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
