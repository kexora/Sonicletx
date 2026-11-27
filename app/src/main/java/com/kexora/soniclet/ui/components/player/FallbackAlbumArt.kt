package com.kexora.soniclet.ui.components.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.R
import kotlin.math.abs

/**
 * 36 vibrant, rich modern music cover palettes with diverse hues,
 * multi-stop gradients, and deep dynamic tones.
 */
private val ALBUM_PALETTES = listOf(
    // Fiery & Warm Ambers
    listOf(Color(0xFFFF3366), Color(0xFFFF6F00), Color(0xFFFFB300)),
    listOf(Color(0xFFFF5722), Color(0xFFFF9800), Color(0xFFFFD54F)),
    listOf(Color(0xFFD81B60), Color(0xFFE91E63), Color(0xFFFF6090)),
    listOf(Color(0xFFE65100), Color(0xFFF57C00), Color(0xFFFFB74D)),
    listOf(Color(0xFFC2185B), Color(0xFFFF1744), Color(0xFFFF8A80)),
    listOf(Color(0xFFB71C1C), Color(0xFFE53935), Color(0xFFFF7043)),

    // Royals, Purples & Violets
    listOf(Color(0xFF4A148C), Color(0xFF7B1FA2), Color(0xFFBA68C8)),
    listOf(Color(0xFF6200EA), Color(0xFF7C4DFF), Color(0xFFB388FF)),
    listOf(Color(0xFF311B92), Color(0xFF512DA8), Color(0xFF7E57C2)),
    listOf(Color(0xFF8E24AA), Color(0xFFAB47BC), Color(0xFFE1BEE7)),
    listOf(Color(0xFF3F51B5), Color(0xFF5C6BC0), Color(0xFF9FA8DA)),
    listOf(Color(0xFF4527A0), Color(0xFF673AB7), Color(0xFF9575CD)),

    // Ocean, Neon Cyan & Electric Blues
    listOf(Color(0xFF0D47A1), Color(0xFF1976D2), Color(0xFF42A5F5)),
    listOf(Color(0xFF01579B), Color(0xFF0288D1), Color(0xFF4FC3F7)),
    listOf(Color(0xFF006064), Color(0xFF0097A7), Color(0xFF26C6DA)),
    listOf(Color(0xFF004D40), Color(0xFF00897B), Color(0xFF4DB6AC)),
    listOf(Color(0xFF00695C), Color(0xFF00B4D8), Color(0xFF90E0EF)),
    listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF5C6BC0)),

    // Emeralds, Jades & Natural Greens
    listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF66BB6A)),
    listOf(Color(0xFF004D40), Color(0xFF2E7D32), Color(0xFF81C784)),
    listOf(Color(0xFF00796B), Color(0xFF388E3C), Color(0xFFA5D6A7)),
    listOf(Color(0xFF33691E), Color(0xFF558B2F), Color(0xFF9CCC65)),
    listOf(Color(0xFF05668D), Color(0xFF02C39A), Color(0xFF00A896)),
    listOf(Color(0xFF2B580C), Color(0xFF639A67), Color(0xFFD8EBB5)),

    // Sunset, Cosmic & Cyberpunk Gradients
    listOf(Color(0xFF4A0E4E), Color(0xFF880E4F), Color(0xFFFF4081)),
    listOf(Color(0xFF240046), Color(0xFF5A189A), Color(0xFF9D4EDD)),
    listOf(Color(0xFF03071E), Color(0xFF370617), Color(0xFF9D0208)),
    listOf(Color(0xFF10002B), Color(0xFF3C096C), Color(0xFF7B2CBF)),
    listOf(Color(0xFF14213D), Color(0xFFFCA311), Color(0xFFE5E5E5)),
    listOf(Color(0xFF0B090A), Color(0xFF161A1D), Color(0xFFE5383B)),

    // Pastel & Modern Neo-Pop
    listOf(Color(0xFFFC5C7D), Color(0xFF6A82FB), Color(0xFF4A00E0)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D), Color(0xFF80FFA8)),
    listOf(Color(0xFFFF416C), Color(0xFFFF4B2B), Color(0xFFFF8A65)),
    listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)),
    listOf(Color(0xFF00B09B), Color(0xFF96C93D), Color(0xFFDEF5B8)),
    listOf(Color(0xFF5433FF), Color(0xFF20BDFF), Color(0xFF5BFFB4))
)

/**
 * Premium Modern Fallback Album Art Generator
 *
 * Uses the official Soniclet app logo as the central badge on top of
 * high-res vinyl acoustic grooves with a deterministically hashed 3-stop
 * studio gradient, unique for every song/album/recording.
 */
@Composable
fun FallbackAlbumArt(
    title: String = "",
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    iconScale: Float = 0.52f
) {
    val cleanTitle = remember(title) { title.trim() }

    // Deterministically pick a vibrant multi-stop studio palette based on song title hash
    val palette = remember(cleanTitle) {
        if (cleanTitle.isBlank()) {
            ALBUM_PALETTES[0]
        } else {
            val hash = abs(cleanTitle.hashCode())
            ALBUM_PALETTES[hash % ALBUM_PALETTES.size]
        }
    }

    // Dynamic gradient angle variation based on hash
    val bgGradient = remember(palette, cleanTitle) {
        val hash = abs(cleanTitle.hashCode())
        val angleVariant = hash % 4
        when (angleVariant) {
            0 -> Brush.linearGradient(colors = palette)
            1 -> Brush.radialGradient(colors = listOf(palette[1], palette[0], palette[2]))
            2 -> Brush.verticalGradient(colors = palette)
            else -> Brush.horizontalGradient(colors = palette)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        // Authentic Vinyl Record Grooves Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            // Outer groove
            drawCircle(
                color = Color.White.copy(alpha = 0.20f),
                radius = minDim * 0.44f,
                style = Stroke(width = 1.2.dp.toPx())
            )
            // Mid groove
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = minDim * 0.34f,
                style = Stroke(width = 0.9.dp.toPx())
            )
            // Inner groove
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = minDim * 0.24f,
                style = Stroke(width = 0.9.dp.toPx())
            )
        }

        // Center vinyl disc core badge containing official App Logo (high-contrast vinyl label)
        Box(
            modifier = Modifier
                .fillMaxSize(0.54f)
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_soniclet_app_logo),
                contentDescription = "Soniclet Cover Art",
                tint = Color.White,
                modifier = Modifier
                    .fillMaxSize(0.64f)
                    .padding(2.dp)
            )
        }
    }
}
