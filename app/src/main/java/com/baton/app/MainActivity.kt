package com.baton.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.auth.AuthSessionState
import com.baton.app.data.preferences.BatonPreferences
import com.baton.app.data.preferences.ThemeMode
import com.baton.app.data.sync.NetworkObserver
import com.baton.app.data.undo.UndoController
import com.baton.app.features.capture.ShareIntake
import com.baton.app.features.onboarding.OnboardingScreen
import com.baton.app.features.search.SearchViewModel
import com.baton.app.features.theme.ThemeViewModel
import com.baton.app.features.vault.VaultExportSheet
import com.baton.app.features.vault.VaultImportSheet
import com.baton.app.ui.auth.AuthScreen
import com.baton.app.ui.components.OfflineIndicator
import com.baton.app.ui.home.HomeScreen
import com.baton.app.ui.settings.SettingsSheet
import com.baton.app.ui.theme.BatonTheme
import com.baton.app.ui.today.TodayScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * M3.5: real [NavHost] with the three primary tabs.
 *
 * v1.4 (PHONE-FINDING-10 / F-02): request POST_NOTIFICATIONS
 * after sign-in via rememberLauncherForActivityResult. The
 * launcher is top-level in MainScaffold so it survives
 * recomposition. A rememberSaveable flag stops re-prompting
 * across config changes.
 *
 * v1.4 (PHONE-FINDING-6): NetworkObserver singleton registered
 * in onStart / unregistered in onStop. The current isOnline is
 * rendered as an OfflineIndicator overlay at the top of the
 * Scaffold.
 *
 * v2.0 (Tier 1.2 + Tier 1.4 + Tier 1.6): first-run onboarding
 * gates the [MainScaffold]; theme is read from the [ThemeViewModel]
 * (DataStore-backed) and passed to [BatonTheme]; the
 * [UndoController] exposes the last [com.baton.app.data.undo.UndoableAction]
 * which the [SnackbarHostState] listens to and shows a 5 s
 * "Undo" affordance.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()
    @javax.inject.Inject lateinit var briefNotifier: com.baton.app.data.brief.BriefNotifier
    @javax.inject.Inject lateinit var networkObserver: NetworkObserver
    @javax.inject.Inject lateinit var preferences: BatonPreferences
    @javax.inject.Inject lateinit var undoController: UndoController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeSharedText(intent)
        consumeQuickCapture(intent)
        briefNotifier.schedule()
        setContent {
            val themeViewModel: ThemeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDark = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            BatonTheme(darkTheme = useDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val seen by preferences.hasSeenOnboarding.collectAsStateWithLifecycle(initialValue = false)
                    if (!seen) {
                        OnboardingScreen(onDone = { /* DataStore flips; recomposition picks it up */ })
                    } else {
                        MainScaffold(
                            rootViewModel = rootViewModel,
                            networkObserver = networkObserver,
                            undoController = undoController,
                            preferences = preferences,
                            onRequestNotificationsPermission = ::requestPostNotifications,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        networkObserver.start()
    }

    override fun onStop() {
        networkObserver.stop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSharedText(intent)
        consumeQuickCapture(intent)
    }

    private fun consumeQuickCapture(intent: Intent?) {
        if (intent?.action == com.baton.app.features.capture.BatonCaptureWidget.ACTION_QUICK_CAPTURE) {
            rootViewModel.onQuickCapture()
        }
    }

    private fun consumeSharedText(intent: Intent?) {
        val payload = ShareIntake.inspect(intent) ?: return
        when (payload) {
            is ShareIntake.Result.Text -> rootViewModel.onSharedText(payload.text)
            is ShareIntake.Result.Image -> {
                // Receiver activity already OCR'd; main entry is text.
            }
        }
    }

    /**
     * v1.4 (PHONE-FINDING-10 / F-02): ask the user for the
     * POST_NOTIFICATIONS permission. On Android 13+ this is a
     * runtime grant; on older versions the manifest declaration
     * is enough. The launcher is created in [MainScaffold] (a
     * Composable, so it survives recomposition) and the
     * activity-level [requestPostNotifications] entry point
     * fires a Toast rationale + delegates to the launcher via
     * the [notifLauncher] reference.
     *
     * If the permission is already held (e.g. the user toggled
     * it on in system settings while the app was backgrounded)
     * we re-queue the brief directly so the WorkManager job
     * reflects the new state.
     */
    fun requestPostNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            briefNotifier.schedule()
            return
        }
        Toast.makeText(
            this,
            R.string.notifications_rationale,
            Toast.LENGTH_LONG,
        ).show()
        val launcher = notifLauncher
        if (launcher == null) {
            Toast.makeText(
                this,
                "Notifications launcher not ready; please retry.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * v1.4 (PHONE-FINDING-10 / F-02): the [MainScaffold]
     * composable assigns this launcher when it builds. The
     * assignment happens in a Composable, so it survives
     * recomposition; we keep a strong reference here so the
     * activity-level [requestPostNotifications] entry point
     * can fire it.
     */
    internal var notifLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null
}

/**
 * M3.5: top-level scaffold.
 *
 * v1.4: hosts the POST_NOTIFICATIONS launcher and renders the
 * OfflineIndicator as a top-end overlay above the inner screens'
 * TopAppBars.
 *
 * v2.0 (Tier 1.6): hosts a single [SnackbarHostState] for the
 * undo snackbar. The host observes [UndoController.last] and
 * shows a 5 s "Undo" affordance when a destructive action
 * pushes a new [com.baton.app.data.undo.UndoableAction]. The
 * snackbar auto-clears the action on dismiss / timeout.
 */
@Composable
private fun MainScaffold(
    rootViewModel: RootViewModel,
    networkObserver: NetworkObserver,
    undoController: UndoController,
    preferences: BatonPreferences,
    onRequestNotificationsPermission: () -> Unit,
) {
    val navController = rememberNavController()
    var showSettings by remember { mutableStateOf(false) }
    var showVaultExport by remember { mutableStateOf(false) }
    var showVaultImport by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val isOnline by networkObserver.isOnline.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastUndo by undoController.last.collectAsStateWithLifecycle()
    val undoLabel = stringResource(R.string.undo)

    // v2.0: a single SnackbarHostState listens to the UndoController
    // flow. When a new action arrives, the snackbar shows "Action
    // undone" + an "Undo" button. 5 s auto-dismissal maps to
    // SnackbarDuration.Short. On undo, the controller's
    // `undoLast()` re-inserts the row.
    LaunchedEffect(lastUndo) {
        val action = lastUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${action.label} ${action.id.take(6)}",
            actionLabel = undoLabel,
            withDismissAction = true,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> {
                scope.launch { undoController.undoLast() }
            }
            SnackbarResult.Dismissed -> {
                undoController.clear()
            }
        }
    }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            (context as? MainActivity)?.briefNotifier?.schedule()
        }
    }
    SideEffect {
        (context as? MainActivity)?.notifLauncher = notifLauncher
    }

    var hasRequestedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasRequestedNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasRequestedNotifications = true
            onRequestNotificationsPermission()
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in setOf(Routes.HOME, Routes.TODAY)) {
                BottomNav(
                    navController = navController,
                    currentRoute = currentRoute,
                    onSettingsClick = { showSettings = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenSettings = { showSettings = true },
                        onOpenPerson = { id -> navController.navigate("person/$id") },
                    )
                }
                composable(Routes.TODAY) {
                    TodayScreen()
                }
                composable(Routes.PERSON) { entry ->
                    val personId = entry.arguments?.getString("personId") ?: return@composable
                    HomeScreenPersonDetail(
                        personId = personId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            OfflineIndicator(
                isOnline = isOnline,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            onDismiss = { showSettings = false },
            onVaultExport = { showSettings = false; showVaultExport = true },
            onVaultImport = { showSettings = false; showVaultImport = true },
        )
    }
    if (showVaultExport) {
        VaultExportSheet(
            onDismiss = { showVaultExport = false },
            onExported = { showVaultExport = false },
        )
    }
    if (showVaultImport) {
        VaultImportSheet(
            onDismiss = { showVaultImport = false },
            onImported = { showVaultImport = false },
        )
    }
}

/**
 * M4-T2: bottom nav. Three tabs. The Home and Today entries use
 * `popUpTo(start)` to avoid growing the back stack. Settings opens
 * the bottom sheet.
 *
 * **v1.2 root-cause fix (BUG-MAIN-005):** the Settings entry's
 * `onClick` was a literal `{}` — the parent [MainScaffold] owns
 * the `showSettings` state but no callback was wired down.
 */
@Composable
private fun BottomNav(
    navController: NavHostController,
    currentRoute: String,
    onSettingsClick: () -> Unit,
) {
    NavigationBar {
        NavEntry(
            label = stringResource(R.string.tab_home),
            icon = Icons.Default.Home,
            route = Routes.HOME,
            currentRoute = currentRoute,
            onClick = {
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
        NavEntry(
            label = stringResource(R.string.tab_today),
            icon = Icons.Default.Today,
            route = Routes.TODAY,
            currentRoute = currentRoute,
            onClick = {
                navController.navigate(Routes.TODAY) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
        NavEntry(
            label = stringResource(R.string.tab_settings),
            icon = Icons.Default.Settings,
            route = "settings-tab",
            currentRoute = currentRoute,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavEntry(
    label: String,
    icon: ImageVector,
    route: String,
    currentRoute: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

/** M3.5: thin wrapper for the person detail nav entry. */
@Composable
private fun HomeScreenPersonDetail(personId: String, onBack: () -> Unit) {
    val vm: com.baton.app.ui.home.PersonDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    com.baton.app.ui.home.PersonDetailScreen(
        personId = personId,
        onBack = onBack,
        viewModel = vm,
    )
}

object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    const val PERSON = "person/{personId}"
    fun person(id: String) = "person/$id"
}

/**
 * Top-level ViewModel that owns the ephemeral UI events (shared text
 * from another app, the widget / tile "quick capture" pulse).
 *
 * v1.5.0 vault mode: no auth gate. The auth state machinery is no
 * longer observed at the root; the app opens straight to the home
 * tabs and SQLCipher keeps the local Room DB encrypted at rest.
 */
@HiltViewModel
class RootViewModel @Inject constructor() : ViewModel() {

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    fun onSharedText(text: String) {
        _sharedText.value = text
    }

    fun consumeSharedText() {
        _sharedText.value = null
    }

    private val _quickCapture = MutableStateFlow(false)
    val quickCapture: StateFlow<Boolean> = _quickCapture.asStateFlow()

    fun onQuickCapture() {
        _quickCapture.value = true
    }

    fun consumeQuickCapture() {
        _quickCapture.value = false
    }
}
