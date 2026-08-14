package com.baton.app.ui.util

import com.baton.app.features.capture.ErrorType
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException

/**
 * v1.2: shared error-string mapper. Keeps PII / SDK details out
 * of the UI while preserving the right semantic for the user.
 *
 * v1.4.5.1: supabase-kt 3.x splits its RestException into two
 * classes — the generic [RestException] in the core module and
 * the auth-specific [AuthRestException] in auth-kt. The earlier
 * `is RestException` check only matched the generic one, so any
 * exception thrown by the auth module fell through to the
 * `else -> default` branch and the user saw a misleading
 * "Invalid email or password" message regardless of the actual
 * server status (e.g. a 429 rate-limit came back as the default).
 * The fix checks both — the auth exception has the same
 * `statusCode` / `errorCode` fields, so a single when() handles
 * both shapes.
 */
object SafeError {
    fun forUser(e: Throwable, default: String): String = when {
        e is RestException || e is AuthRestException -> when (e.statusCode) {
            400, 401 -> "Invalid email or password."
            422 -> "That email looks invalid."
            429 -> "Too many attempts. Try again in a minute."
            in 500..599 -> "Sign-in service unavailable. Try again later."
            else -> default
        }
        e is HttpRequestException || e is IOException -> "No connection. Check your network."
        else -> default
    }

    /**
     * v1.4 (PHONE-FINDING-7): save-context equivalent of [forUser].
     */
    fun forUserSave(e: Throwable, default: String): String = when {
        e is RestException || e is AuthRestException -> when (e.statusCode) {
            401, 403 -> "Your session expired. Please sign in again."
            429 -> "Too many saves. Try again in a minute."
            in 500..599 -> "Save service unavailable. Try again later."
            else -> default
        }
        e is HttpRequestException || e is IOException -> "No connection. Check your network."
        else -> default
    }

    /**
     * v1.4 (PHONE-FINDING-7): user-facing text for the
     * [com.baton.app.features.capture.CaptureUiState.errorType]
     * discriminator.
     */
    fun forCaptureErrorType(type: ErrorType): String? = when (type) {
        ErrorType.NEEDS_PERSON_FIRST ->
            "Save failed. Add a person first to capture instructions."
        ErrorType.NONE -> null
        ErrorType.NETWORK_UNAVAILABLE,
        ErrorType.PERMISSION_DENIED,
        ErrorType.UNKNOWN -> null
    }

    /**
     * v1.4 (PHONE-FINDING-7): classify a thrown exception into
     * the [ErrorType] the VM should set on the [CaptureUiState].
     */
    fun classifyForCapture(e: Throwable): ErrorType = when {
        e is HttpRequestException || e is IOException -> ErrorType.NETWORK_UNAVAILABLE
        e is RestException || e is AuthRestException -> when (e.statusCode) {
            in 500..599 -> ErrorType.NETWORK_UNAVAILABLE
            else -> ErrorType.UNKNOWN
        }
        else -> ErrorType.UNKNOWN
    }
}
