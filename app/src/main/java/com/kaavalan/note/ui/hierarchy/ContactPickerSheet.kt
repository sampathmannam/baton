package com.kaavalan.note.ui.hierarchy

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kaavalan.note.R
import com.kaavalan.note.data.person.ContactSyncService

/**
 * v2.0 (Hierarchy): contact-picker sheet that wraps
 * [ContactSyncService]. Reads `READ_CONTACTS` on first entry, fetches
 * the device's contact list (up to 50 entries — see the service's
 * `limit` parameter), and surfaces a tappable list of `displayName`
 * + `phone` rows. On tap, the parent (AddPersonSheet) fills the
 * name / designation / station fields from the chosen contact.
 *
 * Why this exists: the existing `AddPersonSheet` is keyboard-only
 * (name / designation / station text fields). For an officer
 * transferring an existing contact list, typing each entry by hand
 * is the worst path. This sheet makes "import from phone" one tap
 * away from the same FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerSheet(
    contactSyncService: ContactSyncService,
    onPicked: (displayName: String, phone: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hasPermission by remember { mutableStateOf(contactSyncService.hasPermission()) }
    var candidates by remember { mutableStateOf<List<ContactSyncService.ContactCandidate>>(emptyList()) }
    var permissionAskedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            permissionAskedOnce = true
            hasPermission = granted
            if (granted) candidates = contactSyncService.fetchContactCandidates()
        },
    )

    LaunchedEffect(hasPermission) {
        if (hasPermission && candidates.isEmpty()) {
            candidates = contactSyncService.fetchContactCandidates()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.hierarchy_contact_sync_picker_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            when {
                !hasPermission && !permissionAskedOnce -> {
                    Text(
                        text = stringResource(R.string.hierarchy_contact_sync_permission_rationale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.hierarchy_contact_sync_grant))
                    }
                }
                !hasPermission && permissionAskedOnce -> {
                    Text(
                        text = stringResource(R.string.hierarchy_contact_sync_permission_rationale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                candidates.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.hierarchy_contact_sync_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                        items(candidates, key = { it.phone }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPicked(c.displayName, c.phone)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(c.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
