package com.kexora.soniclet.ui.main

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.ui.theme.BackgroundLuminanceDetector
import com.kexora.soniclet.ui.theme.BackgroundMetrics
import com.kexora.soniclet.ui.theme.BackgroundPresets
import com.kexora.soniclet.utils.CompatBlurTransformation

/**
 * Real-time background luminance analysis state producer.
 *
 * Strictly derives dark/light icon contrast from the ACTUAL resolved background canvas,
 * NEVER from the user's accent color (preventing black status bar icons on Dark Mode with
 * bright accents like Yellow or Cyan).
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
        isDarkTheme
    ) {
        this.value = when {
            uiState.backgroundMode == "CUSTOM" && !uiState.customBackgroundUri.isNullOrBlank() -> {
                BackgroundLuminanceDetector.analyzeWallpaperUri(
                    context = context,
                    uriString = uiState.customBackgroundUri,
                    dimFactor = uiState.bgImageDim
                )
            }
            uiState.backgroundMode == "WHITE" -> BackgroundMetrics.DEFAULT_LIGHT
            uiState.backgroundMode == "AMOLED" -> BackgroundMetrics.DEFAULT_DARK
            else -> {
                // Solid, Gradient, Dynamic Glow, and Default all render over the active Dark/Light canvas
                if (isDarkTheme) BackgroundMetrics.DEFAULT_DARK else BackgroundMetrics.DEFAULT_LIGHT
            }
        }
    }
}

/**
 * Unified, Seamless Edge-to-Edge Root Background Canvas.
 *
 * Renders a single continuous layer from the top of the status bar to the bottom of the
 * navigation bar with zero seams, zero banding, and full Light + Dark Mode harmony.
 */
@Composable
fun AppBackground(
    uiState: MusicUiState,
    isDarkTheme: Boolean,
    currentBgMetrics: BackgroundMetrics
) {
    val baseBackground = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

    // Always paint a 100% opaque base canvas matching the active Dark/Light theme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBackground)
    )

    if (uiState.isKariModeEnabled && uiState.backgroundMode != "CUSTOM") {
        // Silky multi-stop vertical + radial ambient glow over the theme's base canvas (zero banding!)
        val glowSource = if (uiState.currentSong != null && uiState.kariDominantColor != null) {
            Color(uiState.kariDominantColor)
        } else {
            primaryColor
        }
        val intensityScale = (uiState.kariOpacity / 0.60f).coerceIn(0.45f, 1.35f) *
            if (isDarkTheme) 1.0f else 0.72f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Soft radial crown glow behind top header & tabs
                    drawRect(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.00f to glowSource.copy(alpha = (0.14f * intensityScale).coerceIn(0f, 0.24f)),
                                0.35f to glowSource.copy(alpha = (0.06f * intensityScale).coerceIn(0f, 0.14f)),
                                0.72f to glowSource.copy(alpha = (0.015f * intensityScale).coerceIn(0f, 0.05f)),
                                1.00f to Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, 0f),
                            radius = size.width * 1.15f
                        )
                    )
                    // Silky continuous vertical fade that melts invisibly into the base canvas
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to glowSource.copy(alpha = (0.14f * intensityScale).coerceIn(0f, 0.22f)),
                                0.18f to glowSource.copy(alpha = (0.07f * intensityScale).coerceIn(0f, 0.12f)),
                                0.40f to glowSource.copy(alpha = (0.02f * intensityScale).coerceIn(0f, 0.05f)),
                                0.62f to Color.Transparent,
                                1.00f to Color.Transparent
                            )
                        )
                    )
                }
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
                            .crossfade(600)
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
                    if (currentBgMetrics.recommendedSurfaceScrimAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    (if (currentBgMetrics.isDark) Color.Black else Color.White)
                                        .copy(alpha = currentBgMetrics.recommendedSurfaceScrimAlpha * 0.55f)
                                )
                        )
                    }
                }
            }
            "GRADIENT" -> {
                val gradientColors = BackgroundPresets.getGradientColors(
                    presetIndex = uiState.gradientPresetIndex,
                    dominantColor = uiState.kariDominantColor,
                    vibrantColor = uiState.kariVibrantColor
                )
                val leadColor = gradientColors.firstOrNull() ?: primaryColor
                val midColor = gradientColors.getOrElse(1) { leadColor }
                val scale = uiState.gradientIntensity.coerceIn(0.2f, 1.0f) *
                    if (isDarkTheme) 1.0f else 0.70f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.00f to leadColor.copy(alpha = (0.20f * scale).coerceIn(0f, 0.28f)),
                                        0.20f to midColor.copy(alpha = (0.10f * scale).coerceIn(0f, 0.16f)),
                                        0.44f to leadColor.copy(alpha = (0.03f * scale).coerceIn(0f, 0.06f)),
                                        0.66f to Color.Transparent,
                                        1.00f to Color.Transparent
                                    )
                                )
                            )
                        }
                )
            }
            "SOLID" -> {
                if (isDarkTheme && uiState.solidBgOpacity > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(uiState.solidBgColor).copy(alpha = uiState.solidBgOpacity))
                    )
                }
            }
            // "OFF" / "DEFAULT" — pure clean semantic MaterialTheme.colorScheme.background
        }
    }
}
