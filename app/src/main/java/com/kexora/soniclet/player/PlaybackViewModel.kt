package com.kexora.soniclet.player

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import com.kexora.soniclet.audio.BassVibrationManager
import com.kexora.soniclet.audio.CustomRenderersFactory
import com.kexora.soniclet.audio.EightDimensionAudioProcessor
import com.kexora.soniclet.audio.MediaNotificationManager
import com.kexora.soniclet.audio.MusicPlaybackService
import com.kexora.soniclet.audio.PlaybackControlListener
import com.kexora.soniclet.database.DatabaseProvider
import com.kexora.soniclet.database.FolderInfo
import com.kexora.soniclet.database.ListeningHistoryEntity
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.SavedQueue
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.easteregg.MuhLoveRegistry
import com.kexora.soniclet.easteregg.SpecialDateInfo
import com.kexora.soniclet.equalizer.AudioFxViewModel
import com.kexora.soniclet.library.AudioRepository
import com.kexora.soniclet.library.AudioRepositoryImpl
import com.kexora.soniclet.library.LibraryViewModel
import com.kexora.soniclet.library.ListeningHistoryRepository
import com.kexora.soniclet.library.ShuffleMode
import com.kexora.soniclet.radio.RadioBackgroundHost
import com.kexora.soniclet.settings.SettingsSubPage
import com.kexora.soniclet.utils.AudioSpecs
import com.kexora.soniclet.utils.DuplicateGroup
import com.kexora.soniclet.utils.DuplicateScanMode
import com.kexora.soniclet.utils.SmartShuffleOrder
import com.kexora.soniclet.utils.SmartShuffleUtil
import com.kexora.soniclet.utils.UserBehaviorProfiler
import com.kexora.soniclet.widget.SonicletWidgetManager
import com.kexora.soniclet.widget.WidgetController
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repeat modes supported by the music player.
 */
enum class RepeatMode {
    OFF, ONE, ALL
}

/**
 * Navigation tabs for the bottom navigation bar.
 */
enum class NavigationTab {
    HOME, FOLDERS, QUEUE, PLAYER, RADIO, PLAYLISTS, ARTISTS, ALBUMS, GENRES, MOODS, SETTINGS, HIDDEN_ITEMS, USER_GUIDE, PLAYLIST_DETAILS, CATEGORY_DETAILS, SONG_DETAILS, NOW_PLAYING, DUPLICATES, DRIVE_MODE, STATS
}

/**
 * App visual theme settings.
 */
enum class AppTheme {
    DARK, LIGHT, SYSTEM
}

enum class SleepTimerOption(val label: String, val minutes: Int) {
    OFF("Off", 0),
    M15("15 Min", 15),
    M30("30 Min", 30),
    M45("45 Min", 45),
    M60("60 Min", 60),
    M90("90 Min", 90),
    CUSTOM("Custom", -2),
    END_OF_SONG("End of Track", -1)
}

/**
 * Custom user-saved Equalizer Preset.
 */
data class CustomEqPreset(
    val name: String,
    val levels: List<Int>,
    val bass: Int = 0,
    val vocal: Int = 0,
    val treble: Int = 0,
    val virtualizer: Int = 0
)

/**
 * Lyric line with timestamp in milliseconds and text content.
 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val durationMs: Long = 0L,
    val words: List<LyricWord> = emptyList(),
    val isKaraoke: Boolean = true
)

enum class SortOption {
    SONG_NAME, ARTIST_NAME, ALBUM_NAME, TIME_ADDED, DURATION, FOLDER_NAME, YEAR, FILE_SIZE, RANDOM
}

/**
 * State class exposing the complete UI state for Soniclet music player.
 */
data class MusicUiState(
    val currentTab: NavigationTab = NavigationTab.HOME,
    val activeMainTabs: List<NavigationTab> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val totalDuration: Long = 0L,
    val allSongsList: List<Song> = emptyList(),
    val songList: List<Song> = emptyList(),
    val filteredSongList: List<Song> = emptyList(),
    val activeQueue: List<Song> = emptyList(),
    val queueSourceTitle: String = "All Tracks",
    val discoveredFolders: List<FolderInfo> = emptyList(),
    val includeSystemHiddenInLibrary: Boolean = false,
    val excludedFolders: Set<String> = emptySet(),
    val excludedSongIds: Set<Long> = emptySet(),
    val restoredAutoSongIds: Set<Long> = emptySet(),
    val manualFolderUris: Set<String> = emptySet(),
    val showExcludedFoldersDialog: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val savedQueues: List<SavedQueue> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedSmartPlaylistType: String? = null,
    val selectedCategoryName: String? = null,
    val selectedCategoryType: String? = null,
    val selectedCategorySongs: List<Song> = emptyList(),
    val selectedSongForDetails: Song? = null,
    val selectedSongIds: Set<Long> = emptySet(),
    val songToTrim: Song? = null,
    val favoriteSongIds: Set<Long> = emptySet(),
    val playCounts: Map<Long, Int> = emptyMap(),
    val playDurations: Map<Long, Long> = emptyMap(),
    val lastPlayedTimestamps: Map<Long, Long> = emptyMap(),
    val playbackPositions: Map<Long, Long> = emptyMap(),
    val actuallyPlayedSongIds: Set<Long> = emptySet(),
    val songMoods: Map<Long, String> = emptyMap(),
    val recentlyPlayedSongIds: List<Long> = emptyList(),
    // Persistent Room-backed Wrapped History (survives device file deletion)
    val listeningHistory: List<ListeningHistoryEntity> = emptyList(),
    // Session-Locked 5-Layer Home Recommendation Cache
    val selectedHomeCategory: String = "All",
    val homeManualRefreshCounter: Int = 0,
    val homeEligibleSongs: List<Song> = emptyList(),
    val homeForYouSongs: List<Song> = emptyList(),
    val homeMixSongs: List<Song> = emptyList(),
    val homeTopSingers: List<Pair<String, List<Song>>> = emptyList(),
    val appTheme: AppTheme = AppTheme.DARK,
    val accentColor: Long = 0xFF00E676,
    val customBackgroundUri: String? = null,
    val backgroundMode: String = "DYNAMIC",
    val bgImageBlur: Float = 18f,
    val bgImageDim: Float = 0.60f,
    val solidBgColor: Long = 0xFF121212L,
    val solidBgOpacity: Float = 1.0f,
    val gradientPresetIndex: Int = 0,
    val customGradientColors: List<Long> = listOf(0xFF0F2027L, 0xFF203A43L, 0xFF2C5364L),
    val gradientIntensity: Float = 1.0f,
    val gradientBlur: Float = 0f,
    val nowPlayingLayout: String = "CLASSIC",
    val cornerRadiusScale: Float = 0.5f,
    val fontStyle: String = "STANDARD",
    val motionLevel: String = "FLUID",

    val isKariModeEnabled: Boolean = true,
    val kariBlur: Float = 18f,
    val kariOpacity: Float = 0.60f,
    val isShakeEnabled: Boolean = false,
    val isTwoFingerTapEnabled: Boolean = true,
    val twoFingerTapAction: String = "Play/Pause",
    val isThreeFingerSwipeEnabled: Boolean = false,
    val threeFingerSwipeAction: String = "Next/Previous",
    val isVolumeEdgeSwipeEnabled: Boolean = false,
    val isLongPressDragSeekEnabled: Boolean = true,
    val isOneFingerSwipeBackEnabled: Boolean = true,
    val isGridMode: Boolean = false,
    val isTwoFingerSwipeHomeEnabled: Boolean = true,
    val isMiniPlayerGesturesEnabled: Boolean = true,

    val isFlipToPauseEnabled: Boolean = false,
    val flipToPauseAction: String = "Pause",
    val isWristTwistEnabled: Boolean = false,
    val wristTwistAction: String = "Favorite",
    val isPocketModeEnabled: Boolean = false,
    val pocketModeAction: String = "Auto-Play/Pause",
    val shakeAction: String = "Shuffle",
    val isTiltToFastForwardEnabled: Boolean = false,
    val isAirWaveEnabled: Boolean = false,
    val airWaveAction: String = "Play/Pause",
    val isPickUpToPlayEnabled: Boolean = false,
    val isCrossfadeEnabled: Boolean = false,
    val isAutoResumeOnPlugEnabled: Boolean = true,
    val isAutoPlayOnBluetoothEnabled: Boolean = false,
    val singleClickAction: String = "Play/Pause",
    val doubleClickAction: String = "Next",
    val tripleClickAction: String = "Previous",
    val isHearingSafetyEnabled: Boolean = true,
    val isGaplessPlaybackEnabled: Boolean = true,
    val isResumeLastPositionEnabled: Boolean = false,
    val crossfadeDurationSeconds: Int = 3,
    val isFirstLaunch: Boolean = true,
    val kariDominantColor: Long? = null,
    val kariVibrantColor: Long? = null,
    val kariDarkMutedColor: Long? = null,
    val minDurationMs: Long = 10000L,
    val isLoading: Boolean = false,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val searchQuery: String = "",
    val recentSearches: List<String> = emptyList(),
    val errorMessage: String? = null,
    val userNotificationBanner: String? = null,
    val sortOption: SortOption = SortOption.TIME_ADDED,
    val sortAscending: Boolean = false,
    val formatFilter: String = "ALL",
    val durationCategoryFilter: String = "ALL",
    val attributeFilter: String = "ALL",

    // Smart Sleep Timer State
    val sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF,
    val customSleepTimerMinutes: Int = 20,
    val sleepTimerRemainingMs: Long = 0L,
    val finishTrackBeforeSleep: Boolean = false,

    // Synchronized Lyrics Engine State
    val currentLyrics: List<LyricLine> = emptyList(),
    val activeLyricIndex: Int = -1,
    val lyricsSyncOffsetMs: Long = 0L,
    val isKaraokeModeEnabled: Boolean = true,
    val lyricsSourceType: String = "",
    val isLyricsLoading: Boolean = false,

    // Audio Equalizer & FX State
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val is8dAudioEnabled: Boolean = false,
    val audioSessionId: Int = 0,
    val isEqEnabled: Boolean = false,
    val isReverbEnabled: Boolean = false,
    val isBassBoostEnabled: Boolean = false,
    val isVirtualizerEnabled: Boolean = false,
    val isVolumeBoostEnabled: Boolean = false,
    val isDolbyEnabled: Boolean = false,
    val isLosslessEnabled: Boolean = false,
    val isBassVibrationEnabled: Boolean = false,
    val bassVibrationStrength: Int = 80,
    val bassVibrationMode: String = "DJ Club Bass",
    val eqBandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val eqBandFrequencies: List<String> = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"),
    val selectedEqPresetName: String = "Flat / Normal",
    val customEqPresets: List<CustomEqPreset> = emptyList(),
    val bassBoostLevel: Int = 0,
    val vocalBoostLevel: Int = 0,
    val trebleBoostLevel: Int = 0,
    val virtualizerLevel: Int = 0,
    val volumeBoostLevel: Int = 100,
    val reverbPreset: Short = 0,

    // Create Playlist Dialog State
    val showCreatePlaylistDialog: Boolean = false,

    // Settings Sub-Page Navigation State
    val activeSettingsSubPage: SettingsSubPage = SettingsSubPage.APPEARANCE,
    val playTogetherEnabled: Boolean = false,
    val playTogetherFadeEnabled: Boolean = true,

    // 1.1 A-B Repeat Loop State
    val isAbRepeatActive: Boolean = false,
    val abRepeatPointA: Long? = null,
    val abRepeatPointB: Long? = null,

    // 2.4 Audio Codec & Bitrate Inspector State
    val currentSongAudioSpecs: AudioSpecs? = null,
    val songSpecsCache: Map<Long, AudioSpecs> = emptyMap(),

    // Secret Moh Mode State
    val isMohModeActivated: Boolean = false,
    val todaySpecialDate: SpecialDateInfo? = null
)

