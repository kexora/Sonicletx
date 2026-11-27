package com.kexora.soniclet.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.gestures.SwipeBackContainer
import com.kexora.soniclet.library.AlbumsView
import com.kexora.soniclet.library.ArtistsView
import com.kexora.soniclet.library.DuplicatesScreen
import com.kexora.soniclet.library.Folders
import com.kexora.soniclet.library.GenresView
import com.kexora.soniclet.library.HiddenItems
import com.kexora.soniclet.library.HomeScreen
import com.kexora.soniclet.library.MoodsView
import com.kexora.soniclet.library.PlaylistDetails
import com.kexora.soniclet.library.Playlists
import com.kexora.soniclet.library.SongsLibraryView
import com.kexora.soniclet.library.StatsDashboard
import com.kexora.soniclet.player.DriveMode
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.NavigationTab
import com.kexora.soniclet.player.PlayQueue
import com.kexora.soniclet.player.SongDetailsScreen
import com.kexora.soniclet.radio.RadioStreamScreen
import com.kexora.soniclet.settings.AppSettings
import com.kexora.soniclet.settings.SettingsSubPage
import com.kexora.soniclet.ui.components.common.GlobalSearchOverlay
import com.kexora.soniclet.ui.components.player.Kapar
import com.kexora.soniclet.utils.ArtistParserUtil
import com.kexora.soniclet.utils.SearchPredictionUtil

sealed interface MainScreenContent {
    data object Pager : MainScreenContent
    data class Standalone(val tab: NavigationTab) : MainScreenContent
}

fun getMainTabDisplayName(tab: NavigationTab): String {
    return when (tab) {
        NavigationTab.HOME -> "Home"
        NavigationTab.PLAYER -> "Songs"
        NavigationTab.RADIO -> "Radio"
        NavigationTab.PLAYLISTS -> "Playlists"
        NavigationTab.FOLDERS -> "Folders"
        NavigationTab.ARTISTS -> "Artists"
        NavigationTab.ALBUMS -> "Albums"
        NavigationTab.GENRES -> "Genres"
        NavigationTab.MOODS -> "Moods"
        NavigationTab.QUEUE -> "Queue"
        NavigationTab.STATS -> "Wrapped"
        else -> tab.name
    }
}

fun getNavigationTabTitle(effectiveTab: NavigationTab, activeSettingsSubPage: SettingsSubPage): String? {
    return when (effectiveTab) {
        NavigationTab.HOME -> "Home"
        NavigationTab.PLAYER -> "Songs"
        NavigationTab.RADIO -> "Radio"
        NavigationTab.PLAYLISTS -> "Playlists"
        NavigationTab.FOLDERS -> "Folders"
        NavigationTab.ARTISTS -> "Artists"
        NavigationTab.ALBUMS -> "Albums"
        NavigationTab.GENRES -> "Genres"
        NavigationTab.MOODS -> "Moods"
        NavigationTab.QUEUE -> "Queue"
        NavigationTab.STATS -> "Wrapped"
        NavigationTab.SETTINGS -> when (activeSettingsSubPage) {
            SettingsSubPage.HOME_TABS_CUSTOMIZER -> "Customize Home Tabs"
            SettingsSubPage.APPEARANCE -> "Appearance"
            SettingsSubPage.EQUALIZER -> "Equalizer"
            SettingsSubPage.PLAYBACK_SETTINGS -> "Playback"
            SettingsSubPage.MOTION_CONTROLS -> "Motion Controls"
            SettingsSubPage.GESTURES_GUIDE -> "Gestures"
            SettingsSubPage.AUDIO_FOLDERS -> "Folders"
            SettingsSubPage.WIDGETS -> "Widgets"
            SettingsSubPage.VIDEO_TO_M4A -> "Audio Extractor"
            SettingsSubPage.SYSTEM_HIDDEN_FOLDERS -> "Hidden & System Folders"
            SettingsSubPage.PERMISSIONS -> "Permissions"
            SettingsSubPage.ABOUT -> "About"
            SettingsSubPage.MANUAL_ALL,
            SettingsSubPage.MANUAL_GESTURES,
            SettingsSubPage.MANUAL_MOTION -> "User Manual"
        }
        NavigationTab.HIDDEN_ITEMS -> "Hidden Items"
        NavigationTab.DUPLICATES -> "Duplicates"
        NavigationTab.DRIVE_MODE -> "Drive Mode"
        else -> null
    }
}

