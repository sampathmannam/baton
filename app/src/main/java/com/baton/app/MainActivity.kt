package com.baton.app

import android.content.Intent
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
import com.baton.app.features.capture.ShareIntake
import com.baton.app.ui.auth.AuthScreen
import com.baton.app.ui.home.HomeScreen
import com.baton.app.ui.theme.BatonTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // M1-T7: pick up a shared text if the launch intent was a
        // share. (ShareReceiverActivity forwards to MainActivity
        // with EXTRA_SHARED_TEXT.)
        consumeSharedText(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // so getIntent() returns the new one
        consumeSharedText(intent)
    }

    private fun consumeSharedText(intent: Intent?) {
        val payload = ShareIntake.inspect(intent) ?: return
        when (payload) {
            is ShareIntake.Result.Text -> rootViewModel.onSharedText(payload.text)
            is ShareIntake.Result.Image -> {
                // The receiver activity already OCR'd the image and
                // forwarded the text. If the user shared the image
                // directly to MainActivity (e.g. via a deep-link),
                // there's no OCR'd text in the intent — we treat that
                // as "no pre-fill" and the user re-captures.
                // The deep-link path lands here; the share-sheet
                // path lands via ShareReceiverActivity which does
                // the OCR before forwarding.
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

    /**
     * M1-T7: an incoming shared text, exposed to the UI. The
     * HomeScreen observes this, opens the capture sheet, and
     * clears the value once the user sees it.
     */
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    fun onSharedText(text: String) {
        _sharedText.value = text
    }

    fun consumeSharedText() {
        _sharedText.value = null
    }
}
