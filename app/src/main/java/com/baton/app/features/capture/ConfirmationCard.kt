package com.baton.app.features.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baton.app.R

/**
 * M1 confirmation card. Shown in the capture sheet when the LLM
 * returns a non-null [ExtractedInstruction]. The user can edit any
 * field; Confirm creates the instruction (M1-T5).
 *
 * No red colour tokens anywhere (per the global no-shame / no-red
 * constraint). Confidence is shown as a small "High / Medium / Low"
 * chip with amber for Medium and dim grey for Low.
 */
@Composable
fun ConfirmationCard(
    proposal: ExtractedInstruction,
    onPersonChange: (String) -> Unit,
    onActionChange: (String) -> Unit,
    onInstructionTextChange: (String) -> Unit,
    onAddToCalendarChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Proposed instruction",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            ConfidenceChip(proposal.confidence)
        }

        OutlinedTextField(
            value = proposal.person.orEmpty(),
            onValueChange = onPersonChange,
            label = { Text(stringResource(R.string.confirmation_person)) },
            placeholder = { Text("(no person)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = proposal.action,
            onValueChange = onActionChange,
            label = { Text(stringResource(R.string.confirmation_action)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = proposal.instructionText,
            onValueChange = onInstructionTextChange,
            label = { Text(stringResource(R.string.confirmation_instruction_text)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.confirmation_add_to_calendar),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = false,  // M1-T6 wires the toggle state
                onCheckedChange = onAddToCalendarChange,
                enabled = false,  // M1-T6 enables this
            )
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: Double) {
    val (label, color) = when {
        confidence >= 0.8 -> "High" to MaterialTheme.colorScheme.primary
        confidence >= 0.5 -> "Medium" to MaterialTheme.colorScheme.tertiary
        else -> "Low" to MaterialTheme.colorScheme.outline
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = color,
            containerColor = Color.Transparent,
        ),
    )
}
