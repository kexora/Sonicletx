package com.kexora.soniclet.player

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.lyrics.TimeSyncedKaraokeView
import com.kexora.soniclet.ui.components.player.AudioFidelityInspectorBar
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.CompatBlurTransformation
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dynamic ambient album art backdrop layer for the Dhun Now Playing screen.
 */
@Composable
fun DhunArtBackdrop(
    song: Song,
    animatedDominant: Color,
    animatedVibrant: Color,
    bgScale: Float
) {
    val context = LocalContext.current

    // Immediate dynamic vibrant gradient base - guarantees rich colors from frame 1
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedDominant.copy(alpha = 0.52f),
                        animatedVibrant.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    )

    // Blurred Cover Art Backdrop with fast 160ms crossfade to prevent delayed pop-in
    if (song.albumArtUri != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .crossfade(160)
                .apply {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        transformations(CompatBlurTransformation(context, radius = 22, sampling = 6f))
                    }
                }
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier
                .fillMaxSize()
                .scale(bgScale)
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(60.dp, 60.dp) else Modifier)
        )
    }

    // Dark dimming overlay for contrast & readability
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
    )

    // Gradient fade to background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.42f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                        MaterialTheme.colorScheme.background
                    ),
                    startY = 0f
                )
            )
    )
}

/**
 * Flagship Edge-to-Edge Portrait 1:1 Cover Art card with ambient radial glow, glass hairline rim,
 * horizontal swipe track skipping, and tap-to-toggle inline time-synced Karaoke Lyrics.
 */
@Composable
fun DhunPortraitArtCard(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    currentPosition: Long,
    maxArtHeight: Dp,
    artScale: Float,
    auraGlowScale: Float,
    auraGlowAlpha: Float,
    animatedDominant: Color,
    animatedVibrant: Color,
    isInlineLyricsVisible: Boolean,
    onToggleInlineLyrics: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    sheetDragModifier: Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val horizontalOffsetX = remember { Animatable(0f) }
    val swipeThresholdPx = with(density) { 64.dp.toPx() }
    val maxDragPx = with(density) { 92.dp.toPx() }
    val cardShape = RoundedCornerShape(24.dp)

    LaunchedEffect(song.id) {
        horizontalOffsetX.snapTo(0f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(sheetDragModifier)
    ) {
        // Soft ambient radial glow behind artwork (constrained so it never causes layout shift)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(maxHeight = maxArtHeight)
                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                .scale(auraGlowScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            listOf(
                                animatedDominant.copy(alpha = auraGlowAlpha * 0.85f),
                                animatedVibrant.copy(alpha = auraGlowAlpha * 0.40f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                animatedDominant.copy(alpha = auraGlowAlpha * 0.65f),
                                animatedVibrant.copy(alpha = auraGlowAlpha * 0.30f),
                                Color.Transparent
                            )
                        }
                    )
                )
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(38.dp) else Modifier)
        )

        // Edge-to-edge full-width 1:1 squircle artwork / inline Karaoke Lyrics frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(maxHeight = maxArtHeight)
                .aspectRatio(1f, matchHeightConstraintsFirst = false)
                .offset { IntOffset(horizontalOffsetX.value.roundToInt(), 0) }
                .scale(if (isInlineLyricsVisible) 1f else artScale)
                .shadow(
                    elevation = 16.dp,
                    shape = cardShape,
                    spotColor = animatedDominant.copy(alpha = 0.55f),
                    ambientColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(cardShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = cardShape
                )
                .pointerInput(song.id, isInlineLyricsVisible) {
                    if (!isInlineLyricsVisible) {
                        detectTapGestures(
                            onTap = { onToggleInlineLyrics() }
                        )
                    }
                }
                .pointerInput(song.id, isInlineLyricsVisible) {
                    if (!isInlineLyricsVisible) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val currentX = horizontalOffsetX.value
                                scope.launch {
                                    when {
                                        currentX < -swipeThresholdPx -> {
                                            horizontalOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                            onSwipeNext()
                                        }
                                        currentX > swipeThresholdPx -> {
                                            horizontalOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                            onSwipePrevious()
                                        }
                                        else -> {
                                            horizontalOffsetX.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    horizontalOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (abs(dragAmount) > 0f) {
                                    change.consume()
                                    scope.launch {
                                        val next = (horizontalOffsetX.value + dragAmount * 0.72f)
                                            .coerceIn(-maxDragPx, maxDragPx)
                                        horizontalOffsetX.snapTo(next)
                                    }
                                }
                            }
                        )
                    }
                }
                .testTag("dhun_large_cover_art"),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isInlineLyricsVisible,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "artwork_lyrics_flip",
                modifier = Modifier.fillMaxSize()
            ) { showLyrics ->
                if (showLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xD90A100D))
                    ) {
                        TimeSyncedKaraokeView(
                            lyrics = uiState.currentLyrics,
                            activeLyricIndex = uiState.activeLyricIndex,
                            currentPosition = currentPosition,
                            syncOffsetMs = uiState.lyricsSyncOffsetMs,
                            isKaraokeModeEnabled = uiState.isKaraokeModeEnabled,
                            lyricsSourceType = uiState.lyricsSourceType,
                            isLyricsLoading = uiState.isLyricsLoading,
                            isKariModeEnabled = uiState.isKariModeEnabled,
                            dominantColor = animatedDominant,
                            vibrantColor = animatedVibrant,
                            onSeekTo = { pos -> viewModel.seekTo(pos) },
                            onAdjustOffset = { delta -> viewModel.adjustLyricsOffset(delta) },
                            onResetOffset = { viewModel.resetLyricsOffset() },
                            onToggleKaraokeMode = { viewModel.toggleKaraokeMode() },
                            onImportLyrics = { uri -> viewModel.importLyricsFromUri(song, uri) },
                            onClearCustomLyrics = { viewModel.clearImportedLyrics(song) }
                        )

                        // Floating compact pill to flip back to Cover Art
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC0E1512),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(CircleShape)
                                .clickable { onToggleInlineLyrics() }
                                .testTag("inline_lyrics_close_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = "Show Cover Art",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Art",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (song.albumArtUri != null) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(song.albumArtUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Album Art — tap for lyrics or swipe to skip",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    FallbackAlbumArt(song.title, modifier = Modifier.fillMaxSize())
                                }
                            )
                        } else {
                            FallbackAlbumArt(song.title, modifier = Modifier.fillMaxSize())
                        }

                        // Subtle specular highlight on top edge
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.14f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Subtle tap-for-lyrics hint badge at bottom-end
                        if (uiState.currentLyrics.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x990A100D),
                                border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.16f)),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Lyrics available",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Landscape Cover Art card with ambient aura glow and audio fidelity codec badge.
 */
