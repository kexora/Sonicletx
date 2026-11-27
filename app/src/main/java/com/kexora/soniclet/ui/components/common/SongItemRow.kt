package com.kexora.soniclet.ui.components.common
import androidx.compose.material.icons.rounded.Edit

import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.material.icons.rounded.*
import com.kexora.soniclet.ui.components.common.DropContext
import com.kexora.soniclet.ui.components.common.DropContextItem
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import androidx.compose.ui.platform.LocalContext
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItemRow(
    song: Song,
    isSelected: Boolean,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: ((Song) -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onInsertNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onHideSong: (() -> Unit)? = null,
    onMoveSong: (() -> Unit)? = null,
    onCopySong: (() -> Unit)? = null,
    onDeleteSong: (() -> Unit)? = null,
    onOpenSongDetails: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onTrimAudio: (() -> Unit)? = null,
    onShareSong: ((Song) -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onToggleFavoriteSong: ((Song) -> Unit)? = null,
    onInsertNextSong: ((Song) -> Unit)? = null,
    onAddToQueueSong: ((Song) -> Unit)? = null,
    onAddToPlaylistSong: ((Song) -> Unit)? = null,
    onHideSongItem: ((Song) -> Unit)? = null,
    onMoveSongItem: ((Song) -> Unit)? = null,
    onCopySongItem: ((Song) -> Unit)? = null,
    onDeleteSongItem: ((Song) -> Unit)? = null,
    onOpenSongDetailsItem: ((Song) -> Unit)? = null,
    onEditTagsItem: ((Song) -> Unit)? = null,
    onTrimAudioItem: ((Song) -> Unit)? = null,
    isMultiSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 6.dp,
    savedPosition: Long = 0L,
    highlightQuery: String = ""
) {
    var showMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imageRequest = remember(song.albumArtUri, context) {
        ImageRequest.Builder(context)
            .data(song.albumArtUri)
            .size(120, 120)
            .scale(Scale.FILL)
            .crossfade(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val isHighlighted = isSelected || isMultiSelected
    val rowBg = when {
        isMultiSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> Color.Transparent
    }

    val displayTitle = remember(song.title, song.filePath) { cleanDisplayTitle(song.title, song.filePath) }
    val displayArtist = remember(song.artist, song.title, song.filePath, song.folderName) { cleanSongArtist(song) }
    val metaLine = remember(displayArtist, song.duration) {
        "$displayArtist • ${formatMillisToTime(song.duration)}"
    }
    val highlightColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onSongClick(song) },
                onLongClick = onSongLongClick?.let { { it(song) } }
            )
            .testTag("song_item_${song.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
        ) {
            if (isHighlighted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(vertical = 8.dp)
                        .width(3.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Multi-Select Animated Checkbox Indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = isMultiSelectMode,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(22.dp),
                        shape = CircleShape,
                        color = if (isMultiSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = if (isMultiSelected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isMultiSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // Circular Album Art Thumbnail (44.dp)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = "Album art for $displayTitle",
                            modifier = Modifier.size(44.dp),
                            contentScale = ContentScale.Crop,
                            error = {
                                FallbackAlbumArt(title = displayTitle)
                            }
                        )
                    } else {
                        FallbackAlbumArt(title = displayTitle)
                    }

                    // Playing overlay icon indicator
                    if (isSelected && isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingAudioWave(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Song Information Column: crisp 1-line title + 1-line "Artist • 03:35"
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val titleAnnotated = remember(displayTitle, highlightQuery, highlightColor) {
                        buildHighlightedText(displayTitle, highlightQuery, highlightColor)
                    }
                    Text(
                        text = titleAnnotated,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = (-0.1).sp
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metaAnnotated = remember(metaLine, highlightQuery, highlightColor) {
                        buildHighlightedText(metaLine, highlightQuery, highlightColor)
                    }
                    Text(
                        text = metaAnnotated,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                if (trailingContent != null) {
                    trailingContent()
                } else {
                    val canToggleFav = onToggleFavorite != null || onToggleFavoriteSong != null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (canToggleFav) {
                            IconButton(
                                onClick = {
                                    onToggleFavoriteSong?.invoke(song) ?: onToggleFavorite?.invoke()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("song_fav_${song.id}")
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("song_more_${song.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Song options",
                                    tint = if (showMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(if (showMenu) 90f else 0f)
                                )
                            }

                            if (showMenu) {
                            DropContext(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropContextItem(
                                    text = "Play Now",
                                    icon = Icons.Rounded.PlayArrow,
                                    index = 0,
                                    onClick = {
                                        showMenu = false
                                        onSongClick(song)
                                    }
                                )
                                if (onInsertNext != null || onInsertNextSong != null) {
                                    DropContextItem(
                                        text = "Play Next",
                                        icon = Icons.Rounded.PlaylistPlay,
                                        index = 0,
                                        onClick = {
                                            showMenu = false
                                            onInsertNextSong?.invoke(song) ?: onInsertNext?.invoke()
                                        }
                                    )
                                }
                                if (onAddToQueue != null || onAddToQueueSong != null) {
                                    DropContextItem(
                                        text = "Add to Queue",
                                        icon = Icons.Rounded.QueueMusic,
                                        index = 1,
                                        onClick = {
                                            showMenu = false
                                            onAddToQueueSong?.invoke(song) ?: onAddToQueue?.invoke()
                                        }
                                    )
                                }
                                if (canToggleFav) {
                                    DropContextItem(
                                        text = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        iconTint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                                        index = 2,
                                        onClick = {
                                            showMenu = false
                                            onToggleFavoriteSong?.invoke(song) ?: onToggleFavorite?.invoke()
                                        }
                                    )
                                }
                                if (onAddToPlaylist != null || onAddToPlaylistSong != null) {
                                    DropContextItem(
                                        text = "Add to Playlist",
                                        icon = Icons.Rounded.PlaylistAdd,
                                        index = 3,
                                        onClick = {
                                            showMenu = false
                                            onAddToPlaylistSong?.invoke(song) ?: onAddToPlaylist?.invoke()
                                        }
                                    )
                                }
                                DropContextItem(
                                    text = "Share Song",
                                    icon = Icons.Rounded.Share,
                                    index = 4,
                                    onClick = {
                                        showMenu = false
                                        if (onShareSong != null) {
                                            onShareSong(song)
                                        } else {
                                            try {
                                                val uri = song.contentUri
                                                if (uri != null) {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "audio/*"
                                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                        putExtra(android.content.Intent.EXTRA_TEXT, "${song.title} - ${song.artist}")
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Share \"${song.title}\""))
                                                } else {
                                                    android.widget.Toast.makeText(context, "Cannot share track: URI unavailable", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Could not share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                if (onTrimAudio != null || onTrimAudioItem != null) {
                                    DropContextItem(
                                        text = "Trim & Set Ringtone",
                                        icon = androidx.compose.material.icons.Icons.Rounded.ContentCut,
                                        index = 5,
                                        onClick = {
                                            showMenu = false
                                            onTrimAudioItem?.invoke(song) ?: onTrimAudio?.invoke()
                                        }
                                    )
                                }
                                if (onEditTags != null || onEditTagsItem != null) {
                                    DropContextItem(
                                        text = "Edit Tags",
                                        icon = Icons.Rounded.Edit,
                                        index = 4,
                                        onClick = {
                                            showMenu = false
                                            onEditTagsItem?.invoke(song) ?: onEditTags?.invoke()
                                        }
                                    )
                                }
                                if (onOpenSongDetails != null || onOpenSongDetailsItem != null) {
                                    DropContextItem(
                                        text = "Song Info & Edit",
                                        icon = Icons.Rounded.Info,
                                        index = 4,
                                        onClick = {
                                            showMenu = false
                                            onOpenSongDetailsItem?.invoke(song) ?: onOpenSongDetails?.invoke()
                                        }
                                    )
                                }
                                if (onHideSong != null || onHideSongItem != null) {
                                    DropContextItem(
                                        text = "Hide Song",
                                        icon = Icons.Rounded.VisibilityOff,
                                        index = 5,
                                        onClick = {
                                            showMenu = false
                                            onHideSongItem?.invoke(song) ?: onHideSong?.invoke()
                                        }
                                    )
                                }
                                if (onMoveSong != null || onMoveSongItem != null) {
                                    DropContextItem(
                                        text = "Move to...",
                                        icon = Icons.Rounded.DriveFileMove,
                                        index = 6,
                                        onClick = {
                                            showMenu = false
                                            onMoveSongItem?.invoke(song) ?: onMoveSong?.invoke()
                                        }
                                    )
                                }
                                if (onCopySong != null || onCopySongItem != null) {
                                    DropContextItem(
                                        text = "Copy to...",
                                        icon = Icons.Rounded.FileCopy,
                                        index = 7,
                                        onClick = {
                                            showMenu = false
                                            onCopySongItem?.invoke(song) ?: onCopySong?.invoke()
                                        }
                                    )
                                }
                                if (onDeleteSong != null || onDeleteSongItem != null) {
                                    DropContextItem(
                                        text = "Delete",
                                        icon = Icons.Rounded.Delete,
                                        iconTint = MaterialTheme.colorScheme.error,
                                        index = 8,
                                        onClick = {
                                            showMenu = false
                                            onDeleteSongItem?.invoke(song) ?: onDeleteSong?.invoke()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 68.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
        )
    }
}

@Composable
fun PlayingAudioWave(modifier: Modifier = Modifier) {
    val bar1 = remember { Animatable(0.3f) }
    val bar2 = remember { Animatable(0.8f) }
    val bar3 = remember { Animatable(0.5f) }

    LaunchedEffect(Unit) {
        launch {
            bar1.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
        launch {
            bar2.animateTo(
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
        launch {
            bar3.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar1.value).background(Color.White, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar2.value).background(Color.White, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar3.value).background(Color.White, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
    }
}

fun formatMillisToTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val mStr = minutes.toString()
    val sStr = if (seconds < 10) "0$seconds" else seconds.toString()
    return if (minutes < 10) "0$mStr:$sStr" else "$mStr:$sStr"
}

fun cleanSongTitle(song: Song): String {
    return cleanDisplayTitle(song.title, song.filePath)
}

fun cleanSongTitle(rawTitle: String, filePath: String = ""): String {
    return cleanDisplayTitle(rawTitle, filePath)
}

/**
 * Shared UI display-title cleaner that strips noisy filename artifacts (leading "Video - ",
 * "VIDEO _ ", "Full Video _ ", trailing "(MP4)", "[Official Video]", web spam, file extensions)
 * and replaces underscores with clean single spaces without mutating underlying storage files.
 */
fun cleanDisplayTitle(rawTitle: String, filePath: String = ""): String {
    val rawFileName = if (filePath.isNotBlank()) {
        filePath.substringAfterLast('/').substringBeforeLast('.')
    } else ""

    var t = rawTitle.trim()

    // 1. Detect if rawTitle is generic, empty, numeric, or web spam
    val isGenericTitle = t.isBlank() ||
            t.equals("unknown", ignoreCase = true) ||
            t.equals("unknown track", ignoreCase = true) ||
            t.equals("<unknown>", ignoreCase = true) ||
            t.equals("audio", ignoreCase = true) ||
            t.matches(Regex("^(track|audio|sound|recording)[_\\s-]*\\d+$", RegexOption.IGNORE_CASE)) ||
            t.matches(Regex("^\\d+$"))

    val hasWebSpamInTitle = t.contains("www.", ignoreCase = true) ||
            t.contains("y2mate", ignoreCase = true) ||
            t.contains("pagalworld", ignoreCase = true) ||
            t.contains("songs.pk", ignoreCase = true) ||
            t.contains("320kbps", ignoreCase = true) ||
            t.contains("128kbps", ignoreCase = true) ||
            t.contains("mp3pajji", ignoreCase = true)

    val filenameHasIndicScript = rawFileName.any { it.code in 0x0900..0x0D7F }
    val titleHasIndicScript = t.any { it.code in 0x0900..0x0D7F }

    if (rawFileName.isNotBlank() && (isGenericTitle || hasWebSpamInTitle || (filenameHasIndicScript && !titleHasIndicScript))) {
        t = rawFileName
    }

    // 2. Strip common media extensions (.mp3, .m4a, .mp4, .mkv, .webm, etc.)
    t = t.replace(Regex("\\.(mp3|m4a|wav|aac|opus|ogg|flac|wma|mp4|mkv|webm|3gp)$", RegexOption.IGNORE_CASE), "")

    // 3. Strip leading noisy video/audio prefixes (e.g. "Video - ", "VIDEO _ ", "Full Video _ ")
    val leadingNoiseRegex = Regex(
        "^\\s*(?:(?:full\\s+|official\\s+|lyrical\\s+|hd\\s+|4k\\s+|new\\s+)?(?:video|audio|song|music\\s+video)\\s*[:\\-_|•]+\\s*|\\[(?:video|audio|hd|4k)\\]\\s*|\\((?:video|audio|hd|4k)\\)\\s*)",
        RegexOption.IGNORE_CASE
    )
    var prev: String
    do {
        prev = t
        t = t.replace(leadingNoiseRegex, "")
    } while (t != prev)

    // 4. Strip web spam & site promos
    t = t.replace(Regex("\\[(y2mate|pagalworld|songs\\.pk|mr-jatt|djpunjab|ringtone|mp3pajji)[^\\]]*\\]", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("\\((y2mate|pagalworld|songs\\.pk|mr-jatt|djpunjab|ringtone|mp3pajji)[^\\)]*\\)", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("y2mate\\.com\\s*-\\s*", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}\\b", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("\\b(320kbps|128kbps|kbps)\\b", RegexOption.IGNORE_CASE), "")

    // 5. Strip trailing/bracketed format & video tags such as (MP4), [Official Video], (Full Video), (HD)
    t = t.replace(Regex("\\((official|lyric|lyrical|full|hd|4k|1080p|720p|mp4|mp3|video|audio|remix|slowed|reverb|status)[^\\)]*\\)", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("\\[(official|lyric|lyrical|full|hd|4k|1080p|720p|mp4|mp3|video|audio|remix|slowed|reverb|status)[^\\]]*\\]", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("\\s*(?:-|_|\\|)\\s*(?:mp4|official\\s+video|full\\s+video|lyrical\\s+video|hd\\s+video)\\s*$", RegexOption.IGNORE_CASE), "")

    // 6. Call recording date/time suffix cleaning
    val isRecording = t.startsWith("Call", ignoreCase = true) ||
            t.startsWith("REC", ignoreCase = true) ||
            t.startsWith("Record", ignoreCase = true)
    if (isRecording) {
        t = t.replace(Regex("(_|-)\\d{6,8}(_|-)\\d{4,6}$"), "")
        t = t.replace(Regex("(_|-)\\d{6,8}$"), "")
        t = t.replace(Regex("(_|-)\\d{4,}$"), "")
    }

    // 7. Replace underscores with single spaces and collapse redundant whitespace
    t = t.replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('-', '|', '•', ' ')
        .replace(Regex("\\s+"), " ")

    return if (t.isBlank()) {
        val fallback = (if (rawFileName.isNotBlank()) rawFileName else rawTitle).replace('_', ' ').trim()
        fallback.ifBlank { rawTitle }
    } else {
        t
    }
}

/**
 * Highlights matched query tokens inside a display string for Search results.
 */
fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank() || text.isBlank()) {
        return AnnotatedString(text)
    }
    val lowerText = text.lowercase()
    val tokens = cleanQuery.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
    if (tokens.isEmpty()) {
        val idx = lowerText.indexOf(cleanQuery.lowercase())
        if (idx < 0) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text.substring(0, idx))
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(idx, idx + cleanQuery.length))
            }
            append(text.substring(idx + cleanQuery.length))
        }
    }

    val matchedMask = BooleanArray(text.length)
    for (token in tokens) {
        var searchFrom = 0
        while (searchFrom < lowerText.length) {
            val found = lowerText.indexOf(token, searchFrom)
            if (found < 0) break
            val end = (found + token.length).coerceAtMost(text.length)
            for (i in found until end) matchedMask[i] = true
            searchFrom = end
        }
    }

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val isMatch = matchedMask[i]
            var j = i + 1
            while (j < text.length && matchedMask[j] == isMatch) j++
            if (isMatch) {
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(text.substring(i, j))
                }
            } else {
                append(text.substring(i, j))
            }
            i = j
        }
    }
}

fun cleanSongArtist(song: Song): String {
    val raw = song.artist.trim()
    val isUnknown = raw.isBlank() ||
            raw.equals("<unknown>", ignoreCase = true) ||
            raw.equals("unknown", ignoreCase = true) ||
            raw.equals("unknown artist", ignoreCase = true) ||
            raw.equals("<unknown artist>", ignoreCase = true)

    if (isUnknown) {
        val lowerPath = (song.filePath + " " + song.title + " " + song.album).lowercase()
        val isCall = lowerPath.contains("call")
        val isVoice = lowerPath.contains("voice") || lowerPath.contains("record")
        return when {
            isCall -> "Call Recording"
            isVoice -> "Voice Recording"
            song.folderName.isNotBlank() &&
                !song.folderName.equals("Music", ignoreCase = true) &&
                !song.folderName.equals("Download", ignoreCase = true) -> song.folderName
            else -> "Unknown Artist"
        }
    }
    return raw
}

fun cleanSongAlbum(song: Song): String {
    val a = song.album.trim()
    val isUnknown = a.isBlank() ||
            a.equals("<unknown>", ignoreCase = true) ||
            a.equals("unknown", ignoreCase = true)
    return if (isUnknown) {
        song.folderName.ifBlank { "Soniclet" }
    } else {
        a
    }
}
