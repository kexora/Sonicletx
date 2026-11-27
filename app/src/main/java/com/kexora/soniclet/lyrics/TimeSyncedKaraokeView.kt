package com.kexora.soniclet.lyrics

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.player.LyricLine
import com.kexora.soniclet.player.LyricWord

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showOffsetControls by remember { mutableStateOf(false) }

    // System File Picker to select local offline .lrc file
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportLyrics(uri)
        }
    }

    // Auto-scroll physics to keep active karaoke line centered
    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = activeLyricIndex,
                scrollOffset = -220
            )
        }
    }

    val activeHighlightColor = if (isKariModeEnabled) dominantColor else MaterialTheme.colorScheme.primary
    val secondaryHighlightColor = if (isKariModeEnabled) vibrantColor else MaterialTheme.colorScheme.tertiary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP KARAOKE TOOLBAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source badge (e.g. Offline .LRC, Embedded Tag, Imported)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (lyricsSourceType.contains("Imported", true)) Icons.Rounded.FileUpload
                        else if (lyricsSourceType.contains("Embedded", true)) Icons.Rounded.MusicNote
                        else Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = activeHighlightColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (lyrics.isEmpty()) "No Lyrics" else lyricsSourceType.ifEmpty { "Offline .LRC" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Offset calibration button
                Surface(
                    shape = CircleShape,
                    color = if (syncOffsetMs != 0L) activeHighlightColor.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .clickable { showOffsetControls = !showOffsetControls }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Sync Calibration",
                            modifier = Modifier.size(14.dp),
                            tint = if (syncOffsetMs != 0L) activeHighlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (syncOffsetMs == 0L) "Sync" else "%+d ms".format(syncOffsetMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (syncOffsetMs != 0L) activeHighlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Word-by-Word Karaoke Toggle
                Surface(
                    shape = CircleShape,
                    color = if (isKaraokeModeEnabled) activeHighlightColor.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .clickable { onToggleKaraokeMode() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Karaoke Mode",
                            modifier = Modifier.size(14.dp),
                            tint = if (isKaraokeModeEnabled) activeHighlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isKaraokeModeEnabled) "Karaoke ON" else "Line Sync",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isKaraokeModeEnabled) activeHighlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Import / Pick file button
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Import .LRC file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // SYNC OFFSET CALIBRATION BAR (EXPANDABLE)
        AnimatedVisibility(visible = showOffsetControls) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offset: %+d ms".format(syncOffsetMs),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { onAdjustOffset(-500L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("-0.5s", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { onAdjustOffset(-100L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("-0.1s", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = onResetOffset,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { onAdjustOffset(100L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+0.1s", style = MaterialTheme.typography.labelSmall)
                        }
                        FilledTonalButton(
                            onClick = { onAdjustOffset(500L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+0.5s", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // MAIN LYRICS CONTENT
        if (isLyricsLoading) {
            com.kexora.soniclet.ui.components.common.SkeletonLyricsView()
        } else if (lyrics.isEmpty()) {
            // EMPTY STATE: No lyrics found
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = activeHighlightColor,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Synchronized Lyrics Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Place an offline .lrc file in the same folder as the song, or tap below to pick a .lrc file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = activeHighlightColor)
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Offline .LRC")
                        }
                    }
                }
            }
        } else {
            // SCROLLABLE KARAOKE LYRICS LIST
            val effectivePosition = currentPosition + syncOffsetMs

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 40.dp)
            ) {
                itemsIndexed(lyrics) { idx, lyric ->
                    val isActive = idx == activeLyricIndex
                    val isPast = idx < activeLyricIndex

                    if (isActive) {
                        // ACTIVE KARAOKE LINE (CONTAINER WITH WORD-BY-WORD PROGRESSION)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = activeHighlightColor.copy(alpha = 0.12f),
                            border = borderActive(activeHighlightColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(lyric.timeMs) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isKaraokeModeEnabled && lyric.words.isNotEmpty()) {
                                    // WORD-BY-WORD KARAOKE HIGHLIGHT
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        lyric.words.forEach { word ->
                                            val progress = when {
                                                effectivePosition <= word.startMs -> 0f
                                                effectivePosition >= word.endMs -> 1f
                                                else -> {
                                                    val range = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
                                                    ((effectivePosition - word.startMs).toFloat() / range).coerceIn(0f, 1f)
                                                }
                                            }

                                            KaraokeWordText(
                                                word = word.text,
                                                progress = progress,
                                                activeColor = activeHighlightColor,
                                                inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                                fontSize = 21.sp
                                            )
                                        }
                                    }
                                } else {
                                    // STANDARD FULL-LINE HIGHLIGHT
                                    Text(
                                        text = lyric.text,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 21.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = activeHighlightColor,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        // INACTIVE LINES (PAST OR UPCOMING)
                        val textColor = if (isPast) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        }

                        Text(
                            text = lyric.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = if (isPast) FontWeight.Normal else FontWeight.Medium
                            ),
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSeekTo(lyric.timeMs) }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Word in Karaoke Mode:
 * If progress == 0f: Dimmed text.
 * If progress == 1f: Full glowing highlighted text.
 * If progress in 0f..1f: Sweeping clipRect across word letters from left to right!
 */
@Composable
private fun KaraokeWordText(
    word: String,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    if (progress <= 0f) {
        Text(
            text = word,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.Medium
            ),
            color = inactiveColor,
            modifier = modifier.padding(horizontal = 2.dp)
        )
    } else if (progress >= 1f) {
        Text(
            text = word,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold
            ),
            color = activeColor,
            modifier = modifier.padding(horizontal = 2.dp)
        )
    } else {
        // ACTIVELY SINGING WORD: Dual layer with clipRect progressive sweep
        Box(
            modifier = modifier.padding(horizontal = 2.dp)
        ) {
            // Background inactive text
            Text(
                text = word,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium
                ),
                color = inactiveColor
            )

            // Foreground sweeping active text
            Text(
                text = word,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold
                ),
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

@Composable
private fun borderActive(color: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(
            listOf(
                color.copy(alpha = 0.2f),
                color.copy(alpha = 0.7f),
                color.copy(alpha = 0.2f)
            )
        )
    )
}
