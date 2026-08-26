package com.kaavalan.note.features.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaavalan.note.R
import com.kaavalan.note.data.audit.VerifyResult
import com.kaavalan.note.data.local.entities.AuditChainEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v2.0 (PM rating): an in-app audit-log viewer. The audit
 * chain has been writing rows since v1.8.0 (see
 * [com.kaavalan.note.data.audit.AuditChainWriter]) but the
 * user had no way to see the entries. This screen:
 *
 *  - Lists recent audit events (table, kind, row, time).
 *  - Runs the [com.kaavalan.note.data.audit.AuditChainVerifier]
 *    on demand and renders the result (Intact or BrokenAt).
 *
 * **Why a screen, not a sheet.** The audit log can have
 * thousands of rows. A sheet that lazily loads would still
 * be small per page, but the user needs a "verify" button
 * that's a primary action. A full screen with a TopAppBar
 * gives the action room.
 *
 * **What this is NOT.** This is not a forensic tool. The
 * payload of each event is JSON; we render a short
 * preview (first 80 chars) but the user cannot expand it
 * in v2.0.0. A future v2.0.1 could add a per-row detail
 * screen with the full payload + both hashes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    onClose: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.audit_log_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // The "Verify chain" card. A primary action with
            // a clear outcome (Intact → green check, BrokenAt
            // → red icon + table + row + index).
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.audit_log_verify_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.audit_log_verify_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.verify() },
                        enabled = state !is AuditLogViewModel.VerifyState.Running,
                    ) {
                        Text(stringResource(R.string.audit_log_verify_button))
                    }
                    VerifyStateRow(state = state)
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.audit_log_recent_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.audit_log_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(events, key = { it.id }) { event ->
                        AuditEventRow(event = event)
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyStateRow(state: AuditLogViewModel.VerifyState) {
    when (state) {
        is AuditLogViewModel.VerifyState.Idle -> {
            Text(
                text = stringResource(R.string.audit_log_verify_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is AuditLogViewModel.VerifyState.Running -> {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
            )
        }
        is AuditLogViewModel.VerifyState.Intact -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.audit_log_verify_intact,
                        state.eventCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        is AuditLogViewModel.VerifyState.Broken -> {
            // Capture into a local val so the inner
            // @Composable lambdas (which can interfere
            // with smart-cast) see the typed value.
            val broken = state.result
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.audit_log_verify_broken),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.audit_log_verify_broken_detail,
                        broken.index,
                        broken.tableName,
                        broken.rowPk,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuditEventRow(event: AuditChainEventEntity) {
    val timestamp = remember(event.createdAtMs) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(Date(event.createdAtMs))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${event.tableName} · ${event.kind}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "row ${event.rowId} · $timestamp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // v2.0.0: payload preview only. A per-row detail
            // screen with the full payload + both hashes is
            // a v2.0.1 follow-up.
            val preview = remember(event.payload) {
                if (event.payload.length > 80) {
                    event.payload.take(80) + "…"
                } else {
                    event.payload
                }
            }
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
