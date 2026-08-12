package com.baton.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.auth.AuthSessionState
import com.baton.app.features.capture.ShareIntake
import com.baton.app.ui.auth.AuthScreen
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import javax.inject.Inject

/**
 * M3.5: real [NavHost] with the three primary tabs (Home, Today,
 * Settings). M3-T6 used in-place `selectedPersonId` state inside
 * `HomeScreen` to navigate to the person detail; the new
 * [com.baton.app.ui.home.HomeScreen] uses `navController.navigate("person/{id}")`.
 *
 * M4-T2: the bottom navigation bar hosts three tabs. The Settings
 * tab opens a `ModalBottomSheet` (the same sheet M3-T4 used as a
 * top-app-bar action); the gear icon is removed from the home
 * top bar in favour of the bottom nav entry.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()
    @javax.inject.Inject lateinit var briefNotifier: com.baton.app.data.brief.BriefNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeSharedText(intent)
        consumeQuickCapture(intent)
        // v1.0: schedule the daily brief push notification. The
        // schedule is a no-op if the user is on Android 13+ and
        // POST_NOTIFICATIONS isn't granted; the Today tab brief
        // still renders in-app regardless.
        briefNotifier.schedule()
        setContent {
            BatonTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val session by rootViewModel.sessionState.collectAsStateWithLifecycle()
                    when (session) {
                        AuthSessionState.Loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        AuthSessionState.Authenticated -> MainScaffold(rootViewModel = rootViewModel)
                        AuthSessionState.Unauthenticated -> AuthScreen()
                    }
                }
            }
        }
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
}

/**
 * M3.5: top-level scaffold. Three tabs + the M3-T6 person detail
 * sub-screen. The note bar floats above all of them (anchored in
 * [BatonScaffoldHost] below the Scaffold so it covers all screens).
 */
@Composable
private fun MainScaffold(rootViewModel: RootViewModel) {
    val navController = rememberNavController()
    var showSettings by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    Scaffold(
        bottomBar = {
            // M4-T2: only show the bottom nav on the three top-level
            // routes. Person detail hides the nav (focused context).
            if (currentRoute in setOf(Routes.HOME, Routes.TODAY)) {
                BottomNav(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
    }

    // M3-T4/M4-T2: the Settings tab still opens the same bottom
    // sheet. We render it as an overlay here so the nav bar and
    // home content stay mounted.
    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}

/**
 * M4-T2: bottom nav. Three tabs. The Home and Today entries use
 * `popUpTo(start)` to avoid growing the back stack. Settings opens
 * the bottom sheet (no nav entry — see [MainScaffold]).
 */
@Composable
private fun BottomNav(navController: NavHostController, currentRoute: String) {
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
            onClick = { /* opens the bottom sheet in the parent */ },
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

/** M3.5: thin wrapper for the person detail nav entry. Hilt + SavedStateHandle
 *  get the personId from the back stack. */
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
 * Top-level ViewModel that observes the auth session and exposes a
 * three-state [AuthSessionState] flow. Lives for the activity lifetime;
 * [HomeScreen] / [AuthScreen] decide what to render based on its state.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    val sessionState: StateFlow<AuthSessionState> = authRepository
        .observeSessionStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthSessionState.Loading,
        )

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
