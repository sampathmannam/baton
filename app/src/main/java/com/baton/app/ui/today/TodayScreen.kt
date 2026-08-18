package com.baton.app.ui.today

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.baton.app.data.brief.DailyBrief
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.Status
import com.baton.app.data.person.toEntity
import com.baton.app.features.search.SearchBar
import com.baton.app.features.search.SearchViewModel
import com.baton.app.ui.today.brief.MeetingBriefCard
import com.baton.app.ui.today.decay.DecaySection
import com.baton.app.ui.today.win.TodaysWinCard
import com.baton.app.ui.today.worry.WorryBoxSection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * M4-T1: Today screen. The morning brief content lives here.
 *
 * M4-T5: a "Review" button in the top app bar opens the evening
 * review sheet.
 *
 * v1.5.3 (VAULT-010): tapping a note row opens an Instruction
 * detail sheet with Mark done / Drop / Reopen actions. Without
 * this, the day-2 user flow is broken — the user can capture
 * a note but cannot close the loop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    onOpenPerson: (String) -> Unit = {},
) {
    val brief by viewModel.brief.collectAsStateWithLifecycle()
    var showReview by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Instruction?>(null) }
    val searchViewModel: SearchViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val query by searchViewModel.query.collectAsStateWithLifecycle()
    val results by searchViewModel.results.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.tab_today)) },
                    actions = {
                        OutlinedButton(onClick = { showReview = true }) {
                            Text("Review")
                        }
                    },
                )
                SearchBar(viewModel = searchViewModel)
            }
        },
    ) { padding ->
        // v1.6.2: also pull the person filter from the search VM.
        // The visible people list is fed in by the
        // TodayViewModel.persons flow (added in v1.6.2 so the
        // search placeholder is honest on Today too).
        val personResults by searchViewModel.personResults.collectAsStateWithLifecycle()
        val persons by viewModel.persons.collectAsStateWithLifecycle()
        LaunchedEffect(persons) {
            searchViewModel.setVisiblePeople(persons.map { it.toEntity() })
        }
        if (query.isNotEmpty()) {
            com.baton.app.ui.home.HomeScreenSearchResults(
                personResults = personResults,
                instructionResults = results,
                padding = padding,
                onPersonClick = { /* search is read-only on Today */ },
            )
        } else if (brief.isEmpty) {
            EmptyBriefContent()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            // v2.0 Tier 2 (§2.11): Today's win summary.
            item { TodaysWinCard() }
            // v2.0 Tier 2 (§2.1, §2.13, §2.14): the "Haven't
            // touched in N days" section with the redistribution
            // banner.
            item { DecaySection(onOpenPerson = onOpenPerson) }
            // v2.0 Tier 2 (§2.10): the worry box (rendered above
            // the brief so the user sees it first; non-shaming
            // surface).
            item { WorryBoxSection() }
            // v2.0 Tier 2 (§2.7): meeting brief card.
            item { MeetingBriefCard() }
            // Existing brief sections.
            if (brief.isEmpty) {
                item { EmptyBriefContent() }
            } else {
                if (brief.needsYouToday.isNotEmpty()) {
                    item { SectionHeader("Needs you today") }
                    items(items = brief.needsYouToday, key = { it.id }) { ins ->
                        InstructionCard(ins, onClick = { selected = ins })
                    }
                }
                if (brief.waitingOnOthers.isNotEmpty()) {
                    item { SectionHeader("Waiting on others") }
                    items(items = brief.waitingOnOthers, key = { it.id }) { ins ->
                        InstructionCard(ins, onClick = { selected = ins })
                    }
                }
                if (brief.carriedOver.isNotEmpty()) {
                    item { SectionHeader("Carried over") }
                    items(items = brief.carriedOver, key = { it.id }) { ins ->
                        InstructionCard(ins, onClick = { selected = ins })
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        }
    }
    if (showReview) {
        val review by viewModel.review.collectAsStateWithLifecycle()
        EveningReviewSheet(
            review = review,
            onDismiss = { showReview = false },
        )
    }
    selected?.let { ins ->
        InstructionDetailSheet(
            instruction = ins,
            onDismiss = { selected = null },
            onMarkDone = {
                viewModel.markDone(ins.id)
                selected = null
            },
            onDrop = {
                viewModel.markDropped(ins.id)
                selected = null
            },
            onReopen = {
                viewModel.reopen(ins.id)
                selected = null
            },
        )
    }
}

@Composable
private fun EmptyBriefContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing on your plate.",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "When instructions come in, they'll show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * v1.5.3 (VAULT-010): the card is now clickable. Tapping it
 * opens the InstructionDetailSheet. The `clickable` modifier
 * on the outer Card is the smallest change that gets the
 * hit-target correct (the whole row, not just the text).
 */
@Composable
private fun InstructionCard(ins: Instruction, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.a11y_today_row_open)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClickLabel = openLabel, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = ins.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // v1.6.2: skip the body when it duplicates the title.
            // v1.6.1 capture stores a single line of text in both
            // `title` and `rawText` (no separate title/body fields);
            // rendering both makes the card look like it lost a
            // line. When they differ, `rawText` carries the rest
            // of the note.
            if (ins.title != ins.rawText) {
                Text(
                    text = ins.rawText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatTime(ins.capturedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(iso: String): String = try {
    val inst = Instant.parse(iso)
    DateTimeFormatter.ofPattern("d MMM, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(inst)
} catch (e: Exception) { iso }

/**
 * v1.5.3 (VAULT-010): the instruction detail sheet. Shows the
 * full raw text + a status pill + one of three action buttons
 * depending on the current status.
 *
 *  - OPEN / IN_PROGRESS / WAITING_ON_OTHER / ACK_PENDING:
 *    [Mark done] [Drop]
 *  - DONE:
 *    [Reopen]
 *  - DROPPED:
 *    [Reopen]
 *  - CARRIED_OVER:
 *    [Mark done] [Drop]
 *
 * The actions use the same neutral wording as the rest of the
 * app (no "Delete" / "Destroy" / red colour).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructionDetailSheet(
    instruction: Instruction,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit,
    onDrop: () -> Unit,
    onReopen: () -> Unit,
) {
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
                text = instruction.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            StatusPill(instruction.status)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = instruction.rawText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (instruction.dueAt != null) {
                Text(
                    text = "Due: ${formatTime(instruction.dueAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Captured: ${formatTime(instruction.capturedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // The action row. Reopen-only for DONE / DROPPED.
            val isClosed = instruction.status == Status.DONE ||
                instruction.status == Status.DROPPED
            if (isClosed) {
                Button(
                    onClick = onReopen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reopen")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onMarkDone,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Mark done")
                    }
                    OutlinedButton(
                        onClick = onDrop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text("Drop")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * v1.5.3 (VAULT-010): a soft status pill. The colour is
 * surfaceVariant (not red, not amber) — the spec's
 * "no-shame" rule means we never shout at the user about
 * an instruction's state, only label it.
 */
@Composable
private fun StatusPill(status: Status) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = status.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * M4-T5: evening review sheet. One screen. "What got done today"
 * + "What's still open" + a single dismiss tap. No streak, no
 * count-up, no punishment for missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EveningReviewSheet(review: EveningReview, onDismiss: () -> Unit) {
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
            Text("Evening review", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = review.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (review.stillOpen.isEmpty()) {
                Text(
                    "Nothing carried over. Nice.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text("Still open:", style = MaterialTheme.typography.titleSmall)
                review.stillOpen.forEach { ins ->
                    Text(
                        text = "• ${ins.title}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap outside to dismiss. Tomorrow's brief picks up from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
