package com.kexora.soniclet.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kexora.soniclet.database.*
import com.kexora.soniclet.equalizer.*
import com.kexora.soniclet.gestures.*
import com.kexora.soniclet.library.*
import com.kexora.soniclet.permissions.*
import com.kexora.soniclet.player.*
import com.kexora.soniclet.tools.*
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

enum class SettingsSubPage {
    APPEARANCE,
    WIDGETS,
    AUDIO_FOLDERS,
    MOTION_CONTROLS,
    EQUALIZER,
    PLAYBACK_SETTINGS,
    GESTURES_GUIDE,
    PERMISSIONS,
    ABOUT,
    MANUAL_ALL,
    MANUAL_GESTURES,
    MANUAL_MOTION,
    HOME_TABS_CUSTOMIZER,
    VIDEO_TO_M4A,
    SYSTEM_HIDDEN_FOLDERS
}

@Composable
fun SettingsSubPageWrapper(
    onBack: () -> Unit,
    isEnabled: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 2.dp)
    ) {
        content()
    }
}

@Composable
fun AppSettings(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onBackToHome: () -> Unit,
    onReplayTour: (() -> Unit)? = null
) {
    BackHandler {
        when (uiState.activeSettingsSubPage) {
            SettingsSubPage.HOME_TABS_CUSTOMIZER -> viewModel.setSettingsSubPage(SettingsSubPage.APPEARANCE)
            SettingsSubPage.MANUAL_GESTURES -> viewModel.setSettingsSubPage(SettingsSubPage.GESTURES_GUIDE)
            SettingsSubPage.MANUAL_MOTION -> viewModel.setSettingsSubPage(SettingsSubPage.MOTION_CONTROLS)
            SettingsSubPage.MANUAL_ALL -> viewModel.setSettingsSubPage(SettingsSubPage.ABOUT)
            else -> onBackToHome()
        }
    }

    AnimatedContent(
        targetState = uiState.activeSettingsSubPage,
        transitionSpec = {
            (fadeIn(animationSpec = tween(280)))
                .togetherWith(fadeOut(animationSpec = tween(280)))
        },
        label = "settings_subpage_anim",
        modifier = Modifier.fillMaxSize()
    ) { subPage ->
        when (subPage) {
            SettingsSubPage.APPEARANCE -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    AppearanceSettingsSubPage(
                        appTheme = uiState.appTheme,
                        accentColor = uiState.accentColor,
                        backgroundMode = uiState.backgroundMode,
                        customBackgroundUri = uiState.customBackgroundUri,
                        bgImageBlur = uiState.bgImageBlur,
                        bgImageDim = uiState.bgImageDim,
                        solidBgColor = uiState.solidBgColor,
                        solidBgOpacity = uiState.solidBgOpacity,
                        gradientPresetIndex = uiState.gradientPresetIndex,
                        customGradientColors = uiState.customGradientColors,
                        gradientIntensity = uiState.gradientIntensity,
                        gradientBlur = uiState.gradientBlur,

                        isKariModeEnabled = uiState.isKariModeEnabled,
                        kariBlur = uiState.kariBlur,
                        kariOpacity = uiState.kariOpacity,
                        onSelectAppTheme = { viewModel.setAppTheme(it) },
                        onSelectAccentColor = { viewModel.setAccentColor(it) },
                        onSelectBackgroundMode = { viewModel.setBackgroundMode(it) },
                        onSelectCustomBackgroundUri = { viewModel.setCustomBackgroundUri(it) },
                        onSetBgImageBlur = { viewModel.setBgImageBlur(it) },
                        onSetBgImageDim = { viewModel.setBgImageDim(it) },
                        onSetSolidBgColor = { viewModel.setSolidBgColor(it) },
                        onSetSolidBgOpacity = { viewModel.setSolidBgOpacity(it) },
                        onSetGradientPresetIndex = { viewModel.setGradientPresetIndex(it) },
                        onSetCustomGradientColors = { viewModel.setCustomGradientColors(it) },
                        onSetGradientIntensity = { viewModel.setGradientIntensity(it) },
                        onSetGradientBlur = { viewModel.setGradientBlur(it) },

                        onToggleKariMode = { viewModel.setKariModeEnabled(it) },
                        onSetKariBlur = { viewModel.setKariBlur(it) },
                        onSetKariOpacity = { viewModel.setKariOpacity(it) },
                        onOpenHomeCustomizer = { viewModel.setSettingsSubPage(SettingsSubPage.HOME_TABS_CUSTOMIZER) }
                    )
                }
            }
            SettingsSubPage.HOME_TABS_CUSTOMIZER -> {
                SettingsSubPageWrapper(onBack = { viewModel.setSettingsSubPage(SettingsSubPage.APPEARANCE) }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    HomeTabsCustomizerSubPage(
                        viewModel = viewModel,
                        onBack = { viewModel.setSettingsSubPage(SettingsSubPage.APPEARANCE) }
                    )
                }
            }
            SettingsSubPage.WIDGETS -> {
                SwipeBackContainer(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    WidgetSettingsSubPage(
                        currentSong = uiState.currentSong,
                        isPlaying = uiState.isPlaying,
                        onNavigateBack = { onBackToHome() }
                    )
                }
            }
            SettingsSubPage.AUDIO_FOLDERS -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    AudioFoldersSettingsSubPage(
                        excludedFoldersCount = uiState.excludedFolders.size,
                        excludedSongsCount = uiState.excludedSongIds.size,
                        minDurationMs = uiState.minDurationMs,
                        isLoading = uiState.isLoading,
                        onSetMinDurationMs = { viewModel.setMinDurationFilter(it) },
                        onManageFoldersClick = { viewModel.selectTab(NavigationTab.FOLDERS) },
                        onOpenHiddenItems = { viewModel.selectTab(NavigationTab.HIDDEN_ITEMS) },
                        onOpenSystemHiddenFolders = { viewModel.setSettingsSubPage(SettingsSubPage.SYSTEM_HIDDEN_FOLDERS, addToBackStack = true) },
                        onRefreshMediaStore = { viewModel.loadAudioFiles(forceSync = true) },
                        onExcludeJunk = { viewModel.excludeJunkFolders() },
                        onOpenDuplicates = { viewModel.selectTab(NavigationTab.DUPLICATES) }
                    )
                }
            }
            SettingsSubPage.MOTION_CONTROLS -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    MotionSettingsSubPage(
                        isShakeEnabled = uiState.isShakeEnabled,
                        onToggleShakeEnabled = { viewModel.setShakeEnabled(it) },
                        isFlipToPauseEnabled = uiState.isFlipToPauseEnabled,
                        flipToPauseAction = uiState.flipToPauseAction,
                        isWristTwistEnabled = uiState.isWristTwistEnabled,
                        wristTwistAction = uiState.wristTwistAction,
                        isPocketModeEnabled = uiState.isPocketModeEnabled,
                        pocketModeAction = uiState.pocketModeAction,
                        shakeAction = uiState.shakeAction,
                        isTiltToFastForwardEnabled = uiState.isTiltToFastForwardEnabled,
                        isAirWaveEnabled = uiState.isAirWaveEnabled,
                        airWaveAction = uiState.airWaveAction,
                        isPickUpToPlayEnabled = uiState.isPickUpToPlayEnabled,
                        onToggleFlipToPause = { viewModel.setFlipToPauseEnabled(it) },
                        onSetFlipToPauseAction = { viewModel.setFlipToPauseAction(it) },
                        onToggleWristTwist = { viewModel.setWristTwistEnabled(it) },
                        onSetWristTwistAction = { viewModel.setWristTwistAction(it) },
                        onTogglePocketMode = { viewModel.setPocketModeEnabled(it) },
                        onSetPocketModeAction = { viewModel.setPocketModeAction(it) },
                        onSetShakeAction = { viewModel.setShakeAction(it) },
                        onToggleTiltToFastForward = { viewModel.setTiltToFastForwardEnabled(it) },
                        onToggleAirWave = { viewModel.setAirWaveEnabled(it) },
                        onSetAirWaveAction = { viewModel.setAirWaveAction(it) },
                        onTogglePickUpToPlay = { viewModel.setPickUpToPlayEnabled(it) },
                        onOpenMotionManual = { viewModel.setSettingsSubPage(SettingsSubPage.MANUAL_MOTION) }
                    )
                }
            }
            SettingsSubPage.EQUALIZER -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    EqualizerSettingsSubPage(
                        isEqEnabled = uiState.isEqEnabled,
                        isReverbEnabled = uiState.isReverbEnabled,
                        isBassBoostEnabled = uiState.isBassBoostEnabled,
                        isVirtualizerEnabled = uiState.isVirtualizerEnabled,
                        isVolumeBoostEnabled = uiState.isVolumeBoostEnabled,
                        isDolbyEnabled = uiState.isDolbyEnabled,
                        isLosslessEnabled = uiState.isLosslessEnabled,
                        is8dAudioEnabled = uiState.is8dAudioEnabled,
                        isBassVibrationEnabled = uiState.isBassVibrationEnabled,
                        bassVibrationStrength = uiState.bassVibrationStrength,
                        bassVibrationMode = uiState.bassVibrationMode,
                        onToggleEq = { viewModel.setEqEnabled(it) },
                        onToggleReverb = { viewModel.setReverbEnabled(it) },
                        onToggleBassBoost = { viewModel.setBassBoostEnabled(it) },
                        onToggleVirtualizer = { viewModel.setVirtualizerEnabled(it) },
                        onToggleVolumeBoost = { viewModel.setVolumeBoostEnabled(it) },
                        onToggleDolby = { viewModel.setDolbyEnabled(it) },
                        onToggleLossless = { viewModel.setLosslessEnabled(it) },
                        onToggle8dAudio = { viewModel.set8dAudioEnabled(it) },
                        onToggleBassVibration = { viewModel.setBassVibrationEnabled(it) },
                        onSetBassVibrationStrength = { viewModel.setBassVibrationStrength(it) },
                        onSetBassVibrationMode = { viewModel.setBassVibrationMode(it) },
                        onTestBassVibration = { viewModel.testBassVibration() },
                        eqBandLevels = uiState.eqBandLevels,
                        eqBandFrequencies = uiState.eqBandFrequencies,
                        selectedEqPresetName = uiState.selectedEqPresetName,
                        customEqPresets = uiState.customEqPresets,
                        bassBoostLevel = uiState.bassBoostLevel,
                        vocalBoostLevel = uiState.vocalBoostLevel,
                        trebleBoostLevel = uiState.trebleBoostLevel,
                        virtualizerLevel = uiState.virtualizerLevel,
                        reverbPreset = uiState.reverbPreset,
                        onSetEqualizerBandLevel = { band, level -> viewModel.setEqualizerBandLevel(band, level) },
                        onApplyPreset = { name, levels, bass, vocal, treble, virt -> viewModel.applyPreset(name, levels, bass, vocal, treble, virt) },
                        onResetEqualizer = { viewModel.resetEqualizerToDefault() },
                        onSaveCustomPreset = { viewModel.saveCustomPreset(it) },
                        onDeleteCustomPreset = { viewModel.deleteCustomPreset(it) },
                        onSetBassBoostLevel = { viewModel.setBassBoostLevel(it) },
                        onSetVocalBoostLevel = { viewModel.setVocalBoostLevel(it) },
                        onSetTrebleBoostLevel = { viewModel.setTrebleBoostLevel(it) },
                        onSetVirtualizerLevel = { viewModel.setVirtualizerLevel(it) },
                        volumeBoostLevel = uiState.volumeBoostLevel,
                        onSetVolumeBoostLevel = { viewModel.setVolumeBoostLevel(it) },
                        onSetReverbPreset = { viewModel.setReverbPreset(it) },
                        onResetAllAudioFx = { viewModel.resetAllAudioFx() }
                    )
                }
            }
            SettingsSubPage.PLAYBACK_SETTINGS -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    PlaybackSettingsSubPage(
                        playTogetherEnabled = uiState.playTogetherEnabled,
                        onSetPlayTogetherEnabled = { viewModel.setPlayTogetherEnabled(it) },
                        playTogetherFadeEnabled = uiState.playTogetherFadeEnabled,
                        onSetPlayTogetherFadeEnabled = { viewModel.setPlayTogetherFadeEnabled(it) },
                        sleepTimerOption = uiState.sleepTimerOption,
                        customSleepTimerMinutes = uiState.customSleepTimerMinutes,
                        sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                        finishTrackBeforeSleep = uiState.finishTrackBeforeSleep,
                        onToggleFinishTrackBeforeSleep = { viewModel.setFinishTrackBeforeSleep(it) },
                        isCrossfadeEnabled = uiState.isCrossfadeEnabled,
                        isAutoResumeOnPlugEnabled = uiState.isAutoResumeOnPlugEnabled,
                        isAutoPlayOnBluetoothEnabled = uiState.isAutoPlayOnBluetoothEnabled,
                        singleClickAction = uiState.singleClickAction,
                        doubleClickAction = uiState.doubleClickAction,
                        tripleClickAction = uiState.tripleClickAction,
                        isHearingSafetyEnabled = uiState.isHearingSafetyEnabled,
                        isGaplessPlaybackEnabled = uiState.isGaplessPlaybackEnabled,
                        isResumeLastPositionEnabled = uiState.isResumeLastPositionEnabled,
                        crossfadeDurationSeconds = uiState.crossfadeDurationSeconds,
                        onSetSleepTimer = { viewModel.setSleepTimer(it) },
                        onSetCustomSleepTimer = { viewModel.setCustomSleepTimer(it) },
                        onSetCrossfadeEnabled = { viewModel.setCrossfadeEnabled(it) },
                        onSetAutoResumeOnPlugEnabled = { viewModel.setAutoResumeOnPlugEnabled(it) },
                        onSetAutoPlayOnBluetoothEnabled = { viewModel.setAutoPlayOnBluetoothEnabled(it) },
                        onSetSingleClickAction = { viewModel.setSingleClickAction(it) },
                        onSetDoubleClickAction = { viewModel.setDoubleClickAction(it) },
                        onSetTripleClickAction = { viewModel.setTripleClickAction(it) },
                        onSetHearingSafetyEnabled = { viewModel.setHearingSafetyEnabled(it) },
                        onSetGaplessPlaybackEnabled = { viewModel.setGaplessPlaybackEnabled(it) },
                        onSetResumeLastPositionEnabled = { viewModel.setResumeLastPositionEnabled(it) },
                        onSetCrossfadeDuration = { viewModel.setCrossfadeDuration(it) }
                    )
                }
            }
            SettingsSubPage.GESTURES_GUIDE -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    GestureGuideSubPage(
                        onOpenUserGuide = { viewModel.setSettingsSubPage(SettingsSubPage.MANUAL_GESTURES) },
                        onReplayTour = { onReplayTour?.invoke() },
                        isTwoFingerTapEnabled = uiState.isTwoFingerTapEnabled,
                        twoFingerTapAction = uiState.twoFingerTapAction,
                        isThreeFingerSwipeEnabled = uiState.isThreeFingerSwipeEnabled,
                        threeFingerSwipeAction = uiState.threeFingerSwipeAction,
                        isVolumeEdgeSwipeEnabled = uiState.isVolumeEdgeSwipeEnabled,
                        isLongPressDragSeekEnabled = uiState.isLongPressDragSeekEnabled,
                        onToggleTwoFingerTap = { viewModel.setTwoFingerTapEnabled(it) },
                        onSetTwoFingerTapAction = { viewModel.setTwoFingerTapAction(it) },
                        onToggleThreeFingerSwipe = { viewModel.setThreeFingerSwipeEnabled(it) },
                        onSetThreeFingerSwipeAction = { viewModel.setThreeFingerSwipeAction(it) },
                        onToggleVolumeEdgeSwipe = { viewModel.setVolumeEdgeSwipeEnabled(it) },
                        onToggleLongPressDragSeek = { viewModel.setLongPressDragSeekEnabled(it) },
                        isOneFingerSwipeBackEnabled = uiState.isOneFingerSwipeBackEnabled,
                        isTwoFingerSwipeHomeEnabled = uiState.isTwoFingerSwipeHomeEnabled,
                        isMiniPlayerGesturesEnabled = uiState.isMiniPlayerGesturesEnabled,
                        onToggleOneFingerSwipeBack = { viewModel.setOneFingerSwipeBackEnabled(it) },
                        onToggleTwoFingerSwipeHome = { viewModel.setTwoFingerSwipeHomeEnabled(it) },
                        onToggleMiniPlayerGestures = { viewModel.setMiniPlayerGesturesEnabled(it) }
                    )
                }
            }
            SettingsSubPage.PERMISSIONS -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    PermissionsSettingsSubPage()
                }
            }
            SettingsSubPage.ABOUT -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    AboutSettingsSubPage(
                        onOpenUserGuide = { viewModel.setSettingsSubPage(SettingsSubPage.MANUAL_ALL) },
                        isMohModeActivated = uiState.isMohModeActivated,
                        onActivateMohMode = { viewModel.setMohModeActivated(it) }
                    )
                }
            }
            SettingsSubPage.MANUAL_ALL -> {
                SettingsSubPageWrapper(onBack = { viewModel.setSettingsSubPage(SettingsSubPage.ABOUT) }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    UserManualSubPage(section = ManualSection.ALL)
                }
            }
            SettingsSubPage.MANUAL_GESTURES -> {
                SettingsSubPageWrapper(onBack = { viewModel.setSettingsSubPage(SettingsSubPage.GESTURES_GUIDE) }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    UserManualSubPage(section = ManualSection.GESTURES)
                }
            }
            SettingsSubPage.MANUAL_MOTION -> {
                SettingsSubPageWrapper(onBack = { viewModel.setSettingsSubPage(SettingsSubPage.MOTION_CONTROLS) }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    UserManualSubPage(section = ManualSection.MOTION)
                }
            }
            SettingsSubPage.VIDEO_TO_M4A -> {
                SettingsSubPageWrapper(onBack = { onBackToHome() }, isEnabled = uiState.isOneFingerSwipeBackEnabled) {
                    VideoToM4aScreen(
                        onBack = { onBackToHome() },
                        onPlayConvertedSong = { song -> 
                            viewModel.playSong(song, "Video Converter")
                            viewModel.selectTab(NavigationTab.PLAYER)
                        },
                        onRefreshLibrary = { viewModel.loadAudioFiles(forceSync = true) }
                    )
                }
            }
            SettingsSubPage.SYSTEM_HIDDEN_FOLDERS -> {
                SettingsSubPageWrapper(
                    onBack = { onBackToHome() },
                    isEnabled = uiState.isOneFingerSwipeBackEnabled
                ) {
                    SystemHiddenFoldersScreen(
                        onBack = { onBackToHome() },
                        viewModel = viewModel,
                        onScanFinished = { viewModel.loadAudioFiles(forceSync = true) }
                    )
                }
            }
        }
    }
}
