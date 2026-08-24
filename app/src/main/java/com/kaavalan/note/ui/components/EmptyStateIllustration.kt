package com.kaavalan.note.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R

/**
 * v1.6.0: a single hand-drawn empty-state illustration with
 * a one-line headline and (optional) body. The illustrations
 * are monochrome line drawings stored in `res/drawable/`,
 * tinted to [KaavalanAccentColor] so they inherit the theme
 * without per-screen theming.
 *
 * **Why illustrations at all.** The red-dot critique and
 * the Things 3 / Bear / Cron reference set agree: a single
 * monochrome illustration per major empty state is the
 * single highest-impact aesthetic change for the lowest
 * engineering cost. The illustrations are 240x240 PNGs,
 * ~2-4 KB each, and they are the *only* decorative content
 * in the app.
 *
 * **Why monochrome + accent tint.** Three constraints
 * converge:
 *   1. The app has a single accent colour (see
 *      [KaavalanAccentColor]). Decorative content must
 *      use it or be neutral.
 *   2. Material 3 has a `surface` colour but no "empty
 *      state" colour. Using `primary` would compete with
 *      actionable copy. Tint-to-accent is the lowest-
 *      conflict option.
 *   3. The icon already uses the teal-coral gradient;
 *      using the same colour family on illustrations
 *      keeps the visual language unified.
 *
 * **Why not more than 5 illustrations.** The Pendo
 * 80%-never-used rule applies to illustration slots too:
 * if a screen doesn't have an empty state worth depicting,
 * don't draw an illustration for it. Five is the cap.
 */
@Composable
fun EmptyStateIllustration(
    @DrawableRes illustration: Int,
    headline: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Tint the illustration to the accent colour. The
        // source PNG is black-on-transparent; ColorFilter
        // replaces the black with the accent at full alpha.
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            colorFilter = ColorFilter.tint(KaavalanAccentColor),
        )
        Spacer(Modifier.height(16.dp))
        // v1.6.0 typography rule: a single weight change at
        // the right moment is the highest-impact detail in
        // a calm-technology app. Headline is Medium
        // (500); body is Regular.
        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * v1.6.0: the canonical empty-state keys. Each corresponds
 * to a drawable in `res/drawable/empty_*.png` and a one-line
 * headline. The body is a calm explanation; the headline is
 * the moment-of-truth copy.
 */
object EmptyStateKeys {
    /** No people in the vault. First-time use. */
    val People = EmptyStateSpec(
        illustration = R.drawable.empty_people,
        headline = "Nobody here yet.",
        body = "Add the first person who matters to you. " +
            "A name is enough to start.",
    )

    /** Today is empty. The user is caught up. */
    val Today = EmptyStateSpec(
        illustration = R.drawable.empty_today,
        headline = "Nothing on your plate.",
        body = "When instructions come in, they'll show up here.",
    )

    /** Storage is empty. */
    val Storage = EmptyStateSpec(
        illustration = R.drawable.empty_storage,
        headline = "Empty.",
        body = "Your data lives only on this device until you make a backup.",
    )

    /** Worry box is empty. */
    val Worry = EmptyStateSpec(
        illustration = R.drawable.empty_worry,
        headline = "No worries on the list.",
        body = "A worry is something you want to think about again later. " +
            "Add one with the 'worry' capture option.",
    )

    /** Search returned no matches. */
    val Search = EmptyStateSpec(
        illustration = R.drawable.empty_search,
        headline = "No matches.",
        body = "Try a different word. Search runs across people, instructions, and notes.",
    )
}

data class EmptyStateSpec(
    @DrawableRes val illustration: Int,
    val headline: String,
    val body: String,
)
