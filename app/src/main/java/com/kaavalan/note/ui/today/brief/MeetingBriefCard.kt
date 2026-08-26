package com.kaavalan.note.ui.today.brief

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaavalan.note.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * v2.0 Tier 2 (§2.7): the "Brief me before a meeting" card.
 * Hidden when the user has not granted `READ_CALENDAR` (the
 * state reports `isPermissionMissing = true`; we render the
 * rationale hint instead of the event list).
 *
 * v2.1.2 (Barrier 5): the permission-missing state is now
 * tappable. The card's `clickable` modifier fires
 * [onOpenSettings] so a tap on the rationale text opens the
 * Settings sheet (where `READ_CALENDAR` can be granted). The
 * event-full and no-events states do not need navigation,
 * so the click handler is a no-op for them — making the
 * whole card clickable would imply an affordance that
 * doesn't exist ("view brief detail" is not a screen).
 */
@Composable
fun MeetingBriefCard(
    viewModel: MeetingBriefViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Re-query when the user navigates to the Today tab.
    LaunchedEffect(Unit) { viewModel.refresh() }
    val openLabel = stringResource(R.string.meeting_brief_title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // v2.1.2 (Barrier 5): only the permission-missing
            // card is tappable. The other states (no events /
            // events listed) are informational and have no
            // destination to navigate to. Conditionally applying
            // `clickable` keeps the card's hit-target honest —
            // the affordance only appears when it has a job.
            .let { base ->
                if (state.isPermissionMissing) {
                    base.clickable(
                        onClickLabel = openLabel,
                        onClick = onOpenSettings,
                    )
                } else {
                    base
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.meeting_brief_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            when {
                state.isPermissionMissing -> {
                    Text(
                        text = stringResource(R.string.meeting_brief_calendar_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.events.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.meeting_brief_no_events),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    state.events.forEach { entry ->
                        EventBlock(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventBlock(entry: MeetingBriefEntry) {
    val start = Instant.ofEpochMilli(entry.event.startMs)
        .atZone(ZoneId.systemDefault())
    val end = entry.event.endMs?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
    }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val timeText = if (end != null) {
        stringResource(
            R.string.meeting_brief_event_time,
            timeFmt.format(start),
            timeFmt.format(end),
        )
    } else timeFmt.format(start)
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${entry.event.title}  -  ${entry.personName}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.recentInstructions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.meeting_brief_recent_instructions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.recentInstructions.forEach { ins ->
                Text(
                    text = "- ${ins.title}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (entry.recentPhotos.isNotEmpty()) {
            Text(
                text = stringResource(R.string.meeting_brief_recent_photos),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.recentPhotos.forEach { cap ->
                Text(
                    text = "- ${cap.rawText?.take(60) ?: "(photo)"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (entry.recentNotes.isNotEmpty()) {
            Text(
                text = stringResource(R.string.meeting_brief_recent_notes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.recentNotes.forEach { cap ->
                Text(
                    text = "- ${cap.rawText?.take(60) ?: "(empty)"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
