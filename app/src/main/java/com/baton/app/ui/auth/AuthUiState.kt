package com.baton.app.ui.auth

/**
 * v1.4.5: the auth screen now has two paths (email OTP and email +
 * password) and a state machine that needs to track which path
 * the user is on so that an [Error] doesn't accidentally flip the
 * user back to the entry panel when they were 1 keystroke away
 * from finishing the OTP flow.
 *
 * State machine:
 *  Idle --(sendOtp)--> Submitting --(ok)--> CodeSent(email)
 *                                          --(err)--> OtpError(email, msg)
 *                                          --(verifyOtp)--> Submitting
 *                                                            --(ok)--> SignedIn
 *                                                            --(err)--> OtpError(email, msg)
 *  Idle --(signIn/signUp)--> Submitting --(ok)--> SignedIn
 *                                     --(err)--> PasswordError(msg)
 *
 * The [OtpError] carries the email so the screen can re-render
 * the OTP panel (with the typed code preserved by the screen's
 * own remember{}) after the error, without having to re-send
 * the code. The [PasswordError] is a flat error on the entry
 * panel.
 */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Submitting : AuthUiState

    /**
     * The OTP was sent to the user's inbox. The screen should now
     * show the 6-digit code field + Verify button.
     */
    data class CodeSent(val codeEmail: String) : AuthUiState

    /**
     * The OTP was verified, the session is set, and the auth flow's
     * upstream observer (the [com.baton.app.MainActivity] / nav
     * graph) will route the user to the home screen.
     */
    data object SignedIn : AuthUiState

    /**
     * Error in the OTP path. [codeEmail] is preserved so the UI
     * can stay on the OTP panel and let the user retry without
     * re-entering the email.
     */
    data class OtpError(val codeEmail: String, val message: String) : AuthUiState

    /**
     * Error in the password sign-in / sign-up path. The UI returns
     * to the entry panel and shows the error inline.
     */
    data class PasswordError(val message: String) : AuthUiState
}
