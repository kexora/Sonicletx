package com.kexora.soniclet.player

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.FadingMarqueeText
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.CompatBlurTransformation
import com.kexora.soniclet.utils.SmartTitleCleaner
import kotlinx.coroutines.delay
import java.util.Locale

private val DriveBackdropBase = Color(0xFF060807)

/**
 * Distraction-free modern Car Mode (Drive Mode) for Soniclet.
 * Designed for instant glanceability, oversized touch targets, and zero visual clutter.
 */
@Composable
fun DriveMode(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val song = uiState.currentSong
    val isPlaying = uiState.isPlaying
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Keep screen awake automatically while Drive Mode is active
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val currentPositionMs by viewModel.currentPosition.collectAsStateWithLifecycle()
    val totalDuration = if (uiState.totalDuration > 0L) uiState.totalDuration else (song?.duration ?: 0L)

    var swipeFeedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(swipeFeedback) {
        if (swipeFeedback != null) {
            delay(850)
            swipeFeedback = null
        }
    }

    val queueIndex = remember(song, uiState.activeQueue) {
        if (song != null) uiState.activeQueue.indexOfFirst { it.id == song.id } else -1
    }
    val queueCounter = remember(queueIndex, uiState.activeQueue.size) {
        if (queueIndex >= 0 && uiState.activeQueue.isNotEmpty()) {
            "${queueIndex + 1} / ${uiState.activeQueue.size}"
        } else {
            ""
        }
    }

    val isFavorite = song != null && uiState.favoriteSongIds.contains(song.id)
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DriveBackdropBase)
            .pointerInput(song?.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (song != null) {
                            if (dragAccumulator > 75f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                swipeFeedback = "Previous Song"
                                viewModel.playPrevious()
                            } else if (dragAccumulator < -75f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                swipeFeedback = "Next Song"
                                viewModel.playNext()
                            }
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    }
                )
            }
            .testTag("drive_mode_root")
    ) {
        // 1. Opaque Ambient Blurred Artwork Backdrop
        DriveAmbientBackdrop(song = song, isPlaying = isPlaying)

        // 2. Main Foreground Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            DriveTopBar(
                queueCounter = queueCounter,
                onExit = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }
            )

            if (song != null) {
                if (isLandscape) {
                    DriveLandscapeContent(
                        song = song,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        isShuffle = uiState.isShuffleEnabled,
                        currentPositionMs = currentPositionMs,
                        totalDuration = totalDuration,
                        onPlayPause = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        },
                        onPrevious = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playPrevious()
                        },
                        onNext = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playNext()
                        },
                        onSeek = { targetMs ->
                            viewModel.seekTo(targetMs)
                        },
                        onToggleFavorite = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(song.id)
                        },
                        onToggleShuffle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleShuffle()
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    DrivePortraitContent(
                        song = song,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        isShuffle = uiState.isShuffleEnabled,
                        currentPositionMs = currentPositionMs,
                        totalDuration = totalDuration,
                        onPlayPause = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        },
                        onPrevious = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playPrevious()
                        },
                        onNext = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playNext()
                        },
                        onSeek = { targetMs ->
                            viewModel.seekTo(targetMs)
                        },
                        onToggleFavorite = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(song.id)
                        },
                        onToggleShuffle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleShuffle()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                DriveEmptyState(
                    onBack = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Clean Swipe Feedback Pill
        AnimatedVisibility(
            visible = swipeFeedback != null,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.88f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            Surface(
                color = Color(0xFF0E1411).copy(alpha = 0.92f),
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (swipeFeedback == "Previous Song") {
                            Icons.Rounded.SkipPrevious
                        } else {
                            Icons.Rounded.SkipNext
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = swipeFeedback.orEmpty(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveAmbientBackdrop(song: Song?, isPlaying: Boolean) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.22f else 0.10f,
        animationSpec = tween(500),
        label = "drive_glow_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (song?.albumArtUri != null) {
            val imageRequest = remember(song.albumArtUri, context) {
                ImageRequest.Builder(context)
                    .data(song.albumArtUri)
                    .crossfade(450)
                    .apply {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            transformations(CompatBlurTransformation(context, radius = 25, sampling = 6f))
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(56.dp)
                        } else {
                            Modifier
                        }
                    )
            )
        }

        // Rich dark contrast scrim (0.78f opacity) so controls remain ultra-legible day & night
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DriveBackdropBase.copy(alpha = 0.78f))
        )

        // Soft accent aura centered behind the artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = glowAlpha),
                            Color.Transparent
                        ),
                        radius = 850f
                    )
                )
        )
    }
}

