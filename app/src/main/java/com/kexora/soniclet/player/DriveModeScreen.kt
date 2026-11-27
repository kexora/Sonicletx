package com.kexora.soniclet.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import kotlinx.coroutines.delay

@Composable
fun DriveMode(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val song = uiState.currentSong
    val isPlaying = uiState.isPlaying
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Keep screen awake while in Drive Mode (critical for car drivers)
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Playback position state
    val currentPositionMs by viewModel.currentPosition.collectAsStateWithLifecycle()
    val totalDuration = if (uiState.totalDuration > 0) uiState.totalDuration else (song?.duration ?: 0L)

    // Gesture feedback state (brief flash on swipe)
    var gestureFeedbackText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gestureFeedbackText) {
        if (gestureFeedbackText != null) {
            delay(900)
            gestureFeedbackText = null
        }
    }

    // Queue position
    val queueIndex = remember(song, uiState.activeQueue) {
        if (song != null) uiState.activeQueue.indexOfFirst { it.id == song.id } else -1
    }
    val queueText = if (queueIndex != -1 && uiState.activeQueue.isNotEmpty()) {
        "${queueIndex + 1} / ${uiState.activeQueue.size}"
    } else {
        ""
    }

    val isFavorite = song != null && uiState.favoriteSongIds.contains(song.id)

    // Drag detection for full-screen swipes
    var dragAccumulator by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator > 70f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            gestureFeedbackText = "⏮️ Previous Track"
                            viewModel.playPrevious()
                        } else if (dragAccumulator < -70f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            gestureFeedbackText = "Next Track ⏭️"
                            viewModel.playNext()
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    }
                )
            }
    ) {
        // Ambient backdrop glowing aura
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        colors = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.22f else 0.10f),
                                Color(0xFFE91E63).copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.18f else 0.08f),
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.10f else 0.04f),
                                Color(0xFFE91E63).copy(alpha = 0.05f),
                                Color(0xFFE91E63).copy(alpha = 0.01f),
                                Color.Transparent
                            )
                        }
                    )
                )
                .then(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) Modifier.blur(80.dp) else Modifier)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top Bar: Drive Mode status badge + Screen Keep-On indicator + Close button
            DriveModeTopBar(
                queueText = queueText,
                isLandscape = isLandscape,
                onClose = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }
            )

            // Main Content Area
            if (song != null) {
                if (isLandscape) {
                    DriveModeLandscapeContent(
                        song = song,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        currentPositionMs = currentPositionMs,
                        totalDuration = totalDuration,
                        uiState = uiState,
                        viewModel = viewModel,
                        haptic = haptic
                    )
                } else {
                    DriveModePortraitContent(
                        song = song,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        currentPositionMs = currentPositionMs,
                        totalDuration = totalDuration,
                        uiState = uiState,
                        viewModel = viewModel,
                        haptic = haptic
                    )
                }
            } else {
                DriveModeEmptyState(onDismiss = onDismiss)
            }
        }

        // Animated Swipe Gesture Toast / Feedback overlay
        AnimatedVisibility(
            visible = gestureFeedbackText != null,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                shadowElevation = 12.dp
            ) {
                Text(
                    text = gestureFeedbackText ?: "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Top bar header with driving badge, screen keep-awake indicator, and big exit button.
 */
@Composable
private fun DriveModeTopBar(
    queueText: String,
    isLandscape: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 28.dp else 20.dp,
                vertical = if (isLandscape) 8.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Drive mode badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DirectionsCar,
                contentDescription = null,
                tint = Color(0xFFFFB300), // Amber warning car icon
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "DRIVE MODE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                ),
                color = Color.White
            )
            // Screen awake indicator
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676)) // Neon green pulse dot
            )
            Text(
                text = "Awake",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Queue Indicator & Big Close Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (queueText.isNotEmpty()) {
                Surface(
                    color = Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "🎵 $queueText",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Big, high-contrast close button (minimum 48dp touch target)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(if (isLandscape) 44.dp else 52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Exit Drive Mode",
                    tint = Color.White,
                    modifier = Modifier.size(if (isLandscape) 26.dp else 30.dp)
                )
            }
        }
    }
}

/**
 * Portrait Layout for Drive Mode
 */
