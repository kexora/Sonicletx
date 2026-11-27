package com.kexora.soniclet.library

import com.kexora.soniclet.utils.fuzzyMatch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale

import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.ui.components.common.scrollFade
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetails(
    playlist: Playlist?,
    smartPlaylistTitle: String? = null,
    smartIcon: ImageVector? = null,
    smartPlaylistSongs: List<Song>? = null,
    allLibrarySongs: List<Song>,
    currentPlayingSong: Song?,
    isPlaying: Boolean,
    isGridMode: Boolean = false,
    isLoading: Boolean = false,
    favoriteSongIds: Set<Long>,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Int, String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSongsToPlaylist: (String, List<Song>) -> Unit,
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onUpdatePlaylistGradient: (String, Int) -> Unit = { _, _ -> },
    onInsertNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    selectedSongIds: Set<Long> = emptySet(),
    onToggleSongSelection: (Long) -> Unit = {},
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val isSmart = smartPlaylistTitle != null
    val title = smartPlaylistTitle ?: playlist?.name ?: "Unknown Playlist"
    val songs = remember(smartPlaylistSongs, allLibrarySongs, playlist?.songIds) {
        smartPlaylistSongs ?: run {
            val songMap = allLibrarySongs.associateBy { it.id }
            playlist?.songIds?.mapNotNull { songMap[it] }.orEmpty()
        }
    }
    
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs else songs.filter { it.title.fuzzyMatch(searchQuery) || it.artist.fuzzyMatch(searchQuery) }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val scrollOffset by remember(isGridMode) {
        derivedStateOf {
            if (isGridMode) {
                if (gridState.firstVisibleItemIndex > 0) 350f
                else gridState.firstVisibleItemScrollOffset.toFloat()
            } else {
                if (listState.firstVisibleItemIndex > 0) 350f
                else listState.firstVisibleItemScrollOffset.toFloat()
            }
        }
    }

    val collapseFraction by remember {
        derivedStateOf { (scrollOffset / 200f).coerceIn(0f, 1f) }
    }

    val topBarTitleAlpha by remember {
        derivedStateOf { (collapseFraction * 2.5f - 1.2f).coerceIn(0f, 1f) }
    }

    val context = LocalContext.current
    val bottomPadding = LocalPlayerBottomPadding.current + 8.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = topBarTitleAlpha }
            )
            if (!isSmart) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add Songs") },
                        onClick = { showMenu = false; showAddSongsDialog = true },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share All Songs") },
                        onClick = {
                            showMenu = false
                            if (songs.isNotEmpty()) {
                                try {
                                    val uris = songs.mapNotNull { it.contentUri }
                                    if (uris.size == 1) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "audio/*"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                                            putExtra(android.content.Intent.EXTRA_TEXT, "${songs.first().title} - ${songs.first().artist}")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share track"))
                                    } else if (uris.size > 1) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "audio/*"
                                            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share ${uris.size} tracks"))
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Could not share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; showRenameDialog = true },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { showMenu = false; showDeleteDialog = true },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.kexora.soniclet.ui.components.common.SkeletonPlaylistDetailsView()
            }
        } else {
            val totalDurationMs = remember(songs) { songs.sumOf { it.duration } }
            val totalDurationStr = remember(totalDurationMs) {
                val totalSec = totalDurationMs / 1000
                val hr = totalSec / 3600
                val min = (totalSec % 3600) / 60
                if (hr > 0) "${hr}h ${min}m" else "${min}m"
            }

            Column(modifier = Modifier.fillMaxSize()) {
                if (isGridMode) {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        state = gridState,
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .scrollFade(gridState),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item(
                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            key = "playlist_header"
                        ) {
                            PlaylistDetailHeader(
                                title = title,
                                songsCount = songs.size,
                                totalDurationMs = totalDurationMs,
                                totalDurationStr = totalDurationStr,
                                smartIcon = smartIcon,
                                songs = songs,
                                filteredSongs = filteredSongs,
                                collapseFraction = collapseFraction,
                                onPlayQueue = onPlayQueue
                            )
                        }

                        if (searchQuery.isNotBlank()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Matches: ${filteredSongs.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                            }
                        }
                        items(filteredSongs.size, key = { filteredSongs[it].id }) { index ->
                            val song = filteredSongs[index]
                            GeometricTrackGridItem(
                                song = song,
                                isCurrentPlaying = currentPlayingSong?.id == song.id,
                                isPlaying = isPlaying && currentPlayingSong?.id == song.id,
                                onClick = { onPlayQueue(filteredSongs, index, title) },
                                onRemove = if (!isSmart && playlist != null) { { onRemoveSongFromPlaylist(playlist.id, song.id) } } else null
                            )
                        }

                        item(
                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            key = "playlist_detail_grid_brand_footer"
                        ) {
                            com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .scrollFade(listState),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        item(key = "playlist_header") {
                            PlaylistDetailHeader(
                                title = title,
                                songsCount = songs.size,
                                totalDurationMs = totalDurationMs,
                                totalDurationStr = totalDurationStr,
                                smartIcon = smartIcon,
                                songs = songs,
                                filteredSongs = filteredSongs,
                                collapseFraction = collapseFraction,
                                onPlayQueue = onPlayQueue
                            )
                        }

                        if (searchQuery.isNotBlank()) {
                            item {
                                Text(
                                    text = "Matches: ${filteredSongs.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                            val isSelected = selectedSongIds.contains(song.id)
                            GeometricTrackItem(
                                song = song,
                                isCurrentPlaying = currentPlayingSong?.id == song.id,
                                isPlaying = isPlaying && currentPlayingSong?.id == song.id,
                                isMultiSelected = isSelected,
                                isMultiSelectMode = selectedSongIds.isNotEmpty(),
                                onClick = {
                                    if (selectedSongIds.isNotEmpty()) {
                                        onToggleSongSelection(song.id)
                                    } else {
                                        onPlayQueue(filteredSongs, index, title)
                                    }
                                },
                                onLongClick = { onToggleSongSelection(song.id) },
                                onRemove = if (!isSmart && playlist != null) { { onRemoveSongFromPlaylist(playlist.id, song.id) } } else null
                            )
                        }

                        item(key = "playlist_detail_list_brand_footer") {
                            com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                        }
                    }
                }
            }
        }
    }

    if (showAddSongsDialog && playlist != null) {
        AddSongsToPlaylistDialog(
            playlist = playlist,
            allSongs = allLibrarySongs,
            onDismiss = { showAddSongsDialog = false },
            onAddSongs = { ids -> onAddSongsToPlaylist(playlist.id, ids); showAddSongsDialog = false }
        )
    }

    if (showRenameDialog && playlist != null) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName -> onRenamePlaylist(playlist.id, newName); showRenameDialog = false }
        )
    }

    if (showDeleteDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlist.name}'?") },
            confirmButton = {
                TextButton(onClick = { onDeletePlaylist(playlist.id); showDeleteDialog = false; onBack() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GeometricTrackItem(
    song: Song,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    isGridMode: Boolean = false,
    isMultiSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val bgColor = if (isMultiSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else if (isCurrentPlaying) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isMultiSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isMultiSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
            ) {
                if (song.albumArtUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                        }
                    )
                } else {
                    FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                }
                if (isCurrentPlaying && isPlaying) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        AnimatedEQIcon()
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Share Song") },
                        onClick = {
                            showMenu = false
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
                                }
                            } catch (_: Exception) {}
                        },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }
                    )
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from playlist") },
                            onClick = { showMenu = false; onRemove() },
                            leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedEQIcon() {
    Row(
        modifier = Modifier.size(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq")
        val h1 by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse), label = "eq1")
        val h2 by infiniteTransition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse), label = "eq2")
        val h3 by infiniteTransition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "eq3")
        
        Box(modifier = Modifier.width(4.dp).fillMaxHeight(h1).background(Color.White, CircleShape))
        Box(modifier = Modifier.width(4.dp).fillMaxHeight(h2).background(Color.White, CircleShape))
        Box(modifier = Modifier.width(4.dp).fillMaxHeight(h3).background(Color.White, CircleShape))
    }
}

