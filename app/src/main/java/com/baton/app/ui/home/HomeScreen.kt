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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import com.baton.app.RootViewModel
import com.baton.app.data.person.Person
import com.baton.app.data.person.toEntity
import com.baton.app.features.capture.CameraLauncher
import com.baton.app.features.capture.CaptureSheet
import com.baton.app.features.capture.CaptureViewModel
import com.baton.app.features.capture.NoteBar
import com.baton.app.features.capture.PhotoCapture
import com.baton.app.features.capture.VoiceCaptureService
import com.baton.app.features.search.SearchBar
import com.baton.app.features.search.SearchViewModel
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

    // v1.5.4: the camera permission flow. The Photo button used to
    // fire `cameraLauncher.launch(uri)` unconditionally, which
    // crashed with `SecurityException: CAMERA permission denied` on
    // first install (the runtime perm hadn't been granted yet — the
    // v1.3 path assumed the system perm dialog appeared in the
    // `TakePicture` activity, which is not the case). The new
    // pattern: check the perm first, request it via the launcher if
    // missing, then launch the camera on grant. The same
    // `cameraLauncher.launch(uri)` only fires after the user
    // accepts the perm dialog. On denial, surface a friendly
    // message in the capture sheet (the `Microphone permission
    // denied` path is the analogue for the Voice button).
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = CameraLauncher.newCaptureUri(context)
            pendingUri.value = uri
            cameraLauncher.launch(uri)
        } else {
            captureViewModel.onPhotoError("Camera permission denied")
        }
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
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.home_title)) },
                    )
                    // v2.0 (Tier 1.3): the search bar below the
                    // top app bar. The results show on the same
                    // screen when the user types something
                    // matching; when the query is empty the
                    // existing PersonList renders.
                    val searchViewModel: SearchViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    SearchBar(viewModel = searchViewModel)
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddPerson = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_person))
                }
            },
        ) { padding ->
            val searchViewModel: SearchViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val query by searchViewModel.query.collectAsStateWithLifecycle()
            val results by searchViewModel.results.collectAsStateWithLifecycle()
            val personResults by searchViewModel.personResults.collectAsStateWithLifecycle()
            // v1.6.2: feed the visible people list to the search VM
            // so the people filter (new in v1.6.2) has data to work
            // with. When state is Loaded we have a list; otherwise
            // the VM sees an empty list (it will receive a non-empty
            // list on the next state change thanks to
            // `WhileSubscribed(5_000)`).
            val visiblePeople = (state as? HomeUiState.Loaded)?.persons
                ?.map { it.toEntity() }
                .orEmpty()
            LaunchedEffect(visiblePeople) {
                searchViewModel.setVisiblePeople(visiblePeople)
            }
            if (query.isNotEmpty()) {
                HomeScreenSearchResults(
                    personResults = personResults,
                    instructionResults = results,
                    padding = padding,
                    onPersonClick = onOpenPerson,
                )
            } else {
                when (val s = state) {
                    HomeUiState.Empty -> EmptyState(
                        padding = padding,
                        onAddPersonClick = { showAddPerson = true },
                    )
                    HomeUiState.Loading -> LoadingSkeleton(padding)
                    is HomeUiState.Loaded -> PersonList(
                        persons = s.persons,
                        openCountByPersonId = s.openCountByPersonId,
                        stalePersonIds = s.stalePersonIds,
                        padding = padding,
                        onPersonClick = onOpenPerson,
                    )
                    is HomeUiState.Error -> ErrorState(s.message, padding)
                }
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
                    // v1.5.4: gate the camera launch on the
                    // `CAMERA` runtime perm. The v1.3 path fired
                    // `cameraLauncher.launch(uri)` directly, which
                    // crashed on first install with
                    // `SecurityException` because the perm hadn't
                    // been granted yet. The system camera app
                    // does not surface the perm dialog itself
                    // (the calling app must). Mirror the
                    // mic-permission pattern: check, request if
                    // missing, launch on grant.
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        val uri = CameraLauncher.newCaptureUri(context)
                        pendingUri.value = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
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
            // v1.4 (PHONE-FINDING-8): when the user has no people
            // yet, the inline "Add a person first" card on the
            // capture sheet points its "Add person" button at the
            // same entry point the Home screen uses, so the user
            // lands in the same AddPerson form. The sheet is
            // dismissed before the AddPerson sheet opens so the
            // back stack is single-step.
            onOpenAddPerson = {
                captureViewModel.dismissSheet()
                showAddPerson = true
            },
        )
    }
}