@Composable
private fun DriveTopBar(
    queueCounter: String,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Drive Mode status pill + queue counter
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.08f),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.10f)),
            modifier = Modifier.height(38.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsCar,
                    contentDescription = "Drive Mode active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Drive Mode",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = Color.White
                )
                if (queueCounter.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                    Text(
                        text = queueCounter,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
        }

        // Right: Clean Exit pill button
        Surface(
            onClick = onExit,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .height(38.dp)
                .testTag("drive_mode_exit_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Exit Drive Mode",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Exit",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun DrivePortraitContent(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isShuffle: Boolean,
    currentPositionMs: Long,
    totalDuration: Long,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanTitle = remember(song.title, song.filePath) {
        SmartTitleCleaner.cleanTitle(song.title, song.filePath)
    }
    val cleanArtist = remember(song.artist) {
        if (SmartTitleCleaner.isPlaceholderArtist(song.artist)) "Unknown Artist" else song.artist.trim()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Centered Artwork
        DriveArtworkCard(
            song = song,
            isPlaying = isPlaying,
            size = 196.dp
        )

        // 2-Line Track Info Only (Zero album/folder noise)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FadingMarqueeText(
                text = cleanTitle,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.3).sp
                ),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = cleanArtist,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Custom Smooth Pill Seekbar (Zero thumbs or dots)
        DrivePillSeekbar(
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDuration,
            onSeek = onSeek
        )

        // Oversized Primary Transport Controls
        DrivePrimaryControls(
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext
        )

        // Secondary 2-Pill Quick Bar (Favorite + Shuffle)
        DriveSecondaryPillBar(
            isFavorite = isFavorite,
            isShuffle = isShuffle,
            onToggleFavorite = onToggleFavorite,
            onToggleShuffle = onToggleShuffle
        )

        // Minimal Footer Hint
        Text(
            text = "Swipe anywhere to change song",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            color = Color.White.copy(alpha = 0.42f),
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun DriveLandscapeContent(
    song: Song,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isShuffle: Boolean,
    currentPositionMs: Long,
    totalDuration: Long,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanTitle = remember(song.title, song.filePath) {
        SmartTitleCleaner.cleanTitle(song.title, song.filePath)
    }
    val cleanArtist = remember(song.artist) {
        if (SmartTitleCleaner.isPlaceholderArtist(song.artist)) "Unknown Artist" else song.artist.trim()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Left: Artwork + Swipe hint
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DriveArtworkCard(
                song = song,
                isPlaying = isPlaying,
                size = 172.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Swipe anywhere to change song",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.42f),
                textAlign = TextAlign.Center
            )
        }

        // Right: Title, Artist, Seekbar, Primary + Secondary Controls
        Column(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FadingMarqueeText(
                    text = cleanTitle,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cleanArtist,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DrivePillSeekbar(
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDuration,
                onSeek = onSeek
            )

            DrivePrimaryControls(
                isPlaying = isPlaying,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext
            )

            DriveSecondaryPillBar(
                isFavorite = isFavorite,
                isShuffle = isShuffle,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle
            )
        }
    }
}

@Composable
private fun DriveArtworkCard(
    song: Song,
    isPlaying: Boolean,
    size: Dp
) {
    val context = LocalContext.current
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "drive_art_scale"
    )

    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .size(size)
            .scale(artScale)
            .shadow(
                elevation = if (isPlaying) 24.dp else 10.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(Color(0xFF141917))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .crossfade(300)
                .build(),
            contentDescription = "Album cover for ${song.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = { FallbackAlbumArt() },
            loading = { FallbackAlbumArt() }
        )
    }
}

@Composable
private fun DrivePillSeekbar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val playbackFraction = remember(currentPositionMs, totalDurationMs) {
        if (totalDurationMs > 0L) {
            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val displayFraction = if (isDragging) dragFraction else playbackFraction
    val displayPositionMs = if (isDragging) {
        (dragFraction * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)
    } else {
        currentPositionMs.coerceIn(0L, totalDurationMs.coerceAtLeast(0L))
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = Color.White.copy(alpha = 0.14f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drive_mode_seekbar")
    ) {
        // Generous 36.dp vertical touch hitbox around the 10.dp sleek pill bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        if (totalDurationMs > 0L) {
                            val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            onSeek((frac * totalDurationMs).toLong())
                        }
                    }
                }
                .pointerInput(totalDurationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (totalDurationMs > 0L) {
                                isDragging = true
                                dragFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            if (isDragging && totalDurationMs > 0L) {
                                onSeek((dragFraction * totalDurationMs).toLong())
                            }
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, _ ->
                            if (totalDurationMs > 0L) {
                                change.consume()
                                dragFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            ) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                // Inactive track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = radius
                )
                val fillWidth = (size.width * displayFraction).coerceAtLeast(
                    if (displayFraction > 0f) size.height else 0f
                )
                if (fillWidth > 0f) {
                    // Subtle accent glow
                    drawRoundRect(
                        color = activeColor.copy(alpha = 0.28f),
                        topLeft = Offset(0f, -2.dp.toPx()),
                        size = Size(fillWidth, size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius((size.height + 4.dp.toPx()) / 2f)
                    )
                    // Active progress fill
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset.Zero,
                        size = Size(fillWidth, size.height),
                        cornerRadius = radius
                    )
                }
            }
        }

        // Clean tabular timestamps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDriveTime(displayPositionMs),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = Color.White.copy(alpha = 0.78f)
            )
            Text(
                text = formatDriveTime(totalDurationMs),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = Color.White.copy(alpha = 0.58f)
            )
        }
    }
}

