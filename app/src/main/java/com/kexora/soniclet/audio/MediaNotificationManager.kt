package com.kexora.soniclet.audio

import com.kexora.soniclet.radio.RadioBackgroundHost
import com.kexora.soniclet.service.*
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Size
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.kexora.soniclet.MainActivity
import com.kexora.soniclet.R
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.RepeatMode
import com.kexora.soniclet.utils.CustomArtworkManager

interface PlaybackControlListener {
    fun onPlay()
    fun onPause()
    fun onTogglePlayPause()
    fun onPlayNext()
    fun onPlayPrevious()
    fun onRewind()
    fun onFastForward()
    fun onSeekTo(positionMs: Long)
    fun onToggleShuffle()
    fun onSetShuffleMode(enabled: Boolean)
    fun onToggleRepeat()
    fun onSetRepeatMode(repeatMode: RepeatMode)
    fun onToggleFavorite()
    fun onStopPlayback()
    fun refreshNotification()
    fun onHeadphoneClick(count: Int)
}

interface RadioControlListener {
    fun onRadioPlay()
    fun onRadioPause()
    fun onRadioTogglePlayPause()
    fun onRadioNext()
    fun onRadioPrevious()
    fun onRadioRewind()
    fun onRadioFastForward()
    fun onRadioSeekTo(positionMs: Long)
    fun onRadioStop()
}

object MediaNotificationManager {

    const val CHANNEL_ID = "soniclet_playback_channel"
    const val NOTIFICATION_ID = 1001

    var listener: PlaybackControlListener? = null
        private set
    @Volatile
    var radioControlListener: RadioControlListener? = null
        private set
    @Volatile
    var isRadioSessionActive: Boolean = false
        private set
    var lastNotification: android.app.Notification? = null
    var mediaSession: MediaSessionCompat? = null
        private set
    var isNotificationDismissed: Boolean = false

    // In-memory artwork cache by song ID to eliminate redundant storage reads & Binder IPC issues
    private var cachedArtworkSongId: Long = -1L
    private var cachedArtworkBitmap: Bitmap? = null

    fun registerListener(controlListener: PlaybackControlListener) {
        this.listener = controlListener
    }

    fun registerRadioListener(controlListener: RadioControlListener) {
        this.radioControlListener = controlListener
    }

    fun getOrInitRadioListener(context: Context): RadioControlListener {
        return radioControlListener ?: RadioBackgroundHost.getInstance(context).also {
            radioControlListener = it
        }
    }

    fun getOrInitListener(context: Context): PlaybackControlListener? {
        if (listener == null) {
            listener = (context.applicationContext as? com.kexora.soniclet.SonicletApplication)?.musicViewModel
        }
        return listener
    }

