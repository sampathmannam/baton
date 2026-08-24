package com.kaavalan.note.ui.hierarchy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.person.Person
import com.kaavalan.note.ui.home.TagCount

@Composable
fun HomeHierarchySections(outgoing: List<Instruction>, incoming: List<Instruction>, popularTags: List<TagCount>, onTagClick: (String) -> Unit, onInstructionClick: (Instruction) -> Unit) {
    if (outgoing.isEmpty() && incoming.isEmpty() && popularTags.isEmpty()) return
    if (popularTags.isNotEmpty()) {
        item { Text(stringResource(R.string.hierarchy_section_tags), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(popularTags) { tag ->
                    AssistChip(onClick = { onTagClick(tag.tagId) }, label = { Text("#${tag.name}") }, trailingIcon = { Text(stringResource(R.string.hierarchy_tag_chip_count, tag.count), style = MaterialTheme.typography.labelSmall) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (outgoing.isNotEmpty()) {
        item { Text(stringResource(R.string.hierarchy_section_outbox), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        items(outgoing) { ins -> InstructionRow(ins, ins.audience?.label ?: "Single person") { onInstructionClick(ins) } }
    }
    if (incoming.isNotEmpty()) {
        item { Text(stringResource(R.string.hierarchy_section_inbox), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        items(incoming) { ins -> InstructionRow(ins, ins.audience?.label ?: "Direct") { onInstructionClick(ins) } }
    }
    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
}

@Composable private fun InstructionRow(ins: Instruction, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open instruction", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(ins.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ins.dueAtMs != null) DueLabel(ins.dueAtMs)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun HomeHierarchyAwarePersonList(persons: List<Person>, openCountByPersonId: Map<String, Int>, stalePersonIds: Set<String>, outgoing: List<Instruction>, incoming: List<Instruction>, popularTags: List<TagCount>, padding: PaddingValues, onPersonClick: (String) -> Unit, onTagClick: (String) -> Unit, onInstructionClick: (Instruction) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)) {
        HomeHierarchySections(outgoing = outgoing, incoming = incoming, popularTags = popularTags, onTagClick = onTagClick, onInstructionClick = onInstructionClick)
        items(persons) { person ->
            PersonRowSimple(person = person, openCount = openCountByPersonId[person.id] ?: 0, isStale = person.id in stalePersonIds, onClick = { onPersonClick(person.id) })
        }
    }
}

@Composable private fun PersonRowSimple(person: Person, openCount: Int, isStale: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open person", onClick = onClick).padding(horizontal = 0.dp, vertical = 12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(person.designation, person.station).filter { it.isNotBlank() }.joinToString(" \u00b7 ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (openCount > 0) Text("$openCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
