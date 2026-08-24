package com.kaavalan.note.data.brief

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kaavalan.note.MainActivity
import com.kaavalan.note.R
import com.kaavalan.note.data.dates.ImportantDateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * v2.0 Tier 2 (§2.6): the actual morning-brief worker. Fires at
 * 9 AM (or whatever the user picks) via WorkManager and posts a
 * notification with:
 *  - The number of today's important dates.
 *  - The carried-over count from [BriefGenerator].
 *
 * **Channel.** "Baton Brief" (separate from the daily-brief
 * channel so the user can mute the morning notification without
 * losing voice-capture notifications). Created on first use.
 *
 * **Permission.** `POST_NOTIFICATIONS` is required on Android 13+
 * (runtime). The worker no-ops if the permission is not held
 * (matches the existing [BriefNotifier] behaviour).
 *
 * **Permission for the alarm.** `SCHEDULE_EXACT_ALARM` is
 * declared in the manifest. The user must grant it manually on
 * Android 14+; if they don't, WorkManager's `PeriodicWorkRequest`
 * still fires (just at a less precise time).
 */
@HiltWorker
class MorningBriefWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val briefGenerator: BriefGenerator,
    private val dateRepo: ImportantDateRepository,
    private val openCountProvider: OpenCountProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannel()
        val todayEpoch = dateRepo.todayEpochDay()
        val datesToday = dateRepo.observeOnDay(todayEpoch).first()
        val openCount = openCountProvider.todayOpenCount()
        postNotification(
            openCount = openCount,
            dateLabels = datesToday.map { it.label },
        )
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = applicationContext.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.morning_brief_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = applicationContext.getString(R.string.morning_brief_channel_description)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    private fun postNotification(openCount: Int, dateLabels: List<String>) {
        // Permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val title = applicationContext.getString(R.string.morning_brief_title)
        val dateLine = if (dateLabels.isEmpty()) {
            applicationContext.getString(R.string.morning_brief_nothing)
        } else {
            applicationContext.getString(
                R.string.morning_brief_dates_today,
                dateLabels.joinToString(", "),
            ) + "  -  " + applicationContext.resources.getQuantityString(
                // v1.6.4: pluralised (was hard-coded "%1$d carried over"
                // → "1 carried over" / "N carried over").
                R.plurals.morning_brief_carried_over,
                openCount,
                openCount,
            )
        }
        val intent = android.content.Intent(applicationContext, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = android.app.PendingIntent.getActivity(
            applicationContext, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(dateLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(dateLine))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notif)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(appContext: Context, params: WorkerParameters): MorningBriefWorker
    }

    companion object {
        const val CHANNEL_ID = "baton_morning_brief"
        const val NOTIFICATION_ID = 1002
    }
}
