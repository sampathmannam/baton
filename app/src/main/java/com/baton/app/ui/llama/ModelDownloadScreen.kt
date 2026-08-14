package com.baton.app.ui.llama

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.ai.llama.ModelState

/**
 * v1.4.2 (F-10): first-run on-device LLM download screen. The
 * screen is the entry point in the app's start flow when no model
 * file is on disk yet; the parent `MainScaffold` (or whatever
 * `MODEL_DOWNLOAD_ROUTE` host the parent session wires in) routes
 * here on cold start when [com.baton.app.ai.llama.ModelManager.state]
 * is [ModelState.NotStarted] or [ModelState.Failed].
 *
 * The screen is intentionally minimal: a heading that explains
 * what's being fetched, a single primary action per state, and a
 * progress bar while the bytes flow. There is no "skip" — the model
 * is required for the on-device AI capture path
 * (see `com.baton.app.ai.extraction.Extractor`).
 *
 * **State-to-affordance mapping:**
 *  - [ModelState.NotStarted] → "Download model (~1 GB)" button.
 *  - [ModelState.Downloading] → `LinearProgressIndicator` with the
 *    current 0..1 progress; no button (cancellable only via OS-level
 *    network kill — out of scope for v1.4.2).
 *  - [ModelState.Ready] → "Continue" button that calls
 *    [onReady] so the host can navigate to the post-download route
 *    (the parent session owns the routing).
 *  - [ModelState.Failed] → the error text + a "Retry" button.
 */
@Composable
fun ModelDownloadScreen(
    onReady: () -> Unit = {},
    viewModel: ModelDownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModelDownloadContent(
        state = state,
        onDownload = viewModel::startDownload,
        onRetry = viewModel::retry,
        onReady = onReady,
    )
}

@Composable
private fun ModelDownloadContent(
    state: ModelState,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onReady: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.model_download_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.model_download_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            when (state) {
                is ModelState.NotStarted -> NotStartedSection(onDownload = onDownload)
                is ModelState.Downloading -> DownloadingSection(progress = state.progress)
                is ModelState.Ready -> ReadySection(
                    sizeBytes = state.sizeBytes,
                    onReady = onReady,
                )
                is ModelState.Failed -> FailedSection(
                    reason = state.reason,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun NotStartedSection(onDownload: () -> Unit) {
    Button(
        onClick = onDownload,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.model_download_button))
    }
}

@Composable
private fun DownloadingSection(progress: Float) {
    val displayProgress = if (progress < 0f) 0f else progress.coerceIn(0f, 1f)
    val percent = (displayProgress * 100).toInt()
    LinearProgressIndicator(
        progress = { displayProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    )
    Text(
        text = stringResource(R.string.model_download_percent, percent),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReadySection(sizeBytes: Long, onReady: () -> Unit) {
    Text(
        text = stringResource(R.string.model_download_ready),
        style = MaterialTheme.typography.bodyLarge,
    )
    val sizeMb = sizeBytes / (1024L * 1024L)
    Text(
        text = stringResource(R.string.model_download_size_mb, sizeMb),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onReady,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.model_download_continue))
    }
}

@Composable
private fun FailedSection(reason: String, onRetry: () -> Unit) {
    // v1.4 spec §1: error copy is neutral and non-shaming. We render
    // the reason verbatim but in onSurfaceVariant, never in the
    // error colour. The Retry button is the primary action.
    Text(
        text = stringResource(R.string.model_download_failed_reason, reason),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.model_download_retry))
    }
}

/**
 * v1.4.2 (F-10): the navigation route constant the parent session
 * uses to register this screen in the app's `NavHost`. The parent
 * owns the actual `composable(MODEL_DOWNLOAD_ROUTE) { ... }`
 * declaration; this constant is the only cross-cutting contract
 * between this package and the routing layer.
 */
const val MODEL_DOWNLOAD_ROUTE: String = "model_download"
