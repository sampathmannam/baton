package com.baton.app.ui.today.worry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.baton.app.ui.theme.BatonColors
import java.time.LocalDate

/**
 * v2.0 Tier 2 (§2.10): the "Worry box" section. Renders the
 * worry instructions and worry captures in a single list, with a
 * "Review and let go" (resolve) and "Keep" action per row. The
 * row's surface uses [BatonColors.Quiet] at low alpha to read as
 * "a softer area of the app" without crossing into red.
 */
@Composable
fun WorryBoxSection(
    viewModel: WorryBoxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.worry_box_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.worry_box_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.isEmpty) {
            Text(
                text = stringResource(R.string.worry_box_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        state.items.forEach { item ->
            WorryRow(
                item = item,
                onResolve = {
                    when (item) {
                        is WorryItem.Instruction -> viewModel.resolveInstruction(item.data.id)
                        is WorryItem.Capture -> viewModel.resolveCapture(item.data.id)
                    }
                },
                onKeep = {
                    when (item) {
                        is WorryItem.Instruction -> viewModel.keepInstruction(item.data.id)
                        is WorryItem.Capture -> viewModel.keepCapture(item.data.id)
                    }
                },
            )
        }
    }
}

@Composable
private fun WorryRow(
    item: WorryItem,
    onResolve: () -> Unit,
    onKeep: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = BatonColors.Quiet.copy(alpha = 0.10f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.data.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            item.data.rawText?.let { raw ->
                if (raw != item.data.title) {
                    Text(
                        text = raw,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.data.reviewEpochDay?.let { day ->
                val reviewDate = LocalDate.ofEpochDay(day)
                val today = LocalDate.now()
                val daysAway = today.toEpochDay() - day  // positive = past
                val label = when {
                    daysAway > 0 -> "Review was $daysAway days ago (${reviewDate})"
                    daysAway == 0L -> "Review today (${reviewDate})"
                    else -> "Review in ${-daysAway} days (${reviewDate})"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onResolve) {
                    Text(stringResource(R.string.worry_box_review))
                }
                OutlinedButton(onClick = onKeep) {
                    Text(stringResource(R.string.worry_box_keep))
                }
            }
        }
    }
}
