package com.kexora.soniclet.library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.kexora.soniclet.database.FolderInfo
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.SavedQueue
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.database.SongDao
import com.kexora.soniclet.ui.components.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for scanning local audio files, managing playlists,
 * and handling folder exclusions.
 */
// Yeh interface define karta hai ki database/storage se gaane kaise fetch honge
enum class ShuffleMode {
    OFF,
    STANDARD,
    SMART
}

/**
 * Lightweight metadata fingerprint stored per tracked MediaStore song ID so that
 * moved or renamed files (which receive a new MediaStore._ID) can be reconciled automatically.
 */
data class TrackFingerprint(
    val fileName: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val size: Long = 0L
)

fun Song.toTrackFingerprint(includeFileSize: Boolean = true): TrackFingerprint {
    val resolvedFileName = filePath.substringAfterLast('/').trim()
    val resolvedSize = if (includeFileSize && filePath.isNotBlank()) {
        try {
            val f = File(filePath)
            if (f.exists()) f.length() else 0L
        } catch (_: Exception) {
            0L
        }
    } else {
        0L
    }
    return TrackFingerprint(
        fileName = resolvedFileName,
        title = title.trim(),
        artist = artist.trim(),
        duration = duration,
        size = resolvedSize
    )
}

interface AudioRepository {
    val excludedFolderPaths: StateFlow<Set<String>>
    val excludedSongIds: StateFlow<Set<Long>>
    val manualFolderUris: StateFlow<Set<String>>
    fun addManualFolder(uriString: String)
    fun removeManualFolder(uriString: String)
    val minDurationMs: StateFlow<Long>
    val playlists: StateFlow<List<Playlist>>
    val savedQueues: StateFlow<List<SavedQueue>>
    val favoriteSongIds: StateFlow<Set<Long>>
    val playCounts: StateFlow<Map<Long, Int>>
    val playDurations: StateFlow<Map<Long, Long>>
    val completionRatios: StateFlow<Map<Long, Float>>
    val rapidSkipCounts: StateFlow<Map<Long, Int>>
    val songMoods: StateFlow<Map<Long, String>>
    val recentlyPlayedSongIds: StateFlow<List<Long>>
    val accentColor: StateFlow<Long>
    val customBackgroundUri: StateFlow<String?>
    val backgroundMode: StateFlow<String>
    val bgImageBlur: StateFlow<Float>
    val bgImageDim: StateFlow<Float>
    val solidBgColor: StateFlow<Long>
    val solidBgOpacity: StateFlow<Float>
    val gradientPresetIndex: StateFlow<Int>
    val customGradientColors: StateFlow<List<Long>>
    val gradientIntensity: StateFlow<Float>
    val gradientBlur: StateFlow<Float>

    val shuffleMode: StateFlow<ShuffleMode>
    val isShuffleEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<String>

    val appThemeName: StateFlow<String>
    val isKariModeEnabled: StateFlow<Boolean>
    val kariBlur: StateFlow<Float>
    val kariOpacity: StateFlow<Float>
    val isShakeEnabled: StateFlow<Boolean>
    val isTwoFingerTapEnabled: StateFlow<Boolean>
    val twoFingerTapAction: StateFlow<String>
    val isThreeFingerSwipeEnabled: StateFlow<Boolean>
    val threeFingerSwipeAction: StateFlow<String>
    val isVolumeEdgeSwipeEnabled: StateFlow<Boolean>
    val isLongPressDragSeekEnabled: StateFlow<Boolean>

    val isOneFingerSwipeBackEnabled: StateFlow<Boolean>
    val isTwoFingerSwipeHomeEnabled: StateFlow<Boolean>
    val isMiniPlayerGesturesEnabled: StateFlow<Boolean>

    val isFlipToPauseEnabled: StateFlow<Boolean>
    val flipToPauseAction: StateFlow<String>
    val isWristTwistEnabled: StateFlow<Boolean>
    val playTogetherEnabled: StateFlow<Boolean>
    fun setPlayTogetherEnabled(enabled: Boolean)
    val playTogetherFadeEnabled: StateFlow<Boolean>
    fun setPlayTogetherFadeEnabled(enabled: Boolean)
    val wristTwistAction: StateFlow<String>
    val isPocketModeEnabled: StateFlow<Boolean>
    val pocketModeAction: StateFlow<String>
    val shakeAction: StateFlow<String>
    val isTiltToFastForwardEnabled: StateFlow<Boolean>
    val isAirWaveEnabled: StateFlow<Boolean>
    val airWaveAction: StateFlow<String>
    val isPickUpToPlayEnabled: StateFlow<Boolean>
    val isFirstLaunch: StateFlow<Boolean>
    val isMohModeActivated: StateFlow<Boolean>
    fun setMohModeActivated(activated: Boolean)
    val isCrossfadeEnabled: StateFlow<Boolean>
    val isAutoResumeOnPlugEnabled: StateFlow<Boolean>
    val isAutoPlayOnBluetoothEnabled: StateFlow<Boolean>
    val singleClickAction: StateFlow<String>
    val doubleClickAction: StateFlow<String>
    val tripleClickAction: StateFlow<String>
    val isHearingSafetyEnabled: StateFlow<Boolean>
    val isGaplessPlaybackEnabled: StateFlow<Boolean>
    val isResumeLastPositionEnabled: StateFlow<Boolean>
    val crossfadeDurationSeconds: StateFlow<Int>
    fun setCrossfadeEnabled(enabled: Boolean)
    fun setAutoResumeOnPlugEnabled(enabled: Boolean)
    fun setAutoPlayOnBluetoothEnabled(enabled: Boolean)
    fun setSingleClickAction(action: String)
    fun setDoubleClickAction(action: String)
    fun setTripleClickAction(action: String)
    fun setHearingSafetyEnabled(enabled: Boolean)
    fun setGaplessPlaybackEnabled(enabled: Boolean)
    fun setResumeLastPositionEnabled(enabled: Boolean)
    fun setCrossfadeDuration(seconds: Int)

    suspend fun fetchAudioFiles(): List<Song>
    suspend fun syncMediaStore(): List<Song>
    suspend fun syncMediaStoreIfChanged(currentSongs: List<Song>): Pair<Boolean, List<Song>>
    suspend fun updateSongMetadata(songId: Long, newTitle: String, newArtist: String, newAlbum: String, newYear: String = "", newGenre: String = "", newAlbumArtUri: android.net.Uri? = null): Boolean
    suspend fun getAllDiscoveredFolders(): List<FolderInfo>
    fun getAlbumArtUri(albumId: Long): Uri?

    fun excludeDirectory(folderPath: String)
    fun restoreDirectory(folderPath: String)
    fun restoreAllExcludedDirectories()
    fun excludeSong(songId: Long)
    fun restoreSong(songId: Long)
    fun restoreAllExcludedSongs()

    fun setMinDurationFilter(durationMs: Long)
    suspend fun deleteSongPhysical(song: Song): Boolean
    suspend fun moveSongPhysical(song: Song, targetFolderPath: String): Boolean
    suspend fun copySongPhysical(song: Song, targetFolderPath: String): Boolean
    suspend fun moveSongsBatch(songs: List<Song>, targetFolderPath: String): Pair<Int, Int>
    suspend fun copySongsBatch(songs: List<Song>, targetFolderPath: String): Pair<Int, Int>
    suspend fun createNewFolder(parentPath: String, folderName: String): Result<String>
    suspend fun renameFolderPhysical(oldPath: String, newName: String): Result<String>
    suspend fun deleteFolderPhysical(folderPath: String): Boolean
    fun addTrackedFolder(folderPath: String)

    fun setAccentColor(color: Long)
    fun setCustomBackgroundUri(uri: String?)
    fun setBackgroundMode(mode: String)
    fun setBgImageBlur(blur: Float)
    fun setBgImageDim(dim: Float)
    fun setSolidBgColor(color: Long)
    fun setSolidBgOpacity(opacity: Float)
    fun setGradientPresetIndex(index: Int)
    fun setCustomGradientColors(colors: List<Long>)
    fun setGradientIntensity(intensity: Float)
    fun setGradientBlur(blur: Float)

    fun setShuffleMode(mode: ShuffleMode)
    fun setShuffleEnabled(enabled: Boolean)
    fun setRepeatMode(mode: String)

    fun setAppThemeName(themeName: String)
    fun setKariModeEnabled(enabled: Boolean)
    fun setKariBlur(blur: Float)
    fun setKariOpacity(opacity: Float)
    fun setShakeEnabled(enabled: Boolean)
    fun setTwoFingerTapEnabled(enabled: Boolean)
    fun setTwoFingerTapAction(action: String)
    fun setThreeFingerSwipeEnabled(enabled: Boolean)
    fun setThreeFingerSwipeAction(action: String)
    fun setVolumeEdgeSwipeEnabled(enabled: Boolean)
    fun setLongPressDragSeekEnabled(enabled: Boolean)

    fun setOneFingerSwipeBackEnabled(enabled: Boolean)
    fun setTwoFingerSwipeHomeEnabled(enabled: Boolean)
    fun setMiniPlayerGesturesEnabled(enabled: Boolean)

    fun setFlipToPauseEnabled(enabled: Boolean)
    fun setFlipToPauseAction(action: String)
    fun setWristTwistEnabled(enabled: Boolean)
    fun setWristTwistAction(action: String)
    fun setPocketModeEnabled(enabled: Boolean)
    fun setPocketModeAction(action: String)
    fun setShakeAction(action: String)
    fun setTiltToFastForwardEnabled(enabled: Boolean)
    fun setAirWaveEnabled(enabled: Boolean)
    fun setAirWaveAction(action: String)
    fun setPickUpToPlayEnabled(enabled: Boolean)
    fun setFirstLaunch(isFirst: Boolean)

    fun toggleFavorite(songId: Long)
    fun incrementPlayCount(songId: Long)
    fun recordListeningDuration(songId: Long, durationMs: Long)
    fun recordCompletionRatio(songId: Long, ratio: Float)
    fun recordRapidSkip(songId: Long)
    fun flushPlayDurations()
    fun setSongMood(songId: Long, mood: String?)
    fun recordSongPlayed(songId: Long)

    fun createPlaylist(name: String, initialSongIds: List<Long> = emptyList()): Playlist
    fun renamePlaylist(playlistId: String, newName: String)
    fun deletePlaylist(playlistId: String)
    fun addSongsToPlaylist(playlistId: String, songIds: List<Long>)
    fun removeSongFromPlaylist(playlistId: String, songId: Long)
    fun reorderPlaylistSongs(playlistId: String, newSongIds: List<Long>)
    fun updatePlaylistCover(playlistId: String, coverUri: String?)
    fun updatePlaylistGradient(playlistId: String, gradientIndex: Int)

    fun createSavedQueue(name: String, songIds: List<Long>): SavedQueue
    fun upsertSavedQueue(name: String, songIds: List<Long>): SavedQueue
    fun deleteSavedQueue(queueId: String)
    
    fun savePlaybackPosition(songId: Long, positionMs: Long)
    fun getSavedPlaybackPosition(songId: Long): Long
    val playbackPositions: StateFlow<Map<Long, Long>>

    val nowPlayingLayout: StateFlow<String>
    val cornerRadiusScale: StateFlow<Float>
    val fontStyle: StateFlow<String>
    val motionLevel: StateFlow<String>

    val recentSearches: StateFlow<List<String>>
    fun addRecentSearch(query: String)
    fun removeRecentSearch(query: String)
    fun clearRecentSearches()

    val includeSystemHiddenInLibrary: StateFlow<Boolean>
    fun setIncludeSystemHiddenInLibrary(enabled: Boolean)

    val restoredAutoSongIds: StateFlow<Set<Long>>
    val lastPlayedTimestamps: StateFlow<Map<Long, Long>>
    fun syncAutoHiddenItems(allSongs: List<Song>)
    fun upsertPlaylistByName(name: String, songIds: List<Long>): Playlist

    fun setNowPlayingLayout(layout: String)
    fun setCornerRadiusScale(scale: Float)
    fun setFontStyle(style: String)
    fun setMotionLevel(level: String)

    fun recordTrackFingerprint(song: Song)
    fun recordTrackFingerprints(songs: List<Song>)
    fun reconcileMovedOrDeletedSongs(currentSongs: List<Song>): Map<Long, Long>
}

/**
 * Automatic filter for non-music audio files: call recordings, voice recordings,
 * WhatsApp/Telegram voice notes & audio messages, and short clips under 45 seconds.
 */
