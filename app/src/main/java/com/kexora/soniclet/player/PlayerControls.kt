package com.kexora.soniclet.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Edge-to-edge ultra-sleek Seekbar & Progress Scrubber with glow halo,
 * live scrub preview badge, and tabular monospaced timestamps.
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
        targetValue = if (isDragging) 7.5f else 4f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumb_radius"
    )

    val animatedHaloRadius by animateFloatAsState(
        targetValue = if (isDragging) 16f else 0f,
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
                .height(34.dp)
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
                    .height(34.dp)
            ) {
                val centerY = size.height / 2f
                val trackHeightPx = if (isDragging) 4.dp.toPx() else 3.dp.toPx()
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

                // 3. Thumb Outer Glow Halo (appears smoothly while scrubbing)
                if (animatedHaloRadius > 0f) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.28f),
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

        // Tabular monospaced timestamps directly underneath (with live scrub preview highlight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(displayPosition),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = if (isDragging) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * Balanced, non-clipped 3-pill edge-to-edge action row for Portrait Now Playing:
 * [Like]  [16-BIT • 44.1 kHz]  [Up Next]
 * Never overflows or clips the trailing "Up Next" pill on any screen width.
 */
@Composable
fun DhunActionPillRow(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    isFavorite: Boolean,
    animatedVibrant: Color,
    onOpenAudioInspector: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val specs = uiState.currentSongAudioSpecs ?: viewModel.getAudioSpecs(song)
    val likeScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pill_like_scale"
    )
    val pillBg = Color.White.copy(alpha = 0.08f)
    val pillBorder = Color.White.copy(alpha = 0.14f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Like / Favorite Pill
        val likeColor = Color(0xFFFF3366)
        Surface(
            shape = CircleShape,
            color = if (isFavorite) likeColor.copy(alpha = 0.18f) else pillBg,
            border = BorderStroke(
                width = 0.6.dp,
                color = if (isFavorite) likeColor.copy(alpha = 0.50f) else pillBorder
            ),
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .clickable { viewModel.toggleFavorite(song.id) }
                .testTag("now_playing_like_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Unlike" else "Like",
                    tint = if (isFavorite) likeColor else Color.White,
                    modifier = Modifier
                        .size(15.dp)
                        .scale(likeScale)
                )
                Text(
                    text = if (isFavorite) "Liked" else "Like",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = if (isFavorite) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (isFavorite) likeColor else Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // 2. Center Interactive Audio Fidelity / Hi-Res Pill (weighted to prevent right-edge clipping)
        val isHiRes = specs.isHiRes || specs.bitDepth >= 24
        val specAccent = if (isHiRes) Color(0xFFFFD54F) else animatedVibrant
        Surface(
            shape = CircleShape,
            color = if (isHiRes) specAccent.copy(alpha = 0.14f) else pillBg,
            border = BorderStroke(
                width = 0.6.dp,
                color = if (isHiRes) specAccent.copy(alpha = 0.42f) else pillBorder
            ),
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(CircleShape)
                .clickable { onOpenAudioInspector() }
                .testTag("now_playing_audio_spec_pill")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Audio Fidelity Details",
                    tint = if (isHiRes) specAccent else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${specs.bitDepth}-BIT • ${specs.sampleRateFormatted}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (isHiRes) specAccent else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. Up Next Queue Pill (always crisp & unclipped)
        Surface(
            shape = CircleShape,
            color = pillBg,
            border = BorderStroke(0.6.dp, pillBorder),
            modifier = Modifier
                .height(34.dp)
                .clip(CircleShape)
                .clickable { onOpenQueue() }
                .testTag("now_playing_up_next_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Up Next Queue",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Main playback buttons row (Shuffle, Skip Previous, Morphing Circle/Squircle Play/Pause, Skip Next, Repeat).
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
    arrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    modifier: Modifier = Modifier
) {
    val activeAccent = if (isKariModeEnabled) animatedVibrant else MaterialTheme.colorScheme.primary
    val playCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 20.dp else (playButtonSize / 2),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "play_button_corner_morph"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { viewModel.toggleShuffle() },
            modifier = Modifier
                .size(48.dp)
                .testTag("now_playing_shuffle_btn")
        ) {
            val isSmart = uiState.shuffleMode == ShuffleMode.SMART
            val isStandard = uiState.shuffleMode == ShuffleMode.STANDARD
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isSmart) Icons.Rounded.AutoAwesome else Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isSmart || isStandard) activeAccent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(if (isCompactHeight) 22.dp else 25.dp)
                )
                if (isSmart || isStandard) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 14.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(activeAccent)
                    )
                }
            }
        }

        IconButton(
            onClick = { viewModel.playPrevious() },
            modifier = Modifier
                .size(maxOf(skipButtonSize, 48.dp))
                .testTag("now_playing_prev_btn")
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (isCompactHeight) 30.dp else 34.dp)
            )
        }

        // Tactile Morphing Play/Pause Button (Circle when paused <-> 22dp Squircle when playing)
        Surface(
            onClick = { viewModel.togglePlayPause() },
            shape = RoundedCornerShape(playCornerRadius),
            color = if (isKariModeEnabled) animatedDominant else MaterialTheme.colorScheme.primary,
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(playButtonSize)
                .testTag("now_playing_play_pause_btn")
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(140)) + scaleIn(initialScale = 0.85f, animationSpec = tween(140))) togetherWith
                            (fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140)))
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
            modifier = Modifier
                .size(maxOf(skipButtonSize, 48.dp))
                .testTag("now_playing_next_btn")
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (isCompactHeight) 30.dp else 34.dp)
            )
        }

        IconButton(
            onClick = { viewModel.toggleRepeatMode() },
            modifier = Modifier
                .size(48.dp)
                .testTag("now_playing_repeat_btn")
        ) {
            val isRepeatActive = repeatMode != "OFF"
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (repeatMode) {
                        "ONE" -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (isRepeatActive) activeAccent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(if (isCompactHeight) 22.dp else 25.dp)
                )
                if (isRepeatActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 14.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(activeAccent)
                    )
                }
            }
        }
    }
}

