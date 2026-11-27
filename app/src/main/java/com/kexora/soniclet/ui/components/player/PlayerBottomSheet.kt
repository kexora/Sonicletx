package com.kexora.soniclet.ui.components.player

import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*


import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.RepeatMode
import com.kexora.soniclet.library.ShuffleMode
import kotlin.math.sin
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt

import androidx.compose.animation.animateColorAsState
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomSheet(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long,
    totalDuration: Long,
    isShuffleEnabled: Boolean,
    shuffleMode: ShuffleMode = ShuffleMode.OFF,
    repeatMode: RepeatMode,
    queueSourceTitle: String = "",
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf<Color?>(null) }
    var vibrantColor by remember { mutableStateOf<Color?>(null) }
    var mutedColor by remember { mutableStateOf<Color?>(null) }

    androidx.compose.runtime.LaunchedEffect(song.albumArtUri) {
        if (song.albumArtUri != null) {
            val request = coil.request.ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is coil.request.SuccessResult) {
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                        dominantColor = palette?.dominantSwatch?.rgb?.let { Color(it) }
                        vibrantColor = palette?.vibrantSwatch?.rgb?.let { Color(it) }
                        mutedColor = palette?.mutedSwatch?.rgb?.let { Color(it) }
                    }
                }
            }
        } else {
            dominantColor = null
            vibrantColor = null
            mutedColor = null
        }
    }

    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultSecondary = MaterialTheme.colorScheme.secondary
    val defaultBackground = MaterialTheme.colorScheme.background

    val animatedPrimary by animateColorAsState(
        targetValue = vibrantColor ?: defaultPrimary,
        label = "primary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = dominantColor ?: defaultSecondary,
        label = "secondary"
    )
    val animatedBackground by animateColorAsState(
        targetValue = mutedColor?.copy(alpha = 0.15f) ?: defaultBackground,
        label = "background"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = defaultBackground,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedPrimary.copy(alpha = 0.15f),
                            animatedBackground,
                            defaultBackground
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_player_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = queueSourceTitle.ifBlank { "PLAYING FROM LIBRARY" }.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = animatedPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Box(modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Album Art Container with Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = animatedPrimary,
                        spotColor = animatedSecondary
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .crossfade(500)
                            .build(),
                        contentDescription = "Full Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            FallbackAlbumArt(title = song.title)
                        }
                    )
                } else {
                    FallbackAlbumArt(title = song.title)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title, Artist, Album
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = animatedPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Visualizer Canvas
            AnimatedWaveformVisualizer(
                isPlaying = isPlaying,
                primaryColor = animatedPrimary,
                secondaryColor = animatedSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scrubbing Slider & Timers
            var isUserScrubbing by remember { mutableStateOf(false) }
            var scrubPosition by remember { mutableFloatStateOf(0f) }

            val sliderValue = if (isUserScrubbing) scrubPosition else currentPosition.toFloat()
            val maxSliderValue = totalDuration.coerceAtLeast(1L).toFloat()

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue.coerceIn(0f, maxSliderValue),
                    onValueChange = { newValue ->
                        isUserScrubbing = true
                        scrubPosition = newValue
                    },
                    onValueChangeFinished = {
                        isUserScrubbing = false
                        onSeekTo(scrubPosition.toLong())
                    },
                    valueRange = 0f..maxSliderValue,
                    colors = SliderDefaults.colors(
                        thumbColor = animatedPrimary,
                        activeTrackColor = animatedPrimary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playback_position_slider")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMillisToTime(if (isUserScrubbing) scrubPosition.toLong() else currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMillisToTime(totalDuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playback Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.testTag("shuffle_button")
                ) {
                    val isSmart = shuffleMode == ShuffleMode.SMART
                    Icon(
                        imageVector = if (isSmart) Icons.Filled.AutoAwesome else Icons.Default.Shuffle,
                        contentDescription = if (isSmart) "Smart Shuffle" else "Shuffle",
                        tint = if (isShuffleEnabled) animatedPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // Previous Track Button
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("previous_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Main Play / Pause Button
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    animatedPrimary,
                                    animatedSecondary
                                )
                            )
                        )
                        .testTag("main_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Next Track Button
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode Button
                IconButton(
                    onClick = onToggleRepeatMode,
                    modifier = Modifier.testTag("repeat_button")
                ) {
                    val icon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    val tint = if (repeatMode != RepeatMode.OFF) animatedPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat Mode",
                        tint = tint
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedWaveformVisualizer(
    isPlaying: Boolean,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        if (!isPlaying) {
            drawLine(
                color = primaryColor.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                strokeWidth = 3f
            )
            return@Canvas
        }

        val path = Path()
        val amplitude = height * 0.35f
        val frequency = 0.02f

        path.moveTo(0f, centerY)
        for (x in 0..width.toInt() step 5) {
            val y = centerY + amplitude * sin(x * frequency + phase)
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(primaryColor, secondaryColor, primaryColor)
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )
    }
}
