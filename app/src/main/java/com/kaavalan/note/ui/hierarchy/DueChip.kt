package com.kaavalan.note.ui.hierarchy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueChip(dueAtMs: Long?, onSet: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var pickerOpen by remember { mutableStateOf(false) }
    val displayFormatter = remember { DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault()) }
    val label = if (dueAtMs == null) stringResource(R.string.hierarchy_due_chip_tap_to_set) else stringResource(R.string.hierarchy_due_chip_set, displayFormatter.format(Instant.ofEpochMilli(dueAtMs)))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        AssistChip(
            onClick = { pickerOpen = true },
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            // Wrap the clear icon in a `Box.clickable` so the click
            // event is consumed by the icon's own handler (`onSet(null)`)
            // and does NOT propagate to the parent chip's `onClick`
            // (which would also open the date picker). On its own
            // `Modifier.clickable` on an `Icon` does not always stop
            // propagation through a `Surface(onClick = ...)` parent.
            trailingIcon = if (dueAtMs != null) { { Box(modifier = Modifier.size(AssistChipDefaults.IconSize + 8.dp).clickable { onSet(null) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.hierarchy_due_chip_clear), modifier = Modifier.size(AssistChipDefaults.IconSize)) } } } else null,
        )
    }
    if (pickerOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueAtMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = { TextButton(onClick = { val ms = state.selectedDateMillis; if (ms != null) { val local: LocalDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().atTime(12, 0); onSet(local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }; pickerOpen = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancel") } },
        ) { Box(Modifier.padding(8.dp)) { DatePicker(state = state) } }
    }
}

internal fun formatDueLabel(epochMs: Long): String {
    val today = LocalDate.now()
    val picked = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (picked) { today -> "Today"; today.plusDays(1) -> "Tomorrow"; today.minusDays(1) -> "Yesterday"; else -> picked.format(DateTimeFormatter.ofPattern("MMM d")) }
}

@Composable internal fun DueLabel(epochMs: Long) { Text(formatDueLabel(epochMs), style = MaterialTheme.typography.labelSmall) }