@Composable
fun RenderLibraryTab(
    tab: NavigationTab,
    uiState: MusicUiState,
    viewModel: MusicViewModel
) {
    when (tab) {
        NavigationTab.HOME -> {
            HomeScreen(
                uiState = uiState,
                viewModel = viewModel,
                onOpenPlaylist = { playlistId -> viewModel.openPlaylistDetails(playlistId) },
                onOpenCategory = { name, type, songs -> viewModel.openCategory(name, type, songs) },
                onOpenFolder = { _ -> viewModel.selectTab(NavigationTab.FOLDERS) }
            )
        }
        NavigationTab.PLAYER -> {
            SongsLibraryView(
                uiState = uiState,
                viewModel = viewModel
            )
        }
        NavigationTab.RADIO -> {
            // Persistent Radio host stays attached to the live Window outside HorizontalPager
            // so switching tabs never detaches the WebView or interrupts playback.
            Box(modifier = Modifier.fillMaxSize())
        }
        NavigationTab.PLAYLISTS -> {
            Playlists(
                playlists = uiState.playlists,
                allSongs = uiState.songList,
                favoriteSongIds = uiState.favoriteSongIds,
                playCounts = uiState.playCounts,
                songMoods = uiState.songMoods,
                recentlyPlayedSongIds = uiState.recentlyPlayedSongIds,
                searchQuery = uiState.searchQuery,
                isGridMode = uiState.isGridMode,
                isLoading = uiState.isLoading,
                isCreateDialogVisible = uiState.showCreatePlaylistDialog,
                onCreateDialogDismiss = { viewModel.setShowCreatePlaylistDialog(false) },
                onCreatePlaylist = { name, songs -> viewModel.createPlaylist(name, songs) },
                onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                onPlayPlaylist = { playlist ->
                    val songMap = uiState.songList.associateBy { it.id }
                    val songs = playlist.songIds.mapNotNull { songMap[it] }
                    if (songs.isNotEmpty()) {
                        viewModel.playQueue(songs, 0, "Playlist: ${playlist.name}")
                    }
                },
                onPlayQueue = { songs, startIndex, title -> viewModel.playQueue(songs, startIndex, title) },
                onPlaySong = { song, source -> viewModel.playSong(song, source) },
                onRemoveSongFromPlaylist = { pId, sId -> viewModel.removeSongFromPlaylist(pId, sId) },
                onAddSongsToPlaylist = { pId, sIds -> viewModel.addSongsToPlaylist(pId, sIds) },
                onOpenPlaylistDetails = { playlistId -> viewModel.openPlaylistDetails(playlistId) },
                onOpenSmartPlaylistDetails = { smartType -> viewModel.openSmartPlaylistDetails(smartType) }
            )
        }
        NavigationTab.FOLDERS -> {
            Folders(
                discoveredFolders = uiState.discoveredFolders,
                allSongs = uiState.songList,
                searchQuery = uiState.searchQuery,
                isGridMode = uiState.isGridMode,
                viewModel = viewModel,
                onExcludeFolder = { folderPath -> viewModel.excludeFolder(folderPath) },
                manualFolderUris = uiState.manualFolderUris,
                onAddMissingFolder = { uri -> viewModel.addManualFolder(uri) },
                onPlayFolder = { folderPath, title ->
                    val songs = uiState.songList.filter { it.folderPath == folderPath }
                    if (songs.isNotEmpty()) {
                        viewModel.playQueue(songs, 0, "Folder: $title")
                    }
                },
                onPlaySong = { song, folderSongs ->
                    val index = folderSongs.indexOfFirst { it.id == song.id }
                    if (index != -1) {
                        val folderName = folderSongs.firstOrNull()?.folderName?.ifBlank { "Music" } ?: "Folder"
                        viewModel.playQueue(folderSongs, index, "Folder: $folderName")
                    }
                },
                onExcludeSong = { songId -> viewModel.excludeSong(songId) },
                isLoading = uiState.isLoading,
                onInsertNext = { song -> viewModel.insertNext(song) },
                onAddToQueue = { song -> viewModel.addToQueue(song) }
            )
        }
        NavigationTab.ARTISTS -> {
            ArtistsView(uiState = uiState, viewModel = viewModel)
        }
        NavigationTab.ALBUMS -> {
            AlbumsView(uiState = uiState, viewModel = viewModel)
        }
        NavigationTab.GENRES -> {
            GenresView(uiState = uiState, viewModel = viewModel)
        }
        NavigationTab.MOODS -> {
            MoodsView(uiState = uiState, viewModel = viewModel)
        }
        NavigationTab.QUEUE -> {
            PlayQueue(
                activeQueue = uiState.activeQueue,
                queueSourceTitle = uiState.queueSourceTitle,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                searchQuery = uiState.searchQuery,
                isLoading = uiState.isLoading,
                onPlaySong = { song -> viewModel.playSong(song) },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onRemoveFromQueue = { songId -> viewModel.removeFromQueue(songId) },
                onMoveQueueItemToNext = { index -> viewModel.moveQueueItemToNext(index) },
                onMoveQueueItemToBottom = { index -> viewModel.moveQueueItemToBottom(index) },
                onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onClearQueue = { viewModel.clearQueue() },
                savedQueues = uiState.savedQueues,
                onSaveQueue = { name -> viewModel.saveCurrentQueue(name) },
                onLoadSavedQueue = { id -> viewModel.loadSavedQueue(id) },
                onDeleteSavedQueue = { id -> viewModel.deleteSavedQueue(id) },
                selectedSongIds = uiState.selectedSongIds,
                onToggleSongSelection = { songId -> viewModel.toggleSongSelection(songId) },
                favoriteSongIds = uiState.favoriteSongIds,
                actuallyPlayedSongIds = uiState.actuallyPlayedSongIds,
                playbackPositions = uiState.playbackPositions,
                onNavigateToArtist = { artistName ->
                    val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                    val matchingSongs = allS.filter { s ->
                        ArtistParserUtil.splitArtists(s.artist, s.title).any { it.equals(artistName, ignoreCase = true) }
                    }
                    viewModel.openCategory(artistName, "ARTIST", matchingSongs)
                }
            )
        }
        NavigationTab.STATS -> {
            StatsDashboard(uiState = uiState, viewModel = viewModel)
        }
        else -> {}
    }
}

