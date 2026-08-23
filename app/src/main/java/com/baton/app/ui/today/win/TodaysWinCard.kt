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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            // v1.9.4 (drive-verify polish #4): outer vertical
            // padding 8dp -> 4dp. The v1.9.3-era 8dp on top and
            // bottom of an already-padded inner Column produced
            // a ~140px tall card for just 2 lines of text. The
            // "UI should use the screen properly" feedback.
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            // v1.9.4: inner padding 16dp -> 12dp on top + bottom
            // (keep 16dp on sides for the title/body alignment
            // with the rest of the surface). Two text lines +
            // 24dp vertical padding is now ~70dp tall, down
            // from ~94dp.
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.todays_win_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // v1.9.1 (a11y-audit-action-#1): merge the four
            // counts into a single accessibility node so TalkBack
            // reads them as one continuous sentence. Without this,
            // the screen reader pauses at every comma+number and
            // announces each fragment as a separate "text" node,
            // which is the WCAG 1.3.1 + 4.1.2 finding from the
            // a11y audit. The visible text is unchanged; only the
            // semantic announcement is consolidated.
            val summaryText = if (state.isEmpty) {
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
                    // v1.6.8: count_carried_over and count_sensitive
                    // are now <string> (the v1.6.6 <plurals> had
                    // identical one/other items so the wrapper was
                    // wrong). Use stringResource, no quantity arg.
                    append(stringResource(R.string.count_carried_over, state.carriedOverCount))
                    append(stringResource(R.string.count_connector_comma))
                    append(stringResource(R.string.count_sensitive, state.sensitiveCount))
                    append('.')
                }
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = summaryText },
            )
        }
    }
}
