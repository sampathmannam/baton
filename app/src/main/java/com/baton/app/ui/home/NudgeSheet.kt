package com.baton.app.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.nudge.NudgeDraft
import com.baton.app.data.nudge.NudgeDraftGenerator
import com.baton.app.data.person.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M4-T4: nudge sheet. Edit + Copy + Share. On send, the
 * `nudge_drafts` row is marked SENT.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NudgeSheet(
    instruction: Instruction,
    person: Person?,
    onDismiss: () -> Unit,
    viewModel: NudgeSheetViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    LaunchedEffect(instruction.id) {
        viewModel.ensureDraft(instruction, person?.name)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Draft nudge", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Edit before sending. The draft is local; the message itself goes through WhatsApp or SMS, not Baton.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val current = draft
            if (current != null) {
                var text by remember(current.id) { mutableStateOf(current.draftText) }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        scope.launch { viewModel.updateText(current.id, it) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    label = { Text("Message") },
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Baton nudge", text))
                            scope.launch { viewModel.markSent(current.id, "COPY") }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Copy") }
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(intent, "Send nudge")
                            )
                            scope.launch { viewModel.markSent(current.id, "WHATSAPP") }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Share") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            } else {
                Text("Generating draft…", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@HiltViewModel
class NudgeSheetViewModel @Inject constructor(
    private val generator: NudgeDraftGenerator,
) : ViewModel() {

    private val _draft = MutableStateFlow<NudgeDraft?>(null)
    val draft: StateFlow<NudgeDraft?> = _draft.asStateFlow()

    fun ensureDraft(instruction: Instruction, personName: String?) {
        viewModelScope.launch {
            // Reuse the most recent live draft for this instruction
            // if one exists, otherwise generate a new one.
            val existing = generator.observeFor(instruction.id).first()
                .firstOrNull { it.status == "DRAFT" || it.status == "EDITED" }
            _draft.value = existing ?: generator.generate(instruction, personName)
        }
    }

    fun updateText(id: String, text: String) {
        viewModelScope.launch { generator.updateText(id, text) }
    }

    fun markSent(id: String, sentVia: String) {
        viewModelScope.launch { generator.markSent(id, sentVia) }
    }
}
