package com.kaavalan.note.features.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tier 0.4 (cleanup + ship-the-built): a process-wide hot
 * state for the [VoiceCaptureService].
 *
 * v1.5.7 had no public recording-state surface; the only
 * indicator was the foreground service notification. The
 * in-app UI (the capture sheet's primary action row) had to
 * guess whether voice was recording and could not show a
 * "Stop" button inside the app -- the user had to pull down
 * the notification shade and tap the action.
 *
 * v1.6.0 introduces this singleton: a tiny StateFlow
 * (`isRecording`) that the service updates in
 * [VoiceCaptureService.handleStart] and
 * [VoiceCaptureService.handleStop]. The capture sheet
 * collects the flow; when `isRecording == true` the
 * primary-action row renders an in-app "Stop" button that
 * calls `context.stopService(...)` -- the same end state as
 * tapping the notification's Stop action, but reachable
 * without leaving the app.
 *
 * **Why an object (not a Hilt-injected singleton):** the
 * service is created by the system, not by Hilt, and the
 * capture sheet's ViewModel is short-lived. A top-level
 * Kotlin object with a MutableStateFlow is the simplest
 * shape that works for both sides without adding a
 * `@Singleton` provider to a Hilt module.
 */
object VoiceCaptureState {

    /**
     * `true` while [VoiceCaptureService] is in the foreground
     * and recording. The service resets it to `false` in
     * [VoiceCaptureService.handleStop] (after transcription
     * finishes) and in [VoiceCaptureService.onDestroy] (the
     * service is killed by the system or by the user).
     */
    private val _isRecording = MutableStateFlow(false)

    /**
     * Public read-only state. Collectors (the capture sheet,
     * future in-app widgets) react to the change.
     */
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /**
     * Internal write entry point. The service is the only
     * caller. The function is `internal` so unit tests under
     * the same module can drive the state from a fake
     * [VoiceCaptureService] without having to spin up a real
     * [android.app.Service].
     */
    internal fun setRecording(value: Boolean) {
        _isRecording.value = value
    }
}