@Composable
private fun EmptyState(
    padding: PaddingValues,
    onAddPersonClick: () -> Unit,
) {
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
            // v1.4 (PHONE-FINDING-1): the FAB in the Scaffold above
            // uses primaryContainer which is too low-contrast against
            // the dark surface — new users miss the entry point. The
            // empty-state copy now carries its own prominent primary-
            // coloured "Add person" button so the first thing a
            // brand-new user sees gives them an obvious action. The
            // FAB is still present (so power users have a constant
            // anchor), but the empty-state button is the
            // first-impression entry point. The button uses
            // colorScheme.primary (not primaryContainer) so it stands
            // out against both the dark and the light surface.
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAddPersonClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .semantics { contentDescription = "Add person" },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.home_add_person),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun PersonList(
    persons: List<Person>,
    openCountByPersonId: Map<String, Int>,
    stalePersonIds: Set<String>,
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
                isStale = person.id in stalePersonIds,
                onClick = { onPersonClick(person.id) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/**
 * v2.0 (Tier 1.3): the search results. Renders two sections —
 * "People" (from [personResults]) and "Instructions" (from
 * [instructionResults]) — so the placeholder "Search people
 * and instructions" is honest. v1.6.2 added the People section;
 * the Instructions section keeps the per-person grouping from
 * v1.6.0.1.
 */
@Composable
fun HomeScreenSearchResults(
    personResults: List<com.baton.app.data.local.entities.PersonEntity>,
    instructionResults: List<com.baton.app.data.local.entities.InstructionEntity>,
    padding: PaddingValues,
    onPersonClick: (String) -> Unit,
) {
    if (personResults.isEmpty() && instructionResults.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        if (personResults.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_section_people),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(items = personResults, key = { it.id }) { person ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Open person",
                            onClick = { onPersonClick(person.id) },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val sub = listOfNotNull(person.designation, person.station)
                        .joinToString(" \u00b7 ")
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (instructionResults.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_section_instructions),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val byPerson = instructionResults.groupBy { it.personId }
            byPerson.forEach { (personId, list) ->
                item {
                    Text(
                        text = if (personId == null) {
                            stringResource(R.string.search_unassigned)
                        } else {
                            // v1.6.2: show "(unassigned)" or a short
                            // id; the full name lookup happens via
                            // the visible people list. For now we
                            // truncate the id (kept from v1.6.0.1).
                            personId.take(20)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                items(items = list, key = { it.id }) { ins ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = "Open person",
                                onClick = { personId?.let(onPersonClick) },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = ins.title,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        // v1.6.2: skip the body when it duplicates the
                        // title (v1.6.1 capture stores a single line in
                        // both). Same fix as TodayScreen.InstructionCard.
                        if (ins.title != ins.rawText) {
                            Text(
                                text = ins.rawText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/**
 * v1.5.3 (VAULT-009): the loading skeleton. Three quiet
 * placeholder rows that look like real person rows but are
 * just grey rectangles. The user sees something happen on
 * screen from the first frame, instead of a blank white
 * section that looks like a crash.
 *
 * We use the project's spec §3 colour token (surfaceVariant)
 * for the placeholder fill so it works on both light and dark
 * themes without an explicit theme check.
 */
@Composable
private fun LoadingSkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        repeat(4) { index ->
            SkeletonRow()
            if (index < 3) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SkeletonRow() {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Title placeholder — wider bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(16.dp),
            ) {}
            // Subtitle placeholder — narrower bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(12.dp),
            ) {}
        }
    }
}

@Composable
private fun PersonRow(person: Person, openCount: Int, isStale: Boolean, onClick: () -> Unit) {
    // v1.3 (F-19): the row is a clickable list item — TalkBack needs
    // to know it opens the person detail screen. onClickLabel replaces
    // the generic "double-tap to activate" with the action name.
    val openPersonLabel = stringResource(R.string.a11y_person_row_open)
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openPersonLabel) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(person.name, style = MaterialTheme.typography.titleMedium)
                // M4-T3: stale surface. After 3 days of no activity on
                // an OUTGOING instruction (spec §8.2), the row shows
                // a quiet amber dot. Not red, not a count-up — just
                // "this has been quiet".
                if (isStale) {
                    Spacer(Modifier.size(8.dp))
                    // v1.3 (F-19): the stale dot is a pure visual
                    // cue; TalkBack should announce what it means
                    // (no activity for 3+ days), not "dot".
                    val staleDesc = stringResource(R.string.a11y_person_stale_indicator)
                    Surface(
                        modifier = Modifier
                            .size(8.dp)
                            .semantics { contentDescription = staleDesc },
                        color = androidx.compose.ui.graphics.Color(0xFFD9A05B),
                        contentColor = androidx.compose.ui.graphics.Color.Transparent,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ) {}
                }
            }
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
            // v1.3 (F-19): the count badge is just a number on a
            // tinted background. TalkBack would read "3" without
            // context; the semantics modifier replaces that with
            // "3 open instructions" so the user knows what the
            // number is counting.
            val countDesc = if (openCount == 1) {
                stringResource(R.string.a11y_person_count_badge_one)
            } else {
                stringResource(R.string.a11y_person_count_badge, openCount)
            }
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .semantics { contentDescription = countDesc },
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
