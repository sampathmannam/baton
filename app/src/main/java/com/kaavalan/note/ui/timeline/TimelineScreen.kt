package com.kaavalan.note.ui.timeline

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kaavalan.note.R
import com.kaavalan.note.data.instructions.Instruction
import com.kaavalan.note.data.instructions.Priority
import com.kaavalan.note.data.instructions.Status
import com.kaavalan.note.data.instructions.TimelineBucket

@Composable
fun TimelineScreen(
    onCapture: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    TimelineScreen(
        uiState = uiState,
        selectedFilter = selectedFilter,
        onFilterSelected = viewModel::setFilter,
        onRetry = viewModel::retry,
        onCapture = onCapture,
        onOpenSettings = onOpenSettings,
    )
}

// User-facing copy: All, To do, Waiting, Done, Urgent,
// Action with you, Waiting on another person.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    uiState: TimelineUiState,
    selectedFilter: TimelineFilter,
    onFilterSelected: (TimelineFilter) -> Unit,
    onRetry: () -> Unit,
    onCapture: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tab_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCapture) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.timeline_capture))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TimelineFilters(selectedFilter, onFilterSelected)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (uiState) {
                    TimelineUiState.Loading -> CenteredMessage { CircularProgressIndicator() }
                    TimelineUiState.Empty -> CenteredMessage {
                        Text(
                            text = stringResource(R.string.timeline_empty),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    TimelineUiState.Error -> CenteredMessage {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.timeline_error))
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.timeline_retry)) }
                        }
                    }
                    is TimelineUiState.Content -> TimelineList(uiState.sections)
                }
            }
        }
    }
}

@Composable
private fun TimelineFilters(
    selectedFilter: TimelineFilter,
    onFilterSelected: (TimelineFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineFilter.values().forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun TimelineList(sections: List<TimelineSection>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            item(key = "section_${section.bucket}") {
                Text(
                    text = bucketLabel(section.bucket),
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(items = section.instructions, key = { it.id }) { instruction ->
                InstructionRow(instruction)
            }
        }
    }
}

@Composable
private fun InstructionRow(instruction: Instruction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = instruction.actionSummary.ifBlank { instruction.title },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ownershipLabel(instruction.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (instruction.priority == Priority.URGENT) {
                    Text(
                        text = stringResource(R.string.timeline_urgent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
private fun filterLabel(filter: TimelineFilter): String = stringResource(
    when (filter) {
        TimelineFilter.ALL -> R.string.timeline_filter_all
        TimelineFilter.TO_DO -> R.string.timeline_filter_to_do
        TimelineFilter.WAITING -> R.string.timeline_filter_waiting
        TimelineFilter.DONE -> R.string.timeline_filter_done
    },
)

@Composable
private fun bucketLabel(bucket: TimelineBucket): String = stringResource(
    when (bucket) {
        TimelineBucket.LATE -> R.string.timeline_bucket_late
        TimelineBucket.TODAY -> R.string.timeline_bucket_today
        TimelineBucket.NEXT_7_DAYS -> R.string.timeline_bucket_next_7_days
        TimelineBucket.LATER -> R.string.timeline_bucket_later
    },
)

@Composable
private fun ownershipLabel(status: Status): String = stringResource(
    when (status) {
        Status.TO_DO -> R.string.timeline_action_with_you
        Status.WAITING -> R.string.timeline_waiting_on_other
        Status.DONE -> R.string.timeline_completed
    },
)
