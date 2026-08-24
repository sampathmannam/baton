package com.kaavalan.note.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R

/**
 * v1.4 (PHONE-FINDING-6): a small "Offline" pill rendered in the
 * top app bar when the network is unreachable.
 *
 * Color. Per the project's "no red" rule, the pill uses
 * [MaterialTheme.colorScheme.surfaceVariant] — same family as
 * the stuck-outbox card in the settings sheet.
 *
 * Accessibility. The whole pill carries a single
 * [contentDescription] so TalkBack reads the full sentence.
 *
 * No-op render. When [isOnline] is `true`, the composable
 * returns nothing.
 */
@Composable
fun OfflineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isOnline) return
    val desc = stringResource(R.string.offline_pill_a11y)
    Surface(
        modifier = modifier.semantics { contentDescription = desc },
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                contentColor = Color.Transparent,
                shape = CircleShape,
            ) {}
            Text(
                text = stringResource(R.string.offline_pill_label),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
