package com.kexora.soniclet.ui.main

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.ui.theme.BackgroundLuminanceDetector
import com.kexora.soniclet.ui.theme.BackgroundMetrics
import com.kexora.soniclet.ui.theme.BackgroundPresets
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.utils.CompatBlurTransformation

/**
 * Real-time background color and luminance analysis state producer.
 */
@Composable
fun rememberAppBackgroundMetrics(
    uiState: MusicUiState,
    isDarkTheme: Boolean
): State<BackgroundMetrics> {
    val context = LocalContext.current
    return produceState(
        initialValue = if (isDarkTheme) BackgroundMetrics.DEFAULT_DARK else BackgroundMetrics.DEFAULT_LIGHT,
        uiState.backgroundMode,
        uiState.customBackgroundUri,
        uiState.bgImageDim,
        uiState.solidBgColor,
        uiState.solidBgOpacity,
        uiState.gradientPresetIndex,
        uiState.gradientIntensity,
        uiState.isKariModeEnabled,
        uiState.kariDominantColor,
        isDarkTheme
    ) {
        this.value = when {
            uiState.isKariModeEnabled && uiState.backgroundMode != "CUSTOM" -> {
                val topColor = Color(uiState.kariDominantColor ?: uiState.accentColor)
                val midColor = Color(uiState.kariVibrantColor ?: 0xFF0B251CL)
                val botColor = Color(uiState.kariDarkMutedColor ?: 0xFF070B09L)
                BackgroundLuminanceDetector.analyzeGradient(listOf(topColor, midColor, botColor), 1f)
            }
            uiState.backgroundMode == "CUSTOM" && uiState.customBackgroundUri != null -> {
                BackgroundLuminanceDetector.analyzeWallpaperUri(
                    context,
                    uiState.customBackgroundUri.toString(),
                    uiState.bgImageDim
                )
            }
            uiState.backgroundMode == "GRADIENT" -> {
                val colors = BackgroundPresets.getGradientColors(
                    presetIndex = uiState.gradientPresetIndex,
                    dominantColor = uiState.kariDominantColor,
                    vibrantColor = uiState.kariVibrantColor
                )
                BackgroundLuminanceDetector.analyzeGradient(colors, uiState.gradientIntensity)
            }
            uiState.backgroundMode == "SOLID" -> {
                BackgroundLuminanceDetector.analyzeSolid(Color(uiState.solidBgColor), uiState.solidBgOpacity)
            }
            uiState.backgroundMode == "WHITE" -> BackgroundMetrics.DEFAULT_LIGHT
            uiState.backgroundMode == "AMOLED" -> BackgroundMetrics.DEFAULT_DARK
            else -> {
                if (isDarkTheme) BackgroundMetrics.DEFAULT_DARK else BackgroundMetrics.DEFAULT_LIGHT
            }
        }
    }
}

/**
 * Renders the active background layer:
 * - Kari Mode dynamic cover-art gradient with blur and legibility scrim
 * - CUSTOM wallpaper image with CompatBlurTransformation / RenderEffect blur and dim overlay
 * - GRADIENT preset background
 * - SOLID color overlay
 * - Intelligent contrast protector scrim (`recommendedSurfaceScrimAlpha`)
 */
@Composable
fun AppBackground(
    uiState: MusicUiState,
    isDarkTheme: Boolean,
    currentBgMetrics: BackgroundMetrics
) {
    if (uiState.isKariModeEnabled && uiState.backgroundMode != "CUSTOM") {
        // Dynamic Glow (Kari Mode) Cover Art Gradient Background
        val hasTrackPalette = uiState.currentSong != null && uiState.kariDominantColor != null
        val topColor = Color(uiState.kariDominantColor ?: uiState.accentColor)
        val midColor = Color(uiState.kariVibrantColor ?: 0xFF0B251CL)
        val botColor = Color(uiState.kariDarkMutedColor ?: 0xFF070B09L)
        val topAlpha = if (hasTrackPalette) (uiState.kariOpacity * 0.88f).coerceIn(0.15f, 1f) else (uiState.kariOpacity * 0.34f).coerceIn(0.10f, 0.45f)
        val midAlpha = if (hasTrackPalette) (uiState.kariOpacity * 0.58f).coerceIn(0.10f, 0.9f) else (uiState.kariOpacity * 0.45f).coerceIn(0.12f, 0.55f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && uiState.kariBlur > 0f) {
                        Modifier.blur(uiState.kariBlur.dp)
                    } else {
                        Modifier
                    }
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            topColor.copy(alpha = topAlpha),
                            midColor.copy(alpha = midAlpha),
                            botColor.copy(alpha = uiState.kariOpacity.coerceIn(0.25f, 1f)),
                            Color(0xFF070B09)
                        )
                    )
                )
        )
        // Legibility overlay for clean text & controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = ((1f - uiState.kariOpacity) * 0.5f).coerceIn(0f, 0.6f)),
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.78f)
                        )
                    )
                )
        )
    } else {
        when (uiState.backgroundMode) {
            "CUSTOM" -> {
                if (!uiState.customBackgroundUri.isNullOrBlank()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uiState.customBackgroundUri)
                            .crossfade(true)
                            .crossfade(1000)
                            .apply {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && uiState.bgImageBlur > 0f) {
                                    val blurRad = (uiState.bgImageBlur * 1.5f).toInt().coerceIn(3, 25)
                                    transformations(CompatBlurTransformation(context, radius = blurRad, sampling = 4f))
                                }
                            }
                            .build(),
                        contentDescription = "Custom Background",
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && uiState.bgImageBlur > 0f) {
                                    Modifier.blur(uiState.bgImageBlur.dp)
                                } else {
                                    Modifier
                                }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = uiState.bgImageDim))
                    )
                } else {
                    val fallbackColor = if (isDarkTheme) Color(0xFF121218) else Color(0xFFF1F5F9)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(fallbackColor)
                    )
                }
            }
            "GRADIENT" -> {
                val gradientColors = BackgroundPresets.getGradientColors(
                    presetIndex = uiState.gradientPresetIndex,
                    dominantColor = uiState.kariDominantColor,
                    vibrantColor = uiState.kariVibrantColor
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && uiState.gradientBlur > 0f) {
                                Modifier.blur(uiState.gradientBlur.dp)
                            } else {
                                Modifier
                            }
                        )
                        .background(
                            Brush.verticalGradient(
                                colors = gradientColors.map { it.copy(alpha = uiState.gradientIntensity) }
                            )
                        )
                )
                if (uiState.gradientIntensity < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = (1f - uiState.gradientIntensity) * 0.8f))
                    )
                }
            }
            "SOLID" -> {
                if (uiState.solidBgOpacity > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(uiState.solidBgColor).copy(alpha = uiState.solidBgOpacity))
                    )
                }
            }
            // "OFF" / "DEFAULT" - uses pure clean MaterialTheme background
        }
    }

    // Intelligent contrast protector overlay if wallpaper/gradient is bright or high variance
    if (currentBgMetrics.recommendedSurfaceScrimAlpha > 0f &&
        (uiState.backgroundMode == "CUSTOM" || uiState.backgroundMode == "GRADIENT")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    (if (currentBgMetrics.isDark) Color.Black else Color.White)
                        .copy(alpha = currentBgMetrics.recommendedSurfaceScrimAlpha)
                )
        )
    }
}
