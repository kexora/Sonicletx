package com.kexora.soniclet.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.equalizer.*
import com.kexora.soniclet.library.ShuffleMode
import com.kexora.soniclet.settings.SettingsSubPage
import com.kexora.soniclet.tools.*
import com.kexora.soniclet.ui.components.player.*

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Ultra-Sleek YouTube Music Seekbar & Progress Scrubber.
 */
@Composable
fun SleekYouTubeMusicSeekBar(
    currentPosition: Long,
    totalDuration: Long,
    accentColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val displayPosition = if (isDragging) dragPosition.toLong() else currentPosition
    val progressFraction = remember(displayPosition, totalDuration) {
        if (totalDuration > 0) (displayPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        else 0f
    }

    val animatedThumbRadius by animateFloatAsState(
        targetValue = if (isDragging) 7f else 3.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumb_radius"
    )

    val animatedHaloRadius by animateFloatAsState(
        targetValue = if (isDragging) 14f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "halo_radius"
    )

    val inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(totalDuration) {
                    detectTapGestures { offset ->
                        if (totalDuration > 0 && size.width > 0) {
                            val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val targetMs = (newFraction * totalDuration).toLong()
                            onSeek(targetMs)
                        }
                    }
                }
                .pointerInput(totalDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            if (totalDuration > 0 && size.width > 0) {
                                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                dragPosition = frac * totalDuration
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (totalDuration > 0 && size.width > 0) {
                                val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                                dragPosition = frac * totalDuration
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeek(dragPosition.toLong().coerceIn(0L, totalDuration))
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                val centerY = size.height / 2f
                val trackHeightPx = 3.dp.toPx()
                val totalWidthPx = size.width
                val activeWidth = (totalWidthPx * progressFraction).coerceIn(0f, totalWidthPx)

                // 1. Inactive Track Line
                drawLine(
                    color = inactiveTrackColor,
                    start = Offset(0f, centerY),
                    end = Offset(totalWidthPx, centerY),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round
                )

                // 2. Active Track Line
                if (activeWidth > 0f) {
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, centerY),
                        end = Offset(activeWidth, centerY),
                        strokeWidth = trackHeightPx,
                        cap = StrokeCap.Round
                    )
                }

                // 3. Thumb Outer Halo (appears smoothly while dragging)
                if (animatedHaloRadius > 0f) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.30f),
                        radius = animatedHaloRadius.dp.toPx(),
                        center = Offset(activeWidth, centerY)
                    )
                }

                // 4. Thumb Handle
                drawCircle(
                    color = accentColor,
                    radius = animatedThumbRadius.dp.toPx(),
                    center = Offset(activeWidth, centerY)
                )
            }
        }

        // Timestamps below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(displayPosition),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * YouTube Music Style Pill Quick Control Chip.
 */
@Composable
fun QuickControlChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) activeColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) activeColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.5.sp
                ),
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Scrollable pill quick-action row for Portrait Now Playing screen.
 */