@Composable
private fun DriveModePortraitContent(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    currentPositionMs: Long,
    totalDuration: Long,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Large Album Art with Glow & Rounded corners
        DriveAlbumArt(
            song = song,
            size = 210.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Track Title & Artist (Extra readable, High contrast, Marquee)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = song.artist,
                color = Color(0xFFB0B3B8), // High readability soft gray
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (song.album.isNotBlank() && song.album != song.title) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.album,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Seek Bar & Duration Display
        DriveProgressBar(
            currentPositionMs = currentPositionMs,
            totalDuration = totalDuration,
            onSeek = { targetMs ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.seekTo(targetMs)
            }
        )

        // Main Playback Controls: Previous, Play/Pause, Next
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Previous Button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.playPrevious()
                },
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            // Central Play/Pause Button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.togglePlayPause()
                },
                modifier = Modifier
                    .size(86.dp)
                    .shadow(12.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (!isPlaying) Modifier.padding(start = 4.dp) else Modifier
                            )
                    )
                }
            }

            // Next Button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.playNext()
                },
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        // Secondary Driving Actions: Rewind 10s, Heart, Shuffle, Repeat, Forward 10s
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            // Rewind 10s
            DriveActionButton(
                icon = Icons.Rounded.Replay10,
                contentDescription = "Rewind 10 Seconds",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                    viewModel.seekTo(target)
                }
            )

            // Shuffle Button
            DriveActionButton(
                icon = Icons.Rounded.Shuffle,
                contentDescription = "Toggle Shuffle",
                isActive = uiState.isShuffleEnabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleShuffle()
                }
            )

            // Favorite Button (Big Heart, red when liked)
            DriveFavoriteButton(
                isFavorite = isFavorite,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleFavorite(song.id)
                }
            )

            // Repeat Mode Button
            DriveRepeatButton(
                repeatMode = uiState.repeatMode,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleRepeatMode()
                }
            )

            // Forward 10s
            DriveActionButton(
                icon = Icons.Rounded.Forward10,
                contentDescription = "Forward 10 Seconds",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = (currentPositionMs + 10000L).coerceAtMost(totalDuration)
                    viewModel.seekTo(target)
                }
            )
        }

        // Gesture guide pill at bottom
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "◄ SWIPE LEFT / RIGHT TO CHANGE TRACK ►",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * Landscape Layout for Car Dashboard Mounts
 */
@Composable
private fun DriveModeLandscapeContent(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    currentPositionMs: Long,
    totalDuration: Long,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Column: Album Art + Song Title & Artist + Progress Bar
        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .padding(end = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DriveAlbumArt(song = song, size = 120.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = song.artist,
                        color = Color(0xFFB0B3B8),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (song.album.isNotBlank()) {
                        Text(
                            text = song.album,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seek progress bar
            DriveProgressBar(
                currentPositionMs = currentPositionMs,
                totalDuration = totalDuration,
                onSeek = { targetMs ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.seekTo(targetMs)
                }
            )

            // Swipe hint
            Text(
                text = "◄ SWIPE SCREEN FOR NEXT/PREV ►",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Right Column: Giant Controls & Driving Actions
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Playback Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.playPrevious()
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Play / Pause Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.togglePlayPause()
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(10.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(44.dp)
                                .then(
                                    if (!isPlaying) Modifier.padding(start = 3.dp) else Modifier
                                )
                        )
                    }
                }

                // Next Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.playNext()
                    },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Row: Rewind, Heart, Shuffle, Repeat, Forward
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DriveActionButton(
                    icon = Icons.Rounded.Replay10,
                    contentDescription = "Rewind 10 Seconds",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                        viewModel.seekTo(target)
                    }
                )

                DriveActionButton(
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    isActive = uiState.isShuffleEnabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleShuffle()
                    }
                )

                DriveFavoriteButton(
                    isFavorite = isFavorite,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleFavorite(song.id)
                    }
                )

                DriveRepeatButton(
                    repeatMode = uiState.repeatMode,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleRepeatMode()
                    }
                )

                DriveActionButton(
                    icon = Icons.Rounded.Forward10,
                    contentDescription = "Forward 10 Seconds",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val target = (currentPositionMs + 10000L).coerceAtMost(totalDuration)
                        viewModel.seekTo(target)
                    }
                )
            }
        }
    }
}

/**
 * Album Art with Fallback App Logo
 */
@Composable
private fun DriveAlbumArt(
    song: Song,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .size(size)
            .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color.Black),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E2028),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        if (song.albumArtUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album Artwork for ${song.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                },
                error = {
                    FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                }
            )
        } else {
            FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * High visibility seek bar and timestamp row for driving mode
 */
@Composable
private fun DriveProgressBar(
    currentPositionMs: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit
) {
    val progress = if (totalDuration > 0) {
        (currentPositionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // High visibility slider with broad touch surface
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                val newMs = (newProgress * totalDuration).toLong()
                onSeek(newMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPositionMs),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDuration(totalDuration),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Quick Action Button with large touch target and active highlight
 */
@Composable
private fun DriveActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.08f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 28.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Heart Favorite Button with pulsing red heart state
 */
@Composable
private fun DriveFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(
                if (isFavorite) Color(0xFFFF1744).copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.08f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 30.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
            tint = if (isFavorite) Color(0xFFFF1744) else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * Repeat Button supporting OFF, ALL, and ONE modes
 */
@Composable
private fun DriveRepeatButton(
    repeatMode: RepeatMode,
    onClick: () -> Unit
) {
    val isActive = repeatMode != RepeatMode.OFF
    val icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.08f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 28.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Repeat Mode: $repeatMode",
            tint = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Empty state when no track is currently playing
 */
@Composable
private fun DriveModeEmptyState(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicOff,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NO MUSIC PLAYING",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a song from your library to start driving",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(52.dp)
        ) {
            Text(
                text = "Back to Library",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

