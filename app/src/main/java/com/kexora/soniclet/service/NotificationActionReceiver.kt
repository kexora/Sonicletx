package com.kexora.soniclet.service

import com.kexora.soniclet.audio.MediaNotificationManager
import com.kexora.soniclet.radio.RadioBackgroundHost
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kexora.soniclet.MainActivity

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PREVIOUS = "com.kexora.soniclet.ACTION_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.kexora.soniclet.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.kexora.soniclet.ACTION_NEXT"
        const val ACTION_REWIND = "com.kexora.soniclet.ACTION_REWIND"
        const val ACTION_FAST_FORWARD = "com.kexora.soniclet.ACTION_FAST_FORWARD"
        const val ACTION_SHUFFLE = "com.kexora.soniclet.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.kexora.soniclet.ACTION_REPEAT"
        const val ACTION_FAVORITE = "com.kexora.soniclet.ACTION_FAVORITE"
        const val ACTION_STOP = "com.kexora.soniclet.ACTION_STOP"
        const val ACTION_STOP_RADIO = "com.kexora.soniclet.ACTION_STOP_RADIO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_STOP_RADIO) {
            RadioBackgroundHost.getInstance(context).stopBackgroundPlayback()
            return
        }

        if (Intent.ACTION_MEDIA_BUTTON == action) {
            MediaNotificationManager.mediaSession?.let { session ->
                androidx.media.session.MediaButtonReceiver.handleIntent(session, intent)
            }
            return
        }

        if (MediaNotificationManager.isRadioSessionActive) {
            val radioListener = MediaNotificationManager.getOrInitRadioListener(context)
            when (action) {
                ACTION_PREVIOUS -> radioListener.onRadioPrevious()
                ACTION_PLAY_PAUSE -> radioListener.onRadioTogglePlayPause()
                ACTION_NEXT -> radioListener.onRadioNext()
                ACTION_REWIND -> radioListener.onRadioRewind()
                ACTION_FAST_FORWARD -> radioListener.onRadioFastForward()
                ACTION_STOP -> radioListener.onRadioStop()
            }
            return
        }

        val listener = MediaNotificationManager.listener ?: (context.applicationContext as? com.kexora.soniclet.SonicletApplication)?.musicViewModel
        if (listener != null) {
            when (action) {
                ACTION_PREVIOUS -> listener.onPlayPrevious()
                ACTION_PLAY_PAUSE -> listener.onTogglePlayPause()
                ACTION_NEXT -> listener.onPlayNext()
                ACTION_REWIND -> listener.onRewind()
                ACTION_FAST_FORWARD -> listener.onFastForward()
                ACTION_SHUFFLE -> listener.onToggleShuffle()
                ACTION_REPEAT -> listener.onToggleRepeat()
                ACTION_FAVORITE -> {
                    listener.onToggleFavorite()
                    com.kexora.soniclet.widget.SonicletWidgetManager.triggerHeartPop(context)
                }
                ACTION_STOP -> listener.onStopPlayback()
            }
        } else {
            try {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("widget_action", action)
                }
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
