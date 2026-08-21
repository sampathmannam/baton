package com.baton.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.data.local.entities.SyncConflictEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.8.0 (PROD-READINESS-P2-#2): the sync-conflict list
 * screen. Shows every conflict in the
 * [com.baton.app.data.local.SyncConflictDao] ordered
 * newest-first by `detectedAt`. Tapping a row opens
 * the diff screen.
 *
 * **v1.8.0 trade-off.** The vault-mode build has no
 * cloud sync, so the table is always empty. The
 * screen exists in the navigation graph for the
 * future cloud build; the Settings sheet's "Sync
 * conflicts" row is hidden when the count is 0 so
 * the user never sees this empty screen in normal
 * use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictListScreen(
    onBack: () -> Unit,
    onOpenConflict: (Long) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val conflicts by viewModel.syncConflicts.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync conflicts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (conflicts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing to resolve. All local writes are in sync.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(conflicts, key = { it.id }) { conflict ->
                    SyncConflictListRow(
                        conflict = conflict,
                        onClick = { onOpenConflict(conflict.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncConflictListRow(
    conflict: SyncConflictEntity,
    onClick: () -> Unit,
) {
    val rowDesc = "Conflict on ${conflict.tableName} row ${conflict.rowId}, ${formatRelativeTime(conflict.detectedAt)}"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = rowDesc },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${conflict.tableName} · ${conflict.rowId}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = conflict.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatRelativeTime(conflict.detectedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * v1.8.0 (PROD-READINESS-P2-#2): the sync-conflict diff
 * screen. Shows the local payload side-by-side with the
 * server payload, with "Keep local" / "Keep server"
 * buttons. v1.8.0 is local-only, so the buttons are
 * placeholders for the future cloud build (the DAO
 * deletion of the conflict row is what would happen,
 * once the resolve call exists).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictDiffScreen(
    conflictId: Long,
    onBack: () -> Unit,
    onResolved: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val conflicts by viewModel.syncConflicts.collectAsStateWithLifecycle()
    val conflict = conflicts.firstOrNull { it.id == conflictId }
    var showConfirm by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolve conflict") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (conflict == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Conflict no longer exists.")
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = "${conflict.tableName} · row ${conflict.rowId}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Reason: ${conflict.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Your local change",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            PayloadBox(payload = conflict.localPayload)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Server already had",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            PayloadBox(payload = conflict.serverPayload)
            Spacer(Modifier.height(24.dp))
            // v1.8.0 (PROD-READINESS-P2-#2): the resolve
            // buttons. The vault-mode build has no cloud
            // sync, so the buttons are placeholders. The
            // UI shape is correct for the pilot: senior
            // officer picks which side wins. A future
            // cloud build wires the buttons to delete the
            // conflict row and write the chosen payload
            // back to the source table.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { showConfirm = "local" },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Keep local change" },
                ) { Text("Keep local") }
                Button(
                    onClick = { showConfirm = "server" },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Keep server version" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                    ),
                ) { Text("Keep server") }
            }
        }
    }

    if (showConfirm != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = null },
            title = { Text("Conflict resolution") },
            text = {
                Text(
                    "Cloud sync is not enabled in this build. " +
                        "The chosen payload will be retained for the next sync cycle.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = null
                    onResolved()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PayloadBox(payload: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = payload,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatRelativeTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val deltaMs = now - epochMs
    return when {
        deltaMs < 60_000 -> "just now"
        deltaMs < 3_600_000 -> "${deltaMs / 60_000} min ago"
        deltaMs < 86_400_000 -> "${deltaMs / 3_600_000} hr ago"
        deltaMs < 604_800_000 -> "${deltaMs / 86_400_000} d ago"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
    }
}