@Composable
fun DhunQuickControlsRow(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    isFavorite: Boolean,
    animatedVibrant: Color,
    onOpenTrackInfo: () -> Unit,
    onOpenQueueTab: (Int) -> Unit,
    onOpenAudioFx: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenMoodDialog: () -> Unit,
    onOpenMoreOptions: () -> Unit
) {
    val sleepTimerOption = uiState.sleepTimerOption
    val sleepTimerRemainingMs = uiState.sleepTimerRemainingMs
    val playbackSpeed = uiState.playbackSpeed
    val playbackPitch = uiState.playbackPitch

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Thumbs Up / Like
        QuickControlChip(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.ThumbUp,
            label = if (isFavorite) "Liked" else "Like",
            isActive = isFavorite,
            activeColor = Color(0xFFFF3366),
            onClick = { viewModel.toggleFavorite(song.id) }
        )

        // 2. Audio Quality & Details Badge (Combined Bit Depth + Track Specs)
        val specs = uiState.currentSongAudioSpecs ?: viewModel.getAudioSpecs(song)
        QuickControlChip(
            icon = Icons.Rounded.GraphicEq,
            label = "${specs.bitDepth}-BIT • ${specs.sampleRateFormatted}",
            isActive = specs.bitDepth >= 24,
            activeColor = animatedVibrant,
            onClick = onOpenTrackInfo
        )

        // 3. Up Next Queue
        QuickControlChip(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = "Up Next",
            isActive = false,
            onClick = { onOpenQueueTab(0) }
        )

        // 4. Sound FX / EQ
        val isFxActive = uiState.isEqEnabled || uiState.isBassBoostEnabled ||
            uiState.isVirtualizerEnabled || uiState.is8dAudioEnabled ||
            uiState.isDolbyEnabled || uiState.isReverbEnabled ||
            (uiState.isVolumeBoostEnabled && uiState.volumeBoostLevel > 100)
        QuickControlChip(
            icon = Icons.Rounded.Equalizer,
            label = "Sound FX",
            isActive = isFxActive,
            activeColor = Color(0xFF00E676),
            onClick = onOpenAudioFx
        )

        // 5. Lyrics
        QuickControlChip(
            icon = Icons.Rounded.Subject,
            label = "Lyrics",
            isActive = false,
            onClick = { onOpenQueueTab(1) }
        )

        // 6. Sleep Timer
        val isSleepActive = sleepTimerOption != SleepTimerOption.OFF
        val sleepLabel = if (isSleepActive && sleepTimerRemainingMs > 0) {
            val mins = (sleepTimerRemainingMs / 60000).toInt()
            "${mins}m left"
        } else "Sleep Timer"
        QuickControlChip(
            icon = Icons.Rounded.Bedtime,
            label = sleepLabel,
            isActive = isSleepActive,
            activeColor = Color(0xFF00E676),
            onClick = onOpenSleepTimer
        )

        // 7. Playback Speed
        val isCustomSpeed = playbackSpeed != 1.0f || playbackPitch != 1.0f
        QuickControlChip(
            icon = Icons.Rounded.Speed,
            label = "${playbackSpeed}x Speed",
            isActive = isCustomSpeed,
            activeColor = animatedVibrant,
            onClick = onOpenSpeedDialog
        )

        // 8. Drive Mode
        QuickControlChip(
            icon = Icons.Rounded.DirectionsCar,
            label = "Drive Mode",
            isActive = false,
            onClick = { viewModel.selectTab(NavigationTab.DRIVE_MODE) }
        )

        // 9. Mood
        val currentMood = uiState.songMoods[song.id]
        QuickControlChip(
            icon = Icons.Rounded.SentimentSatisfiedAlt,
            label = currentMood ?: "Mood",
            isActive = currentMood != null,
            activeColor = animatedVibrant,
            onClick = onOpenMoodDialog
        )

        // 10. More Options
        QuickControlChip(
            icon = Icons.Rounded.MoreVert,
            label = "More",
            isActive = false,
            onClick = onOpenMoreOptions
        )
    }
}

/**
 * Main playback buttons row (Shuffle, Skip Previous, Hero Play/Pause, Skip Next, Repeat).
 */
