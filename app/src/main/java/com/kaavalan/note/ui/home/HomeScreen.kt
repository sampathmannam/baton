package com.kaavalan.note.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaavalan.note.R
import com.kaavalan.note.RootViewModel
import com.kaavalan.note.data.groups.GroupLabel
import com.kaavalan.note.data.person.PersonProfile
import com.kaavalan.note.features.capture.CameraLauncher
import com.kaavalan.note.features.capture.CaptureSheet
import com.kaavalan.note.features.capture.CaptureViewModel
import com.kaavalan.note.features.capture.NoteBar
import com.kaavalan.note.features.capture.PhotoCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
    onOpenPerson: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    captureViewModel: CaptureViewModel = hiltViewModel(),
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val sharedText by rootViewModel.sharedText.collectAsStateWithLifecycle()
    val quickCapture by rootViewModel.quickCapture.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var showAddPerson by remember { mutableStateOf(false) }
    var showAddGroup by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }

    LaunchedEffect(sharedText) {
        sharedText?.let { text ->
            captureViewModel.onTextChanged(text)
            captureViewModel.openSheet()
            rootViewModel.consumeSharedText()
        }
    }
    LaunchedEffect(quickCapture) {
        if (quickCapture) {
            captureViewModel.openSheet()
            rootViewModel.consumeQuickCapture()
        }
    }

    val pendingUri = remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingUri.value
        if (success && uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { PhotoCapture.recognize(context, uri) }.getOrElse { "" }
                }
                if (text.isNotBlank()) captureViewModel.onPhotoTextRecognized(text)
            }
        }
        pendingUri.value = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            CameraLauncher.newCaptureUri(context).also { uri ->
                pendingUri.value = uri
                cameraLauncher.launch(uri)
            }
        } else {
            captureViewModel.onPhotoError("Camera permission denied")
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) captureViewModel.onVoiceStart(context)
        else captureViewModel.onVoiceError("Microphone permission denied")
    }

    val loaded = state as? HomeUiState.Loaded
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tab_settings))
                    }
                },
            )
        },
        bottomBar = {
            NoteBar(
                onTextClick = captureViewModel::openSheet,
                onCameraClick = {
                    if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        CameraLauncher.newCaptureUri(context).also { uri ->
                            pendingUri.value = uri
                            cameraLauncher.launch(uri)
                        }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onMicClick = {
                    if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        captureViewModel.onVoiceStart(context)
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddPerson = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_person))
            }
        },
    ) { padding ->
        when (val current = state) {
            HomeUiState.Loading -> CenteredPeopleContent(padding) { CircularProgressIndicator() }
            is HomeUiState.Error -> CenteredPeopleContent(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.message)
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.timeline_retry)) }
                }
            }
            HomeUiState.Empty -> PeopleEmptyState(
                padding = padding,
                onAddPerson = { showAddPerson = true },
                onAddGroup = { showAddGroup = true },
                onImportContact = { showContactPicker = true },
            )
            is HomeUiState.Loaded -> PeopleContent(
                persons = current.persons,
                groupLabels = current.groupLabels,
                query = query,
                onQueryChange = { query = it },
                padding = padding,
                onOpenPerson = onOpenPerson,
                onAddGroup = { showAddGroup = true },
                onDeleteGroup = viewModel::deleteGroupLabel,
            )
        }
    }

    if (showAddPerson) {
        AddPersonSheet(
            onSave = { name, phone, rankOrRole, unit ->
                viewModel.createPerson(name, phone, rankOrRole, unit)
                showAddPerson = false
            },
            onDismiss = { showAddPerson = false },
        )
    }
    if (showAddGroup) {
        AddGroupLabelSheet(
            people = loaded?.persons.orEmpty(),
            onSave = { name, responsiblePersonId ->
                viewModel.createGroupLabel(name, responsiblePersonId)
                showAddGroup = false
            },
            onDismiss = { showAddGroup = false },
        )
    }
    if (showContactPicker) {
        com.kaavalan.note.ui.hierarchy.ContactPickerSheet(
            contactSyncService = viewModel.contactSyncService(),
            onPicked = { name, phone ->
                viewModel.importContact(name, phone)
                showContactPicker = false
            },
            onDismiss = { showContactPicker = false },
        )
    }
    if (captureState.isVisible) {
        CaptureSheet(
            viewModel = captureViewModel,
            onDismiss = {},
            onOpenAddPerson = {
                captureViewModel.dismissSheet()
                showAddPerson = true
            },
        )
    }
}

@Composable
private fun PeopleContent(
    persons: List<PersonProfile>,
    groupLabels: List<GroupLabel>,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    padding: PaddingValues,
    onOpenPerson: (String) -> Unit,
    onAddGroup: () -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    val visiblePeople = remember(persons, query.text) {
        val needle = query.text.trim()
        if (needle.isEmpty()) persons else persons.filter { person ->
            listOfNotNull(person.name, person.phone, person.rankOrRole, person.unit)
                .any { it.contains(needle, ignoreCase = true) }
        }
    }
    val names = remember(persons) { persons.associate { it.id to it.name } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "people-search") {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.people_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "group-heading") {
            SectionHeader(
                title = stringResource(R.string.people_group_labels),
                action = stringResource(R.string.group_label_add),
                onAction = onAddGroup,
            )
        }
        items(groupLabels, key = { "group-${it.id}" }) { label ->
            GroupLabelRow(label, names[label.responsiblePersonId], onDeleteGroup)
        }
        item(key = "people-heading") {
            Text(
                text = stringResource(R.string.tab_people),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (visiblePeople.isEmpty()) {
            item(key = "people-no-results") {
                Text(
                    stringResource(R.string.people_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(visiblePeople, key = { "person-${it.id}" }) { person ->
                PersonRow(person, onClick = { onOpenPerson(person.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun GroupLabelRow(
    label: GroupLabel,
    responsiblePersonName: String?,
    onDelete: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                responsiblePersonName
                    ?: stringResource(R.string.group_label_no_responsible_person),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDelete(label.id) }) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.group_label_delete))
        }
    }
}

@Composable
private fun PersonRow(person: PersonProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val roleAndUnit = listOfNotNull(person.rankOrRole, person.unit).joinToString(" • ")
            if (roleAndUnit.isNotEmpty()) {
                Text(
                    roleAndUnit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            person.phone?.let {
                Text(
                    text = "${stringResource(R.string.person_phone)}: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeopleEmptyState(
    padding: PaddingValues,
    onAddPerson: () -> Unit,
    onAddGroup: () -> Unit,
    onImportContact: () -> Unit,
) {
    CenteredPeopleContent(padding) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.home_empty_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAddPerson) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_add_person))
            }
            OutlinedButton(onClick = onAddGroup) { Text(stringResource(R.string.group_label_add)) }
            OutlinedButton(onClick = onImportContact) {
                Text(stringResource(R.string.hierarchy_contact_sync_open_picker))
            }
        }
    }
}

@Composable
private fun CenteredPeopleContent(
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
