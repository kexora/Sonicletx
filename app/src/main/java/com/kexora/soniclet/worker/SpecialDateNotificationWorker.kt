package com.kexora.soniclet.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kexora.soniclet.MainActivity
import com.kexora.soniclet.R
import com.kexora.soniclet.easteregg.MuhLoveRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class SpecialDateNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // STRICT EASTER EGG RULE: If user did NOT activate Muh Mode, cancel everything and exit!
            if (!com.kexora.soniclet.easteregg.MuhLoveRegistry.isMuhModeActive(appContext)) {
                for (i in 0..20) {
                    notificationManager.cancel(com.kexora.soniclet.easteregg.MuhLoveNotificationReceiver.NOTIFICATION_ID + i)
                }
                com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.cancelAlarm(appContext)
                cancel(appContext)
                return@withContext Result.success()
            }

            val specialDate = com.kexora.soniclet.easteregg.MuhLoveRegistry.getTodaySpecialDate()

            if (specialDate == null) {
                // STRICT DATE RULE: Today is NOT one of the special dates (21 March, 1 July, 23 November)
                // Clear any celebration notification and ensure scheduler is ready for next midnight
                for (i in 0..20) {
                    notificationManager.cancel(com.kexora.soniclet.easteregg.MuhLoveNotificationReceiver.NOTIFICATION_ID + i)
                }
                com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.scheduleNextAlarm(appContext)
                return@withContext Result.success()
            }

            // Today IS an active special celebration date!
            // Sync and deliver dynamic, non-repeating message for this specific date
            com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.syncSpecialCelebration(appContext)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun showPersistentLoveNotification(
        context: Context,
        dateInfo: com.kexora.soniclet.easteregg.SpecialDateInfo
    ) {
        if (!com.kexora.soniclet.easteregg.MuhLoveRegistry.isMuhModeActive(context)) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "moon_mode_special_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Special Celebration Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Romantic and celebration notifications for special dates"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2027,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Read dynamic randomized message tailored to this specific date (with accurate age and years)
        val selectedMessage = MuhLoveRegistry.getRandomMessage(dateInfo)

        // Persistent notification (setOngoing(true))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_soniclet)
            .setContentTitle("${dateInfo.celebrationIcon} ${dateInfo.title}")
            .setContentText(selectedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(selectedMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Persistent system notification
            .setAutoCancel(false)
            .build()

        val notificationId = 770022
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val WORK_NAME = "SpecialDateEventCheckWork"

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (ignored: Exception) {}
        }

        fun schedule(context: Context) {
            // Strictly check if Muh Mode is active before scheduling periodic background work
            if (!com.kexora.soniclet.easteregg.MuhLoveRegistry.isMuhModeActive(context)) {
                cancel(context)
                return
            }

            // Periodic work request scheduled to run every 15 minutes (WorkManager standard minimum interval is 15 min)
            val workRequest = PeriodicWorkRequestBuilder<SpecialDateNotificationWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // flex interval
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
