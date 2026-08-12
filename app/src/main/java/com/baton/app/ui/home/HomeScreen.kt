package com.baton.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.RootViewModel
import com.baton.app.data.person.Person
import com.baton.app.features.capture.CameraLauncher
import com.baton.app.features.capture.CaptureSheet
import com.baton.app.features.capture.CaptureViewModel
import com.baton.app.features.capture.NoteBar
import com.baton.app.features.capture.PhotoCapture
import com.baton.app.features.capture.VoiceCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * M3.5: Home tab. M3-T6 in-place routing is replaced with a real
 * `composable("person/{personId}")` entry in the parent
 * [com.baton.app.MainActivity]'s NavHost. The HomeScreen no longer
 * owns the [selectedPersonId] state; it calls [onOpenPerson] to
 * trigger the nav.
 *
 * M3-T4: the settings gear in the top bar is removed (M4-T2
 * promotes Settings to a bottom-nav tab). The sheet is owned by
 * MainScaffold and is opened via [onOpenSettings].
 */
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
    var showAddPerson by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sharedText) {
        val text = sharedText
        if (text != null) {
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
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingUri.value
        if (success && uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { PhotoCapture.recognize(context, uri) }.getOrElse { "" }
                }
                if (text.isNotBlank()) {
                    captureViewModel.onPhotoTextRecognized(text)
                }
            }
        }
        pendingUri.value = null
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            captureViewModel.onVoiceStart(context)
        } else {
            captureViewModel.onVoiceError("Microphone permission denied")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_title)) },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddPerson = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_person))
                }
            },
        ) { padding ->
            when (val s = state) {
                HomeUiState.Empty -> EmptyState(padding)
                HomeUiState.Loading -> Box(modifier = Modifier.fillMaxSize().padding(padding))
                is HomeUiState.Loaded -> PersonList(
                    persons = s.persons,
                    openCountByPersonId = s.openCountByPersonId,
                    padding = padding,
                    onPersonClick = onOpenPerson,
                )
                is HomeUiState.Error -> ErrorState(s.message, padding)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            NoteBar(
                onTextClick = { captureViewModel.openSheet() },
                onCameraClick = {
                    val uri = CameraLauncher.newCaptureUri(context)
                    pendingUri.value = uri
                    cameraLauncher.launch(uri)
                },
                onMicClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        captureViewModel.onVoiceStart(context)
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        }
    }

    if (showAddPerson) {
        AddPersonSheet(
            onSave = { name, designation, station ->
                scope.launch {
                    viewModel.createPerson(name, designation, station)
                }
                showAddPerson = false
            },
            onDismiss = { showAddPerson = false },
        )
    }

    if (captureState.isVisible) {
        CaptureSheet(
            viewModel = captureViewModel,
            onDismiss = { /* sheet closed via VM */ },
        )
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PersonList(
    persons: List<Person>,
    openCountByPersonId: Map<String, Int>,
    padding: PaddingValues,
    onPersonClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        items(items = persons, key = { it.id }) { person ->
            PersonRow(
                person = person,
                openCount = openCountByPersonId[person.id] ?: 0,
                onClick = { onPersonClick(person.id) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun PersonRow(person: Person, openCount: Int, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium)
            val sub = listOfNotNull(person.designation, person.station).joinToString(" • ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (openCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = openCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
