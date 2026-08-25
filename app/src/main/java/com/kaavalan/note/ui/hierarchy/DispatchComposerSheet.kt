package com.kaavalan.note.ui.hierarchy

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kaavalan.note.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchComposerSheet(initialText: String, senderName: String, senderDesignation: String?, senderDivision: String?, onDismiss: () -> Unit, onSaved: (String) -> Unit, viewModel: DispatchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rawText by remember { mutableStateOf(initialText) }
    var pickerOpen by remember { mutableStateOf(false) }
    var dispatchOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.hierarchy_audience_picker_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = rawText, onValueChange = { rawText = it }, label = { Text("Instruction") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
            Spacer(Modifier.height(12.dp))
            AssistChip(onClick = { pickerOpen = true }, label = { Text(state.audience?.label ?: "Pick audience", style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) })
            Spacer(Modifier.height(16.dp))
            Button(onClick = { dispatchOpen = true }, enabled = state.audience != null && rawText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Next") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickerOpen) { AudiencePickerSheet(state.roster, onPicked = { viewModel.setAudience(it); pickerOpen = false }, onDismiss = { pickerOpen = false }) }
    if (dispatchOpen) {
        // Pass the composer's viewmodel explicitly so the inner sheet
        // shares the same DispatchViewModel instance (and therefore the
        // same audience/dueAtMs/channels state) even if the inner
        // sheet is ever lifted into a different scope (e.g. a
        // separate bottom-sheet fragment).
        DispatchSheet(viewModel = viewModel, title = rawText.take(60), rawText = rawText, senderName = senderName, senderDesignation = senderDesignation, senderDivision = senderDivision, onDismiss = { dispatchOpen = false }, onSent = { id -> dispatchOpen = false; onSaved(id) })
    }
}