@Composable
fun AddSongsToPlaylistDialog(
    playlist: Playlist,
    allSongs: List<Song>,
    onDismiss: () -> Unit,
    onAddSongs: (List<Song>) -> Unit
) {
    val existingIds = playlist.songIds.toSet()
    val availableSongs = allSongs.filter { it.id !in existingIds }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Songs") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                itemsIndexed(availableSongs, key = { _, song -> song.id }) { _, song ->
                    val isSelected = selectedIds.contains(song.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAddSongs(allSongs.filter { it.id in selectedIds }) }) {
                Text("Add (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onRename(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


val PLAYLIST_GRADIENT_PRESETS = listOf(
    listOf(Color(0xFF6A11CB), Color(0xFF2575FC)),
    listOf(Color(0xFFFF5F6D), Color(0xFFFFC371)),
    listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    listOf(Color(0xFFEE0979), Color(0xFFFF6A00)),
    listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
    listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
    listOf(Color(0xFFED213A), Color(0xFF93291E)),
    listOf(Color(0xFF1D976C), Color(0xFF93F9B9))
)




@Composable
fun GeometricTrackGridItem(
    song: Song,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.aspectRatio(1f).fillMaxWidth()) {
                if (song.albumArtUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                        }
                    )
                } else {
                    FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                }
                if (isCurrentPlaying && isPlaying) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                        AnimatedEQIcon()
                    }
                }
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp).background(Color.Black.copy(alpha=0.4f), CircleShape)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share Song") },
                            onClick = {
                                showMenu = false
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
                                    }
                                } catch (_: Exception) {}
                            },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }
                        )
                        if (onRemove != null) {
                            DropdownMenuItem(
                                text = { Text("Remove") },
                                onClick = { showMenu = false; onRemove() },
                                leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) }
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    title: String,
    songsCount: Int,
    totalDurationMs: Long,
    totalDurationStr: String,
    smartIcon: ImageVector?,
    songs: List<Song>,
    filteredSongs: List<Song>,
    collapseFraction: Float,
    onPlayQueue: (List<Song>, Int, String) -> Unit
) {
    val artworkScale = (1f - collapseFraction * 0.45f).coerceIn(0.55f, 1f)
    val artworkAlpha = (1f - collapseFraction * 1.25f).coerceIn(0f, 1f)
    val artworkSize = (135 * artworkScale).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Dynamic Artwork with smooth scroll scaling & fading
        Box(
            modifier = Modifier
                .size(artworkSize)
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                    alpha = artworkAlpha
                }
                .shadow(
                    elevation = (14 * (1f - collapseFraction)).dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(22.dp))
        ) {
            com.kexora.soniclet.ui.components.library.DynamicPlaylistCover(
                songs = songs,
                defaultIcon = smartIcon ?: Icons.Default.LibraryMusic,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (collapseFraction < 0.85f) {
            Spacer(modifier = Modifier.height((12 * (1f - collapseFraction)).dp))

            // Title & Subtitle Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - collapseFraction * 1.5f).coerceIn(0f, 1f)
                }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "$songsCount tracks${if (totalDurationMs > 0) " • $totalDurationStr" else ""}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height((12 * (1f - collapseFraction)).dp))
        }

        // Clean Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (filteredSongs.isNotEmpty()) onPlayQueue(filteredSongs, 0, title) },
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 22.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play All", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }

            Spacer(modifier = Modifier.width(10.dp))

            FilledTonalIconButton(
                onClick = {
                    val shuffled = filteredSongs.shuffled()
                    if (shuffled.isNotEmpty()) onPlayQueue(shuffled, 0, title)
                },
                modifier = Modifier.size(38.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(19.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
