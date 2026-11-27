package com.kexora.soniclet.ui.theme

import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*


import androidx.compose.ui.graphics.Color

data class GradientPreset(
    val id: Int,
    val name: String,
    val description: String,
    val colors: List<Color>,
    val isDynamic: Boolean = false
)

data class SolidColorPreset(
    val colorValue: Long,
    val name: String
)

object BackgroundPresets {
    val DARK_SOLID_PRESETS = listOf(
        SolidColorPreset(0xFF000000L, "Pure AMOLED"),
        SolidColorPreset(0xFF121212L, "Deep Obsidian"),
        SolidColorPreset(0xFF1A1A24L, "Midnight Slate"),
        SolidColorPreset(0xFF0A1128L, "Deep Navy"),
        SolidColorPreset(0xFF0B1D13L, "Forest Deep"),
        SolidColorPreset(0xFF1A0923L, "Dark Plum"),
        SolidColorPreset(0xFF220911L, "Wine Crimson"),
        SolidColorPreset(0xFF1F1608L, "Espresso")
    )

    val LIGHT_SOLID_PRESETS = listOf(
        SolidColorPreset(0xFFFFFFFFL, "Pure White"),
        SolidColorPreset(0xFFF8F9FAL, "Ghost White"),
        SolidColorPreset(0xFFF5F5F0L, "Warm Ivory"),
        SolidColorPreset(0xFFF0F4F8L, "Ice Blue"),
        SolidColorPreset(0xFFF0FDF4L, "Mint Pastel"),
        SolidColorPreset(0xFFFAF5FFL, "Lavender"),
        SolidColorPreset(0xFFFFF7EDL, "Warm Peach"),
        SolidColorPreset(0xFFE2E8F0L, "Cool Silver")
    )

    val SOLID_PRESETS = DARK_SOLID_PRESETS + LIGHT_SOLID_PRESETS

    val GRADIENT_PRESETS = listOf(
        GradientPreset(
            id = 0,
            name = "Midnight Neon",
            description = "Deep slate into cyan teal",
            colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
        ),
        GradientPreset(
            id = 1,
            name = "Purple Aurora",
            description = "Cosmic violet into deep obsidian",
            colors = listOf(Color(0xFF3B0764), Color(0xFF1E1B4B), Color(0xFF090A0F))
        ),
        GradientPreset(
            id = 2,
            name = "Cyberpunk Sunset",
            description = "Synthwave purple, pink & amber glow",
            colors = listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B))
        ),
        GradientPreset(
            id = 3,
            name = "Emerald Abyss",
            description = "Rich jade into dark jungle",
            colors = listOf(Color(0xFF052B1E), Color(0xFF0F4C3A), Color(0xFF06140E))
        ),
        GradientPreset(
            id = 4,
            name = "Deep Oceanic",
            description = "Dark abyss into electric sapphire",
            colors = listOf(Color(0xFF000428), Color(0xFF004E92), Color(0xFF000814))
        ),
        GradientPreset(
            id = 5,
            name = "Cosmic Velvet",
            description = "Smoky purple into platinum dust",
            colors = listOf(Color(0xFF1F1C2C), Color(0xFF433F58), Color(0xFF0D0C13))
        ),
        GradientPreset(
            id = 6,
            name = "Dark Amber",
            description = "Warm espresso into molten copper",
            colors = listOf(Color(0xFF2C1600), Color(0xFF4A2500), Color(0xFF120800))
        ),
        GradientPreset(
            id = 7,
            name = "Dynamic Album Art",
            description = "Synced live with now playing song",
            colors = listOf(Color(0xFF1E1E1E), Color(0xFF121212)),
            isDynamic = true
        )
    )

    fun getGradientColors(presetIndex: Int, dominantColor: Long?, vibrantColor: Long?, customGradientColors: List<Long> = emptyList()): List<Color> {
        if (presetIndex == 8 && customGradientColors.isNotEmpty()) {
            return customGradientColors.map { Color(it) }
        }
        if (presetIndex == 7 && dominantColor != null) {
            val dom = Color(dominantColor)
            val vib = vibrantColor?.let { Color(it) } ?: dom
            return listOf(
                dom.copy(alpha = 0.85f),
                vib.copy(alpha = 0.65f),
                Color(0xFF0A0A0E)
            )
        }
        val safeIndex = presetIndex.coerceIn(0, GRADIENT_PRESETS.size - 1)
        val preset = GRADIENT_PRESETS[safeIndex]
        if (preset.isDynamic && dominantColor != null) {
            val dom = Color(dominantColor)
            val vib = vibrantColor?.let { Color(it) } ?: dom
            return listOf(
                dom.copy(alpha = 0.85f),
                vib.copy(alpha = 0.65f),
                Color(0xFF0A0A0E)
            )
        }
        return preset.colors
    }
}
