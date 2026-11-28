package com.kexora.soniclet.player

import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.utils.fuzzyMatch
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.library.*
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.database.SavedQueue
import java.util.Locale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import com.kexora.soniclet.utils.ArtistParserUtil

/**
 * Professional World-Class Play Queue Screen.
 * Architecture Features:
 * 1. Prominent Now Playing Hero Card (Large Artwork, Live Audio Waves, Quick Controls).
 * 2. Structured Up Next Queue & Collapsible Previously Played History.
 * 3. Quick Track Reordering (Move to Play Next, Move to Bottom, Remove).
 * 4. Multiple Saved Queue Presets Switcher Bar.
 * 5. Floating "Jump to Now Playing" FAB when scrolled away.
 */
@Composable
fun PlayQueue(
    activeQueue: List<Song>,
    queueSourceTitle: String,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onTogglePlayPause: () -> Unit = {},
    onRemoveFromQueue: (Long) -> Unit,
    onMoveQueueItemToNext: ((Int) -> Unit)? = null,
    onMoveQueueItemToBottom: ((Int) -> Unit)? = null,
    onReorderQueueItem: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    onToggleFavorite: ((Long) -> Unit)? = null,
    onClearQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    searchQuery: String = "",
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onSaveQueue: ((String) -> Unit)? = null,
    savedQueues: List<SavedQueue> = emptyList(),
    onLoadSavedQueue: ((String) -> Unit)? = null,
    onDeleteSavedQueue: ((String) -> Unit)? = null,
    selectedSongIds: Set<Long> = emptySet(),
    onToggleSongSelection: (Long) -> Unit = {},
    favoriteSongIds: Set<Long> = emptySet(),
    actuallyPlayedSongIds: Set<Long> = emptySet(),
    playbackPositions: Map<Long, Long> = emptyMap(),
    onNavigateToArtist: ((String) -> Unit)? = null,
    isEmbeddedInSheet: Boolean = false
) {
    var showSaveDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var queueToDelete by remember { mutableStateOf<SavedQueue?>(null) }
    var isHistoryExpanded by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Dialog to save current queue under a custom name
    if (showSaveDialog) {
        var queueName by remember { mutableStateOf(queueSourceTitle.ifBlank { "My Queue" }) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    text = "Save Current Queue",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                OutlinedTextField(
                    value = queueName,
                    onValueChange = { queueName = it },
                    label = { Text("Queue Name (e.g. Chill Mix, Gym)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (queueName.isNotBlank() && onSaveQueue != null) {
                        onSaveQueue(queueName)
                        showSaveDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog to delete a saved queue
    if (queueToDelete != null) {
        val q = queueToDelete!!
        AlertDialog(
            onDismissRequest = { queueToDelete = null },
            title = { Text("Delete Queue") },
            text = { Text("Remove \"${q.name}\" from your saved multiple queues?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSavedQueue?.invoke(q.id)
                        queueToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { queueToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val filteredQueue = remember(activeQueue, searchQuery) {
        if (searchQuery.isBlank()) activeQueue
        else activeQueue.filter { song ->
            song.title.fuzzyMatch(searchQuery) ||
            song.artist.fuzzyMatch(searchQuery)
        }
    }

    val currentIndex = remember(filteredQueue, currentSong?.id) {
        filteredQueue.indexOfFirst { it.id == currentSong?.id }
    }

    val historySongs = remember(filteredQueue, currentIndex) {
        if (currentIndex > 0) filteredQueue.take(currentIndex) else emptyList()
    }

    val nowPlayingTrack = remember(filteredQueue, currentIndex, currentSong) {
        if (currentIndex >= 0 && currentIndex < filteredQueue.size) filteredQueue[currentIndex] else currentSong
    }

    val upNextSongs = remember(filteredQueue, currentIndex) {
        if (currentIndex >= 0 && currentIndex < filteredQueue.size - 1) {
            filteredQueue.drop(currentIndex + 1)
        } else if (currentIndex == -1) {
            filteredQueue
        } else {
            emptyList()
        }
    }

    val listState = rememberLazyListState()

    // Smooth coverart header scroll animation: alpha & scale derived from list scroll offset
    val heroAlphaAndScale by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                val offset = listState.firstVisibleItemScrollOffset.toFloat()
                val alpha = ((320f - offset) / 320f).coerceIn(0f, 1f)
                val scale = (0.85f + 0.15f * alpha).coerceIn(0.85f, 1f)
                alpha to scale
            } else {
                0f to 0.85f
            }
        }
    }

    // Floating FAB visibility when user scrolls far from Now Playing item
    val showJumpToPlayingFab by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            nowPlayingTrack != null && (firstVisible > 4 || firstVisible < (currentIndex.coerceAtLeast(0) - 2))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (filteredQueue.isEmpty()) {
            if (isLoading) {
                SkeletonQueueView()
            } else {
                EmptyCategoryState(
                    icon = Icons.Rounded.QueueMusic,
                    title = "Queue is Empty",
                    description = "Play any folder, playlist, or song to start your queue."
                )
            }
        } else {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomPadding = if (isEmbeddedInSheet) {
                24.dp + navBarBottom
            } else {
                maxOf(24.dp, LocalPlayerBottomPadding.current + 16.dp) + navBarBottom
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollFade(listState),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // 1. SAVED QUEUES SWITCHER & SOURCE HEADER
                item(key = "queue_source_switcher") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        // Source Title Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sourceIcon = getQueueSourceIcon(queueSourceTitle)
                            Icon(
                                imageVector = sourceIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = queueSourceTitle.ifBlank { "Active Queue" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${filteredQueue.size} tracks",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Saved Queues Horizontal Pill Strip
                        if (savedQueues.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(savedQueues, key = { it.id }) { sq ->
                                    val isCurrentActive = sq.name.equals(queueSourceTitle, ignoreCase = true)
                                    val icon = getQueueSourceIcon(sq.name)

                                    Surface(
                                        onClick = { onLoadSavedQueue?.invoke(sq.id) },
                                        shape = CircleShape,
                                        color = if (isCurrentActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        contentColor = if (isCurrentActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        border = if (isCurrentActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = sq.name.removePrefix("Folder: ").removePrefix("Playlist: "),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 11.5.sp
                                                ),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "${sq.songIds.size}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { queueToDelete = sq },
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Delete queue",
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }



                // 3. FULL CONTINUOUS FIXED QUEUE LIST HEADER & QUICK ACTION CHIPS
                item(key = "full_queue_section_header") {
                    val totalDuration = remember(filteredQueue) { filteredQueue.sumOf { it.duration } }
                    val durationText = if (totalDuration > 0) " • ${formatDuration(totalDuration)}" else ""

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 6.dp, start = 4.dp, end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Playing Queue",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${filteredQueue.size} tracks$durationText",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Quick Queue Action Chips
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = onToggleShuffle,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle Queue",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showSaveDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BookmarkAdd,
                                        contentDescription = "Save Queue",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onClearQueue,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = "Clear Queue",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. FIXED CONTINUOUS QUEUE ITEMS LIST (Index 01..N)
                itemsIndexed(
                    items = filteredQueue,
                    key = { globalIndex, song -> "q_item_${song.id}_$globalIndex" }
                ) { globalIndex, song ->
                    val isPast = globalIndex < currentIndex && actuallyPlayedSongIds.contains(song.id)
                    val isPlayingNow = globalIndex == currentIndex

                    QueueItemRow(
                        index = globalIndex + 1,
                        song = song,
                        isPlayingNow = isPlayingNow,
                        isPastTrack = isPast,
                        savedPosition = playbackPositions[song.id] ?: 0L,
                        isMultiSelected = selectedSongIds.contains(song.id),
                        isMultiSelectMode = selectedSongIds.isNotEmpty(),
                        isFavorite = favoriteSongIds.contains(song.id),
                        onClick = {
                            if (selectedSongIds.isNotEmpty()) {
                                onToggleSongSelection(song.id)
                            } else {
                                onPlaySong(song)
                            }
                        },
                        onLongClick = { onToggleSongSelection(song.id) },
                        onRemove = { onRemoveFromQueue(song.id) },
                        onMoveToNext = { onMoveQueueItemToNext?.invoke(globalIndex) },
                        onMoveToBottom = { onMoveQueueItemToBottom?.invoke(globalIndex) },
                        onMoveUp = if (globalIndex > 0) { { onReorderQueueItem?.invoke(globalIndex, globalIndex - 1) } } else null,
                        onMoveDown = if (globalIndex < filteredQueue.size - 1) { { onReorderQueueItem?.invoke(globalIndex, globalIndex + 1) } } else null,
                        onToggleFavorite = { onToggleFavorite?.invoke(song.id) },
                        onNavigateToArtist = onNavigateToArtist
                    )
                }

                item(key = "queue_brand_footer") {
                    MadeInIndiaFooter()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueItemRow(
    index: Int,
    song: Song,
    isPlayingNow: Boolean,
    isPastTrack: Boolean = false,
    isMultiSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRemove: () -> Unit,
    onMoveToNext: (() -> Unit)? = null,
    onMoveToBottom: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    savedPosition: Long = 0L,
    onNavigateToArtist: ((String) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val rowBg = when {
        isMultiSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isPlayingNow -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val border = when {
        isMultiSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        else -> null
    }

    val contentAlpha = if (isPastTrack) 0.45f else 1.0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(12.dp)) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sleek Playing Left Accent Indicator Bar
            if (isPlayingNow) {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, end = 5.dp)
                        .width(3.dp)
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            // Multi-Select Animated Checkbox Indicator
            AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Surface(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(18.dp),
                    shape = CircleShape,
                    color = if (isMultiSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = if (isMultiSelected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isMultiSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Track Number Index
            Text(
                text = String.format("%02d", index),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isPlayingNow) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.5.sp
                ),
                color = if (isPlayingNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPastTrack) 0.35f else 0.7f),
                modifier = Modifier.width(22.dp)
            )

            // Album Art Thumbnail
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            ) {
                if (song.albumArtUri != null) {
                    val queueContext = LocalContext.current
                    val queueArtKey = remember(song.id, song.dateAdded, song.albumArtUri, queueContext) {
                        com.kexora.soniclet.utils.CustomArtworkManager.buildSongArtCacheKey(queueContext, song)
                    }
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(queueContext)
                            .data(song.albumArtUri)
                            .memoryCacheKey("${queueArtKey}_queue")
                            .diskCacheKey(queueArtKey)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        error = { FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize().clip(CircleShape)) }
                    )
                } else {
                    FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title, Artists & Length (Super Compact & Clean)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanSongTitle(song),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isPlayingNow) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPlayingNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )

                Spacer(modifier = Modifier.height(1.dp))

                val rawArtist = if (song.artist.isBlank() || song.artist == "<unknown>") "Unknown Artist" else song.artist
                val artistText = remember(rawArtist, song.title) {
                    val parsed = ArtistParserUtil.splitArtists(rawArtist, song.title)
                    if (parsed.isNotEmpty()) parsed.take(2).joinToString(", ") else "Unknown Artist"
                }
                val durationText = if (song.duration > 0) " • ${formatDuration(song.duration)}" else ""

                Text(
                    text = "$artistText$durationText",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPastTrack) 0.45f else 0.85f),
                    modifier = Modifier.clickable { onNavigateToArtist?.invoke(rawArtist) }
                )
            }

            // Quick Actions Overflow Menu & Reorder Handle
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (onMoveUp != null) {
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (onMoveDown != null) {
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    if (onMoveToNext != null && !isPastTrack) {
                        DropdownMenuItem(
                            text = { Text("Play Next") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onMoveToNext()
                            }
                        )
                    }
                    if (onMoveToBottom != null && !isPastTrack) {
                        DropdownMenuItem(
                            text = { Text("Move to Bottom") },
                            leadingIcon = { Icon(Icons.Rounded.South, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onMoveToBottom()
                            }
                        )
                    }
                    if (onToggleFavorite != null) {
                        DropdownMenuItem(
                            text = { Text(if (isFavorite) "Remove Favorite" else "Favorite") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleFavorite()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Queue", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }

            // Interactive Drag / Reorder Handle Icon
            IconButton(
                onClick = {
                    if (onMoveUp != null) onMoveUp() else if (onMoveDown != null) onMoveDown()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Reorder,
                    contentDescription = "Reorder track",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun getQueueSourceIcon(title: String): ImageVector {
    val lower = title.lowercase(Locale.getDefault())
    return when {
        lower.contains("call") -> Icons.Rounded.PhoneInTalk
        lower.contains("voice") || lower.contains("memo") -> Icons.Rounded.Mic
        lower.contains("hidden") || lower.contains("nomedia") -> Icons.Rounded.FolderSpecial
        lower.startsWith("folder:") || lower.contains("folder") -> Icons.Rounded.Folder
        lower.startsWith("playlist:") || lower.contains("playlist") -> Icons.Rounded.QueueMusic
        lower.startsWith("album:") || lower.contains("album") -> Icons.Rounded.Album
        lower.startsWith("artist:") || lower.contains("artist") -> Icons.Rounded.Person
        lower.contains("favorite") -> Icons.Rounded.Favorite
        lower.contains("smart") -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.GraphicEq
    }
}


internal fun getSystemHiddenBadge(song: Song): String? {
    val path = (song.folderPath.ifBlank { song.filePath }).lowercase()
    val name = song.folderName.ifBlank { song.folderPath.substringAfterLast('/') }.ifBlank { "Hidden Folder" }
    val album = song.album.lowercase()

    return when {
        path.contains("call_recording") || path.contains("callrecording") || path.contains("recordings/call") || album.contains("call recording") || path.contains("call") -> {
            "🎙️ Call Recording • $name"
        }
        path.contains("voice") || path.contains("memo") || album.contains("voice") || path.contains("sound_recorder") -> {
            "🎙️ Voice Memo • $name"
        }
        path.contains(".nomedia") || album.contains(".nomedia") -> {
            "🔒 Hidden Folder • $name"
        }
        name.startsWith(".") || path.contains("/.") || album.contains("system hidden") -> {
            "🔒 System Hidden • $name"
        }
        album.startsWith("system hidden") -> {
            "🔒 System Hidden • $name"
        }
        else -> null
    }
}

