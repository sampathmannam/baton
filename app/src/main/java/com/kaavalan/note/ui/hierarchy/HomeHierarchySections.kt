package com.kaavalan.note.ui.hierarchy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R
import com.kaavalan.note.data.instructions.audienceFromColumns
import com.kaavalan.note.data.local.entities.InstructionEntity
import com.kaavalan.note.data.person.Person
import com.kaavalan.note.ui.home.TagCount

fun LazyListScope.homeHierarchySections(
    outgoing: List<InstructionEntity>,
    incoming: List<InstructionEntity>,
    popularTags: List<TagCount>,
    onTagClick: (String) -> Unit,
    onInstructionClick: (InstructionEntity) -> Unit,
) {
    if (outgoing.isEmpty() && incoming.isEmpty() && popularTags.isEmpty()) return
    if (popularTags.isNotEmpty()) {
        item { Text(stringResource(R.string.hierarchy_section_tags), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(popularTags, key = { it.tagId }) { tag ->
                    AssistChip(onClick = { onTagClick(tag.tagId) }, label = { Text("#${tag.name}") }, trailingIcon = { Text(stringResource(R.string.hierarchy_tag_chip_count, tag.count), style = MaterialTheme.typography.labelSmall) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (outgoing.isNotEmpty()) {
        item { Text(stringResource(R.string.hierarchy_section_outbox), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        items(outgoing, key = { it.id }) { ins -> InstructionRow(ins, audienceFromColumns(ins.audienceKind, ins.audienceTarget, ins.audienceLabel)?.label ?: "Single person") { onInstructionClick(ins) } }
    }
    if (incoming.isNotEmpty()) {
        item { Text(stringResource(R.string.hierarchy_section_inbox), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        items(incoming, key = { it.id }) { ins -> InstructionRow(ins, audienceFromColumns(ins.audienceKind, ins.audienceTarget, ins.audienceLabel)?.label ?: "Direct") { onInstructionClick(ins) } }
    }
    item {
        // A slightly thicker divider so the user can tell where the
        // hierarchy section ends and the people list begins — both
        // the per-person dividers and this one use outlineVariant
        // colour, so a thickness bump is the cheapest way to make
        // the boundary readable.
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable private fun InstructionRow(ins: InstructionEntity, subtitle: String, onClick: () -> Unit) {
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
fun HomeHierarchyAwarePersonList(persons: List<Person>, openCountByPersonId: Map<String, Int>, stalePersonIds: Set<String>, outgoing: List<InstructionEntity>, incoming: List<InstructionEntity>, popularTags: List<TagCount>, padding: PaddingValues, onPersonClick: (String) -> Unit, onTagClick: (String) -> Unit, onInstructionClick: (InstructionEntity) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)) {
        homeHierarchySections(outgoing = outgoing, incoming = incoming, popularTags = popularTags, onTagClick = onTagClick, onInstructionClick = onInstructionClick)
        items(persons, key = { it.id }) { person ->
            PersonRowSimple(person = person, openCount = openCountByPersonId[person.id] ?: 0, isStale = person.id in stalePersonIds, onClick = { onPersonClick(person.id) })
        }
    }
}

@Composable private fun PersonRowSimple(person: Person, openCount: Int, isStale: Boolean, onClick: () -> Unit) {
    // Material 3 design: list items should be at least 88dp tall to
    // meet the minimum touch-target guideline (48dp) with breathing
    // room. `heightIn(min = 88.dp)` matches the rest of the Home
    // list (the pre-v2.0 `PersonList` used the same token).
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(onClickLabel = "Open person", onClick = onClick).padding(horizontal = 0.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(person.designation, person.station).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (openCount > 0) Text("$openCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