@Composable
fun DhunPlaybackButtonsRow(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    repeatMode: String,
    isKariModeEnabled: Boolean,
    animatedDominant: Color,
    animatedVibrant: Color,
    playButtonSize: Dp,
    playIconSize: Dp,
    skipButtonSize: Dp,
    isCompactHeight: Boolean,
    arrangement: Arrangement.Horizontal = Arrangement.SpaceBetween
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { viewModel.toggleShuffle() }) {
            val isSmart = uiState.shuffleMode == ShuffleMode.SMART
            val isStandard = uiState.shuffleMode == ShuffleMode.STANDARD
            Icon(
                imageVector = if (isSmart) Icons.Rounded.AutoAwesome else Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isSmart || isStandard) (if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.primary)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(if (isCompactHeight) 24.dp else 28.dp)
            )
        }

        IconButton(
            onClick = { viewModel.playPrevious() },
            modifier = Modifier.size(skipButtonSize)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Hero Play/Pause Button
        Surface(
            shape = CircleShape,
            color = if (isKariModeEnabled) animatedDominant else MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(playButtonSize)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.togglePlayPause() }
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "play_pause_icon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = if (isKariModeEnabled) Color.White else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(playIconSize)
                    )
                }
            }
        }

        IconButton(
            onClick = { viewModel.playNext() },
            modifier = Modifier.size(skipButtonSize)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(onClick = { viewModel.toggleRepeatMode() }) {
            Icon(
                imageVector = when (repeatMode) {
                    "ONE" -> Icons.Rounded.RepeatOne
                    "ALL" -> Icons.Rounded.Repeat
                    else -> Icons.Rounded.Repeat
                },
                contentDescription = "Repeat",
                tint = if (repeatMode != "OFF") (if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.primary)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(if (isCompactHeight) 24.dp else 28.dp)
            )
        }
    }
}

/**
 * Landscape 5-icon shortcut row (Queue, Sleep Timer, Audio FX, Speed, Driving Mode).
 */
