package com.baton.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sign-in / sign-up screen.
 *
 * **v1.4.5:** two auth paths in one screen.
 *
 * - **Email OTP (top):** the user types their email, gets a 6-digit
 *   code (or magic link) in their inbox, types the code back, signed
 *   in. No password ever. This is the path the user picked to avoid
 *   typing a password — the original Google sign-in request was
 *   blocked on the Google Cloud setup, and OTP delivers the same
 *   "no password" UX without the third-party infra.
 * - **Email + password (bottom, collapsible):** the original M0
 *   flow, kept as a fallback for users who prefer it (or who have
 *   rate-limited Supabase OTP for the day).
 *
 * State machine is owned by [AuthViewModel]. The screen only
 * renders the current state and forwards user actions.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Baton", style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Welcome back",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        when (val s = state) {
            is AuthUiState.CodeSent -> OtpVerifyPanel(
                email = s.codeEmail,
                isSubmitting = false,
                errorMessage = null,
                onVerify = { code -> viewModel.verifyOtp(s.codeEmail, code) },
                onUsePasswordInstead = { viewModel.reset() },
                onResend = { viewModel.sendOtp(s.codeEmail) },
            )
            is AuthUiState.OtpError -> OtpVerifyPanel(
                email = s.codeEmail,
                isSubmitting = false,
                errorMessage = s.message,
                onVerify = { code -> viewModel.verifyOtp(s.codeEmail, code) },
                onUsePasswordInstead = { viewModel.reset() },
                onResend = { viewModel.sendOtp(s.codeEmail) },
            )
            else -> EntryPanel(
                state = s,
                onSendOtp = viewModel::sendOtp,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
            )
        }
    }
}

/**
 * v1.4.5: the "type email, get a code" panel. Shown when the
 * state is anything other than [AuthUiState.CodeSent] /
 * [AuthUiState.OtpError] (i.e. the initial Idle / Submitting /
 * PasswordError states). Password sign-in is a collapsible
 * sub-section below the OTP CTA.
 */
@Composable
private fun EntryPanel(
    state: AuthUiState,
    onSendOtp: (String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val isSubmitting = state is AuthUiState.Submitting

    // --- OTP path (default visible) ---
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSubmitting,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = { onSendOtp(email) },
        enabled = !isSubmitting && email.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (isSubmitting) "Sending..." else "Continue with email")
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "We'll email you a one-time code. No password needed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (state is AuthUiState.PasswordError) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    // --- Password path (collapsible) ---
    Spacer(modifier = Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            "  or  ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(16.dp))

    if (showPassword) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (isSignUp) onSignUp(email, password) else onSignIn(email, password)
                },
                enabled = !isSubmitting && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isSignUp) "Create account" else "Sign in")
            }
            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(if (isSignUp) "Sign in" else "Create")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    TextButton(onClick = { showPassword = !showPassword }) {
        Text(if (showPassword) "Hide password sign-in" else "Use password instead")
    }
}

/**
 * v1.4.5: shown after the OTP has been sent. The user types the
 * 6-digit code, taps Verify. We display the email as a read-only
 * label so the user can confirm which inbox the code went to.
 */
@Composable
private fun OtpVerifyPanel(
    email: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onVerify: (String) -> Unit,
    onUsePasswordInstead: () -> Unit,
    onResend: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Text(
        "We sent a code to",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        email,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(8) },
        label = { Text("6-digit code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSubmitting,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = { onVerify(code) },
        enabled = !isSubmitting && code.length >= 6,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (isSubmitting) "Verifying..." else "Verify")
    }
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onResend, enabled = !isSubmitting) {
            Text("Resend code")
        }
        TextButton(onClick = onUsePasswordInstead) {
            Text("Use password instead")
        }
    }
}