    private fun initMediaSession(context: Context) {
        if (mediaSession == null) {
            val mediaButtonReceiver = android.content.ComponentName(context, androidx.media.session.MediaButtonReceiver::class.java)
            mediaSession = MediaSessionCompat(context, "SonicletMediaSession", mediaButtonReceiver, null).apply {
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )

                // Critical for Android 12+ (API 31+): FLAG_MUTABLE allows SystemUI lock screen
                // and bluetooth receivers to attach KeyEvent into mediaButtonIntent.
                val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, context, androidx.media.session.MediaButtonReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val mediaButtonPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    mediaButtonIntent,
                    flags
                )
                setMediaButtonReceiver(mediaButtonPendingIntent)

                setCallback(object : MediaSessionCompat.Callback() {
                    private var clickCount = 0
                    private var clickRunnable: Runnable? = null
                    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

                    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                        val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (keyEvent != null) {
                            val keyCode = keyEvent.keyCode
                            val action = keyEvent.action
                            if (action == KeyEvent.ACTION_DOWN) {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                        mainHandler.post {
                                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioTogglePlayPause()
                                            else getOrInitListener(context)?.onTogglePlayPause()
                                        }
                                        return true
                                    }
                                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                                        clickCount++
                                        clickRunnable?.let { mainHandler.removeCallbacks(it) }
                                        clickRunnable = Runnable {
                                            if (isRadioSessionActive) {
                                                when (clickCount) {
                                                    1 -> getOrInitRadioListener(context).onRadioTogglePlayPause()
                                                    2 -> getOrInitRadioListener(context).onRadioNext()
                                                    3 -> getOrInitRadioListener(context).onRadioPrevious()
                                                }
                                            } else {
                                                getOrInitListener(context)?.onHeadphoneClick(clickCount)
                                            }
                                            clickCount = 0
                                        }
                                        mainHandler.postDelayed(clickRunnable!!, 350)
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                        mainHandler.post {
                                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioNext()
                                            else getOrInitListener(context)?.onPlayNext()
                                        }
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                        mainHandler.post {
                                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioPrevious()
                                            else getOrInitListener(context)?.onPlayPrevious()
                                        }
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                        mainHandler.post {
                                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioPlay()
                                            else getOrInitListener(context)?.onPlay()
                                        }
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                        mainHandler.post {
                                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioPause()
                                            else getOrInitListener(context)?.onPause()
                                        }
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                                        mainHandler.post {
                                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioStop()
                                            else getOrInitListener(context)?.onStopPlayback()
                                        }
                                        return true
                                    }
                                }
                            } else if (action == KeyEvent.ACTION_UP) {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_HEADSETHOOK,
                                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                    KeyEvent.KEYCODE_MEDIA_NEXT,
                                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                                    KeyEvent.KEYCODE_MEDIA_REWIND,
                                    KeyEvent.KEYCODE_MEDIA_PLAY,
                                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                                    KeyEvent.KEYCODE_MEDIA_STOP -> return true
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }

                    override fun onPlay() {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioPlay()
                            else getOrInitListener(context)?.onPlay()
                        }
                    }
                    override fun onPause() {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioPause()
                            else getOrInitListener(context)?.onPause()
                        }
                    }
                    override fun onSkipToNext() {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioNext()
                            else getOrInitListener(context)?.onPlayNext()
                        }
                    }
                    override fun onSkipToPrevious() {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioPrevious()
                            else getOrInitListener(context)?.onPlayPrevious()
                        }
                    }
                    override fun onRewind() {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioRewind()
                            else getOrInitListener(context)?.onRewind()
                        }
                    }
                    override fun onFastForward() {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioFastForward()
                            else getOrInitListener(context)?.onFastForward()
                        }
                    }
                    override fun onSeekTo(pos: Long) {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioSeekTo(pos)
                            else getOrInitListener(context)?.onSeekTo(pos)
                        }
                    }
                    override fun onSetShuffleMode(shuffleMode: Int) {
                        mainHandler.post {
                            if (!isRadioSessionActive) {
                                val enabled = (shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE)
                                getOrInitListener(context)?.onSetShuffleMode(enabled)
                            }
                        }
                    }
                    override fun onSetRepeatMode(repeatMode: Int) {
                        mainHandler.post {
                            if (!isRadioSessionActive) {
                                val targetMode = when (repeatMode) {
                                    PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.ONE
                                    PlaybackStateCompat.REPEAT_MODE_ALL -> RepeatMode.ALL
                                    else -> RepeatMode.OFF
                                }
                                getOrInitListener(context)?.onSetRepeatMode(targetMode)
                            }
                        }
                    }
                    override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                        mainHandler.post {
                            if (isRadioSessionActive) {
                                val rl = getOrInitRadioListener(context)
                                when (action) {
                                    NotificationActionReceiver.ACTION_REWIND -> rl.onRadioRewind()
                                    NotificationActionReceiver.ACTION_FAST_FORWARD -> rl.onRadioFastForward()
                                    NotificationActionReceiver.ACTION_STOP_RADIO,
                                    NotificationActionReceiver.ACTION_STOP -> rl.onRadioStop()
                                }
                                return@post
                            }
                            val l = getOrInitListener(context)
                            when (action) {
                                NotificationActionReceiver.ACTION_SHUFFLE -> l?.onToggleShuffle()
                                NotificationActionReceiver.ACTION_REPEAT -> l?.onToggleRepeat()
                                NotificationActionReceiver.ACTION_FAVORITE -> l?.onToggleFavorite()
                                NotificationActionReceiver.ACTION_REWIND -> l?.onRewind()
                                NotificationActionReceiver.ACTION_FAST_FORWARD -> l?.onFastForward()
                            }
                        }
                    }
                    override fun onStop() {
                        mainHandler.post {
                            if (isRadioSessionActive) getOrInitRadioListener(context).onRadioStop()
                            else getOrInitListener(context)?.onStopPlayback()
                        }
                    }
                })
                isActive = true
            }
        }
    }

    /**
     * Fast, lightweight real-time state sync for MediaSession (position, state, shuffle, repeat).
     * Uses SystemClock.elapsedRealtime() so OS SystemUI media carousel can accurately extrapolate
     * seekbar position without drift or freezing.
     */
    fun updatePlaybackState(
        isPlaying: Boolean,
        currentPositionMs: Long,
        durationMs: Long,
        isShuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        isFavorite: Boolean
    ) {
        try {
            mediaSession?.let { session ->
                val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                val playbackSpeed = if (isPlaying) 1.0f else 0.0f
                val totalDuration = durationMs.coerceAtLeast(0L)

                val playbackState = PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                                PlaybackStateCompat.ACTION_REWIND or
                                PlaybackStateCompat.ACTION_FAST_FORWARD or
                                PlaybackStateCompat.ACTION_SEEK_TO or
                                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                                PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                                PlaybackStateCompat.ACTION_STOP
                    )
                    .setState(state, currentPositionMs, playbackSpeed, SystemClock.elapsedRealtime())
                    .setBufferedPosition(totalDuration)
                    .addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            NotificationActionReceiver.ACTION_SHUFFLE,
                            if (isShuffleEnabled) "Shuffle On" else "Shuffle Off",
                            if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
                        ).build()
                    )
                    .addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            NotificationActionReceiver.ACTION_REPEAT,
                            when (repeatMode) {
                                RepeatMode.ONE -> "Repeat One"
                                RepeatMode.ALL -> "Repeat All"
                                else -> "Repeat Off"
                            },
                            when (repeatMode) {
                                RepeatMode.ONE -> R.drawable.ic_repeat_one
                                RepeatMode.ALL -> R.drawable.ic_repeat_all
                                else -> R.drawable.ic_repeat_off
                            }
                        ).build()
                    )
                    .addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            NotificationActionReceiver.ACTION_FAVORITE,
                            if (isFavorite) "Remove Favorite" else "Add Favorite",
                            if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
                        ).build()
                    )
                    .build()

                session.setPlaybackState(playbackState)

                // Sync standard MediaSession Shuffle & Repeat modes
                val sessionShuffleMode = if (isShuffleEnabled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE
                session.setShuffleMode(sessionShuffleMode)

                val sessionRepeatMode = when (repeatMode) {
                    RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                    RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
                session.setRepeatMode(sessionRepeatMode)

                session.isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showNotification(
        context: Context,
        song: Song,
        isPlaying: Boolean,
        currentPositionMs: Long = 0L,
        durationMs: Long = 0L,
        isShuffleEnabled: Boolean = false,
        repeatMode: RepeatMode = RepeatMode.OFF,
        isFavorite: Boolean = false,
        activeQueue: List<Song> = emptyList()
    ) {
        if (isNotificationDismissed && !isPlaying) {
            return
        }
        isRadioSessionActive = false
        isNotificationDismissed = false

        initMediaSession(context)
        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val artBitmap = loadSongArtwork(context, song)

        val totalDuration = if (durationMs > 0) durationMs else (if (song.duration > 0) song.duration else 0L)

        // 1. Update MediaSession state & controls with elapsedRealtime
        updatePlaybackState(
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            durationMs = totalDuration,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isFavorite = isFavorite
        )

        // 2. Update MediaSession metadata (Title, Artist, Album, Cover Art)
        try {
            mediaSession?.let { session ->
                val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, song.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, totalDuration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBitmap)
                    .build()

                session.setMetadata(metadata)

                if (activeQueue.isNotEmpty()) {
                    val queueItems = activeQueue.take(30).mapIndexed { _, qSong ->
                        val qMetadata = android.support.v4.media.MediaDescriptionCompat.Builder()
                            .setMediaId(qSong.id.toString())
                            .setTitle(qSong.title)
                            .setSubtitle(qSong.artist)
                            .build()
                        MediaSessionCompat.QueueItem(qMetadata, qSong.id)
                    }
                    session.setQueue(queueItems)
                }

                session.isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Always ensure session is active so lockscreen controls never disconnect
        mediaSession?.isActive = true

        // Intent to open MainActivity on notification tap
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "com.kexora.soniclet.ACTION_OPEN_TAB"
            putExtra("target_tab", "NOW_PLAYING")
        }

        val pendingContentIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun createPendingIntent(actionString: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = actionString
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val favoriteIntent = createPendingIntent(NotificationActionReceiver.ACTION_FAVORITE, 101)
        val prevIntent = createPendingIntent(NotificationActionReceiver.ACTION_PREVIOUS, 102)
        val playPauseIntent = createPendingIntent(NotificationActionReceiver.ACTION_PLAY_PAUSE, 103)
        val nextIntent = createPendingIntent(NotificationActionReceiver.ACTION_NEXT, 104)
        val shuffleIntent = createPendingIntent(NotificationActionReceiver.ACTION_SHUFFLE, 105)
        val repeatIntent = createPendingIntent(NotificationActionReceiver.ACTION_REPEAT, 106)
        val stopIntent = createPendingIntent(NotificationActionReceiver.ACTION_STOP, 109)

        val favIcon = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        val shuffleIcon = if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val repeatIcon = when (repeatMode) {
            RepeatMode.ONE -> R.drawable.ic_repeat_one
            RepeatMode.ALL -> R.drawable.ic_repeat_all
            else -> R.drawable.ic_repeat_off
        }

        val shuffleText = if (isShuffleEnabled) "Shuffle: ON" else "Shuffle: OFF"
        val repeatText = when (repeatMode) {
            RepeatMode.ONE -> "Repeat: ONE"
            RepeatMode.ALL -> "Repeat: ALL"
            else -> "Repeat: OFF"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_soniclet)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
            .setSubText("$shuffleText • $repeatText")
            .setLargeIcon(artBitmap)
            .setContentIntent(pendingContentIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(shuffleIcon, if (isShuffleEnabled) "Shuffle" else "Unshuffle", shuffleIntent)
            .addAction(R.drawable.ic_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_next, "Next", nextIntent)
            .addAction(repeatIcon, "Repeat", repeatIntent)

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
        }

        val notification = builder.build()
        lastNotification = notification
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            MusicPlaybackService.updateForegroundNotification(notification, context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Syncs Soniclet Radio playback directly to Android's Media Control Center, Lockscreen,
     * Notification Shade, and Bluetooth/headset controls with a clean minimal layout:
     * Artwork + Title only (no seekbar slider or extra subtitle) and Previous, Play/Pause, Next controls.
     */
    fun showRadioNotification(
        context: Context,
        title: String,
        isPlaying: Boolean,
        artworkBitmap: Bitmap? = null
    ) {
        isRadioSessionActive = true
        isNotificationDismissed = false

        initMediaSession(context)
        createNotificationChannel(context)

        val safeTitle = title.trim().ifBlank { "Soniclet Radio" }
        val artBitmap = if (artworkBitmap != null && !artworkBitmap.isRecycled) {
            artworkBitmap
        } else {
            createDefaultArtworkBitmap(context, safeTitle, 256)
        }

        try {
            mediaSession?.let { session ->
                val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                val playbackSpeed = if (isPlaying) 1.0f else 0.0f
                // Only Previous, Play/Pause, Next, and Stop (no seek/rewind/fast-forward -> hides slider)
                val actions = PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_STOP

                val stopCustomAction = PlaybackStateCompat.CustomAction.Builder(
                    NotificationActionReceiver.ACTION_STOP_RADIO,
                    "Stop Radio",
                    R.drawable.ic_stop
                ).build()

                val playbackState = PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .addCustomAction(stopCustomAction)
                    .setState(
                        state,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                        playbackSpeed,
                        SystemClock.elapsedRealtime()
                    )
                    .build()

                session.setPlaybackState(playbackState)

                // Artwork + Title only; negative duration signals non-seekable live radio so OS hides slider
                val metadata = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, safeTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, safeTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artBitmap)
                    .build()

                session.setMetadata(metadata)
                session.isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            action = "com.kexora.soniclet.ACTION_OPEN_TAB"
            putExtra("target_tab", "RADIO")
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            2041,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun createRadioPendingIntent(actionString: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = actionString
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val prevIntent = createRadioPendingIntent(NotificationActionReceiver.ACTION_PREVIOUS, 202)
        val playPauseIntent = createRadioPendingIntent(NotificationActionReceiver.ACTION_PLAY_PAUSE, 203)
        val nextIntent = createRadioPendingIntent(NotificationActionReceiver.ACTION_NEXT, 204)
        val stopRadioIntent = createRadioPendingIntent(NotificationActionReceiver.ACTION_STOP_RADIO, 209)

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_soniclet)
            .setContentTitle(safeTitle)
            .setLargeIcon(artBitmap)
            .setContentIntent(pendingContentIntent)
            .setDeleteIntent(stopRadioIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(R.drawable.ic_next, "Next", nextIntent)
            .addAction(R.drawable.ic_stop, "Stop Radio", stopRadioIntent)

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
        }

        val notification = builder.build()
        lastNotification = notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            MusicPlaybackService.updateRadioForegroundNotification(notification, context, isPlaying, safeTitle)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissRadioNotification(context: Context) {
        if (!isRadioSessionActive) return
        isRadioSessionActive = false
        dismissNotification(context)
    }

    fun dismissNotification(context: Context) {
        isRadioSessionActive = false
        isNotificationDismissed = true
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        mediaSession?.isActive = false
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Soniclet Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live notifications for lock screen and status bar audio controls"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Loads artwork scaled to safe 256x256 dimensions using RGB_565 (131 KB)
     * to guarantee zero Binder TransactionTooLargeException across all Android versions.
     * Uses modern MediaStore loadThumbnail on Android 10+ (API 29+), falling back
     * to custom artwork, MediaMetadataRetriever, and dynamic vinyl fallback.
     */
    private fun loadSongArtwork(context: Context, song: Song): Bitmap {
        if (cachedArtworkSongId == song.id && cachedArtworkBitmap != null && !cachedArtworkBitmap!!.isRecycled) {
            return cachedArtworkBitmap!!
        }

        val targetSize = 256

        // Strict per-song isolation: custom artwork for song.id -> track's own embedded picture -> fallback vinyl art
        // NEVER uses MediaStore loadThumbnail or shared content://media/external/audio/albumart/$albumId
        val resolved = CustomArtworkManager.loadStrictSongArtworkBitmap(
            context = context,
            songId = song.id,
            audioUri = song.contentUri,
            maxDimPx = targetSize,
            preferredConfig = Bitmap.Config.RGB_565
        )
        if (resolved != null) {
            val scaled = if (resolved.width != targetSize || resolved.height != targetSize) {
                Bitmap.createScaledBitmap(resolved, targetSize, targetSize, true)
            } else {
                resolved
            }
            if (scaled !== resolved && !resolved.isRecycled) {
                resolved.recycle()
            }
            cachedArtworkSongId = song.id
            cachedArtworkBitmap = scaled
            return scaled
        }

        // 5. Fallback dynamic vinyl art
        val defaultBm = createDefaultArtworkBitmap(context, song.title, targetSize)
        cachedArtworkSongId = song.id
        cachedArtworkBitmap = defaultBm
        return defaultBm
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun createDefaultArtworkBitmap(context: Context, songTitle: String = "", size: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)

        val palettePairs = listOf(
            Pair(Color.parseColor("#FF3366"), Color.parseColor("#FF6F00")),
            Pair(Color.parseColor("#FF5722"), Color.parseColor("#FF9800")),
            Pair(Color.parseColor("#D81B60"), Color.parseColor("#E91E63")),
            Pair(Color.parseColor("#E65100"), Color.parseColor("#F57C00")),
            Pair(Color.parseColor("#4A148C"), Color.parseColor("#7B1FA2")),
            Pair(Color.parseColor("#6200EA"), Color.parseColor("#7C4DFF")),
            Pair(Color.parseColor("#311B92"), Color.parseColor("#512DA8")),
            Pair(Color.parseColor("#0D47A1"), Color.parseColor("#1976D2")),
            Pair(Color.parseColor("#01579B"), Color.parseColor("#0288D1")),
            Pair(Color.parseColor("#006064"), Color.parseColor("#0097A7")),
            Pair(Color.parseColor("#1B5E20"), Color.parseColor("#2E7D32")),
            Pair(Color.parseColor("#004D40"), Color.parseColor("#00897B")),
            Pair(Color.parseColor("#4A0E4E"), Color.parseColor("#FF4081")),
            Pair(Color.parseColor("#FC5C7D"), Color.parseColor("#6A82FB")),
            Pair(Color.parseColor("#11998E"), Color.parseColor("#38EF7D")),
            Pair(Color.parseColor("#FF416C"), Color.parseColor("#FF4B2B"))
        )
        val hash = Math.abs(songTitle.trim().hashCode())
        val selectedPair = palettePairs[hash % palettePairs.size]

        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                selectedPair.first,
                selectedPair.second,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val groovePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.argb(40, 255, 255, 255)
            strokeWidth = 2f
        }
        val center = size / 2f
        canvas.drawCircle(center, center, size * 0.44f, groovePaint)
        groovePaint.strokeWidth = 1.5f
        canvas.drawCircle(center, center, size * 0.34f, groovePaint)
        canvas.drawCircle(center, center, size * 0.24f, groovePaint)

        val corePaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(89, 0, 0, 0)
        }
        val coreRadius = size * 0.26f
        canvas.drawCircle(center, center, coreRadius, corePaint)

        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_soniclet_app_logo)
        if (drawable != null) {
            val iconSize = (coreRadius * 1.35f).toInt()
            val offset = (size - iconSize) / 2
            drawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
            DrawableCompat.setTint(drawable, Color.argb(245, 255, 255, 255))
            drawable.draw(canvas)
        }
        return bitmap
    }
}
