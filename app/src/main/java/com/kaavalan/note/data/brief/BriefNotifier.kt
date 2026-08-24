package com.kaavalan.note.data.brief

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.kaavalan.note.MainActivity
import com.kaavalan.note.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.0: daily brief push notifications. WorkManager wakes a
 * [BriefNotifierWorker] at the user's brief_time (default 07:30)
 * every day. The notification opens MainActivity on the Today
 * tab; the actual brief is computed in TodayViewModel on open.
 *
 * **No server cron needed.** The brief is computed from the
 * SQLCipher-encrypted Room mirror.
 *
 * **Permission.** Silent on Android 13+ if `POST_NOTIFICATIONS`
 * isn't granted.
 */
@Singleton
class BriefNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = kaavalan-note_brief"
        const val CHANNEL_NAME = "Daily brief"
        const val NOTIFICATION_ID = 1001
        const val UNIQUE_WORK_NAME = kaavalan-note_daily_brief"
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Morning brief from Kaavalan note" }
            mgr.createNotificationChannel(ch)
        }
    }

    fun schedule(hourOfDay: Int = 7, minute: Int = 30) {
        ensureChannel()
        // v1.4 (PHONE-FINDING-10 / F-02): on Android 13+ we MUST
        // hold POST_NOTIFICATIONS at schedule time, not just at
        // post time. The v1.0–v1.3 schedule always enqueued the
        // work and relied on postNotification() to silently
        // no-op. Now we skip enqueueing entirely on TIRAMISU+
        // when the runtime permission isn't held. MainActivity
        // re-calls `schedule()` after a successful permission
        // grant, so this no-op is self-healing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val target = today.atTime(hourOfDay, minute).atZone(zone).toInstant()
        val firstRun = if (target.isAfter(now)) target else target.plus(Duration.ofDays(1))
        val initialDelay = Duration.between(now, firstRun)
        val req = PeriodicWorkRequestBuilder<BriefNotifierWorker>(1, java.util.concurrent.TimeUnit.DAYS)
            .setInitialDelay(initialDelay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    fun postMorningBrief(openCount: Int) {
        val title = "Kaavalan brief"
        val text = if (openCount == 0) {
            "Nothing on your plate."
        } else {
            "$openCount open to look at today."
        }
        postNotification(title, text)
    }

    private fun postNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        }
    }
}

/**
 * v1.0: the worker that fires the morning brief notification.
 * Uses HiltWorker so it can inject the notifier and the brief
 * generator.
 *
 * **v1.2 root-cause fix (F-01 in the capture/AI audit):** the v1.1
 * pass hardcoded `instructions = emptyList()` here, so the
 * generated brief was always empty and the notification always
 * said "Nothing on your plate." The actual count now comes from
 * the instruction repository (the same source the Today tab
 * uses), filtered to the three "needs attention" statuses.
 */
@HiltWorker
class BriefNotifierWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val briefGenerator: BriefGenerator,
    private val notifier: BriefNotifier,
    private val openCountProvider: OpenCountProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        notifier.ensureChannel()
        val openCount = openCountProvider.todayOpenCount()
        notifier.postMorningBrief(openCount)
        return Result.success()
    }

    @AssistedFactory
    interface Factory {
        fun create(appContext: Context, params: WorkerParameters): BriefNotifierWorker
    }
}
