package com.kexora.soniclet.service

import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kexora.soniclet.widget.WidgetController

class SonicletBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Restore last known widget and playback state from WidgetController
                val lastSong = WidgetController.getLastPlayedSong(context)
                if (lastSong != null) {
                    val isPlaying = WidgetController.isLastPlaying(context)
                    val pos = WidgetController.getLastPosition(context)
                    com.kexora.soniclet.widget.SonicletWidgetManager.updateAllWidgets(
                        context = context,
                        song = lastSong,
                        isPlaying = false, // paused on boot for safety
                        isFavorite = false,
                        currentPositionMs = pos
                    )
                }

                // Sync secret Easter Egg celebrations strictly ONLY if Muh mode is active
                if (com.kexora.soniclet.easteregg.MuhLoveRegistry.isMuhModeActive(context)) {
                    com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.syncSpecialCelebration(context)
                    com.kexora.soniclet.worker.SpecialDateNotificationWorker.schedule(context)
                } else {
                    com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.cancelAlarm(context)
                    com.kexora.soniclet.worker.SpecialDateNotificationWorker.cancel(context)
                }
            }
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                // Real-time synchronization when day rolls over or time changes strictly ONLY if Muh mode is active
                if (com.kexora.soniclet.easteregg.MuhLoveRegistry.isMuhModeActive(context)) {
                    com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.syncSpecialCelebration(context)
                    com.kexora.soniclet.worker.SpecialDateNotificationWorker.schedule(context)
                } else {
                    com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.cancelAlarm(context)
                    com.kexora.soniclet.worker.SpecialDateNotificationWorker.cancel(context)
                }
            }
        }
    }
}
