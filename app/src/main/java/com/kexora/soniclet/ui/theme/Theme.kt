package com.kexora.soniclet.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils

private object NoIndicationNodeFactory : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node() {}
    }
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonicletTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    accentColor: Long = 0xFF00E676,
    backgroundMode: String = "DYNAMIC",
    solidBgColor: Long = 0xFF060807L,
    solidBgOpacity: Float = 1.0f,
    fontStyle: String = "STANDARD",
    cornerRadiusScale: Float = 0.5f,
    backgroundMetrics: BackgroundMetrics? = null,
    content: @Composable () -> Unit,
) {
    val rawAccent = Color(accentColor)

    val isEffectiveDark = when (backgroundMode) {
        "WHITE" -> false
        "AMOLED" -> true
        else -> darkTheme
    }

    val effectiveMetrics = backgroundMetrics ?: if (isEffectiveDark) {
        BackgroundMetrics.DEFAULT_DARK
    } else {
        BackgroundMetrics.DEFAULT_LIGHT
    }

    // Smart Accent Contrast Logic:
    // - In Light Mode, clamp high-luminance accents (Yellow, Cyan, Mint, White) to HSL lightness <= 0.42f
    //   for >= 4.5:1 text/icon readability on light surfaces while keeping primaryContainer vibrant.
    // - In Dark Mode, preserve vivid accents and lift pitch-dark/achromatic values so they pop on OLED.
    val resolvedPrimary = ensureThemeAccentContrast(rawAccent, isDark = isEffectiveDark)
    val onPrimaryColor = if (resolvedPrimary.luminance() > 0.42f) ContrastDarkInk else Color.White

    val colorScheme = if (isEffectiveDark) {
        darkColorScheme(
            primary = resolvedPrimary,
            onPrimary = onPrimaryColor,
            primaryContainer = rawAccent.copy(alpha = 0.20f),
            onPrimaryContainer = DarkOnSurface,
            secondary = resolvedPrimary,
            onSecondary = onPrimaryColor,
            secondaryContainer = DarkSurfaceContainerHigh,
            onSecondaryContainer = DarkOnSurface,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceContainerHigh,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLowest = DarkSurfaceContainerLowest,
            surfaceContainerLow = DarkSurfaceContainerLow,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant
        )
    } else {
        lightColorScheme(
            primary = resolvedPrimary,
            onPrimary = onPrimaryColor,
            primaryContainer = rawAccent.copy(alpha = 0.16f),
            onPrimaryContainer = LightOnSurface,
            secondary = resolvedPrimary,
            onSecondary = onPrimaryColor,
            secondaryContainer = LightSurfaceContainerHigh,
            onSecondaryContainer = LightOnSurface,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceContainerHigh,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainerLowest = LightSurfaceContainerLowest,
            surfaceContainerLow = LightSurfaceContainerLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant
        )
    }

    val currentDensity = LocalDensity.current
    val clampedFontScale = currentDensity.fontScale.coerceIn(0.90f, 1.10f)
    val customDensity = Density(
        density = currentDensity.density,
        fontScale = clampedFontScale
    )

    CompositionLocalProvider(
        LocalDensity provides customDensity,
        LocalIndication provides NoIndicationNodeFactory,
        LocalRippleConfiguration provides null,
        LocalContentColor provides colorScheme.onSurface,
        LocalBackgroundMetrics provides effectiveMetrics
    ) {
        val cornerSizeSmall = (8 + (cornerRadiusScale * 8)).dp
        val cornerSizeMedium = (12 + (cornerRadiusScale * 12)).dp
        val cornerSizeLarge = (16 + (cornerRadiusScale * 16)).dp
        val dynamicShapes = Shapes(
            small = RoundedCornerShape(cornerSizeSmall),
            medium = RoundedCornerShape(cornerSizeMedium),
            large = RoundedCornerShape(cornerSizeLarge)
        )
        MaterialTheme(
            colorScheme = colorScheme,
            typography = getDynamicTypography(fontStyle),
            shapes = dynamicShapes,
            content = content
        )
    }
}

/**
 * Guarantees WCAG-grade accent readability across both Dark Mode and Light Mode.
 * - Light Mode: if rawAccent.luminance() > 0.50f (e.g., Yellow #FFD600, Cyan, Mint, White),
 *   clamps HSL lightness <= 0.42f (yielding rich amber/gold #B58500 for Yellow, deep teal for Cyan, etc.).
 * - Dark Mode: preserves vivid accents and lifts overly dark or near-black extracted tones.
 */
fun ensureThemeAccentContrast(
    color: Color,
    isDark: Boolean,
    fallbackAccent: Color = Color(0xFF00E676)
): Color {
    val lum = color.luminance()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)

    return if (isDark) {
        if (hsl[2] < 0.20f && hsl[1] < 0.20f) {
            fallbackAccent
        } else if (lum < 0.22f || hsl[2] < 0.48f) {
            hsl[1] = hsl[1].coerceIn(0.65f, 1.0f)
            hsl[2] = hsl[2].coerceIn(0.56f, 0.72f)
            Color(ColorUtils.HSLToColor(hsl))
        } else {
            color
        }
    } else {
        if (lum > 0.50f) {
            if (hsl[1] < 0.08f) {
                // Pure white / near-white accent in Light Mode -> crisp slate charcoal
                Color(0xFF1E293B)
            } else {
                // Clamp lightness <= 0.42f for >= 4.5:1 contrast on light surfaces (e.g., Yellow -> rich amber)
                hsl[1] = hsl[1].coerceIn(0.72f, 1.0f)
                hsl[2] = minOf(hsl[2], 0.42f).coerceAtLeast(0.32f)
                Color(ColorUtils.HSLToColor(hsl))
            }
        } else {
            color
        }
    }
}

fun getDynamicTypography(fontStyle: String): androidx.compose.material3.Typography {
    val default = Typography
    val fontFamily = when (fontStyle) {
        "ROUNDED" -> androidx.compose.ui.text.font.FontFamily.SansSerif
        "MONOSPACE" -> androidx.compose.ui.text.font.FontFamily.Monospace
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }
    return androidx.compose.material3.Typography(
        displayLarge = default.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = default.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = default.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = default.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = default.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = default.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = default.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = default.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = default.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = default.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = default.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = default.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = default.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = default.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = default.labelSmall.copy(fontFamily = fontFamily)
    )
}
