package com.kexora.soniclet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.palette.graphics.Palette
import com.kexora.soniclet.MainActivity
import com.kexora.soniclet.R
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.RepeatMode
import com.kexora.soniclet.service.NotificationActionReceiver
import com.kexora.soniclet.utils.SmartTitleCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

object SonicletWidgetManager {

    private const val PREFS_NAME = "soniclet_widget_prefs"

    // Customization Keys
    const val KEY_THEME = "widget_theme" // AMOLED, GLASS, LIGHT_GLASS, ALBUM_COLOR
    const val KEY_CORNER_RADIUS = "widget_corner_radius" // PILL, ROUNDED, SQUARE
    const val KEY_CORNER_RADIUS_FLOAT = "widget_corner_radius_float" // 0.0f (square) to 1.0f (pill)
    const val KEY_OPACITY = "widget_opacity" // 0.20f to 1.0f
    const val KEY_BLUR = "widget_blur" // 0.0f (sharp) to 1.0f (deep blur)
    const val KEY_ACCENT_COLOR = "widget_accent_color" // Long ARGB hex (e.g., 0xFF00E676)
    const val KEY_BG_COVER_ART = "widget_bg_cover_art" // Show album photo in background
    const val KEY_HIDE_ART_THUMBNAIL = "widget_hide_art_thumbnail"
    const val KEY_SHOW_FAVORITE = "widget_show_favorite"
    const val KEY_SHOW_SECONDARY = "widget_show_secondary"
    const val KEY_SHOW_PROGRESS = "widget_show_progress"
    const val KEY_SHOW_QUALITY_TAG = "widget_show_quality_tag"
    const val KEY_SHOW_SEEK_BUTTONS = "widget_show_seek_buttons"
    const val KEY_SHOW_PREV_NEXT = "widget_show_prev_next"
    const val KEY_SHOW_SHUFFLE_REPEAT = "widget_show_shuffle_repeat"
    const val KEY_DYNAMIC_THEME = "widget_dynamic_theme"
    const val KEY_LIVE_SYNC = "widget_live_sync"

    // Last Known Song Cache
    private const val CACHE_SONG_TITLE = "cache_song_title"
    private const val CACHE_SONG_ARTIST = "cache_song_artist"
    private const val CACHE_SONG_ALBUM = "cache_song_album"
    private const val CACHE_SONG_URI = "cache_song_uri"
    private const val CACHE_SONG_ART_URI = "cache_song_art_uri"
    private const val CACHE_SONG_PATH = "cache_song_path"
    private const val CACHE_SONG_DURATION = "cache_song_duration"
    private const val CACHE_IS_PLAYING = "cache_is_playing"
    private const val CACHE_IS_FAV = "cache_is_fav"
    private const val CACHE_REPEAT_MODE = "cache_repeat_mode"
    private const val CACHE_IS_SHUFFLE = "cache_is_shuffle"
    private const val CACHE_POSITION = "cache_position"

    private const val DEFAULT_ACCENT_HEX = 0xFF00E676L