/**
 * Core ViewModel managing UI state, playback controls, queue management, and delegating
 * library management to [LibraryViewModel] and audio effects/equalizer/theme settings to [AudioFxViewModel].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicViewModel @Inject constructor(
    internal val audioRepository: AudioRepository,
    internal val context: Context
) : ViewModel(), PlaybackControlListener {

    internal val _uiState = MutableStateFlow(MusicUiState(isFirstLaunch = audioRepository.isFirstLaunch.value))
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    internal val prefs = context.getSharedPreferences("soniclet_ui_prefs", Context.MODE_PRIVATE)

    val defaultMainTabs = listOf(
        NavigationTab.HOME, NavigationTab.PLAYER, NavigationTab.RADIO, NavigationTab.PLAYLISTS, NavigationTab.FOLDERS,
        NavigationTab.ARTISTS, NavigationTab.ALBUMS, NavigationTab.GENRES,
        NavigationTab.MOODS, NavigationTab.QUEUE, NavigationTab.STATS
    )

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    val eightDProcessor = EightDimensionAudioProcessor()
    internal val bassVibrationManager = BassVibrationManager(context)

    internal val listeningHistoryRepository: ListeningHistoryRepository by lazy {
        ListeningHistoryRepository(DatabaseProvider.getDatabase(context).listeningHistoryDao())
    }

    internal val libVm = LibraryViewModel(this, viewModelScope)
    internal val fxVm = AudioFxViewModel(this, viewModelScope)

    private var positionUpdateJob: Job? = null
    private var activeListeningSong: Song? = null
    private var activeSongSessionListeningMs: Long = 0L
    private var pendingUnflushedListenMs: Long = 0L
    private var hasQualifiedActivePlay: Boolean = false
    private var stopForegroundDelayJob: Job? = null
    private var volumeGuardJob: Job? = null
    private var highVolumeMinutes = 0

    private var volumeFadeJob: Job? = null
    private var crossfadeJob: Job? = null
    private var pausedByAudioFocus = false
    private var pausedByInterruption = false
    private var wasPlayingBeforeInterruption = false
    private var isDuckedByOtherAudio = false
    private var interruptionWatcherJob: Job? = null
    private var duckRecoveryWatcherJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastPlayedMediaId: String? = null
    private var hasCrossfadedCurrentSong = false
    private var bypassResumeForNextTransition = false
    private var skipResumeEvaluationForNextTransition = false
    private var hasRestoredLastSession = false

    private fun effectiveDurationMs(song: Song): Long {
        val playerDur = try {
            val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                ?.removePrefix("manual_")
                ?.substringBefore("_")
                ?.toLongOrNull()
            if (currentMediaId == song.id && exoPlayer.duration > 0L) exoPlayer.duration else 0L
        } catch (_: Exception) {
            0L
        }
        return if (playerDur > 0L) playerDur else song.duration.coerceAtLeast(0L)
    }

    private fun persistLastSessionState(
        song: Song,
        positionMs: Long,
        durationMs: Long,
        queueOverride: List<Song>? = null,
        queueTitleOverride: String? = null
    ) {
        try {
            val safeSessionPos = ListeningHistoryRepository.sanitizePositionForResumeSave(positionMs, durationMs)
            val queue = (queueOverride ?: _uiState.value.activeQueue).take(500)
            val queueIdsCsv = if (queue.isNotEmpty()) queue.joinToString(",") { it.id.toString() } else song.id.toString()
            val queueTitle = (queueTitleOverride ?: _uiState.value.queueSourceTitle).ifBlank { "Active Queue" }
            prefs.edit()
                .putLong("last_session_song_id", song.id)
                .putLong("last_session_position_ms", safeSessionPos)
                .putString("last_session_queue_ids", queueIdsCsv)
                .putString("last_session_queue_title", queueTitle)
                .apply()
        } catch (_: Exception) {}
    }

    private fun saveTrackPositionWithSmartPolicy(song: Song, positionMs: Long, durationMs: Long) {
        val safePosition = ListeningHistoryRepository.sanitizePositionForResumeSave(positionMs, durationMs)
        audioRepository.savePlaybackPosition(song.id, safePosition)
        persistLastSessionState(song = song, positionMs = safePosition, durationMs = durationMs)
    }

    private fun snapshotOutgoingTrackPositionBeforeSwitch() {
        val outgoingSong = _uiState.value.currentSong ?: return
        val outgoingPos = try {
            if (exoPlayer.mediaItemCount > 0) exoPlayer.currentPosition.coerceAtLeast(0L)
            else _currentPosition.value.coerceAtLeast(0L)
        } catch (_: Exception) {
            _currentPosition.value.coerceAtLeast(0L)
        }
        val outgoingDuration = effectiveDurationMs(outgoingSong)
        saveTrackPositionWithSmartPolicy(outgoingSong, outgoingPos, outgoingDuration)
    }

    private fun resolveAndApplySmartResume(song: Song, bypassResume: Boolean): Long {
        val effectiveDur = effectiveDurationMs(song)
        if (bypassResume) {
            audioRepository.savePlaybackPosition(song.id, 0L)
            return 0L
        }
        if (!_uiState.value.isResumeLastPositionEnabled) {
            return 0L
        }
        val savedPos = audioRepository.getSavedPlaybackPosition(song.id)
        val validatedResumePos = ListeningHistoryRepository.resolveSmartResumePosition(
            savedPos = savedPos,
            durationMs = effectiveDur
        )
        return if (validatedResumePos > 0L) {
            validatedResumePos
        } else {
            if (savedPos > 0L) {
                audioRepository.savePlaybackPosition(song.id, 0L)
            }
            0L
        }
    }

    fun persistPlaybackStateSafely() {
        try {
            val song = _uiState.value.currentSong ?: return
            val durationMs = effectiveDurationMs(song)
            val isEnded = try {
                exoPlayer.playbackState == Player.STATE_ENDED
            } catch (_: Exception) {
                false
            }
            val currentPos = if (isEnded) {
                0L
            } else {
                try {
                    if (exoPlayer.mediaItemCount > 0) exoPlayer.currentPosition.coerceAtLeast(0L)
                    else _currentPosition.value.coerceAtLeast(0L)
                } catch (_: Exception) {
                    _currentPosition.value.coerceAtLeast(0L)
                }
            }
            saveTrackPositionWithSmartPolicy(song, currentPos, durationMs)
        } catch (_: Exception) {}
    }

    internal fun restoreLastSessionStateIfNeeded(
        visibleSongs: List<Song>,
        allSongs: List<Song>,
        remappedIds: Map<Long, Long>
    ) {
        if (hasRestoredLastSession) return
        if (_uiState.value.currentSong != null || exoPlayer.mediaItemCount > 0) {
            hasRestoredLastSession = true
            return
        }
        hasRestoredLastSession = true

        val savedSongId = prefs.getLong("last_session_song_id", -1L)
        if (savedSongId <= 0L) return

        val resolvedSongId = remappedIds[savedSongId] ?: savedSongId
        val allById = allSongs.associateBy { it.id }
        val visibleById = visibleSongs.associateBy { it.id }
        val restoredSong = visibleById[resolvedSongId] ?: allById[resolvedSongId] ?: return

        val savedQueueCsv = prefs.getString("last_session_queue_ids", null)
        val restoredQueue = if (!savedQueueCsv.isNullOrBlank()) {
            savedQueueCsv.split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .mapNotNull { id ->
                    val resolved = remappedIds[id] ?: id
                    visibleById[resolved] ?: allById[resolved]
                }
                .distinctBy { it.id }
        } else {
            emptyList()
        }.ifEmpty {
            visibleSongs.ifEmpty { listOf(restoredSong) }
        }

        val finalQueue = if (restoredQueue.any { it.id == restoredSong.id }) {
            restoredQueue
        } else {
            listOf(restoredSong) + restoredQueue
        }
        val restoredQueueTitle = prefs.getString("last_session_queue_title", "Active Queue")
            ?.ifBlank { "Active Queue" } ?: "Active Queue"

        val savedSessionPos = prefs.getLong("last_session_position_ms", 0L)
        val restoredPos = ListeningHistoryRepository.resolveSmartResumePosition(
            savedPos = savedSessionPos,
            durationMs = restoredSong.duration
        )
        if (restoredPos <= 0L && savedSessionPos > 0L) {
            prefs.edit().putLong("last_session_position_ms", 0L).apply()
            audioRepository.savePlaybackPosition(restoredSong.id, 0L)
        }

        _currentPosition.value = restoredPos
        _uiState.update { state ->
            state.copy(
                currentSong = restoredSong,
                activeQueue = finalQueue,
                queueSourceTitle = restoredQueueTitle,
                totalDuration = restoredSong.duration.coerceAtLeast(0L)
            )
        }
        try { fxVm.extractKariPalette(restoredSong) } catch (_: Exception) {}
        try { fxVm.loadLyricsForSong(restoredSong) } catch (_: Exception) {}
        try { fxVm.inspectAudioSpecsForSong(restoredSong) } catch (_: Exception) {}
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousSong = _uiState.value.currentSong ?: activeListeningSong
            val isNaturalCompletion = (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            val isRepeatLoop = (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)

            // Step 1: Auto-clear saved playback position immediately when outgoing track finishes naturally or loops
            if (previousSong != null && (isNaturalCompletion || isRepeatLoop)) {
                audioRepository.savePlaybackPosition(previousSong.id, 0L)
                persistLastSessionState(
                    song = previousSong,
                    positionMs = 0L,
                    durationMs = effectiveDurationMs(previousSong)
                )
            }

            hasCrossfadedCurrentSong = false
            crossfadeJob?.cancel()

            if (_uiState.value.isCrossfadeEnabled && isNaturalCompletion) {
                exoPlayer.volume = 0.15f
                val crossfadeDurationMs = (_uiState.value.crossfadeDurationSeconds * 1000L).coerceIn(1000L, 5000L)
                crossfadeJob = viewModelScope.launch {
                    val steps = (crossfadeDurationMs / 100L).toInt().coerceAtLeast(1)
                    val stepDelay = crossfadeDurationMs / steps
                    for (i in 0..steps) {
                        if (!exoPlayer.isPlaying) break
                        val ratio = (i.toFloat() / steps.toFloat())
                        exoPlayer.volume = ratio.coerceIn(0.15f, 1.0f)
                        delay(stepDelay)
                    }
                    exoPlayer.volume = 1.0f
                }
            } else {
                exoPlayer.volume = 1.0f
            }

            if (_uiState.value.sleepTimerOption == SleepTimerOption.END_OF_SONG && isNaturalCompletion) {
                finalizePreviousListeningSession(endedNaturally = true)
                exoPlayer.pause()
                _uiState.update { it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingMs = 0L) }
                return
            }
            lastPlayedMediaId = mediaItem?.mediaId

            val songIdString = mediaItem?.mediaId?.removePrefix("manual_")?.substringBefore("_")
            val songId = songIdString?.toLongOrNull()
            if (songId != null) {
                val song = _uiState.value.allSongsList.find { it.id == songId }
                    ?: _uiState.value.songList.find { it.id == songId }
                    ?: _uiState.value.activeQueue.find { it.id == songId }
                    ?: _uiState.value.currentSong?.takeIf { it.id == songId }
                if (song != null) {
                    _uiState.update {
                        it.copy(
                            currentSong = song,
                            isAbRepeatActive = false,
                            abRepeatPointA = null,
                            abRepeatPointB = null
                        )
                    }
                    finalizePreviousListeningSession(endedNaturally = isNaturalCompletion)
                    activeListeningSong = song
                    activeSongSessionListeningMs = 0L
                    pendingUnflushedListenMs = 0L
                    hasQualifiedActivePlay = false

                    try { fxVm.extractKariPalette(song) } catch (_: Exception) {}
                    try { fxVm.loadLyricsForSong(song) } catch (_: Exception) {}
                    try { fxVm.inspectAudioSpecsForSong(song) } catch (_: Exception) {}
                    try { audioRepository.recordTrackFingerprint(song) } catch (_: Exception) {}

                    _uiState.update { state -> state.copy(actuallyPlayedSongIds = state.actuallyPlayedSongIds + song.id) }

                    val shouldSkipResumeEval = skipResumeEvaluationForNextTransition
                    skipResumeEvaluationForNextTransition = false

                    if (!shouldSkipResumeEval) {
                        try {
                            val isRepeatOneOverride = isRepeatLoop ||
                                (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE && previousSong?.id == song.id && isNaturalCompletion)
                            val bypassResume = isRepeatOneOverride || bypassResumeForNextTransition
                            bypassResumeForNextTransition = false

                            val resumePos = resolveAndApplySmartResume(song = song, bypassResume = bypassResume)
                            if (resumePos > 0L) {
                                exoPlayer.seekTo(resumePos)
                                _currentPosition.value = resumePos
                            } else {
                                if (exoPlayer.currentPosition > 0L) {
                                    exoPlayer.seekTo(0L)
                                }
                                _currentPosition.value = 0L
                            }
                            persistLastSessionState(
                                song = song,
                                positionMs = resumePos,
                                durationMs = effectiveDurationMs(song)
                            )
                        } catch (_: Exception) {}
                    } else {
                        bypassResumeForNextTransition = false
                        persistLastSessionState(
                            song = song,
                            positionMs = _currentPosition.value,
                            durationMs = effectiveDurationMs(song)
                        )
                    }

                    try { updateNotification() } catch (_: Exception) {}
                }
            } else {
                skipResumeEvaluationForNextTransition = false
                bypassResumeForNextTransition = false
                if (_uiState.value.currentSong == null) {
                    _uiState.update { it.copy(currentSong = null) }
                }
            }
            syncQueueFromExoPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            bassVibrationManager.updateSettings(
                enabled = _uiState.value.isBassVibrationEnabled,
                strength = _uiState.value.bassVibrationStrength,
                mode = _uiState.value.bassVibrationMode,
                isPlaying = isPlaying,
                audioSessionId = _uiState.value.audioSessionId
            )
            updateNotification()
            if (isPlaying) {
                stopForegroundDelayJob?.cancel()
                startPositionTracker()
                MusicPlaybackService.startForegroundService(context)
            } else {
                stopPositionTracker()

                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                _uiState.value.currentSong?.let { song ->
                    val effectiveDur = effectiveDurationMs(song)
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        audioRepository.savePlaybackPosition(song.id, 0L)
                        persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDur)
                    } else {
                        saveTrackPositionWithSmartPolicy(song, currentPos, effectiveDur)
                    }
                }

                stopForegroundDelayJob?.cancel()
                stopForegroundDelayJob = viewModelScope.launch {
                    val waitTimeMs = if (pausedByInterruption || pausedByAudioFocus) 600_000L else 300_000L
                    delay(waitTimeMs)
                    if (!exoPlayer.isPlaying && !pausedByInterruption && !pausedByAudioFocus) {
                        MusicPlaybackService.stopForegroundService(context)
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val audioSessionId = exoPlayer.audioSessionId
                    _uiState.update {
                        it.copy(
                            totalDuration = exoPlayer.duration.coerceAtLeast(0L),
                            audioSessionId = audioSessionId,
                            errorMessage = null
                        )
                    }
                    fxVm.setupEqualizer(audioSessionId)
                    bassVibrationManager.updateSettings(
                        enabled = _uiState.value.isBassVibrationEnabled,
                        strength = _uiState.value.bassVibrationStrength,
                        mode = _uiState.value.bassVibrationMode,
                        isPlaying = exoPlayer.isPlaying,
                        audioSessionId = audioSessionId
                    )
                }
                Player.STATE_ENDED -> {
                    // Step 1: Auto-clear saved playback position immediately on natural completion
                    _uiState.value.currentSong?.let { song ->
                        audioRepository.savePlaybackPosition(song.id, 0L)
                        persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDurationMs(song))
                    }
                    activeListeningSong?.let { song ->
                        if (song.id != _uiState.value.currentSong?.id) {
                            audioRepository.savePlaybackPosition(song.id, 0L)
                        }
                    }
                    onSongEnded()
                }
                Player.STATE_BUFFERING -> {}
                Player.STATE_IDLE -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = "Playback error: ${error.localizedMessage ?: "Unable to play audio track"}"
                )
            }
        }
    }

    internal val exoPlayer: ExoPlayer by lazy {
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val renderersFactory = CustomRenderersFactory(context, eightDProcessor)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                addListener(playerListener)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
            }
    }

    private fun loadActiveMainTabs() {
        val saved = prefs.getString("active_main_tabs", null)
        val tabs = if (saved != null) {
            val loaded = saved.split(",").mapNotNull { name ->
                try { NavigationTab.valueOf(name) } catch (_: Exception) { null }
            }.toMutableList()
            if (loaded.isNotEmpty() && !loaded.contains(NavigationTab.HOME)) {
                loaded.add(0, NavigationTab.HOME)
            }
            if (!prefs.getBoolean("migrated_radio_tab_v1", false)) {
                if (loaded.isNotEmpty() && !loaded.contains(NavigationTab.RADIO)) {
                    val insertIdx = loaded.indexOf(NavigationTab.PLAYER).let { idx ->
                        if (idx >= 0) idx + 1 else minOf(1, loaded.size)
                    }
                    loaded.add(insertIdx, NavigationTab.RADIO)
                    prefs.edit()
                        .putString("active_main_tabs", loaded.joinToString(",") { it.name })
                        .putBoolean("migrated_radio_tab_v1", true)
                        .apply()
                } else {
                    prefs.edit().putBoolean("migrated_radio_tab_v1", true).apply()
                }
            }
            loaded
        } else {
            prefs.edit().putBoolean("migrated_radio_tab_v1", true).apply()
            defaultMainTabs
        }
        _uiState.update { it.copy(activeMainTabs = tabs.ifEmpty { defaultMainTabs }) }
    }

    fun updateActiveMainTabs(tabs: List<NavigationTab>) {
        val validTabs = tabs.ifEmpty { defaultMainTabs }
        val tabsString = validTabs.joinToString(",") { it.name }
        prefs.edit().putString("active_main_tabs", tabsString).apply()
        _uiState.update { state ->
            val updatedTab = if (state.currentTab in validTabs ||
                state.currentTab == NavigationTab.SETTINGS ||
                state.currentTab == NavigationTab.NOW_PLAYING ||
                state.currentTab == NavigationTab.DRIVE_MODE ||
                state.currentTab == NavigationTab.HIDDEN_ITEMS ||
                state.currentTab == NavigationTab.DUPLICATES
            ) {
                state.currentTab
            } else {
                validTabs.firstOrNull() ?: NavigationTab.HOME
            }
            state.copy(activeMainTabs = validTabs, currentTab = updatedTab)
        }
    }

    private fun isPhoneCallActive(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audioManager.mode == AudioManager.MODE_RINGTONE
        } catch (_: Exception) {
            false
        }
    }

    private fun fadeVolumeTo(targetVol: Float, durationMs: Long) {
        volumeFadeJob?.cancel()
        volumeFadeJob = viewModelScope.launch {
            val startVol = exoPlayer.volume
            val steps = 15
            val delayMs = (durationMs / steps).coerceAtLeast(10L)
            val stepSize = (targetVol - startVol) / steps
            var currentVol = startVol
            for (i in 0 until steps) {
                delay(delayMs)
                currentVol += stepSize
                exoPlayer.volume = currentVol.coerceIn(0f, 1f)
            }
            exoPlayer.volume = targetVol.coerceIn(0f, 1f)
        }
    }

    private fun startInterruptionResumeWatcher() {
        interruptionWatcherJob?.cancel()
        interruptionWatcherJob = viewModelScope.launch(Dispatchers.Default) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@launch
            var consecutiveSilenceCount = 0
            val maxWaitMs = 15 * 60 * 1000L
            val startTime = System.currentTimeMillis()

            while (pausedByInterruption && (System.currentTimeMillis() - startTime < maxWaitMs)) {
                delay(800L)
                if (!pausedByInterruption || RadioBackgroundHost.isRadioPlayingNow()) break

                if (isPhoneCallActive()) {
                    consecutiveSilenceCount = 0
                    continue
                }

                if (audioManager.isMusicActive) {
                    consecutiveSilenceCount = 0
                    continue
                }

                consecutiveSilenceCount++
                if (consecutiveSilenceCount >= 2) {
                    withContext(Dispatchers.Main) {
                        if (pausedByInterruption && !exoPlayer.isPlaying && !RadioBackgroundHost.isRadioPlayingNow()) {
                            val granted = requestAudioFocus()
                            if (granted) {
                                pausedByInterruption = false
                                pausedByAudioFocus = false
                                fadeVolumeAndPlay(0.0f, 1.0f, 650L)
                            }
                        }
                    }
                    if (!pausedByInterruption) break
                }
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val playTogether = _uiState.value.playTogetherEnabled
        val playTogetherFade = _uiState.value.playTogetherFadeEnabled

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent focus loss (e.g. another media app or Radio started):
                // pause cleanly without polling/stealing audio focus back.
                duckRecoveryWatcherJob?.cancel()
                interruptionWatcherJob?.cancel()
                isDuckedByOtherAudio = false
                pausedByInterruption = false
                pausedByAudioFocus = false
                if (exoPlayer.isPlaying) {
                    fadeVolumeAndPause(1.0f, 0.0f, 300L)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (playTogether) {
                    if (playTogetherFade && exoPlayer.isPlaying && !isDuckedByOtherAudio) {
                        isDuckedByOtherAudio = true
                        fadeVolumeTo(0.28f, 250L)
                    }
                } else {
                    if (exoPlayer.isPlaying) {
                        pausedByAudioFocus = true
                        pausedByInterruption = true
                        wasPlayingBeforeInterruption = true
                        fadeVolumeAndPause(1.0f, 0.0f, 350L)
                        startInterruptionResumeWatcher()
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (playTogether && playTogetherFade && exoPlayer.isPlaying && !isDuckedByOtherAudio) {
                    // Duck once and wait cleanly for AUDIOFOCUS_GAIN (never run a polling focus loop)
                    isDuckedByOtherAudio = true
                    fadeVolumeTo(0.28f, 250L)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                duckRecoveryWatcherJob?.cancel()
                if (playTogether) {
                    if (isDuckedByOtherAudio) {
                        isDuckedByOtherAudio = false
                        fadeVolumeTo(1.0f, 350L)
                    } else if (exoPlayer.isPlaying) {
                        exoPlayer.volume = 1.0f
                    }
                } else {
                    if (pausedByInterruption || pausedByAudioFocus) {
                        pausedByInterruption = false
                        pausedByAudioFocus = false
                        interruptionWatcherJob?.cancel()
                        fadeVolumeAndPlay(0.0f, 1.0f, 500L)
                    } else if (exoPlayer.isPlaying) {
                        isDuckedByOtherAudio = false
                        exoPlayer.volume = 1.0f
                    }
                }
            }
        }
    }

    private fun fadeVolumeAndPause(startVol: Float, endVol: Float, durationMs: Long) {
        volumeFadeJob?.cancel()
        volumeFadeJob = viewModelScope.launch {
            val steps = 15
            val delayMs = (durationMs / steps).coerceAtLeast(10L)
            val stepSize = (endVol - startVol) / steps
            var currentVol = startVol
            exoPlayer.volume = currentVol
            for (i in 0 until steps) {
                delay(delayMs)
                currentVol += stepSize
                exoPlayer.volume = currentVol.coerceIn(0f, 1f)
            }
            exoPlayer.volume = endVol
            if (endVol == 0f) {
                exoPlayer.pause()
            }
        }
    }

    private fun fadeVolumeAndPlay(startVol: Float, endVol: Float, durationMs: Long) {
        volumeFadeJob?.cancel()
        volumeFadeJob = viewModelScope.launch {
            exoPlayer.volume = startVol
            playWithFocusCheck()
            val steps = 15
            val delayMs = (durationMs / steps).coerceAtLeast(10L)
            val stepSize = (endVol - startVol) / steps
            var currentVol = startVol
            for (i in 0 until steps) {
                delay(delayMs)
                currentVol += stepSize
                exoPlayer.volume = currentVol.coerceIn(0f, 1f)
            }
            exoPlayer.volume = endVol
        }
    }

    private fun playWithFocusCheck(forceStartFromBeginning: Boolean = false) {
        if (exoPlayer.mediaItemCount == 0 && _uiState.value.currentSong != null) {
            val current = _uiState.value.currentSong!!
            val queue = _uiState.value.activeQueue.ifEmpty { _uiState.value.filteredSongList.ifEmpty { _uiState.value.songList } }
            val effectiveDur = effectiveDurationMs(current)
            val rawPos = if (forceStartFromBeginning) 0L else _currentPosition.value.coerceAtLeast(0L)
            val safeStartPos = if (forceStartFromBeginning ||
                ListeningHistoryRepository.isTrackCompletedOrNearEnd(rawPos, effectiveDur) ||
                rawPos < ListeningHistoryRepository.EARLY_START_GUARD_MS ||
                effectiveDur in 1L until ListeningHistoryRepository.MIN_RESUME_TRACK_DURATION_MS
            ) {
                audioRepository.savePlaybackPosition(current.id, 0L)
                persistLastSessionState(song = current, positionMs = 0L, durationMs = effectiveDur)
                0L
            } else {
                rawPos
            }
            _currentPosition.value = safeStartPos
            skipResumeEvaluationForNextTransition = true
            if (queue.isNotEmpty()) {
                val startIndex = queue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                val mediaItems = queue.map {
                    MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri).build()
                }
                exoPlayer.setMediaItems(mediaItems, startIndex, safeStartPos)
            } else {
                val mediaItem = MediaItem.Builder().setMediaId(current.id.toString()).setUri(current.contentUri).build()
                exoPlayer.setMediaItem(mediaItem, safeStartPos)
            }
        }
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
            // Step 5: Always restart cleanly from 0L and clear saved position when pressing Play from STATE_ENDED
            _uiState.value.currentSong?.let { song ->
                audioRepository.savePlaybackPosition(song.id, 0L)
                persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDurationMs(song))
            }
            _currentPosition.value = 0L
            exoPlayer.seekTo(0L)
            exoPlayer.prepare()
        }
        // Completely stop Radio & release Radio audio focus before local playback starts
        RadioBackgroundHost.stopForLocalPlaybackIfInitialized()
        if (requestAudioFocus()) {
            isDuckedByOtherAudio = false
            exoPlayer.volume = 1.0f
            exoPlayer.play()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val playTogether = _uiState.value.playTogetherEnabled
        val playTogetherFade = _uiState.value.playTogetherFadeEnabled

        if (playTogether && !playTogetherFade) return true

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusGainType = if (playTogether && playTogetherFade) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        } else {
            AudioManager.AUDIOFOCUS_GAIN
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(focusGainType)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                focusGainType
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
    }

    private fun abandonAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(cntx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    abandonAudioFocus()
                    pausedByAudioFocus = false
                }
            }
        }
    }

    private val headsetPlugReceiver = object : BroadcastReceiver() {
        override fun onReceive(cntx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                if (state == 1) {
                    if (_uiState.value.isAutoResumeOnPlugEnabled && _uiState.value.currentSong != null && !exoPlayer.isPlaying) {
                        playWithFocusCheck()
                    }
                }
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(cntx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    if (_uiState.value.isAutoPlayOnBluetoothEnabled && _uiState.value.currentSong != null && !exoPlayer.isPlaying) {
                        playWithFocusCheck()
                    }
                }
            }
        }
    }

    private fun startVolumeGuard() {
        volumeGuardJob?.cancel()
        volumeGuardJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                if (_uiState.value.isHearingSafetyEnabled && exoPlayer.isPlaying) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                    if (currentVolume.toFloat() / maxVolume > 0.8f) {
                        highVolumeMinutes++
                        if (highVolumeMinutes >= 30) {
                            launch(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Hearing Safety: High volume detected for 30 minutes. Lowering volume to protect your ears per WHO guidelines.",
                                    Toast.LENGTH_LONG
                                ).show()
                                val safeVolume = (maxVolume * 0.6f).toInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, safeVolume, AudioManager.FLAG_SHOW_UI)
                                highVolumeMinutes = 0
                            }
                        }
                    } else {
                        if (highVolumeMinutes > 0) highVolumeMinutes--
                    }
                }
            }
        }
    }

    private fun syncQueueFromExoPlayer() {
        val timeline = exoPlayer.currentTimeline
        if (timeline.isEmpty) {
            if (exoPlayer.mediaItemCount == 0) {
                _uiState.update { it.copy(activeQueue = emptyList()) }
            }
            return
        }

        val currentQueue = _uiState.value.activeQueue
        if (currentQueue.isNotEmpty() && exoPlayer.shuffleModeEnabled) {
            return
        }

        val fullQueue = mutableListOf<Song>()
        val window = Timeline.Window()
        val allSongs = _uiState.value.allSongsList.ifEmpty { _uiState.value.songList }

        val count = timeline.windowCount
        for (i in 0 until count) {
            val mediaItem = timeline.getWindow(i, window).mediaItem
            val songIdString = mediaItem.mediaId.removePrefix("manual_").substringBefore("_")
            val songId = songIdString.toLongOrNull()

            if (songId != null) {
                val song = allSongs.find { it.id == songId }
                    ?: currentQueue.find { it.id == songId }
                    ?: _uiState.value.currentSong?.takeIf { it.id == songId }
                if (song != null) {
                    fullQueue.add(song)
                }
            }
        }

        if (fullQueue.isNotEmpty() && currentQueue.isEmpty()) {
            _uiState.update { it.copy(activeQueue = fullQueue) }
        }
    }

    init {
        loadActiveMainTabs()
        MediaNotificationManager.registerListener(this)
        startVolumeGuard()

        val savedEqEnabled = prefs.getBoolean("setting_eq_enabled", false)
        val savedBassVibEnabled = prefs.getBoolean("bass_vibration_enabled", false)
        val savedBassVibStrength = prefs.getInt("bass_vibration_strength", 80)
        val savedBassVibMode = prefs.getString("bass_vibration_mode", "DJ Club Bass") ?: "DJ Club Bass"
        _uiState.update {
            it.copy(
                isEqEnabled = savedEqEnabled,
                isBassVibrationEnabled = savedBassVibEnabled,
                bassVibrationStrength = savedBassVibStrength,
                bassVibrationMode = savedBassVibMode
            )
        }

        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(audioBecomingNoisyReceiver, filter)

            val plugFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            context.registerReceiver(headsetPlugReceiver, plugFilter)

            val btFilter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            context.registerReceiver(bluetoothReceiver, btFilter)
        } catch (_: Exception) {}

        libVm.registerMediaStoreObserver()

        viewModelScope.launch {
            audioRepository.includeSystemHiddenInLibrary.collectLatest { included ->
                _uiState.update { it.copy(includeSystemHiddenInLibrary = included) }
                loadAudioFiles()
            }
        }

        viewModelScope.launch {
            audioRepository.excludedFolderPaths.collectLatest { excluded ->
                _uiState.update { it.copy(excludedFolders = excluded) }
                loadAudioFiles()
            }
        }

        viewModelScope.launch {
            audioRepository.excludedSongIds.collectLatest { excluded ->
                _uiState.update { it.copy(excludedSongIds = excluded) }
                loadAudioFiles()
            }
        }

        viewModelScope.launch {
            audioRepository.restoredAutoSongIds.collectLatest { restored ->
                _uiState.update { it.copy(restoredAutoSongIds = restored) }
                loadAudioFiles()
            }
        }

        viewModelScope.launch {
            audioRepository.minDurationMs.collectLatest { minDur ->
                _uiState.update { it.copy(minDurationMs = minDur) }
                loadAudioFiles()
            }
        }

        viewModelScope.launch {
            audioRepository.playlists.collectLatest { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }

        viewModelScope.launch {
            audioRepository.savedQueues.collectLatest { queues ->
                _uiState.update { it.copy(savedQueues = queues) }
            }
        }

        viewModelScope.launch {
            audioRepository.favoriteSongIds.collectLatest { favs ->
                _uiState.update { it.copy(favoriteSongIds = favs) }
                updateNotification()
            }
        }

        viewModelScope.launch {
            audioRepository.playCounts.collectLatest { counts ->
                _uiState.update { it.copy(playCounts = counts) }
            }
        }

        viewModelScope.launch {
            audioRepository.playDurations.collectLatest { durations ->
                _uiState.update { it.copy(playDurations = durations) }
            }
        }

        viewModelScope.launch {
            listeningHistoryRepository.allHistory.collectLatest { history ->
                _uiState.update { it.copy(listeningHistory = history) }
            }
        }

        viewModelScope.launch {
            audioRepository.lastPlayedTimestamps.collectLatest { timestamps ->
                _uiState.update { it.copy(lastPlayedTimestamps = timestamps) }
            }
        }

        viewModelScope.launch {
            audioRepository.playbackPositions.collectLatest { positions ->
                _uiState.update { it.copy(playbackPositions = positions) }
            }
        }

        viewModelScope.launch {
            audioRepository.songMoods.collectLatest { moods ->
                _uiState.update { it.copy(songMoods = moods) }
            }
        }

        viewModelScope.launch {
            audioRepository.recentlyPlayedSongIds.collectLatest { recents ->
                _uiState.update { it.copy(recentlyPlayedSongIds = recents) }
            }
        }

        viewModelScope.launch {
            audioRepository.recentSearches.collectLatest { recents ->
                _uiState.update { it.copy(recentSearches = recents) }
            }
        }

        viewModelScope.launch { audioRepository.accentColor.collectLatest { accent -> _uiState.update { it.copy(accentColor = accent) } } }
        viewModelScope.launch { audioRepository.customBackgroundUri.collectLatest { uri -> _uiState.update { it.copy(customBackgroundUri = uri) } } }
        viewModelScope.launch { audioRepository.backgroundMode.collectLatest { mode -> _uiState.update { it.copy(backgroundMode = mode) } } }
        viewModelScope.launch { audioRepository.bgImageBlur.collectLatest { blur -> _uiState.update { it.copy(bgImageBlur = blur) } } }
        viewModelScope.launch { audioRepository.bgImageDim.collectLatest { dim -> _uiState.update { it.copy(bgImageDim = dim) } } }
        viewModelScope.launch { audioRepository.solidBgColor.collectLatest { color -> _uiState.update { it.copy(solidBgColor = color) } } }
        viewModelScope.launch { audioRepository.solidBgOpacity.collectLatest { opacity -> _uiState.update { it.copy(solidBgOpacity = opacity) } } }
        viewModelScope.launch { audioRepository.gradientPresetIndex.collectLatest { preset -> _uiState.update { it.copy(gradientPresetIndex = preset) } } }
        viewModelScope.launch { audioRepository.customGradientColors.collectLatest { colors -> _uiState.update { it.copy(customGradientColors = colors) } } }
        viewModelScope.launch { audioRepository.gradientIntensity.collectLatest { intensity -> _uiState.update { it.copy(gradientIntensity = intensity) } } }
        viewModelScope.launch { audioRepository.gradientBlur.collectLatest { blur -> _uiState.update { it.copy(gradientBlur = blur) } } }
        viewModelScope.launch { audioRepository.nowPlayingLayout.collectLatest { l -> _uiState.update { it.copy(nowPlayingLayout = l) } } }
        viewModelScope.launch { audioRepository.cornerRadiusScale.collectLatest { s -> _uiState.update { it.copy(cornerRadiusScale = s) } } }
        viewModelScope.launch { audioRepository.fontStyle.collectLatest { f -> _uiState.update { it.copy(fontStyle = f) } } }
        viewModelScope.launch { audioRepository.motionLevel.collectLatest { m -> _uiState.update { it.copy(motionLevel = m) } } }

        viewModelScope.launch {
            audioRepository.playTogetherEnabled.collectLatest { playTogether ->
                _uiState.update { it.copy(playTogetherEnabled = playTogether) }
                if (playTogether && !_uiState.value.playTogetherFadeEnabled) {
                    abandonAudioFocus()
                    if (isDuckedByOtherAudio) {
                        isDuckedByOtherAudio = false
                        exoPlayer.volume = 1.0f
                    }
                } else if (exoPlayer.isPlaying) {
                    requestAudioFocus()
                }
            }
        }
        viewModelScope.launch {
            audioRepository.playTogetherFadeEnabled.collectLatest { playTogetherFade ->
                _uiState.update { it.copy(playTogetherFadeEnabled = playTogetherFade) }
                if (_uiState.value.playTogetherEnabled) {
                    if (playTogetherFade) {
                        if (exoPlayer.isPlaying) {
                            requestAudioFocus()
                        }
                    } else {
                        abandonAudioFocus()
                        if (isDuckedByOtherAudio) {
                            isDuckedByOtherAudio = false
                            exoPlayer.volume = 1.0f
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            audioRepository.appThemeName.collectLatest { themeName ->
                val theme = try { AppTheme.valueOf(themeName) } catch (_: Exception) { AppTheme.SYSTEM }
                _uiState.update { it.copy(appTheme = theme) }
            }
        }

        viewModelScope.launch {
            audioRepository.shuffleMode.collectLatest { mode ->
                _uiState.update { it.copy(shuffleMode = mode, isShuffleEnabled = mode != ShuffleMode.OFF) }
                exoPlayer.shuffleModeEnabled = (mode == ShuffleMode.STANDARD)
            }
        }

        viewModelScope.launch {
            audioRepository.repeatMode.collectLatest { modeString ->
                val mode = try { RepeatMode.valueOf(modeString) } catch (_: Exception) { RepeatMode.OFF }
                _uiState.update { it.copy(repeatMode = mode) }
                exoPlayer.repeatMode = when (mode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
            }
        }

        viewModelScope.launch { audioRepository.isKariModeEnabled.collectLatest { kari -> _uiState.update { it.copy(isKariModeEnabled = kari) } } }
        viewModelScope.launch { audioRepository.kariBlur.collectLatest { blur -> _uiState.update { it.copy(kariBlur = blur) } } }
        viewModelScope.launch { audioRepository.kariOpacity.collectLatest { opacity -> _uiState.update { it.copy(kariOpacity = opacity) } } }
        viewModelScope.launch { audioRepository.isShakeEnabled.collectLatest { enabled -> _uiState.update { it.copy(isShakeEnabled = enabled) } } }
        viewModelScope.launch { audioRepository.isTwoFingerTapEnabled.collectLatest { v -> _uiState.update { it.copy(isTwoFingerTapEnabled = v) } } }
        viewModelScope.launch { audioRepository.twoFingerTapAction.collectLatest { v -> _uiState.update { it.copy(twoFingerTapAction = v) } } }
        viewModelScope.launch { audioRepository.isThreeFingerSwipeEnabled.collectLatest { v -> _uiState.update { it.copy(isThreeFingerSwipeEnabled = v) } } }
        viewModelScope.launch { audioRepository.threeFingerSwipeAction.collectLatest { v -> _uiState.update { it.copy(threeFingerSwipeAction = v) } } }
        viewModelScope.launch { audioRepository.isVolumeEdgeSwipeEnabled.collectLatest { v -> _uiState.update { it.copy(isVolumeEdgeSwipeEnabled = v) } } }
        viewModelScope.launch { audioRepository.isLongPressDragSeekEnabled.collectLatest { v -> _uiState.update { it.copy(isLongPressDragSeekEnabled = v) } } }
        viewModelScope.launch { audioRepository.isOneFingerSwipeBackEnabled.collectLatest { v -> _uiState.update { it.copy(isOneFingerSwipeBackEnabled = v) } } }
        viewModelScope.launch { audioRepository.isTwoFingerSwipeHomeEnabled.collectLatest { v -> _uiState.update { it.copy(isTwoFingerSwipeHomeEnabled = v) } } }
        viewModelScope.launch { audioRepository.isMiniPlayerGesturesEnabled.collectLatest { v -> _uiState.update { it.copy(isMiniPlayerGesturesEnabled = v) } } }

        viewModelScope.launch { audioRepository.isFlipToPauseEnabled.collectLatest { v -> _uiState.update { it.copy(isFlipToPauseEnabled = v) } } }
        viewModelScope.launch { audioRepository.flipToPauseAction.collectLatest { v -> _uiState.update { it.copy(flipToPauseAction = v) } } }
        viewModelScope.launch { audioRepository.isWristTwistEnabled.collectLatest { v -> _uiState.update { it.copy(isWristTwistEnabled = v) } } }
        viewModelScope.launch { audioRepository.wristTwistAction.collectLatest { v -> _uiState.update { it.copy(wristTwistAction = v) } } }
        viewModelScope.launch { audioRepository.isPocketModeEnabled.collectLatest { v -> _uiState.update { it.copy(isPocketModeEnabled = v) } } }
        viewModelScope.launch { audioRepository.pocketModeAction.collectLatest { v -> _uiState.update { it.copy(pocketModeAction = v) } } }
        viewModelScope.launch { audioRepository.shakeAction.collectLatest { v -> _uiState.update { it.copy(shakeAction = v) } } }
        viewModelScope.launch { audioRepository.isTiltToFastForwardEnabled.collectLatest { v -> _uiState.update { it.copy(isTiltToFastForwardEnabled = v) } } }
        viewModelScope.launch { audioRepository.isAirWaveEnabled.collectLatest { v -> _uiState.update { it.copy(isAirWaveEnabled = v) } } }
        viewModelScope.launch { audioRepository.airWaveAction.collectLatest { v -> _uiState.update { it.copy(airWaveAction = v) } } }
        viewModelScope.launch { audioRepository.isPickUpToPlayEnabled.collectLatest { v -> _uiState.update { it.copy(isPickUpToPlayEnabled = v) } } }
        viewModelScope.launch { audioRepository.isFirstLaunch.collectLatest { isFirst -> _uiState.update { it.copy(isFirstLaunch = isFirst) } } }

        viewModelScope.launch {
            audioRepository.isMohModeActivated.collectLatest { activated ->
                val special = if (activated) MuhLoveRegistry.getTodaySpecialDate() else null
                _uiState.update { it.copy(isMohModeActivated = activated, todaySpecialDate = special) }
            }
        }

        viewModelScope.launch { audioRepository.isCrossfadeEnabled.collectLatest { enabled -> _uiState.update { it.copy(isCrossfadeEnabled = enabled) } } }
        viewModelScope.launch { audioRepository.crossfadeDurationSeconds.collectLatest { sec -> _uiState.update { it.copy(crossfadeDurationSeconds = sec) } } }
        viewModelScope.launch { audioRepository.isAutoResumeOnPlugEnabled.collectLatest { enabled -> _uiState.update { it.copy(isAutoResumeOnPlugEnabled = enabled) } } }
        viewModelScope.launch { audioRepository.isAutoPlayOnBluetoothEnabled.collectLatest { enabled -> _uiState.update { it.copy(isAutoPlayOnBluetoothEnabled = enabled) } } }
        viewModelScope.launch { audioRepository.singleClickAction.collectLatest { action -> _uiState.update { it.copy(singleClickAction = action) } } }
        viewModelScope.launch { audioRepository.doubleClickAction.collectLatest { action -> _uiState.update { it.copy(doubleClickAction = action) } } }
        viewModelScope.launch { audioRepository.tripleClickAction.collectLatest { action -> _uiState.update { it.copy(tripleClickAction = action) } } }
        viewModelScope.launch { audioRepository.isHearingSafetyEnabled.collectLatest { enabled -> _uiState.update { it.copy(isHearingSafetyEnabled = enabled) } } }
        viewModelScope.launch {
            audioRepository.isGaplessPlaybackEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(isGaplessPlaybackEnabled = enabled) }
                try {
                    exoPlayer.skipSilenceEnabled = enabled
                } catch (_: Exception) {}
            }
        }
        viewModelScope.launch { audioRepository.isResumeLastPositionEnabled.collectLatest { enabled -> _uiState.update { it.copy(isResumeLastPositionEnabled = enabled) } } }

        loadAudioFiles()
    }

    private val tabBackStack = mutableListOf<NavigationTab>()
    private val subPageBackStack = mutableListOf<SettingsSubPage>()

    internal fun removeTabFromBackStack(tab: NavigationTab) {
        tabBackStack.remove(tab)
    }

    fun selectTab(tab: NavigationTab) {
        val current = _uiState.value.currentTab
        if (current != tab) {
            if (tab == NavigationTab.HOME) {
                tabBackStack.clear()
            } else {
                if (tabBackStack.isEmpty() || tabBackStack.last() != current) {
                    tabBackStack.add(current)
                }
            }
            if (tab != NavigationTab.SETTINGS) {
                subPageBackStack.clear()
            }
            _uiState.update { it.copy(currentTab = tab, searchQuery = "", selectedSongIds = emptySet()) }
        }
    }

    fun setSettingsSubPage(subPage: SettingsSubPage, addToBackStack: Boolean = false) {
        val currentTab = _uiState.value.currentTab
        val currentSubPage = _uiState.value.activeSettingsSubPage
        if (addToBackStack && currentTab == NavigationTab.SETTINGS && currentSubPage != subPage) {
            subPageBackStack.add(currentSubPage)
        }
        _uiState.update { it.copy(activeSettingsSubPage = subPage, selectedSongIds = emptySet()) }
    }

    fun clearSubPageBackStack() {
        subPageBackStack.clear()
    }

    fun popTabStack(): Boolean {
        if (_uiState.value.currentTab == NavigationTab.SETTINGS) {
            if (subPageBackStack.isNotEmpty()) {
                val prevSub = subPageBackStack.removeAt(subPageBackStack.size - 1)
                _uiState.update { it.copy(activeSettingsSubPage = prevSub) }
                return true
            }
            when (_uiState.value.activeSettingsSubPage) {
                SettingsSubPage.HOME_TABS_CUSTOMIZER -> {
                    _uiState.update { it.copy(activeSettingsSubPage = SettingsSubPage.APPEARANCE) }
                    return true
                }
                SettingsSubPage.MANUAL_GESTURES -> {
                    _uiState.update { it.copy(activeSettingsSubPage = SettingsSubPage.GESTURES_GUIDE) }
                    return true
                }
                SettingsSubPage.MANUAL_MOTION -> {
                    _uiState.update { it.copy(activeSettingsSubPage = SettingsSubPage.MOTION_CONTROLS) }
                    return true
                }
                SettingsSubPage.MANUAL_ALL -> {
                    _uiState.update { it.copy(activeSettingsSubPage = SettingsSubPage.ABOUT) }
                    return true
                }
                else -> {}
            }
        }

        if (tabBackStack.isNotEmpty()) {
            val prevTab = tabBackStack.removeAt(tabBackStack.size - 1)
            _uiState.update { it.copy(currentTab = prevTab, selectedSongIds = emptySet()) }
            return true
        } else if (_uiState.value.currentTab != NavigationTab.HOME) {
            _uiState.update { it.copy(currentTab = NavigationTab.HOME, selectedSongIds = emptySet()) }
            return true
        }
        return false
    }

    // CORE PLAYBACK CONTROLS
    fun playSong(song: Song, queueSource: String? = null, songsContext: List<Song>? = null) {
        try {
            hasCrossfadedCurrentSong = false
            crossfadeJob?.cancel()
            volumeFadeJob?.cancel()
            interruptionWatcherJob?.cancel()
            duckRecoveryWatcherJob?.cancel()
            pausedByInterruption = false
            pausedByAudioFocus = false
            isDuckedByOtherAudio = false
            exoPlayer.volume = 1.0f

            val currentSource = _uiState.value.queueSourceTitle
            val isSourceSwitch = queueSource != null && queueSource != currentSource
            queueSource?.let { src -> _uiState.update { it.copy(queueSourceTitle = src) } }

            if (!songsContext.isNullOrEmpty()) {
                val targetIndex = songsContext.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                playQueue(songsContext, targetIndex, queueSource ?: "Active Queue")
                return
            }

            var foundIndex = -1
            if (!isSourceSwitch) {
                for (i in 0 until exoPlayer.mediaItemCount) {
                    if (exoPlayer.getMediaItemAt(i).mediaId == song.id.toString()) {
                        foundIndex = i
                        break
                    }
                }
            }

            if (foundIndex != -1) {
                val isSameSong = (_uiState.value.currentSong?.id == song.id)
                if (!isSameSong) {
                    snapshotOutgoingTrackPositionBeforeSwitch()
                    skipResumeEvaluationForNextTransition = false
                    exoPlayer.seekToDefaultPosition(foundIndex)
                } else {
                    audioRepository.savePlaybackPosition(song.id, 0L)
                    persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDurationMs(song))
                    _currentPosition.value = 0L
                    exoPlayer.seekTo(foundIndex, 0L)
                }
                if (exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
                playWithFocusCheck()
            } else {
                val songsList = _uiState.value.filteredSongList.ifEmpty { _uiState.value.songList }
                val targetIndex = songsList.indexOfFirst { it.id == song.id }
                if (targetIndex != -1) {
                    playQueue(songsList, targetIndex, queueSource ?: "All Tracks")
                } else {
                    snapshotOutgoingTrackPositionBeforeSwitch()
                    skipResumeEvaluationForNextTransition = false

                    val updatedAll = _uiState.value.allSongsList.toMutableList()
                    if (updatedAll.none { it.id == song.id }) {
                        updatedAll.add(0, song)
                    }
                    val updatedSongList = _uiState.value.songList.toMutableList()
                    if (updatedSongList.none { it.id == song.id }) {
                        updatedSongList.add(0, song)
                    }

                    _uiState.update {
                        it.copy(
                            allSongsList = updatedAll,
                            songList = updatedSongList,
                            activeQueue = listOf(song),
                            currentSong = song,
                            queueSourceTitle = queueSource ?: "Converted Audio"
                        )
                    }

                    val mediaItem = MediaItem.Builder().setMediaId(song.id.toString()).setUri(song.contentUri).build()
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    playWithFocusCheck()
                    try { fxVm.extractKariPalette(song) } catch (_: Exception) {}
                    try { updateNotification() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Cannot play: ${e.localizedMessage}") }
        }
    }

    fun playExternalUri(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        var title = uri.lastPathSegment ?: "External Audio"
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var duration = 0L
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: artist
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: album
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                duration = durStr.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val song = Song(
            id = System.currentTimeMillis(),
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            contentUri = uri,
            albumArtUri = null,
            folderPath = "External",
            folderName = "External App",
            dateAdded = System.currentTimeMillis() / 1000
        )
        _uiState.update { it.copy(activeQueue = listOf(song), queueSourceTitle = "External App", currentTab = NavigationTab.PLAYER) }
        playSong(song, "External App")
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0, sourceTitle: String = "Active Queue") {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val targetSong = songs[safeIndex]

        if (_uiState.value.currentSong?.id != targetSong.id) {
            snapshotOutgoingTrackPositionBeforeSwitch()
        }
        skipResumeEvaluationForNextTransition = false

        _uiState.update { it.copy(activeQueue = songs, queueSourceTitle = sourceTitle) }

        if (sourceTitle.isNotBlank() && sourceTitle != "Active Queue" && sourceTitle != "External App") {
            viewModelScope.launch {
                audioRepository.upsertSavedQueue(sourceTitle, songs.map { it.id })
            }
        }

        val mediaItems = songs.map {
            MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.contentUri).build()
        }

        exoPlayer.setMediaItems(mediaItems, safeIndex, C.TIME_UNSET)
        when (_uiState.value.shuffleMode) {
            ShuffleMode.OFF -> {
                exoPlayer.shuffleModeEnabled = false
            }
            ShuffleMode.STANDARD -> {
                if (mediaItems.size > 1) {
                    exoPlayer.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(mediaItems.size, System.currentTimeMillis()))
                }
                exoPlayer.shuffleModeEnabled = true
            }
            ShuffleMode.SMART -> {
                if (sourceTitle.contains("(Smart)")) {
                    exoPlayer.setShuffleOrder(SmartShuffleOrder((0 until mediaItems.size).toList().toIntArray()))
                    exoPlayer.shuffleModeEnabled = true
                } else {
                    applySmartShuffleToActiveQueue()
                }
            }
        }
        exoPlayer.prepare()
        playWithFocusCheck()
        syncQueueFromExoPlayer()
    }

    fun togglePlayPause() {
        val current = _uiState.value.currentSong
        if (current == null) {
            val list = _uiState.value.activeQueue.ifEmpty { _uiState.value.filteredSongList }
            if (list.isNotEmpty()) {
                playSong(list.first())
            }
            return
        }

        if (exoPlayer.isPlaying) {
            pausedByInterruption = false
            pausedByAudioFocus = false
            interruptionWatcherJob?.cancel()
            duckRecoveryWatcherJob?.cancel()
            isDuckedByOtherAudio = false
            exoPlayer.pause()
            abandonAudioFocus()
        } else {
            pausedByInterruption = false
            pausedByAudioFocus = false
            interruptionWatcherJob?.cancel()
            duckRecoveryWatcherJob?.cancel()
            isDuckedByOtherAudio = false
            playWithFocusCheck()
        }
    }

    fun seekTo(positionMs: Long) {
        val safePosition = positionMs.coerceIn(0L, _uiState.value.totalDuration.coerceAtLeast(1L))
        exoPlayer.seekTo(safePosition)
        _currentPosition.value = safePosition
        _uiState.value.currentSong?.let { song ->
            saveTrackPositionWithSmartPolicy(song, safePosition, effectiveDurationMs(song))
        }
        val currentState = _uiState.value
        if (currentState.currentLyrics.isNotEmpty()) {
            val idx = com.kexora.soniclet.lyrics.LyricsUtil.findActiveLyricIndex(
                currentState.currentLyrics,
                safePosition + currentState.lyricsSyncOffsetMs
            )
            if (idx != currentState.activeLyricIndex) {
                _uiState.update { it.copy(activeLyricIndex = idx) }
            }
        }
        MediaNotificationManager.updatePlaybackState(
            isPlaying = exoPlayer.isPlaying,
            currentPositionMs = safePosition,
            durationMs = _uiState.value.totalDuration,
            isShuffleEnabled = _uiState.value.isShuffleEnabled,
            repeatMode = _uiState.value.repeatMode,
            isFavorite = _uiState.value.currentSong?.let { _uiState.value.favoriteSongIds.contains(it.id) } ?: false
        )
        updateNotification()
    }

    fun playNext() {
        snapshotOutgoingTrackPositionBeforeSwitch()
        skipResumeEvaluationForNextTransition = false
        if (_uiState.value.repeatMode == RepeatMode.ONE) {
            val currentIndex = exoPlayer.currentMediaItemIndex
            val totalItems = exoPlayer.mediaItemCount
            if (totalItems > 1) {
                val nextIndex = (currentIndex + 1) % totalItems
                exoPlayer.seekToDefaultPosition(nextIndex)
                if (exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
                playWithFocusCheck()
                return
            }
        }
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            playWithFocusCheck()
        } else if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.seekToDefaultPosition(0)
            playWithFocusCheck()
        } else {
            val queue = _uiState.value.activeQueue.ifEmpty { _uiState.value.songList }
            val current = _uiState.value.currentSong
            if (queue.isNotEmpty()) {
                val curIdx = queue.indexOfFirst { it.id == current?.id }
                val nextIdx = if (curIdx != -1 && curIdx + 1 < queue.size) curIdx + 1 else 0
                playSong(queue[nextIdx])
            }
        }
    }

    fun skipToNext() = playNext()

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            // Step 5: Restarting current track via Previous clears saved position and seeks cleanly to 0L
            _uiState.value.currentSong?.let { song ->
                audioRepository.savePlaybackPosition(song.id, 0L)
                persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDurationMs(song))
            }
            exoPlayer.seekTo(0L)
            _currentPosition.value = 0L
            updateNotification(overridePositionMs = 0L)
        } else if (_uiState.value.repeatMode == RepeatMode.ONE) {
            val currentIndex = exoPlayer.currentMediaItemIndex
            val totalItems = exoPlayer.mediaItemCount
            if (totalItems > 1) {
                snapshotOutgoingTrackPositionBeforeSwitch()
                skipResumeEvaluationForNextTransition = false
                val prevIndex = if (currentIndex - 1 < 0) totalItems - 1 else currentIndex - 1
                exoPlayer.seekToDefaultPosition(prevIndex)
                if (exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
                playWithFocusCheck()
                return
            }
            _uiState.value.currentSong?.let { song ->
                audioRepository.savePlaybackPosition(song.id, 0L)
                persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDurationMs(song))
            }
            exoPlayer.seekTo(0L)
            _currentPosition.value = 0L
            updateNotification(overridePositionMs = 0L)
        } else if (exoPlayer.hasPreviousMediaItem()) {
            snapshotOutgoingTrackPositionBeforeSwitch()
            skipResumeEvaluationForNextTransition = false
            exoPlayer.seekToPreviousMediaItem()
            playWithFocusCheck()
        } else if (exoPlayer.mediaItemCount > 0) {
            snapshotOutgoingTrackPositionBeforeSwitch()
            skipResumeEvaluationForNextTransition = false
            exoPlayer.seekToDefaultPosition(exoPlayer.mediaItemCount - 1)
            playWithFocusCheck()
        } else {
            val queue = _uiState.value.activeQueue.ifEmpty { _uiState.value.songList }
            val current = _uiState.value.currentSong
            if (queue.isNotEmpty()) {
                val curIdx = queue.indexOfFirst { it.id == current?.id }
                val prevIdx = if (curIdx > 0) curIdx - 1 else queue.size - 1
                playSong(queue[prevIdx])
            } else {
                current?.let { song ->
                    audioRepository.savePlaybackPosition(song.id, 0L)
                    persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDurationMs(song))
                }
                exoPlayer.seekTo(0L)
                _currentPosition.value = 0L
                updateNotification(overridePositionMs = 0L)
            }
        }
    }

    fun toggleShuffle() {
        val nextMode = when (_uiState.value.shuffleMode) {
            ShuffleMode.OFF -> ShuffleMode.STANDARD
            ShuffleMode.STANDARD -> ShuffleMode.SMART
            ShuffleMode.SMART -> ShuffleMode.OFF
        }
        setShuffleMode(nextMode)
    }

    fun setShuffleMode(mode: ShuffleMode) {
        val isShuffle = (mode != ShuffleMode.OFF)
        _uiState.update { it.copy(shuffleMode = mode, isShuffleEnabled = isShuffle) }
        audioRepository.setShuffleMode(mode)
        when (mode) {
            ShuffleMode.OFF -> {
                exoPlayer.shuffleModeEnabled = false
                try { Toast.makeText(context, "Shuffle: OFF", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            }
            ShuffleMode.STANDARD -> {
                val totalCount = exoPlayer.mediaItemCount
                if (totalCount > 1) {
                    exoPlayer.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(totalCount, System.currentTimeMillis()))
                }
                exoPlayer.shuffleModeEnabled = true
                try { Toast.makeText(context, "🔀 Shuffle: ON (Random)", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            }
            ShuffleMode.SMART -> {
                applySmartShuffleToActiveQueue()
                try { Toast.makeText(context, "✨ Smart Shuffle: Prioritizing less played tracks with balanced genres", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            }
        }
        syncQueueFromExoPlayer()
        updateNotification()
    }

    fun applySmartShuffleToActiveQueue() {
        val totalCount = exoPlayer.mediaItemCount
        if (totalCount <= 1) {
            exoPlayer.shuffleModeEnabled = false
            return
        }

        val currentIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, totalCount - 1)
        val indexedSongs = mutableListOf<Pair<Int, Song>>()
        for (index in 0 until totalCount) {
            val mediaItem = exoPlayer.getMediaItemAt(index)
            val songIdString = mediaItem.mediaId.removePrefix("manual_").substringBefore("_")
            val songId = songIdString.toLongOrNull()
            val song = songId?.let { id -> _uiState.value.allSongsList.find { it.id == id } }
            if (song != null) {
                indexedSongs.add(index to song)
            }
        }

        if (indexedSongs.isEmpty()) {
            exoPlayer.shuffleModeEnabled = false
            return
        }

        val otherSongsWithIndex = indexedSongs.filter { it.first != currentIndex }
        val otherSongs = otherSongsWithIndex.map { it.second }

        val smartOrderedOtherSongs = SmartShuffleUtil.generateSmartShuffle(
            songs = otherSongs,
            playCounts = _uiState.value.playCounts,
            playDurations = _uiState.value.playDurations,
            recentlyPlayedIds = _uiState.value.recentlyPlayedSongIds,
            currentSongId = null
        )

        val remainingIndicesMap = otherSongsWithIndex.groupBy { it.second.id }
            .mapValues { it.value.map { pair -> pair.first }.toMutableList() }

        val smartIndices = mutableListOf<Int>()
        smartIndices.add(currentIndex)

        for (song in smartOrderedOtherSongs) {
            val list = remainingIndicesMap[song.id]
            if (!list.isNullOrEmpty()) {
                smartIndices.add(list.removeAt(0))
            }
        }

        for (i in 0 until totalCount) {
            if (!smartIndices.contains(i)) {
                smartIndices.add(i)
            }
        }

        exoPlayer.setShuffleOrder(SmartShuffleOrder(smartIndices.toIntArray()))
        exoPlayer.shuffleModeEnabled = true
    }

    fun playSmartShuffled(songs: List<Song>, sourceTitle: String = "Smart Shuffle") {
        if (songs.isEmpty()) return
        val smartOrdered = SmartShuffleUtil.generateSmartShuffle(
            songs = songs,
            playCounts = _uiState.value.playCounts,
            playDurations = _uiState.value.playDurations,
            recentlyPlayedIds = _uiState.value.recentlyPlayedSongIds
        )
        audioRepository.setShuffleMode(ShuffleMode.SMART)
        try {
            Toast.makeText(context, "✨ Smart Shuffle: Playing ${smartOrdered.size} tracks prioritized by play history", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        playQueue(smartOrdered, 0, "$sourceTitle (Smart)")
    }

    fun toggleRepeatMode() {
        val nextMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.update { it.copy(repeatMode = nextMode) }
        exoPlayer.repeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        audioRepository.setRepeatMode(nextMode.name)
        syncQueueFromExoPlayer()
        updateNotification()
    }

    // 1.1 A-B Repeat Loop Controller
    fun setAbRepeatPointA() {
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        _uiState.update {
            it.copy(
                abRepeatPointA = currentPos,
                abRepeatPointB = null,
                isAbRepeatActive = false
            )
        }
    }

    fun setAbRepeatPointB() {
        val a = _uiState.value.abRepeatPointA ?: return
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (currentPos > a + 500L) {
            _uiState.update {
                it.copy(
                    abRepeatPointB = currentPos,
                    isAbRepeatActive = true
                )
            }
        }
    }

    fun toggleAbRepeatActive() {
        val state = _uiState.value
        if (state.abRepeatPointA != null && state.abRepeatPointB != null) {
            val newActive = !state.isAbRepeatActive
            _uiState.update { it.copy(isAbRepeatActive = newActive) }
            if (newActive) {
                exoPlayer.seekTo(state.abRepeatPointA)
            }
        }
    }

    fun clearAbRepeat() {
        _uiState.update {
            it.copy(
                isAbRepeatActive = false,
                abRepeatPointA = null,
                abRepeatPointB = null
            )
        }
    }

    // SAVED QUEUES & QUEUE MANAGEMENT
    fun saveCurrentQueue(name: String) {
        val currentQueue = _uiState.value.activeQueue
        if (currentQueue.isEmpty()) return
        audioRepository.createSavedQueue(name, currentQueue.map { it.id })
    }

    fun loadSavedQueue(queueId: String) {
        val savedQueue = _uiState.value.savedQueues.find { it.id == queueId } ?: return
        val allSongs = _uiState.value.allSongsList.ifEmpty { _uiState.value.songList }
        val mappedSongs = savedQueue.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
        if (mappedSongs.isNotEmpty()) {
            playQueue(mappedSongs, 0, savedQueue.name)
        }
    }

    fun deleteSavedQueue(queueId: String) = audioRepository.deleteSavedQueue(queueId)

    fun insertNext(song: Song) {
        val uid = "manual_${song.id}_${System.currentTimeMillis()}"
        val mediaItem = MediaItem.Builder().setMediaId(uid).setUri(song.contentUri).build()
        val insertIndex = if (exoPlayer.currentMediaItemIndex != C.INDEX_UNSET) {
            exoPlayer.currentMediaItemIndex + 1
        } else {
            exoPlayer.mediaItemCount
        }
        exoPlayer.addMediaItem(insertIndex, mediaItem)
        syncQueueFromExoPlayer()
    }

    fun addToQueue(song: Song) {
        val uid = "manual_${song.id}_${System.currentTimeMillis()}"
        val mediaItem = MediaItem.Builder().setMediaId(uid).setUri(song.contentUri).build()
        exoPlayer.addMediaItem(exoPlayer.mediaItemCount, mediaItem)
        syncQueueFromExoPlayer()
    }

    fun addSongsToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        songs.forEach { song ->
            val uid = "manual_${song.id}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
            val mediaItem = MediaItem.Builder().setMediaId(uid).setUri(song.contentUri).build()
            exoPlayer.addMediaItem(exoPlayer.mediaItemCount, mediaItem)
        }
        syncQueueFromExoPlayer()
    }

    fun removeFromQueue(songId: Long) {
        val totalCount = exoPlayer.mediaItemCount
        if (totalCount == 0) return

        val currentIndex = exoPlayer.currentMediaItemIndex

        if (totalCount == 1) {
            val item = exoPlayer.getMediaItemAt(0)
            val extractedId = item.mediaId.removePrefix("manual_").substringBefore("_").toLongOrNull()
            if (extractedId == songId) {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
                abandonAudioFocus()
                pausedByAudioFocus = false
                exoPlayer.clearMediaItems()
                _uiState.update { it.copy(currentSong = null, isPlaying = false, activeQueue = emptyList()) }
                MediaNotificationManager.dismissNotification(context)
                stopForegroundDelayJob?.cancel()
                MusicPlaybackService.stopForegroundService(context)
                return
            }
        }

        var targetIndex = -1
        if (currentIndex != C.INDEX_UNSET) {
            for (i in (currentIndex + 1) until totalCount) {
                val item = exoPlayer.getMediaItemAt(i)
                val extractedId = item.mediaId.removePrefix("manual_").substringBefore("_").toLongOrNull()
                if (extractedId == songId) {
                    targetIndex = i
                    break
                }
            }
        }

        if (targetIndex == -1 && currentIndex != C.INDEX_UNSET && currentIndex in 0 until totalCount) {
            val currentItem = exoPlayer.getMediaItemAt(currentIndex)
            val currentExtractedId = currentItem.mediaId.removePrefix("manual_").substringBefore("_").toLongOrNull()
            if (currentExtractedId == songId) {
                targetIndex = currentIndex
            }
        }

        if (targetIndex == -1) {
            for (i in 0 until totalCount) {
                val item = exoPlayer.getMediaItemAt(i)
                val extractedId = item.mediaId.removePrefix("manual_").substringBefore("_").toLongOrNull()
                if (extractedId == songId) {
                    targetIndex = i
                    break
                }
            }
        }

        if (targetIndex != -1) {
            if (targetIndex == currentIndex) {
                if (exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNextMediaItem()
                    exoPlayer.removeMediaItem(targetIndex)
                } else if (exoPlayer.hasPreviousMediaItem()) {
                    exoPlayer.seekToPreviousMediaItem()
                    exoPlayer.removeMediaItem(targetIndex)
                } else {
                    exoPlayer.removeMediaItem(targetIndex)
                }
            } else {
                exoPlayer.removeMediaItem(targetIndex)
            }
        }
        syncQueueFromExoPlayer()
    }

    fun clearQueue() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
        abandonAudioFocus()
        pausedByAudioFocus = false
        pausedByInterruption = false
        interruptionWatcherJob?.cancel()
        duckRecoveryWatcherJob?.cancel()
        isDuckedByOtherAudio = false
        exoPlayer.clearMediaItems()
        _uiState.update { it.copy(currentSong = null, isPlaying = false, activeQueue = emptyList()) }
        MediaNotificationManager.dismissNotification(context)
        stopForegroundDelayJob?.cancel()
        MusicPlaybackService.stopForegroundService(context)
        syncQueueFromExoPlayer()
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        val totalCount = exoPlayer.mediaItemCount
        if (fromIndex in 0 until totalCount && toIndex in 0 until totalCount && fromIndex != toIndex) {
            exoPlayer.moveMediaItem(fromIndex, toIndex)
            syncQueueFromExoPlayer()
        }
    }

    fun moveQueueItemToNext(fromIndex: Int) {
        val totalCount = exoPlayer.mediaItemCount
        val currentIndex = exoPlayer.currentMediaItemIndex
        val targetIndex = if (currentIndex != C.INDEX_UNSET) (currentIndex + 1).coerceAtMost(totalCount - 1) else 0
        if (fromIndex in 0 until totalCount && fromIndex != targetIndex) {
            exoPlayer.moveMediaItem(fromIndex, targetIndex)
            syncQueueFromExoPlayer()
        }
    }

    fun moveQueueItemToBottom(fromIndex: Int) {
        val totalCount = exoPlayer.mediaItemCount
        if (fromIndex in 0 until totalCount && fromIndex != totalCount - 1) {
            exoPlayer.moveMediaItem(fromIndex, totalCount - 1)
            syncQueueFromExoPlayer()
        }
    }

    private fun finalizePreviousListeningSession(endedNaturally: Boolean = false) {
        val prevSong = activeListeningSong ?: return
        val totalSessionMs = activeSongSessionListeningMs
        val remainingDelta = pendingUnflushedListenMs
        val wasQualified = hasQualifiedActivePlay

        activeListeningSong = null
        activeSongSessionListeningMs = 0L
        pendingUnflushedListenMs = 0L
        hasQualifiedActivePlay = false

        try {
            if (wasQualified) {
                if (remainingDelta > 0L) {
                    audioRepository.recordListeningDuration(prevSong.id, remainingDelta)
                    viewModelScope.launch(Dispatchers.IO) {
                        listeningHistoryRepository.addListeningDurationDelta(prevSong, remainingDelta)
                    }
                }
                audioRepository.flushPlayDurations()
                val completionRatio = when {
                    endedNaturally -> 1.0f
                    prevSong.duration > 0L -> (totalSessionMs.toFloat() / prevSong.duration.toFloat()).coerceIn(0f, 1.5f)
                    else -> 0.5f
                }
                audioRepository.recordCompletionRatio(prevSong.id, completionRatio)
                UserBehaviorProfiler.recordPlayEvent(context, prevSong, completionRatio)
            } else if (!endedNaturally && totalSessionMs in 300L until 5_000L) {
                // Layer 2: Rapid skip (< 5 seconds of actual playback)
                audioRepository.recordRapidSkip(prevSong.id)
                UserBehaviorProfiler.recordPlayEvent(context, prevSong, -1.0f)
            }
        } catch (_: Exception) {}
    }

    private fun onSongEnded() {
        finalizePreviousListeningSession(endedNaturally = true)
        if (_uiState.value.sleepTimerOption == SleepTimerOption.END_OF_SONG) {
            exoPlayer.pause()
            _uiState.update { it.copy(sleepTimerOption = SleepTimerOption.OFF, sleepTimerRemainingMs = 0L) }
        }
    }

    fun playFromBeginning() {
        _uiState.value.currentSong?.let { song ->
            // Step 5: Explicit Play from Beginning bypasses saved resume and clears stored position
            bypassResumeForNextTransition = true
            audioRepository.savePlaybackPosition(song.id, 0L)
            persistLastSessionState(song = song, positionMs = 0L, durationMs = effectiveDurationMs(song))
            _currentPosition.value = 0L
            if (exoPlayer.mediaItemCount > 0) {
                exoPlayer.seekTo(0L)
                if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.prepare()
                }
            }
            if (!exoPlayer.isPlaying) {
                playWithFocusCheck(forceStartFromBeginning = true)
            }
            bypassResumeForNextTransition = false
            updateNotification(overridePositionMs = 0L)
        }
    }

    private fun startPositionTracker() {
        stopPositionTracker()
        positionUpdateJob = viewModelScope.launch {
            var lastSavedPos = -1L
            var lastWidgetSyncTime = 0L
            var lastPlaybackStateSyncTime = 0L
            var lastTrackTimestamp = System.currentTimeMillis()
            while (exoPlayer.isPlaying) {
                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                _currentPosition.value = currentPos

                val now = System.currentTimeMillis()

                if (now - lastPlaybackStateSyncTime > 15000L) {
                    lastPlaybackStateSyncTime = now
                    MediaNotificationManager.updatePlaybackState(
                        isPlaying = true,
                        currentPositionMs = currentPos,
                        durationMs = duration,
                        isShuffleEnabled = _uiState.value.isShuffleEnabled,
                        repeatMode = _uiState.value.repeatMode,
                        isFavorite = _uiState.value.currentSong?.let { _uiState.value.favoriteSongIds.contains(it.id) } ?: false
                    )
                }

                val elapsedSinceLast = now - lastTrackTimestamp
                lastTrackTimestamp = now
                val currentSong = _uiState.value.currentSong
                if (currentSong != null && elapsedSinceLast in 20L..2500L) {
                    if (activeListeningSong?.id != currentSong.id) {
                        finalizePreviousListeningSession(endedNaturally = false)
                        activeListeningSong = currentSong
                        activeSongSessionListeningMs = 0L
                        pendingUnflushedListenMs = 0L
                        hasQualifiedActivePlay = false
                    }
                    activeSongSessionListeningMs += elapsedSinceLast
                    pendingUnflushedListenMs += elapsedSinceLast

                    // Layer 2: Count track as genuinely listened ONLY after >= 15 seconds of actual playback
                    if (!hasQualifiedActivePlay && activeSongSessionListeningMs >= ListeningHistoryRepository.MIN_QUALIFIED_LISTEN_MS) {
                        hasQualifiedActivePlay = true
                        val initialMs = pendingUnflushedListenMs
                        pendingUnflushedListenMs = 0L
                        audioRepository.incrementPlayCount(currentSong.id)
                        audioRepository.recordListeningDuration(currentSong.id, initialMs)
                        audioRepository.flushPlayDurations()
                        viewModelScope.launch(Dispatchers.IO) {
                            listeningHistoryRepository.recordQualifiedListen(
                                song = currentSong,
                                listenedMs = initialMs,
                                incrementPlayCount = true
                            )
                        }
                    } else if (hasQualifiedActivePlay && pendingUnflushedListenMs >= 10_000L) {
                        val deltaMs = pendingUnflushedListenMs
                        pendingUnflushedListenMs = 0L
                        audioRepository.recordListeningDuration(currentSong.id, deltaMs)
                        viewModelScope.launch(Dispatchers.IO) {
                            listeningHistoryRepository.addListeningDurationDelta(currentSong, deltaMs)
                        }
                    }
                }

                // Steps 2 & 3: Smart periodic save with near-end (92% / last 10s), early-start (< 10s), and short-track (< 60s) guards
                if (currentSong != null) {
                    val effectiveDur = if (duration > 0L) duration else currentSong.duration.coerceAtLeast(0L)
                    if (ListeningHistoryRepository.isTrackCompletedOrNearEnd(currentPos, effectiveDur)) {
                        if (audioRepository.getSavedPlaybackPosition(currentSong.id) > 0L || lastSavedPos != 0L) {
                            audioRepository.savePlaybackPosition(currentSong.id, 0L)
                            persistLastSessionState(song = currentSong, positionMs = 0L, durationMs = effectiveDur)
                            lastSavedPos = 0L
                        }
                    } else if (abs(currentPos - lastSavedPos) >= 5000L) {
                        saveTrackPositionWithSmartPolicy(currentSong, currentPos, effectiveDur)
                        lastSavedPos = currentPos
                    }
                }

                val abState = _uiState.value
                if (abState.isAbRepeatActive && abState.abRepeatPointA != null && abState.abRepeatPointB != null) {
                    val ptA = abState.abRepeatPointA
                    val ptB = abState.abRepeatPointB
                    if (currentPos >= ptB || currentPos < ptA) {
                        exoPlayer.seekTo(ptA)
                        _currentPosition.value = ptA
                    }
                }

                val lyrics = _uiState.value.currentLyrics
                val offset = _uiState.value.lyricsSyncOffsetMs
                val effectivePos = currentPos + offset
                if (lyrics.isNotEmpty()) {
                    val idx = com.kexora.soniclet.lyrics.LyricsUtil.findActiveLyricIndex(lyrics, effectivePos)
                    if (idx != _uiState.value.activeLyricIndex) {
                        _uiState.update { it.copy(activeLyricIndex = idx) }
                    }
                }

                if (duration > 0L && duration != _uiState.value.totalDuration) {
                    _uiState.update { it.copy(totalDuration = duration) }
                }

                if (now - lastWidgetSyncTime >= 1000L) {
                    lastWidgetSyncTime = now
                    val liveSync = SonicletWidgetManager.getPrefs(context).getBoolean(SonicletWidgetManager.KEY_LIVE_SYNC, true)
                    if (liveSync) {
                        WidgetController.savePlaybackState(
                            context = context,
                            song = _uiState.value.currentSong,
                            isPlaying = exoPlayer.isPlaying,
                            positionMs = currentPos,
                            durationMs = duration,
                            isShuffle = _uiState.value.isShuffleEnabled,
                            repeatMode = _uiState.value.repeatMode,
                            isFavorite = _uiState.value.currentSong?.let { _uiState.value.favoriteSongIds.contains(it.id) } ?: false
                        )
                    }
                }

                if (_uiState.value.isCrossfadeEnabled && _uiState.value.repeatMode != RepeatMode.ONE && !hasCrossfadedCurrentSong) {
                    val crossfadeDurationMs = (_uiState.value.crossfadeDurationSeconds * 1000L).coerceIn(1000L, 5000L)
                    if (duration > crossfadeDurationMs + 3000L && currentPos >= duration - crossfadeDurationMs) {
                        hasCrossfadedCurrentSong = true
                        crossfadeJob?.cancel()
                        crossfadeJob = viewModelScope.launch {
                            val steps = (crossfadeDurationMs / 100L).toInt().coerceAtLeast(1)
                            val stepDelay = crossfadeDurationMs / steps
                            for (i in 0..steps) {
                                if (!exoPlayer.isPlaying) break
                                val ratio = 1f - (i.toFloat() / steps.toFloat())
                                exoPlayer.volume = ratio.coerceIn(0.15f, 1f)
                                delay(stepDelay)
                            }
                        }
                    }
                }

                // 50ms high-frequency ticker whenever lyrics are loaded & Now Playing is active
                val tickDelayMs = if (lyrics.isNotEmpty() && _uiState.value.currentTab == NavigationTab.NOW_PLAYING) 50L else 100L
                delay(tickDelayMs)
            }
        }
    }

    private fun stopPositionTracker() {
        val activeSong = activeListeningSong
        if (hasQualifiedActivePlay && activeSong != null && pendingUnflushedListenMs > 0L) {
            val deltaMs = pendingUnflushedListenMs
            pendingUnflushedListenMs = 0L
            audioRepository.recordListeningDuration(activeSong.id, deltaMs)
            audioRepository.flushPlayDurations()
            viewModelScope.launch(Dispatchers.IO) {
                listeningHistoryRepository.addListeningDurationDelta(activeSong, deltaMs)
            }
        }
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun updateNotification(overridePositionMs: Long? = null, overrideIsPlaying: Boolean? = null) {
        val song = _uiState.value.currentSong ?: return
        val isPlaying = overrideIsPlaying ?: _uiState.value.isPlaying
        val currentPos = (overridePositionMs ?: exoPlayer.currentPosition).coerceAtLeast(0L)
        val duration = if (exoPlayer.duration > 0) exoPlayer.duration else (if (song.duration > 0) song.duration else 0L)
        val isShuffle = _uiState.value.isShuffleEnabled
        val repeatMode = _uiState.value.repeatMode
        val isFav = _uiState.value.favoriteSongIds.contains(song.id)

        MediaNotificationManager.showNotification(
            context = context,
            song = song,
            isPlaying = isPlaying,
            currentPositionMs = currentPos,
            durationMs = duration,
            isShuffleEnabled = isShuffle,
            repeatMode = repeatMode,
            isFavorite = isFav,
            activeQueue = _uiState.value.activeQueue
        )

        WidgetController.savePlaybackState(
            context = context,
            song = song,
            isPlaying = isPlaying,
            positionMs = currentPos,
            durationMs = duration,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            isFavorite = isFav
        )
    }

    override fun onPlay() {
        if (!exoPlayer.isPlaying) {
            playWithFocusCheck()
        }
    }

    override fun onPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }

    override fun onTogglePlayPause() = togglePlayPause()
    override fun onPlayNext() = playNext()
    override fun onPlayPrevious() = playPrevious()

    override fun onRewind() {
        val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
        seekTo(newPos)
    }

    override fun onFastForward() {
        val dur = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
        val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(dur)
        seekTo(newPos)
    }

    override fun onSeekTo(positionMs: Long) = seekTo(positionMs)
    override fun onToggleShuffle() = toggleShuffle()

    override fun onSetShuffleMode(enabled: Boolean) {
        val target = if (enabled) ShuffleMode.STANDARD else ShuffleMode.OFF
        setShuffleMode(target)
    }

    override fun onToggleRepeat() = toggleRepeatMode()

    override fun onSetRepeatMode(repeatMode: RepeatMode) {
        audioRepository.setRepeatMode(repeatMode.name)
        syncQueueFromExoPlayer()
        updateNotification()
    }

    override fun onToggleFavorite() {
        _uiState.value.currentSong?.let { song ->
            toggleFavorite(song.id)
        }
    }

    override fun refreshNotification() = updateNotification()

    override fun onHeadphoneClick(count: Int) {
        val action = when (count) {
            1 -> _uiState.value.singleClickAction
            2 -> _uiState.value.doubleClickAction
            3 -> _uiState.value.tripleClickAction
            else -> "None"
        }
        executeCustomAction(action)
    }

    private fun executeCustomAction(action: String) {
        when (action) {
            "Play/Pause" -> onTogglePlayPause()
            "Next" -> onPlayNext()
            "Previous" -> onPlayPrevious()
            "Fast Forward" -> onFastForward()
            "Rewind" -> onRewind()
            "Favorite" -> onToggleFavorite()
            "Volume Up" -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            "Volume Down" -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    override fun onStopPlayback() {
        persistPlaybackStateSafely()
        crossfadeJob?.cancel()
        volumeFadeJob?.cancel()
        interruptionWatcherJob?.cancel()
        duckRecoveryWatcherJob?.cancel()
        isDuckedByOtherAudio = false
        exoPlayer.volume = 1.0f
        exoPlayer.pause()
        abandonAudioFocus()
        pausedByAudioFocus = false
        pausedByInterruption = false
        _uiState.update { it.copy(isPlaying = false) }
        MediaNotificationManager.dismissNotification(context)
        stopForegroundDelayJob?.cancel()
        MusicPlaybackService.stopForegroundService(context)
    }

    fun stopAndHideMiniPlayerForRadio() {
        persistPlaybackStateSafely()
        crossfadeJob?.cancel()
        volumeFadeJob?.cancel()
        interruptionWatcherJob?.cancel()
        duckRecoveryWatcherJob?.cancel()
        stopForegroundDelayJob?.cancel()
        isDuckedByOtherAudio = false
        exoPlayer.volume = 1.0f
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
        exoPlayer.stop()
        abandonAudioFocus()
        pausedByAudioFocus = false
        pausedByInterruption = false
        _uiState.update {
            it.copy(
                isPlaying = false,
                currentSong = null
            )
        }
    }

    override fun onCleared() {
        persistPlaybackStateSafely()
        super.onCleared()
        libVm.unregisterMediaStoreObserver()
        try {
            context.unregisterReceiver(audioBecomingNoisyReceiver)
            context.unregisterReceiver(headsetPlugReceiver)
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        fxVm.releaseEffects()
        bassVibrationManager.stop()
        crossfadeJob?.cancel()
        volumeGuardJob?.cancel()
        stopPositionTracker()
        MediaNotificationManager.dismissNotification(context)
        MusicPlaybackService.stopForegroundService(context)
        exoPlayer.removeListener(playerListener)
        exoPlayer.stop()
        exoPlayer.release()
    }

    fun showNotificationBanner(message: String) {
        _uiState.update { it.copy(userNotificationBanner = message) }
    }

    fun clearNotificationBanner() {
        _uiState.update { it.copy(userNotificationBanner = null) }
    }

    // DELEGATED LIBRARY, FOLDER, PLAYLIST, SEARCH & MULTI-SELECT METHODS (LibVm)
    val manualFolderUris: StateFlow<Set<String>> = audioRepository.manualFolderUris
    fun onAppResumeCheckNewSongs() = libVm.onAppResumeCheckNewSongs()
    fun triggerAutoScanDebounced(delayMs: Long = 1500L) = libVm.triggerAutoScanDebounced(delayMs)
    fun loadAudioFiles(forceSync: Boolean = false) = libVm.loadAudioFiles(forceSync)
    fun setHomeCategory(category: String) = libVm.setHomeCategory(category)
    fun refreshHomeRecommendations() = libVm.refreshHomeRecommendations()
    fun removeWrappedHistoryItem(songKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            listeningHistoryRepository.removeHistoryEntry(songKey)
        }
    }
    fun updateSearchQuery(query: String) = libVm.updateSearchQuery(query)
    fun addRecentSearch(query: String) = libVm.addRecentSearch(query)
    fun removeRecentSearch(query: String) = libVm.removeRecentSearch(query)
    fun clearRecentSearches() = libVm.clearRecentSearches()
    fun setSortOption(option: SortOption) = libVm.setSortOption(option)
    fun toggleSortDirection() = libVm.toggleSortDirection()
    fun setFormatFilter(format: String) = libVm.setFormatFilter(format)
    fun setDurationCategoryFilter(category: String) = libVm.setDurationCategoryFilter(category)
    fun setAttributeFilter(attribute: String) = libVm.setAttributeFilter(attribute)
    fun resetAllSongFilters() = libVm.resetAllSongFilters()
    fun applySongFiltersAndSort(
        sortOption: SortOption,
        sortAscending: Boolean,
        formatFilter: String,
        durationCategoryFilter: String,
        attributeFilter: String,
        isGridMode: Boolean? = null
    ) = libVm.applySongFiltersAndSort(sortOption, sortAscending, formatFilter, durationCategoryFilter, attributeFilter, isGridMode)
    fun toggleGridMode() = libVm.toggleGridMode()
    fun addManualFolder(uri: Uri) = libVm.addManualFolder(uri)
    fun removeManualFolder(uriString: String) = libVm.removeManualFolder(uriString)
    fun setIncludeSystemHiddenInLibrary(enabled: Boolean) = libVm.setIncludeSystemHiddenInLibrary(enabled)
    fun excludeFolder(folderPath: String) = libVm.excludeFolder(folderPath)
    fun excludeJunkFolders() = libVm.excludeJunkFolders()
    fun restoreFolder(folderPath: String) = libVm.restoreFolder(folderPath)
    fun restoreAllExcludedFolders() = libVm.restoreAllExcludedFolders()
    fun excludeSong(songId: Long) = libVm.excludeSong(songId)
    fun restoreSong(songId: Long) = libVm.restoreSong(songId)
    fun restoreAllExcludedSongs() = libVm.restoreAllExcludedSongs()
    fun setShowExcludedFoldersDialog(show: Boolean) = libVm.setShowExcludedFoldersDialog(show)
    fun toggleFavorite(songId: Long) = libVm.toggleFavorite(songId)
    fun setSongMood(songId: Long, mood: String?) = libVm.setSongMood(songId, mood)
    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) = libVm.createPlaylist(name, initialSongs)
    fun openOrCreateDynamicPlaylist(name: String, songs: List<Song>) = libVm.openOrCreateDynamicPlaylist(name, songs)
    fun renamePlaylist(playlistId: String, newName: String) = libVm.renamePlaylist(playlistId, newName)
    fun deletePlaylist(playlistId: String) = libVm.deletePlaylist(playlistId)
    fun addSongsToPlaylist(playlistId: String, songs: List<Song>) = libVm.addSongsToPlaylist(playlistId, songs)
    fun removeSongFromPlaylist(playlistId: String, songId: Long) = libVm.removeSongFromPlaylist(playlistId, songId)
    fun updatePlaylistCover(playlistId: String, coverUri: String?) = libVm.updatePlaylistCover(playlistId, coverUri)
    fun updatePlaylistGradient(playlistId: String, gradientIndex: Int) = libVm.updatePlaylistGradient(playlistId, gradientIndex)
    fun reorderPlaylistSongs(playlistId: String, newSongIds: List<Long>) = libVm.reorderPlaylistSongs(playlistId, newSongIds)
    fun openPlaylistDetails(playlistId: String) = libVm.openPlaylistDetails(playlistId)
    fun openSmartPlaylistDetails(smartType: String) = libVm.openSmartPlaylistDetails(smartType)
    fun openTrimDialog(song: Song) = libVm.openTrimDialog(song)
    fun closeTrimDialog() = libVm.closeTrimDialog()
    fun openSongDetails(song: Song) = libVm.openSongDetails(song)
    fun openCategory(name: String, type: String, songs: List<Song>) = libVm.openCategory(name, type, songs)
    fun openCategoryAndClosePlayer(name: String, type: String, songs: List<Song>) = libVm.openCategoryAndClosePlayer(name, type, songs)
    fun closeCategory() = libVm.closeCategory()
    suspend fun updateSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String,
        newAlbumArtUri: Uri? = null,
        newAlbumArtist: String? = null,
        newTrackNumber: String? = null,
        newComposer: String? = null,
        newComment: String? = null,
        newLyrics: String? = null
    ): Boolean = libVm.updateSongMetadata(
        songId = songId,
        newTitle = newTitle,
        newArtist = newArtist,
        newAlbum = newAlbum,
        newYear = newYear,
        newGenre = newGenre,
        newAlbumArtUri = newAlbumArtUri,
        newAlbumArtist = newAlbumArtist,
        newTrackNumber = newTrackNumber,
        newComposer = newComposer,
        newComment = newComment,
        newLyrics = newLyrics
    )
    fun setMinDurationFilter(durationMs: Long) = libVm.setMinDurationFilter(durationMs)
    fun analyzeDuplicateGroups(
        mode: DuplicateScanMode = DuplicateScanMode.SMART_MATCH
    ): List<DuplicateGroup> = libVm.analyzeDuplicateGroups(mode)
    fun findDuplicateSongs(): List<List<Song>> = libVm.findDuplicateSongs()
    fun onSongsExternallyDeleted(songIds: Collection<Long>) = libVm.onSongsExternallyDeleted(songIds)
    suspend fun deleteSongPhysical(song: Song): Boolean = libVm.deleteSongPhysical(song)
    fun deleteSong(song: Song, onResult: ((Boolean) -> Unit)? = null) = libVm.deleteSong(song, onResult)
    suspend fun moveSongPhysical(song: Song, targetFolderPath: String): Boolean = libVm.moveSongPhysical(song, targetFolderPath)
    suspend fun copySongPhysical(song: Song, targetFolderPath: String): Boolean = libVm.copySongPhysical(song, targetFolderPath)
    fun createFolder(parentPath: String, folderName: String, onResult: (Boolean, String) -> Unit) = libVm.createFolder(parentPath, folderName, onResult)
    fun renameFolder(oldPath: String, newName: String, onResult: (Boolean, String) -> Unit) = libVm.renameFolder(oldPath, newName, onResult)
    fun deleteFolder(folderPath: String, onResult: (Boolean) -> Unit) = libVm.deleteFolder(folderPath, onResult)
    fun moveSongsBatch(songs: List<Song>, targetFolderPath: String, onComplete: (Int, Int) -> Unit) = libVm.moveSongsBatch(songs, targetFolderPath, onComplete)
    fun copySongsBatch(songs: List<Song>, targetFolderPath: String, onComplete: (Int, Int) -> Unit) = libVm.copySongsBatch(songs, targetFolderPath, onComplete)
    fun rescanStorage() = libVm.rescanStorage()
    fun setShowCreatePlaylistDialog(show: Boolean) = libVm.setShowCreatePlaylistDialog(show)
    fun toggleSongSelection(songId: Long) = libVm.toggleSongSelection(songId)
    fun selectAllSongs(songIds: Collection<Long>) = libVm.selectAllSongs(songIds)
    fun clearSelectedSongs() = libVm.clearSelectedSongs()
    fun getSelectedSongs(): List<Song> = libVm.getSelectedSongs()
    fun playSelectedSongs() = libVm.playSelectedSongs()
    fun insertSelectedSongsNext() = libVm.insertSelectedSongsNext()
    fun addSelectedSongsToQueue() = libVm.addSelectedSongsToQueue()
    fun addSelectedSongsToPlaylist(playlistId: String) = libVm.addSelectedSongsToPlaylist(playlistId)
    fun toggleFavoriteSelectedSongs() = libVm.toggleFavoriteSelectedSongs()
    fun excludeSelectedSongs() = libVm.excludeSelectedSongs()
    fun removeSelectedSongsFromPlaylist(playlistId: String) = libVm.removeSelectedSongsFromPlaylist(playlistId)
    fun removeSelectedSongsFromQueue() = libVm.removeSelectedSongsFromQueue()
    fun shareSong(context: Context, song: Song) = libVm.shareSong(context, song)
    fun shareSongs(context: Context, songs: List<Song>) = libVm.shareSongs(context, songs)
    fun shareSelectedSongs(context: Context) = libVm.shareSelectedSongs(context)
    fun deleteSelectedSongs(onComplete: ((Int) -> Unit)? = null) = libVm.deleteSelectedSongs(onComplete)

    // DELEGATED EQUALIZER, FX, SLEEP TIMER, LYRICS & THEME SETTINGS METHODS (FxVm)
    fun setEqEnabled(enabled: Boolean) = fxVm.setEqEnabled(enabled)
    fun setReverbEnabled(enabled: Boolean) = fxVm.setReverbEnabled(enabled)
    fun setBassBoostEnabled(enabled: Boolean) = fxVm.setBassBoostEnabled(enabled)
    fun setVirtualizerEnabled(enabled: Boolean) = fxVm.setVirtualizerEnabled(enabled)
    fun setVolumeBoostEnabled(enabled: Boolean) = fxVm.setVolumeBoostEnabled(enabled)
    fun setDolbyEnabled(enabled: Boolean) = fxVm.setDolbyEnabled(enabled)
    fun setLosslessEnabled(enabled: Boolean) = fxVm.setLosslessEnabled(enabled)
    fun setBassVibrationEnabled(enabled: Boolean) = fxVm.setBassVibrationEnabled(enabled)
    fun setBassVibrationStrength(strength: Int) = fxVm.setBassVibrationStrength(strength)
    fun setBassVibrationMode(mode: String) = fxVm.setBassVibrationMode(mode)
    fun testBassVibration() = fxVm.testBassVibration()
    fun setEqualizerBandLevel(bandIndex: Int, level: Int) = fxVm.setEqualizerBandLevel(bandIndex, level)
    fun setBassBoostLevel(level: Int) = fxVm.setBassBoostLevel(level)
    fun setVocalBoostLevel(level: Int) = fxVm.setVocalBoostLevel(level)
    fun setTrebleBoostLevel(level: Int) = fxVm.setTrebleBoostLevel(level)
    fun setVirtualizerLevel(level: Int) = fxVm.setVirtualizerLevel(level)
    fun setVolumeBoostLevel(level: Int) = fxVm.setVolumeBoostLevel(level)
    fun setReverbPreset(preset: Short) = fxVm.setReverbPreset(preset)
    fun applyPreset(name: String, levels: List<Int>, bass: Int, vocal: Int, treble: Int, virt: Int) =
        fxVm.applyPreset(name, levels, bass, vocal, treble, virt)
    fun set8dAudioEnabled(enabled: Boolean) = fxVm.set8dAudioEnabled(enabled)
    fun resetAllAudioFx() = fxVm.resetAllAudioFx()
    fun resetEqualizerToDefault() = fxVm.resetEqualizerToDefault()
    fun saveCustomPreset(name: String) = fxVm.saveCustomPreset(name)
    fun deleteCustomPreset(name: String) = fxVm.deleteCustomPreset(name)
    fun setFinishTrackBeforeSleep(enabled: Boolean) = fxVm.setFinishTrackBeforeSleep(enabled)
    fun setSleepTimer(option: SleepTimerOption) = fxVm.setSleepTimer(option)
    fun setCustomSleepTimer(minutes: Int) = fxVm.setCustomSleepTimer(minutes)
    fun setPlaybackSpeed(speed: Float) = fxVm.setPlaybackSpeed(speed)
    fun setPlaybackPitch(pitch: Float) = fxVm.setPlaybackPitch(pitch)
    fun loadLyricsForSong(song: Song) = fxVm.loadLyricsForSong(song)
    fun importLyricsFromUri(song: Song, uri: Uri) = fxVm.importLyricsFromUri(song, uri)
    fun adjustLyricsOffset(deltaMs: Long) = fxVm.adjustLyricsOffset(deltaMs)
    fun resetLyricsOffset() = fxVm.resetLyricsOffset()
    fun toggleKaraokeMode() = fxVm.toggleKaraokeMode()
    fun clearImportedLyrics(song: Song) = fxVm.clearImportedLyrics(song)
    fun inspectAudioSpecsForSong(song: Song) = fxVm.inspectAudioSpecsForSong(song)
    fun getAudioSpecs(song: Song): AudioSpecs = fxVm.getAudioSpecs(song)
    fun setAccentColor(colorValue: Long) = fxVm.setAccentColor(colorValue)
    fun setCustomBackgroundUri(uri: String?) = fxVm.setCustomBackgroundUri(uri)
    fun setBackgroundMode(mode: String) = fxVm.setBackgroundMode(mode)
    fun setBgImageBlur(blur: Float) = fxVm.setBgImageBlur(blur)
    fun setBgImageDim(dim: Float) = fxVm.setBgImageDim(dim)
    fun setSolidBgColor(color: Long) = fxVm.setSolidBgColor(color)
    fun setSolidBgOpacity(opacity: Float) = fxVm.setSolidBgOpacity(opacity)
    fun setGradientPresetIndex(index: Int) = fxVm.setGradientPresetIndex(index)
    fun setCustomGradientColors(colors: List<Long>) = fxVm.setCustomGradientColors(colors)
    fun setGradientIntensity(intensity: Float) = fxVm.setGradientIntensity(intensity)
    fun setNowPlayingLayout(layout: String) = fxVm.setNowPlayingLayout(layout)
    fun setCornerRadiusScale(scale: Float) = fxVm.setCornerRadiusScale(scale)
    fun setFontStyle(style: String) = fxVm.setFontStyle(style)
    fun setMotionLevel(level: String) = fxVm.setMotionLevel(level)
    fun setGradientBlur(blur: Float) = fxVm.setGradientBlur(blur)
    fun setAppTheme(theme: AppTheme) = fxVm.setAppTheme(theme)
    fun setKariModeEnabled(enabled: Boolean) = fxVm.setKariModeEnabled(enabled)
    fun setKariBlur(blur: Float) = fxVm.setKariBlur(blur)
    fun setKariOpacity(opacity: Float) = fxVm.setKariOpacity(opacity)
    fun setShakeEnabled(enabled: Boolean) = fxVm.setShakeEnabled(enabled)
    fun setTwoFingerTapEnabled(enabled: Boolean) = fxVm.setTwoFingerTapEnabled(enabled)
    fun setTwoFingerTapAction(action: String) = fxVm.setTwoFingerTapAction(action)
    fun setThreeFingerSwipeEnabled(enabled: Boolean) = fxVm.setThreeFingerSwipeEnabled(enabled)
    fun setThreeFingerSwipeAction(action: String) = fxVm.setThreeFingerSwipeAction(action)
    fun setVolumeEdgeSwipeEnabled(enabled: Boolean) = fxVm.setVolumeEdgeSwipeEnabled(enabled)
    fun setLongPressDragSeekEnabled(enabled: Boolean) = fxVm.setLongPressDragSeekEnabled(enabled)
    fun setPlayTogetherEnabled(enabled: Boolean) = fxVm.setPlayTogetherEnabled(enabled)
    fun setPlayTogetherFadeEnabled(enabled: Boolean) = fxVm.setPlayTogetherFadeEnabled(enabled)
    fun setOneFingerSwipeBackEnabled(enabled: Boolean) = fxVm.setOneFingerSwipeBackEnabled(enabled)
    fun setTwoFingerSwipeHomeEnabled(enabled: Boolean) = fxVm.setTwoFingerSwipeHomeEnabled(enabled)
    fun setMiniPlayerGesturesEnabled(enabled: Boolean) = fxVm.setMiniPlayerGesturesEnabled(enabled)
    fun setFlipToPauseEnabled(enabled: Boolean) = fxVm.setFlipToPauseEnabled(enabled)
    fun setFlipToPauseAction(action: String) = fxVm.setFlipToPauseAction(action)
    fun setWristTwistEnabled(enabled: Boolean) = fxVm.setWristTwistEnabled(enabled)
    fun setWristTwistAction(action: String) = fxVm.setWristTwistAction(action)
    fun setPocketModeEnabled(enabled: Boolean) = fxVm.setPocketModeEnabled(enabled)
    fun setPocketModeAction(action: String) = fxVm.setPocketModeAction(action)
    fun setShakeAction(action: String) = fxVm.setShakeAction(action)
    fun setTiltToFastForwardEnabled(enabled: Boolean) = fxVm.setTiltToFastForwardEnabled(enabled)
    fun setAirWaveEnabled(enabled: Boolean) = fxVm.setAirWaveEnabled(enabled)
    fun setAirWaveAction(action: String) = fxVm.setAirWaveAction(action)
    fun setPickUpToPlayEnabled(enabled: Boolean) = fxVm.setPickUpToPlayEnabled(enabled)
    fun setCrossfadeEnabled(enabled: Boolean) = fxVm.setCrossfadeEnabled(enabled)
    fun setAutoResumeOnPlugEnabled(enabled: Boolean) = fxVm.setAutoResumeOnPlugEnabled(enabled)
    fun setAutoPlayOnBluetoothEnabled(enabled: Boolean) = fxVm.setAutoPlayOnBluetoothEnabled(enabled)
    fun setSingleClickAction(action: String) = fxVm.setSingleClickAction(action)
    fun setDoubleClickAction(action: String) = fxVm.setDoubleClickAction(action)
    fun setTripleClickAction(action: String) = fxVm.setTripleClickAction(action)
    fun setHearingSafetyEnabled(enabled: Boolean) = fxVm.setHearingSafetyEnabled(enabled)
    fun setGaplessPlaybackEnabled(enabled: Boolean) = fxVm.setGaplessPlaybackEnabled(enabled)
    fun setCrossfadeDuration(seconds: Int) = fxVm.setCrossfadeDuration(seconds)
    fun setResumeLastPositionEnabled(enabled: Boolean) = fxVm.setResumeLastPositionEnabled(enabled)
    fun setFirstLaunch(isFirst: Boolean) = fxVm.setFirstLaunch(isFirst)
    fun setMohModeActivated(activated: Boolean) = fxVm.setMohModeActivated(activated)
    fun refreshSpecialCelebrationDate() = fxVm.refreshSpecialCelebrationDate()

    class Factory(
        private val context: Context,
        private val repository: AudioRepository = AudioRepositoryImpl(DatabaseProvider.getDatabase(context).songDao(), context)
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MusicViewModel(repository, context) as T
        }
    }
}