@Composable
fun RenderStandaloneTab(
    tab: NavigationTab,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onReplayTour: () -> Unit
) {
    when (tab) {
        NavigationTab.PLAYER,
        NavigationTab.RADIO,
        NavigationTab.PLAYLISTS,
        NavigationTab.FOLDERS,
        NavigationTab.ARTISTS,
        NavigationTab.ALBUMS,
        NavigationTab.GENRES,
        NavigationTab.MOODS,
        NavigationTab.QUEUE,
        NavigationTab.STATS -> {
            SwipeBackContainer(
                onBack = { viewModel.popTabStack() },
                isEnabled = uiState.isOneFingerSwipeBackEnabled
            ) {
                RenderLibraryTab(
                    tab = tab,
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
        NavigationTab.PLAYLIST_DETAILS -> {
            val selectedPlaylist = uiState.playlists.find { it.id == uiState.selectedPlaylistId }
            val smartType = uiState.selectedSmartPlaylistType

            val smartTitle: String? = smartType
            val smartIcon: ImageVector? = when (smartType) {
                "Favorites" -> Icons.Filled.Favorite
                "Recently Added" -> Icons.Filled.MoreTime
                "Recently Played" -> Icons.Filled.History
                "Most Played" -> Icons.Filled.Whatshot
                "Forgotten Favorites" -> Icons.Filled.AutoAwesome
                "Not Played" -> Icons.Filled.Block
                "Late Night" -> Icons.Filled.NightlightRound
                "Workout" -> Icons.Filled.FitnessCenter
                "Chill" -> Icons.Filled.Coffee
                "Romantic" -> Icons.Filled.FavoriteBorder
                null -> null
                else -> Icons.Filled.LibraryMusic
            }
            val smartSongs: List<Song>? = when (smartType) {
                "Favorites" -> uiState.songList.filter { uiState.favoriteSongIds.contains(it.id) }
                "Recently Added" -> uiState.songList
                    .sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id })
                    .take(50)
                "Recently Played" -> uiState.recentlyPlayedSongIds.mapNotNull { id ->
                    uiState.songList.find { it.id == id }
                }
                "Most Played" -> uiState.songList
                    .filter { (uiState.playDurations[it.id] ?: 0L) > 0L || (uiState.playCounts[it.id] ?: 0) > 0 }
                    .sortedByDescending {
                        (uiState.playDurations[it.id] ?: 0L).takeIf { d -> d > 0L }
                            ?: ((uiState.playCounts[it.id] ?: 0).toLong() * it.duration)
                    }
                    .take(30)
                "Forgotten Favorites" -> {
                    val recentIds = uiState.recentlyPlayedSongIds.take(40).toSet()
                    uiState.songList.filter {
                        ((uiState.playDurations[it.id] ?: 0L) > 60000L || (uiState.playCounts[it.id] ?: 0) >= 3) &&
                            it.id !in recentIds
                    }
                }
                "Not Played" -> uiState.songList.filter {
                    (uiState.playDurations[it.id] ?: 0L) == 0L && (uiState.playCounts[it.id] ?: 0) == 0
                }
                "Late Night", "Workout", "Chill", "Romantic" -> uiState.songList.filter {
                    uiState.songMoods[it.id] == smartType
                }
                null -> null
                else -> uiState.songList.filter { uiState.songMoods[it.id] == smartType }
            }

            SwipeBackContainer(
                onBack = { viewModel.popTabStack() },
                isEnabled = uiState.isOneFingerSwipeBackEnabled
            ) {
                PlaylistDetails(
                    playlist = selectedPlaylist,
                    smartPlaylistTitle = smartTitle,
                    smartIcon = smartIcon,
                    smartPlaylistSongs = smartSongs,
                    allLibrarySongs = uiState.songList,
                    currentPlayingSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    favoriteSongIds = uiState.favoriteSongIds,
                    searchQuery = uiState.searchQuery,
                    isGridMode = uiState.isGridMode,
                    isLoading = uiState.isLoading,
                    onBack = { viewModel.popTabStack() },
                    onPlayQueue = { songs, startIndex, title -> viewModel.playQueue(songs, startIndex, title) },
                    onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                    onAddSongsToPlaylist = { pId, songs -> viewModel.addSongsToPlaylist(pId, songs) },
                    onRemoveSongFromPlaylist = { pId, sId -> viewModel.removeSongFromPlaylist(pId, sId) },
                    onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                    onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                    onUpdatePlaylistGradient = { id, gradientIndex ->
                        viewModel.updatePlaylistGradient(id, gradientIndex)
                    },
                    onInsertNext = { song -> viewModel.insertNext(song) },
                    onAddToQueue = { song -> viewModel.addToQueue(song) },
                    selectedSongIds = uiState.selectedSongIds,
                    onToggleSongSelection = { songId -> viewModel.toggleSongSelection(songId) }
                )
            }
        }
        NavigationTab.CATEGORY_DETAILS -> {
            val categoryTitle = uiState.selectedCategoryName ?: "Category"
            val categorySongs = uiState.selectedCategorySongs
            val smartIcon = when (uiState.selectedCategoryType) {
                "ALBUM" -> Icons.Filled.LibraryMusic
                "ARTIST" -> Icons.Filled.LibraryMusic
                "GENRE" -> Icons.Filled.LibraryMusic
                "MOOD" -> Icons.Filled.FavoriteBorder
                else -> Icons.Filled.LibraryMusic
            }
            SwipeBackContainer(
                onBack = { viewModel.popTabStack() },
                isEnabled = uiState.isOneFingerSwipeBackEnabled
            ) {
                PlaylistDetails(
                    playlist = null,
                    smartPlaylistTitle = categoryTitle,
                    smartIcon = smartIcon,
                    smartPlaylistSongs = categorySongs,
                    allLibrarySongs = uiState.songList,
                    currentPlayingSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    favoriteSongIds = uiState.favoriteSongIds,
                    searchQuery = uiState.searchQuery,
                    isGridMode = uiState.isGridMode,
                    isLoading = uiState.isLoading,
                    onBack = { viewModel.popTabStack() },
                    onPlayQueue = { songs, startIndex, title -> viewModel.playQueue(songs, startIndex, title) },
                    onToggleFavorite = { songId -> viewModel.toggleFavorite(songId) },
                    onAddSongsToPlaylist = { pId, songs -> viewModel.addSongsToPlaylist(pId, songs) },
                    onRemoveSongFromPlaylist = { pId, sId -> viewModel.removeSongFromPlaylist(pId, sId) },
                    onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                    onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                    onUpdatePlaylistGradient = { id, gradientIndex ->
                        viewModel.updatePlaylistGradient(id, gradientIndex)
                    },
                    onInsertNext = { song -> viewModel.insertNext(song) },
                    onAddToQueue = { song -> viewModel.addToQueue(song) },
                    selectedSongIds = uiState.selectedSongIds,
                    onToggleSongSelection = { songId -> viewModel.toggleSongSelection(songId) }
                )
            }
        }
        NavigationTab.DUPLICATES -> {
            SwipeBackContainer(
                onBack = { viewModel.popTabStack() },
                isEnabled = uiState.isOneFingerSwipeBackEnabled
            ) {
                DuplicatesScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.popTabStack() }
                )
            }
        }
        NavigationTab.DRIVE_MODE -> {
            SwipeBackContainer(
                onBack = { viewModel.popTabStack() },
                isEnabled = uiState.isOneFingerSwipeBackEnabled
            ) {
                DriveMode(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDismiss = { viewModel.popTabStack() }
                )
            }
        }
        NavigationTab.HIDDEN_ITEMS -> {
            SwipeBackContainer(
                onBack = { viewModel.popTabStack() },
                isEnabled = uiState.isOneFingerSwipeBackEnabled
            ) {
                HiddenItems(
                    excludedFolders = uiState.excludedFolders,
                    excludedSongIds = uiState.excludedSongIds,
                    allSongs = uiState.allSongsList,
                    restoredAutoSongIds = uiState.restoredAutoSongIds,
                    searchQuery = uiState.searchQuery,
                    onRestoreFolder = { folderPath -> viewModel.restoreFolder(folderPath) },
                    onRestoreAllFolders = { viewModel.restoreAllExcludedFolders() },
                    onRestoreSong = { songId -> viewModel.restoreSong(songId) },
                    onRestoreAllSongs = { viewModel.restoreAllExcludedSongs() },
                    isLoading = uiState.isLoading,
                    onOpenSystemHiddenVault = {
                        viewModel.setSettingsSubPage(SettingsSubPage.SYSTEM_HIDDEN_FOLDERS)
                        viewModel.selectTab(NavigationTab.SETTINGS)
                    },
                    onBack = { viewModel.popTabStack() }
                )
            }
        }
        NavigationTab.SONG_DETAILS -> {
            val detailSong = uiState.selectedSongForDetails ?: uiState.currentSong ?: uiState.songList.firstOrNull()
            if (detailSong != null) {
                SwipeBackContainer(
                    onBack = { viewModel.popTabStack() },
                    isEnabled = uiState.isOneFingerSwipeBackEnabled
                ) {
                    SongDetailsScreen(
                        song = detailSong,
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = { viewModel.popTabStack() }
                    )
                }
            }
        }
        NavigationTab.USER_GUIDE -> {
            LaunchedEffect(Unit) {
                viewModel.setSettingsSubPage(SettingsSubPage.GESTURES_GUIDE)
                viewModel.selectTab(NavigationTab.SETTINGS)
            }
        }
        NavigationTab.NOW_PLAYING -> {
            // Rendered as high-performance fluid slide-up overlay
            Box(modifier = Modifier.fillMaxSize())
        }
        NavigationTab.SETTINGS -> {
            SwipeBackContainer(
                onBack = { viewModel.popTabStack() },
                isEnabled = uiState.isOneFingerSwipeBackEnabled
            ) {
                AppSettings(
                    uiState = uiState,
                    viewModel = viewModel,
                    onBackToHome = { viewModel.popTabStack() },
                    onReplayTour = onReplayTour
                )
            }
        }
        else -> {}
    }
}

