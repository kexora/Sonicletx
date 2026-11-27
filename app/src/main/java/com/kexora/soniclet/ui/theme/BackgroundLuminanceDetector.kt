package com.kexora.soniclet.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent metrics representing background color, luminance, and contrast characteristics.
 */
@Immutable
data class BackgroundMetrics(
    val overallLuminance: Float,
    val topLuminance: Float,
    val bottomLuminance: Float,
    val isDark: Boolean,
    val isTopDark: Boolean,
    val isBottomDark: Boolean,
    val hasHighVariance: Boolean,
    val dominantColor: Color,
    val recommendedTextColor: Color,
    val recommendedTextSecondaryColor: Color,
    val recommendedTextShadow: Shadow?,
    val recommendedSurfaceScrimAlpha: Float,
    val recommendedSurfaceColor: Color = if (isDark) Color(0xFF141416) else Color(0xFFFFFFFF),
    val recommendedSurfaceVariantColor: Color = if (isDark) Color(0xFF202026) else Color(0xFFF1F5F9),
    val recommendedBorderColor: Color = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFCBD5E1)
) {
    val isLight: Boolean get() = !isDark
    val recommendedIconColor: Color get() = recommendedTextColor
    val recommendedIconSecondaryColor: Color get() = recommendedTextSecondaryColor

    companion object {
        val DEFAULT_DARK = BackgroundMetrics(
            overallLuminance = 0.04f,
            topLuminance = 0.04f,
            bottomLuminance = 0.04f,
            isDark = true,
            isTopDark = true,
            isBottomDark = true,
            hasHighVariance = false,
            dominantColor = DarkBackground,
            recommendedTextColor = DarkOnSurface,
            recommendedTextSecondaryColor = DarkOnSurfaceVariant,
            recommendedTextShadow = null,
            recommendedSurfaceScrimAlpha = 0f,
            recommendedSurfaceColor = DarkSurface,
            recommendedSurfaceVariantColor = DarkSurfaceContainerHigh,
            recommendedBorderColor = DarkOutlineVariant
        )

        val DEFAULT_LIGHT = BackgroundMetrics(
            overallLuminance = 0.94f,
            topLuminance = 0.94f,
            bottomLuminance = 0.94f,
            isDark = false,
            isTopDark = false,
            isBottomDark = false,
            hasHighVariance = false,
            dominantColor = LightBackground,
            recommendedTextColor = LightOnSurface,
            recommendedTextSecondaryColor = LightOnSurfaceVariant,
            recommendedTextShadow = null,
            recommendedSurfaceScrimAlpha = 0f,
            recommendedSurfaceColor = LightSurface,
            recommendedSurfaceVariantColor = LightSurfaceContainerHigh,
            recommendedBorderColor = LightOutlineVariant
        )
    }
}

val LocalBackgroundMetrics = staticCompositionLocalOf { BackgroundMetrics.DEFAULT_DARK }

/**
 * Advanced Background Color & Luminance Detection Engine.
 * Analyzes Custom Wallpapers, Gradients, Kari Mode, and Solid Colors in real-time,
 * guaranteeing 100% legibility of fonts, icons, and surfaces in dark & light modes.
 */
object BackgroundLuminanceDetector {

    // Fast memory cache to prevent re-decoding wallpapers repeatedly
    private val analysisCache = ConcurrentHashMap<String, BackgroundMetrics>()

