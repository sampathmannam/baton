package com.baton.app.features.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.util.Log
import com.baton.app.MainActivity
import com.baton.app.R
import com.baton.app.ai.whisper.WhisperBridge
import com.baton.app.ai.whisper.WhisperModelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 * M2-T4: foreground voice-capture service.
 *
 * Lifecycle:
 *  1. Activity calls [start] with a [ResultReceiver] (the
 *     Activity's `MainActivity` typically).
 *  2. Service starts as a foreground service with
 *     `foregroundServiceType=microphone`, posts a sticky
 *     notification, and begins recording with `AudioRecord` at
 *     16 kHz mono PCM-16.
 *  3. User taps "Stop" in the notification (or the caller
 *     invokes [stop] from outside the service), the service
 *     stops the AudioRecord, writes the PCM to a temp file, and
 *     hands it to [WhisperBridge.transcribe].
 *  4. The transcribed text is delivered to the [ResultReceiver]
 *     via `RESULT_OK` + a `KEY_TEXT` bundle. Errors are
 *     delivered as `RESULT_ERROR` with a `KEY_ERROR` message.
 *  5. The service stops itself and tears down the notification.
 *
 * **Threading:** recording runs on a dedicated coroutine on
 * `Dispatchers.IO`. Transcription is dispatched via
 * [WhisperBridge.transcribe] which already uses
 * `Dispatchers.Default.limitedParallelism(1)` internally.
 *
 * **Permissions:** the caller must hold `RECORD_AUDIO` before
 * invoking [start]. The service does NOT request the permission
 * itself; the UI flow does.
 */
@AndroidEntryPoint
class VoiceCaptureService : Service() {

    @Inject lateinit var whisper: WhisperBridge
    @Inject lateinit var modelManager: WhisperModelManager

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private var recordJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var pcmFile: File? = null
    private var resultReceiver: ResultReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop()
            else -> {
                Log.w(TAG, "unknown action: ${intent?.action}")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val receiver: ResultReceiver? = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        resultReceiver = receiver
        startForegroundWithNotification()
        startRecording()
    }

    private fun handleStop() {
        stopRecording()
        scope.launch {
            try {
                val pcmBytes = pcmFile?.takeIf { it.exists() && it.length() > 0 }?.readBytes()
                if (pcmBytes == null || pcmBytes.isEmpty()) {
                    deliverError("No audio captured.")
                    return@launch
                }
                val text = transcribe(pcmBytes)
                deliverText(text)
            } catch (e: Exception) {
                Log.e(TAG, "transcribe failed", e)
                deliverError(e.message ?: "Transcription failed")
            } finally {
                stopForegroundCompat()
                stopSelf()
            }
        }
    }

    private suspend fun transcribe(pcmBytes: ByteArray): String {
        // Lazy-load the model the first time we have PCM to feed
        // it. This is one-shot per process; subsequent calls reuse
        // the loaded model.
        if (!whisper.isLoaded0()) {
            val modelFile = modelManager.modelFile()
            if (!modelFile.exists()) {
                throw IllegalStateException(
                    "Whisper model not downloaded. Run WhisperModelManager.downloadModel() first."
                )
            }
            whisper.load(modelFile)
        }
        return whisper.transcribe(pcmBytes, sampleRate = SAMPLE_RATE)
    }

    private fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            deliverError("AudioRecord: invalid buffer size $minBuf")
            stopSelf()
            return
        }
        val record = try {
            @Suppress("MissingPermission")  // The caller must hold RECORD_AUDIO.
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
            )
        } catch (e: SecurityException) {
            deliverError("RECORD_AUDIO permission denied")
            stopSelf()
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            deliverError("AudioRecord failed to initialize")
            record.release()
            stopSelf()
            return
        }
        audioRecord = record
        val file = File(cacheDir, "voice-${System.currentTimeMillis()}.pcm")
        pcmFile = file

        record.startRecording()
        recordJob = scope.launch {
            try {
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(minBuf)
                    while (isActive) {
                        val n = record.read(buf, 0, buf.size)
                        if (n > 0) out.write(buf, 0, n)
                        if (n < 0) break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "recording io", e)
                deliverError("Audio I/O failed: ${e.message}")
            }
        }
    }

    private fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            } catch (_: IllegalStateException) { }
            it.release()
        }
        audioRecord = null
    }

    private fun startForegroundWithNotification() {
        val channelId = ensureChannel()
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.voice_capture_title))
            .setContentText(getString(R.string.voice_capture_text))
            // v1.2 (F-MED-19): use a vector monochrome small icon,
            // not the launcher mipmap. The system tints the small
            // icon; a launcher mipmap renders as a coloured blob
            // (or invisible on some launchers).
            .setSmallIcon(R.drawable.ic_voice_notification)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .addAction(0, getString(R.string.voice_capture_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun ensureChannel(): String {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.voice_capture_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
                nm.createNotificationChannel(ch)
            }
        }
        return CHANNEL_ID
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun deliverText(text: String) {
        val receiver = resultReceiver ?: return
        val bundle = Bundle().apply { putString(KEY_TEXT, text) }
        receiver.send(RESULT_OK, bundle)
    }

    private fun deliverError(message: String) {
        val receiver = resultReceiver ?: return
        val bundle = Bundle().apply { putString(KEY_ERROR, message) }
        receiver.send(RESULT_ERROR, bundle)
    }

    override fun onDestroy() {
        recordJob?.cancel()
        audioRecord?.release()
        audioRecord = null
        supervisor.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BatonVoice"

        const val ACTION_START = "com.baton.app.action.VOICE_START"
        const val ACTION_STOP = "com.baton.app.action.VOICE_STOP"

        const val EXTRA_RESULT_RECEIVER = "result_receiver"

        const val KEY_TEXT = "text"
        const val KEY_ERROR = "error"

        const val RESULT_OK = 1
        const val RESULT_ERROR = 2

        const val CHANNEL_ID = "voice_capture"
        const val NOTIFICATION_ID = 1011

        const val SAMPLE_RATE = 16000

        /**
         * Convenience for callers. The receiver is delivered the
         * transcript (or error) when the service finishes. The
         * caller must hold the `RECORD_AUDIO` runtime permission
         * before invoking.
         */
        fun start(context: Context, receiver: ResultReceiver) {
            val intent = Intent(context, VoiceCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_RECEIVER, receiver)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Convenience to stop the service. The service also stops
         * itself when transcription completes; this is the
         * user-cancel path.
         */
        fun stop(context: Context) {
            val intent = Intent(context, VoiceCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
