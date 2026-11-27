package com.kexora.soniclet.audio

import com.kexora.soniclet.gestures.SmartMotionManager
import com.kexora.soniclet.radio.RadioBackgroundHost
import com.kexora.soniclet.service.*
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kexora.soniclet.MainActivity
import com.kexora.soniclet.R

class MusicPlaybackService : Service() {
    private var smartMotionManager: SmartMotionManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    var isForegroundServiceRunning: Boolean = false
        private set
    @Volatile
    var isRadioForegroundActive: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        smartMotionManager = SmartMotionManager(this)
        smartMotionManager?.start()

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Soniclet:AudioPlaybackWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                // Acquire with safe 4-hour max timeout to guarantee CPU remains alive during screen lock
                wakeLock?.acquire(4 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildRadioNotification(title: String): android.app.Notification {
        val openRadioIntent = Intent(this, MainActivity::class.java).apply {
            action = "com.kexora.soniclet.ACTION_OPEN_TAB"
            putExtra("target_tab", "RADIO")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            2041,
            openRadioIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopRadioIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP_RADIO
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            2042,
            stopRadioIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val safeTitle = title.ifBlank { "Soniclet Radio" }
        return NotificationCompat.Builder(this, MediaNotificationManager.CHANNEL_ID)
            .setContentTitle(safeTitle)
            .setContentText("Playing in background • Tap to open")
            .setSmallIcon(R.drawable.ic_notification_soniclet)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop Radio", stopPendingIntent)
            .build()
    }

    companion object {
        private const val ACTION_START_FOREGROUND = "com.kexora.soniclet.action.START_FOREGROUND"
        private const val ACTION_STOP_FOREGROUND = "com.kexora.soniclet.action.STOP_FOREGROUND"
        private const val ACTION_START_RADIO_FOREGROUND = "com.kexora.soniclet.action.START_RADIO_FOREGROUND"
        private const val ACTION_STOP_RADIO_FOREGROUND = "com.kexora.soniclet.action.STOP_RADIO_FOREGROUND"
        private const val EXTRA_RADIO_TITLE = "extra_radio_title"
        private var instance: MusicPlaybackService? = null

        fun updateForegroundNotification(notification: android.app.Notification, context: Context? = null) {
            val service = instance
            if (service != null) {
                try {
                    val localPlaying = (service.applicationContext as? com.kexora.soniclet.SonicletApplication)
                        ?.musicViewModel?.uiState?.value?.isPlaying == true
                    if (localPlaying && service.isRadioForegroundActive) {
                        RadioBackgroundHost.stopForLocalPlaybackIfInitialized()
                    }
                    service.isRadioForegroundActive = false

                    val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(MediaNotificationManager.NOTIFICATION_ID, notification)

                    if (!service.isForegroundServiceRunning) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            service.startForeground(
                                MediaNotificationManager.NOTIFICATION_ID,
                                notification,
                                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            )
                        } else {
                            service.startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
                        }
                        service.isForegroundServiceRunning = true
                    }
                    service.acquireWakeLock()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (context != null) {
                startForegroundService(context)
            }
        }

        fun startForegroundService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_START_FOREGROUND
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopForegroundService(context: Context) {
            // Do not tear down the foreground service if Radio is actively using it
            if (instance?.isRadioForegroundActive == true) return
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateRadioForegroundNotification(
            notification: android.app.Notification,
            context: Context,
            isPlaying: Boolean,
            title: String
        ) {
            val service = instance
            if (service != null) {
                try {
                    val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(MediaNotificationManager.NOTIFICATION_ID, notification)
                    service.isRadioForegroundActive = true

                    if (isPlaying) {
                        if (!service.isForegroundServiceRunning) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                service.startForeground(
                                    MediaNotificationManager.NOTIFICATION_ID,
                                    notification,
                                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                                )
                            } else {
                                service.startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
                            }
                            service.isForegroundServiceRunning = true
                        }
                        service.acquireWakeLock()
                    } else {
                        // Keep paused MediaStyle card visible in Control Center so user can tap Play to resume
                        service.releaseWakeLock()
                        if (service.isForegroundServiceRunning) {
                            service.isForegroundServiceRunning = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                service.stopForeground(STOP_FOREGROUND_DETACH)
                            } else {
                                @Suppress("DEPRECATION")
                                service.stopForeground(false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (isPlaying) {
                startRadioForeground(context, title)
            }
        }

        fun startRadioForeground(context: Context, title: String) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_START_RADIO_FOREGROUND
                putExtra(EXTRA_RADIO_TITLE, title)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopRadioForeground(context: Context) {
            if (instance?.isRadioForegroundActive != true) return
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP_RADIO_FOREGROUND
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // Always ensure channel exists
        MediaNotificationManager.createNotificationChannel(this)

        if (action == ACTION_START_RADIO_FOREGROUND) {
            val title = intent.getStringExtra(EXTRA_RADIO_TITLE).orEmpty()
            try {
                val notification = MediaNotificationManager.lastNotification ?: buildRadioNotification(title)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        MediaNotificationManager.NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
                }
                isForegroundServiceRunning = true
                isRadioForegroundActive = true
                acquireWakeLock()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return START_STICKY
        }

        if (action == ACTION_STOP_RADIO_FOREGROUND) {
            if (isRadioForegroundActive) {
                isRadioForegroundActive = false
                isForegroundServiceRunning = false
                releaseWakeLock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                try {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(MediaNotificationManager.NOTIFICATION_ID)
                } catch (_: Exception) {
                }
            }
            return START_STICKY
        }

        if (action == ACTION_STOP_FOREGROUND) {
            if (isRadioForegroundActive) {
                return START_STICKY
            }
            isForegroundServiceRunning = false
            releaseWakeLock()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            return START_STICKY
        }

        // Whenever started (including media buttons from Android O+ MediaButtonReceiver),
        // we MUST call startForeground immediately to fulfill foreground service contract.
        try {
            val notification = MediaNotificationManager.lastNotification ?: NotificationCompat.Builder(this, MediaNotificationManager.CHANNEL_ID)
                .setContentTitle("Soniclet Music Player")
                .setContentText("Playback active")
                .setSmallIcon(R.drawable.ic_notification_soniclet)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build()

            if (!isForegroundServiceRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        MediaNotificationManager.NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(MediaNotificationManager.NOTIFICATION_ID, notification)
                }
                isForegroundServiceRunning = true
            }
            acquireWakeLock()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (action == Intent.ACTION_MEDIA_BUTTON) {
            MediaNotificationManager.mediaSession?.let { session ->
                androidx.media.session.MediaButtonReceiver.handleIntent(session, intent)
            }
            return START_STICKY
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep service & background Radio WebView alive when swiped from recents so audio continues playing
        if (isRadioForegroundActive) {
            RadioBackgroundHost.keepAliveAfterTaskRemovedIfInitialized()
        }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        releaseWakeLock()
        smartMotionManager?.stop()
        super.onDestroy()
    }
}