/**
 * Minimal, borderless edge-to-edge bottom utility bar anchored above the system navigation bar:
 * Lyrics | Audio FX | Sleep | Drive | Share
 */
@Composable
fun DhunBottomUtilityBar(
    uiState: MusicUiState,
    isInlineLyricsVisible: Boolean,
    onToggleLyrics: () -> Unit,
    onOpenAudioFx: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenDriveMode: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFxActive = uiState.isEqEnabled || uiState.isBassBoostEnabled ||
        uiState.isVirtualizerEnabled || uiState.is8dAudioEnabled ||
        uiState.isDolbyEnabled || uiState.isReverbEnabled ||
        (uiState.isVolumeBoostEnabled && uiState.volumeBoostLevel > 100)

    val isSleepActive = uiState.sleepTimerOption != SleepTimerOption.OFF
    val sleepLabel = if (isSleepActive && uiState.sleepTimerRemainingMs > 0) {
        val mins = ((uiState.sleepTimerRemainingMs + 59999L) / 60000L).toInt()
        "${mins}m"
    } else {
        "Sleep"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomUtilityItem(
            icon = Icons.Rounded.Lyrics,
            label = "Lyrics",
            isActive = isInlineLyricsVisible,
            onClick = onToggleLyrics,
            modifier = Modifier
                .weight(1f)
                .testTag("utility_lyrics_btn")
        )
        BottomUtilityItem(
            icon = Icons.Rounded.Equalizer,
            label = "Audio FX",
            isActive = isFxActive,
            onClick = onOpenAudioFx,
            modifier = Modifier
                .weight(1f)
                .testTag("utility_audio_fx_btn")
        )
        BottomUtilityItem(
            icon = Icons.Rounded.Bedtime,
            label = sleepLabel,
            isActive = isSleepActive,
            onClick = onOpenSleepTimer,
            modifier = Modifier
                .weight(1f)
                .testTag("utility_sleep_btn")
        )
        BottomUtilityItem(
            icon = Icons.Rounded.DirectionsCar,
            label = "Drive",
            isActive = false,
            onClick = onOpenDriveMode,
            modifier = Modifier
                .weight(1f)
                .testTag("utility_drive_btn")
        )
        BottomUtilityItem(
            icon = Icons.Rounded.Share,
            label = "Share",
            isActive = false,
            onClick = onShare,
            modifier = Modifier
                .weight(1f)
                .testTag("utility_share_btn")
        )
    }
}

@Composable
private fun BottomUtilityItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val tint = if (isActive) activeColor else Color.White.copy(alpha = 0.72f)

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else Color.Transparent)
        )
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
        IconButton(
            onClick = onOpenQueue,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        val isMiddleSleepActive = uiState.sleepTimerOption != SleepTimerOption.OFF
        IconButton(
            onClick = onOpenSleepTimer,
            modifier = Modifier.size(44.dp)
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

        val isMiddleFxActive = uiState.isEqEnabled || uiState.isBassBoostEnabled ||
            uiState.isVirtualizerEnabled || uiState.is8dAudioEnabled ||
            uiState.isDolbyEnabled || uiState.isReverbEnabled ||
            (uiState.isVolumeBoostEnabled && uiState.volumeBoostLevel > 100)
        IconButton(
            onClick = onOpenAudioFx,
            modifier = Modifier.size(44.dp)
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

        val isMiddleSpeedActive = uiState.playbackSpeed != 1.0f || uiState.playbackPitch != 1.0f
        IconButton(
            onClick = onOpenSpeedDialog,
            modifier = Modifier.size(44.dp)
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

        IconButton(
            onClick = { viewModel.selectTab(NavigationTab.DRIVE_MODE) },
            modifier = Modifier.size(44.dp)
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