@Composable
fun DhunLandscapeArtCard(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    totalWidth: Dp,
    totalHeight: Dp,
    artScale: Float,
    auraGlowScale: Float,
    auraGlowAlpha: Float,
    animatedDominant: Color,
    animatedVibrant: Color,
    onOpenInspector: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Ambient Aura Glow behind artwork
            Box(
                modifier = Modifier
                    .size(minOf(totalWidth * 0.40f, totalHeight * 0.68f, 270.dp) * auraGlowScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                listOf(
                                    animatedDominant.copy(alpha = auraGlowAlpha),
                                    animatedVibrant.copy(alpha = auraGlowAlpha * 0.5f),
                                    Color.Transparent
                                )
                            } else {
                                listOf(
                                    animatedDominant.copy(alpha = auraGlowAlpha * 0.75f),
                                    animatedVibrant.copy(alpha = auraGlowAlpha * 0.35f),
                                    animatedVibrant.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            }
                        )
                    )
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(32.dp) else Modifier)
            )

            Box(
                modifier = Modifier
                    .sizeIn(
                        maxWidth = minOf(totalWidth * 0.40f, totalHeight * 0.68f, 270.dp),
                        maxHeight = minOf(totalHeight * 0.68f, 270.dp)
                    )
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .scale(artScale)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = animatedDominant.copy(alpha = 0.5f))
                    .testTag("dhun_large_cover_art"),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            FallbackAlbumArt(song.title, modifier = Modifier.fillMaxSize())
                        }
                    )
                } else {
                    FallbackAlbumArt(song.title, modifier = Modifier.fillMaxSize())
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        AudioFidelityInspectorBar(
            specs = uiState.currentSongAudioSpecs ?: viewModel.getAudioSpecs(song),
            onOpenInspector = onOpenInspector
        )
    }
}
