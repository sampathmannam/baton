package com.baton.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.baton.app.data.auth.AuthRepository
import com.baton.app.data.auth.AuthSessionState
import com.baton.app.ui.auth.AuthScreen
import com.baton.app.ui.home.HomeScreen
import com.baton.app.ui.theme.BatonTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                        AuthSessionState.Authenticated -> HomeScreen()
                        AuthSessionState.Unauthenticated -> AuthScreen()
                    }
                }
            }
        }
    }
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
}