    fun triggerHeartPop(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean("CACHE_HEART_POP", true).apply()
        updateAllWidgetsFromCache(context)
        GlobalScope.launch(Dispatchers.Main) {
            delay(200)
            prefs.edit().putBoolean("CACHE_HEART_POP", false).apply()
            updateAllWidgetsFromCache(context)
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveCustomization(
        context: Context,
        theme: String? = null,
        cornerRadius: String? = null,
        cornerRadiusFloat: Float? = null,
        opacity: Float? = null,
        blur: Float? = null,
        accentColor: Long? = null,
        bgCoverArt: Boolean? = null,
        hideArtThumbnail: Boolean? = null,
        showFavorite: Boolean? = null,
        showSecondary: Boolean? = null,
        showProgress: Boolean? = null,
        showQualityTag: Boolean? = null,
        showSeekButtons: Boolean? = null,
        showPrevNext: Boolean? = null,
        showShuffleRepeat: Boolean? = null,
        dynamicTheme: Boolean? = null,
        liveSync: Boolean? = null
    ) {
        val editor = getPrefs(context).edit()
        theme?.let { editor.putString(KEY_THEME, it) }
        cornerRadius?.let { editor.putString(KEY_CORNER_RADIUS, it) }
        cornerRadiusFloat?.let { editor.putFloat(KEY_CORNER_RADIUS_FLOAT, it.coerceIn(0f, 1f)) }
        opacity?.let { editor.putFloat(KEY_OPACITY, it.coerceIn(0.20f, 1f)) }
        blur?.let { editor.putFloat(KEY_BLUR, it.coerceIn(0f, 1f)) }
        accentColor?.let { editor.putLong(KEY_ACCENT_COLOR, it) }
        bgCoverArt?.let { editor.putBoolean(KEY_BG_COVER_ART, it) }
        hideArtThumbnail?.let { editor.putBoolean(KEY_HIDE_ART_THUMBNAIL, it) }
        showFavorite?.let { editor.putBoolean(KEY_SHOW_FAVORITE, it) }
        showSecondary?.let { editor.putBoolean(KEY_SHOW_SECONDARY, it) }
        showProgress?.let { editor.putBoolean(KEY_SHOW_PROGRESS, it) }
        showQualityTag?.let { editor.putBoolean(KEY_SHOW_QUALITY_TAG, it) }
        showSeekButtons?.let { editor.putBoolean(KEY_SHOW_SEEK_BUTTONS, it) }
        showPrevNext?.let { editor.putBoolean(KEY_SHOW_PREV_NEXT, it) }
        showShuffleRepeat?.let { editor.putBoolean(KEY_SHOW_SHUFFLE_REPEAT, it) }
        dynamicTheme?.let { editor.putBoolean(KEY_DYNAMIC_THEME, it) }
        liveSync?.let { editor.putBoolean(KEY_LIVE_SYNC, it) }
        editor.apply()

        updateAllWidgetsFromCache(context)
    }

    fun updateAllWidgets(
        context: Context,
        song: Song?,
        isPlaying: Boolean,
        isFavorite: Boolean,
        repeatMode: RepeatMode = RepeatMode.OFF,
        isShuffle: Boolean = false,
        currentPositionMs: Long = 0L
    ) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()

        if (song != null) {
            val cleanedTitle = SmartTitleCleaner.cleanTitle(song.title, song.filePath)
            val cleanedArtist = if (SmartTitleCleaner.isPlaceholderArtist(song.artist)) {
                "Unknown Artist"
            } else {
                song.artist.trim()
            }
            editor.putString(CACHE_SONG_TITLE, cleanedTitle)
            editor.putString(CACHE_SONG_ARTIST, cleanedArtist)
            editor.putString(CACHE_SONG_ALBUM, song.album)
            editor.putString(CACHE_SONG_URI, song.contentUri.toString())
            editor.putString(CACHE_SONG_ART_URI, song.albumArtUri?.toString())
            editor.putString(CACHE_SONG_PATH, song.filePath)
            editor.putLong(CACHE_SONG_DURATION, song.duration)
        }
        editor.putBoolean(CACHE_IS_PLAYING, isPlaying)
        editor.putBoolean(CACHE_IS_FAV, isFavorite)
        editor.putString(CACHE_REPEAT_MODE, repeatMode.name)
        editor.putBoolean(CACHE_IS_SHUFFLE, isShuffle)
        editor.putLong(CACHE_POSITION, currentPositionMs)
        editor.apply()

        val title = prefs.getString(CACHE_SONG_TITLE, "Soniclet Music") ?: "Soniclet Music"
        val artist = prefs.getString(CACHE_SONG_ARTIST, "Tap to play") ?: "Tap to play"
        val album = prefs.getString(CACHE_SONG_ALBUM, "Soniclet Library") ?: "Soniclet Library"
        val contentUriString = song?.contentUri?.toString() ?: prefs.getString(CACHE_SONG_URI, null)
        val artUriString = song?.albumArtUri?.toString() ?: prefs.getString(CACHE_SONG_ART_URI, null)
        val filePath = song?.filePath ?: prefs.getString(CACHE_SONG_PATH, "") ?: ""
        val durationMs = song?.duration ?: prefs.getLong(CACHE_SONG_DURATION, 0L)

        renderAllWidgetProviders(
            context = context,
            title = title,
            artist = artist,
            album = album,
            contentUriString = contentUriString,
            artUriString = artUriString,
            filePath = filePath,
            durationMs = durationMs,
            positionMs = currentPositionMs,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            isHeartPop = false
        )
    }

    fun updateAllWidgetsFromCache(context: Context) {
        val prefs = getPrefs(context)
        val title = prefs.getString(CACHE_SONG_TITLE, "Soniclet Music") ?: "Soniclet Music"
        val artist = prefs.getString(CACHE_SONG_ARTIST, "Tap to play") ?: "Tap to play"
        val album = prefs.getString(CACHE_SONG_ALBUM, "Soniclet Library") ?: "Soniclet Library"
        val contentUriString = prefs.getString(CACHE_SONG_URI, null)
        val artUriString = prefs.getString(CACHE_SONG_ART_URI, null)
        val filePath = prefs.getString(CACHE_SONG_PATH, "") ?: ""
        val durationMs = prefs.getLong(CACHE_SONG_DURATION, 0L)
        val positionMs = prefs.getLong(CACHE_POSITION, 0L)
        val isPlaying = prefs.getBoolean(CACHE_IS_PLAYING, false)
        val isFavorite = prefs.getBoolean(CACHE_IS_FAV, false)
        val repeatModeStr = prefs.getString(CACHE_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name
        val repeatMode = try { RepeatMode.valueOf(repeatModeStr) } catch (_: Exception) { RepeatMode.OFF }
        val isShuffle = prefs.getBoolean(CACHE_IS_SHUFFLE, false)
        val isHeartPop = prefs.getBoolean("CACHE_HEART_POP", false)

        renderAllWidgetProviders(
            context = context,
            title = title,
            artist = artist,
            album = album,
            contentUriString = contentUriString,
            artUriString = artUriString,
            filePath = filePath,
            durationMs = durationMs,
            positionMs = positionMs,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            isHeartPop = isHeartPop
        )
    }

    private fun renderAllWidgetProviders(
        context: Context,
        title: String,
        artist: String,
        album: String,
        contentUriString: String?,
        artUriString: String?,
        filePath: String,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean,
        isFavorite: Boolean,
        repeatMode: RepeatMode,
        isShuffle: Boolean,
        isHeartPop: Boolean = false
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val adaptiveIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, SonicletWidgetProvider::class.java)
        )

        for (id in adaptiveIds) {
            updateWidgetByIdInternal(
                context, appWidgetManager, id,
                title, artist, album, contentUriString, artUriString, filePath,
                durationMs, positionMs, isPlaying, isFavorite, repeatMode.name, isShuffle, isHeartPop
            )
        }
    }

