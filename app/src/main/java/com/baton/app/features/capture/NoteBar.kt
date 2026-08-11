package com.baton.app.features.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baton.app.R

/**
 * The single note bar at the bottom of every screen. Tapping anywhere on
 * the bar opens the capture sheet. The mic and camera icons are wired in
 * M2; in M1 they are present but disabled, signalling what's coming.
 */
@Composable
fun NoteBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .semantics { contentDescription = "Add note" },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.note_bar_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = stringResource(R.string.note_bar_camera),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(R.string.note_bar_mic),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}
