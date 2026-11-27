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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import com.kexora.soniclet.lyrics.TimeSyncedKaraokeView
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.PlayQueue
import com.kexora.soniclet.utils.ArtistParserUtil

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

/**
 * Partial-Height Slide-Up Up Next & Lyrics Sheet (occupies max 74% height, leaving ~26% of
 * the blurred Now Playing backdrop visible behind a dark dim scrim with zero text bleed-through).
 */
@Composable
fun PlayerQueueLyricsSheet(
    visible: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    currentPosition: Long,
    animatedDominant: Color,
    animatedVibrant: Color,
    onDismissSheet: () -> Unit,
    onDismissPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetSurfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    var sheetDragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedSheetOffsetY by animateFloatAsState(
        targetValue = sheetDragOffsetY,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "partial_queue_sheet_offset"
    )

    LaunchedEffect(visible) {
        if (visible) sheetDragOffsetY = 0f
    }

    BackHandler(enabled = visible) {
        onDismissSheet()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dark dim scrim covering top ~26% of player; tapping dismisses sheet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.60f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissSheet
                    )
            )

            // Partial-Height Frosted Opaque Sheet (74% max height)
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.92f,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.74f)
                    .align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(0, animatedSheetOffsetY.toInt().coerceAtLeast(0))
                        }
                        .testTag("partial_queue_lyrics_sheet"),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = sheetSurfaceColor,
                    contentColor = onSurface,
                    tonalElevation = 0.dp,
                    border = BorderStroke(0.8.dp, outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Drag-down header zone with pill handle + segmented switcher
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (sheetDragOffsetY > 90f) {
                                                onDismissSheet()
                                                sheetDragOffsetY = 0f
                                            } else {
                                                sheetDragOffsetY = 0f
                                            }
                                        },
                                        onDragCancel = { sheetDragOffsetY = 0f },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (dragAmount > 0 || sheetDragOffsetY > 0) {
                                                change.consume()
                                                sheetDragOffsetY = (sheetDragOffsetY + dragAmount).coerceAtLeast(0f)
                                            }
                                        }
                                    )
                                }
                                .padding(top = 10.dp, bottom = 8.dp, start = 12.dp, end = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(onSurfaceVariant.copy(alpha = 0.30f))
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        sheetDragOffsetY = 0f
                                        onDismissSheet()
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("partial_sheet_collapse_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Collapse Sheet",
                                        tint = onSurface,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Centered CircleShape Segmented Pill Switcher (UP NEXT | LYRICS)
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(0.6.dp, outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val tabs = listOf("UP NEXT", "LYRICS")
                                        tabs.forEachIndexed { index, title ->
                                            val isSelected = selectedTab == index
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else Color.Transparent
                                                    )
                                                    .clickable { onSelectTab(index) }
                                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.5.sp,
                                                        letterSpacing = 0.7.sp
                                                    ),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (selectedTab == 0) viewModel.toggleShuffle()
                                        else viewModel.loadLyricsForSong(song)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == 0) {
                                            Icons.Rounded.Shuffle
                                        } else {
                                            Icons.Rounded.Refresh
                                        },
                                        contentDescription = if (selectedTab == 0) "Shuffle Queue" else "Reload Lyrics",
                                        tint = onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.10f)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (selectedTab == 0) {
                                PlayQueue(
                                    activeQueue = uiState.activeQueue,
                                    queueSourceTitle = uiState.queueSourceTitle,
                                    currentSong = song,
                                    isPlaying = uiState.isPlaying,
                                    onPlaySong = { viewModel.playSong(it) },
                                    onTogglePlayPause = { viewModel.togglePlayPause() },
                                    onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                                    onMoveQueueItemToNext = { viewModel.moveQueueItemToNext(it) },
                                    onMoveQueueItemToBottom = { viewModel.moveQueueItemToBottom(it) },
                                    onReorderQueueItem = { from, to -> viewModel.reorderQueueItem(from, to) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onClearQueue = { viewModel.clearQueue() },
                                    onToggleShuffle = { viewModel.toggleShuffle() },
                                    favoriteSongIds = uiState.favoriteSongIds,
                                    actuallyPlayedSongIds = uiState.actuallyPlayedSongIds,
                                    playbackPositions = uiState.playbackPositions,
                                    onNavigateToArtist = { artistName ->
                                        val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                                        val matchingSongs = allS.filter { s ->
                                            ArtistParserUtil.splitArtists(s.artist, s.title)
                                                .any { it.equals(artistName, ignoreCase = true) }
                                        }
                                        viewModel.openCategory(artistName, "ARTIST", matchingSongs)
                                        onDismissPlayer()
                                    },
                                    isEmbeddedInSheet = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                LaunchedEffect(song.id) {
                                    if (uiState.currentLyrics.isEmpty()) {
                                        viewModel.loadLyricsForSong(song)
                                    }
                                }
                                TimeSyncedKaraokeView(
                                    lyrics = uiState.currentLyrics,
                                    activeLyricIndex = uiState.activeLyricIndex,
                                    currentPosition = currentPosition,
                                    syncOffsetMs = uiState.lyricsSyncOffsetMs,
                                    isKaraokeModeEnabled = uiState.isKaraokeModeEnabled,
                                    lyricsSourceType = uiState.lyricsSourceType,
                                    isLyricsLoading = uiState.isLyricsLoading,
                                    isKariModeEnabled = uiState.isKariModeEnabled,
                                    dominantColor = animatedDominant,
                                    vibrantColor = animatedVibrant,
                                    onSeekTo = { pos -> viewModel.seekTo(pos) },
                                    onAdjustOffset = { delta -> viewModel.adjustLyricsOffset(delta) },
                                    onResetOffset = { viewModel.resetLyricsOffset() },
                                    onToggleKaraokeMode = { viewModel.toggleKaraokeMode() },
                                    onImportLyrics = { uri -> viewModel.importLyricsFromUri(song, uri) },
                                    onClearCustomLyrics = { viewModel.clearImportedLyrics(song) },
                                    isPlaying = uiState.isPlaying,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .navigationBarsPadding()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

