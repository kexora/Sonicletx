package com.kexora.soniclet.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.lyrics.*
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.utils.CompatBlurTransformation

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

    // Immediate dynamic vibrant gradient base - guarantees gorgeous rich colors from frame 1
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedDominant.copy(alpha = 0.55f),
                        animatedVibrant.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
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
            .background(Color.Black.copy(alpha = 0.62f))
    )

    // Gradient fade to background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.40f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background
                    ),
                    startY = 0f
                )
            )
    )
}

/**
 * Portrait 1:1 Cover Art card with pulsing audio aura glow and top specular highlight.
 */
@Composable
fun DhunPortraitArtCard(
    song: Song,
    targetArtSize: Dp,
    isCompactHeight: Boolean,
    artScale: Float,
    auraGlowScale: Float,
    auraGlowAlpha: Float,
    animatedDominant: Color,
    animatedVibrant: Color,
    sheetDragModifier: Modifier
) {
    val context = LocalContext.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-18).dp)
            .then(sheetDragModifier),
        propagateMinConstraints = true
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing Audio Aura Glow (Fixed size with visual scale to prevent layout bouncing)
            Box(
                modifier = Modifier
                    .size(targetArtSize)
                    .scale(auraGlowScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                listOf(
                                    animatedDominant.copy(alpha = auraGlowAlpha),
                                    animatedVibrant.copy(alpha = auraGlowAlpha * 0.45f),
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
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(36.dp) else Modifier)
            )

            // Main Cover Art Card (YouTube Music style 1:1, rounded corners, drop shadow)
            Box(
                modifier = Modifier
                    .size(targetArtSize)
                    .scale(artScale)
                    .clip(RoundedCornerShape(if (isCompactHeight) 18.dp else 22.dp))
                    .shadow(
                        elevation = if (isCompactHeight) 10.dp else 16.dp,
                        shape = RoundedCornerShape(if (isCompactHeight) 18.dp else 22.dp),
                        spotColor = animatedDominant.copy(alpha = 0.5f)
                    )
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

                // Specular light highlight on top edge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
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
