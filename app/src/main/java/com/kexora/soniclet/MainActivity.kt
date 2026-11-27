package com.kexora.soniclet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kexora.soniclet.easteregg.MuhLoveNotificationScheduler
import com.kexora.soniclet.easteregg.MuhLoveRegistry
import com.kexora.soniclet.gestures.GlobalAppGesturesContainer
import com.kexora.soniclet.permissions.PermissionHandler
import com.kexora.soniclet.permissions.isAudioPermissionGranted
import com.kexora.soniclet.player.AppTheme
import com.kexora.soniclet.player.MiniPlayer
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.NavigationTab
import com.kexora.soniclet.radio.RadioBackgroundHost
import com.kexora.soniclet.radio.RadioMiniBanner
import com.kexora.soniclet.service.NotificationActionReceiver
import com.kexora.soniclet.ui.components.navigation.AppSidebarDrawer
import com.kexora.soniclet.ui.main.AppBackground
import com.kexora.soniclet.ui.main.AppOverlays
import com.kexora.soniclet.ui.main.MainTabRouterSection
import com.kexora.soniclet.ui.main.rememberAppBackgroundMetrics
import com.kexora.soniclet.ui.theme.SonicletTheme
import com.kexora.soniclet.worker.SpecialDateNotificationWorker
import kotlinx.coroutines.launch

