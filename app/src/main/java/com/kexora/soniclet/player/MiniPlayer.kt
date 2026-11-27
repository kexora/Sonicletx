package com.kexora.soniclet.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.FadingMarqueeText
import com.kexora.soniclet.ui.components.player.*
import java.util.Locale
import kotlin.math.abs

/**
 * Super Clean, Ultra-Ergonomic Pill-Shaped MiniPlayer.
 *
 * Design Focus:
 * 1. 100% Pill-shaped container (RoundedCornerShape 100.dp / CircleShape).
 * 2. Clean & Focused: Album Art thumbnail, Title, Artist name, Song duration (elapsed / total), and Play/Pause button.
 * 3. Fluid Physics Gestures: Swipe left/right for Next/Previous track, swipe up/tap to expand to Full Player.
 * 4. Micro bottom progress bar integrated smoothly into the pill contour.
 */
@Composable
fun MiniPlayer(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    song: Song? = null,
    isPlaying: Boolean = false,
    queueSourceTitle: String = "",
    currentPositionProvider: () -> Long = { 0L },
    totalDuration: Long = 0L,
    onTogglePlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onExpandPlayer: () -> Unit = {},
    onOpenAudioFx: (() -> Unit)? = null,
    isAudioFxActive: Boolean = false,
    isPlayerExpanded: Boolean = false,
    isGesturesEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isHorizontalDragging by remember { mutableStateOf(false) }
    var isVerticalDragging by remember { mutableStateOf(false) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    val activePosition = currentPositionProvider()
    val rawProgress = if (totalDuration > 0L) {
        (activePosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Smooth animated progress
    val smoothProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "mini_player_progress"
    )

    val animatedTranslationY by animateFloatAsState(
        targetValue = if (isVerticalDragging) accumulatedDragY.coerceIn(-100f, 15f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "mini_player_slide_y"
    )
    val animatedTranslationX by animateFloatAsState(
        targetValue = if (isHorizontalDragging) accumulatedDragX.coerceIn(-60f, 60f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mini_player_slide_x"
    )

    val isDark = isSystemInDarkTheme()
    val shadowColor = if (isDark) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        Color.Black.copy(alpha = 0.12f)
    }

    AnimatedVisibility(
        visible = song != null && !isPlayerExpanded,
        enter = slideInVertically(
            initialOffsetY = { it * 2 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it * 2 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        ) + fadeOut(animationSpec = tween(180)),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    translationX = animatedTranslationX
                    translationY = animatedTranslationY
                }
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(100.dp),
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGesturesEnabled) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        onExpandPlayer()
                                    }
                                )
                            }
                        } else {
                            Modifier.clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onExpandPlayer()
                            }
                        }
                    )
                    .then(
                        if (isGesturesEnabled) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                        isHorizontalDragging = false
                                        isVerticalDragging = false
                                    },
                                    onDragEnd = {
                                        if (isVerticalDragging) {
                                            if (accumulatedDragY < -18f) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onExpandPlayer()
                                            }
                                        } else if (isHorizontalDragging) {
                                            if (accumulatedDragX < -30f) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onSkipNext()
                                            } else if (accumulatedDragX > 30f) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                onSkipPrevious()
                                            }
                                        }
                                        isHorizontalDragging = false
                                        isVerticalDragging = false
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                    },
                                    onDragCancel = {
                                        isHorizontalDragging = false
                                        isVerticalDragging = false
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDragX += dragAmount.x
                                        accumulatedDragY += dragAmount.y

                                        if (!isHorizontalDragging && !isVerticalDragging) {
                                            if (abs(accumulatedDragY) > abs(accumulatedDragX) && abs(accumulatedDragY) > 10f) {
                                                isVerticalDragging = true
                                            } else if (abs(accumulatedDragX) > abs(accumulatedDragY) && abs(accumulatedDragX) > 10f) {
                                                isHorizontalDragging = true
                                            }
                                        }
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Custom asymmetric shapes matching outer pill capsule contours
                    val artworkShape = RoundedCornerShape(
                        topStart = 21.dp,
                        bottomStart = 21.dp,
                        topEnd = 6.dp,
                        bottomEnd = 6.dp
                    )
                    val playPauseShape = RoundedCornerShape(
                        topStart = 6.dp,
                        bottomStart = 6.dp,
                        topEnd = 20.dp,
                        bottomEnd = 20.dp
                    )

                    // Main Content Row (Pill Layout)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Pill Album Art with Live Equalizer Badge (Outer left pill curve, inner flat side)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(artworkShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (song?.albumArtUri != null) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(song.albumArtUri)
                                        .size(120, 120)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Album Art",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = {
                                        FallbackAlbumArt(title = song.title)
                                    }
                                )
                            } else {
                                FallbackAlbumArt(title = song?.title ?: "S")
                            }

                            // Live playing equalizer indicator badge
                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MiniLiveEqualizerBars(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 2. Song Details: Title, Artist & Duration
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (queueSourceTitle.isNotBlank() && (queueSourceTitle.contains("🎙️") || queueSourceTitle.contains("🔒") || queueSourceTitle.contains("Recording") || queueSourceTitle.contains("Hidden"))) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text(
                                        text = queueSourceTitle,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            // Song Title
                            FadingMarqueeText(
                                text = song?.let { com.kexora.soniclet.ui.components.common.cleanSongTitle(it) } ?: "",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    letterSpacing = (-0.2).sp,
                                    lineHeight = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                delayMillis = 2000L,
                                enableFade = true,
                                edgeGradientWidth = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Artist name & Song Duration
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FadingMarqueeText(
                                    text = song?.artist ?: "Unknown Artist",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    delayMillis = 2000L,
                                    enableFade = true,
                                    edgeGradientWidth = 6.dp,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                // Duration indicator: mm:ss / mm:ss
                                Text(
                                    text = if (totalDuration > 0L) {
                                        "• ${formatTime(activePosition)} / ${formatTime(totalDuration)}"
                                    } else {
                                        "• ${formatTime(activePosition)}"
                                    },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                    maxLines = 1
                                )
                            }
                        }

                        // 3. Play / Pause Button (Inner flat side, outer right pill curve)
                        val playPauseInteraction = remember { MutableInteractionSource() }
                        val isPressed by playPauseInteraction.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.88f else 1.0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "play_pause_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                                .clip(playPauseShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(
                                    interactionSource = playPauseInteraction,
                                    indication = null
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onTogglePlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (scaleIn(animationSpec = tween(150)) + fadeIn()) togetherWith
                                            (scaleOut(animationSpec = tween(150)) + fadeOut())
                                },
                                label = "play_pause_icon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 4. Integrated Sleek Micro Progress Line along bottom curve
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    ) {
                        if (smoothProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(smoothProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                                MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated micro equalizer bars for the MiniPlayer album art badge.
 */
@Composable
private fun MiniLiveEqualizerBars(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mini_eq_anim")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "mini_bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "mini_bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "mini_bar3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(1.6.dp)
                .fillMaxHeight(bar1Height)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(1.6.dp)
                .fillMaxHeight(bar2Height)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(1.6.dp)
                .fillMaxHeight(bar3Height)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
