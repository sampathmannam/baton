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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    // Hilt's SavedStateHandle lets the VM pick up the `personId`
    // nav arg without us passing it manually. The VM exposes
    // `state` as a Flow that the composable collects.
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                personName = s.person.name,
                subtitle = listOfNotNull(s.person.designation, s.person.station).joinToString(" • "),
                instructions = s.instructions,
                padding = padding,
            )
        }
    }
}

@Composable
private fun PersonTimeline(
    personName: String,
    subtitle: String,
    instructions: List<Instruction>,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = personName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.person_detail_timeline_count,
                        instructions.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                InstructionRow(ins)
            }
        }
        // Leave room for the NoteBar (carried over from M2).
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun InstructionRow(instruction: Instruction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
        }
    }
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
    androidx.compose.material3.Surface(
        color = bg,
        contentColor = fg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
    ) {
        Text(
            text = status.name.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() },
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
