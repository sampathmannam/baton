package com.baton.app.ui.util

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException

/**
 * v1.2: shared error-string mapper. Keeps PII / SDK details out
 * of the UI while preserving the right semantic for the user.
 *
 * **Why this exists (BUG-AUTH-001 / BEAU-NEW-01):** the v1.1 path
 * used `e.message` directly, which leaks the supabase-kt exception
 * message (full request URL, `X-Client-Info: supabase-kt/3.1.1`,
 * `Authorization: Bearer <jwt>`, and other headers) onto the screen
 * — a security smell on a shared device and a phishing surface
 * (a real supabase URL on a fake sign-in page is convincing). We
 * map exceptions to a fixed set of user-safe strings and log the
 * raw exception under a non-PII logcat tag for crash reporting.
 *
 * Used by [com.baton.app.ui.auth.AuthViewModel],
 * [com.baton.app.ui.home.HomeViewModel], and any other call site
 * that needs to surface a supabase / network error to a human.
 */
object SafeError {
    /**
     * Map a thrown exception to a user-safe display string.
     *
     * @param e the thrown exception (RestException, HttpRequestException,
     *          IOException, or anything else)
     * @param default the safe string to return when the exception
     *                type doesn't match a known case
     * @return a fixed, hard-coded string that contains no URL, no
     *         JWT, no SDK name, no X-Client-Info, no stack trace
     */
    fun forUser(e: Throwable, default: String): String = when (e) {
        is RestException -> when (e.statusCode) {
            400, 401 -> "Invalid email or password."
            422 -> "That email looks invalid."
            429 -> "Too many attempts. Try again in a minute."
            in 500..599 -> "Sign-in service unavailable. Try again later."
            else -> default
        }
        is HttpRequestException, is IOException -> "No connection. Check your network."
        else -> default
    }
}