object AutoHideAudioFilter {
    private val AUTO_HIDE_FOLDER_KEYWORDS = listOf(
        "call",
        "recordings",
        "recording",
        "voice recorder",
        "voicerecorder",
        "sound recorder",
        "sound_recorder",
        "audio recorder",
        "voice notes",
        "voicenotes",
        "voice memos",
        "whatsapp voice notes",
        "whatsapp audio",
        "telegram audio",
        "telegram voice"
    )

    private val AUTO_HIDE_FILE_PREFIXES = listOf(
        "ptt-",
        "ptt_",
        "aud-",
        "aud_",
        "call_",
        "call-",
        "call ",
        "callrecording",
        "rec_",
        "rec-",
        "rec ",
        "recording",
        "voice_",
        "voice-",
        "voicenote"
    )

    fun isUnwantedVoiceOrCallFolder(folderPath: String, folderName: String): Boolean {
        val pathLower = folderPath.trim().lowercase()
        val nameLower = folderName.trim().lowercase()
        if (nameLower in AUTO_HIDE_FOLDER_KEYWORDS) return true
        if (AUTO_HIDE_FOLDER_KEYWORDS.any { kw -> nameLower.contains(kw) }) return true
        val segments = pathLower.split('/').filter { it.isNotBlank() }
        return segments.any { seg ->
            AUTO_HIDE_FOLDER_KEYWORDS.any { kw -> seg == kw || seg.contains(kw) }
        }
    }

    fun isUnwantedVoiceOrCallSong(song: Song): Boolean {
        // Short voice clips under 45 seconds
        if (song.duration in 1L until 45_000L) return true
        if (isUnwantedVoiceOrCallFolder(song.folderPath, song.folderName)) return true

        val rawFileName = song.filePath.substringAfterLast('/').trim().lowercase()
        val cleanTitle = song.title.trim().lowercase()
        return AUTO_HIDE_FILE_PREFIXES.any { prefix ->
            rawFileName.startsWith(prefix) || cleanTitle.startsWith(prefix)
        }
    }
}

/**
 * Production implementation of [AudioRepository] using ContentResolver and MediaStore.Audio.Media.
 * Persists user exclusions, playlists, and settings in SharedPreferences.
 */
