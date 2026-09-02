package com.kaavalan.note.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R
import com.kaavalan.note.data.local.entities.InstructionEntity
import com.kaavalan.note.data.local.entities.PersonEntity

/**
 * Compatibility model for the retired hierarchy screen. The active People screen no longer
 * exposes tag counts, but the legacy Today surface remains in the source tree until Stage 8.
 */
data class TagCount(
    val tagId: String,
    val name: String,
    val count: Int,
)

/**
 * Compile-only compatibility renderer for the obsolete Today search path.
 * The redesigned People screen owns person search. Stage 8 removes Today and
 * this adapter together after replacement tests pass.
 */
@Composable
fun HomeScreenSearchResults(
    personResults: List<PersonEntity>,
    instructionResults: List<InstructionEntity>,
    personNameById: Map<String, String>,
    padding: PaddingValues,
    onPersonClick: (String) -> Unit,
    onInstructionClick: (InstructionEntity) -> Unit,
) {
    if (personResults.isEmpty() && instructionResults.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.search_no_results))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (personResults.isNotEmpty()) {
            item(key = "legacy-people-heading") {
                Text(
                    stringResource(R.string.search_section_people),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(personResults, key = { "legacy-person-${it.id}" }) { person ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPersonClick(person.id) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(person.name)
                    listOfNotNull(person.designation, person.station)
                        .joinToString(" • ")
                        .takeIf(String::isNotEmpty)
                        ?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider()
            }
        }
        if (instructionResults.isNotEmpty()) {
            item(key = "legacy-instructions-heading") {
                Text(
                    stringResource(R.string.search_section_instructions),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
            items(instructionResults, key = { "legacy-instruction-${it.id}" }) { instruction ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInstructionClick(instruction) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(instruction.title)
                    instruction.personId?.let { personId ->
                        personNameById[personId]?.let { name ->
                            Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