@Composable
private fun DrivePrimaryControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val playCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 46.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "drive_play_corner"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 76.dp Circular Previous
        Surface(
            onClick = onPrevious,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .size(76.dp)
                .testTag("drive_mode_prev_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous song",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Giant 92.dp Morphing Play/Pause
        Surface(
            onClick = onPlayPause,
            shape = RoundedCornerShape(playCornerRadius),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(92.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(playCornerRadius),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                )
                .testTag("drive_mode_play_pause_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(44.dp)
                        .then(if (!isPlaying) Modifier.padding(start = 3.dp) else Modifier)
                )
            }
        }

        // 76.dp Circular Next
        Surface(
            onClick = onNext,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .size(76.dp)
                .testTag("drive_mode_next_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next song",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun DriveSecondaryPillBar(
    isFavorite: Boolean,
    isShuffle: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    val favBg by animateColorAsState(
        targetValue = if (isFavorite) primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "drive_fav_bg"
    )
    val favBorder by animateColorAsState(
        targetValue = if (isFavorite) primary.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(200),
        label = "drive_fav_border"
    )
    val favScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "drive_fav_scale"
    )

    val shuffleBg by animateColorAsState(
        targetValue = if (isShuffle) primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "drive_shuffle_bg"
    )
    val shuffleBorder by animateColorAsState(
        targetValue = if (isShuffle) primary.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(200),
        label = "drive_shuffle_border"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Favorite Pill
        Surface(
            onClick = onToggleFavorite,
            shape = CircleShape,
            color = favBg,
            border = BorderStroke(1.dp, favBorder),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("drive_mode_favorite_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) primary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(20.dp)
                        .scale(favScale)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFavorite) "Liked" else "Favorite",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = if (isFavorite) primary else Color.White.copy(alpha = 0.90f)
                )
            }
        }

        // Shuffle Pill
        Surface(
            onClick = onToggleShuffle,
            shape = CircleShape,
            color = shuffleBg,
            border = BorderStroke(1.dp, shuffleBorder),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("drive_mode_shuffle_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = if (isShuffle) "Turn off shuffle" else "Turn on shuffle",
                    tint = if (isShuffle) primary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isShuffle) "Shuffle On" else "Shuffle",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = if (isShuffle) primary else Color.White.copy(alpha = 0.90f)
                )
            }
        }
    }
}

@Composable
private fun DriveEmptyState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No song playing",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick a song from your library to use Drive Mode",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = Color.White.copy(alpha = 0.68f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onBack,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .height(48.dp)
                .testTag("drive_mode_back_to_songs")
        ) {
            Text(
                text = "Back to Songs",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

private fun formatDriveTime(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
