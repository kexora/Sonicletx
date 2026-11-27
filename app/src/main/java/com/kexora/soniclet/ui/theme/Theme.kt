package com.kexora.soniclet.ui.theme

import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
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
    backgroundMode: String = "AMOLED",
    solidBgColor: Long = 0xFF000000L,
    solidBgOpacity: Float = 1.0f,
    fontStyle: String = "STANDARD",
    cornerRadiusScale: Float = 0.5f,
    backgroundMetrics: BackgroundMetrics? = null,
    content: @Composable () -> Unit,
) {
    val rawAccent = Color(accentColor)
    // Determine effective canvas luminance:
    val isSolidActive = (backgroundMode == "SOLID" || backgroundMode == "WHITE" || backgroundMode == "AMOLED") && solidBgOpacity >= 0.2f
    val solidColor = if (backgroundMode == "WHITE") Color.White else if (backgroundMode == "AMOLED") Color.Black else Color(solidBgColor)
    val r = solidColor.red
    val g = solidColor.green
    val b = solidColor.blue
    val solidLum = 0.2126f * r + 0.7152f * g + 0.0722f * b

    // Advanced dynamic background detection:
    // If backgroundMetrics is provided (e.g. from Custom Wallpaper or Gradient analysis),
    // use its real detected luminance. Otherwise, fallback to calculated solid / theme luminance.
    val effectiveMetrics = backgroundMetrics ?: when {
        backgroundMode == "WHITE" -> BackgroundMetrics.DEFAULT_LIGHT
        backgroundMode == "AMOLED" -> BackgroundMetrics.DEFAULT_DARK
        isSolidActive -> BackgroundLuminanceDetector.analyzeSolid(solidColor, solidBgOpacity)
        else -> if (darkTheme) BackgroundMetrics.DEFAULT_DARK else BackgroundMetrics.DEFAULT_LIGHT
    }

    val isEffectiveDark = when {
        backgroundMode == "WHITE" -> false
        backgroundMode == "AMOLED" -> true
        // Always respect user's explicit theme choice (Light vs Dark)!
        else -> darkTheme
    }

    val accent = ensureThemeAccentContrast(rawAccent, isDark = isEffectiveDark)
    val onPrimaryColor = if (BackgroundLuminanceDetector.getLuminance(accent) > 0.45f) Color(0xFF0F172A) else Color.White

    val isWallpaperOrGradient = backgroundMode == "CUSTOM" || backgroundMode == "GRADIENT"

    val bgColor = when {
        isWallpaperOrGradient -> Color.Transparent // Allow background image/gradient to show through cleanly!
        !isEffectiveDark -> Color(0xFFF1F5F9) // Clean off-white canvas for standard Light Mode
        else -> Color.Black
    }

    val surfaceColor = if (isEffectiveDark) {
        if (isWallpaperOrGradient) Color(0xCC141418) else effectiveMetrics.recommendedSurfaceColor
    } else {
        if (isWallpaperOrGradient) Color(0xF2F8FAFC) else Color(0xFFFFFFFF)
    }

    val surfaceVariant = if (isEffectiveDark) {
        if (isWallpaperOrGradient) Color(0xDF1E1E28) else effectiveMetrics.recommendedSurfaceVariantColor
    } else {
        if (isWallpaperOrGradient) Color(0xE8E2E8F0) else Color(0xFFE2E8F0)
    }

    // High contrast bold text colors
    val textColor = if (isEffectiveDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isEffectiveDark) Color(0xFF94A3B8) else Color(0xFF334155)
    val borderColor = if (isEffectiveDark) Color.White.copy(alpha = 0.12f) else Color(0xFFCBD5E1)

    val colorScheme = if (isEffectiveDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onPrimaryColor,
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = onPrimaryColor,
            background = bgColor,
            onBackground = textColor,
            surface = surfaceColor,
            onSurface = textColor,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            outline = borderColor,
            outlineVariant = surfaceVariant
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onPrimaryColor,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = Color(0xFF0F172A),
            secondary = accent,
            onSecondary = onPrimaryColor,
            background = bgColor,
            onBackground = textColor,
            surface = surfaceColor,
            onSurface = textColor,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = textSecondary,
            outline = borderColor,
            outlineVariant = Color(0xFFCBD5E1)
        )
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val clampedFontScale = currentDensity.fontScale.coerceIn(0.90f, 1.10f)
    val customDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = clampedFontScale
    )

    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides customDensity,
        LocalIndication provides NoIndicationNodeFactory,
        LocalRippleConfiguration provides null,
        androidx.compose.material3.LocalContentColor provides textColor,
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
fun ensureThemeAccentContrast(color: Color, isDark: Boolean, fallbackAccent: Color = Color(0xFF00E676)): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv)
    return if (isDark) {
        // If extracted color is pitch black or achromatic dark grey, fallback to vibrant accent
        if (hsv[2] < 0.25f && hsv[1] < 0.25f) {
            fallbackAccent
        } else if (lum < 0.35f || hsv[2] < 0.70f) {
            // Boost saturation and brightness for AMOLED / dark background punch and clarity
            hsv[1] = hsv[1].coerceIn(0.75f, 1.0f)
            hsv[2] = 0.98f
            Color(android.graphics.Color.HSVToColor(hsv))
        } else {
            color
        }
    } else {
        // Light mode: ensure high contrast readability against light/white surfaces
        // Saturated and deep enough without looking dull or low contrast
        if (lum > 0.40f || hsv[2] > 0.75f) {
            hsv[1] = hsv[1].coerceIn(0.85f, 1.0f)
            hsv[2] = 0.62f
            Color(android.graphics.Color.HSVToColor(hsv))
        } else {
            color
        }
    }
}
fun getDynamicTypography(fontStyle: String): androidx.compose.material3.Typography {
    // Standard typography
    val default = Typography
    // In a real app we would load specific fonts here (e.g. Rounded, Monospace).
    // For simplicity, we just use the built-in system fonts.
    val fontFamily = when (fontStyle) {
        "ROUNDED" -> androidx.compose.ui.text.font.FontFamily.SansSerif // Assume sans-serif is rounded enough or fallback
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
