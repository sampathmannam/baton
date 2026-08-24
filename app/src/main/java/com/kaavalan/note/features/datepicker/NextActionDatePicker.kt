package com.kaavalan.note.features.datepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kaavalan.note.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tier 1.5 (v2.0): a tiny "next action date" picker.
 *
 * The user picks a date via the Material 3 `DatePickerDialog`
 * (returns UTC midnight millis). The user can optionally
 * pick a time via the `TimePicker` (9 AM default). The
 * combined epoch-millis is the result.
 *
 * **No-shame copy.** The dialogs use only the calm palette
 * tokens — no red error colour. The "Clear" button is a
 * neutral text button that sets the value to `null` (the
 * caller persists the row without `nextActionAt`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextActionDatePicker(
    current: Long?,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val label = if (current == null) {
            stringResource(R.string.instruction_next_action_pick)
        } else {
            val formatted = remember(current) {
                Instant.ofEpochMilli(current)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
            }
            stringResource(R.string.instruction_next_action_set, formatted)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (current == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (current != null) {
            IconButton(onClick = { onSelected(null) }) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.instruction_next_action_clear),
                )
            }
        }
        TextButton(onClick = { showDate = true }) {
            Text(stringResource(R.string.instruction_next_action_pick))
        }
    }

    if (showDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = current ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateMillis = dateState.selectedDateMillis
                        showDate = false
                        if (pendingDateMillis != null) {
                            showTime = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.date_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.date_picker_cancel))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = 9,
            initialMinute = 0,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showTime = false }) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTime = false }) {
                            Text(stringResource(R.string.date_picker_cancel))
                        }
                        TextButton(onClick = {
                            val date = pendingDateMillis
                            if (date != null) {
                                val utc = Instant.ofEpochMilli(date)
                                val localDate = utc.atZone(ZoneId.systemDefault()).toLocalDate()
                                val time = LocalTime.of(timeState.hour, timeState.minute)
                                val combined = LocalDateTime.of(localDate, time)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                                onSelected(combined)
                            }
                            showTime = false
                        }) {
                            Text(stringResource(R.string.date_picker_ok))
                        }
                    }
                }
            }
        }
    }
}
