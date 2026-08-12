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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.baton.app.ui.settings.SettingsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
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
    var showSettings by remember { mutableStateOf(false) }
    // M3-T6: when the user taps a row, set the id; the PersonDetailScreen
    // composable below is rendered conditionally. Tapping back clears it.
    var selectedPersonId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // M1-T7: when a shared text arrives, pre-fill the capture sheet
    // and open it. The user can then tap Extract (or edit) and the
    // usual capture flow takes over. We consume the value once the
    // sheet is open so the next share lands fresh.
    LaunchedEffect(sharedText) {
        val text = sharedText
        if (text != null) {
            captureViewModel.onTextChanged(text)
            captureViewModel.openSheet()
            rootViewModel.consumeSharedText()
        }
    }

    // M2-T5: when the user taps the quick-settings tile or the
    // home-screen widget, the deep link fires ACTION_QUICK_CAPTURE
    // and MainActivity signals via [RootViewModel.quickCapture].
    // We open the capture sheet (empty, focused on the text input)
    // and consume the signal so a config change does not re-open.
    LaunchedEffect(quickCapture) {
        if (quickCapture) {
            captureViewModel.openSheet()
            rootViewModel.consumeQuickCapture()
        }
    }

    // M2-T2: camera launcher. The launch returns `success=true`
    // when the user took a picture; we OCR the file at the
    // pending URI and feed the result to the capture VM.
    val pendingUri = remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingUri.value
        if (success && uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { PhotoCapture.recognize(context, uri) }
                        .getOrElse { "" }
                }
                if (text.isNotBlank()) {
                    captureViewModel.onPhotoTextRecognized(text)
                }
            }
        }
        pendingUri.value = null
    }

    // M2-T4: voice capture. The user taps the mic icon; we check
    // for RECORD_AUDIO permission first, request it if missing,
    // then start the foreground service.
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
                    actions = {
                        // M3-T4: settings entry point. The icon is
                        // placed in the top-app-bar actions slot so
                        // the people list still owns the bulk of the
                        // header. The bottom-sheet handles the actual
                        // sign-out flow.
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
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
            when (val s = state) {
                HomeUiState.Empty -> EmptyState(padding)
                HomeUiState.Loading -> Box(modifier = Modifier.fillMaxSize().padding(padding))
                is HomeUiState.Loaded -> PersonList(
                    persons = s.persons,
                    openCountByPersonId = s.openCountByPersonId,
                    padding = padding,
                    onPersonClick = { personId -> selectedPersonId = personId },
                )
                is HomeUiState.Error -> ErrorState(s.message, padding)
            }
        }

        // The single note bar floats at the bottom on top of every screen.
        // In M1 it sits on Home only; in M4 it moves to MainActivity so it
        // floats above Home, Today, and Settings.
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
                    // M2-T4: tap the mic to start voice capture.
                    // Check the permission first; the system
                    // permission dialog appears only on the first
                    // ask. After granted, the service starts.
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
            onDismiss = { /* sheet is closed via VM dismissSheet(); nothing to do */ },
        )
    }

    // M3-T4: Settings bottom-sheet. Tapping the gear in the top bar
    // flips `showSettings`; the sheet handles sign-out + closing
    // itself. The session observer in MainActivity picks up the
    // post-sign-out state and re-renders the AuthScreen.
    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
        )
    }

    // M3-T6: when a person row is tapped, render the detail
    // screen on top. `selectedPersonId` lives in this composable
    // so back-navigation just clears it. The detail screen's
    // ViewModel reads the id from SavedStateHandle; we use a
    // custom factory so the same Hilt-injected DAOs land in the
    // VM without a real nav graph.
    val personId = selectedPersonId
    if (personId != null) {
        PersonDetailScreenEntry(
            personId = personId,
            onBack = { selectedPersonId = null },
        )
    }
}

/**
 * M3-T6: a thin wrapper that resolves the [PersonDetailViewModel]
 * via Hilt's [PersonDetailViewModelFactoryEntryPoint] and hands
 * it to the standard [PersonDetailScreen] composable. The
 * `personId` is injected into the VM's [SavedStateHandle] so
 * the VM's `savedStateHandle.get<String>("personId")` returns
 * the right value.
 *
 * **No nav graph yet.** M3 ships in-place routing: the Home
 * tab flips `selectedPersonId` and this composable takes over.
 * When M3.5 lands a real `NavHost` (Today tab + deep links)
 * the wiring moves into `composable("person/{personId}")` and
 * this entry wrapper is removed. The VM contract is unchanged.
 */
@androidx.compose.runtime.Composable
private fun PersonDetailScreenEntry(
    personId: String,
    onBack: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
        ctx,
        PersonDetailViewModelFactoryEntryPoint::class.java,
    )
    val vm = androidx.lifecycle.viewmodel.compose.viewModel<PersonDetailViewModel>(
        key = "person-detail-$personId",
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            addInitializer(PersonDetailViewModel::class) {
                PersonDetailViewModel(
                    savedStateHandle = androidx.lifecycle.SavedStateHandle(
                        mapOf(PersonDetailViewModel.ARG_PERSON_ID to personId),
                    ),
                    personDao = entryPoint.personDao(),
                    instructionDao = entryPoint.instructionDao(),
                )
            }
        },
    )
    PersonDetailScreen(
        personId = personId,
        onBack = onBack,
        viewModel = vm,
    )
}

/**
 * Hilt entry point that exposes the singletons the
 * [PersonDetailScreenEntry] factory needs. Lives at the
 * [BatonApplication] scope.
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PersonDetailViewModelFactoryEntryPoint {
    fun personDao(): com.baton.app.data.local.PersonDao
    fun instructionDao(): com.baton.app.data.local.InstructionDao
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
            Spacer(Modifier.height(80.dp))  // leave room for the NoteBar
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
        item { Spacer(Modifier.height(80.dp)) }  // leave room for the NoteBar
    }
}

@Composable
private fun PersonRow(person: Person, openCount: Int, onClick: () -> Unit) {
    // M3-T5: the row is now a `Row` (name + designation on the
    // left, open-count badge on the right). When `openCount == 0`
    // we hide the badge entirely — the spec says "no shame
    // language", so the count is only shown when there's
    // something to look at. The badge uses the `tertiary` color
    // (a calm blue) rather than red; "carried over, not overdue"
    // is the design rule (spec §3.3).
    //
    // M3-T6: tapping the row navigates to the PersonDetailScreen
    // (the timeline). The click is on the whole row, not just
    // the badge.
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
            val sub = listOfNotNull(person.designation, person.station)
                .joinToString(" • ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (openCount > 0) {
            androidx.compose.material3.Surface(
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
