package com.kexora.soniclet.easteregg

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.kexora.soniclet.MainActivity
import com.kexora.soniclet.R
import java.util.Calendar

class MuhLoveNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // STRICT EASTER EGG RULE: Only send notification if Muh Mode is currently active!
        if (!MuhLoveRegistry.isMuhModeActive(context)) {
            for (i in 0..20) {
                notificationManager.cancel(NOTIFICATION_ID + i)
            }
            MuhLoveNotificationScheduler.cancelAlarm(context)
            return
        }

        val specialDate = MuhLoveRegistry.getTodaySpecialDate()

        if (specialDate != null) {
            // STRICT DATE RULE: Today IS one of the special dates (e.g., 21 March, 1 July, 23 November)
            // Show the next dynamic, non-repeating message for this specific date
            showLoveNotification(context, specialDate)
            // Re-schedule the alarm to trigger every ~45 minutes throughout this special day
            MuhLoveNotificationScheduler.scheduleNextAlarm(context)
        } else {
            // STRICT DATE RULE: Today is NOT a special date!
            // Dismiss and clear any celebration notification
            for (i in 0..20) {
                notificationManager.cancel(NOTIFICATION_ID + i)
            }
            // Schedule the alarm to wake up at midnight to check when the next special day arrives
            MuhLoveNotificationScheduler.scheduleNextAlarm(context)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 770022
        const val CHANNEL_ID = "muh_love_channel"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Special Moments & Dedications 💖",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time celebration and personalized notes for Karishma"
                    enableLights(true)
                    lightColor = 0xFFFF1493.toInt()
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 150, 300)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showLoveNotification(context: Context, dateInfo: SpecialDateInfo) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // STRICT EASTER EGG RULE: Only send notification if Muh Mode is currently active!
            if (!MuhLoveRegistry.isMuhModeActive(context)) {
                for (i in 0..20) {
                    notificationManager.cancel(NOTIFICATION_ID + i)
                }
                return
            }

            // STRICT DATE RULE: Verify that today is genuinely this special date
            val todaySpecial = MuhLoveRegistry.getTodaySpecialDate()
            if (todaySpecial == null || todaySpecial.month != dateInfo.month || todaySpecial.dayOfMonth != dateInfo.dayOfMonth) {
                for (i in 0..20) {
                    notificationManager.cancel(NOTIFICATION_ID + i)
                }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            ensureNotificationChannel(context)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_easter_egg", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2026,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Dynamic new message every time this notification triggers throughout the day
            val message = MuhLoveRegistry.getNextDynamicMessage(context, dateInfo)

            val prefs = context.getSharedPreferences("soniclet_special_date_notif_tracker", Context.MODE_PRIVATE)
            val msgIndex = prefs.getInt("msg_idx_${dateInfo.month}_${dateInfo.dayOfMonth}", 0)
            // Use rotating notification ID so every message arrives as a distinct new notification throughout 24 hours
            val notificationId = NOTIFICATION_ID + (msgIndex % 15)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_soniclet)
                .setColor(0xFFFF1493.toInt())
                .setContentTitle("${dateInfo.celebrationIcon} ${dateInfo.title}")
                .setContentText(message)
                .setSubText(dateInfo.badgeText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message).setSummaryText(dateInfo.badgeText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false) // Alert each time so user gets a fresh alert banner with sound & vibration
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            notificationManager.notify(notificationId, notification)
        }

        fun showPreviewTestNotification(context: Context, note: MuhLoveMessage) {
            if (!MuhLoveRegistry.isMuhModeActive(context)) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            ensureNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_easter_egg", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                2026,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_soniclet)
                .setColor(0xFFFF1493.toInt())
                .setContentTitle("${note.icon} Special Notification Test ✨")
                .setContentText(note.text)
                .setSubText("Preview Note 💌")
                .setStyle(NotificationCompat.BigTextStyle().bigText(note.text).setSummaryText("Special Date System Active"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}

object MuhLoveNotificationScheduler {

    private const val ALARM_REQUEST_CODE = 998877

    /**
     * Synchronizes special date celebration notifications.
     * Strictly verifies that user has activated Muh Mode AND that today is one of the special dates (21 March, 1 July, 23 November).
     * For all other users or non-special days, it cancels and removes any celebration notification immediately.
     */
    fun syncSpecialCelebration(context: Context, forceImmediate: Boolean = false) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // STRICT EASTER EGG RULE: Only send notification if Muh Mode is currently active!
        if (!MuhLoveRegistry.isMuhModeActive(context)) {
            for (i in 0..20) {
                notificationManager.cancel(MuhLoveNotificationReceiver.NOTIFICATION_ID + i)
            }
            cancelAlarm(context)
            return
        }

        val specialDate = MuhLoveRegistry.getTodaySpecialDate()

        if (specialDate != null) {
            val prefs = context.getSharedPreferences("soniclet_special_date_notif_tracker", Context.MODE_PRIVATE)
            val lastTime = prefs.getLong("last_notif_time_${specialDate.month}_${specialDate.dayOfMonth}", 0L)
            val now = System.currentTimeMillis()
            val minInterval = 15 * 60 * 1000L // 15 minutes interval so notifications keep coming continuously for 24 hours

            // Show a fresh notification if forced, first time today, or more than minInterval elapsed
            if (forceImmediate || lastTime == 0L || now - lastTime >= minInterval) {
                MuhLoveNotificationReceiver.showLoveNotification(context, specialDate)
            }
            scheduleNextAlarm(context)
        } else {
            // Not a special date: cancel any existing notifications immediately
            for (i in 0..20) {
                notificationManager.cancel(MuhLoveNotificationReceiver.NOTIFICATION_ID + i)
            }
            scheduleNextAlarm(context)
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MuhLoveNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleNextAlarm(context: Context) {
        if (!MuhLoveRegistry.isMuhModeActive(context)) {
            cancelAlarm(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, MuhLoveNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()

        // Next midnight (00:00:05 of next day) to check when the next day begins
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val specialDate = MuhLoveRegistry.getTodaySpecialDate()

        val triggerAtMillis = if (specialDate != null) {
            // On a special date: alert every 15 minutes ("har kuch der pe notification aata rahega 24 hours") with a new message
            val refreshInterval = 15 * 60 * 1000L
            minOf(now + refreshInterval, nextMidnight)
        } else {
            // On a normal day: wake up exactly at midnight to check if tomorrow is a special date
            nextMidnight
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (ignored: Exception) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerImmediateCheck(context: Context, forceDemo: Boolean = false) {
        if (!MuhLoveRegistry.isMuhModeActive(context)) return

        val specialDate = MuhLoveRegistry.getTodaySpecialDate()
        if (specialDate != null) {
            MuhLoveNotificationReceiver.showLoveNotification(context, specialDate)
            scheduleNextAlarm(context)
        } else if (forceDemo) {
            val note = MuhLoveRegistry.getRandomLoveMessage()
            MuhLoveNotificationReceiver.showPreviewTestNotification(context, note)
            scheduleNextAlarm(context)
        }
    }
}