/**
 * Complete Tab Header (Kapar + ScrollableTabRow) and Main Tab Router (Pager + Standalone + Radio Host + GlobalSearchOverlay).
 */
@Composable
fun ColumnScope.MainTabRouterSection(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    hasMountedRadioHost: Boolean,
    showMiniPlayer: Boolean,
    onOpenDrawer: () -> Unit,
    onReplayTour: () -> Unit
) {
    val mainTabsList = uiState.activeMainTabs
    var underlyingTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }
    LaunchedEffect(uiState.currentTab) {
        if (uiState.currentTab != NavigationTab.NOW_PLAYING) {
            underlyingTab = uiState.currentTab
        }
    }
    val effectiveTab = if (uiState.currentTab == NavigationTab.NOW_PLAYING) underlyingTab else uiState.currentTab

    val mainTabsNames = mainTabsList.map { getMainTabDisplayName(it) }
    val isMainPagerTab = mainTabsList.contains(effectiveTab)
    val currentTabTitle = getNavigationTabTitle(effectiveTab, uiState.activeSettingsSubPage)

    val showKapar = effectiveTab != NavigationTab.DRIVE_MODE &&
        effectiveTab != NavigationTab.PLAYLIST_DETAILS &&
        effectiveTab != NavigationTab.CATEGORY_DETAILS &&
        effectiveTab != NavigationTab.DUPLICATES &&
        effectiveTab != NavigationTab.SONG_DETAILS
    var isSearchActive by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.updateSearchQuery("")
    }

    AnimatedVisibility(
        visible = showKapar && !isSearchActive,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        Column {
            Kapar(
                currentTab = effectiveTab,
                trackCount = uiState.filteredSongList.size,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                excludedItemCount = uiState.excludedFolders.size + uiState.excludedSongIds.size,
                onOpenExcludedItems = { viewModel.selectTab(NavigationTab.HIDDEN_ITEMS) },
                onRefresh = { viewModel.loadAudioFiles(forceSync = true) },
                onOpenSettingsSubPage = { subPage ->
                    viewModel.clearSubPageBackStack()
                    viewModel.setSettingsSubPage(subPage)
                    viewModel.selectTab(NavigationTab.SETTINGS)
                },
                onCreatePlaylist = { viewModel.setShowCreatePlaylistDialog(true) },
                isSearchActive = isSearchActive,
                onSearchActiveChange = { isSearchActive = it },
                isGridMode = uiState.isGridMode,
                onToggleGridMode = { viewModel.toggleGridMode() },
                appTheme = uiState.appTheme,
                onAppThemeChange = { viewModel.setAppTheme(it) },
                onOpenDrawer = onOpenDrawer,
                canGoBack = !isMainPagerTab,
                onBack = { viewModel.popTabStack() },
                titleText = currentTabTitle,
                suggestionCandidates = remember(
                    effectiveTab,
                    uiState.songList,
                    uiState.playlists,
                    uiState.discoveredFolders
                ) {
                    SearchPredictionUtil.getSuggestionCandidates(
                        effectiveTab,
                        uiState.songList,
                        uiState.playlists,
                        uiState.discoveredFolders
                    )
                }
            )

            AnimatedVisibility(
                visible = isMainPagerTab && !isSearchActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ScrollableTabRow(
                    selectedTabIndex = mainTabsList.indexOf(effectiveTab).coerceAtLeast(0),
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    indicator = { tabPositions ->
                        val selectedIndex = mainTabsList.indexOf(effectiveTab).coerceAtLeast(0)
                        if (selectedIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedIndex])
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    mainTabsNames.forEachIndexed { index, title ->
                        val isSelected = mainTabsList[index] == effectiveTab
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(mainTabsList[index]) },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (showMiniPlayer) 86.dp + navBarPadding + 4.dp else navBarPadding + 16.dp
    val isRadioTabVisible = effectiveTab == NavigationTab.RADIO &&
        !isSearchActive &&
        uiState.currentTab != NavigationTab.NOW_PLAYING &&
        uiState.currentTab != NavigationTab.DRIVE_MODE

    val radioTabIndex = mainTabsList.indexOf(NavigationTab.RADIO)
    val onRadioSwipePreviousTab: () -> Unit = {
        if (radioTabIndex > 0) {
            viewModel.selectTab(mainTabsList[radioTabIndex - 1])
        }
    }
    val onRadioSwipeNextTab: () -> Unit = {
        if (radioTabIndex >= 0 && radioTabIndex < mainTabsList.lastIndex) {
            viewModel.selectTab(mainTabsList[radioTabIndex + 1])
        }
    }

    CompositionLocalProvider(
        LocalPlayerBottomPadding provides bottomPadding
    ) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (hasMountedRadioHost || isRadioTabVisible) {
            RadioStreamScreen(
                viewModel = viewModel,
                isActiveTab = isRadioTabVisible,
                onSwipeToPreviousTab = onRadioSwipePreviousTab,
                onSwipeToNextTab = onRadioSwipeNextTab,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isRadioTabVisible) 2f else -1f)
                    .graphicsLayer {
                        alpha = if (isRadioTabVisible) 1f else 0f
                    }
            )
        }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                val currentScreenContent = if (isMainPagerTab) {
                    MainScreenContent.Pager
                } else {
                    MainScreenContent.Standalone(effectiveTab)
                }

                AnimatedContent(
                    targetState = currentScreenContent,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "tab_content"
                ) { screenContent ->
                    when (screenContent) {
                        MainScreenContent.Pager -> {
                            val pagerState = rememberPagerState(
                                initialPage = mainTabsList.indexOf(effectiveTab).coerceAtLeast(0),
                                pageCount = { mainTabsList.size }
                            )

                            LaunchedEffect(pagerState.currentPage) {
                                if (uiState.selectedSongIds.isNotEmpty()) {
                                    viewModel.clearSelectedSongs()
                                }
                            }

                            // Sync settled page to ViewModel cleanly only when swipe settles
                            LaunchedEffect(pagerState, mainTabsList) {
                                snapshotFlow { pagerState.settledPage }.collect { settledPage ->
                                    val newTab = mainTabsList.getOrNull(settledPage)
                                    if (newTab != null && effectiveTab != newTab && isMainPagerTab) {
                                        viewModel.selectTab(newTab)
                                    }
                                }
                            }

                            // Animate pager when tab changes externally without fighting user drag
                            LaunchedEffect(effectiveTab, mainTabsList) {
                                if (isMainPagerTab) {
                                    val expectedPage = mainTabsList.indexOf(effectiveTab).coerceAtLeast(0)
                                    if (pagerState.currentPage != expectedPage && !pagerState.isScrollInProgress) {
                                        pagerState.animateScrollToPage(expectedPage)
                                    }
                                }
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = true,
                                beyondViewportPageCount = 0
                            ) { page ->
                                mainTabsList.getOrNull(page)?.let { pageTab ->
                                    RenderLibraryTab(
                                        tab = pageTab,
                                        uiState = uiState,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                        is MainScreenContent.Standalone -> {
                            RenderStandaloneTab(
                                tab = screenContent.tab,
                                uiState = uiState,
                                viewModel = viewModel,
                                onReplayTour = onReplayTour
                            )
                        }
                    }
                }
            }

        androidx.compose.animation.AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.zIndex(5f)
        ) {
            GlobalSearchOverlay(
                query = uiState.searchQuery,
                uiState = uiState,
                viewModel = viewModel,
                onCloseSearch = {
                    isSearchActive = false
                    viewModel.updateSearchQuery("")
                },
                onPlaySong = { song, songList ->
                    viewModel.playSong(song = song, songsContext = songList)
                },
                onOpenArtist = { artistName, artistSongs ->
                    viewModel.openCategory(artistName, "ARTIST", artistSongs)
                    isSearchActive = false
                },
                onOpenAlbum = { albumName, albumSongs ->
                    viewModel.openCategory(albumName, "ALBUM", albumSongs)
                    isSearchActive = false
                },
                onOpenPlaylist = { playlistId ->
                    viewModel.openPlaylistDetails(playlistId)
                    isSearchActive = false
                },
                onOpenFolder = { _ ->
                    viewModel.selectTab(NavigationTab.FOLDERS)
                    isSearchActive = false
                },
                onSearchQueryChange = { newQuery ->
                    viewModel.updateSearchQuery(newQuery)
                }
            )
        }
    }
    }
}
