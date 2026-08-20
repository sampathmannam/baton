package com.baton.app.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baton.app.R

/**
 * v2.0 T3-3 (threat-model-led settings copy): a full-screen
 * text view of the threat model. The user opens it from
 * Settings -> Threat model. No code logic, just a long-
 * form explanation of what the app does and does not protect
 * against.
 *
 * **Why a full screen?** The threat model is 5-6 short
 * paragraphs. A dialog or a sheet would either truncate it
 * or force a lot of vertical scrolling inside a modal. A
 * dedicated screen with a back button reads better on a
 * phone in landscape and respects the user's reading time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatModelScreen(
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.threat_model_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.threat_model_back),
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.threat_model_lead),
                style = MaterialTheme.typography.bodyLarge,
            )
            ThreatModelSection(
                title = stringResource(R.string.threat_model_section_storage),
                body = stringResource(R.string.threat_model_section_storage_body),
            )
            ThreatModelSection(
                title = stringResource(R.string.threat_model_section_locked),
                body = stringResource(R.string.threat_model_section_locked_body),
            )
            ThreatModelSection(
                title = stringResource(R.string.threat_model_section_unlocked),
                body = stringResource(R.string.threat_model_section_unlocked_body),
            )
            ThreatModelSection(
                title = stringResource(R.string.threat_model_section_backup),
                body = stringResource(R.string.threat_model_section_backup_body),
            )
            ThreatModelSection(
                title = stringResource(R.string.threat_model_section_vault),
                body = stringResource(R.string.threat_model_section_vault_body),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.threat_model_closing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThreatModelSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
