package com.baton.app.ui.today.decay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.ui.theme.BatonColors

/**
 * v2.0 Tier 2 (§2.1, §2.13, §2.14): the "Haven't touched in N
 * days" section rendered above the existing Today brief. Calm
 * palette, no red — the [ReachOutPill] uses
 * [BatonColors.Quiet] / [BatonColors.Done] / muted brown, never
 * error colour.
 *
 * The section is hidden entirely when the list is empty; the
 * `EmptyState` line lives in the parent Today brief. Filter chip
 * row is always visible so the user can switch between
 * 14 / 30 / 60 / 90 d.
 */
@Composable
fun DecaySection(
    onOpenPerson: (String) -> Unit,
    viewModel: DecayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRedistribute by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.decay_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.decay_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterChipsRow(
            current = state.filterDays,
            onPick = viewModel::setFilter,
        )
        // §2.14: redistribution banner — only when the quiet
        // pile is > 5 (matches the spec).
        if (state.quietCount > 5) {
            AssistChip(
                onClick = { showRedistribute = true },
                label = {
                    Text(stringResource(R.string.bulk_snooze_banner, state.quietCount))
                },
            )
        }
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(R.string.decay_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.rows.forEach { row ->
                DecayRow(
                    row = row,
                    onClick = { onOpenPerson(row.id) },
                )
            }
        }
    }

    if (showRedistribute) {
        RedistributeDialog(
            count = state.quietCount,
            onConfirm = {
                viewModel.redistribute()
                showRedistribute = false
            },
            onDismiss = { showRedistribute = false },
        )
    }
}

@Composable
private fun FilterChipsRow(current: Int, onPick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(DecayViewModel.FILTER_OPTIONS) { days ->
            FilterChip(
                selected = current == days,
                onClick = { onPick(days) },
                label = { Text(stringResource(filterResFor(days))) },
            )
        }
    }
}

private fun filterResFor(days: Int): Int = when (days) {
    14 -> R.string.decay_filter_14d
    30 -> R.string.decay_filter_30d
    60 -> R.string.decay_filter_60d
    90 -> R.string.decay_filter_90d
    else -> R.string.decay_filter_30d
}

@Composable
private fun DecayRow(row: DecayRow, onClick: () -> Unit) {
    val openLabel = stringResource(R.string.a11y_decay_row_open)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openLabel, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                row.designation?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.decay_days_quiet, row.daysQuiet.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            ReachOutPill(row.status)
        }
    }
}

/**
 * v2.0 Tier 2 (§2.13): the reach-out status pill. The colour
 * tokens are taken from the existing calm palette
 * ([BatonColors.Quiet] = amber for "Quiet a while",
 * `tertiaryContainer` for "Getting due", [BatonColors.Done] for
 * "On track"). We never use `MaterialTheme.colorScheme.error` or
 * `Color.Red` here - spec §3.3 forbids red "overdue" semantics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReachOutPill(state: ReachOutStatus) {
    val (color, label) = when (state) {
        ReachOutStatus.QuietAWhile -> BatonColors.Quiet to R.string.status_quiet_a_while
        ReachOutStatus.GettingDue -> BatonColors.PriorityLow to R.string.status_getting_due
        ReachOutStatus.OnTrack -> BatonColors.Done to R.string.status_on_track
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun RedistributeDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bulk_snooze_title)) },
        text = { Text(stringResource(R.string.bulk_snooze_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.bulk_snooze_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bulk_snooze_cancel))
            }
        },
    )
}
