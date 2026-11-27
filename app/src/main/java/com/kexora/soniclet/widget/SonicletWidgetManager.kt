package com.kexora.soniclet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.kexora.soniclet.MainActivity
import com.kexora.soniclet.R
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.service.NotificationActionReceiver
import com.kexora.soniclet.player.RepeatMode

object SonicletWidgetManager {

    private const val PREFS_NAME = "soniclet_widget_prefs"

    // Customization Keys
    const val KEY_THEME = "widget_theme" // AMOLED, GLASS, FROSTED, ACCENT, GRADIENT, TRANSPARENT
    const val KEY_CORNER_RADIUS = "widget_corner_radius" // PILL, ROUNDED, SQUARE
    const val KEY_CORNER_RADIUS_FLOAT = "widget_corner_radius_float" // 0.0f (square) to 1.0f (pill)
    const val KEY_OPACITY = "widget_opacity" // 1.0f, 0.85f, 0.65f, 0.40f, 0.15f
    const val KEY_BLUR = "widget_blur" // 0.0f (sharp) to 1.0f (deep blur)
    const val KEY_ACCENT_COLOR = "widget_accent_color" // Long hex (e.g., 0xFF00E676)
    const val KEY_BG_COVER_ART = "widget_bg_cover_art" // Full cover art background
    const val KEY_HIDE_ART_THUMBNAIL = "widget_hide_art_thumbnail" // Hide small art box when bg is art
    const val KEY_SHOW_FAVORITE = "widget_show_favorite"
    const val KEY_SHOW_SECONDARY = "widget_show_secondary"
    const val KEY_SHOW_PROGRESS = "widget_show_progress"
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
    private const val CACHE_SONG_DURATION = "cache_song_duration"
    private const val CACHE_IS_PLAYING = "cache_is_playing"
    private const val CACHE_IS_FAV = "cache_is_fav"
    private const val CACHE_REPEAT_MODE = "cache_repeat_mode"
    private const val CACHE_IS_SHUFFLE = "cache_is_shuffle"
    private const val CACHE_POSITION = "cache_position"

    
    fun triggerHeartPop(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean("CACHE_HEART_POP", true).apply()
        updateAllWidgetsFromCache(context)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            kotlinx.coroutines.delay(200)
            prefs.edit().putBoolean("CACHE_HEART_POP", false).apply()
            updateAllWidgetsFromCache(context)
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Save Customization
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
        showSeekButtons: Boolean? = null,
        showPrevNext: Boolean? = null,
        showShuffleRepeat: Boolean? = null,
        dynamicTheme: Boolean? = null,
        liveSync: Boolean? = null
    ) {
        val editor = getPrefs(context).edit()
        theme?.let { editor.putString(KEY_THEME, it) }
        cornerRadius?.let { editor.putString(KEY_CORNER_RADIUS, it) }
        cornerRadiusFloat?.let { editor.putFloat(KEY_CORNER_RADIUS_FLOAT, it) }
        opacity?.let { editor.putFloat(KEY_OPACITY, it) }
        blur?.let { editor.putFloat(KEY_BLUR, it) }
        accentColor?.let { editor.putLong(KEY_ACCENT_COLOR, it) }
        bgCoverArt?.let { editor.putBoolean(KEY_BG_COVER_ART, it) }
        hideArtThumbnail?.let { editor.putBoolean(KEY_HIDE_ART_THUMBNAIL, it) }
        showFavorite?.let { editor.putBoolean(KEY_SHOW_FAVORITE, it) }
        showSecondary?.let { editor.putBoolean(KEY_SHOW_SECONDARY, it) }
        showProgress?.let { editor.putBoolean(KEY_SHOW_PROGRESS, it) }
        showSeekButtons?.let { editor.putBoolean(KEY_SHOW_SEEK_BUTTONS, it) }
        showPrevNext?.let { editor.putBoolean(KEY_SHOW_PREV_NEXT, it) }
        showShuffleRepeat?.let { editor.putBoolean(KEY_SHOW_SHUFFLE_REPEAT, it) }
        dynamicTheme?.let { editor.putBoolean(KEY_DYNAMIC_THEME, it) }
        liveSync?.let { editor.putBoolean(KEY_LIVE_SYNC, it) }
        editor.apply()

        // Trigger live refresh of all widgets
        updateAllWidgetsFromCache(context)
    }

