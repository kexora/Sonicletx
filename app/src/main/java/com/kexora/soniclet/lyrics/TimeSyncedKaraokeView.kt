package com.kexora.soniclet.lyrics

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.LyricLine
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeSyncedKaraokeView(
    lyrics: List<LyricLine>,
    activeLyricIndex: Int,
    currentPosition: Long,
    syncOffsetMs: Long,
    isKaraokeModeEnabled: Boolean,
    lyricsSourceType: String,
    isLyricsLoading: Boolean,
    isKariModeEnabled: Boolean,
    dominantColor: Color,
    vibrantColor: Color,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onToggleKaraokeMode: () -> Unit,
    onImportLyrics: (Uri) -> Unit,
    onClearCustomLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    onReturnToArt: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    var showOffsetControls by remember { mutableStateOf(false) }

    // Pause auto-scroll for 3 seconds whenever the user manually scrolls
    var isUserScrolling by remember { mutableStateOf(false) }
    var lastUserScrollReleaseMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolling = true
        } else if (isUserScrolling) {
            val releasedAt = SystemClock.elapsedRealtime()
            lastUserScrollReleaseMs = releasedAt
            delay(3000L)
            if (lastUserScrollReleaseMs == releasedAt) {
                isUserScrolling = false
            }
        }
    }

    // 50ms high-frequency monotonic interpolation between Media3 position syncs
    var basePositionMs by remember { mutableLongStateOf(currentPosition) }
    var baseElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var smoothPositionMs by remember { mutableLongStateOf(currentPosition) }

    LaunchedEffect(currentPosition) {
        basePositionMs = currentPosition
        baseElapsedMs = SystemClock.elapsedRealtime()
        smoothPositionMs = currentPosition
    }

    LaunchedEffect(isPlaying, lyrics.isNotEmpty()) {
        if (isPlaying && lyrics.isNotEmpty()) {
            while (true) {
                val delta = (SystemClock.elapsedRealtime() - baseElapsedMs).coerceIn(0L, 180L)
                smoothPositionMs = basePositionMs + delta
                delay(50L)
            }
        } else {
            smoothPositionMs = currentPosition
        }
    }

    val effectivePositionMs = smoothPositionMs + syncOffsetMs
    val resolvedActiveIndex = remember(lyrics, effectivePositionMs, activeLyricIndex) {
        if (lyrics.isEmpty()) -1
        else LyricsUtil.findActiveLyricIndex(lyrics, effectivePositionMs)
    }

    // System File Picker to select local offline .lrc file
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportLyrics(uri)
        }
    }

    // Smooth center auto-scroll using live viewport height (respects 3s manual scroll pause)
    LaunchedEffect(resolvedActiveIndex, isUserScrolling) {
        if (!isUserScrolling && resolvedActiveIndex in lyrics.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val centerOffset = if (viewportHeight > 0) -(viewportHeight / 2) + 48 else -180
            try {
                listState.animateScrollToItem(
                    index = resolvedActiveIndex,
                    scrollOffset = centerOffset
                )
            } catch (_: Exception) {
            }
        }
    }

    val activeAccent = if (isKariModeEnabled) vibrantColor else MaterialTheme.colorScheme.primary
    val pillFill = Color.White.copy(alpha = 0.09f)
    val pillBorder = Color.White.copy(alpha = 0.14f)
    val offsetSecondsFormatted = remember(syncOffsetMs) {
        String.format(Locale.US, "%+.1fs", syncOffsetMs / 1000.0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // SINGLE CLEAN NON-COLLIDING TOP PILL ROW:
        // [Sync] [Karaoke] [Import]  <--- weight(1f) --->  [Art]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Sync Offset Pill
            val isOffsetActive = syncOffsetMs != 0L || showOffsetControls
            Surface(
                shape = CircleShape,
                color = if (isOffsetActive) activeAccent.copy(alpha = 0.22f) else pillFill,
                border = BorderStroke(0.6.dp, if (isOffsetActive) activeAccent.copy(alpha = 0.55f) else pillBorder),
                modifier = Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .clickable { showOffsetControls = !showOffsetControls }
                    .testTag("lyrics_sync_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Sync Offset",
                        modifier = Modifier.size(13.dp),
                        tint = if (isOffsetActive) activeAccent else Color.White.copy(alpha = 0.85f)
                    )
                    Text(
                        text = if (syncOffsetMs == 0L) "Sync" else offsetSecondsFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (isOffsetActive) activeAccent else Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 2. Karaoke Mode Toggle Pill
            Surface(
                shape = CircleShape,
                color = if (isKaraokeModeEnabled) activeAccent.copy(alpha = 0.22f) else pillFill,
                border = BorderStroke(0.6.dp, if (isKaraokeModeEnabled) activeAccent.copy(alpha = 0.55f) else pillBorder),
                modifier = Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .clickable { onToggleKaraokeMode() }
                    .testTag("lyrics_karaoke_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Karaoke Mode",
                        modifier = Modifier.size(13.dp),
                        tint = if (isKaraokeModeEnabled) activeAccent else Color.White.copy(alpha = 0.78f)
                    )
                    Text(
                        text = if (isKaraokeModeEnabled) "Karaoke" else "Line",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isKaraokeModeEnabled) activeAccent else Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 3. Import .LRC Circle Icon Pill
            Surface(
                shape = CircleShape,
                color = pillFill,
                border = BorderStroke(0.6.dp, pillBorder),
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { filePickerLauncher.launch("*/*") }
                    .testTag("lyrics_import_icon_pill")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Import .LRC file",
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. Optional Return-to-Art Pill (anchored cleanly on far right with ZERO collision)
            if (onReturnToArt != null) {
                Surface(
                    shape = CircleShape,
                    color = pillFill,
                    border = BorderStroke(0.6.dp, pillBorder),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(CircleShape)
                        .clickable { onReturnToArt() }
                        .testTag("inline_lyrics_close_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = "Show Cover Art",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Art",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // COMPACT INLINE SYNC OFFSET PILL BAR
        AnimatedVisibility(
            visible = showOffsetControls,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SyncOffsetStepPill("-0.5s") { onAdjustOffset(-500L) }
                SyncOffsetStepPill("-0.1s") { onAdjustOffset(-100L) }

                Surface(
                    shape = CircleShape,
                    color = activeAccent.copy(alpha = 0.16f),
                    border = BorderStroke(0.6.dp, activeAccent.copy(alpha = 0.45f)),
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = offsetSecondsFormatted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = activeAccent
                        )
                    }
                }

                SyncOffsetStepPill("+0.1s") { onAdjustOffset(100L) }
                SyncOffsetStepPill("+0.5s") { onAdjustOffset(500L) }
                SyncOffsetStepPill("Reset", isAccent = syncOffsetMs != 0L, accentColor = activeAccent) {
                    onResetOffset()
                }
            }
        }

        // MAIN LYRICS CONTENT (COMPLETELY BOX-FREE DIRECTLY OVER BLURRED BACKDROP)
        if (isLyricsLoading) {
            com.kexora.soniclet.ui.components.common.SkeletonLyricsView()
        } else if (lyrics.isEmpty()) {
            // Box-Free Floating Empty State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lyrics,
                    contentDescription = null,
                    tint = activeAccent.copy(alpha = 0.90f),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Synced Lyrics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add a local .lrc file or embed synced lyrics in ID3 Studio.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeAccent,
                        contentColor = Color(0xFF06100B)
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("lyrics_empty_import_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import .LRC",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        } else {
            // Box-Free Apple Music / Spotify-Style Synced Lyrics List with Top & Bottom Edge Fade
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (size.height > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.09f to Color.Black,
                                    0.89f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 48.dp, bottom = 72.dp, start = 8.dp, end = 8.dp)
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { idx, item -> "${item.timeMs}_$idx" }
                ) { idx, lyric ->
                    val isActive = idx == resolvedActiveIndex
                    val targetSeekMs = (lyric.timeMs - syncOffsetMs).coerceAtLeast(0L)
                    val scale by animateFloatAsState(
                        targetValue = if (isActive) 1.03f else 0.97f,
                        animationSpec = tween(220),
                        label = "lyric_line_scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                isUserScrolling = false
                                onSeekTo(targetSeekMs)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActive) {
                            if (isKaraokeModeEnabled && lyric.words.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    lyric.words.forEach { word ->
                                        val progress = when {
                                            effectivePositionMs <= word.startMs -> 0f
                                            effectivePositionMs >= word.endMs -> 1f
                                            else -> {
                                                val range = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
                                                ((effectivePositionMs - word.startMs).toFloat() / range).coerceIn(0f, 1f)
                                            }
                                        }

                                        KaraokeWordText(
                                            word = word.text,
                                            progress = progress,
                                            activeColor = Color.White,
                                            glowColor = activeAccent,
                                            inactiveColor = Color.White.copy(alpha = 0.42f),
                                            fontSize = 21.sp
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = lyric.text,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 21.sp,
                                        lineHeight = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        shadow = Shadow(
                                            color = activeAccent.copy(alpha = 0.50f),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 18f
                                        )
                                    ),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Text(
                                text = lyric.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    lineHeight = 23.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.38f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncOffsetStepPill(
    label: String,
    isAccent: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isAccent) accentColor.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(
            0.6.dp,
            if (isAccent) accentColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f)
        ),
        modifier = Modifier
            .height(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = if (isAccent) accentColor else Color.White.copy(alpha = 0.90f)
            )
        }
    }
}

/**
 * Individual Word in Karaoke Mode with progressive left-to-right luminous sweep.
 */
@Composable
private fun KaraokeWordText(
    word: String,
    progress: Float,
    activeColor: Color,
    glowColor: Color,
    inactiveColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val activeStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = fontSize,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        shadow = Shadow(
            color = glowColor.copy(alpha = 0.55f),
            offset = Offset(0f, 0f),
            blurRadius = 16f
        )
    )
    val inactiveStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = fontSize,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    )

    if (progress <= 0f) {
        Text(
            text = word,
            style = inactiveStyle,
            color = inactiveColor,
            modifier = modifier.padding(horizontal = 2.5.dp)
        )
    } else if (progress >= 1f) {
        Text(
            text = word,
            style = activeStyle,
            color = activeColor,
            modifier = modifier.padding(horizontal = 2.5.dp)
        )
    } else {
        Box(modifier = modifier.padding(horizontal = 2.5.dp)) {
            Text(
                text = word,
                style = inactiveStyle,
                color = inactiveColor
            )
            Text(
                text = word,
                style = activeStyle,
                color = activeColor,
                modifier = Modifier.drawWithContent {
                    clipRect(
                        left = 0f,
                        top = 0f,
                        right = size.width * progress,
                        bottom = size.height
                    ) {
                        this@drawWithContent.drawContent()
                    }
                }
            )
        }
    }
}
