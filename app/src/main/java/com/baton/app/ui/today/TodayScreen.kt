package com.baton.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.baton.app.R
import com.baton.app.data.brief.DailyBrief
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.Status
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * M4-T1: Today screen. The morning brief content lives here.
 *
 * M4-T5: a "Review" button in the top app bar opens the evening
 * review sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val brief by viewModel.brief.collectAsStateWithLifecycle()
    var showReview by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_today)) },
                actions = {
                    OutlinedButton(onClick = { showReview = true }) {
                        Text("Review")
                    }
                },
            )
        },
    ) { padding ->
        if (brief.isEmpty) {
            EmptyBrief(padding)
        } else {
            BriefContent(brief, padding)
        }
    }
    if (showReview) {
        val review by viewModel.review.collectAsStateWithLifecycle()
        EveningReviewSheet(
            review = review,
            onDismiss = { showReview = false },
        )
    }
}

@Composable
private fun EmptyBrief(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
}

@Composable
private fun BriefContent(brief: DailyBrief, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (brief.needsYouToday.isNotEmpty()) {
            item { SectionHeader("Needs you today") }
            items(items = brief.needsYouToday, key = { it.id }) { ins ->
                InstructionCard(ins)
            }
        }
        if (brief.waitingOnOthers.isNotEmpty()) {
            item { SectionHeader("Waiting on others") }
            items(items = brief.waitingOnOthers, key = { it.id }) { ins ->
                InstructionCard(ins)
            }
        }
        if (brief.carriedOver.isNotEmpty()) {
            item { SectionHeader("Carried over") }
            items(items = brief.carriedOver, key = { it.id }) { ins ->
                InstructionCard(ins)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
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

@Composable
private fun InstructionCard(ins: Instruction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
            Text(
                text = ins.rawText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
