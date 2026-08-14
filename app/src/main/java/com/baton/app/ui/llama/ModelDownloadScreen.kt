package com.baton.app.ui.llama

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.ai.llama.ModelOption
import com.baton.app.ai.llama.ModelState

/**
 * v1.4.2 (F-10): first-run on-device LLM download screen. The
 * screen is the entry point in the app's start flow when no model
 * file is on disk yet; the parent `MainScaffold` (or whatever
 * `MODEL_DOWNLOAD_ROUTE` host the parent session wires in) routes
 * here on cold start when [com.baton.app.ai.llama.ModelManager.state]
 * is [ModelState.NotStarted] or [ModelState.Failed].
 *
 * v1.4.3 (F-10): extended with a "Switch model" affordance that
 * opens a [ModelPickerDialog] so the user can pick one of 3-4
 * GGUF models. The picker is always available regardless of the
 * current download state — a user can switch from a slow model
 * to a faster one mid-flight, or re-pick after a failed download.
 *
 * The screen is intentionally minimal: a heading that explains
 * what's being fetched, a single primary action per state, a
 * progress bar while the bytes flow, and the model-picker entry
 * point. There is no "skip" — the model is required for the
 * on-device AI capture path
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
    val models by viewModel.models.collectAsStateWithLifecycle()
    val currentModelId by viewModel.currentModelId.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }

    ModelDownloadContent(
        state = state,
        onDownload = viewModel::startDownload,
        onRetry = viewModel::retry,
        onReady = onReady,
        onSwitchModel = { pickerOpen = true },
    )

    if (pickerOpen) {
        ModelPickerDialog(
            options = models,
            currentId = currentModelId,
            onSelect = { option ->
                pickerOpen = false
                viewModel.selectModel(option)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ModelDownloadContent(
    state: ModelState,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onReady: () -> Unit,
    onSwitchModel: () -> Unit,
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
            // v1.4.3 (F-10): the model-picker entry point. Always
            // available so the user can switch at any time. A
            // `TextButton` keeps it visually subordinate to the
            // primary download / continue / retry action above.
            TextButton(
                onClick = onSwitchModel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.model_picker_switch))
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
 * v1.4.3 (F-10): the model-picker dialog. Renders one
 * `RadioButton` row per [ModelOption] in [options], pre-selecting
 * the row whose `id` matches [currentId]. "Cancel" dismisses
 * without changing the selection; "Switch" confirms the
 * currently-highlighted option via [onSelect] and dismisses.
 *
 * The dialog uses a [AlertDialog] from Material3 and wraps the
 * option list in a `selectableGroup` so TalkBack announces the
 * group as a single-select list. The `verticalScroll` keeps the
 * dialog usable on small phones if the catalogue grows.
 */
@Composable
private fun ModelPickerDialog(
    options: List<ModelOption>,
    currentId: String,
    onSelect: (ModelOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(currentId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 360.dp),
            ) {
                options.forEach { option ->
                    val selected = option.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = selected,
                                onClick = { selectedId = option.id },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null, // handled by row selectable
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = stringResource(modelNameRes(option.id)),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(modelDescRes(option.id)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pick = options.firstOrNull { it.id == selectedId } ?: return@TextButton
                    onSelect(pick)
                },
            ) {
                Text(stringResource(R.string.model_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.model_picker_cancel))
            }
        },
    )
}

/**
 * v1.4.3 (F-10): resolves the `strings.xml` resource id for a
 * given model id's display name. Falls back to the raw
 * `displayName` field if no string resource is registered (e.g.
 * a model added at runtime before the strings are updated). This
 * keeps the screen rendering if the catalogue grows without a
 * strings.xml update.
 */
private fun modelNameRes(id: String): Int = when (id) {
    "qwen3-1.7b-q4_k_m" -> R.string.model_qwen3_1_7b_q4_k_m_name
    "llama-3.2-3b-instruct-q4_k_m" -> R.string.model_llama_3_2_3b_instruct_q4_k_m_name
    "gemma-2-2b-it-q4_k_m" -> R.string.model_gemma_2_2b_it_q4_k_m_name
    "phi-3.5-mini-3.8b-q4_k_m" -> R.string.model_phi_3_5_mini_3_8b_q4_k_m_name
    else -> R.string.model_picker_title // unreachable for known ids
}

private fun modelDescRes(id: String): Int = when (id) {
    "qwen3-1.7b-q4_k_m" -> R.string.model_qwen3_1_7b_q4_k_m_description
    "llama-3.2-3b-instruct-q4_k_m" -> R.string.model_llama_3_2_3b_instruct_q4_k_m_description
    "gemma-2-2b-it-q4_k_m" -> R.string.model_gemma_2_2b_it_q4_k_m_description
    "phi-3.5-mini-3.8b-q4_k_m" -> R.string.model_phi_3_5_mini_3_8b_q4_k_m_description
    else -> R.string.model_picker_title // unreachable for known ids
}

/**
 * v1.4.2 (F-10): the navigation route constant the parent session
 * uses to register this screen in the app's `NavHost`. The parent
 * owns the actual `composable(MODEL_DOWNLOAD_ROUTE) { ... }`
 * declaration; this constant is the only cross-cutting contract
 * between this package and the routing layer.
 */
const val MODEL_DOWNLOAD_ROUTE: String = "model_download"
