package com.kaavalan.note.features.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R

/**
 * The single note bar at the bottom of every screen. Tapping the
 * text opens the capture sheet for typing. The mic and camera
 * icons jump straight into their respective capture flows
 * (M2-T2 photo, M2-T4 voice).
 *
 * v1.7.1 (P1 Q1): the Photo and Voice buttons are now icon +
 * label stacks instead of bare IconButtons. The v1.6.8 set
 * `contentDescription` on the icon but no visible text — the
 * affordance was invisible to users without TalkBack. Now the
 * label is a small caption under the icon so the user can see
 * "Photo" and "Voice" without having to long-press to discover
 * the buttons.
 */
@Composable
fun NoteBar(
    onTextClick: () -> Unit,
    onCameraClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val addNoteDesc = stringResource(R.string.a11y_add_note)
    val photoLabel = stringResource(R.string.note_bar_camera)
    val voiceLabel = stringResource(R.string.note_bar_mic)
    // v2.1.1+1 (P1 fix: NoteBar click routing): the
    // Surface's onClick used to fire for any tap on the bar
    // (including near-but-not-on the Photo/Voice icon
    // columns), which meant a tap at the bottom of the bar
    // opened the text capture sheet instead of opening the
    // camera. Make the Surface non-clickable; bind the
    // text-trigger to a clickable Box that owns the left
    // "Quick note" region; leave the Photo/Voice icon
    // columns as the only clickable areas on the right.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .semantics { contentDescription = addNoteDesc },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The text region is the only place a tap should
            // open the capture sheet. The Photo/Voice columns
            // handle their own taps. Bind the click to a Box
            // around the Text so taps in the padding between
            // Text and Photo don't fall through to nothing.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        onClickLabel = addNoteDesc,
                        onClick = onTextClick,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.note_bar_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // v1.7.1 (P1 Q1): icon + label stack. The
            // IconButton wraps the whole Column so the
            // clickable target is the full icon+label
            // pair, not just the icon. The label is
            // `labelSmall` so the row stays compact.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(56.dp),
            ) {
                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = photoLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    text = photoLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(56.dp),
            ) {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = voiceLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    text = voiceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
