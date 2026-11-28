package com.kexora.soniclet.ui.main

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.equalizer.QuickAudioFxBottomSheet
import com.kexora.soniclet.player.Dhun
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.NavigationTab
import com.kexora.soniclet.settings.SettingsSubPage
import com.kexora.soniclet.tools.RingtoneCutterDialog
import com.kexora.soniclet.ui.components.common.AnimatedToastBanner
import com.kexora.soniclet.ui.components.common.SonicletAlertDialog
import com.kexora.soniclet.ui.components.navigation.AddToPlaylistBottomSheet
import com.kexora.soniclet.ui.components.navigation.MultiSelectFloatingPillBar
import com.kexora.soniclet.ui.components.player.SleepTimerDialog
import kotlinx.coroutines.delay

/**
 * Renders all floating UI overlays, sheets, dialogs, full-screen Now Playing (Dhun),
 * multi-select pill bar, toast banners, and the startup splash overlay.
 */
@Composable
fun BoxScope.AppOverlays(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    maxContentWidth: Dp,
    showMiniPlayer: Boolean = uiState.currentSong != null,
    showSplash: Boolean,
    onSplashFinished: () -> Unit,
    showMiniPlayerAudioFxSheet: Boolean,
    onDismissMiniPlayerAudioFxSheet: () -> Unit,
    showSleepTimerSheet: Boolean = false,
    onDismissSleepTimerSheet: () -> Unit = {},
    showExitDialog: Boolean,
    onDismissExitDialog: () -> Unit
) {
    val context = LocalContext.current
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    // Universal Floating Pill Bar for Multiple Selection (constrained to maxContentWidth)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = maxContentWidth)
            .fillMaxWidth()
            .align(Alignment.Center)
    ) {
        AnimatedVisibility(
            visible = uiState.selectedSongIds.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { it * 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it * 2 },
                animationSpec = tween(200)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .zIndex(10f)
        ) {
            val currentContextSongIds: List<Long> = remember(
                uiState.currentTab,
                uiState.selectedPlaylistId,
                uiState.selectedSmartPlaylistType,
                uiState.selectedCategorySongs,
                uiState.filteredSongList,
                uiState.activeQueue,
                uiState.playlists,
                uiState.songList,
                uiState.favoriteSongIds,
                uiState.recentlyPlayedSongIds,
                uiState.playCounts,
                uiState.songMoods
            ) {
                when (uiState.currentTab) {
                    NavigationTab.CATEGORY_DETAILS -> uiState.selectedCategorySongs.map { it.id }
                    NavigationTab.PLAYLIST_DETAILS -> {
                        if (uiState.selectedPlaylistId != null) {
                            val validIds = uiState.songList.map { it.id }.toSet()
                            val p = uiState.playlists.find { it.id == uiState.selectedPlaylistId }
                            p?.songIds?.filter { validIds.contains(it) } ?: emptyList()
                        } else when (uiState.selectedSmartPlaylistType) {
                            "Favorites" -> uiState.songList.filter { uiState.favoriteSongIds.contains(it.id) }.map { it.id }
                            "Recently Added" -> uiState.songList
                                .sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id })
                                .take(50)
                                .map { it.id }
                            "Recently Played" -> uiState.recentlyPlayedSongIds.mapNotNull { id ->
                                uiState.songList.find { it.id == id }?.id
                            }
                            "Most Played" -> uiState.songList
                                .filter { (uiState.playCounts[it.id] ?: 0) > 0 }
                                .sortedByDescending { uiState.playCounts[it.id] ?: 0 }
                                .take(30)
                                .map { it.id }
                            "Forgotten Favorites" -> {
                                val recentIds = uiState.recentlyPlayedSongIds.take(40).toSet()
                                uiState.songList
                                    .filter { (uiState.playCounts[it.id] ?: 0) >= 3 && it.id !in recentIds }
                                    .map { it.id }
                            }
                            "Not Played" -> uiState.songList
                                .filter { (uiState.playCounts[it.id] ?: 0) == 0 }
                                .map { it.id }
                            else -> {
                                val mood = uiState.selectedSmartPlaylistType
                                if (mood != null) {
                                    uiState.songList.filter { uiState.songMoods[it.id] == mood }.map { it.id }
                                } else {
                                    uiState.filteredSongList.map { it.id }
                                }
                            }
                        }
                    }
                    NavigationTab.QUEUE -> uiState.activeQueue.map { it.id }
                    else -> uiState.filteredSongList.map { it.id }
                }
            }
            val isAllSelected = currentContextSongIds.isNotEmpty() &&
                currentContextSongIds.all { it in uiState.selectedSongIds }
            val isPlaylistContext = uiState.currentTab == NavigationTab.PLAYLIST_DETAILS &&
                uiState.selectedPlaylistId != null
            val isQueueContext = uiState.currentTab == NavigationTab.QUEUE

            MultiSelectFloatingPillBar(
                selectedCount = uiState.selectedSongIds.size,
                isAllSelected = isAllSelected,
                onClearSelection = { viewModel.clearSelectedSongs() },
                onSelectAll = {
                    if (isAllSelected) {
                        viewModel.clearSelectedSongs()
                    } else {
                        viewModel.selectAllSongs(currentContextSongIds)
                    }
                },
                onPlaySelected = {
                    val count = uiState.selectedSongIds.size
                    viewModel.playSelectedSongs()
                    viewModel.showNotificationBanner("Playing $count track${if (count > 1) "s" else ""}")
                },
                onInsertNext = {
                    val count = uiState.selectedSongIds.size
                    viewModel.insertSelectedSongsNext()
                    viewModel.showNotificationBanner("$count track${if (count > 1) "s" else ""} will play next")
                },
                onAddToQueue = {
                    val count = uiState.selectedSongIds.size
                    viewModel.addSelectedSongsToQueue()
                    viewModel.showNotificationBanner("Added $count track${if (count > 1) "s" else ""} to queue")
                },
                onAddToPlaylist = { showAddToPlaylistSheet = true },
                onToggleFavorite = {
                    val count = uiState.selectedSongIds.size
                    viewModel.toggleFavoriteSelectedSongs()
                    viewModel.showNotificationBanner("Updated favorites for $count track${if (count > 1) "s" else ""}")
                },
                onShareSelected = { viewModel.shareSelectedSongs(context) },
                onRemoveFromContext = when {
                    isPlaylistContext -> {
                        {
                            val pId = uiState.selectedPlaylistId
                            if (pId != null) {
                                viewModel.removeSelectedSongsFromPlaylist(pId)
                                viewModel.showNotificationBanner("Removed from playlist")
                            }
                        }
                    }
                    isQueueContext -> {
                        {
                            viewModel.removeSelectedSongsFromQueue()
                            viewModel.showNotificationBanner("Removed from queue")
                        }
                    }
                    else -> null
                },
                removeContextLabel = when {
                    isPlaylistContext -> "Remove"
                    isQueueContext -> "Remove"
                    else -> null
                },
                removeContextIcon = Icons.Rounded.RemoveCircleOutline,
                showInsertNext = !isQueueContext,
                showAddToQueue = !isQueueContext,
                showRemoveFromContext = isPlaylistContext || isQueueContext,
                showExclude = !isPlaylistContext && !isQueueContext,
                showDelete = !isPlaylistContext && !isQueueContext,
                onExcludeSelected = {
                    val count = uiState.selectedSongIds.size
                    viewModel.excludeSelectedSongs()
                    viewModel.showNotificationBanner("Hidden $count track${if (count > 1) "s" else ""}")
                },
                onDeleteSelected = { showDeleteSelectedDialog = true }
            )
        }
    }

    // Full-Screen Fluid Slide-Up Now Playing Player (Dhun)
    AnimatedVisibility(
        visible = uiState.currentTab == NavigationTab.NOW_PLAYING && uiState.currentSong != null,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(
                dampingRatio = 0.90f,
                stiffness = Spring.StiffnessMedium
            )
        ),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        Dhun(
            song = uiState.currentSong!!,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                viewModel.popTabStack()
            }
        )
    }

    // User Notification Toast Banner anchored cleanly above MiniPlayer
    AnimatedToastBanner(
        message = uiState.userNotificationBanner,
        onDismiss = { viewModel.clearNotificationBanner() },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = if (showMiniPlayer) 96.dp else 20.dp)
            .zIndex(60f)
    )

    if (showMiniPlayerAudioFxSheet) {
        QuickAudioFxBottomSheet(
            uiState = uiState,
            viewModel = viewModel,
            onOpenFullEqualizer = {
                onDismissMiniPlayerAudioFxSheet()
                viewModel.setSettingsSubPage(SettingsSubPage.EQUALIZER)
                viewModel.selectTab(NavigationTab.SETTINGS)
            },
            onDismiss = onDismissMiniPlayerAudioFxSheet
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerDialog(
            currentOption = uiState.sleepTimerOption,
            remainingMs = uiState.sleepTimerRemainingMs,
            finishTrackBeforeSleep = uiState.finishTrackBeforeSleep,
            onSelectOption = { option ->
                viewModel.setSleepTimer(option)
                onDismissSleepTimerSheet()
            },
            onSetCustomTime = { minutes ->
                viewModel.setCustomSleepTimer(minutes)
                onDismissSleepTimerSheet()
            },
            onToggleFinishTrackBeforeSleep = { enabled ->
                viewModel.setFinishTrackBeforeSleep(enabled)
            },
            onDismiss = onDismissSleepTimerSheet
        )
    }

    if (showAddToPlaylistSheet) {
        val validSongIds = remember(uiState.songList) { uiState.songList.map { it.id }.toSet() }
        AddToPlaylistBottomSheet(
            playlists = uiState.playlists,
            selectedCount = uiState.selectedSongIds.size,
            onPlaylistSelected = { playlistId ->
                viewModel.addSelectedSongsToPlaylist(playlistId)
                showAddToPlaylistSheet = false
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name, viewModel.getSelectedSongs())
                viewModel.clearSelectedSongs()
                showAddToPlaylistSheet = false
            },
            onDismiss = { showAddToPlaylistSheet = false },
            validSongIds = validSongIds
        )
    }

    if (showDeleteSelectedDialog) {
        val count = uiState.selectedSongIds.size
        SonicletAlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = "Delete $count track${if (count > 1) "s" else ""}?",
            message = "This will permanently delete the selected audio file${if (count > 1) "s" else ""} from your device storage.",
            confirmButtonText = "Delete",
            onConfirm = {
                showDeleteSelectedDialog = false
                viewModel.deleteSelectedSongs { deleted ->
                    viewModel.showNotificationBanner("Deleted $deleted track${if (deleted > 1) "s" else ""}")
                }
            },
            dismissButtonText = "Cancel",
            onDismiss = { showDeleteSelectedDialog = false },
            icon = Icons.Rounded.DeleteOutline,
            isDestructive = true
        )
    }

    if (uiState.songToTrim != null) {
        RingtoneCutterDialog(
            song = uiState.songToTrim!!,
            onDismissRequest = { viewModel.closeTrimDialog() }
        )
    }

    if (showExitDialog) {
        SonicletAlertDialog(
            onDismissRequest = onDismissExitDialog,
            title = "Exit Soniclet?",
            message = "Are you sure you want to exit the app? Active playback will stop completely.",
            confirmButtonText = "Exit App",
            onConfirm = {
                onDismissExitDialog()
                viewModel.onStopPlayback()
                (context as? Activity)?.finishAndRemoveTask()
            },
            dismissButtonText = "Keep Listening",
            onDismiss = onDismissExitDialog,
            icon = Icons.Rounded.Close,
            isDestructive = true,
            useAppLogo = false
        )
    }

    // Seamless 3-Tier Animated Splash Screen Overlay (Cold Launch + On-Demand Preview)
    val isPreviewActive = uiState.previewSplashTier != null
    AnimatedVisibility(
        visible = showSplash || isPreviewActive,
        enter = if (isPreviewActive) fadeIn(animationSpec = tween(220)) else EnterTransition.None,
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing
            )
        ),
        modifier = Modifier.zIndex(200f)
    ) {
        com.kexora.soniclet.ui.components.splash.SonicletSplashScreen(
            appTheme = uiState.appTheme,
            isSpecialModeEnabled = uiState.isSpecialModeEnabled,
            specialDates = uiState.specialDates,
            forcedTier = uiState.previewSplashTier,
            forcedSpecialDate = uiState.previewSpecialDate,
            onSplashFinished = {
                if (isPreviewActive) {
                    viewModel.clearSplashPreview()
                }
                if (showSplash) {
                    onSplashFinished()
                }
            }
        )
    }
}


