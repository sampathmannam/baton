package com.kaavalan.note.features.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R
import com.kaavalan.note.data.tags.Tag
import com.kaavalan.note.data.tags.TagKind
import com.kaavalan.note.ui.theme.KaavalanNoteThemeTokens

/**
 * M3-T7: tag picker for the capture sheet. Shows the user's existing
 * tags as a horizontally-scrolling chip row. Each chip is a [FilterChip];
 * tapping toggles selection. The "+ #tag" button at the end opens a
 * small text field to author a free-form tag (the row in the spec).
 *
 * **No red on the chips** — even FREE tags render in the `tertiary`
 * container. The dot color on the chip is the `color` column from the
 * server (auto-set on first sight for structural kinds; null for FREE).
 */
@Composable
fun TagPicker(
    available: List<Tag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onAddFree: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var composingFree by remember { mutableStateOf(false) }
    var freeText by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.tag_picker_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            items(items = available, key = { it.id }) { tag ->
                TagChip(
                    tag = tag,
                    selected = tag.id in selected,
                    onClick = { onToggle(tag.id) },
                )
            }
            item {
                if (composingFree) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = freeText,
                            onValueChange = { freeText = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.tag_picker_free_placeholder)) },
                            modifier = Modifier.heightIn(min = 40.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        TextButton(
                            onClick = {
                                onAddFree(freeText)
                                freeText = ""
                                composingFree = false
                            },
                            enabled = freeText.isNotBlank(),
                        ) { Text(stringResource(R.string.tag_picker_add)) }
                    }
                } else {
                    AssistChip(
                        onClick = { composingFree = true },
                        label = { Text(stringResource(R.string.tag_picker_add_chip)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * M3-T7: single tag chip. The dot is the kind's "color token" (a
 * quiet material color) when no `color` is set on the server. Selected
 * state shows a solid container; unselected is a tonal variant.
 */
@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // v1.6.8: theme-aware kind colour. The function reads
    // from the current MaterialTheme so the chip dot flips
    // when the user switches themes.
    val kindColor = colorForKind(tag.kind)
    FilterChip(
        modifier = modifier,
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tag.kind != TagKind.FREE) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        color = tag.color?.let(::parseHex) ?: kindColor,
                        contentColor = Color.Transparent,
                        shape = CircleShape,
                    ) {}
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    text = if (tag.kind == TagKind.FREE) "#${tag.name}" else tag.name,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
    )
}

/**
 * M3-T7: kind → quiet color (v1.6.8: theme-aware). PERSON /
 * DESIGNATION / STATION get the structural cool blue; CASE /
 * FIR / PRIORITY get the warm tertiary; FREE is the outlined
 * neutral. The colour is resolved from the current
 * `MaterialTheme` via [BatonThemeTokens] so the chip dot
 * flips when the user switches themes.
 */
@Composable
internal fun colorForKind(kind: TagKind): Color = when (kind) {
    TagKind.PERSON, TagKind.DESIGNATION, TagKind.STATION -> BatonThemeTokens.kindBlue()
    TagKind.CASE, TagKind.FIR, TagKind.PRIORITY -> BatonThemeTokens.kindWarm()
    TagKind.FREE -> BatonThemeTokens.kindNeutral()
}

/**
 * Parse a `#RRGGBB` (or `RRGGBB`) hex into a Compose [Color].
 * v1.6.8: the fallback (invalid hex) is the theme-neutral
 * grey. Callers that need the theme-aware variant should
 * pass the result through [colorForKind] instead.
 */
internal fun parseHex(hex: String): Color {
    val s = hex.removePrefix("#")
    val v = s.toLong(16)
    return when (s.length) {
        6 -> Color(0xFF000000L or v)
        8 -> Color(v)
        else -> com.kaavalan.note.ui.theme.BatonColors.KindNeutralLight
    }
}