    /**
     * Computes the perceived luminance of a Color using standard ITU-R BT.709.
     */
    fun getLuminance(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue
        return (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
    }

    /**
     * Computes luminance from an ARGB packed Int.
     */
    private fun getPixelLuminance(pixel: Int): Float {
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        return (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
    }

    /**
     * Builds complete BackgroundMetrics from computed luminance values.
     */
    fun createMetrics(
        rawOverallLum: Float,
        rawTopLum: Float,
        rawBottomLum: Float,
        dominantColor: Color = Color.Black,
        dimFactor: Float = 0f,
        hasVariance: Boolean = false,
        isWallpaperOrGradient: Boolean = false
    ): BackgroundMetrics {
        // Effective luminance after compositing the dimming overlay
        val effectiveOverall = (rawOverallLum * (1f - dimFactor)).coerceIn(0f, 1f)
        val effectiveTop = (rawTopLum * (1f - dimFactor)).coerceIn(0f, 1f)
        val effectiveBottom = (rawBottomLum * (1f - dimFactor)).coerceIn(0f, 1f)

        // Only switch to dark system icons when composited luminance clearly exceeds 0.58f
        val isDark = effectiveOverall <= 0.58f
        val isTopDark = effectiveTop <= 0.58f
        val isBottomDark = effectiveBottom <= 0.58f

        // High contrast colors
        val textColor = if (isDark) DarkOnSurface else LightOnSurface
        val textSecondaryColor = if (isDark) DarkOnSurfaceVariant else LightOnSurfaceVariant

        // Text shadow for wallpapers and complex backgrounds to prevent washed-out text on bright or varied areas
        val textShadow = if (isWallpaperOrGradient) {
            if (isDark) {
                Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 6f
                )
            } else {
                Shadow(
                    color = Color.White.copy(alpha = 0.75f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 6f
                )
            }
        } else null

        // Recommended adaptive scrim overlay alpha when wallpaper is complex or bright
        val scrimAlpha = if (isWallpaperOrGradient) {
            when {
                hasVariance && dimFactor < 0.35f -> 0.32f
                !isDark && dimFactor < 0.30f -> 0.28f
                else -> 0.12f
            }
        } else 0f

        // Recommended surface & container colors that guarantee extreme contrast
        val surfaceColor = if (isDark) DarkSurface else LightSurface
        val surfaceVariant = if (isDark) DarkSurfaceContainerHigh else LightSurfaceContainerHigh
        val borderColor = if (isDark) DarkOutlineVariant else LightOutlineVariant

        return BackgroundMetrics(
            overallLuminance = effectiveOverall,
            topLuminance = effectiveTop,
            bottomLuminance = effectiveBottom,
            isDark = isDark,
            isTopDark = isTopDark,
            isBottomDark = isBottomDark,
            hasHighVariance = hasVariance,
            dominantColor = dominantColor,
            recommendedTextColor = textColor,
            recommendedTextSecondaryColor = textSecondaryColor,
            recommendedTextShadow = textShadow,
            recommendedSurfaceScrimAlpha = scrimAlpha,
            recommendedSurfaceColor = surfaceColor,
            recommendedSurfaceVariantColor = surfaceVariant,
            recommendedBorderColor = borderColor
        )
    }

    /**
     * Asynchronously decodes a tiny thumbnail of the wallpaper to calculate exact pixel luminance and variance.
     */
    suspend fun analyzeWallpaperUri(
        context: Context,
        uriString: String,
        dimFactor: Float
    ): BackgroundMetrics = withContext(Dispatchers.IO) {
        val cacheKey = "$uriString-$dimFactor"
        val cached = analysisCache[cacheKey]
        if (cached != null) return@withContext cached

        var input: InputStream? = null
        try {
            val uri = Uri.parse(uriString)
            input = context.contentResolver.openInputStream(uri) ?: return@withContext BackgroundMetrics.DEFAULT_DARK

            // Downsample to a fast, lightweight 48x48 bitmap for luminance profiling
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, boundsOptions)
            input.close()

            val sampleSize = max(1, max(boundsOptions.outWidth / 48, boundsOptions.outHeight / 48))
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            input = context.contentResolver.openInputStream(uri) ?: return@withContext BackgroundMetrics.DEFAULT_DARK
            val thumb = BitmapFactory.decodeStream(input, null, decodeOptions) ?: return@withContext BackgroundMetrics.DEFAULT_DARK

            val width = thumb.width
            val height = thumb.height
            val pixels = IntArray(width * height)
            thumb.getPixels(pixels, 0, width, 0, 0, width, height)
            thumb.recycle()

            var totalLum = 0.0
            var topLum = 0.0
            var bottomLum = 0.0
            var topCount = 0
            var bottomCount = 0

            var minLum = 1.0f
            var maxLum = 0.0f

            val topThreshold = (height * 0.28f).toInt()
            val bottomThreshold = (height * 0.72f).toInt()

            for (y in 0 until height) {
                val isTop = y < topThreshold
                val isBottom = y > bottomThreshold
                for (x in 0 until width) {
                    val p = pixels[y * width + x]
                    val lum = getPixelLuminance(p)

                    totalLum += lum
                    if (lum < minLum) minLum = lum
                    if (lum > maxLum) maxLum = lum

                    if (isTop) {
                        topLum += lum
                        topCount++
                    } else if (isBottom) {
                        bottomLum += lum
                        bottomCount++
                    }
                }
            }

            val avgOverall = (totalLum / pixels.size).toFloat()
            val avgTop = if (topCount > 0) (topLum / topCount).toFloat() else avgOverall
            val avgBottom = if (bottomCount > 0) (bottomLum / bottomCount).toFloat() else avgOverall
            val variance = (maxLum - minLum) > 0.42f

            val result = createMetrics(
                rawOverallLum = avgOverall,
                rawTopLum = avgTop,
                rawBottomLum = avgBottom,
                dominantColor = Color(pixels[pixels.size / 2]),
                dimFactor = dimFactor,
                hasVariance = variance,
                isWallpaperOrGradient = true
            )

            analysisCache[cacheKey] = result
            result
        } catch (e: Throwable) {
            BackgroundMetrics.DEFAULT_DARK
        } finally {
            try { input?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Analyzes a Gradient preset to determine its overall, top, and bottom luminance.
     */
    fun analyzeGradient(colors: List<Color>, intensity: Float): BackgroundMetrics {
        if (colors.isEmpty()) return BackgroundMetrics.DEFAULT_DARK

        val topColor = colors.first()
        val bottomColor = colors.last()

        val topLum = getLuminance(topColor) * intensity
        val bottomLum = getLuminance(bottomColor) * intensity

        var totalLum = 0f
        colors.forEach { totalLum += getLuminance(it) }
        val overallLum = (totalLum / colors.size) * intensity

        val variance = kotlin.math.abs(topLum - bottomLum) > 0.35f

        return createMetrics(
            rawOverallLum = overallLum,
            rawTopLum = topLum,
            rawBottomLum = bottomLum,
            dominantColor = topColor,
            dimFactor = 0f,
            hasVariance = variance,
            isWallpaperOrGradient = true
        )
    }

    /**
     * Analyzes a Solid background color and opacity.
     */
    fun analyzeSolid(color: Color, opacity: Float): BackgroundMetrics {
        val lum = getLuminance(color) * opacity
        return createMetrics(
            rawOverallLum = lum,
            rawTopLum = lum,
            rawBottomLum = lum,
            dominantColor = color,
            dimFactor = 0f,
            hasVariance = false,
            isWallpaperOrGradient = false
        )
    }
}
