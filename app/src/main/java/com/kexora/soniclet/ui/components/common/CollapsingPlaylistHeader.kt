package com.kexora.soniclet.ui.components.common

import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.library.PLAYLIST_GRADIENT_PRESETS

@Composable
fun CollapsingPlaylistHeader(
    playlist: Playlist?,
    isSmartPlaylist: Boolean,
    title: String,
    smartIcon: ImageVector?,
    songs: List<Song>,
    totalDurationFormatted: String,
    scrollOffset: Float,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    onEditName: () -> Unit,
    onChangeCoverStyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 200.dp.toPx() }
    val fraction = (scrollOffset / maxOffsetPx).coerceIn(0f, 1f)
    
    val gradientIndex = playlist?.colorGradientIndex ?: 0
    val gradientColors = PLAYLIST_GRADIENT_PRESETS.getOrElse(gradientIndex) { PLAYLIST_GRADIENT_PRESETS[0] }
    
    // Header background fading in
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val bgColor = surfaceColor.copy(alpha = fraction)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = bgColor,
        shadowElevation = if (fraction > 0.8f) 4.dp else 0.dp,
        tonalElevation = if (fraction > 0.8f) 4.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp - (270.dp * fraction))
        ) {
            // Morphing Content
            // 1. Image Cover
            val imageSize = 160.dp - (124.dp * fraction) // 160 to 36
            val imageCorner = 24.dp - (16.dp * fraction) // 24 to 8
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = 16.dp - (8.dp * fraction), // 16 to 8
                        start = 16.dp * fraction // 0 to 16
                    )
                    .graphicsLayer {
                        val transX = -((size.width / 2f) * fraction) + (with(density) { 16.dp.toPx() } * fraction)
                        translationX = if (fraction > 0f) -size.width/2f else 0f // We handle alignment with Bias or just manual constraints.
                    }
                    // Actually, BoxWithConstraints or simple layout is better
            )
        }
    }
}
