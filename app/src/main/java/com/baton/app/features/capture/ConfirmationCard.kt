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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baton.app.R

/**
 * M1 confirmation card.
 *
 * v1.4 (F-23): the confidence chip now uses same-family
 * container/label pairs to hit WCAG AA contrast.
 */
@Composable
fun ConfirmationCard(
    proposal: ExtractedInstruction,
    addToCalendar: Boolean,
    onPersonChange: (String) -> Unit,
    onActionChange: (String) -> Unit,
    onInstructionTextChange: (String) -> Unit,
    onAddToCalendarChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDueAt = !proposal.dueAt.isNullOrBlank()
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.confirmation_add_to_calendar),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!hasDueAt) {
                    Text(
                        text = "Needs a due time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Switch(
                checked = addToCalendar && hasDueAt,
                onCheckedChange = onAddToCalendarChange,
                enabled = hasDueAt,
            )
        }
    }
}

/**
 * v1.4 (F-23): WCAG AA contrast for the confidence chip.
 *
 *   - confidence >= 0.8  -> primaryContainer / onPrimaryContainer
 *   - confidence >= 0.5  -> tertiaryContainer / onTertiaryContainer
 *   - else               -> surfaceVariant / onSurfaceVariant
 */
@Composable
private fun ConfidenceChip(confidence: Double) {
    val label = when {
        confidence >= 0.8 -> "High"
        confidence >= 0.5 -> "Medium"
        else -> "Low"
    }
    val (containerColor, labelColor) = when {
        confidence >= 0.8 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        confidence >= 0.5 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val confidenceDesc = when {
        confidence >= 0.8 -> stringResource(R.string.a11y_confidence_high)
        confidence >= 0.5 -> stringResource(R.string.a11y_confidence_medium)
        else -> stringResource(R.string.a11y_confidence_low)
    }
    AssistChip(
        onClick = {},
        label = { Text(label, color = labelColor) },
        modifier = Modifier.semantics { contentDescription = confidenceDesc },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = labelColor,
            containerColor = containerColor,
        ),
    )
}

/**
 * v1.4 (F-23): the testable mapping from a [confidence] value to
 * the M3 container colour the [ConfidenceChip] should render.
 */
fun confidenceContainerColor(confidence: Double): androidx.compose.ui.graphics.Color = when {
    confidence >= 0.8 -> M3TestColors.PrimaryContainer
    confidence >= 0.5 -> M3TestColors.TertiaryContainer
    else -> M3TestColors.SurfaceVariant
}

/**
 * v1.4 (F-23): the v1.3 confidence-chip label colour for a given
 * [confidence] value, in the same M3 container pair as
 * [confidenceContainerColor].
 */
fun confidenceLabelColor(confidence: Double): androidx.compose.ui.graphics.Color = when {
    confidence >= 0.8 -> M3TestColors.OnPrimaryContainer
    confidence >= 0.5 -> M3TestColors.OnTertiaryContainer
    else -> M3TestColors.OnSurfaceVariant
}

private object M3TestColors {
    val PrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD7E3FC)
    val OnPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001A41)
    val TertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFD8E4)
    val OnTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF31111D)
    val SurfaceVariant = androidx.compose.ui.graphics.Color(0xFFEFEAE0)
    val OnSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF6B6358)
}