// App ka main entry point (Yeh app ki pehli screen hai)
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MusicViewModel

    private val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context?.let {
                if (MuhLoveRegistry.isMuhModeActive(it)) {
                    MuhLoveNotificationScheduler.syncSpecialCelebration(it)
                }
            }
            if (::viewModel.isInitialized) {
                viewModel.refreshSpecialCelebrationDate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshSpecialCelebrationDate()
            viewModel.onAppResumeCheckNewSongs()
        }
        if (MuhLoveRegistry.isMuhModeActive(this)) {
            MuhLoveNotificationScheduler.syncSpecialCelebration(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.clearNotificationBanner()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dateChangeReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        viewModel = (applicationContext as SonicletApplication).musicViewModel
        viewModel.refreshSpecialCelebrationDate()

        val dateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction("android.intent.action.TIME_SET")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dateChangeReceiver, dateFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dateChangeReceiver, dateFilter)
        }

        if (MuhLoveRegistry.isMuhModeActive(this)) {
            MuhLoveNotificationScheduler.syncSpecialCelebration(this)
            SpecialDateNotificationWorker.schedule(this)
        } else {
            MuhLoveNotificationScheduler.cancelAlarm(this)
            SpecialDateNotificationWorker.cancel(this)
        }

        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val currentPositionState = viewModel.currentPosition.collectAsStateWithLifecycle()

            val isDarkTheme = when (uiState.appTheme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            val currentBgMetrics by rememberAppBackgroundMetrics(uiState = uiState, isDarkTheme = isDarkTheme)

            val isAudioGranted = isAudioPermissionGranted(context)
            var showSplash by remember { mutableStateOf(true) }
            var isPlayerExpanded by remember { mutableStateOf(false) }
            var showExitDialog by remember { mutableStateOf(false) }
            var showReplayTour by remember { mutableStateOf(false) }
            var showMiniPlayerAudioFxSheet by remember { mutableStateOf(false) }
            var showSleepTimerSheet by remember { mutableStateOf(false) }

            val isOnboardingVisible = (uiState.isFirstLaunch && !isAudioGranted) || showReplayTour
            val isNowPlayingVisible = uiState.currentTab == NavigationTab.NOW_PLAYING && uiState.currentSong != null
            val isNowPlayingDarkBackdrop = isNowPlayingVisible && (isDarkTheme || uiState.currentSong?.albumArtUri != null)
            val isDriveModeVisible = uiState.currentTab == NavigationTab.DRIVE_MODE

            val useLightStatusBars: Boolean
            val useLightNavBars: Boolean
            when {
                isOnboardingVisible || isNowPlayingDarkBackdrop || isDriveModeVisible -> {
                    // Pure dark OLED screens always get crisp white system bar icons
                    useLightStatusBars = false
                    useLightNavBars = false
                }
                isNowPlayingVisible && !isDarkTheme && uiState.currentSong?.albumArtUri == null -> {
                    // Light Mode Now Playing fallback when track has no cover art
                    useLightStatusBars = true
                    useLightNavBars = true
                }
                uiState.backgroundMode == "CUSTOM" && !uiState.customBackgroundUri.isNullOrBlank() -> {
                    // Custom Wallpaper: switch to dark icons ONLY when post-dim luminance > 0.58f
                    useLightStatusBars = currentBgMetrics.topLuminance > 0.58f
                    useLightNavBars = currentBgMetrics.bottomLuminance > 0.58f
                }
                else -> {
                    // Solid, Gradient, Dynamic Glow & Default strictly follow Dark vs Light Mode
                    useLightStatusBars = !isDarkTheme
                    useLightNavBars = !isDarkTheme
                }
            }

            val insetsController = remember(window) { WindowCompat.getInsetsController(window, window.decorView) }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = useLightStatusBars
                insetsController.isAppearanceLightNavigationBars = useLightNavBars
            }

            val radioHost = remember(context) { RadioBackgroundHost.getInstance(context) }
            val radioHostState by radioHost.state.collectAsStateWithLifecycle()

            var hasMountedRadioHost by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(uiState.currentTab, radioHostState.isRadioPlaying, radioHostState.isRadioSessionActive) {
                if (uiState.currentTab == NavigationTab.RADIO || radioHostState.isRadioPlaying || radioHostState.isRadioSessionActive) {
                    hasMountedRadioHost = true
                }
            }

            val isOnRadioTab = uiState.currentTab == NavigationTab.RADIO
            val isRadioConnectedAndPlaying = radioHostState.isRadioConnected && radioHostState.isRadioPlaying
            val isRadioActiveInBackground = radioHostState.isRadioConnected &&
                (radioHostState.isRadioPlaying || radioHostState.isRadioSessionActive)
            val showRadioMiniBanner = isRadioActiveInBackground &&
                !isOnRadioTab &&
                uiState.currentTab != NavigationTab.DRIVE_MODE &&
                uiState.currentTab != NavigationTab.NOW_PLAYING
            val showMiniPlayer = uiState.currentTab != NavigationTab.DRIVE_MODE &&
                uiState.currentSong != null &&
                !showRadioMiniBanner &&
                !(isOnRadioTab && isRadioConnectedAndPlaying)
            val hasBottomBar = showMiniPlayer || showRadioMiniBanner

            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val screenWidth = configuration.screenWidthDp.dp
            val maxContentWidth = when {
                screenWidth >= 1200.dp -> 1100.dp
                screenWidth >= 840.dp -> 900.dp
                isLandscape -> 960.dp
                else -> 640.dp
            }

            val handleGlobalBack: () -> Unit = {
                when {
                    uiState.selectedSongIds.isNotEmpty() -> viewModel.clearSelectedSongs()
                    isPlayerExpanded -> isPlayerExpanded = false
                    uiState.showExcludedFoldersDialog -> viewModel.setShowExcludedFoldersDialog(false)
                    uiState.showCreatePlaylistDialog -> viewModel.setShowCreatePlaylistDialog(false)
                    uiState.searchQuery.isNotEmpty() -> viewModel.updateSearchQuery("")
                    viewModel.popTabStack() -> {}
                    uiState.currentTab != NavigationTab.HOME -> viewModel.selectTab(NavigationTab.HOME)
                    else -> showExitDialog = true
                }
            }

            val handleDirectHome: () -> Unit = {
                isPlayerExpanded = false
                viewModel.setShowExcludedFoldersDialog(false)
                viewModel.updateSearchQuery("")
                viewModel.selectTab(NavigationTab.HOME)
            }

            BackHandler { handleGlobalBack() }

            SonicletTheme(
                darkTheme = isDarkTheme,
                dynamicColor = false,
                accentColor = if (uiState.isKariModeEnabled && uiState.kariDominantColor != null) {
                    uiState.kariDominantColor!!
                } else {
                    uiState.accentColor
                },
                backgroundMode = uiState.backgroundMode,
                solidBgColor = uiState.solidBgColor,
                solidBgOpacity = uiState.solidBgOpacity,
                backgroundMetrics = currentBgMetrics
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (isAudioGranted && uiState.isFirstLaunch) {
                            LaunchedEffect(Unit) {
                                viewModel.setFirstLaunch(false)
                            }
                        }
                        PermissionHandler(
                            forceShowTour = isOnboardingVisible,
                            currentTheme = uiState.appTheme,
                            accentColor = uiState.accentColor,
                            onSelectTheme = { theme -> viewModel.setAppTheme(theme) },
                            onSelectAccentColor = { color -> viewModel.setAccentColor(color) },
                            onTourDismissed = {
                                viewModel.setFirstLaunch(false)
                                showReplayTour = false
                            },
                            onPermissionGranted = { viewModel.loadAudioFiles() }
                        ) {
                            GlobalAppGesturesContainer(
                                onSingleFingerSwipeRight = {
                                    val isOnMainPagerOrRadio = uiState.currentTab == NavigationTab.RADIO ||
                                        uiState.activeMainTabs.contains(uiState.currentTab)
                                    if (uiState.isOneFingerSwipeBackEnabled && !isOnMainPagerOrRadio) {
                                        handleGlobalBack()
                                    }
                                },
                                onTwoFingerSwipeHome = {
                                    if (uiState.isTwoFingerSwipeHomeEnabled) handleDirectHome()
                                },
                                onTwoFingerTap = {
                                    if (uiState.isTwoFingerTapEnabled) {
                                        when (uiState.twoFingerTapAction) {
                                            "Play/Pause" -> viewModel.togglePlayPause()
                                            "Next", "Next Track", "Next/Previous" -> viewModel.playNext()
                                            "Previous" -> viewModel.playPrevious()
                                            "Shuffle" -> viewModel.toggleShuffle()
                                            "Favorite" -> uiState.currentSong?.let { viewModel.toggleFavorite(it.id) }
                                        }
                                    }
                                },
                                onThreeFingerSwipeLeft = {
                                    if (uiState.isThreeFingerSwipeEnabled) {
                                        when (uiState.threeFingerSwipeAction) {
                                            "Next", "Next Track", "Next/Previous" -> viewModel.playNext()
                                            "Play/Pause" -> viewModel.togglePlayPause()
                                            "Previous" -> viewModel.playPrevious()
                                            "Shuffle" -> viewModel.toggleShuffle()
                                            "Favorite" -> uiState.currentSong?.let { viewModel.toggleFavorite(it.id) }
                                        }
                                    }
                                },
                                onThreeFingerSwipeRight = {
                                    if (uiState.isThreeFingerSwipeEnabled) {
                                        when (uiState.threeFingerSwipeAction) {
                                            "Next", "Next Track", "Next/Previous" -> viewModel.playPrevious()
                                            "Play/Pause" -> viewModel.togglePlayPause()
                                            "Previous" -> viewModel.playNext()
                                            "Shuffle" -> viewModel.toggleShuffle()
                                            "Favorite" -> uiState.currentSong?.let { viewModel.toggleFavorite(it.id) }
                                        }
                                    }
                                }
                            ) {
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                    AppBackground(
                                        uiState = uiState,
                                        isDarkTheme = isDarkTheme,
                                        currentBgMetrics = currentBgMetrics
                                    )

                                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                                    val scope = rememberCoroutineScope()

                                    BackHandler(enabled = drawerState.isOpen) {
                                        scope.launch { drawerState.close() }
                                    }

                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                        ModalNavigationDrawer(
                                            drawerState = drawerState,
                                            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.65f),
                                            drawerContent = {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    AppSidebarDrawer(
                                                        currentTab = uiState.currentTab,
                                                        appTheme = uiState.appTheme,
                                                        onAppThemeChange = { viewModel.setAppTheme(it) },
                                                        isGridMode = uiState.isGridMode,
                                                        onToggleGridMode = { viewModel.toggleGridMode() },
                                                        onRefresh = { viewModel.loadAudioFiles(forceSync = true) },
                                                        isScanning = uiState.isLoading,
                                                        songCount = uiState.allSongsList.size.coerceAtLeast(uiState.songList.size),
                                                        playlistCount = uiState.playlists.size,
                                                        excludedItemCount = uiState.excludedFolders.size + uiState.excludedSongIds.size,
                                                        sleepTimerOption = uiState.sleepTimerOption,
                                                        sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                                                        isEqEnabled = uiState.isEqEnabled,
                                                        selectedEqPresetName = uiState.selectedEqPresetName,
                                                        isReverbEnabled = uiState.isReverbEnabled,
                                                        isGaplessPlaybackEnabled = uiState.isGaplessPlaybackEnabled,
                                                        isCrossfadeEnabled = uiState.isCrossfadeEnabled,
                                                        crossfadeDurationSeconds = uiState.crossfadeDurationSeconds,
                                                        backgroundMode = uiState.backgroundMode,
                                                        isKariModeEnabled = uiState.isKariModeEnabled,
                                                        isShakeEnabled = uiState.isShakeEnabled,
                                                        isFlipToPauseEnabled = uiState.isFlipToPauseEnabled,
                                                        isPocketModeEnabled = uiState.isPocketModeEnabled,
                                                        isOneFingerSwipeBackEnabled = uiState.isOneFingerSwipeBackEnabled,
                                                        isVolumeEdgeSwipeEnabled = uiState.isVolumeEdgeSwipeEnabled,
                                                        onSetSleepTimer = { viewModel.setSleepTimer(it) },
                                                        onSetCustomSleepTimer = { viewModel.setCustomSleepTimer(it) },
                                                        onOpenSleepTimer = {
                                                            scope.launch {
                                                                drawerState.close()
                                                                showSleepTimerSheet = true
                                                            }
                                                        },
                                                        onOpenExcludedItems = {
                                                            scope.launch {
                                                                drawerState.close()
                                                                viewModel.selectTab(NavigationTab.HIDDEN_ITEMS)
                                                            }
                                                        },
                                                        onCreatePlaylist = {
                                                            scope.launch {
                                                                drawerState.close()
                                                                viewModel.setShowCreatePlaylistDialog(true)
                                                            }
                                                        },
                                                        onNavigateTab = { tab ->
                                                            scope.launch {
                                                                drawerState.close()
                                                                viewModel.selectTab(tab)
                                                            }
                                                        },
                                                        onOpenSettingsSubPage = { subPage ->
                                                            scope.launch {
                                                                drawerState.close()
                                                                viewModel.clearSubPageBackStack()
                                                                viewModel.setSettingsSubPage(subPage)
                                                                viewModel.selectTab(NavigationTab.SETTINGS)
                                                            }
                                                        },
                                                        onCloseDrawer = { scope.launch { drawerState.close() } }
                                                    )
                                                }
                                            },
                                            gesturesEnabled = false
                                        ) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .testTag("soniclet_main_scaffold"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .widthIn(max = maxContentWidth)
                                                            .fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.fillMaxSize()) {
                                                            MainTabRouterSection(
                                                                uiState = uiState,
                                                                viewModel = viewModel,
                                                                hasMountedRadioHost = hasMountedRadioHost,
                                                                showMiniPlayer = hasBottomBar,
                                                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                                                onReplayTour = { showReplayTour = true }
                                                            )
                                                        }

                                                        AnimatedVisibility(
                                                            visible = showMiniPlayer,
                                                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                                                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                                                            modifier = Modifier.align(Alignment.BottomCenter)
                                                        ) {
                                                            val isAnyFxActive = uiState.isEqEnabled || uiState.isBassBoostEnabled ||
                                                                uiState.isVirtualizerEnabled || uiState.is8dAudioEnabled ||
                                                                uiState.isDolbyEnabled || uiState.isReverbEnabled ||
                                                                (uiState.isVolumeBoostEnabled && uiState.volumeBoostLevel > 100)
                                                            MiniPlayer(
                                                                currentTab = uiState.currentTab,
                                                                isGesturesEnabled = uiState.isMiniPlayerGesturesEnabled,
                                                                onTabSelected = { tab -> viewModel.selectTab(tab) },
                                                                song = uiState.currentSong,
                                                                isPlaying = uiState.isPlaying,
                                                                queueSourceTitle = uiState.queueSourceTitle,
                                                                currentPositionProvider = { currentPositionState.value },
                                                                totalDuration = uiState.totalDuration,
                                                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                                                onSkipPrevious = { viewModel.playPrevious() },
                                                                onSkipNext = { viewModel.playNext() },
                                                                onSeekTo = { pos -> viewModel.seekTo(pos) },
                                                                onExpandPlayer = { viewModel.selectTab(NavigationTab.NOW_PLAYING) },
                                                                onOpenAudioFx = { showMiniPlayerAudioFxSheet = true },
                                                                isAudioFxActive = isAnyFxActive,
                                                                isPlayerExpanded = isPlayerExpanded
                                                            )
                                                        }

                                                        AnimatedVisibility(
                                                            visible = showRadioMiniBanner,
                                                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                                                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                                                            modifier = Modifier.align(Alignment.BottomCenter)
                                                        ) {
                                                            RadioMiniBanner(
                                                                hostState = radioHostState,
                                                                onOpenRadioTab = { viewModel.selectTab(NavigationTab.RADIO) },
                                                                onTogglePlayPause = { radioHost.onRadioTogglePlayPause() },
                                                                onStopRadio = { radioHost.stopBackgroundPlayback() }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AppOverlays(
                        uiState = uiState,
                        viewModel = viewModel,
                        maxContentWidth = maxContentWidth,
                        showMiniPlayer = hasBottomBar,
                        showSplash = showSplash,
                        onSplashFinished = { showSplash = false },
                        showMiniPlayerAudioFxSheet = showMiniPlayerAudioFxSheet,
                        onDismissMiniPlayerAudioFxSheet = { showMiniPlayerAudioFxSheet = false },
                        showSleepTimerSheet = showSleepTimerSheet,
                        onDismissSleepTimerSheet = { showSleepTimerSheet = false },
                        showExitDialog = showExitDialog,
                        onDismissExitDialog = { showExitDialog = false }
                    )
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val currentConfig = newBase.resources.configuration
        val clampedScale = currentConfig.fontScale.coerceIn(0.90f, 1.10f)
        if (currentConfig.fontScale != clampedScale) {
            val overrideConfig = Configuration(currentConfig).apply {
                fontScale = clampedScale
            }
            super.attachBaseContext(newBase.createConfigurationContext(overrideConfig))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action) {
            intent.data?.let { uri ->
                viewModel.playExternalUri(uri)
            }
        }

        if (intent.action == "com.kexora.soniclet.ACTION_OPEN_TAB") {
            intent.getStringExtra("target_tab")?.let { tabStr ->
                try {
                    val tab = NavigationTab.valueOf(tabStr)
                    viewModel.selectTab(tab)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            intent.action = null
            intent.removeExtra("target_tab")
        }

        intent.getStringExtra("widget_action")?.let { action ->
            when (action) {
                NotificationActionReceiver.ACTION_PREVIOUS -> viewModel.playPrevious()
                NotificationActionReceiver.ACTION_PLAY_PAUSE -> viewModel.togglePlayPause()
                NotificationActionReceiver.ACTION_NEXT -> viewModel.playNext()
                NotificationActionReceiver.ACTION_REWIND -> viewModel.onRewind()
                NotificationActionReceiver.ACTION_FAST_FORWARD -> viewModel.onFastForward()
                NotificationActionReceiver.ACTION_SHUFFLE -> viewModel.toggleShuffle()
                NotificationActionReceiver.ACTION_REPEAT -> viewModel.toggleRepeatMode()
                NotificationActionReceiver.ACTION_FAVORITE -> viewModel.uiState.value.currentSong?.id?.let {
                    viewModel.toggleFavorite(it)
                }
            }
            intent.removeExtra("widget_action")
        }
    }
}

@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    com.kexora.soniclet.gestures.SwipeBackContainer(
        onBack = onBack,
        modifier = modifier,
        isEnabled = isEnabled,
        content = content
    )
}