    fun updateWidgetById(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val prefs = getPrefs(context)
        val title = prefs.getString(CACHE_SONG_TITLE, "Soniclet Music") ?: "Soniclet Music"
        val artist = prefs.getString(CACHE_SONG_ARTIST, "Tap to play") ?: "Tap to play"
        val album = prefs.getString(CACHE_SONG_ALBUM, "Soniclet Library") ?: "Soniclet Library"
        val contentUriString = prefs.getString(CACHE_SONG_URI, null)
        val artUriString = prefs.getString(CACHE_SONG_ART_URI, null)
        val filePath = prefs.getString(CACHE_SONG_PATH, "") ?: ""
        val durationMs = prefs.getLong(CACHE_SONG_DURATION, 0L)
        val positionMs = prefs.getLong(CACHE_POSITION, 0L)
        val isPlaying = prefs.getBoolean(CACHE_IS_PLAYING, false)
        val isFavorite = prefs.getBoolean(CACHE_IS_FAV, false)
        val repeatModeStr = prefs.getString(CACHE_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name
        val isShuffle = prefs.getBoolean(CACHE_IS_SHUFFLE, false)
        val isHeartPop = prefs.getBoolean("CACHE_HEART_POP", false)

        updateWidgetByIdInternal(
            context, appWidgetManager, appWidgetId,
            title, artist, album, contentUriString, artUriString, filePath,
            durationMs, positionMs, isPlaying, isFavorite, repeatModeStr, isShuffle, isHeartPop
        )
    }

    private fun updateWidgetByIdInternal(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String,
        artist: String,
        album: String,
        contentUriString: String?,
        artUriString: String?,
        filePath: String,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean,
        isFavorite: Boolean,
        repeatModeStr: String,
        isShuffle: Boolean,
        isHeartPop: Boolean
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

        val width = if (minWidth == 0) 250 else minWidth
        val height = if (maxHeight == 0) 110 else maxHeight
        val repeatMode = try { RepeatMode.valueOf(repeatModeStr) } catch (_: Exception) { RepeatMode.OFF }

        val views = when {
            width < 110 && height < 110 -> buildTinyRemoteViews(
                context, title, contentUriString, artUriString, isPlaying
            )
            width < 220 && height < 110 -> buildSmallBarRemoteViews(
                context, title, artist, contentUriString, artUriString, isPlaying
            )
            width >= 220 && height < 110 -> buildCompactRemoteViews(
                context, title, artist, contentUriString, artUriString, isPlaying, isFavorite, durationMs, positionMs, isHeartPop
            )
            width < 200 && height in 110..179 -> buildSquareRemoteViews(
                context, title, artist, contentUriString, artUriString, isPlaying
            )
            width >= 200 && height in 110..179 -> buildCardRemoteViews(
                context, title, artist, album, contentUriString, artUriString, durationMs, positionMs, isPlaying, isFavorite, repeatMode, isShuffle, isHeartPop
            )
            height >= 180 && width < 230 -> buildVinylRemoteViews(
                context, title, artist, contentUriString, artUriString, isPlaying, isFavorite, isShuffle, isHeartPop
            )
            else -> buildProRemoteViews(
                context, title, artist, album, contentUriString, artUriString, filePath, durationMs, positionMs, isPlaying, isFavorite, repeatMode, isShuffle, isHeartPop
            )
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun readAccentColor(prefs: SharedPreferences): Int {
        return try {
            prefs.getLong(KEY_ACCENT_COLOR, DEFAULT_ACCENT_HEX).toInt()
        } catch (_: Exception) {
            try {
                prefs.getInt(KEY_ACCENT_COLOR, DEFAULT_ACCENT_HEX.toInt())
            } catch (_: Exception) {
                DEFAULT_ACCENT_HEX.toInt()
            }
        }
    }

    private fun isLightSurface(theme: String, bgCoverArt: Boolean, hasRealCoverArt: Boolean): Boolean {
        return theme == "LIGHT_GLASS" && !(bgCoverArt && hasRealCoverArt)
    }

    private fun detectQualityBadge(filePath: String, uriString: String?): String {
        val sample = (filePath.ifBlank { uriString ?: "" }).lowercase(Locale.ROOT)
        return when {
            sample.endsWith(".flac") || sample.endsWith(".wav") || sample.endsWith(".alac") || sample.endsWith(".aiff") -> "HI-RES"
            sample.endsWith(".m4a") || sample.endsWith(".aac") -> "M4A"
            sample.endsWith(".ogg") || sample.endsWith(".opus") -> "OPUS"
            else -> "MP3"
        }
    }

    private fun applyIconTint(views: RemoteViews, viewId: Int, tintColor: Int) {
        views.setInt(viewId, "setColorFilter", tintColor)
    }

    // PendingIntent Generators
    fun getLaunchAppPendingIntent(
        context: Context,
        reqCode: Int,
        actionStr: String? = null,
        targetTab: String? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (actionStr != null) action = actionStr
            if (targetTab != null) putExtra("target_tab", targetTab)
        }
        return PendingIntent.getActivity(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getActionPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // 1. Tiny 1x1 Widget (Full-bleed rounded album cover + centered Play/Pause, zero cramped text)
    fun buildTinyRemoteViews(
        context: Context,
        title: String,
        contentUriString: String?,
        artUriString: String?,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tiny_layout)
        val prefs = getPrefs(context)
        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 0.55f)
        val accentColor = readAccentColor(prefs)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val dynamicTheme = theme == "ALBUM_COLOR" || prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.85f)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null

        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)

        if (hasRealCoverArt) {
            val roundedArt = getRoundedCornerBitmap(rawArtBitmap!!, 220, 220, cornerRadiusFloat * 55f)
            views.setImageViewBitmap(R.id.widget_art, roundedArt)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
            views.setViewVisibility(R.id.widget_dynamic_bg, View.GONE)
            views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
        } else {
            val dynBg = getGeneratedBgBitmap(
                220, 220, null, false, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, 0.35f
            )
            views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
            views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)
            views.setViewVisibility(R.id.widget_art, View.GONE)
            views.setViewVisibility(R.id.widget_scrim, View.GONE)
        }

        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))
        views.setOnClickPendingIntent(
            R.id.widget_root,
            getLaunchAppPendingIntent(context, 101, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING")
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_play,
            getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 104)
        )
        return views
    }

    // 2. Small Bar 4x1 Widget
    fun buildSmallBarRemoteViews(
        context: Context,
        title: String,
        artist: String = "Tap to play",
        contentUriString: String? = null,
        artUriString: String? = null,
        isPlaying: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_small_bar_layout)
        val prefs = getPrefs(context)
        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 0.55f)
        val accentColor = readAccentColor(prefs)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val dynamicTheme = theme == "ALBUM_COLOR" || prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.85f)
        val blur = prefs.getFloat(KEY_BLUR, 0.55f)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 120, 120, 12f)
        val lightMode = isLightSurface(theme, bgCoverArt, hasRealCoverArt)

        views.setImageViewBitmap(R.id.widget_art, artBitmap)
        views.setViewVisibility(R.id.widget_art, View.VISIBLE)

        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(
            640, 180, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur
        )
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        val titleColor = if (lightMode) Color.parseColor("#141917") else Color.WHITE
        val subtitleColor = if (lightMode) Color.parseColor("#545B58") else Color.parseColor("#B8BCC2")
        val iconTint = if (lightMode) Color.parseColor("#1D2320") else Color.WHITE

        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextColor(R.id.widget_artist, subtitleColor)

        applyIconTint(views, R.id.widget_btn_prev, iconTint)
        applyIconTint(views, R.id.widget_btn_next, iconTint)

        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        views.setOnClickPendingIntent(
            R.id.widget_root,
            getLaunchAppPendingIntent(context, 111, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING")
        )
        views.setOnClickPendingIntent(
            R.id.widget_info_container,
            getLaunchAppPendingIntent(context, 112, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING")
        )
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 113))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 114))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 115))
        return views
    }

    // 3. Compact 4x1 Widget
    fun buildCompactRemoteViews(
        context: Context,
        title: String,
        artist: String,
        contentUriString: String?,
        artUriString: String?,
        isPlaying: Boolean,
        isFavorite: Boolean,
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        isHeartPop: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_layout)
        val prefs = getPrefs(context)

        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 0.55f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showPrevNext = prefs.getBoolean(KEY_SHOW_PREV_NEXT, true)
        val showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val accentColor = readAccentColor(prefs)
        val dynamicTheme = theme == "ALBUM_COLOR" || prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.85f)
        val blur = prefs.getFloat(KEY_BLUR, 0.55f)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 140, 140, 14f)
        val lightMode = isLightSurface(theme, bgCoverArt, hasRealCoverArt)

        views.setImageViewBitmap(R.id.widget_art, artBitmap)
        views.setViewVisibility(R.id.widget_art, View.VISIBLE)

        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(
            800, 200, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur
        )
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        val titleColor = if (lightMode) Color.parseColor("#141917") else Color.WHITE
        val subtitleColor = if (lightMode) Color.parseColor("#545B58") else Color.parseColor("#B8BCC2")
        val iconTint = if (lightMode) Color.parseColor("#1D2320") else Color.WHITE

        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextColor(R.id.widget_artist, subtitleColor)

        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        applyIconTint(views, R.id.widget_btn_prev, iconTint)
        applyIconTint(views, R.id.widget_btn_next, iconTint)
        views.setViewVisibility(R.id.widget_btn_prev, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (showPrevNext) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        applyIconTint(
            views,
            R.id.widget_btn_fav,
            when {
                isHeartPop -> Color.RED
                isFavorite -> accentColor
                else -> iconTint
            }
        )
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)

        if (showProgress && durationMs > 0) {
            views.setViewVisibility(R.id.widget_progress_container, View.VISIBLE)
            val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_progress, 100, progressPercent, false)
            views.setOnClickPendingIntent(R.id.widget_bar_seek_backward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 218))
            views.setOnClickPendingIntent(R.id.widget_bar_seek_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 219))
        } else {
            views.setViewVisibility(R.id.widget_progress_container, View.GONE)
        }

        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 201))
        views.setOnClickPendingIntent(R.id.widget_art, getLaunchAppPendingIntent(context, 202, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_info_container, getLaunchAppPendingIntent(context, 203, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 204))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 205))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 206))
        views.setOnClickPendingIntent(R.id.widget_btn_fav, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAVORITE, 207))

        return views
    }

    // 4. Square 2x2 Widget
    fun buildSquareRemoteViews(
        context: Context,
        title: String,
        artist: String,
        contentUriString: String?,
        artUriString: String?,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_square_layout)
        val prefs = getPrefs(context)
        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 0.55f)
        val accentColor = readAccentColor(prefs)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val dynamicTheme = theme == "ALBUM_COLOR" || prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.85f)
        val blur = prefs.getFloat(KEY_BLUR, 0.55f)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 180, 180, 16f)
        val lightMode = isLightSurface(theme, bgCoverArt, hasRealCoverArt)

        views.setImageViewBitmap(R.id.widget_art, artBitmap)
        views.setViewVisibility(R.id.widget_art, View.VISIBLE)

        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(
            400, 400, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur
        )
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        val titleColor = if (lightMode) Color.parseColor("#141917") else Color.WHITE
        val subtitleColor = if (lightMode) Color.parseColor("#545B58") else Color.parseColor("#B8BCC2")
        val iconTint = if (lightMode) Color.parseColor("#1D2320") else Color.WHITE

        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextColor(R.id.widget_artist, subtitleColor)

        applyIconTint(views, R.id.widget_btn_prev, iconTint)
        applyIconTint(views, R.id.widget_btn_next, iconTint)

        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 121, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 123))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 124))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 125))
        return views
    }

    // 5. Vinyl 2x2 Widget
    fun buildVinylRemoteViews(
        context: Context,
        title: String,
        artist: String,
        contentUriString: String?,
        artUriString: String?,
        isPlaying: Boolean,
        isFavorite: Boolean,
        isShuffle: Boolean,
        isHeartPop: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vinyl_layout)
        val prefs = getPrefs(context)

        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 0.55f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showShuffleRepeat = prefs.getBoolean(KEY_SHOW_SHUFFLE_REPEAT, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val accentColor = readAccentColor(prefs)
        val dynamicTheme = theme == "ALBUM_COLOR" || prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.85f)
        val blur = prefs.getFloat(KEY_BLUR, 0.55f)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val circleArt = loadCircularArtworkBitmap(context, contentUriString, artUriString, 180)
        val lightMode = isLightSurface(theme, bgCoverArt, hasRealCoverArt)

        views.setImageViewBitmap(R.id.widget_art, circleArt)

        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(
            420, 420, rawArtBitmap ?: circleArt, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur
        )
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        val titleColor = if (lightMode) Color.parseColor("#141917") else Color.WHITE
        val subtitleColor = if (lightMode) Color.parseColor("#545B58") else Color.parseColor("#B8BCC2")
        val iconTint = if (lightMode) Color.parseColor("#1D2320") else Color.WHITE

        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextColor(R.id.widget_artist, subtitleColor)

        applyIconTint(views, R.id.widget_btn_prev, iconTint)
        applyIconTint(views, R.id.widget_btn_next, iconTint)

        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        applyIconTint(
            views,
            R.id.widget_btn_fav,
            when {
                isHeartPop -> Color.RED
                isFavorite -> accentColor
                else -> iconTint
            }
        )
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_shuffle, if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
        applyIconTint(views, R.id.widget_btn_shuffle, if (isShuffle) accentColor else iconTint)
        views.setViewVisibility(R.id.widget_btn_shuffle, if (showShuffleRepeat) View.VISIBLE else View.GONE)

        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 401))
        views.setOnClickPendingIntent(R.id.widget_vinyl_outer, getLaunchAppPendingIntent(context, 402, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 403))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 404))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 405))
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, getActionPendingIntent(context, NotificationActionReceiver.ACTION_SHUFFLE, 406))
        views.setOnClickPendingIntent(R.id.widget_btn_fav, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAVORITE, 407))

        return views
    }

    // 6. Card 4x2 Widget
    fun buildCardRemoteViews(
        context: Context,
        title: String,
        artist: String,
        album: String,
        contentUriString: String?,
        artUriString: String?,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean,
        isFavorite: Boolean,
        repeatMode: RepeatMode,
        isShuffle: Boolean,
        isHeartPop: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_card_layout)
        val prefs = getPrefs(context)

        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 0.55f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showSecondary = prefs.getBoolean(KEY_SHOW_SECONDARY, true)
        val showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true)
        val showSeekButtons = prefs.getBoolean(KEY_SHOW_SEEK_BUTTONS, true)
        val showPrevNext = prefs.getBoolean(KEY_SHOW_PREV_NEXT, true)
        val showShuffleRepeat = prefs.getBoolean(KEY_SHOW_SHUFFLE_REPEAT, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val accentColor = readAccentColor(prefs)
        val dynamicTheme = theme == "ALBUM_COLOR" || prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.85f)
        val blur = prefs.getFloat(KEY_BLUR, 0.55f)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 180, 180, 18f)
        val lightMode = isLightSurface(theme, bgCoverArt, hasRealCoverArt)

        views.setImageViewBitmap(R.id.widget_art, artBitmap)
        views.setViewVisibility(R.id.widget_art, View.VISIBLE)

        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(
            800, 340, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur
        )
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        val titleColor = if (lightMode) Color.parseColor("#141917") else Color.WHITE
        val subtitleColor = if (lightMode) Color.parseColor("#545B58") else Color.parseColor("#C2C6CC")
        val mutedColor = if (lightMode) Color.parseColor("#717875") else Color.parseColor("#92979E")
        val iconTint = if (lightMode) Color.parseColor("#1D2320") else Color.WHITE

        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextColor(R.id.widget_artist, subtitleColor)
        views.setTextViewText(R.id.widget_subtitle, album)
        views.setTextColor(R.id.widget_subtitle, mutedColor)
        views.setViewVisibility(R.id.widget_subtitle, if (showSecondary && album.isNotBlank()) View.VISIBLE else View.GONE)

        if (showProgress && durationMs > 0) {
            views.setViewVisibility(R.id.widget_progress_container, View.VISIBLE)
            val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_progress, 100, progressPercent, false)
            views.setOnClickPendingIntent(R.id.widget_bar_seek_backward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 318))
            views.setOnClickPendingIntent(R.id.widget_bar_seek_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 319))
        } else {
            views.setViewVisibility(R.id.widget_progress_container, View.GONE)
        }

        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        applyIconTint(views, R.id.widget_btn_prev, iconTint)
        applyIconTint(views, R.id.widget_btn_next, iconTint)
        applyIconTint(views, R.id.widget_btn_rewind, iconTint)
        applyIconTint(views, R.id.widget_btn_fast_forward, iconTint)

        views.setViewVisibility(R.id.widget_btn_prev, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_rewind, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_fast_forward, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_shuffle, if (showShuffleRepeat) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_repeat, if (showShuffleRepeat) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        applyIconTint(
            views,
            R.id.widget_btn_fav,
            when {
                isHeartPop -> Color.RED
                isFavorite -> accentColor
                else -> iconTint
            }
        )
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_shuffle, if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
        applyIconTint(views, R.id.widget_btn_shuffle, if (isShuffle) accentColor else iconTint)

        views.setImageViewResource(
            R.id.widget_btn_repeat,
            when (repeatMode) {
                RepeatMode.ONE -> R.drawable.ic_repeat_one
                RepeatMode.ALL -> R.drawable.ic_repeat_all
                else -> R.drawable.ic_repeat_off
            }
        )
        applyIconTint(views, R.id.widget_btn_repeat, if (repeatMode != RepeatMode.OFF) accentColor else iconTint)

        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 301))
        views.setOnClickPendingIntent(R.id.widget_art, getLaunchAppPendingIntent(context, 302, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_info_container, getLaunchAppPendingIntent(context, 303, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 304))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 305))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 306))
        views.setOnClickPendingIntent(R.id.widget_btn_rewind, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 307))
        views.setOnClickPendingIntent(R.id.widget_btn_fast_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 308))
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, getActionPendingIntent(context, NotificationActionReceiver.ACTION_SHUFFLE, 309))
        views.setOnClickPendingIntent(R.id.widget_btn_repeat, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REPEAT, 310))
        views.setOnClickPendingIntent(R.id.widget_btn_fav, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAVORITE, 311))

        return views
    }

    // 7. Pro 4x3 Widget
    fun buildProRemoteViews(
        context: Context,
        title: String,
        artist: String,
        album: String,
        contentUriString: String?,
        artUriString: String?,
        filePath: String = "",
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        isPlaying: Boolean = false,
        isFavorite: Boolean = false,
        repeatMode: RepeatMode = RepeatMode.OFF,
        isShuffle: Boolean = false,
        isHeartPop: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_pro_layout)
        val prefs = getPrefs(context)

        val theme = prefs.getString(KEY_THEME, "GLASS") ?: "GLASS"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 0.55f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showSecondary = prefs.getBoolean(KEY_SHOW_SECONDARY, true)
        val showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true)
        val showQualityTag = prefs.getBoolean(KEY_SHOW_QUALITY_TAG, true)
        val showSeekButtons = prefs.getBoolean(KEY_SHOW_SEEK_BUTTONS, true)
        val showPrevNext = prefs.getBoolean(KEY_SHOW_PREV_NEXT, true)
        val showShuffleRepeat = prefs.getBoolean(KEY_SHOW_SHUFFLE_REPEAT, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val accentColor = readAccentColor(prefs)
        val dynamicTheme = theme == "ALBUM_COLOR" || prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.85f)
        val blur = prefs.getFloat(KEY_BLUR, 0.55f)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 220, 220, 20f)
        val lightMode = isLightSurface(theme, bgCoverArt, hasRealCoverArt)

        views.setImageViewBitmap(R.id.widget_art, artBitmap)
        views.setViewVisibility(R.id.widget_art, View.VISIBLE)

        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(
            800, 520, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur
        )
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        val titleColor = if (lightMode) Color.parseColor("#141917") else Color.WHITE
        val subtitleColor = if (lightMode) Color.parseColor("#545B58") else Color.parseColor("#C2C6CC")
        val mutedColor = if (lightMode) Color.parseColor("#717875") else Color.parseColor("#92979E")
        val iconTint = if (lightMode) Color.parseColor("#1D2320") else Color.WHITE

        views.setTextColor(R.id.widget_brand_label, accentColor)
        views.setTextViewText(R.id.widget_audio_badge, detectQualityBadge(filePath, contentUriString))
        views.setTextColor(R.id.widget_audio_badge, titleColor)
        views.setViewVisibility(R.id.widget_audio_badge, if (showQualityTag) View.VISIBLE else View.GONE)

        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextColor(R.id.widget_artist, subtitleColor)
        views.setTextViewText(R.id.widget_subtitle, album)
        views.setTextColor(R.id.widget_subtitle, mutedColor)
        views.setViewVisibility(R.id.widget_subtitle, if (showSecondary && album.isNotBlank()) View.VISIBLE else View.GONE)

        if (showProgress && durationMs > 0) {
            views.setViewVisibility(R.id.widget_progress_section, View.VISIBLE)
            val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            val waveformBitmap = generateWaveformBitmap(420, 56, progressPercent, accentColor)
            views.setImageViewBitmap(R.id.widget_waveform, waveformBitmap)
            views.setTextViewText(R.id.widget_time_elapsed, formatTime(positionMs))
            views.setTextViewText(R.id.widget_time_total, formatTime(durationMs))
            views.setTextColor(R.id.widget_time_elapsed, mutedColor)
            views.setTextColor(R.id.widget_time_total, mutedColor)

            views.setOnClickPendingIntent(R.id.widget_bar_seek_backward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 518))
            views.setOnClickPendingIntent(R.id.widget_bar_seek_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 519))
            views.setOnClickPendingIntent(R.id.widget_time_elapsed, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 520))
            views.setOnClickPendingIntent(R.id.widget_time_total, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 521))
        } else {
            views.setViewVisibility(R.id.widget_progress_section, View.GONE)
        }

        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        applyIconTint(views, R.id.widget_btn_queue, iconTint)
        applyIconTint(views, R.id.widget_btn_playlist, iconTint)
        applyIconTint(views, R.id.widget_btn_prev, iconTint)
        applyIconTint(views, R.id.widget_btn_next, iconTint)
        applyIconTint(views, R.id.widget_btn_rewind, iconTint)
        applyIconTint(views, R.id.widget_btn_fast_forward, iconTint)

        views.setViewVisibility(R.id.widget_btn_prev, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_rewind, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_fast_forward, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_shuffle, if (showShuffleRepeat) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_repeat, if (showShuffleRepeat) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        applyIconTint(
            views,
            R.id.widget_btn_fav,
            when {
                isHeartPop -> Color.RED
                isFavorite -> accentColor
                else -> iconTint
            }
        )
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_shuffle, if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
        applyIconTint(views, R.id.widget_btn_shuffle, if (isShuffle) accentColor else iconTint)

        views.setImageViewResource(
            R.id.widget_btn_repeat,
            when (repeatMode) {
                RepeatMode.ONE -> R.drawable.ic_repeat_one
                RepeatMode.ALL -> R.drawable.ic_repeat_all
                else -> R.drawable.ic_repeat_off
            }
        )
        applyIconTint(views, R.id.widget_btn_repeat, if (repeatMode != RepeatMode.OFF) accentColor else iconTint)

        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 501))
        views.setOnClickPendingIntent(R.id.widget_center_section, getLaunchAppPendingIntent(context, 502, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_queue, getLaunchAppPendingIntent(context, 503, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "QUEUE"))
        views.setOnClickPendingIntent(R.id.widget_btn_playlist, getLaunchAppPendingIntent(context, 504, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "PLAYLISTS"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 505))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 506))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 507))
        views.setOnClickPendingIntent(R.id.widget_btn_rewind, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 508))
        views.setOnClickPendingIntent(R.id.widget_btn_fast_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 509))
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, getActionPendingIntent(context, NotificationActionReceiver.ACTION_SHUFFLE, 510))
        views.setOnClickPendingIntent(R.id.widget_btn_repeat, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REPEAT, 511))
        views.setOnClickPendingIntent(R.id.widget_btn_fav, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAVORITE, 512))

        return views
    }

    private fun generateWaveformBitmap(width: Int, height: Int, progressPercent: Int, accentColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paintPlayed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        val paintUnplayed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }

        val barCount = 40
        val spacing = width / barCount.toFloat()
        val heights = floatArrayOf(
            0.25f, 0.45f, 0.35f, 0.65f, 0.85f, 1.0f, 0.72f, 0.52f, 0.42f, 0.55f,
            0.90f, 0.62f, 0.35f, 0.45f, 0.72f, 0.92f, 0.80f, 0.52f, 0.34f, 0.25f,
            0.44f, 0.64f, 0.54f, 0.82f, 1.0f, 0.72f, 0.42f, 0.34f, 0.52f, 0.72f,
            0.90f, 0.62f, 0.42f, 0.34f, 0.52f, 0.82f, 0.62f, 0.42f, 0.26f, 0.18f
        )

        val progressIndex = (progressPercent / 100f * barCount).toInt()
        for (i in 0 until barCount) {
            val h = heights[i % heights.size] * (height - 8f)
            val x = i * spacing + spacing / 2f
            val y1 = (height - h) / 2f
            val y2 = y1 + h
            canvas.drawLine(x, y1, x, y2, if (i < progressIndex) paintPlayed else paintUnplayed)
        }
        return bitmap
    }

    fun blurBitmap(source: Bitmap, blurPercent: Float): Bitmap {
        if (blurPercent <= 0.02f) return source
        return try {
            val radius = (blurPercent * 20f).toInt().coerceIn(1, 25)
            val maxDim = 160
            val scale = minOf(1.0f, maxDim.toFloat() / maxOf(source.width, source.height).toFloat())
            val targetW = maxOf(16, (source.width * scale).toInt())
            val targetH = maxOf(16, (source.height * scale).toInt())
            val smallBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(source, targetW, targetH, true)
            } else {
                source.copy(Bitmap.Config.ARGB_8888, true)
            }
            val blurred = fastBoxBlur(smallBitmap, radius)
            if (smallBitmap != source && smallBitmap != blurred) {
                smallBitmap.recycle()
            }
            blurred
        } catch (_: Exception) {
            source
        }
    }

    private fun fastBoxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        val r = radius.coerceIn(1, 25)
        val div = 2 * r + 1

        for (y in 0 until h) {
            val rowOffset = y * w
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var aSum = 0

            for (i in -r..r) {
                val clampedX = i.coerceIn(0, w - 1)
                val c = pixels[rowOffset + clampedX]
                aSum += (c ushr 24)
                rSum += ((c ushr 16) and 0xFF)
                gSum += ((c ushr 8) and 0xFF)
                bSum += (c and 0xFF)
            }

            for (x in 0 until w) {
                out[rowOffset + x] = ((aSum / div) shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)

                val cOut = pixels[rowOffset + (x - r).coerceIn(0, w - 1)]
                val cIn = pixels[rowOffset + (x + r + 1).coerceIn(0, w - 1)]
                aSum += (cIn ushr 24) - (cOut ushr 24)
                rSum += ((cIn ushr 16) and 0xFF) - ((cOut ushr 16) and 0xFF)
                gSum += ((cIn ushr 8) and 0xFF) - ((cOut ushr 8) and 0xFF)
                bSum += (cIn and 0xFF) - (cOut and 0xFF)
            }
        }

        for (x in 0 until w) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            var aSum = 0

            for (i in -r..r) {
                val clampedY = i.coerceIn(0, h - 1)
                val c = out[clampedY * w + x]
                aSum += (c ushr 24)
                rSum += ((c ushr 16) and 0xFF)
                gSum += ((c ushr 8) and 0xFF)
                bSum += (c and 0xFF)
            }

            for (y in 0 until h) {
                val idx = y * w + x
                pixels[idx] = ((aSum / div) shl 24) or
                    ((rSum / div) shl 16) or
                    ((gSum / div) shl 8) or
                    (bSum / div)

                val cOut = out[(y - r).coerceIn(0, h - 1) * w + x]
                val cIn = out[(y + r + 1).coerceIn(0, h - 1) * w + x]
                aSum += (cIn ushr 24) - (cOut ushr 24)
                rSum += ((cIn ushr 16) and 0xFF) - ((cOut ushr 16) and 0xFF)
                gSum += ((cIn ushr 8) and 0xFF) - ((cOut ushr 8) and 0xFF)
                bSum += (cIn and 0xFF) - (cOut and 0xFF)
            }
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(pixels, 0, w, 0, 0, w, h)
        return res
    }

    private fun getGeneratedBgBitmap(
        width: Int,
        height: Int,
        artBitmap: Bitmap?,
        hasRealCoverArt: Boolean,
        bgCoverArtEnabled: Boolean,
        dynamicTheme: Boolean,
        theme: String,
        cornerRadiusFloat: Float,
        opacity: Float,
        accentColor: Int,
        blurPercent: Float = 0.55f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val maxRadius = minOf(width, height) / 2f
        val radius = cornerRadiusFloat.coerceIn(0f, 1f) * maxRadius
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val alpha = opacity.coerceIn(0.20f, 1f)

        val clipPath = Path().apply {
            addRoundRect(bounds, radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val isLight = isLightSurface(theme, bgCoverArtEnabled, hasRealCoverArt)

        if (bgCoverArtEnabled && hasRealCoverArt && artBitmap != null) {
            val bitmapToDraw = if (blurPercent > 0.02f) blurBitmap(artBitmap, blurPercent) else artBitmap
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcW = bitmapToDraw.width.toFloat().coerceAtLeast(1f)
            val srcH = bitmapToDraw.height.toFloat().coerceAtLeast(1f)
            val scale = maxOf(width / srcW, height / srcH)
            val scaledW = srcW * scale
            val scaledH = srcH * scale
            val dx = (width - scaledW) / 2f
            val dy = (height - scaledH) / 2f
            canvas.drawBitmap(bitmapToDraw, null, RectF(dx, dy, dx + scaledW, dy + scaledH), artPaint)
            if (bitmapToDraw != artBitmap) bitmapToDraw.recycle()

            val topScrimAlpha = ((0.35f + alpha * 0.45f).coerceIn(0.35f, 0.88f) * 255).toInt()
            val bottomScrimAlpha = ((0.55f + alpha * 0.38f).coerceIn(0.55f, 0.94f) * 255).toInt()
            paint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.argb(topScrimAlpha, 8, 12, 10),
                Color.argb(bottomScrimAlpha, 6, 8, 7),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(bounds, paint)
        } else if ((theme == "ALBUM_COLOR" || dynamicTheme) && artBitmap != null) {
            val palette = Palette.from(artBitmap).generate()
            val dominant = palette.getDominantColor(Color.parseColor("#16231D"))
            val hsv = FloatArray(3)
            Color.colorToHSV(dominant, hsv)
            hsv[2] = (hsv[2] * 0.36f).coerceIn(0.10f, 0.32f)
            val darkDom = Color.HSVToColor(hsv)
            val deeper = ColorUtils.blendARGB(darkDom, Color.BLACK, 0.45f)
            paint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                ColorUtils.setAlphaComponent(darkDom, (alpha * 255).toInt()),
                ColorUtils.setAlphaComponent(deeper, (alpha * 255).toInt()),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(bounds, paint)
        } else {
            when (theme) {
                "LIGHT_GLASS" -> {
                    paint.shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        Color.argb((alpha * 242).toInt(), 246, 248, 247),
                        Color.argb((alpha * 242).toInt(), 230, 236, 233),
                        Shader.TileMode.CLAMP
                    )
                }
                "GLASS", "FROSTED" -> {
                    paint.shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        Color.argb((alpha * 218).toInt(), 24, 30, 28),
                        Color.argb((alpha * 228).toInt(), 12, 16, 15),
                        Shader.TileMode.CLAMP
                    )
                }
                "ACCENT" -> {
                    paint.shader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        Color.argb((alpha * 110).toInt(), Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                        Color.argb((alpha * 248).toInt(), 8, 11, 10),
                        Shader.TileMode.CLAMP
                    )
                }
                else -> { // AMOLED (Pure Black)
                    paint.color = Color.argb((alpha * 255).toInt(), 7, 10, 9)
                }
            }
            canvas.drawRect(bounds, paint)
        }

        // Subtle crisp outline
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = if (isLight) Color.argb(30, 0, 0, 0) else Color.argb(26, 255, 255, 255)
        }
        canvas.drawRoundRect(bounds, radius, radius, borderPaint)

        canvas.restore()
        return bitmap
    }

    fun loadRawArtworkBitmap(
        context: Context,
        contentUriString: String?,
        artUriString: String?
    ): Bitmap? {
        if (artUriString != null) {
            try {
                val uri = Uri.parse(artUriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) return bmp
                }
            } catch (_: Exception) {
                // fallback to embedded art
            }
        }

        if (contentUriString != null) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(contentUriString))
                val picture = retriever.embeddedPicture
                if (picture != null) {
                    return BitmapFactory.decodeByteArray(picture, 0, picture.size)
                }
            } catch (_: Exception) {
                // ignore
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun loadArtworkBitmap(
        context: Context,
        contentUriString: String?,
        artUriString: String?,
        width: Int,
        height: Int,
        cornerRadiusDp: Float
    ): Bitmap {
        val rawBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
            ?: createFallbackArtworkBitmap(context, width, height)
        return getRoundedCornerBitmap(rawBitmap, width, height, cornerRadiusDp)
    }

    private fun loadCircularArtworkBitmap(
        context: Context,
        contentUriString: String?,
        artUriString: String?,
        size: Int
    ): Bitmap {
        return loadArtworkBitmap(context, contentUriString, artUriString, size, size, size / 2f)
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, width: Int, height: Int, radiusDp: Float): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val radiusPx = (radiusDp * 2.0f).coerceAtMost(minOf(width, height) / 2f)
        val rect = Rect(0, 0, width, height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        // Center-crop source into square/target rect
        val srcW = bitmap.width
        val srcH = bitmap.height
        val srcRect = if (srcW > srcH) {
            val xOff = (srcW - srcH) / 2
            Rect(xOff, 0, xOff + srcH, srcH)
        } else {
            val yOff = (srcH - srcW) / 2
            Rect(0, yOff, srcW, yOff + srcW)
        }
        canvas.drawBitmap(bitmap, srcRect, rect, paint)
        return output
    }

    private fun createFallbackArtworkBitmap(context: Context, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#202624"),
                Color.parseColor("#111514"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(18, 255, 255, 255)
            strokeWidth = 1.5f
        }
        val center = width / 2f
        canvas.drawCircle(center, center, width * 0.42f, groovePaint)
        canvas.drawCircle(center, center, width * 0.30f, groovePaint)

        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_soniclet_app_logo)
        if (drawable != null) {
            val iconSize = (minOf(width, height) * 0.52f).toInt()
            val offsetX = (width - iconSize) / 2
            val offsetY = (height - iconSize) / 2
            drawable.setBounds(offsetX, offsetY, offsetX + iconSize, offsetY + iconSize)
            DrawableCompat.setTint(drawable, Color.parseColor("#EDEDF2"))
            drawable.draw(canvas)
        }
        return bitmap
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms.coerceAtLeast(0L) / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    private fun createPlayButtonBitmap(
        context: Context,
        isPlaying: Boolean,
        accentColor: Int,
        cornerRadiusFloat: Float = 0.55f
    ): Bitmap {
        val sizePx = 104
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }

        val buttonRadius = 18f + (cornerRadiusFloat.coerceIn(0f, 1f) * 34f)
        val bounds = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.drawRoundRect(bounds, buttonRadius, buttonRadius, paint)

        val iconId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val drawable = ContextCompat.getDrawable(context, iconId)?.mutate()
        if (drawable != null) {
            val paddingPx = 26
            drawable.setBounds(paddingPx, paddingPx, sizePx - paddingPx, sizePx - paddingPx)
            val isBrightAccent = ColorUtils.calculateLuminance(accentColor) > 0.42
            DrawableCompat.setTint(drawable, if (isBrightAccent) Color.parseColor("#0A0F0D") else Color.WHITE)
            drawable.draw(canvas)
        }
        return bitmap
    }

    fun requestPinWidget(context: Context, widgetTypeIndex: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val providerComponent = ComponentName(context, SonicletWidgetProvider::class.java)
                val prefs = getPrefs(context)
                val title = prefs.getString(CACHE_SONG_TITLE, "Soniclet Music") ?: "Soniclet Music"
                val artist = prefs.getString(CACHE_SONG_ARTIST, "Tap to play") ?: "Tap to play"
                val album = prefs.getString(CACHE_SONG_ALBUM, "Soniclet Library") ?: "Soniclet Library"
                val contentUri = prefs.getString(CACHE_SONG_URI, null)
                val artUri = prefs.getString(CACHE_SONG_ART_URI, null)
                val path = prefs.getString(CACHE_SONG_PATH, "") ?: ""
                val duration = prefs.getLong(CACHE_SONG_DURATION, 215_000L)
                val position = prefs.getLong(CACHE_POSITION, 68_000L)
                val isPlaying = prefs.getBoolean(CACHE_IS_PLAYING, false)
                val isFav = prefs.getBoolean(CACHE_IS_FAV, false)

                val previewViews = when (widgetTypeIndex) {
                    0 -> buildTinyRemoteViews(context, title, contentUri, artUri, isPlaying)
                    1 -> buildSmallBarRemoteViews(context, title, artist, contentUri, artUri, isPlaying)
                    2 -> buildCompactRemoteViews(context, title, artist, contentUri, artUri, isPlaying, isFav, duration, position)
                    3 -> buildSquareRemoteViews(context, title, artist, contentUri, artUri, isPlaying)
                    4 -> buildVinylRemoteViews(context, title, artist, contentUri, artUri, isPlaying, isFav, false)
                    5 -> buildCardRemoteViews(context, title, artist, album, contentUri, artUri, duration, position, isPlaying, isFav, RepeatMode.OFF, false)
                    else -> buildProRemoteViews(context, title, artist, album, contentUri, artUri, path, duration, position, isPlaying, isFav, RepeatMode.OFF, false)
                }
                val extras = Bundle().apply {
                    putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, previewViews)
                }
                return appWidgetManager.requestPinAppWidget(providerComponent, extras, null)
            }
        }
        return false
    }
}
