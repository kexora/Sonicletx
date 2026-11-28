package com.kexora.soniclet.library

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.FolderInfo
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.common.DropContext
import com.kexora.soniclet.ui.components.common.DropContextItem
import com.kexora.soniclet.ui.components.common.SongItemRow
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.utils.fuzzyMatch

@Composable
fun FolderSongsDetailView(
    folder: FolderInfo,
    songs: List<Song>,
    allDiscoveredFolders: List<FolderInfo>,
    searchQuery: String,
    viewModel: MusicViewModel?,
    onBack: () -> Unit,
    onNavigateToFolder: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayFolder: () -> Unit,
    onExcludeSong: (Long) -> Unit,
    onInsertNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
    onNewSubfolder: () -> Unit,
    onShowProperties: () -> Unit,
    onTransferSongs: (List<Song>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val uiState = viewModel?.uiState?.collectAsState()?.value ?: remember { MusicUiState() }
    val selectedSongIds = uiState.selectedSongIds
    val isMultiSelectMode = selectedSongIds.isNotEmpty()
    var localSearch by rememberSaveable { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Breadcrumbs path segments: e.g. /storage/emulated/0/Music/Rock -> [Music, Rock]
    val pathSegments = remember(folder.path) {
        val rootPrefix = "/storage/emulated/0/"
        val clean = if (folder.path.startsWith(rootPrefix)) folder.path.removePrefix(rootPrefix) else folder.path
        val parts = clean.split("/").filter { it.isNotBlank() }
        parts.mapIndexed { index, name ->
            val accumulated = if (folder.path.startsWith(rootPrefix)) {
                rootPrefix + parts.take(index + 1).joinToString("/")
            } else {
                "/" + parts.take(index + 1).joinToString("/")
            }
            Pair(name, accumulated)
        }
    }

    // Identify child subfolders if any
    val subfolders = remember(allDiscoveredFolders, folder.path) {
        allDiscoveredFolders.filter { other ->
            other.path != folder.path && other.path.startsWith(folder.path + "/")
        }
    }

    val displayedSongs = remember(songs, localSearch, searchQuery) {
        val q = if (localSearch.isNotBlank()) localSearch else searchQuery
        if (q.isBlank()) songs
        else songs.filter {
            it.title.fuzzyMatch(q) || it.artist.fuzzyMatch(q) || it.album.fuzzyMatch(q)
        }
    }

    val totalDurationMs = remember(songs) { songs.sumOf { it.duration } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Modern Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detailTrackLabel = if (songs.size == 1) "1 track" else "${songs.size} tracks"
                Text(
                    text = "$detailTrackLabel • ${formatFolderDuration(totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Move / Copy Quick Buttons when multi-selecting
            if (isMultiSelectMode) {
                IconButton(
                    onClick = {
                        val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
                        onTransferSongs(selectedSongs, false)
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DriveFileMove,
                        contentDescription = "Move Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(
                    onClick = {
                        val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
                        onTransferSongs(selectedSongs, true)
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy Selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))
            }

            // Multi-Select Toggle Button
            IconButton(
                onClick = {
                    if (isMultiSelectMode) {
                        viewModel?.clearSelectedSongs()
                    } else {
                        viewModel?.selectAllSongs(displayedSongs.map { it.id })
                    }
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isMultiSelectMode) Icons.Rounded.Check else Icons.Rounded.Checklist,
                    contentDescription = "Select Tracks",
                    tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Essential Actions Dropdown Menu
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Folder Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropContext(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    if (isMultiSelectMode) {
                        DropContextItem(
                            text = "Move Selected (${selectedSongIds.size})",
                            icon = Icons.Rounded.DriveFileMove,
                            index = 0,
                            onClick = {
                                val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
                                onTransferSongs(selectedSongs, false)
                                showMoreMenu = false
                            }
                        )
                        DropContextItem(
                            text = "Copy Selected (${selectedSongIds.size})",
                            icon = Icons.Rounded.ContentCopy,
                            index = 1,
                            onClick = {
                                val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
                                onTransferSongs(selectedSongs, true)
                                showMoreMenu = false
                            }
                        )
                    }
                    DropContextItem(text = "Rename Folder", icon = Icons.Rounded.Edit, index = 2, onClick = { onRenameFolder(); showMoreMenu = false })
                    DropContextItem(text = "New Subfolder", icon = Icons.Rounded.CreateNewFolder, index = 3, onClick = { onNewSubfolder(); showMoreMenu = false })
                    DropContextItem(text = "Folder Properties", icon = Icons.Rounded.Info, index = 4, onClick = { onShowProperties(); showMoreMenu = false })
                    DropContextItem(text = "Delete Folder", icon = Icons.Rounded.Delete, iconTint = MaterialTheme.colorScheme.error, index = 5, onClick = { onDeleteFolder(); showMoreMenu = false })
                }
            }
        }

        val bottomPadding = if (isMultiSelectMode && selectedSongIds.isNotEmpty()) {
            LocalPlayerBottomPadding.current + 72.dp
        } else {
            LocalPlayerBottomPadding.current + 8.dp
        }
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .scrollFade(listState),
            contentPadding = PaddingValues(top = 2.dp, bottom = bottomPadding)
        ) {
            // Breadcrumb Navigation Row (Scrolls naturally with list, no card borders)
            if (pathSegments.size > 1) {
                item(key = "breadcrumbs") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .clickable(onClick = onBack)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Folders",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        items(pathSegments) { (segmentName, segmentPath) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                val isLast = segmentPath == folder.path
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                        .clickable { if (!isLast) onNavigateToFolder(segmentPath) }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = segmentName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sleek Action Buttons Row (Play All & Shuffle - scrolls with content)
            if (songs.isNotEmpty()) {
                item(key = "play_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPlayFolder,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play All",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    val shuffled = songs.shuffled()
                                    onPlaySong(shuffled.first())
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Subfolders Row (if any - scrolls with content)
            if (subfolders.isNotEmpty()) {
                item(key = "subfolders") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(subfolders, key = { it.path }) { sub ->
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .clickable { onNavigateToFolder(sub.path) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // Tracks list or Empty State
            if (displayedSongs.isEmpty()) {
                item(key = "empty_state") {
                    EmptyFolderDetailState(
                        icon = if (localSearch.isNotBlank()) Icons.Rounded.SearchOff else Icons.Rounded.MusicNote,
                        title = if (localSearch.isNotBlank()) "No Results" else "Folder is Empty",
                        description = if (localSearch.isNotBlank()) "No tracks match \"$localSearch\"" else "There are no audio files in this directory."
                    )
                }
            } else {
                items(displayedSongs, key = { it.id }) { song ->
                    val isCurrentPlaying = song.id == uiState.currentSong?.id
                    val isMulti = selectedSongIds.contains(song.id)
                    val isFav = uiState.favoriteSongIds.contains(song.id)
                    SongItemRow(
                        song = song,
                        isSelected = isCurrentPlaying,
                        isPlaying = isCurrentPlaying && uiState.isPlaying,
                        isFavorite = isFav,
                        isMultiSelected = isMulti,
                        isMultiSelectMode = isMultiSelectMode,
                        savedPosition = uiState.playbackPositions[song.id] ?: 0L,
                        horizontalPadding = 12.dp,
                        verticalPadding = 3.dp,
                        onSongClick = {
                            if (isMultiSelectMode) {
                                viewModel?.toggleSongSelection(song.id)
                            } else {
                                onPlaySong(song)
                            }
                        },
                        onSongLongClick = {
                            viewModel?.toggleSongSelection(song.id)
                        },
                        onToggleFavoriteSong = {
                            viewModel?.toggleFavorite(song.id)
                        },
                        onInsertNext = { onInsertNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onMoveSongItem = { onTransferSongs(listOf(song), false) },
                        onCopySongItem = { onTransferSongs(listOf(song), true) },
                        onDeleteSongItem = {
                            viewModel?.deleteSong(song)
                            Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                        },
                        onHideSongItem = {
                            onExcludeSong(song.id)
                            Toast.makeText(context, "Song hidden", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            item(key = "folder_detail_brand_footer") {
                com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
            }
        }
    }
}

@Composable
private fun EmptyFolderDetailState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