@Composable
fun DhunLandscapeBottomShortcuts(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    animatedVibrant: Color,
    sheetDragModifier: Modifier,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenAudioFx: () -> Unit,
    onOpenSpeedDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(sheetDragModifier),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Queue Shortcut
        IconButton(
            onClick = onOpenQueue,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Sleep Timer Shortcut
        val isMiddleSleepActive = uiState.sleepTimerOption != SleepTimerOption.OFF
        IconButton(
            onClick = onOpenSleepTimer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = "Sleep Timer",
                    tint = if (isMiddleSleepActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (isMiddleSleepActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                }
            }
        }

        // 3. Audio FX Shortcut
        val isMiddleFxActive = uiState.isEqEnabled || uiState.isBassBoostEnabled ||
            uiState.isVirtualizerEnabled || uiState.is8dAudioEnabled ||
            uiState.isDolbyEnabled || uiState.isReverbEnabled ||
            (uiState.isVolumeBoostEnabled && uiState.volumeBoostLevel > 100)
        IconButton(
            onClick = onOpenAudioFx,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = "Audio FX & Equalizer",
                    tint = if (isMiddleFxActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (isMiddleFxActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                }
            }
        }

        // 4. Playback Speed Shortcut
        val isMiddleSpeedActive = uiState.playbackSpeed != 1.0f || uiState.playbackPitch != 1.0f
        IconButton(
            onClick = onOpenSpeedDialog,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = "Playback Speed",
                    tint = if (isMiddleSpeedActive) animatedVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (isMiddleSpeedActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(animatedVibrant)
                    )
                }
            }
        }

        // 5. Driving Mode Shortcut
        IconButton(
            onClick = { viewModel.selectTab(NavigationTab.DRIVE_MODE) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DirectionsCar,
                contentDescription = "Driving Mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * All popups, modals, and bottom sheets used by the Dhun Now Playing screen.
 */
@Composable
fun DhunDialogsAndSheets(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    totalDuration: Long,
    showMoreOptionsSheet: Boolean,
    onShowMoreOptionsChange: (Boolean) -> Unit,
    showTrackInfoDialog: Boolean,
    onShowTrackInfoChange: (Boolean) -> Unit,
    showSleepTimerDialog: Boolean,
    onShowSleepTimerChange: (Boolean) -> Unit,
    showSpeedDialog: Boolean,
    onShowSpeedDialogChange: (Boolean) -> Unit,
    showMoodDialog: Boolean,
    onShowMoodDialogChange: (Boolean) -> Unit,
    showAudioInspectorSheet: Boolean,
    onShowAudioInspectorChange: (Boolean) -> Unit,
    showQuickAudioFxSheet: Boolean,
    onShowQuickAudioFxChange: (Boolean) -> Unit,
    showMultipleArtistsSheet: Boolean,
    onShowMultipleArtistsChange: (Boolean) -> Unit,
    onDismissPlayer: () -> Unit
) {
    if (showMoreOptionsSheet) {
        PlayerMoreOptionsSheet(
            song = song,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { onShowMoreOptionsChange(false) },
            onOpenAudioFx = { onShowQuickAudioFxChange(true) },
            onOpenTrackInfo = { onShowTrackInfoChange(true) },
            onOpenSleepTimer = { onShowSleepTimerChange(true) },
            onOpenSpeedPitch = { onShowSpeedDialogChange(true) },
            onOpenMoodDialog = { onShowMoodDialogChange(true) },
            onOpenAudioInspector = { onShowAudioInspectorChange(true) },
            onOpenMetadataEditor = { viewModel.openSongDetails(song) }
        )
    }

    if (showTrackInfoDialog) {
        TrackInfoDialog(
            song = song,
            specs = uiState.currentSongAudioSpecs ?: viewModel.getAudioSpecs(song),
            totalDuration = totalDuration,
            onOpenMetadataEditor = {
                onShowTrackInfoChange(false)
                viewModel.openSongDetails(song)
            },
            onDismiss = { onShowTrackInfoChange(false) }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentOption = uiState.sleepTimerOption,
            remainingMs = uiState.sleepTimerRemainingMs,
            finishTrackBeforeSleep = uiState.finishTrackBeforeSleep,
            onSelectOption = {
                viewModel.setSleepTimer(it)
                onShowSleepTimerChange(false)
            },
            onSetCustomTime = {
                viewModel.setCustomSleepTimer(it)
                onShowSleepTimerChange(false)
            },
            onToggleFinishTrackBeforeSleep = { viewModel.setFinishTrackBeforeSleep(it) },
            onDismiss = { onShowSleepTimerChange(false) }
        )
    }

    if (showSpeedDialog) {
        SpeedPitchDialog(
            currentSpeed = uiState.playbackSpeed,
            currentPitch = uiState.playbackPitch,
            onSetSpeed = { viewModel.setPlaybackSpeed(it) },
            onSetPitch = { viewModel.setPlaybackPitch(it) },
            onDismiss = { onShowSpeedDialogChange(false) }
        )
    }

    if (showMoodDialog) {
        SongMoodDialog(
            song = song,
            currentMood = uiState.songMoods[song.id],
            onSelectMood = { mood -> viewModel.setSongMood(song.id, mood) },
            onDismiss = { onShowMoodDialogChange(false) }
        )
    }

    if (showAudioInspectorSheet) {
        AudioInspectorBottomSheet(
            song = song,
            specs = uiState.currentSongAudioSpecs ?: viewModel.getAudioSpecs(song),
            onDismiss = { onShowAudioInspectorChange(false) },
            onOpenMetadataEditor = {
                onShowAudioInspectorChange(false)
                viewModel.openSongDetails(song)
            }
        )
    }

    if (showQuickAudioFxSheet) {
        QuickAudioFxBottomSheet(
            uiState = uiState,
            viewModel = viewModel,
            onOpenFullEqualizer = {
                onShowQuickAudioFxChange(false)
                viewModel.setSettingsSubPage(SettingsSubPage.EQUALIZER)
                viewModel.selectTab(NavigationTab.SETTINGS)
            },
            onDismiss = { onShowQuickAudioFxChange(false) }
        )
    }

    if (showMultipleArtistsSheet) {
        MultipleArtistsSheet(
            song = song,
            allSongs = uiState.songList,
            onDismissRequest = { onShowMultipleArtistsChange(false) },
            onSelectArtist = { selectedArtistName, songs ->
                onShowMultipleArtistsChange(false)
                viewModel.openCategory(selectedArtistName, "ARTIST", songs)
                onDismissPlayer()
            }
        )
    }
}
