package com.kexora.soniclet.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.RepeatMode

/**
 * Unified WidgetController managing state persistence, widget synchronization,
 * and background playback persistence across system restarts and app termination.
 */
object WidgetController {

    private const val PREFS_NAME = "soniclet_widget_controller_prefs"
    
    private const val KEY_SONG_ID = "ctrl_song_id"
    private const val KEY_SONG_TITLE = "ctrl_song_title"
    private const val KEY_SONG_ARTIST = "ctrl_song_artist"
    private const val KEY_SONG_ALBUM = "ctrl_song_album"
    private const val KEY_SONG_URI = "ctrl_song_uri"
    private const val KEY_SONG_ART_URI = "ctrl_song_art_uri"
    private const val KEY_DURATION = "ctrl_duration"
    private const val KEY_POSITION = "ctrl_position"
    private const val KEY_IS_PLAYING = "ctrl_is_playing"
    private const val KEY_IS_FAV = "ctrl_is_fav"
    private const val KEY_SHUFFLE = "ctrl_shuffle"
    private const val KEY_REPEAT_MODE = "ctrl_repeat_mode"

    fun savePlaybackState(
        context: Context,
        song: Song?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        isShuffle: Boolean,
        repeatMode: RepeatMode,
        isFavorite: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (song != null) {
            editor.putLong(KEY_SONG_ID, song.id)
            editor.putString(KEY_SONG_TITLE, song.title)
            editor.putString(KEY_SONG_ARTIST, song.artist)
            editor.putString(KEY_SONG_ALBUM, song.album)
            editor.putString(KEY_SONG_URI, song.contentUri.toString())
            editor.putString(KEY_SONG_ART_URI, song.albumArtUri?.toString())
            editor.putLong(KEY_DURATION, song.duration)
        }
        editor.putBoolean(KEY_IS_PLAYING, isPlaying)
        editor.putLong(KEY_POSITION, positionMs)
        editor.putBoolean(KEY_IS_FAV, isFavorite)
        editor.putBoolean(KEY_SHUFFLE, isShuffle)
        editor.putString(KEY_REPEAT_MODE, repeatMode.name)
        editor.apply()

        // Sync with SonicletWidgetManager
        SonicletWidgetManager.updateAllWidgets(
            context = context,
            song = song,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            repeatMode = repeatMode,
            isShuffle = isShuffle,
            currentPositionMs = positionMs
        )
    }

    fun getLastPlayedSong(context: Context): Song? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_SONG_URI, null) ?: return null
        val id = prefs.getLong(KEY_SONG_ID, 0L)
        val title = prefs.getString(KEY_SONG_TITLE, "Unknown Title") ?: "Unknown Title"
        val artist = prefs.getString(KEY_SONG_ARTIST, "Unknown Artist") ?: "Unknown Artist"
        val album = prefs.getString(KEY_SONG_ALBUM, "Unknown Album") ?: "Unknown Album"
        val duration = prefs.getLong(KEY_DURATION, 0L)
        val artUriStr = prefs.getString(KEY_SONG_ART_URI, null)

        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            contentUri = Uri.parse(uriString),
            albumArtUri = artUriStr?.let { Uri.parse(it) }
        )
    }

    fun isLastPlaying(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_PLAYING, false)
    }

    fun getLastPosition(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_POSITION, 0L)
    }
}
