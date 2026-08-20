package com.baton.app.ui.today.win

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R

/**
 * v2.0 Tier 2 (§2.11): "Today's win" card. Always visible on the
 * Today tab (it's a single-card summary of the user's day), with
 * a neutral copy template that never reads as a score or a streak.
 */
@Composable
fun TodaysWinCard(
    viewModel: TodaysWinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.todays_win_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (state.isEmpty) {
                    stringResource(R.string.todays_win_summary_zero)
                } else {
                    // v1.6.6 P1: per-segment pluralization. The
                    // previous single stringResource call used 4
                    // %d args in a template that could not change
                    // "1 capture" vs "5 captures", "1 person" vs
                    // "5 people", etc. Build the sentence from
                    // individual pluralStringResource calls joined
                    // by ", " and ending with ".".
                    buildString {
                        append(pluralStringResource(R.plurals.count_captures, state.captureCount, state.captureCount))
                        append(stringResource(R.string.count_connector_comma))
                        append(pluralStringResource(R.plurals.count_people, state.peopleCount, state.peopleCount))
                        append(stringResource(R.string.count_connector_comma))
                        append(pluralStringResource(R.plurals.count_carried_over, state.carriedOverCount, state.carriedOverCount))
                        append(stringResource(R.string.count_connector_comma))
                        append(pluralStringResource(R.plurals.count_sensitive, state.sensitiveCount, state.sensitiveCount))
                        append('.')
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