@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val context: Context
) : AudioRepository {

    private val prefs = context.getSharedPreferences("soniclet_audio_prefs", Context.MODE_PRIVATE)

    private val _excludedFolderPaths = MutableStateFlow<Set<String>>(emptySet())
    override val excludedFolderPaths: StateFlow<Set<String>> = _excludedFolderPaths.asStateFlow()

    private val _manualFolderUris = MutableStateFlow<Set<String>>(emptySet())
    override val manualFolderUris: StateFlow<Set<String>> = _manualFolderUris.asStateFlow()

    private val _excludedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    override val excludedSongIds: StateFlow<Set<Long>> = _excludedSongIds.asStateFlow()

    private val _restoredAutoFolders = MutableStateFlow<Set<String>>(emptySet())
    private val _restoredAutoSongIds = MutableStateFlow<Set<Long>>(emptySet())
    override val restoredAutoSongIds: StateFlow<Set<Long>> = _restoredAutoSongIds.asStateFlow()

    private val _lastPlayedTimestamps = MutableStateFlow<Map<Long, Long>>(emptyMap())
    override val lastPlayedTimestamps: StateFlow<Map<Long, Long>> = _lastPlayedTimestamps.asStateFlow()

    private val _minDurationMs = MutableStateFlow<Long>(10000L)
    override val minDurationMs: StateFlow<Long> = _minDurationMs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    override val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _savedQueues = MutableStateFlow<List<SavedQueue>>(emptyList())
    override val savedQueues: StateFlow<List<SavedQueue>> = _savedQueues.asStateFlow()

    private val _favoriteSongIds = MutableStateFlow<Set<Long>>(emptySet())
    override val favoriteSongIds: StateFlow<Set<Long>> = _favoriteSongIds.asStateFlow()

    private val _playCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    override val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()

    private val _playDurations = MutableStateFlow<Map<Long, Long>>(emptyMap())
    override val playDurations: StateFlow<Map<Long, Long>> = _playDurations.asStateFlow()
    private var lastDurationPersistTime = 0L

    private val _completionRatios = MutableStateFlow<Map<Long, Float>>(emptyMap())
    override val completionRatios: StateFlow<Map<Long, Float>> = _completionRatios.asStateFlow()

    private val _rapidSkipCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    override val rapidSkipCounts: StateFlow<Map<Long, Int>> = _rapidSkipCounts.asStateFlow()

    private val _playbackPositions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    override val playbackPositions: StateFlow<Map<Long, Long>> = _playbackPositions.asStateFlow()

    private val fingerprintLock = Any()
    private val trackFingerprints = mutableMapOf<Long, TrackFingerprint>()

    private val _songMoods = MutableStateFlow<Map<Long, String>>(emptyMap())
    override val songMoods: StateFlow<Map<Long, String>> = _songMoods.asStateFlow()

    private val _recentlyPlayedSongIds = MutableStateFlow<List<Long>>(emptyList())
    override val recentlyPlayedSongIds: StateFlow<List<Long>> = _recentlyPlayedSongIds.asStateFlow()

    private val _accentColor = MutableStateFlow<Long>(0xFF00E676)
    override val accentColor: StateFlow<Long> = _accentColor.asStateFlow()

    private val _customBackgroundUri = MutableStateFlow<String?>(null)
    override val customBackgroundUri: StateFlow<String?> = _customBackgroundUri.asStateFlow()

    private val _backgroundMode = MutableStateFlow<String>("AMOLED")
    override val backgroundMode: StateFlow<String> = _backgroundMode.asStateFlow()

    private val _bgImageBlur = MutableStateFlow<Float>(10f)
    override val bgImageBlur: StateFlow<Float> = _bgImageBlur.asStateFlow()

    private val _bgImageDim = MutableStateFlow<Float>(0.5f)
    override val bgImageDim: StateFlow<Float> = _bgImageDim.asStateFlow()

    private val _solidBgColor = MutableStateFlow<Long>(0xFF121212L)
    override val solidBgColor: StateFlow<Long> = _solidBgColor.asStateFlow()

    private val _solidBgOpacity = MutableStateFlow<Float>(1.0f)
    override val solidBgOpacity: StateFlow<Float> = _solidBgOpacity.asStateFlow()

    private val _gradientPresetIndex = MutableStateFlow<Int>(0)
    override val gradientPresetIndex: StateFlow<Int> = _gradientPresetIndex.asStateFlow()
    private val _customGradientColors = MutableStateFlow<List<Long>>(listOf(0xFF0F2027L, 0xFF203A43L, 0xFF2C5364L))
    override val customGradientColors: StateFlow<List<Long>> = _customGradientColors.asStateFlow()

    private val _gradientIntensity = MutableStateFlow<Float>(1.0f)
    override val gradientIntensity: StateFlow<Float> = _gradientIntensity.asStateFlow()
    
    
    
    

    
    private val _nowPlayingLayout = kotlinx.coroutines.flow.MutableStateFlow("CLASSIC")
    override val nowPlayingLayout: StateFlow<String> = _nowPlayingLayout.asStateFlow()

    private val _cornerRadiusScale = kotlinx.coroutines.flow.MutableStateFlow(0.5f)
    override val cornerRadiusScale: StateFlow<Float> = _cornerRadiusScale.asStateFlow()

    private val _fontStyle = kotlinx.coroutines.flow.MutableStateFlow("STANDARD")
    override val fontStyle: StateFlow<String> = _fontStyle.asStateFlow()

    private val _motionLevel = kotlinx.coroutines.flow.MutableStateFlow("FLUID")
    override val motionLevel: StateFlow<String> = _motionLevel.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    override val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()
private val _gradientBlur = MutableStateFlow<Float>(0f)
    override val gradientBlur: StateFlow<Float>
 = _gradientBlur.asStateFlow()

    private val _shuffleMode = MutableStateFlow<ShuffleMode>(
        try {
            val saved = prefs.getString("setting_shuffle_mode_v2", null)
            if (saved != null) {
                ShuffleMode.valueOf(saved)
            } else if (prefs.getBoolean("setting_shuffle_enabled", false)) {
                ShuffleMode.STANDARD
            } else {
                ShuffleMode.OFF
            }
        } catch (e: Exception) {
            ShuffleMode.OFF
        }
    )
    override val shuffleMode: StateFlow<ShuffleMode> = _shuffleMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow<Boolean>(_shuffleMode.value != ShuffleMode.OFF)
    override val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow<String>(prefs.getString("setting_repeat_mode", "OFF") ?: "OFF")
    override val repeatMode: StateFlow<String> = _repeatMode.asStateFlow()

    private val _appThemeName = MutableStateFlow<String>("SYSTEM")
    override val appThemeName: StateFlow<String> = _appThemeName.asStateFlow()

    private val _isKariModeEnabled = MutableStateFlow<Boolean>(false)
    override val isKariModeEnabled: StateFlow<Boolean> = _isKariModeEnabled.asStateFlow()

    private val _kariBlur = MutableStateFlow<Float>(12f)
    override val kariBlur: StateFlow<Float> = _kariBlur.asStateFlow()

    private val _kariOpacity = MutableStateFlow<Float>(0.9f)
    override val kariOpacity: StateFlow<Float> = _kariOpacity.asStateFlow()

    private val _isShakeEnabled = MutableStateFlow<Boolean>(false)
    override val isShakeEnabled: StateFlow<Boolean> = _isShakeEnabled.asStateFlow()

    private val _isTwoFingerTapEnabled = MutableStateFlow(true)
    override val isTwoFingerTapEnabled: StateFlow<Boolean> = _isTwoFingerTapEnabled.asStateFlow()
    private val _twoFingerTapAction = MutableStateFlow("Play/Pause")
    override val twoFingerTapAction: StateFlow<String> = _twoFingerTapAction.asStateFlow()
    private val _isThreeFingerSwipeEnabled = MutableStateFlow(true)
    override val isThreeFingerSwipeEnabled: StateFlow<Boolean> = _isThreeFingerSwipeEnabled.asStateFlow()
    private val _threeFingerSwipeAction = MutableStateFlow("Next/Previous")
    override val threeFingerSwipeAction: StateFlow<String> = _threeFingerSwipeAction.asStateFlow()
    private val _isVolumeEdgeSwipeEnabled = MutableStateFlow(true)
    override val isVolumeEdgeSwipeEnabled: StateFlow<Boolean> = _isVolumeEdgeSwipeEnabled.asStateFlow()
    private val _isLongPressDragSeekEnabled = MutableStateFlow(true)
    override val isLongPressDragSeekEnabled: StateFlow<Boolean> = _isLongPressDragSeekEnabled.asStateFlow()

    private val _isOneFingerSwipeBackEnabled = MutableStateFlow(true)
    override val isOneFingerSwipeBackEnabled: StateFlow<Boolean> = _isOneFingerSwipeBackEnabled.asStateFlow()
    
    private val _isTwoFingerSwipeHomeEnabled = MutableStateFlow(true)
    override val isTwoFingerSwipeHomeEnabled: StateFlow<Boolean> = _isTwoFingerSwipeHomeEnabled.asStateFlow()
    
    private val _isMiniPlayerGesturesEnabled = MutableStateFlow(true)
    override val isMiniPlayerGesturesEnabled: StateFlow<Boolean> = _isMiniPlayerGesturesEnabled.asStateFlow()
    private val _isFlipToPauseEnabled = MutableStateFlow(true)
    override val isFlipToPauseEnabled: StateFlow<Boolean> = _isFlipToPauseEnabled.asStateFlow()
    private val _flipToPauseAction = MutableStateFlow("Pause")
    override val flipToPauseAction: StateFlow<String> = _flipToPauseAction.asStateFlow()
    private val _playTogetherEnabled = MutableStateFlow(false)
    override val playTogetherEnabled: StateFlow<Boolean> = _playTogetherEnabled.asStateFlow()
    private val _playTogetherFadeEnabled = MutableStateFlow(true)
    override val playTogetherFadeEnabled: StateFlow<Boolean> = _playTogetherFadeEnabled.asStateFlow()

    private val _isWristTwistEnabled = MutableStateFlow(true)
    override val isWristTwistEnabled: StateFlow<Boolean> = _isWristTwistEnabled.asStateFlow()
    private val _wristTwistAction = MutableStateFlow("Favorite")
    override val wristTwistAction: StateFlow<String> = _wristTwistAction.asStateFlow()
    private val _isPocketModeEnabled = MutableStateFlow(true)
    override val isPocketModeEnabled: StateFlow<Boolean> = _isPocketModeEnabled.asStateFlow()
    private val _pocketModeAction = MutableStateFlow("Auto-Play/Pause")
    override val pocketModeAction: StateFlow<String> = _pocketModeAction.asStateFlow()
    private val _shakeAction = MutableStateFlow("Shuffle")
    override val shakeAction: StateFlow<String> = _shakeAction.asStateFlow()
    private val _isTiltToFastForwardEnabled = MutableStateFlow(true)
    override val isTiltToFastForwardEnabled: StateFlow<Boolean> = _isTiltToFastForwardEnabled.asStateFlow()
    private val _isAirWaveEnabled = MutableStateFlow(false)
    override val isAirWaveEnabled: StateFlow<Boolean> = _isAirWaveEnabled.asStateFlow()
    private val _airWaveAction = MutableStateFlow("Play/Pause")
    override val airWaveAction: StateFlow<String> = _airWaveAction.asStateFlow()
    private val _isPickUpToPlayEnabled = MutableStateFlow(false)
    override val isPickUpToPlayEnabled: StateFlow<Boolean> = _isPickUpToPlayEnabled.asStateFlow()
    private val _isCrossfadeEnabled = MutableStateFlow<Boolean>(false)
    override val isCrossfadeEnabled: StateFlow<Boolean> = _isCrossfadeEnabled.asStateFlow()

    private val _isAutoResumeOnPlugEnabled = MutableStateFlow<Boolean>(false)
    override val isAutoResumeOnPlugEnabled: StateFlow<Boolean> = _isAutoResumeOnPlugEnabled.asStateFlow()

    private val _isAutoPlayOnBluetoothEnabled = MutableStateFlow<Boolean>(false)
    override val isAutoPlayOnBluetoothEnabled: StateFlow<Boolean> = _isAutoPlayOnBluetoothEnabled.asStateFlow()

    private val _singleClickAction = MutableStateFlow<String>("Play/Pause")
    override val singleClickAction: StateFlow<String> = _singleClickAction.asStateFlow()

    private val _doubleClickAction = MutableStateFlow<String>("Next")
    override val doubleClickAction: StateFlow<String> = _doubleClickAction.asStateFlow()

    private val _tripleClickAction = MutableStateFlow<String>("Previous")
    override val tripleClickAction: StateFlow<String> = _tripleClickAction.asStateFlow()

    private val _isHearingSafetyEnabled = MutableStateFlow<Boolean>(true)
    override val isHearingSafetyEnabled: StateFlow<Boolean> = _isHearingSafetyEnabled.asStateFlow()

    private val _isGaplessPlaybackEnabled = MutableStateFlow<Boolean>(true)
    override val isGaplessPlaybackEnabled: StateFlow<Boolean> = _isGaplessPlaybackEnabled.asStateFlow()

    private val _isResumeLastPositionEnabled = MutableStateFlow<Boolean>(true)
    override val isResumeLastPositionEnabled: StateFlow<Boolean> = _isResumeLastPositionEnabled.asStateFlow()

    private val _crossfadeDurationSeconds = MutableStateFlow<Int>(3)
    override val crossfadeDurationSeconds: StateFlow<Int> = _crossfadeDurationSeconds.asStateFlow()

    private val _isFirstLaunch = MutableStateFlow<Boolean>(true)
    override val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

    private val _isMohModeActivated = MutableStateFlow<Boolean>(false)
    override val isMohModeActivated: StateFlow<Boolean> = _isMohModeActivated.asStateFlow()

    private val _includeSystemHiddenInLibrary = MutableStateFlow<Boolean>(false)
    override val includeSystemHiddenInLibrary: StateFlow<Boolean> = _includeSystemHiddenInLibrary.asStateFlow()

    override fun setIncludeSystemHiddenInLibrary(enabled: Boolean) {
        _includeSystemHiddenInLibrary.value = enabled
        prefs.edit().putBoolean("setting_include_system_hidden_library", enabled).apply()
    }

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    init {
        loadPersistedData()
    }

    private fun loadPersistedData() {
        // Excluded folders
        val savedExcluded = prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
        _excludedFolderPaths.value = savedExcluded

        // Excluded songs
        val savedExcludedSongs = prefs.getStringSet("excluded_songs", emptySet()) ?: emptySet()
        _excludedSongIds.value = savedExcludedSongs.mapNotNull { it.toLongOrNull() }.toSet()

        // Restored auto-hidden folders and songs
        val savedRestoredFolders = prefs.getStringSet("restored_auto_hidden_folders", emptySet()) ?: emptySet()
        _restoredAutoFolders.value = savedRestoredFolders

        val savedRestoredSongs = prefs.getStringSet("restored_auto_hidden_songs", emptySet()) ?: emptySet()
        _restoredAutoSongIds.value = savedRestoredSongs.mapNotNull { it.toLongOrNull() }.toSet()

        // Last played timestamps JSON
        val savedLastPlayedJson = prefs.getString("last_played_timestamps_json", null)
        if (savedLastPlayedJson != null) {
            try {
                val map = mutableMapOf<Long, Long>()
                val obj = JSONObject(savedLastPlayedJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLong = key.toLongOrNull()
                    if (keyLong != null) {
                        map[keyLong] = obj.getLong(key)
                    }
                }
                _lastPlayedTimestamps.value = map
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Min duration filter
        val savedMinDuration = prefs.getLong("min_duration_ms", 10000L)
        _minDurationMs.value = savedMinDuration

        // Persistent settings
        _accentColor.value = prefs.getLong("setting_accent_color", 0xFF00E676)
        val savedBgUri = prefs.getString("setting_bg_uri", null)
        if (savedBgUri != null) {
            try {
                val parsed = Uri.parse(savedBgUri)
                if (parsed.scheme == "file") {
                    val file = File(parsed.path ?: "")
                    if (file.exists() && file.length() > 0) {
                        _customBackgroundUri.value = savedBgUri
                    } else {
                        _customBackgroundUri.value = null
                        prefs.edit().remove("setting_bg_uri").apply()
                    }
                } else {
                    _customBackgroundUri.value = savedBgUri
                }
            } catch (e: Exception) {
                _customBackgroundUri.value = savedBgUri
            }
        } else {
            _customBackgroundUri.value = null
        }
        if (!prefs.contains("setting_bg_mode")) {
            prefs.edit().putString("setting_bg_mode", "AMOLED").apply()
            _backgroundMode.value = "AMOLED"
        } else {
            _backgroundMode.value = prefs.getString("setting_bg_mode", "AMOLED") ?: "AMOLED"
        }
        _bgImageBlur.value = prefs.getFloat("setting_bg_image_blur", 10f)
        _bgImageDim.value = prefs.getFloat("setting_bg_image_dim", 0.5f)
        _solidBgColor.value = prefs.getLong("setting_solid_bg_color", 0xFF121212L)
        _solidBgOpacity.value = prefs.getFloat("setting_solid_bg_opacity", 1.0f)
        _gradientPresetIndex.value = prefs.getInt("setting_gradient_preset", 0)
        
        val customColorsStr = prefs.getString("setting_custom_gradient_colors", null)
        if (customColorsStr != null) {
            try {
                val colors = customColorsStr.split(",").map { it.toLong() }
                _customGradientColors.value = colors
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _gradientIntensity.value = prefs.getFloat("setting_gradient_intensity", 1.0f)
        
        _nowPlayingLayout.value = prefs.getString("setting_now_playing_layout", "CLASSIC") ?: "CLASSIC"
        _cornerRadiusScale.value = prefs.getFloat("setting_corner_radius", 0.5f)
        _fontStyle.value = prefs.getString("setting_font_style", "STANDARD") ?: "STANDARD"
        _motionLevel.value = prefs.getString("setting_motion_level", "FLUID") ?: "FLUID"
        _gradientBlur.value = prefs.getFloat("setting_gradient_blur", 0f)
        
        if (!prefs.contains("setting_app_theme")) {
            prefs.edit().putString("setting_app_theme", "DARK").apply()
            _appThemeName.value = "DARK"
        } else {
            _appThemeName.value = prefs.getString("setting_app_theme", "DARK") ?: "DARK"
        }
        
        _isKariModeEnabled.value = prefs.getBoolean("setting_kari_mode", false)
        _kariBlur.value = prefs.getFloat("setting_kari_blur", 12f)
        _kariOpacity.value = prefs.getFloat("setting_kari_opacity", 0.9f)
        _isShakeEnabled.value = prefs.getBoolean("setting_shake_enabled", false)
        _isTwoFingerTapEnabled.value = prefs.getBoolean("setting_two_finger_tap_enabled", true)
        _twoFingerTapAction.value = prefs.getString("setting_two_finger_tap_action", "Play/Pause") ?: "Play/Pause"
        _isThreeFingerSwipeEnabled.value = prefs.getBoolean("setting_three_finger_swipe_enabled", true)
        _threeFingerSwipeAction.value = prefs.getString("setting_three_finger_swipe_action", "Next/Previous") ?: "Next/Previous"
        _isVolumeEdgeSwipeEnabled.value = prefs.getBoolean("setting_volume_edge_swipe_enabled", true)
        _isLongPressDragSeekEnabled.value = prefs.getBoolean("setting_long_press_drag_seek_enabled", true)

        _isOneFingerSwipeBackEnabled.value = prefs.getBoolean("setting_one_finger_swipe_back_enabled", true)
        _isTwoFingerSwipeHomeEnabled.value = prefs.getBoolean("setting_two_finger_swipe_home_enabled", true)
        _isMiniPlayerGesturesEnabled.value = prefs.getBoolean("setting_mini_player_gestures_enabled", true)

        _isFlipToPauseEnabled.value = prefs.getBoolean("setting_flip_to_pause_enabled", false)
        _flipToPauseAction.value = prefs.getString("setting_flip_to_pause_action", "Pause") ?: "Pause"
        _isWristTwistEnabled.value = prefs.getBoolean("setting_wrist_twist_enabled", false)
        _playTogetherEnabled.value = prefs.getBoolean("setting_play_together", false)
        _playTogetherFadeEnabled.value = prefs.getBoolean("setting_play_together_fade", true)
        _wristTwistAction.value = prefs.getString("setting_wrist_twist_action", "Favorite") ?: "Favorite"
        _isPocketModeEnabled.value = prefs.getBoolean("setting_pocket_mode_enabled", false)
        _pocketModeAction.value = prefs.getString("setting_pocket_mode_action", "Auto-Play/Pause") ?: "Auto-Play/Pause"
        _shakeAction.value = prefs.getString("setting_shake_action", "Shuffle") ?: "Shuffle"
        _isTiltToFastForwardEnabled.value = prefs.getBoolean("setting_tilt_to_fast_forward_enabled", false)
        _isCrossfadeEnabled.value = prefs.getBoolean("setting_crossfade_enabled", false)
        _isAutoResumeOnPlugEnabled.value = prefs.getBoolean("setting_auto_resume_on_plug", false)
        _isAutoPlayOnBluetoothEnabled.value = prefs.getBoolean("setting_auto_play_on_bluetooth", false)
        _singleClickAction.value = prefs.getString("setting_single_click_action", "Play/Pause") ?: "Play/Pause"
        _doubleClickAction.value = prefs.getString("setting_double_click_action", "Next") ?: "Next"
        _tripleClickAction.value = prefs.getString("setting_triple_click_action", "Previous") ?: "Previous"
        _isHearingSafetyEnabled.value = prefs.getBoolean("setting_hearing_safety", true)
        _isGaplessPlaybackEnabled.value = prefs.getBoolean("setting_gapless_enabled", true)
        _isResumeLastPositionEnabled.value = prefs.getBoolean("setting_resume_last_position", true)
        _crossfadeDurationSeconds.value = prefs.getInt("setting_crossfade_duration", 3)
        val hasAudioPerm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val rawFirstLaunch = prefs.getBoolean("setting_is_first_launch", true)
        val finalFirstLaunch = if (hasAudioPerm) {
            if (rawFirstLaunch) {
                prefs.edit().putBoolean("setting_is_first_launch", false).apply()
            }
            false
        } else {
            rawFirstLaunch
        }
        _isFirstLaunch.value = finalFirstLaunch
        val isMohActive = prefs.getBoolean("setting_moh_mode_activated", false)
        _isMohModeActivated.value = isMohActive
        if (!isMohActive) {
            // Strictly clean up any leftover Easter Egg notifications or alarms for non-easter-egg users
            com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.cancelAlarm(context)
            com.kexora.soniclet.worker.SpecialDateNotificationWorker.cancel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            for (i in 0..20) {
                nm?.cancel(com.kexora.soniclet.easteregg.MuhLoveNotificationReceiver.NOTIFICATION_ID + i)
            }
        }
        _includeSystemHiddenInLibrary.value = prefs.getBoolean("setting_include_system_hidden_library", false)

        // Favorites
        val savedFavs = prefs.getStringSet("favorite_songs", emptySet()) ?: emptySet()
        val favLongs = savedFavs.mapNotNull { it.toLongOrNull() }.toSet()
        _favoriteSongIds.value = favLongs

        // Recently Played JSON
        val savedRecentJson = prefs.getString("recently_played_json", null)
        if (savedRecentJson != null) {
            try {
                val list = mutableListOf<Long>()
                val array = JSONArray(savedRecentJson)
                for (i in 0 until array.length()) {
                    list.add(array.getLong(i))
                }
                _recentlyPlayedSongIds.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Play Counts
        val savedCountsJson = prefs.getString("play_counts_json", null)
        if (savedCountsJson != null) {
            try {
                val map = mutableMapOf<Long, Int>()
                val obj = JSONObject(savedCountsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLong = key.toLongOrNull()
                    if (keyLong != null) {
                        map[keyLong] = obj.getInt(key)
                    }
                }
                _playCounts.value = map
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Play Durations (Listening Time in ms)
        val savedDurationsJson = prefs.getString("play_durations_json", null)
        if (savedDurationsJson != null) {
            try {
                val map = mutableMapOf<Long, Long>()
                val obj = JSONObject(savedDurationsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLong = key.toLongOrNull()
                    if (keyLong != null) {
                        map[keyLong] = obj.getLong(key)
                    }
                }
                _playDurations.value = map
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Completion Ratios (0.0..1.5)
        val savedCompletionJson = prefs.getString("completion_ratios_json", null)
        if (savedCompletionJson != null) {
            try {
                val map = mutableMapOf<Long, Float>()
                val obj = JSONObject(savedCompletionJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLong = key.toLongOrNull()
                    if (keyLong != null) {
                        map[keyLong] = obj.optDouble(key, 0.0).toFloat()
                    }
                }
                _completionRatios.value = map
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Rapid Skip Counts (< 5s skips)
        val savedSkipsJson = prefs.getString("rapid_skip_counts_json", null)
        if (savedSkipsJson != null) {
            try {
                val map = mutableMapOf<Long, Int>()
                val obj = JSONObject(savedSkipsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLong = key.toLongOrNull()
                    if (keyLong != null) {
                        map[keyLong] = obj.optInt(key, 0)
                    }
                }
                _rapidSkipCounts.value = map
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Playback Positions Map
        try {
            val posMap = mutableMapOf<Long, Long>()
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("playback_pos_") && (value is Long || value is Int)) {
                    val songId = key.removePrefix("playback_pos_").toLongOrNull()
                    val pos = (value as? Long) ?: (value as? Int)?.toLong() ?: 0L
                    if (songId != null && pos > 0L) {
                        posMap[songId] = pos
                    }
                }
            }
            _playbackPositions.value = posMap
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Song Moods
        val savedMoodsJson = prefs.getString("song_moods_json", null)
        if (savedMoodsJson != null) {
            try {
                val map = mutableMapOf<Long, String>()
                val obj = JSONObject(savedMoodsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLong = key.toLongOrNull()
                    if (keyLong != null) {
                        map[keyLong] = obj.getString(key)
                    }
                }
                _songMoods.value = map
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Playlists JSON
        val savedPlaylistsJson = prefs.getString("playlists_json", null)
        if (savedPlaylistsJson != null) {
            try {
                val list = mutableListOf<Playlist>()
                val array = JSONArray(savedPlaylistsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    val customCoverUri = if (obj.has("customCoverUri")) obj.getString("customCoverUri") else null
                    val colorGradientIndex = obj.optInt("colorGradientIndex", 0)
                    val songsArray = obj.optJSONArray("songIds")
                    val songIds = mutableListOf<Long>()
                    if (songsArray != null) {
                        for (j in 0 until songsArray.length()) {
                            songIds.add(songsArray.getLong(j))
                        }
                    }
                    list.add(Playlist(id = id, name = name, songIds = songIds, createdAt = createdAt, customCoverUri = customCoverUri, colorGradientIndex = colorGradientIndex))
                }
                // Filter out any legacy duplicate Favorites custom playlists since Favorites is a native pre-built smart playlist
                val cleanList = list.filterNot { 
                    it.id == "pl_favorites" || 
                    it.name.trim().equals("Favorites", ignoreCase = true) || 
                    it.name.trim().equals("Favorites ❤️", ignoreCase = true) 
                }
                _playlists.value = cleanList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Saved Queues JSON
        val savedQueuesJson = prefs.getString("saved_queues_json", null)
        if (savedQueuesJson != null) {
            try {
                val list = mutableListOf<SavedQueue>()
                val array = JSONArray(savedQueuesJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    val songsArray = obj.optJSONArray("songIds")
                    val songIds = mutableListOf<Long>()
                    if (songsArray != null) {
                        for (j in 0 until songsArray.length()) {
                            songIds.add(songsArray.getLong(j))
                        }
                    }
                    list.add(SavedQueue(id = id, name = name, songIds = songIds, createdAt = createdAt))
                }
                _savedQueues.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Recent Searches JSON
        val savedRecentSearchesJson = prefs.getString("recent_searches_list_json", null)
        if (savedRecentSearchesJson != null) {
            try {
                val list = mutableListOf<String>()
                val array = JSONArray(savedRecentSearchesJson)
                for (i in 0 until array.length()) {
                    val s = array.getString(i)
                    if (s.isNotBlank()) list.add(s)
                }
                _recentSearches.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Track Fingerprints JSON for automatic moved/renamed file ID healing
        val savedFingerprintsJson = prefs.getString("song_fingerprints_json", null)
        if (savedFingerprintsJson != null) {
            try {
                val obj = JSONObject(savedFingerprintsJson)
                val loaded = mutableMapOf<Long, TrackFingerprint>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull() ?: continue
                    val fpObj = obj.optJSONObject(key) ?: continue
                    loaded[id] = TrackFingerprint(
                        fileName = fpObj.optString("fileName", ""),
                        title = fpObj.optString("title", ""),
                        artist = fpObj.optString("artist", ""),
                        duration = fpObj.optLong("duration", 0L),
                        size = fpObj.optLong("size", 0L)
                    )
                }
                synchronized(fingerprintLock) {
                    trackFingerprints.clear()
                    trackFingerprints.putAll(loaded)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun persistRecentSearches(list: List<String>) {
        _recentSearches.value = list
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            prefs.edit().putString("recent_searches_list_json", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(clean, ignoreCase = true) }
        current.add(0, clean)
        val trimmed = current.take(20)
        persistRecentSearches(trimmed)
    }

    override fun removeRecentSearch(query: String) {
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        persistRecentSearches(current)
    }

    override fun clearRecentSearches() {
        persistRecentSearches(emptyList())
    }

    private fun persistFavorites(set: Set<Long>) {
        _favoriteSongIds.value = set
        val stringSet = set.map { it.toString() }.toSet()
        prefs.edit().putStringSet("favorite_songs", stringSet).apply()

        // Ensure no duplicate custom user playlist named Favorites is stored
        val existing = _playlists.value
        val cleanedList = existing.filterNot { 
            it.id == "pl_favorites" || 
            it.name.trim().equals("Favorites", ignoreCase = true) || 
            it.name.trim().equals("Favorites ❤️", ignoreCase = true) 
        }
        if (cleanedList.size != existing.size) {
            persistPlaylists(cleanedList)
        }
    }

    private fun persistPlayCounts(map: Map<Long, Int>) {
        _playCounts.value = map
        try {
            val obj = JSONObject()
            map.forEach { (id, count) -> obj.put(id.toString(), count) }
            prefs.edit().putString("play_counts_json", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistPlayDurations(map: Map<Long, Long>) {
        _playDurations.value = map
        try {
            val obj = JSONObject()
            map.forEach { (id, dur) -> obj.put(id.toString(), dur) }
            prefs.edit().putString("play_durations_json", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistSongMoods(map: Map<Long, String>) {
        _songMoods.value = map
        try {
            val obj = JSONObject()
            map.forEach { (id, mood) -> obj.put(id.toString(), mood) }
            prefs.edit().putString("song_moods_json", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setSongMood(songId: Long, mood: String?) {
        val current = _songMoods.value.toMutableMap()
        if (mood == null) {
            current.remove(songId)
        } else {
            current[songId] = mood
        }
        persistSongMoods(current)
    }

    override fun toggleFavorite(songId: Long) {
        val current = _favoriteSongIds.value
        val updated = if (current.contains(songId)) current - songId else current + songId
        persistFavorites(updated)
    }

    override fun incrementPlayCount(songId: Long) {
        val current = _playCounts.value.toMutableMap()
        current[songId] = (current[songId] ?: 0) + 1
        persistPlayCounts(current)
        recordSongPlayed(songId)
    }

    override fun recordListeningDuration(songId: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val current = _playDurations.value.toMutableMap()
        val updated = (current[songId] ?: 0L) + durationMs
        current[songId] = updated
        _playDurations.value = current

        val now = System.currentTimeMillis()
        if (now - lastDurationPersistTime > 4000L) {
            lastDurationPersistTime = now
            persistPlayDurations(current)
        }
    }

    override fun flushPlayDurations() {
        persistPlayDurations(_playDurations.value)
    }

    private fun persistCompletionRatios(map: Map<Long, Float>) {
        _completionRatios.value = map
        try {
            val obj = JSONObject()
            map.forEach { (id, ratio) -> obj.put(id.toString(), ratio.toDouble()) }
            prefs.edit().putString("completion_ratios_json", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistRapidSkipCounts(map: Map<Long, Int>) {
        _rapidSkipCounts.value = map
        try {
            val obj = JSONObject()
            map.forEach { (id, skips) -> obj.put(id.toString(), skips) }
            prefs.edit().putString("rapid_skip_counts_json", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun recordCompletionRatio(songId: Long, ratio: Float) {
        val clamped = ratio.coerceIn(0f, 1.5f)
        val current = _completionRatios.value.toMutableMap()
        val prev = current[songId]
        // Exponential moving average so repeated full listens converge cleanly toward 1.0
        current[songId] = if (prev == null) clamped else (prev * 0.45f + clamped * 0.55f)
        persistCompletionRatios(current)
    }

    override fun recordRapidSkip(songId: Long) {
        val current = _rapidSkipCounts.value.toMutableMap()
        current[songId] = (current[songId] ?: 0) + 1
        persistRapidSkipCounts(current)
    }

    override fun recordSongPlayed(songId: Long) {
        val updatedList = _recentlyPlayedSongIds.value.toMutableList()
        updatedList.remove(songId)
        updatedList.add(0, songId)
        if (updatedList.size > 200) {
            updatedList.removeAt(updatedList.size - 1)
        }
        persistRecentlyPlayed(updatedList)

        val now = System.currentTimeMillis()
        val updatedTimestamps = _lastPlayedTimestamps.value.toMutableMap()
        updatedTimestamps[songId] = now
        persistLastPlayedTimestamps(updatedTimestamps)
    }

    private fun persistLastPlayedTimestamps(map: Map<Long, Long>) {
        _lastPlayedTimestamps.value = map
        try {
            val obj = JSONObject()
            map.forEach { (id, ts) -> obj.put(id.toString(), ts) }
            prefs.edit().putString("last_played_timestamps_json", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistRecentlyPlayed(list: List<Long>) {
        _recentlyPlayedSongIds.value = list
        try {
            val array = JSONArray()
            list.forEach { array.put(it) }
            prefs.edit().putString("recently_played_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistExcludedFolders(set: Set<String>) {
        _excludedFolderPaths.value = set
        prefs.edit().putStringSet("excluded_folders", set).apply()
    }

    private fun persistExcludedSongs(set: Set<Long>) {
        _excludedSongIds.value = set
        prefs.edit().putStringSet("excluded_songs", set.map { it.toString() }.toSet()).apply()
    }

    private fun persistRestoredAutoFolders(set: Set<String>) {
        _restoredAutoFolders.value = set
        prefs.edit().putStringSet("restored_auto_hidden_folders", set).apply()
    }

    private fun persistRestoredAutoSongs(set: Set<Long>) {
        _restoredAutoSongIds.value = set
        prefs.edit().putStringSet("restored_auto_hidden_songs", set.map { it.toString() }.toSet()).apply()
    }

    override fun syncAutoHiddenItems(allSongs: List<Song>) {
        if (allSongs.isEmpty()) return
        val restoredFolders = _restoredAutoFolders.value
        val restoredSongs = _restoredAutoSongIds.value

        val currentExcludedFolders = _excludedFolderPaths.value.toMutableSet()
        val currentExcludedSongs = _excludedSongIds.value.toMutableSet()
        var foldersChanged = false
        var songsChanged = false

        for (song in allSongs) {
            val cleanFolderPath = song.folderPath.trimEnd('/')
            val isFolderRestored = restoredFolders.any { it.trimEnd('/').equals(cleanFolderPath, ignoreCase = true) }
            if (!isFolderRestored && AutoHideAudioFilter.isUnwantedVoiceOrCallFolder(song.folderPath, song.folderName)) {
                if (currentExcludedFolders.add(song.folderPath)) {
                    foldersChanged = true
                }
            }
            if (!restoredSongs.contains(song.id) && AutoHideAudioFilter.isUnwantedVoiceOrCallSong(song)) {
                if (currentExcludedSongs.add(song.id)) {
                    songsChanged = true
                }
            }
        }

        if (foldersChanged) {
            persistExcludedFolders(currentExcludedFolders)
        }
        if (songsChanged) {
            persistExcludedSongs(currentExcludedSongs)
        }
    }

    private fun persistMinDuration(minDuration: Long) {
        _minDurationMs.value = minDuration
        prefs.edit().putLong("min_duration_ms", minDuration).apply()
    }

    private fun persistPlaylists(list: List<Playlist>) {
        _playlists.value = list
        try {
            val array = JSONArray()
            list.forEach { p ->
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("createdAt", p.createdAt)
                if (p.customCoverUri != null) {
                    obj.put("customCoverUri", p.customCoverUri)
                }
                obj.put("colorGradientIndex", p.colorGradientIndex)
                val songsArr = JSONArray()
                p.songIds.forEach { id -> songsArr.put(id) }
                obj.put("songIds", songsArr)
                array.put(obj)
            }
            prefs.edit().putString("playlists_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistSavedQueues(list: List<SavedQueue>) {
        _savedQueues.value = list
        try {
            val array = JSONArray()
            list.forEach { q ->
                val obj = JSONObject()
                obj.put("id", q.id)
                obj.put("name", q.name)
                obj.put("createdAt", q.createdAt)
                val songsArr = JSONArray()
                q.songIds.forEach { id -> songsArr.put(id) }
                obj.put("songIds", songsArr)
                array.put(obj)
            }
            prefs.edit().putString("saved_queues_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val vaultRepository = VaultRepository(context, songDao, prefs)
    private val mediaRepository = MediaRepository(context, songDao, prefs, vaultRepository)

    override suspend fun fetchAudioFiles(): List<Song> =
        mediaRepository.fetchAudioFiles(_excludedFolderPaths.value, _minDurationMs.value, _manualFolderUris.value)

    override suspend fun syncMediaStore(): List<Song> =
        mediaRepository.syncMediaStore(_excludedFolderPaths.value, _minDurationMs.value, _manualFolderUris.value)

    override suspend fun syncMediaStoreIfChanged(currentSongs: List<Song>): Pair<Boolean, List<Song>> =
        mediaRepository.syncMediaStoreIfChanged(currentSongs, _excludedFolderPaths.value, _minDurationMs.value, _manualFolderUris.value)

    override suspend fun getAllDiscoveredFolders(): List<FolderInfo> =
        mediaRepository.getAllDiscoveredFolders(_excludedFolderPaths.value, _includeSystemHiddenInLibrary.value)

    override fun getAlbumArtUri(albumId: Long): Uri? =
        mediaRepository.getAlbumArtUri(albumId)

    override fun addManualFolder(uriString: String) {
        val current = _manualFolderUris.value.toMutableSet()
        current.add(uriString)
        _manualFolderUris.value = current
        prefs.edit().putStringSet("manual_folders", current).apply()
    }

    override fun removeManualFolder(uriString: String) {
        val current = _manualFolderUris.value.toMutableSet()
        current.remove(uriString)
        _manualFolderUris.value = current
        prefs.edit().putStringSet("manual_folders", current).apply()
    }

    override fun excludeDirectory(folderPath: String) {
        val cleanTarget = folderPath.trimEnd('/')
        val updatedRestoredFolders = _restoredAutoFolders.value.filterNot {
            val clean = it.trimEnd('/')
            clean.equals(cleanTarget, ignoreCase = true) || clean.startsWith("$cleanTarget/")
        }.toSet()
        persistRestoredAutoFolders(updatedRestoredFolders)

        val updated = _excludedFolderPaths.value.toMutableSet().apply { add(folderPath) }
        persistExcludedFolders(updated)
    }

    override fun restoreDirectory(folderPath: String) {
        val cleanTarget = folderPath.trimEnd('/')
        val updatedRestoredFolders = _restoredAutoFolders.value.toMutableSet().apply { add(folderPath) }
        persistRestoredAutoFolders(updatedRestoredFolders)

        val updated = _excludedFolderPaths.value.filterNot {
            val clean = it.trimEnd('/')
            clean == cleanTarget || clean.startsWith("$cleanTarget/")
        }.toSet()
        persistExcludedFolders(updated)
    }

    override fun restoreAllExcludedDirectories() {
        val updatedRestoredFolders = _restoredAutoFolders.value + _excludedFolderPaths.value
        persistRestoredAutoFolders(updatedRestoredFolders)
        persistExcludedFolders(emptySet())
    }

    override fun excludeSong(songId: Long) {
        if (_restoredAutoSongIds.value.contains(songId)) {
            persistRestoredAutoSongs(_restoredAutoSongIds.value - songId)
        }
        val updated = _excludedSongIds.value.toMutableSet().apply { add(songId) }
        persistExcludedSongs(updated)
    }

    override fun restoreSong(songId: Long) {
        persistRestoredAutoSongs(_restoredAutoSongIds.value + songId)
        val updated = _excludedSongIds.value.toMutableSet().apply { remove(songId) }
        persistExcludedSongs(updated)
    }

    override fun restoreAllExcludedSongs() {
        persistRestoredAutoSongs(_restoredAutoSongIds.value + _excludedSongIds.value)
        persistExcludedSongs(emptySet())
    }

    override fun setMinDurationFilter(durationMs: Long) {
        persistMinDuration(durationMs)
    }

    override suspend fun deleteSongPhysical(song: Song): Boolean =
        vaultRepository.deleteSongPhysical(song)

    override suspend fun moveSongPhysical(song: Song, targetFolderPath: String): Boolean =
        vaultRepository.moveSongPhysical(song, targetFolderPath)

    override suspend fun copySongPhysical(song: Song, targetFolderPath: String): Boolean =
        vaultRepository.copySongPhysical(song, targetFolderPath)

    override suspend fun moveSongsBatch(songs: List<Song>, targetFolderPath: String): Pair<Int, Int> =
        vaultRepository.moveSongsBatch(songs, targetFolderPath)

    override suspend fun copySongsBatch(songs: List<Song>, targetFolderPath: String): Pair<Int, Int> =
        vaultRepository.copySongsBatch(songs, targetFolderPath)

    override suspend fun createNewFolder(parentPath: String, folderName: String): Result<String> =
        vaultRepository.createNewFolder(parentPath, folderName)

    override suspend fun renameFolderPhysical(oldPath: String, newName: String): Result<String> =
        vaultRepository.renameFolderPhysical(oldPath, newName, _excludedFolderPaths.value) { updated ->
            persistExcludedFolders(updated)
        }

    override suspend fun deleteFolderPhysical(folderPath: String): Boolean =
        vaultRepository.deleteFolderPhysical(folderPath, _excludedFolderPaths.value) { updated ->
            persistExcludedFolders(updated)
        }

    override fun addTrackedFolder(folderPath: String) {
        vaultRepository.addTrackedFolder(folderPath)
    }

    override fun setAccentColor(color: Long) {
        _accentColor.value = color
        prefs.edit().putLong("setting_accent_color", color).apply()
    }

    override fun setCustomBackgroundUri(uri: String?) {
        if (uri == null) {
            try {
                val dir = File(context.filesDir, "custom_backgrounds")
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _customBackgroundUri.value = null
            prefs.edit().remove("setting_bg_uri").apply()
        } else {
            val internalDir = File(context.filesDir, "custom_backgrounds").apply { mkdirs() }
            val savedInternalUri: String = try {
                val parsed = Uri.parse(uri)
                if (parsed.scheme == "file" && parsed.path?.startsWith(internalDir.absolutePath) == true) {
                    uri
                } else {
                    internalDir.listFiles()?.forEach { it.delete() }
                    val destFile = File(internalDir, "bg_wallpaper_${System.currentTimeMillis()}.jpg")
                    val inputStream = context.contentResolver.openInputStream(parsed)
                    if (inputStream != null) {
                        inputStream.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Uri.fromFile(destFile).toString()
                    } else {
                        uri
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uri
            }

            _customBackgroundUri.value = savedInternalUri
            prefs.edit().putString("setting_bg_uri", savedInternalUri).apply()
        }
    }

    override fun setBackgroundMode(mode: String) {
        _backgroundMode.value = mode
        prefs.edit().putString("setting_bg_mode", mode).apply()
    }

    override fun setBgImageBlur(blur: Float) {
        _bgImageBlur.value = blur
        prefs.edit().putFloat("setting_bg_image_blur", blur).apply()
    }

    override fun setBgImageDim(dim: Float) {
        _bgImageDim.value = dim
        prefs.edit().putFloat("setting_bg_image_dim", dim).apply()
    }

    override fun setSolidBgColor(color: Long) {
        _solidBgColor.value = color
        prefs.edit().putLong("setting_solid_bg_color", color).apply()
    }

    override fun setSolidBgOpacity(opacity: Float) {
        _solidBgOpacity.value = opacity
        prefs.edit().putFloat("setting_solid_bg_opacity", opacity).apply()
    }

    override fun setGradientPresetIndex(index: Int) {
        _gradientPresetIndex.value = index
        prefs.edit().putInt("setting_gradient_preset", index).apply()
    }

    override fun setCustomGradientColors(colors: List<Long>) {
        _customGradientColors.value = colors
        prefs.edit().putString("setting_custom_gradient_colors", colors.joinToString(",") { it.toString() }).apply()
    }

    override fun setGradientIntensity(intensity: Float) {
        _gradientIntensity.value = intensity
        prefs.edit().putFloat("setting_gradient_intensity", intensity).apply()
    }

    
    override fun setNowPlayingLayout(layout: String) {
        _nowPlayingLayout.value = layout
        prefs.edit().putString("setting_now_playing_layout", layout).apply()
    }
    
    override fun setCornerRadiusScale(scale: Float) {
        _cornerRadiusScale.value = scale
        prefs.edit().putFloat("setting_corner_radius", scale).apply()
    }
    
    override fun setFontStyle(style: String) {
        _fontStyle.value = style
        prefs.edit().putString("setting_font_style", style).apply()
    }
    
    override fun setMotionLevel(level: String) {
        _motionLevel.value = level
        prefs.edit().putString("setting_motion_level", level).apply()
    }
override fun setGradientBlur(blur: Float)
 {
        _gradientBlur.value = blur
        prefs.edit().putFloat("setting_gradient_blur", blur).apply()
    }

    override fun setShuffleMode(mode: ShuffleMode) {
        _shuffleMode.value = mode
        _isShuffleEnabled.value = (mode != ShuffleMode.OFF)
        prefs.edit()
            .putString("setting_shuffle_mode_v2", mode.name)
            .putBoolean("setting_shuffle_enabled", mode != ShuffleMode.OFF)
            .apply()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        val targetMode = if (enabled) ShuffleMode.STANDARD else ShuffleMode.OFF
        setShuffleMode(targetMode)
    }

    override fun setRepeatMode(mode: String) {
        _repeatMode.value = mode
        prefs.edit().putString("setting_repeat_mode", mode).apply()
    }

    override fun setAppThemeName(themeName: String) {
        _appThemeName.value = themeName
        prefs.edit().putString("setting_app_theme", themeName).apply()
    }

    override fun setKariModeEnabled(enabled: Boolean) {
        _isKariModeEnabled.value = enabled
        prefs.edit().putBoolean("setting_kari_mode", enabled).apply()
    }

    override fun setKariBlur(blur: Float) {
        _kariBlur.value = blur
        prefs.edit().putFloat("setting_kari_blur", blur).apply()
    }

    override fun setKariOpacity(opacity: Float) {
        _kariOpacity.value = opacity
        prefs.edit().putFloat("setting_kari_opacity", opacity).apply()
    }


    override fun setShakeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_shake_enabled", enabled).apply()
        _isShakeEnabled.value = enabled
    }
    override fun setTwoFingerTapEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_two_finger_tap_enabled", enabled).apply()
        _isTwoFingerTapEnabled.value = enabled
    }
    override fun setTwoFingerTapAction(action: String) {
        prefs.edit().putString("setting_two_finger_tap_action", action).apply()
        _twoFingerTapAction.value = action
    }
    override fun setThreeFingerSwipeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_three_finger_swipe_enabled", enabled).apply()
        _isThreeFingerSwipeEnabled.value = enabled
    }
    override fun setThreeFingerSwipeAction(action: String) {
        prefs.edit().putString("setting_three_finger_swipe_action", action).apply()
        _threeFingerSwipeAction.value = action
    }
    override fun setVolumeEdgeSwipeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_volume_edge_swipe_enabled", enabled).apply()
        _isVolumeEdgeSwipeEnabled.value = enabled
    }
    override fun setLongPressDragSeekEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_long_press_drag_seek_enabled", enabled).apply()
        _isLongPressDragSeekEnabled.value = enabled
    }

    override fun setPlayTogetherEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_play_together", enabled).apply()
        _playTogetherEnabled.value = enabled
    }

    override fun setPlayTogetherFadeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_play_together_fade", enabled).apply()
        _playTogetherFadeEnabled.value = enabled
    }

    override fun setOneFingerSwipeBackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_one_finger_swipe_back_enabled", enabled).apply()
        _isOneFingerSwipeBackEnabled.value = enabled
    }

    override fun setTwoFingerSwipeHomeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_two_finger_swipe_home_enabled", enabled).apply()
        _isTwoFingerSwipeHomeEnabled.value = enabled
    }

    override fun setMiniPlayerGesturesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_mini_player_gestures_enabled", enabled).apply()
        _isMiniPlayerGesturesEnabled.value = enabled
    }

    override fun setFlipToPauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_flip_to_pause_enabled", enabled).apply()
        _isFlipToPauseEnabled.value = enabled
    }
    override fun setFlipToPauseAction(action: String) {
        prefs.edit().putString("setting_flip_to_pause_action", action).apply()
        _flipToPauseAction.value = action
    }
    override fun setWristTwistEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_wrist_twist_enabled", enabled).apply()
        _isWristTwistEnabled.value = enabled
    }
    override fun setWristTwistAction(action: String) {
        prefs.edit().putString("setting_wrist_twist_action", action).apply()
        _wristTwistAction.value = action
    }
    override fun setPocketModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_pocket_mode_enabled", enabled).apply()
        _isPocketModeEnabled.value = enabled
    }
    override fun setPocketModeAction(action: String) {
        prefs.edit().putString("setting_pocket_mode_action", action).apply()
        _pocketModeAction.value = action
    }
    override fun setShakeAction(action: String) {
        prefs.edit().putString("setting_shake_action", action).apply()
        _shakeAction.value = action
    }
    override fun setTiltToFastForwardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_tilt_to_fast_forward_enabled", enabled).apply()
        _isTiltToFastForwardEnabled.value = enabled
    }
    
    override fun setAirWaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_air_wave_enabled", enabled).apply()
        _isAirWaveEnabled.value = enabled
    }
    
    override fun setAirWaveAction(action: String) {
        prefs.edit().putString("setting_air_wave_action", action).apply()
        _airWaveAction.value = action
    }
    
    override fun setPickUpToPlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("setting_pick_up_to_play_enabled", enabled).apply()
        _isPickUpToPlayEnabled.value = enabled
    }
    override fun setCrossfadeEnabled(enabled: Boolean) {
        _isCrossfadeEnabled.value = enabled
        prefs.edit().putBoolean("setting_crossfade_enabled", enabled).apply()
    }

    override fun setAutoResumeOnPlugEnabled(enabled: Boolean) {
        _isAutoResumeOnPlugEnabled.value = enabled
        prefs.edit().putBoolean("setting_auto_resume_on_plug", enabled).apply()
    }

    override fun setAutoPlayOnBluetoothEnabled(enabled: Boolean) {
        _isAutoPlayOnBluetoothEnabled.value = enabled
        prefs.edit().putBoolean("setting_auto_play_on_bluetooth", enabled).apply()
    }

    override fun setSingleClickAction(action: String) {
        _singleClickAction.value = action
        prefs.edit().putString("setting_single_click_action", action).apply()
    }

    override fun setDoubleClickAction(action: String) {
        _doubleClickAction.value = action
        prefs.edit().putString("setting_double_click_action", action).apply()
    }

    override fun setTripleClickAction(action: String) {
        _tripleClickAction.value = action
        prefs.edit().putString("setting_triple_click_action", action).apply()
    }

    override fun setHearingSafetyEnabled(enabled: Boolean) {
        _isHearingSafetyEnabled.value = enabled
        prefs.edit().putBoolean("setting_hearing_safety", enabled).apply()
    }

    override fun setGaplessPlaybackEnabled(enabled: Boolean) {
        _isGaplessPlaybackEnabled.value = enabled
        prefs.edit().putBoolean("setting_gapless_enabled", enabled).apply()
    }

    override fun setResumeLastPositionEnabled(enabled: Boolean) {
        _isResumeLastPositionEnabled.value = enabled
        prefs.edit().putBoolean("setting_resume_last_position", enabled).apply()
    }

    override fun setCrossfadeDuration(seconds: Int) {
        val safe = seconds.coerceIn(1, 5)
        _crossfadeDurationSeconds.value = safe
        prefs.edit().putInt("setting_crossfade_duration", safe).apply()
    }

    override fun setFirstLaunch(isFirst: Boolean) {
        _isFirstLaunch.value = isFirst
        prefs.edit().putBoolean("setting_is_first_launch", isFirst).apply()
    }

    override fun setMohModeActivated(activated: Boolean) {
        _isMohModeActivated.value = activated
        prefs.edit().putBoolean("setting_moh_mode_activated", activated).apply()
        context.getSharedPreferences("soniclet_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("setting_moh_mode_activated", activated).apply()
        if (activated) {
            com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.syncSpecialCelebration(context)
            com.kexora.soniclet.worker.SpecialDateNotificationWorker.schedule(context)
        } else {
            com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler.cancelAlarm(context)
            com.kexora.soniclet.worker.SpecialDateNotificationWorker.cancel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            for (i in 0..20) {
                nm?.cancel(com.kexora.soniclet.easteregg.MuhLoveNotificationReceiver.NOTIFICATION_ID + i)
            }
        }
    }

    override fun createSavedQueue(name: String, songIds: List<Long>): SavedQueue {
        val savedQueue = SavedQueue(
            id = "sq_${System.currentTimeMillis()}",
            name = name.ifBlank { "Saved Queue" },
            songIds = songIds
        )
        val updated = _savedQueues.value + savedQueue
        persistSavedQueues(updated)
        return savedQueue
    }

    override fun upsertSavedQueue(name: String, songIds: List<Long>): SavedQueue {
        val cleanName = name.ifBlank { "Saved Queue" }.trim()
        val currentList = _savedQueues.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        val resultQueue: SavedQueue
        if (existingIndex != -1) {
            val existing = currentList[existingIndex]
            resultQueue = existing.copy(songIds = songIds, createdAt = System.currentTimeMillis())
            currentList[existingIndex] = resultQueue
        } else {
            resultQueue = SavedQueue(
                id = "sq_${System.currentTimeMillis()}",
                name = cleanName,
                songIds = songIds,
                createdAt = System.currentTimeMillis()
            )
            currentList.add(0, resultQueue)
        }
        persistSavedQueues(currentList)
        return resultQueue
    }

    override fun deleteSavedQueue(queueId: String) {
        val updated = _savedQueues.value.filterNot { it.id == queueId }
        persistSavedQueues(updated)
    }

    override fun createPlaylist(name: String, initialSongIds: List<Long>): Playlist {
        val playlist = Playlist(
            id = "pl_${System.currentTimeMillis()}",
            name = name.ifBlank { "My Playlist" },
            songIds = initialSongIds
        )
        val updated = _playlists.value + playlist
        persistPlaylists(updated)
        return playlist
    }

    override fun upsertPlaylistByName(name: String, songIds: List<Long>): Playlist {
        val cleanName = name.ifBlank { "My Playlist" }.trim()
        val distinctSongIds = songIds.distinct()
        val currentList = _playlists.value.toMutableList()
        val existingIdx = currentList.indexOfFirst { it.name.trim().equals(cleanName, ignoreCase = true) }
        val targetPlaylist: Playlist
        if (existingIdx != -1) {
            val existing = currentList[existingIdx]
            targetPlaylist = existing.copy(
                name = cleanName,
                songIds = distinctSongIds
            )
            currentList[existingIdx] = targetPlaylist
        } else {
            targetPlaylist = Playlist(
                id = "pl_${System.currentTimeMillis()}",
                name = cleanName,
                songIds = distinctSongIds,
                createdAt = System.currentTimeMillis()
            )
            currentList.add(0, targetPlaylist)
        }
        persistPlaylists(currentList)
        return targetPlaylist
    }

    override fun renamePlaylist(playlistId: String, newName: String) {
        val updated = _playlists.value.map { p ->
            if (p.id == playlistId) p.copy(name = newName.ifBlank { p.name }) else p
        }
        persistPlaylists(updated)
    }

    override fun deletePlaylist(playlistId: String) {
        try {
            val coverDir = File(context.filesDir, "playlist_covers")
            if (coverDir.exists()) {
                coverDir.listFiles { file -> file.name.startsWith("cover_${playlistId}") }?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val updated = _playlists.value.filterNot { it.id == playlistId }
        persistPlaylists(updated)
    }

    override fun addSongsToPlaylist(playlistId: String, songIds: List<Long>) {
        val updated = _playlists.value.map { p ->
            if (p.id == playlistId) {
                val combined = (p.songIds + songIds).distinct()
                p.copy(songIds = combined)
            } else p
        }
        persistPlaylists(updated)
    }

    override fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val updated = _playlists.value.map { p ->
            if (p.id == playlistId) {
                p.copy(songIds = p.songIds.filterNot { it == songId })
            } else p
        }
        persistPlaylists(updated)
    }

    override fun reorderPlaylistSongs(playlistId: String, newSongIds: List<Long>) {
        val updated = _playlists.value.map { p ->
            if (p.id == playlistId) {
                p.copy(songIds = newSongIds)
            } else p
        }
        persistPlaylists(updated)
    }

    override fun updatePlaylistCover(playlistId: String, coverUri: String?) {
        val finalUri: String? = if (coverUri == null) {
            try {
                val coverDir = File(context.filesDir, "playlist_covers")
                if (coverDir.exists()) {
                    coverDir.listFiles { file -> file.name.startsWith("cover_${playlistId}") }?.forEach { it.delete() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        } else {
            val coverDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
            try {
                val parsed = Uri.parse(coverUri)
                if (parsed.scheme == "file" && parsed.path?.startsWith(coverDir.absolutePath) == true) {
                    coverUri
                } else {
                    coverDir.listFiles { file -> file.name.startsWith("cover_${playlistId}") }?.forEach { it.delete() }
                    val destFile = File(coverDir, "cover_${playlistId}_${System.currentTimeMillis()}.jpg")
                    val inputStream = context.contentResolver.openInputStream(parsed)
                    if (inputStream != null) {
                        inputStream.use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Uri.fromFile(destFile).toString()
                    } else {
                        coverUri
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                coverUri
            }
        }

        val updated = _playlists.value.map { p ->
            if (p.id == playlistId) {
                p.copy(customCoverUri = finalUri)
            } else p
        }
        persistPlaylists(updated)
    }

    override fun updatePlaylistGradient(playlistId: String, gradientIndex: Int) {
        val updated = _playlists.value.map { p ->
            if (p.id == playlistId) {
                p.copy(colorGradientIndex = gradientIndex)
            } else p
        }
        persistPlaylists(updated)
    }

    override suspend fun updateSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String,
        newAlbumArtUri: android.net.Uri?
    ): Boolean = mediaRepository.updateSongMetadata(
        songId = songId,
        newTitle = newTitle,
        newArtist = newArtist,
        newAlbum = newAlbum,
        newYear = newYear,
        newGenre = newGenre,
        newAlbumArtUri = newAlbumArtUri
    )

    override fun savePlaybackPosition(songId: Long, positionMs: Long) {
        if (positionMs <= 1000L) {
            prefs.edit().remove("playback_pos_$songId").apply()
            val current = _playbackPositions.value.toMutableMap()
            current.remove(songId)
            _playbackPositions.value = current
        } else {
            prefs.edit().putLong("playback_pos_$songId", positionMs).apply()
            val current = _playbackPositions.value.toMutableMap()
            current[songId] = positionMs
            _playbackPositions.value = current
        }
    }

    override fun getSavedPlaybackPosition(songId: Long): Long {
        return _playbackPositions.value[songId] ?: prefs.getLong("playback_pos_$songId", 0L)
    }

    private fun persistTrackFingerprintsLocked() {
        try {
            val root = JSONObject()
            trackFingerprints.forEach { (id, fp) ->
                val fpObj = JSONObject().apply {
                    put("fileName", fp.fileName)
                    put("title", fp.title)
                    put("artist", fp.artist)
                    put("duration", fp.duration)
                    put("size", fp.size)
                }
                root.put(id.toString(), fpObj)
            }
            prefs.edit().putString("song_fingerprints_json", root.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun recordTrackFingerprint(song: Song) {
        val fp = song.toTrackFingerprint(includeFileSize = true)
        synchronized(fingerprintLock) {
            if (trackFingerprints[song.id] != fp) {
                trackFingerprints[song.id] = fp
                persistTrackFingerprintsLocked()
            }
        }
    }

    override fun recordTrackFingerprints(songs: List<Song>) {
        if (songs.isEmpty()) return
        var changed = false
        synchronized(fingerprintLock) {
            for (song in songs) {
                val existing = trackFingerprints[song.id]
                val resolvedName = song.filePath.substringAfterLast('/').trim()
                val needsRefresh = existing == null ||
                    existing.fileName != resolvedName ||
                    existing.title != song.title.trim() ||
                    existing.artist != song.artist.trim() ||
                    existing.duration != song.duration ||
                    (existing.size <= 0L && song.filePath.isNotBlank())
                if (needsRefresh) {
                    val fp = song.toTrackFingerprint(includeFileSize = true)
                    if (existing != fp) {
                        trackFingerprints[song.id] = fp
                        changed = true
                    }
                }
            }
            if (changed) {
                persistTrackFingerprintsLocked()
            }
        }
    }

    private fun normalizeMeta(value: String): String {
        val trimmed = value.trim().lowercase(java.util.Locale.ROOT)
        return trimmed.replace(Regex("\\s+"), " ")
    }

    private fun isMeaningfulArtist(normArtist: String): Boolean {
        return normArtist.isNotBlank() &&
            normArtist != "<unknown>" &&
            normArtist != "unknown" &&
            normArtist != "unknown artist"
    }

    private fun fileNameStem(fileName: String): String {
        val clean = normalizeMeta(fileName)
        val dotIdx = clean.lastIndexOf('.')
        return if (dotIdx > 0) clean.substring(0, dotIdx) else clean
    }

    override fun reconcileMovedOrDeletedSongs(currentSongs: List<Song>): Map<Long, Long> {
        // Safety guard: never purge user data on an empty or transient/unpermitted scan
        if (currentSongs.isEmpty()) return emptyMap()

        val currentById = HashMap<Long, Song>(currentSongs.size * 2)
        for (song in currentSongs) {
            currentById[song.id] = song
        }

        val currentPlaylists = _playlists.value
        val currentFavorites = _favoriteSongIds.value
        val currentRecentlyPlayed = _recentlyPlayedSongIds.value
        val currentPlayCounts = _playCounts.value
        val currentPlayDurations = _playDurations.value
        val currentCompletionRatios = _completionRatios.value
        val currentRapidSkipCounts = _rapidSkipCounts.value
        val currentLastPlayed = _lastPlayedTimestamps.value
        val currentSongMoods = _songMoods.value
        val currentSavedQueues = _savedQueues.value
        val currentPlaybackPositions = _playbackPositions.value

        val allTrackedIds = LinkedHashSet<Long>()
        currentPlaylists.forEach { allTrackedIds.addAll(it.songIds) }
        allTrackedIds.addAll(currentFavorites)
        allTrackedIds.addAll(currentRecentlyPlayed)
        allTrackedIds.addAll(currentPlayCounts.keys)
        allTrackedIds.addAll(currentPlayDurations.keys)
        allTrackedIds.addAll(currentCompletionRatios.keys)
        allTrackedIds.addAll(currentRapidSkipCounts.keys)
        allTrackedIds.addAll(currentLastPlayed.keys)
        allTrackedIds.addAll(currentSongMoods.keys)
        currentSavedQueues.forEach { allTrackedIds.addAll(it.songIds) }
        allTrackedIds.addAll(currentPlaybackPositions.keys)

        // Backfill / refresh fingerprints for all valid tracked IDs so future moves are always recoverable
        var fingerprintsDirty = false
        synchronized(fingerprintLock) {
            for (id in allTrackedIds) {
                val song = currentById[id] ?: continue
                val existing = trackFingerprints[id]
                val fileName = song.filePath.substringAfterLast('/').trim()
                val needsUpdate = existing == null ||
                    existing.fileName != fileName ||
                    existing.title != song.title.trim() ||
                    existing.artist != song.artist.trim() ||
                    existing.duration != song.duration ||
                    (existing.size <= 0L && song.filePath.isNotBlank())
                if (needsUpdate) {
                    val fresh = song.toTrackFingerprint(includeFileSize = true)
                    if (existing != fresh) {
                        trackFingerprints[id] = fresh
                        fingerprintsDirty = true
                    }
                }
            }
        }

        val staleIds = allTrackedIds.filter { !currentById.containsKey(it) }
        if (staleIds.isEmpty()) {
            if (fingerprintsDirty) {
                synchronized(fingerprintLock) { persistTrackFingerprintsLocked() }
            }
            return emptyMap()
        }

        data class CandidateMeta(
            val song: Song,
            val normFileName: String,
            val normStem: String,
            val normTitle: String,
            val normArtist: String,
            val duration: Long
        ) {
            val lazySize: Long by lazy {
                if (song.filePath.isNotBlank()) {
                    try {
                        val f = File(song.filePath)
                        if (f.exists()) f.length() else 0L
                    } catch (_: Exception) {
                        0L
                    }
                } else 0L
            }
        }

        val candidates = currentSongs.map { song ->
            val rawName = song.filePath.substringAfterLast('/').trim()
            CandidateMeta(
                song = song,
                normFileName = normalizeMeta(rawName),
                normStem = fileNameStem(rawName),
                normTitle = normalizeMeta(song.title),
                normArtist = normalizeMeta(song.artist),
                duration = song.duration
            )
        }

        val fingerprintSnapshot: Map<Long, TrackFingerprint> = synchronized(fingerprintLock) {
            HashMap(trackFingerprints)
        }

        val durationToleranceMs = 1500L
        val remapped = HashMap<Long, Long>()
        val deadIds = HashSet<Long>()

        for (staleId in staleIds) {
            val fp = fingerprintSnapshot[staleId]
            if (fp == null) {
                deadIds.add(staleId)
                continue
            }

            val fpFileName = normalizeMeta(fp.fileName)
            val fpStem = fileNameStem(fp.fileName)
            val fpTitle = normalizeMeta(fp.title)
            val fpArtist = normalizeMeta(fp.artist)
            val fpHasArtist = isMeaningfulArtist(fpArtist)

            var bestMatch: Song? = null
            var bestScore = 0

            for (cand in candidates) {
                val durationCompatible = if (fp.duration > 0L && cand.duration > 0L) {
                    kotlin.math.abs(fp.duration - cand.duration) <= durationToleranceMs
                } else {
                    true
                }
                if (!durationCompatible) continue

                var score = 0
                val exactFileNameMatch = fpFileName.isNotBlank() && cand.normFileName == fpFileName
                val exactStemMatch = fpStem.isNotBlank() && cand.normStem == fpStem
                val exactTitleMatch = fpTitle.isNotBlank() && cand.normTitle == fpTitle
                val exactArtistMatch = fpHasArtist && cand.normArtist == fpArtist

                if (exactFileNameMatch) score += 120
                else if (exactStemMatch) score += 70

                if (exactTitleMatch) score += 70
                if (exactArtistMatch) score += 50

                // Check exact file size when we have a plausible match or saved size to catch renamed files
                if (fp.size > 0L && (score >= 40 || (fp.duration > 0L && cand.duration > 0L && kotlin.math.abs(fp.duration - cand.duration) <= 500L))) {
                    if (cand.lazySize > 0L && cand.lazySize == fp.size) {
                        score += 110
                    }
                }

                // Exact duration tie-breaker bonus
                if (score >= 100 && fp.duration > 0L && cand.duration > 0L) {
                    val diff = kotlin.math.abs(fp.duration - cand.duration)
                    if (diff <= 100L) score += 20
                    else if (diff <= 500L) score += 10
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = cand.song
                }
            }

            // Require strong confidence (>= 110: e.g. exact fileName+duration, title+artist+duration, or exact size+duration)
            // Or unique title+duration match when artist metadata is absent
            val resolvedMatch = if (bestMatch != null && bestScore >= 110) {
                bestMatch
            } else if (bestMatch != null && bestScore >= 70 && !fpHasArtist && fpTitle.isNotBlank()) {
                val titleMatches = candidates.count { cand ->
                    cand.normTitle == fpTitle &&
                        (fp.duration <= 0L || cand.duration <= 0L || kotlin.math.abs(fp.duration - cand.duration) <= durationToleranceMs)
                }
                if (titleMatches == 1) bestMatch else null
            } else {
                null
            }

            if (resolvedMatch != null) {
                remapped[staleId] = resolvedMatch.id
            } else {
                deadIds.add(staleId)
            }
        }

        // Helper to resolve an ID list preserving order and deduplicating
        fun healIdList(ids: List<Long>): List<Long> {
            val out = ArrayList<Long>(ids.size)
            val seen = HashSet<Long>(ids.size * 2)
            for (id in ids) {
                val resolved = remapped[id] ?: id
                if (currentById.containsKey(resolved) && seen.add(resolved)) {
                    out.add(resolved)
                }
            }
            return out
        }

        // 1. Playlists
        val updatedPlaylists = currentPlaylists.map { playlist ->
            val healedIds = healIdList(playlist.songIds)
            if (healedIds != playlist.songIds) playlist.copy(songIds = healedIds) else playlist
        }
        if (updatedPlaylists != currentPlaylists) {
            persistPlaylists(updatedPlaylists)
        }

        // 2. Favorites
        val updatedFavorites = LinkedHashSet<Long>(currentFavorites.size)
        for (id in currentFavorites) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved)) {
                updatedFavorites.add(resolved)
            }
        }
        if (updatedFavorites != currentFavorites) {
            persistFavorites(updatedFavorites)
        }

        // 3. Recently Played
        val updatedRecent = healIdList(currentRecentlyPlayed)
        if (updatedRecent != currentRecentlyPlayed) {
            persistRecentlyPlayed(updatedRecent)
        }

        // 4. Play Counts (merge counts if old & new IDs both exist)
        val updatedPlayCounts = LinkedHashMap<Long, Int>(currentPlayCounts.size)
        for ((id, count) in currentPlayCounts) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved)) {
                updatedPlayCounts[resolved] = (updatedPlayCounts[resolved] ?: 0) + count
            }
        }
        if (updatedPlayCounts != currentPlayCounts) {
            persistPlayCounts(updatedPlayCounts)
        }

        // 5. Play Durations (merge durations if old & new IDs both exist)
        val updatedPlayDurations = LinkedHashMap<Long, Long>(currentPlayDurations.size)
        for ((id, dur) in currentPlayDurations) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved)) {
                updatedPlayDurations[resolved] = (updatedPlayDurations[resolved] ?: 0L) + dur
            }
        }
        if (updatedPlayDurations != currentPlayDurations) {
            persistPlayDurations(updatedPlayDurations)
        }

        // 5b. Completion Ratios (keep max completion ratio on merge)
        val updatedCompletionRatios = LinkedHashMap<Long, Float>(currentCompletionRatios.size)
        for ((id, ratio) in currentCompletionRatios) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved)) {
                val prev = updatedCompletionRatios[resolved] ?: 0f
                updatedCompletionRatios[resolved] = maxOf(prev, ratio)
            }
        }
        if (updatedCompletionRatios != currentCompletionRatios) {
            persistCompletionRatios(updatedCompletionRatios)
        }

        // 5c. Rapid Skip Counts (merge counts)
        val updatedRapidSkips = LinkedHashMap<Long, Int>(currentRapidSkipCounts.size)
        for ((id, skips) in currentRapidSkipCounts) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved)) {
                updatedRapidSkips[resolved] = (updatedRapidSkips[resolved] ?: 0) + skips
            }
        }
        if (updatedRapidSkips != currentRapidSkipCounts) {
            persistRapidSkipCounts(updatedRapidSkips)
        }

        // 6. Last Played Timestamps (keep latest timestamp)
        val updatedLastPlayed = LinkedHashMap<Long, Long>(currentLastPlayed.size)
        for ((id, ts) in currentLastPlayed) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved)) {
                val prev = updatedLastPlayed[resolved] ?: 0L
                updatedLastPlayed[resolved] = maxOf(prev, ts)
            }
        }
        if (updatedLastPlayed != currentLastPlayed) {
            persistLastPlayedTimestamps(updatedLastPlayed)
        }

        // 7. Song Moods
        val updatedMoods = LinkedHashMap<Long, String>(currentSongMoods.size)
        for ((id, mood) in currentSongMoods) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved) && !updatedMoods.containsKey(resolved)) {
                updatedMoods[resolved] = mood
            }
        }
        if (updatedMoods != currentSongMoods) {
            persistSongMoods(updatedMoods)
        }

        // 8. Saved Queues
        val updatedSavedQueues = currentSavedQueues.map { queue ->
            val healedIds = healIdList(queue.songIds)
            if (healedIds != queue.songIds) queue.copy(songIds = healedIds) else queue
        }
        if (updatedSavedQueues != currentSavedQueues) {
            persistSavedQueues(updatedSavedQueues)
        }

        // 9. Playback Positions
        val updatedPositions = LinkedHashMap<Long, Long>(currentPlaybackPositions.size)
        var positionsChanged = false
        val posEditor = prefs.edit()
        for ((id, pos) in currentPlaybackPositions) {
            val resolved = remapped[id] ?: id
            if (currentById.containsKey(resolved)) {
                val existingPos = updatedPositions[resolved] ?: 0L
                val mergedPos = maxOf(existingPos, pos)
                updatedPositions[resolved] = mergedPos
                if (resolved != id) {
                    posEditor.remove("playback_pos_$id")
                    posEditor.putLong("playback_pos_$resolved", mergedPos)
                    positionsChanged = true
                }
            } else {
                posEditor.remove("playback_pos_$id")
                positionsChanged = true
            }
        }
        if (positionsChanged) {
            posEditor.apply()
            _playbackPositions.value = updatedPositions
        }

        // 10. Update Track Fingerprints map (promote remapped entries & prune deleted ones)
        synchronized(fingerprintLock) {
            for ((oldId, newId) in remapped) {
                trackFingerprints.remove(oldId)
                currentById[newId]?.let { newSong ->
                    trackFingerprints[newId] = newSong.toTrackFingerprint(includeFileSize = true)
                }
                fingerprintsDirty = true
            }
            for (deadId in deadIds) {
                if (trackFingerprints.remove(deadId) != null) {
                    fingerprintsDirty = true
                }
            }
            if (fingerprintsDirty) {
                persistTrackFingerprintsLocked()
            }
        }

        return remapped
    }
}

/**
 * Fast MediaStore audio & video queries, metadata persistence, and folder mapping.
 */
internal class MediaRepository(
    private val context: Context,
    private val songDao: com.kexora.soniclet.database.SongDao,
    private val prefs: android.content.SharedPreferences,
    private val vaultRepository: VaultRepository
) {
    private val contentResolver get() = context.contentResolver

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        return try {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        } catch (e: Exception) {
            null
        }
    }

    fun extractFolderInfo(filePath: String?, relativePath: String?): Pair<String, String> {
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            val parent = file.parentFile
            if (parent != null) {
                val folderName = parent.name.ifBlank { "Internal Storage" }
                return Pair(parent.absolutePath, folderName)
            }
        }
        if (!relativePath.isNullOrBlank()) {
            val cleanPath = relativePath.trimEnd('/')
            val folderName = cleanPath.substringAfterLast('/', cleanPath).ifBlank { "Music" }
            return Pair("/storage/emulated/0/$cleanPath", folderName)
        }
        return Pair("/storage/emulated/0/Music", "Music")
    }

    private fun isSupportedAudioFormat(mimeType: String): Boolean {
        val lowerMime = mimeType.lowercase()
        return lowerMime.contains("mp3") ||
            lowerMime.contains("aac") ||
            lowerMime.contains("flac") ||
            lowerMime.contains("wav") ||
            lowerMime.contains("mpeg") ||
            lowerMime.contains("ogg") ||
            lowerMime.contains("audio/")
    }

    fun scanAllSongsInternal(
        excludedFolderPaths: Set<String>,
        minDurationMs: Long,
        manualFolderUris: Set<String>
    ): List<Song> {
        val songsList = mutableListOf<Song>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }

        val projection = projectionList.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMs"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val rawTitle = cursor.getString(titleColumn) ?: ""
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val mimeType = if (mimeTypeColumn != -1) cursor.getString(mimeTypeColumn) else null
                    val filePath = if (dataColumn != -1) cursor.getString(dataColumn) else null
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn) else 0L
                    val relativePath = if (relativePathColumn != -1) cursor.getString(relativePathColumn) else null

                    val resolvedTitle = cleanSongTitle(rawTitle, filePath ?: "")
                    val folderInfo = extractFolderInfo(filePath, relativePath)

                    if (mimeType == null || isSupportedAudioFormat(mimeType)) {
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val customArt = com.kexora.soniclet.utils.CustomArtworkManager.getCustomArtworkUri(context, id)
                        val albumArtUri = customArt ?: getAlbumArtUri(albumId)

                        val cleanArtist = if (artist.isBlank() || artist.trim().equals("<unknown>", ignoreCase = true) || artist.trim().equals("unknown", ignoreCase = true)) {
                            "Unknown Artist"
                        } else {
                            artist.trim()
                        }
                        val cleanAlbum = if (album.isBlank() || album.trim().equals("<unknown>", ignoreCase = true) || album.trim().equals("unknown", ignoreCase = true)) {
                            "Unknown Album"
                        } else {
                            album.trim()
                        }

                        songsList.add(
                            Song(
                                id = id,
                                title = resolvedTitle,
                                artist = cleanArtist,
                                album = cleanAlbum,
                                duration = duration,
                                contentUri = contentUri,
                                albumArtUri = albumArtUri,
                                folderPath = folderInfo.first,
                                folderName = folderInfo.second,
                                dateAdded = dateAdded,
                                filePath = filePath ?: ""
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (manualFolderUris.isNotEmpty()) {
            val existingSignatures = songsList.map {
                "${it.title.trim().lowercase()}_${it.artist.trim().lowercase()}_${it.duration / 1000}"
            }.toMutableSet()

            for (uriString in manualFolderUris) {
                try {
                    val uri = Uri.parse(uriString)
                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        vaultRepository.scanDocumentFileRecursively(
                            doc = rootDoc,
                            songsList = songsList,
                            existingSignatures = existingSignatures,
                            excluded = excludedFolderPaths,
                            minDuration = minDurationMs
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return songsList.distinctBy {
            "${it.title.trim().lowercase()}_${it.artist.trim().lowercase()}_${it.duration / 1000}"
        }
    }

    private fun sanitizeSongsList(songs: List<Song>): List<Song> {
        return songs.map { s ->
            val cleanArtist = if (s.artist.isBlank() || s.artist.trim().equals("<unknown>", ignoreCase = true) || s.artist.trim().equals("unknown", ignoreCase = true)) {
                "Unknown Artist"
            } else s.artist
            val cleanAlbum = if (s.album.isBlank() || s.album.trim().equals("<unknown>", ignoreCase = true) || s.album.trim().equals("unknown", ignoreCase = true)) {
                "Unknown Album"
            } else s.album
            if (cleanArtist != s.artist || cleanAlbum != s.album) {
                s.copy(artist = cleanArtist, album = cleanAlbum)
            } else s
        }
    }

    suspend fun fetchAudioFiles(
        excludedFolderPaths: Set<String>,
        minDurationMs: Long,
        manualFolderUris: Set<String>
    ): List<Song> = withContext(Dispatchers.IO) {
        val cached = songDao.getAllSongsSync()
        if (cached.isNotEmpty()) {
            return@withContext sanitizeSongsList(cached)
        }
        return@withContext syncMediaStore(excludedFolderPaths, minDurationMs, manualFolderUris)
    }

    suspend fun syncMediaStore(
        excludedFolderPaths: Set<String>,
        minDurationMs: Long,
        manualFolderUris: Set<String>
    ): List<Song> = withContext(Dispatchers.IO) {
        val distinctSongsList = scanAllSongsInternal(excludedFolderPaths, minDurationMs, manualFolderUris)
        songDao.replaceAllSongs(distinctSongsList)
        distinctSongsList
    }

    suspend fun syncMediaStoreIfChanged(
        currentSongs: List<Song>,
        excludedFolderPaths: Set<String>,
        minDurationMs: Long,
        manualFolderUris: Set<String>
    ): Pair<Boolean, List<Song>> = withContext(Dispatchers.IO) {
        val freshlyScanned = scanAllSongsInternal(excludedFolderPaths, minDurationMs, manualFolderUris)
        val currentMap = currentSongs.associateBy { it.id }
        val hasChanged = freshlyScanned.size != currentSongs.size ||
            freshlyScanned.any { newS ->
                val oldS = currentMap[newS.id]
                oldS == null ||
                    newS.title != oldS.title ||
                    newS.artist != oldS.artist ||
                    newS.duration != oldS.duration ||
                    newS.albumArtUri != oldS.albumArtUri ||
                    newS.folderPath != oldS.folderPath ||
                    newS.dateAdded != oldS.dateAdded
            }
        if (hasChanged) {
            songDao.replaceAllSongs(freshlyScanned)
            Pair(true, freshlyScanned)
        } else {
            Pair(false, currentSongs)
        }
    }

    suspend fun getAllDiscoveredFolders(
        excludedFolderPaths: Set<String>,
        includeSystemHiddenInLibrary: Boolean
    ): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, Pair<String, Int>>()
        val allSongs = songDao.getAllSongsSync()

        allSongs.forEach { song ->
            val current = folderMap[song.folderPath]
            if (current == null) {
                folderMap[song.folderPath] = Pair(song.folderName, 1)
            } else {
                folderMap[song.folderPath] = Pair(song.folderName, current.second + 1)
            }
        }

        val trackedCustom = prefs.getStringSet("tracked_custom_folders", emptySet()) ?: emptySet()
        trackedCustom.forEach { path ->
            if (!folderMap.containsKey(path)) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val count = allSongs.count { it.folderPath == path || it.folderPath.startsWith(path) }
                    folderMap[path] = Pair(dir.name, count)
                }
            }
        }

        if (includeSystemHiddenInLibrary) {
            try {
                val db = com.kexora.soniclet.database.DatabaseProvider.getDatabase(context)
                val hiddenFolders = db.systemHiddenDao().getFoldersSync()
                hiddenFolders.forEach { hf ->
                    if (!folderMap.containsKey(hf.path) && hf.audioCount > 0) {
                        folderMap[hf.path] = Pair(hf.name, hf.audioCount)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        folderMap.map { (path, nameAndCount) ->
            val cleanPath = path.trimEnd('/')
            val isFolderExcluded = excludedFolderPaths.any { excludedPath ->
                val cleanExcluded = excludedPath.trimEnd('/')
                cleanPath == cleanExcluded || cleanPath.startsWith("$cleanExcluded/")
            }
            FolderInfo(
                path = path,
                name = nameAndCount.first,
                songCount = nameAndCount.second,
                isExcluded = isFolderExcluded
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun updateSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String,
        newAlbumArtUri: Uri?
    ): Boolean = withContext(Dispatchers.IO) {
        val cached = songDao.getAllSongsSync()
        val target = cached.find { it.id == songId } ?: return@withContext false

        val writeSuccess = com.kexora.soniclet.tools.TagEditorUtil.editSongTags(
            context = context,
            song = target,
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newYear = newYear,
            newGenre = newGenre,
            newAlbumArtUri = newAlbumArtUri
        )

        val persistentArtUri = if (newAlbumArtUri != null) {
            com.kexora.soniclet.utils.CustomArtworkManager.saveCustomArtwork(context, songId, newAlbumArtUri) ?: newAlbumArtUri
        } else {
            target.albumArtUri
        }

        val updated = target.copy(
            title = newTitle.ifBlank { target.title },
            artist = newArtist.ifBlank { target.artist },
            album = newAlbum.ifBlank { target.album },
            albumArtUri = persistentArtUri ?: target.albumArtUri,
            year = newYear.ifBlank { target.year },
            genre = newGenre.ifBlank { target.genre }
        )
        songDao.updateSong(updated)
        com.kexora.soniclet.utils.CustomArtworkManager.invalidateCoilCache(context)
        writeSuccess
    }
}
