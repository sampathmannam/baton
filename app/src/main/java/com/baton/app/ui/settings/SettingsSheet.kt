package com.baton.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baton.app.R
import kotlinx.coroutines.launch

/**
 * M3-T4: Settings bottom sheet.
 *
 * Currently a single screen with a "Sign out" button. The sheet is
 * a ModalBottomSheet that lives in [com.baton.app.ui.home.HomeScreen]
 * and is triggered by the gear icon in the top app bar.
 *
 * **Why a sheet, not a separate tab.** The M3 plan lists Settings
 * as one of the three primary tabs (Home, Today, Settings). The
 * Today tab is a brief screen that lands in M4 (it depends on the
 * MindAnchor AppState IPC). For M3, Settings is a single sign-out
 * affordance; promoting it to a tab is a future refactor once Today
 * exists and the navigation graph is needed.
 *
 * **Sign-out flow.** The sheet calls into [SettingsViewModel.signOut]
 * which:
 *  1. Calls [com.baton.app.data.auth.AuthRepository.signOut] to
 *     drop the Supabase session and clear the JWT.
 *  2. Calls [com.baton.app.data.local.AppInitializer.runOnSignOut]
 *     to wipe the SQLCipher passphrase and the on-disk DB.
 *  3. Closes the sheet.
 *
 * The session observer in [com.baton.app.MainActivity] then
 * transitions to [com.baton.app.data.auth.AuthSessionState.Unauthenticated]
 * and the Compose tree re-renders the auth screen. No explicit
 * navigation is required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()

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
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        // Don't dismiss before the VM finishes — the
                        // VM calls AuthRepository.signOut(), which
                        // transitions the session observer to
                        // Unauthenticated, which causes the activity
                        // to re-render and tear down the HomeScreen
                        // (and the sheet along with it).
                        viewModel.signOut()
                    }
                },
                enabled = !signingOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = if (signingOut) {
                        stringResource(R.string.settings_signing_out)
                    } else {
                        stringResource(R.string.settings_sign_out)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
