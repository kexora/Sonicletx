package com.kexora.soniclet.player

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
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
 * 100% Opaque Dynamic Blur Backdrop for Now Playing — zero bleed-through from underlying screens.
 * Renders a solid Color(0xFF070B09) base, heavily blurred full-bleed album artwork
 * (Modifier.blur(56.dp) on Android 12+ and FastBlurUtil bitmap blur on Android 11-),
 * and a smooth vertical dark frosted gradient scrim.
 */
@Composable
fun DhunArtBackdrop(
    song: Song,
    animatedDominant: Color,
    animatedVibrant: Color,
    bgScale: Float
) {
    val context = LocalContext.current
    val isAppLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val hasArtwork = song.albumArtUri != null

    if (!hasArtwork && isAppLightMode) {
        // Light Mode fallback when track has no cover art: clean semantic canvas with soft accent crown glow
        val baseLight = MaterialTheme.colorScheme.background
        val primary = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseLight)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to primary.copy(alpha = 0.12f),
                            0.28f to primary.copy(alpha = 0.04f),
                            0.65f to Color.Transparent,
                            1.00f to Color.Transparent
                        )
                    )
                )
        )
        return
    }

    val solidBase = Color(0xFF060807)

    // Layer 1: 100% opaque OLED base — guarantees zero bleed-through from Home/Songs/MiniPlayer
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(solidBase)
    )

    // Layer 2: Ambient color wash for instant frame-1 color richness
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedDominant.copy(alpha = 0.34f),
                        animatedVibrant.copy(alpha = 0.16f),
                        solidBase
                    )
                )
            )
    )

    // Layer 3: Full-bleed heavily blurred album artwork
    if (hasArtwork) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .crossfade(220)
                .apply {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        transformations(CompatBlurTransformation(context, radius = 25, sampling = 6f))
                    }
                }
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier
                .fillMaxSize()
                .scale(bgScale)
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(56.dp, 56.dp) else Modifier)
        )
    }

    // Layer 4: Smooth vertical dark frosted gradient scrim (crisp contrast + dynamic album glow)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xA6060807),
                        Color(0xCC060807),
                        Color(0xF5060807)
                    )
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
            .fillMaxSize()
            .then(if (!isInlineLyricsVisible) sheetDragModifier else Modifier)
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
                // Box-Free, Transparent Inline Lyrics directly over the blurred album backdrop
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
                    onClearCustomLyrics = { viewModel.clearImportedLyrics(song) },
                    isPlaying = uiState.isPlaying,
                    onReturnToArt = { onToggleInlineLyrics() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Soft ambient radial glow behind artwork
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
                                            animatedDominant.copy(alpha = auraGlowAlpha * 0.80f),
                                            animatedVibrant.copy(alpha = auraGlowAlpha * 0.35f),
                                            Color.Transparent
                                        )
                                    } else {
                                        listOf(
                                            animatedDominant.copy(alpha = auraGlowAlpha * 0.60f),
                                            animatedVibrant.copy(alpha = auraGlowAlpha * 0.28f),
                                            Color.Transparent
                                        )
                                    }
                                )
                            )
                            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(38.dp) else Modifier)
                    )

                    // Clean 1:1 squircle album cover (zero stray badges, crisp glass hairline rim)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(maxHeight = maxArtHeight)
                            .aspectRatio(1f, matchHeightConstraintsFirst = false)
                            .offset { IntOffset(horizontalOffsetX.value.roundToInt(), 0) }
                            .scale(artScale)
                            .shadow(
                                elevation = 16.dp,
                                shape = cardShape,
                                spotColor = animatedDominant.copy(alpha = 0.50f),
                                ambientColor = Color.Black.copy(alpha = 0.55f)
                            )
                            .clip(cardShape)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.14f),
                                shape = cardShape
                            )
                            .pointerInput(song.id) {
                                detectTapGestures(
                                    onTap = { onToggleInlineLyrics() }
                                )
                            }
                            .pointerInput(song.id) {
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
                            .testTag("dhun_large_cover_art"),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = song,
                            contentKey = { it.id },
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) +
                                    slideInHorizontally(animationSpec = tween(220)) { it / 10 }) togetherWith
                                    (fadeOut(animationSpec = tween(180)) +
                                        slideOutHorizontally(animationSpec = tween(180)) { -it / 10 })
                            },
                            label = "artwork_track_transition",
                            modifier = Modifier.fillMaxSize()
                        ) { targetSong ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (targetSong.albumArtUri != null) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(targetSong.albumArtUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Album Art — tap for lyrics or swipe to skip",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        error = {
                                            FallbackAlbumArt(targetSong.title, modifier = Modifier.fillMaxSize())
                                        }
                                    )
                                } else {
                                    FallbackAlbumArt(targetSong.title, modifier = Modifier.fillMaxSize())
                                }

                                // Subtle specular highlight on top edge
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.White.copy(alpha = 0.12f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
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