    // Cache Playback State & Update
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
            editor.putString(CACHE_SONG_TITLE, song.title)
            editor.putString(CACHE_SONG_ARTIST, song.artist)
            editor.putString(CACHE_SONG_ALBUM, song.album)
            editor.putString(CACHE_SONG_URI, song.contentUri.toString())
            editor.putString(CACHE_SONG_ART_URI, song.albumArtUri?.toString())
            editor.putLong(CACHE_SONG_DURATION, song.duration)
        }
        editor.putBoolean(CACHE_IS_PLAYING, isPlaying)
        editor.putBoolean(CACHE_IS_FAV, isFavorite)
        editor.putString(CACHE_REPEAT_MODE, repeatMode.name)
        editor.putBoolean(CACHE_IS_SHUFFLE, isShuffle)
        editor.putLong(CACHE_POSITION, currentPositionMs)
        editor.apply()

        renderAllWidgetProviders(
            context = context,
            title = song?.title ?: prefs.getString(CACHE_SONG_TITLE, "Soniclet Music") ?: "Soniclet Music",
            artist = song?.artist ?: prefs.getString(CACHE_SONG_ARTIST, "Soniclet") ?: "Soniclet",
            album = song?.album ?: prefs.getString(CACHE_SONG_ALBUM, "HD Audio") ?: "HD Audio",
            contentUriString = song?.contentUri?.toString() ?: prefs.getString(CACHE_SONG_URI, null),
            artUriString = song?.albumArtUri?.toString() ?: prefs.getString(CACHE_SONG_ART_URI, null),
            durationMs = song?.duration ?: prefs.getLong(CACHE_SONG_DURATION, 0L),
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
        val artist = prefs.getString(CACHE_SONG_ARTIST, "Soniclet") ?: "Soniclet"
        val album = prefs.getString(CACHE_SONG_ALBUM, "HD Audio") ?: "HD Audio"
        val contentUriString = prefs.getString(CACHE_SONG_URI, null)
        val artUriString = prefs.getString(CACHE_SONG_ART_URI, null)
        val durationMs = prefs.getLong(CACHE_SONG_DURATION, 0L)
        val positionMs = prefs.getLong(CACHE_POSITION, 0L)
        val isPlaying = prefs.getBoolean(CACHE_IS_PLAYING, false)
        val isFavorite = prefs.getBoolean(CACHE_IS_FAV, false)
        val repeatModeStr = prefs.getString(CACHE_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name
        val repeatMode = try { RepeatMode.valueOf(repeatModeStr) } catch (e: Exception) { RepeatMode.OFF }
        val isShuffle = prefs.getBoolean(CACHE_IS_SHUFFLE, false)
        val isHeartPop = prefs.getBoolean("CACHE_HEART_POP", false)

        renderAllWidgetProviders(
            context = context,
            title = title,
            artist = artist,
            album = album,
            contentUriString = contentUriString,
            artUriString = artUriString,
            durationMs = durationMs,
            positionMs = positionMs,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            isHeartPop = false
        )
    }

    private fun renderAllWidgetProviders(
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
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val adaptiveIds = appWidgetManager.getAppWidgetIds(android.content.ComponentName(context, SonicletWidgetProvider::class.java))
        
        for (id in adaptiveIds) {
            updateWidgetByIdInternal(
                context, appWidgetManager, id,
                title, artist, album, contentUriString, artUriString,
                durationMs, positionMs, isPlaying, isFavorite, repeatMode.name, isShuffle, isHeartPop
            )
        }
    }

    fun updateWidgetById(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val prefs = getPrefs(context)
        val title = prefs.getString(CACHE_SONG_TITLE, "Soniclet Music") ?: "Soniclet Music"
        val artist = prefs.getString(CACHE_SONG_ARTIST, "Soniclet") ?: "Soniclet"
        val album = prefs.getString(CACHE_SONG_ALBUM, "HD Audio") ?: "HD Audio"
        val contentUriString = prefs.getString(CACHE_SONG_URI, null)
        val artUriString = prefs.getString(CACHE_SONG_ART_URI, null)
        val durationMs = prefs.getLong(CACHE_SONG_DURATION, 0L)
        val positionMs = prefs.getLong(CACHE_POSITION, 0L)
        val isPlaying = prefs.getBoolean(CACHE_IS_PLAYING, false)
        val isFavorite = prefs.getBoolean(CACHE_IS_FAV, false)
        val repeatModeStr = prefs.getString(CACHE_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name
        val isShuffle = prefs.getBoolean(CACHE_IS_SHUFFLE, false)
        val isHeartPop = prefs.getBoolean("CACHE_HEART_POP", false)
        
        updateWidgetByIdInternal(
            context, appWidgetManager, appWidgetId,
            title, artist, album, contentUriString, artUriString,
            durationMs, positionMs, isPlaying, isFavorite, repeatModeStr, isShuffle, isHeartPop
        )
    }

    private fun updateWidgetByIdInternal(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int,
        title: String, artist: String, album: String, contentUriString: String?, artUriString: String?,
        durationMs: Long, positionMs: Long, isPlaying: Boolean, isFavorite: Boolean, repeatModeStr: String, isShuffle: Boolean, isHeartPop: Boolean
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        
        val width = if (minWidth == 0) 250 else minWidth
        val height = if (maxHeight == 0) 110 else maxHeight
        
        val repeatMode = try { RepeatMode.valueOf(repeatModeStr) } catch(e: Exception) { RepeatMode.OFF }
        
        val views = when {
            width < 100 && height < 100 -> buildTinyRemoteViews(context, title, contentUriString, artUriString, isPlaying) // 1x1 Tiny
            width < 180 && height < 100 -> buildSmallBarRemoteViews(context, title, contentUriString, artUriString, isPlaying) // 2x1 Mini Bar
            width >= 180 && height < 100 -> buildCompactRemoteViews(context, title, artist, contentUriString, artUriString, isPlaying, isFavorite, durationMs, positionMs, isHeartPop) // 3x1, 4x1 Compact
            width < 180 && height >= 100 && height < 180 -> buildSquareRemoteViews(context, title, artist, contentUriString, artUriString, isPlaying) // 2x2 Square
            width >= 180 && height >= 100 && height < 180 -> buildCardRemoteViews(context, title, artist, album, contentUriString, artUriString, durationMs, positionMs, isPlaying, isFavorite, repeatMode, isShuffle, isHeartPop) // 3x2, 4x2 Card
            height >= 180 && width < 230 -> buildVinylRemoteViews(context, title, artist, contentUriString, artUriString, isPlaying, isFavorite, isShuffle, isHeartPop) // 3x3 Vinyl
            else -> buildProRemoteViews(context, title, artist, album, contentUriString, artUriString, durationMs, positionMs, isPlaying, isFavorite, repeatMode, isShuffle, isHeartPop) // 4x3, 4x4 Pro
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // PendingIntent Generators
    fun getLaunchAppPendingIntent(context: Context, reqCode: Int, actionStr: String? = null, targetTab: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (actionStr != null) {
                action = actionStr
            }
            if (targetTab != null) {
                putExtra("target_tab", targetTab)
            }
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

    // Builder 1: Compact Widget RemoteViews

    fun buildTinyRemoteViews(
        context: Context, title: String, contentUriString: String?, artUriString: String?, isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tiny_layout)
        val prefs = getPrefs(context)
        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 1.0f)
        val accentColor = try { prefs.getLong(KEY_ACCENT_COLOR, 0xFFFFFFFF).toInt() } catch (e: Exception) { try { prefs.getInt(KEY_ACCENT_COLOR, 0xFFFFFFFF.toInt()) } catch(e2: Exception) { 0xFFFFFFFF.toInt() } }
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val hideArtThumbnail = prefs.getBoolean(KEY_HIDE_ART_THUMBNAIL, true)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 200, 200, 0f)

        if (hasRealCoverArt && bgCoverArt && hideArtThumbnail) {
            views.setViewVisibility(R.id.widget_art, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
        }
        
        val dynamicTheme = prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        val blur = prefs.getFloat(KEY_BLUR, 0.5f)
        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(200, 200, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur)
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)
        
        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))
        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 101, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 104))
        return views
    }

    fun buildSmallBarRemoteViews(
        context: Context, title: String, contentUriString: String?, artUriString: String?, isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_small_bar_layout)
        val prefs = getPrefs(context)
        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 1.0f)
        val accentColor = try { prefs.getLong(KEY_ACCENT_COLOR, 0xFFFFFFFF).toInt() } catch (e: Exception) { try { prefs.getInt(KEY_ACCENT_COLOR, 0xFFFFFFFF.toInt()) } catch(e2: Exception) { 0xFFFFFFFF.toInt() } }
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val hideArtThumbnail = prefs.getBoolean(KEY_HIDE_ART_THUMBNAIL, true)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 120, 120, 16f)

        if (hasRealCoverArt && bgCoverArt && hideArtThumbnail) {
            views.setViewVisibility(R.id.widget_art, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
        }
        
        val dynamicTheme = prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        val blur = prefs.getFloat(KEY_BLUR, 0.5f)
        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(500, 200, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur)
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)
        
        // Adaptive padding for corner radius
        val density = context.resources.displayMetrics.density
        val padH = (cornerRadiusFloat * 10 * density).toInt() + (8 * density).toInt()
        val padV = (6 * density).toInt()
        views.setViewPadding(R.id.widget_inner, padH, padV, padH, padV)

        views.setTextViewText(R.id.widget_title, title)
        
        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))
        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 111, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 114))
        return views
    }

    fun buildSquareRemoteViews(
        context: Context, title: String, artist: String, contentUriString: String?, artUriString: String?, isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_square_layout)
        val prefs = getPrefs(context)
        val theme = prefs.getString(KEY_THEME, "AMOLED") ?: "AMOLED"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 1.0f)
        val accentColor = try { prefs.getLong(KEY_ACCENT_COLOR, 0xFFFFFFFF).toInt() } catch (e: Exception) { try { prefs.getInt(KEY_ACCENT_COLOR, 0xFFFFFFFF.toInt()) } catch(e2: Exception) { 0xFFFFFFFF.toInt() } }
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val hideArtThumbnail = prefs.getBoolean(KEY_HIDE_ART_THUMBNAIL, true)

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 300, 300, 0f)

        if (hasRealCoverArt && bgCoverArt && hideArtThumbnail) {
            views.setViewVisibility(R.id.widget_art, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
        }
        
        val dynamicTheme = prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        val blur = prefs.getFloat(KEY_BLUR, 0.5f)
        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(400, 400, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur)
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)
        
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        
        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))
        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 121, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 124))
        return views
    }

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
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 1.0f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showPrevNext = prefs.getBoolean(KEY_SHOW_PREV_NEXT, true)
        val showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val hideArtThumbnail = prefs.getBoolean(KEY_HIDE_ART_THUMBNAIL, true)
        val accentColor = try { prefs.getLong(KEY_ACCENT_COLOR, 0xFFFFFFFF).toInt() } catch (e: Exception) { try { prefs.getInt(KEY_ACCENT_COLOR, 0xFFFFFFFF.toInt()) } catch(e2: Exception) { 0xFFFFFFFF.toInt() } }

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 120, 120, 16f)

        // Cover art in background; remove separate photo box when enabled
        if (hasRealCoverArt && bgCoverArt && hideArtThumbnail) {
            views.setViewVisibility(R.id.widget_art, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
        }

        val dynamicTheme = prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        val blur = prefs.getFloat(KEY_BLUR, 0.5f)
        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(800, 200, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur)
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        // Adaptive padding
        val density = context.resources.displayMetrics.density
        val padH = (cornerRadiusFloat * 12 * density).toInt() + (8 * density).toInt()
        val padV = (6 * density).toInt()
        views.setViewPadding(R.id.widget_inner, padH, padV, padH, padV)

        // Text & Icons
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)

        // Controls
        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        views.setViewVisibility(R.id.widget_btn_prev, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (showPrevNext) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        if (isHeartPop) {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", Color.RED)
        } else {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", 0)
        }
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)

        // Progress Bar & Touch Seek Zones
        if (showProgress && durationMs > 0) {
            views.setViewVisibility(R.id.widget_progress_container, View.VISIBLE)
            val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_progress, 100, progressPercent, false)
            views.setOnClickPendingIntent(R.id.widget_bar_seek_backward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 218))
            views.setOnClickPendingIntent(R.id.widget_bar_seek_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 219))
        } else {
            views.setViewVisibility(R.id.widget_progress_container, View.GONE)
        }

        // Click Actions
        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 201))
        views.setOnClickPendingIntent(R.id.widget_art, getLaunchAppPendingIntent(context, 202, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_info_container, getLaunchAppPendingIntent(context, 203, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 204))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 205))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 206))
        views.setOnClickPendingIntent(R.id.widget_btn_fav, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAVORITE, 207))

        return views
    }

    // Builder 2: Card Widget RemoteViews
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
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 1.0f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showSecondary = prefs.getBoolean(KEY_SHOW_SECONDARY, true)
        val showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true)
        val showSeekButtons = prefs.getBoolean(KEY_SHOW_SEEK_BUTTONS, true)
        val showPrevNext = prefs.getBoolean(KEY_SHOW_PREV_NEXT, true)
        val showShuffleRepeat = prefs.getBoolean(KEY_SHOW_SHUFFLE_REPEAT, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val hideArtThumbnail = prefs.getBoolean(KEY_HIDE_ART_THUMBNAIL, true)
        val accentColor = try { prefs.getLong(KEY_ACCENT_COLOR, 0xFFFFFFFF).toInt() } catch (e: Exception) { try { prefs.getInt(KEY_ACCENT_COLOR, 0xFFFFFFFF.toInt()) } catch(e2: Exception) { 0xFFFFFFFF.toInt() } }

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 160, 160, 20f)

        if (hasRealCoverArt && bgCoverArt && hideArtThumbnail) {
            views.setViewVisibility(R.id.widget_art, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
        }

        val dynamicTheme = prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        val blur = prefs.getFloat(KEY_BLUR, 0.5f)
        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(800, 300, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur)
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        // Adaptive padding
        val density = context.resources.displayMetrics.density
        val padH = (cornerRadiusFloat * 12 * density).toInt() + (8 * density).toInt()
        val padV = (8 * density).toInt()
        views.setViewPadding(R.id.widget_inner, padH, padV, padH, padV)

        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextViewText(R.id.widget_subtitle, album)
        views.setViewVisibility(R.id.widget_subtitle, if (showSecondary) View.VISIBLE else View.GONE)

        // Progress & Interactive Bar Seeking
        if (showProgress && durationMs > 0) {
            views.setViewVisibility(R.id.widget_progress_container, View.VISIBLE)
            val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_progress, 100, progressPercent, false)
            views.setOnClickPendingIntent(R.id.widget_bar_seek_backward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 318))
            views.setOnClickPendingIntent(R.id.widget_bar_seek_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 319))
        } else {
            views.setViewVisibility(R.id.widget_progress_container, View.GONE)
        }

        // Controls
        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        views.setViewVisibility(R.id.widget_btn_prev, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_rewind, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_fast_forward, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_shuffle, if (showShuffleRepeat) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_repeat, if (showShuffleRepeat) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        if (isHeartPop) {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", Color.RED)
        } else {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", 0)
        }
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_shuffle, if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
        views.setImageViewResource(
            R.id.widget_btn_repeat,
            when (repeatMode) {
                RepeatMode.ONE -> R.drawable.ic_repeat_one
                RepeatMode.ALL -> R.drawable.ic_repeat_all
                else -> R.drawable.ic_repeat_off
            }
        )

        // Actions
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

    // Builder 3: Vinyl Retro Widget RemoteViews
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
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 1.0f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showShuffleRepeat = prefs.getBoolean(KEY_SHOW_SHUFFLE_REPEAT, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val accentColor = try { prefs.getLong(KEY_ACCENT_COLOR, 0xFFFFFFFF).toInt() } catch (e: Exception) { try { prefs.getInt(KEY_ACCENT_COLOR, 0xFFFFFFFF.toInt()) } catch(e2: Exception) { 0xFFFFFFFF.toInt() } }

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val circleArt = loadCircularArtworkBitmap(context, contentUriString, artUriString, 160)
        views.setImageViewBitmap(R.id.widget_art, circleArt)

        val dynamicTheme = prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        val blur = prefs.getFloat(KEY_BLUR, 0.5f)
        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(800, 300, rawArtBitmap ?: circleArt, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur)
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)

        // Controls
        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        if (isHeartPop) {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", Color.RED)
        } else {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", 0)
        }
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)
        views.setImageViewResource(R.id.widget_btn_shuffle, if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
        views.setViewVisibility(R.id.widget_btn_shuffle, if (showShuffleRepeat) View.VISIBLE else View.GONE)

        // Actions
        views.setOnClickPendingIntent(R.id.widget_root, getLaunchAppPendingIntent(context, 401))
        views.setOnClickPendingIntent(R.id.widget_vinyl_outer, getLaunchAppPendingIntent(context, 402, actionStr = "com.kexora.soniclet.ACTION_OPEN_TAB", targetTab = "NOW_PLAYING"))
        views.setOnClickPendingIntent(R.id.widget_btn_play, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PLAY_PAUSE, 403))
        views.setOnClickPendingIntent(R.id.widget_btn_prev, getActionPendingIntent(context, NotificationActionReceiver.ACTION_PREVIOUS, 404))
        views.setOnClickPendingIntent(R.id.widget_btn_next, getActionPendingIntent(context, NotificationActionReceiver.ACTION_NEXT, 405))
        views.setOnClickPendingIntent(R.id.widget_btn_shuffle, getActionPendingIntent(context, NotificationActionReceiver.ACTION_SHUFFLE, 406))
        views.setOnClickPendingIntent(R.id.widget_btn_fav, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAVORITE, 407))

        return views
    }

    // Builder 4: Pro Deck RemoteViews
    fun buildProRemoteViews(
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
        val views = RemoteViews(context.packageName, R.layout.widget_pro_layout)
        val prefs = getPrefs(context)

        val theme = prefs.getString(KEY_THEME, "GLASS") ?: "GLASS"
        val cornerRadiusFloat = prefs.getFloat(KEY_CORNER_RADIUS_FLOAT, 1.0f)
        val showFav = prefs.getBoolean(KEY_SHOW_FAVORITE, true)
        val showSecondary = prefs.getBoolean(KEY_SHOW_SECONDARY, true)
        val showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true)
        val showSeekButtons = prefs.getBoolean(KEY_SHOW_SEEK_BUTTONS, true)
        val showPrevNext = prefs.getBoolean(KEY_SHOW_PREV_NEXT, true)
        val showShuffleRepeat = prefs.getBoolean(KEY_SHOW_SHUFFLE_REPEAT, true)
        val bgCoverArt = prefs.getBoolean(KEY_BG_COVER_ART, true)
        val hideArtThumbnail = prefs.getBoolean(KEY_HIDE_ART_THUMBNAIL, true)
        val accentColor = try { prefs.getLong(KEY_ACCENT_COLOR, 0xFFFFFFFF).toInt() } catch (e: Exception) { try { prefs.getInt(KEY_ACCENT_COLOR, 0xFFFFFFFF.toInt()) } catch(e2: Exception) { 0xFFFFFFFF.toInt() } }

        val rawArtBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)
        val hasRealCoverArt = rawArtBitmap != null
        val artBitmap = loadArtworkBitmap(context, contentUriString, artUriString, 200, 200, 24f)

        if (hasRealCoverArt && bgCoverArt && hideArtThumbnail) {
            views.setViewVisibility(R.id.widget_art, View.GONE)
        } else {
            views.setImageViewBitmap(R.id.widget_art, artBitmap)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)
        }

        val dynamicTheme = prefs.getBoolean(KEY_DYNAMIC_THEME, false)
        val opacity = prefs.getFloat(KEY_OPACITY, 0.95f)
        val blur = prefs.getFloat(KEY_BLUR, 0.5f)
        views.setInt(R.id.widget_inner, "setBackgroundResource", 0)
        val dynBg = getGeneratedBgBitmap(800, 500, rawArtBitmap ?: artBitmap, hasRealCoverArt, bgCoverArt, dynamicTheme, theme, cornerRadiusFloat, opacity, accentColor, blur)
        views.setImageViewBitmap(R.id.widget_dynamic_bg, dynBg)
        views.setViewVisibility(R.id.widget_dynamic_bg, View.VISIBLE)

        // Adaptive padding
        val density = context.resources.displayMetrics.density
        val padH = (cornerRadiusFloat * 12 * density).toInt() + (8 * density).toInt()
        val padV = (8 * density).toInt()
        views.setViewPadding(R.id.widget_inner, padH, padV, padH, padV)

        views.setTextColor(R.id.widget_brand_label, accentColor)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setTextViewText(R.id.widget_subtitle, album)
        views.setViewVisibility(R.id.widget_subtitle, if (showSecondary) View.VISIBLE else View.GONE)

        // Progress, Time & Interactive Seeking
        if (showProgress && durationMs > 0) {
            views.setViewVisibility(R.id.widget_progress_section, View.VISIBLE)
            val progressPercent = ((positionMs.toFloat() / durationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            val waveformBitmap = generateWaveformBitmap(400, 60, progressPercent, accentColor)
            views.setImageViewBitmap(R.id.widget_waveform, waveformBitmap)
            views.setTextViewText(R.id.widget_time_elapsed, formatTime(positionMs))
            views.setTextViewText(R.id.widget_time_total, formatTime(durationMs))

            views.setOnClickPendingIntent(R.id.widget_bar_seek_backward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 518))
            views.setOnClickPendingIntent(R.id.widget_bar_seek_forward, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 519))
            views.setOnClickPendingIntent(R.id.widget_time_elapsed, getActionPendingIntent(context, NotificationActionReceiver.ACTION_REWIND, 520))
            views.setOnClickPendingIntent(R.id.widget_time_total, getActionPendingIntent(context, NotificationActionReceiver.ACTION_FAST_FORWARD, 521))
        } else {
            views.setViewVisibility(R.id.widget_progress_section, View.GONE)
        }

        // Controls
        views.setImageViewResource(R.id.widget_btn_play, 0)
        views.setImageViewBitmap(R.id.widget_btn_play, createPlayButtonBitmap(context, isPlaying, accentColor, cornerRadiusFloat))

        views.setViewVisibility(R.id.widget_btn_prev, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_next, if (showPrevNext) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_rewind, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_fast_forward, if (showSeekButtons) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_shuffle, if (showShuffleRepeat) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_btn_repeat, if (showShuffleRepeat) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_btn_fav, if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        if (isHeartPop) {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", Color.RED)
        } else {
            views.setInt(R.id.widget_btn_fav, "setColorFilter", 0)
        }
        views.setViewVisibility(R.id.widget_btn_fav, if (showFav) View.VISIBLE else View.GONE)
        views.setImageViewResource(R.id.widget_btn_shuffle, if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
        views.setImageViewResource(
            R.id.widget_btn_repeat,
            when (repeatMode) {
                RepeatMode.ONE -> R.drawable.ic_repeat_one
                RepeatMode.ALL -> R.drawable.ic_repeat_all
                else -> R.drawable.ic_repeat_off
            }
        )

        // Actions
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
            color = Color.argb(80, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        
        val barCount = 40
        val spacing = width / barCount.toFloat()
        
        // Pseudo-random waveform based on index
        val heights = floatArrayOf(
            0.2f, 0.4f, 0.3f, 0.6f, 0.8f, 1.0f, 0.7f, 0.5f, 0.4f, 0.5f,
            0.9f, 0.6f, 0.3f, 0.4f, 0.7f, 0.9f, 0.8f, 0.5f, 0.3f, 0.2f,
            0.4f, 0.6f, 0.5f, 0.8f, 1.0f, 0.7f, 0.4f, 0.3f, 0.5f, 0.7f,
            0.9f, 0.6f, 0.4f, 0.3f, 0.5f, 0.8f, 0.6f, 0.4f, 0.2f, 0.1f
        )
        
        val progressIndex = (progressPercent / 100f * barCount).toInt()
        
        for (i in 0 until barCount) {
            val h = heights[i % heights.size] * height
            val x = i * spacing + spacing / 2f
            val y1 = (height - h) / 2f
            val y2 = y1 + h
            
            val paint = if (i < progressIndex) paintPlayed else paintUnplayed
            canvas.drawLine(x, y1, x, y2, paint)
        }
        
        return bitmap
    }

    
    fun blurBitmap(source: Bitmap, blurPercent: Float): Bitmap {
        if (blurPercent <= 0.02f) return source
        return try {
            val radius = (blurPercent * 20f).toInt().coerceIn(1, 25)
            // Downsample to max 160x160 for optimal blur speed and smoothness
            val maxDim = 160
            val scale = Math.min(1.0f, maxDim.toFloat() / Math.max(source.width, source.height).toFloat())
            val targetW = Math.max(16, (source.width * scale).toInt())
            val targetH = Math.max(16, (source.height * scale).toInt())
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
        } catch (e: Exception) {
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

        // Horizontal pass
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

                val xOut = (x - r).coerceIn(0, w - 1)
                val xIn = (x + r + 1).coerceIn(0, w - 1)
                val cOut = pixels[rowOffset + xOut]
                val cIn = pixels[rowOffset + xIn]

                aSum += (cIn ushr 24) - (cOut ushr 24)
                rSum += ((cIn ushr 16) and 0xFF) - ((cOut ushr 16) and 0xFF)
                gSum += ((cIn ushr 8) and 0xFF) - ((cOut ushr 8) and 0xFF)
                bSum += (cIn and 0xFF) - (cOut and 0xFF)
            }
        }

        // Vertical pass
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

                val yOut = (y - r).coerceIn(0, h - 1)
                val yIn = (y + r + 1).coerceIn(0, h - 1)
                val cOut = out[yOut * w + x]
                val cIn = out[yIn * w + x]

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
        blurPercent: Float = 0.5f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val maxRadius = Math.min(width, height) / 2f
        val radius = cornerRadiusFloat * maxRadius
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())

        val path = android.graphics.Path().apply {
            addRoundRect(bounds, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)

        // Solid dark base to prevent launcher wallpaper & background icons from bleeding through
        val solidBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((opacity.coerceIn(0.85f, 1.0f) * 255).toInt(), 16, 16, 20)
        }
        canvas.drawRect(bounds, solidBasePaint)

        if (bgCoverArtEnabled && hasRealCoverArt && artBitmap != null && width > 0 && height > 0) {
            val bitmapToDraw = if (blurPercent > 0.02f) {
                blurBitmap(artBitmap, blurPercent)
            } else {
                artBitmap
            }
            // Draw album art as background
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcW = bitmapToDraw.width.toFloat()
            val srcH = bitmapToDraw.height.toFloat()
            val scale = Math.max(width / srcW, height / srcH)
            val scaledW = srcW * scale
            val scaledH = srcH * scale
            val dx = (width - scaledW) / 2f
            val dy = (height - scaledH) / 2f
            val destRect = RectF(dx, dy, dx + scaledW, dy + scaledH)
            canvas.drawBitmap(bitmapToDraw, null, destRect, artPaint)
            if (bitmapToDraw != artBitmap) {
                bitmapToDraw.recycle()
            }

            // Darkening contrast scrim overlay
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.argb(((0.45f + (1f - opacity) * 0.4f).coerceIn(0.25f, 0.95f) * 255).toInt(), 12, 12, 16),
                    Color.argb(((0.75f + (1f - opacity) * 0.2f).coerceIn(0.45f, 0.98f) * 255).toInt(), 6, 6, 10),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(bounds, scrimPaint)

            // Subtle accent tint glow
            val accentScrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(16, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            }
            canvas.drawRect(bounds, accentScrim)
        } else {
            // Normal theme fallback ("agar coverart nahi hoga to normal react rahega")
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (dynamicTheme && artBitmap != null && width > 0 && height > 0) {
                val palette = androidx.palette.graphics.Palette.from(artBitmap).generate()
                var domColor = palette.getDominantColor(Color.DKGRAY)
                val hsv = FloatArray(3)
                Color.colorToHSV(domColor, hsv)
                hsv[2] *= 0.4f // darken value
                domColor = Color.HSVToColor(hsv)
                paint.color = Color.argb(
                    (opacity * 255).toInt(),
                    Color.red(domColor),
                    Color.green(domColor),
                    Color.blue(domColor)
                )
                canvas.drawRect(bounds, paint)
            } else {
                val alpha = opacity.coerceIn(0f, 1f)
                when (theme) {
                    "GLASS" -> {
                        paint.shader = android.graphics.LinearGradient(
                            0f, 0f, 0f, height.toFloat(),
                            Color.argb((alpha * 204).toInt(), 26, 27, 36),
                            Color.argb((alpha * 204).toInt(), 14, 15, 20),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                    "FROSTED" -> {
                        paint.shader = android.graphics.LinearGradient(
                            0f, 0f, 0f, height.toFloat(),
                            Color.argb((alpha * 238).toInt(), 34, 36, 48),
                            Color.argb((alpha * 238).toInt(), 18, 19, 26),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                    "ACCENT" -> {
                        paint.shader = android.graphics.LinearGradient(
                            0f, 0f, 0f, height.toFloat(),
                            Color.argb((alpha * 89).toInt(), Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                            Color.argb((alpha * 255).toInt(), 8, 8, 12),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                    "GRADIENT" -> {
                        paint.shader = android.graphics.LinearGradient(
                            0f, 0f, width.toFloat(), height.toFloat(),
                            Color.argb((alpha * 255).toInt(), 40, 20, 56),
                            Color.argb((alpha * 255).toInt(), 10, 16, 36),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                    "TRANSPARENT" -> {
                        paint.color = Color.TRANSPARENT
                    }
                    else -> { // AMOLED
                        paint.shader = android.graphics.LinearGradient(
                            0f, 0f, 0f, height.toFloat(),
                            Color.argb((alpha * 255).toInt(), 8, 8, 12),
                            Color.argb((alpha * 255).toInt(), 8, 8, 12),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    }
                }
                canvas.drawRect(bounds, paint)
            }
        }

        // Clean subtle border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(28, 255, 255, 255)
        }
        canvas.drawRect(bounds, borderPaint)

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
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bmp = inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                    if (bmp != null) return bmp
                }
            } catch (e: Exception) {
                // fallback
            }
        }

        if (contentUriString != null) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, Uri.parse(contentUriString))
                val picture = retriever.embeddedPicture
                retriever.release()
                if (picture != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
                    if (bmp != null) return bmp
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        return null
    }

    // Artwork Bitmap loader with rounded corners
    private fun loadArtworkBitmap(
        context: Context,
        contentUriString: String?,
        artUriString: String?,
        width: Int,
        height: Int,
        cornerRadiusDp: Float
    ): Bitmap {
        var rawBitmap = loadRawArtworkBitmap(context, contentUriString, artUriString)

        if (rawBitmap == null) {
            rawBitmap = createFallbackArtworkBitmap(context, width, height)
        }

        return getRoundedCornerBitmap(rawBitmap, width, height, cornerRadiusDp)
    }

    private fun loadCircularArtworkBitmap(
        context: Context,
        contentUriString: String?,
        artUriString: String?,
        size: Int
    ): Bitmap {
        val rounded = loadArtworkBitmap(context, contentUriString, artUriString, size, size, size / 2f)
        return rounded
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, width: Int, height: Int, radiusDp: Float): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        val density = 2.0f
        val radiusPx = radiusDp * density
        val rect = Rect(0, 0, width, height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawBitmap(bitmap, srcRect, rect, paint)

        return output
    }

    private fun createFallbackArtworkBitmap(context: Context, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Monochrome dark gradient
        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#242428"),
                Color.parseColor("#131315"),
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Concentric vinyl groove circles
        val groovePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.argb(14, 255, 255, 255)
            strokeWidth = 1.5f
        }
        val center = width / 2f
        canvas.drawCircle(center, center, width * 0.44f, groovePaint)
        groovePaint.strokeWidth = 1.0f
        canvas.drawCircle(center, center, width * 0.32f, groovePaint)

        // Draw official Soniclet launcher vector logo
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_soniclet_app_logo)
        if (drawable != null) {
            val iconSize = (Math.min(width, height) * 0.54f).toInt()
            val offsetX = (width - iconSize) / 2
            val offsetY = (height - iconSize) / 2
            drawable.setBounds(offsetX, offsetY, offsetX + iconSize, offsetY + iconSize)
            DrawableCompat.setTint(drawable, Color.parseColor("#EDEDF2"))
            drawable.draw(canvas)
        }

        return bitmap
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun createPlayButtonBitmap(context: Context, isPlaying: Boolean, accentColor: Int, cornerRadiusFloat: Float = 0.5f): Bitmap {
        val sizePx = 100
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        
        // Dynamically adjust button roundness based on corner radius setting
        val buttonRadius = 16f + (cornerRadiusFloat.coerceIn(0f, 1f) * 34f)
        val bounds = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.drawRoundRect(bounds, buttonRadius, buttonRadius, paint)

        val iconId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val drawable = ContextCompat.getDrawable(context, iconId)
        if (drawable != null) {
            val paddingPx = 25
            drawable.setBounds(paddingPx, paddingPx, sizePx - paddingPx, sizePx - paddingPx)
            DrawableCompat.setTint(drawable, Color.BLACK)
            drawable.draw(canvas)
        }
        return bitmap
    }

    // Pin Widget API Helper (Android 8.0+)
    fun requestPinWidget(context: Context, widgetTypeIndex: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                val providerClass = SonicletWidgetProvider::class.java
                val providerComponent = ComponentName(context, providerClass)
                val previewViews = when (widgetTypeIndex) {
                    0 -> buildTinyRemoteViews(context, "Soniclet", null, null, false)
                    1 -> buildSmallBarRemoteViews(context, "Soniclet", null, null, false)
                    2 -> buildCompactRemoteViews(context, "Now Playing", "Soniclet", null, null, false, false)
                    3 -> buildSquareRemoteViews(context, "Now Playing", "Soniclet", null, null, false)
                    4 -> buildCardRemoteViews(context, "Now Playing", "Soniclet", "Album", null, null, 0L, 0L, false, false, RepeatMode.OFF, false)
                    5 -> buildVinylRemoteViews(context, "Now Playing", "Soniclet", null, null, false, false, false)
                    else -> buildProRemoteViews(context, "Now Playing", "Soniclet", "Pro Deck", null, null, 0L, 0L, false, false, RepeatMode.OFF, false)
                }
                val extras = Bundle().apply {
                    putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, previewViews)
                }
                val success = appWidgetManager.requestPinAppWidget(providerComponent, extras, null)
                return success
            }
        }
        return false
    }
}
