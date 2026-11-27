package com.kexora.soniclet.library

import androidx.compose.foundation.BorderStroke
import com.kexora.soniclet.database.*
import com.kexora.soniclet.player.*
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.tools.*
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

import androidx.compose.ui.draw.alpha

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import com.kexora.soniclet.player.SortOption
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items



import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.kexora.soniclet.database.Song
import androidx.compose.material.icons.filled.Favorite

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kexora.soniclet.ui.components.common.SongItemRow
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SongsLibraryView(
    uiState: MusicUiState,
    viewModel: MusicViewModel
) {

    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showOrganizerDialog by remember { mutableStateOf<Song?>(null) }
    var isCopyMode by remember { mutableStateOf(false) }


    if (songToAddToPlaylist != null && !showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { songToAddToPlaylist = null },
            title = { Text("Add to Playlist", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        TextButton(
                            onClick = {
                                showCreatePlaylistDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Playlist")
                        }
                    }
                    items(uiState.playlists) { playlist ->
                        TextButton(
                            onClick = {
                                viewModel.addSongsToPlaylist(playlist.id, listOf(songToAddToPlaylist!!))
                                songToAddToPlaylist = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(playlist.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songToAddToPlaylist = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var newPlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank() && songToAddToPlaylist != null) {
                            viewModel.createPlaylist(newPlaylistName, listOf(songToAddToPlaylist!!))
                            showCreatePlaylistDialog = false
                            songToAddToPlaylist = null
                        }
                    }
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    var songToDelete by remember { mutableStateOf<Song?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    if (showOrganizerDialog != null) {
        com.kexora.soniclet.ui.components.common.FileOrganizerDialog(
            song = showOrganizerDialog!!,
            folders = uiState.discoveredFolders,
            isCopy = isCopyMode,
            viewModel = viewModel,
            onDismiss = { showOrganizerDialog = null }
        )
    }

    if (songToDelete != null) {
        val song = songToDelete!!
        com.kexora.soniclet.ui.components.common.SonicletAlertDialog(
            onDismissRequest = { songToDelete = null },
            title = "Delete Song?",
            message = "Are you sure you want to permanently delete '${song.title}' from your device? This action cannot be undone.",
            confirmButtonText = "Delete",
            dismissButtonText = "Cancel",
            isDestructive = true,
            icon = Icons.Rounded.DeleteOutline,
            onConfirm = {
                songToDelete = null
                coroutineScope.launch {
                    val success = viewModel.deleteSongPhysical(song)
                    if (success) {
                        android.widget.Toast.makeText(context, "Deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to delete. File may be protected.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    var showSortSheet by remember { mutableStateOf(false) }
    var showSongHeaderMenu by remember { mutableStateOf(false) }
    var songForDetailsModal by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Main Song List / Empty / Loading State
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (uiState.isGridMode) {
                        com.kexora.soniclet.ui.components.common.SkeletonSongGrid()
                    } else {
                        com.kexora.soniclet.ui.components.common.SkeletonSongList()
                    }
                }
            }

            uiState.filteredSongList.isEmpty() -> {
                val isFilterActive = uiState.sortOption != SortOption.TIME_ADDED ||
                        uiState.sortAscending ||
                        uiState.formatFilter != "ALL" ||
                        uiState.durationCategoryFilter != "ALL" ||
                        uiState.attributeFilter != "ALL" ||
                        uiState.searchQuery.isNotEmpty()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Top Action Bar so user can always open Filter / Sort
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "0 Tracks",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Surface(
                            onClick = { showSortSheet = true },
                            shape = CircleShape,
                            color = if (isFilterActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isFilterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Sort & Filter",
                                    tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(19.dp)
                                )
                                if (isFilterActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-3).dp, y = 3.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uiState.searchQuery.isNotEmpty()) Icons.Rounded.SearchOff else Icons.Rounded.FilterAltOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty())
                                    "No tracks match \"${uiState.searchQuery}\""
                                else if (isFilterActive)
                                    "No tracks match active filters"
                                else
                                    "No local music found (>${uiState.minDurationMs / 1000}s)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isFilterActive)
                                    "Try clearing or adjusting your active filters to see your tracks."
                                else
                                    "Check Folder Exclusions in Settings or add audio files to device storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (isFilterActive) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewModel.resetAllSongFilters() },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reset All Filters", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    }

                                    FilledTonalButton(
                                        onClick = { showSortSheet = true },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Adjust Filters", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val listState = rememberLazyListState()
                val density = LocalDensity.current
                val haptic = LocalHapticFeedback.current
                val coroutineScope = rememberCoroutineScope()
                
                val onToggleFavoriteSongLambda: (Song) -> Unit = remember(viewModel) { { song -> viewModel.toggleFavorite(song.id) } }
                val onInsertNextSongLambda: (Song) -> Unit = remember(viewModel) { { song -> viewModel.insertNext(song) } }
                val onAddToQueueSongLambda: (Song) -> Unit = remember(viewModel) { { song -> viewModel.addToQueue(song) } }
                val onAddToPlaylistSongLambda: (Song) -> Unit = remember { { song -> songToAddToPlaylist = song } }
                val onHideSongItemLambda: (Song) -> Unit = remember(viewModel) { { song -> viewModel.excludeSong(song.id) } }
                val onMoveSongItemLambda: (Song) -> Unit = remember { { song ->
                    isCopyMode = false
                    showOrganizerDialog = song
                } }
                val onCopySongItemLambda: (Song) -> Unit = remember { { song ->
                    isCopyMode = true
                    showOrganizerDialog = song
                } }
                val onDeleteSongItemLambda: (Song) -> Unit = remember { { song -> songToDelete = song } }
                val onOpenSongDetailsItemLambda: (Song) -> Unit = remember(viewModel) { { song -> viewModel.openSongDetails(song) } }
                val onTrimAudioItemLambda: (Song) -> Unit = remember(viewModel) { { song -> viewModel.openTrimDialog(song) } }
                val onShareSongItemLambda: (Song) -> Unit = remember { { song ->
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
                } }

                val currentPlayingSongId by rememberUpdatedState(uiState.currentSong?.id)

                val onSongClickCallback: (Song) -> Unit = remember(viewModel, uiState.selectedSongIds, currentPlayingSongId) {
                    { selectedSong ->
                        if (uiState.selectedSongIds.isNotEmpty()) {
                            viewModel.toggleSongSelection(selectedSong.id)
                        } else if (selectedSong.id == currentPlayingSongId) {
                            viewModel.togglePlayPause()
                        } else {
                            viewModel.playSong(selectedSong, "All Tracks")
                        }
                    }
                }

                val onSongLongClickCallback: (Song) -> Unit = remember(viewModel) {
                    { selectedSong ->
                        viewModel.toggleSongSelection(selectedSong.id)
                    }
                }

                val totalSongs = uiState.filteredSongList.size

                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val specialDate = if (uiState.isMohModeActivated) {
                            uiState.todaySpecialDate ?: remember {
                                com.kexora.soniclet.easteregg.MuhLoveRegistry.getTodaySpecialDate()
                            }
                        } else null

                        if (uiState.isGridMode) {
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                state = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
                                modifier = Modifier.fillMaxSize().testTag("song_list_lazy_grid"),
                                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (uiState.isMohModeActivated && specialDate != null) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                        com.kexora.soniclet.easteregg.MuhLoveCard(
                                            dateInfo = specialDate,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                }

                                 item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (uiState.filteredSongList.isNotEmpty()) {
                                                        viewModel.playQueue(uiState.filteredSongList, 0, "All Tracks")
                                                    }
                                                },
                                                modifier = Modifier.height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Play",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    if (uiState.filteredSongList.isNotEmpty()) {
                                                        viewModel.playSmartShuffled(uiState.filteredSongList, "All Tracks")
                                                    }
                                                },
                                                modifier = Modifier.height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = "Smart Shuffle", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Smart",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            
                                            FilledTonalButton(
                                                onClick = {
                                                    if (uiState.filteredSongList.isNotEmpty()) {
                                                        if (!uiState.isShuffleEnabled) {
                                                            viewModel.toggleShuffle()
                                                        }
                                                        val shuffled = uiState.filteredSongList.shuffled()
                                                        viewModel.playQueue(shuffled, 0, "All Tracks (Shuffle)")
                                                    }
                                                },
                                                modifier = Modifier.height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Shuffle",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        
                                        val isGridFilterActive = uiState.sortOption != SortOption.TIME_ADDED ||
                                                uiState.sortAscending ||
                                                uiState.formatFilter != "ALL" ||
                                                uiState.durationCategoryFilter != "ALL" ||
                                                uiState.attributeFilter != "ALL"

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                onClick = { showSortSheet = true },
                                                shape = CircleShape,
                                                color = if (isGridFilterActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                border = BorderStroke(1.dp, if (isGridFilterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Tune, 
                                                        contentDescription = "Sort & Filter",
                                                        tint = if (isGridFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                    if (isGridFilterActive) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = (-3).dp, y = 3.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.primary)
                                                        )
                                                    }
                                                }
                                            }

                                            Box {
                                                Surface(
                                                    onClick = { showSongHeaderMenu = true },
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.MoreVert,
                                                            contentDescription = "More Options",
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                                DropContext(
                                                    expanded = showSongHeaderMenu,
                                                    onDismissRequest = { showSongHeaderMenu = false }
                                                ) {
                                                    DropContextItem(
                                                        text = if (uiState.selectedSongIds.size == uiState.filteredSongList.size && uiState.filteredSongList.isNotEmpty()) "Deselect All" else "Select All (${uiState.filteredSongList.size})",
                                                        icon = Icons.Rounded.SelectAll,
                                                        index = 0,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            if (uiState.selectedSongIds.size == uiState.filteredSongList.size && uiState.filteredSongList.isNotEmpty()) {
                                                                viewModel.clearSelectedSongs()
                                                            } else {
                                                                viewModel.selectAllSongs(uiState.filteredSongList.map { it.id })
                                                            }
                                                        }
                                                    )
                                                    DropContextItem(
                                                        text = "Share Multiple Songs...",
                                                        icon = Icons.Rounded.Share,
                                                        index = 1,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            if (uiState.selectedSongIds.isEmpty()) {
                                                                if (uiState.filteredSongList.isNotEmpty()) {
                                                                    viewModel.toggleSongSelection(uiState.filteredSongList.first().id)
                                                                }
                                                                android.widget.Toast.makeText(context, "Select songs to share, then tap Share in floating bar", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                viewModel.shareSelectedSongs(context)
                                                            }
                                                        }
                                                    )
                                                    DropContextItem(
                                                        text = "Refresh Library",
                                                        icon = Icons.Rounded.Refresh,
                                                        index = 2,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            viewModel.rescanStorage()
                                                        }
                                                    )
                                                    DropContextItem(
                                                        text = "Reset Filters",
                                                        icon = Icons.Rounded.RestartAlt,
                                                        index = 3,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            viewModel.resetAllSongFilters()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                items(
                                    items = uiState.filteredSongList,
                                    key = { it.id }
                                ) { song ->
                                    val isSelected = song.id == uiState.currentSong?.id
                                    val isFavorite = uiState.favoriteSongIds.contains(song.id)
                                    val isMultiSelected = uiState.selectedSongIds.contains(song.id)

                                    SongGridItem(
                                        song = song,
                                        isSelected = isSelected || isMultiSelected,
                                        isPlaying = isSelected && uiState.isPlaying,
                                        isFavorite = isFavorite,
                                        onSongClick = onSongClickCallback,
                                        onSongLongClick = onSongLongClickCallback,
                                        onToggleFavoriteSong = onToggleFavoriteSongLambda,
                                        onInsertNextSong = onInsertNextSongLambda,
                                        onAddToQueueSong = onAddToQueueSongLambda,
                                        onAddToPlaylistSong = onAddToPlaylistSongLambda,
                                        onShareSong = onShareSongItemLambda,
                                        onTrimAudioItem = onTrimAudioItemLambda,
                                        onOpenSongDetailsItem = onOpenSongDetailsItemLambda,
                                        onDeleteSongItem = onDeleteSongItemLambda
                                    )
                                }

                                item(
                                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                                    key = "song_grid_brand_footer"
                                ) {
                                    com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                                }
                            }
                        } else {
                            val resolvedSongsBottomPad = maxOf(
                                110.dp,
                                com.kexora.soniclet.LocalPlayerBottomPadding.current + 16.dp
                            )
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("song_list_lazy_column")
                                    .scrollFade(listState),
                                contentPadding = PaddingValues(
                                    start = 0.dp,
                                    end = 0.dp,
                                    top = 4.dp,
                                    bottom = resolvedSongsBottomPad
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (uiState.isMohModeActivated && specialDate != null) {
                                    item {
                                        com.kexora.soniclet.easteregg.MuhLoveCard(
                                            dateInfo = specialDate,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    if (uiState.filteredSongList.isNotEmpty()) {
                                                        viewModel.playQueue(uiState.filteredSongList, 0, "All Tracks")
                                                    }
                                                },
                                                modifier = Modifier.height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Play",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            FilledTonalButton(
                                                onClick = {
                                                    if (uiState.filteredSongList.isNotEmpty()) {
                                                        viewModel.playSmartShuffled(uiState.filteredSongList, "All Tracks")
                                                    }
                                                },
                                                modifier = Modifier.height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = "Smart Shuffle", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Smart",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            
                                            FilledTonalButton(
                                                onClick = {
                                                    if (uiState.filteredSongList.isNotEmpty()) {
                                                        if (!uiState.isShuffleEnabled) {
                                                            viewModel.toggleShuffle()
                                                        }
                                                        val shuffled = uiState.filteredSongList.shuffled()
                                                        viewModel.playQueue(shuffled, 0, "All Tracks (Shuffle)")
                                                    }
                                                },
                                                modifier = Modifier.height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Shuffle",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        
                                        val isListFilterActive = uiState.sortOption != SortOption.TIME_ADDED ||
                                                uiState.sortAscending ||
                                                uiState.formatFilter != "ALL" ||
                                                uiState.durationCategoryFilter != "ALL" ||
                                                uiState.attributeFilter != "ALL"

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                onClick = { showSortSheet = true },
                                                shape = CircleShape,
                                                color = if (isListFilterActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                border = BorderStroke(1.dp, if (isListFilterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Tune, 
                                                        contentDescription = "Sort & Filter",
                                                        tint = if (isListFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                    if (isListFilterActive) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .align(Alignment.TopEnd)
                                                                .offset(x = (-3).dp, y = 3.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.primary)
                                                        )
                                                    }
                                                }
                                            }

                                            Box {
                                                Surface(
                                                    onClick = { showSongHeaderMenu = true },
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.MoreVert,
                                                            contentDescription = "More Options",
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }
                                                }
                                                DropContext(
                                                    expanded = showSongHeaderMenu,
                                                    onDismissRequest = { showSongHeaderMenu = false }
                                                ) {
                                                    DropContextItem(
                                                        text = if (uiState.selectedSongIds.size == uiState.filteredSongList.size && uiState.filteredSongList.isNotEmpty()) "Deselect All" else "Select All (${uiState.filteredSongList.size})",
                                                        icon = Icons.Rounded.SelectAll,
                                                        index = 0,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            if (uiState.selectedSongIds.size == uiState.filteredSongList.size && uiState.filteredSongList.isNotEmpty()) {
                                                                viewModel.clearSelectedSongs()
                                                            } else {
                                                                viewModel.selectAllSongs(uiState.filteredSongList.map { it.id })
                                                            }
                                                        }
                                                    )
                                                    DropContextItem(
                                                        text = "Share Multiple Songs...",
                                                        icon = Icons.Rounded.Share,
                                                        index = 1,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            if (uiState.selectedSongIds.isEmpty()) {
                                                                if (uiState.filteredSongList.isNotEmpty()) {
                                                                    viewModel.toggleSongSelection(uiState.filteredSongList.first().id)
                                                                }
                                                                android.widget.Toast.makeText(context, "Select songs to share, then tap Share in floating bar", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                viewModel.shareSelectedSongs(context)
                                                            }
                                                        }
                                                    )
                                                    DropContextItem(
                                                        text = "Refresh Library",
                                                        icon = Icons.Rounded.Refresh,
                                                        index = 2,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            viewModel.rescanStorage()
                                                        }
                                                    )
                                                    DropContextItem(
                                                        text = "Reset Filters",
                                                        icon = Icons.Rounded.RestartAlt,
                                                        index = 3,
                                                        onClick = {
                                                            showSongHeaderMenu = false
                                                            viewModel.resetAllSongFilters()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                items(
                                    items = uiState.filteredSongList,
                                    key = { it.id },
                                    contentType = { "song_row" }
                                ) { song ->
                                    val isSelected = song.id == uiState.currentSong?.id
                                    val isFavorite = uiState.favoriteSongIds.contains(song.id)
                                    val isMultiSelected = uiState.selectedSongIds.contains(song.id)
                                    SongItemRow(
                                        song = song,
                                        isSelected = isSelected,
                                        isPlaying = isSelected && uiState.isPlaying,
                                        isFavorite = isFavorite,
                                        isMultiSelected = isMultiSelected,
                                        isMultiSelectMode = uiState.selectedSongIds.isNotEmpty(),
                                        savedPosition = uiState.playbackPositions[song.id] ?: 0L,
                                        onSongClick = onSongClickCallback,
                                        onSongLongClick = onSongLongClickCallback,
                                        onToggleFavoriteSong = onToggleFavoriteSongLambda,
                                        onInsertNextSong = onInsertNextSongLambda,
                                        onAddToQueueSong = onAddToQueueSongLambda,
                                        onAddToPlaylistSong = onAddToPlaylistSongLambda,
                                        onHideSongItem = onHideSongItemLambda,
                                        onMoveSongItem = onMoveSongItemLambda,
                                        onCopySongItem = onCopySongItemLambda,
                                        onDeleteSongItem = onDeleteSongItemLambda,
                                        onOpenSongDetailsItem = onOpenSongDetailsItemLambda,
                                        onTrimAudioItem = onTrimAudioItemLambda,
                                        onShareSong = onShareSongItemLambda
                                    )
                                }

                                item(key = "song_list_brand_footer") {
                                    com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                                }
                            } // End LazyColumn
                        }
                    } // End Box wrapping LazyColumn/Grid
                } // End MultiSelect Column
            }
        }
    }

    if (showSortSheet) {
        com.kexora.soniclet.ui.components.library.SongFilterSortSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showSortSheet = false }
        )
    }
}



@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SongGridItem(
    song: Song,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onToggleFavoriteSong: ((Song) -> Unit)? = null,
    onInsertNextSong: ((Song) -> Unit)? = null,
    onAddToQueueSong: ((Song) -> Unit)? = null,
    onAddToPlaylistSong: ((Song) -> Unit)? = null,
    onShareSong: ((Song) -> Unit)? = null,
    onTrimAudioItem: ((Song) -> Unit)? = null,
    onOpenSongDetailsItem: ((Song) -> Unit)? = null,
    onDeleteSongItem: ((Song) -> Unit)? = null
) {
    var showGridMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Transparent)
            .combinedClickable(
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
                if (song.albumArtUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover art for ${song.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                        }
                    )
                } else {
                    FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                }
                if (isPlaying) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                        AnimatedEQIcon()
                    }
                }

                // 3-Dot Overflow Menu Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { showGridMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropContext(
                        expanded = showGridMenu,
                        onDismissRequest = { showGridMenu = false }
                    ) {
                        if (onInsertNextSong != null) {
                            DropContextItem(
                                text = "Play Next",
                                icon = Icons.Rounded.PlaylistPlay,
                                index = 0,
                                onClick = {
                                    showGridMenu = false
                                    onInsertNextSong(song)
                                }
                            )
                        }
                        if (onAddToQueueSong != null) {
                            DropContextItem(
                                text = "Add to Queue",
                                icon = Icons.Rounded.QueueMusic,
                                index = 1,
                                onClick = {
                                    showGridMenu = false
                                    onAddToQueueSong(song)
                                }
                            )
                        }
                        if (onToggleFavoriteSong != null) {
                            DropContextItem(
                                text = if (isFavorite) "Remove Favorite" else "Add to Favorite",
                                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                iconTint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                                index = 2,
                                onClick = {
                                    showGridMenu = false
                                    onToggleFavoriteSong(song)
                                }
                            )
                        }
                        if (onAddToPlaylistSong != null) {
                            DropContextItem(
                                text = "Add to Playlist",
                                icon = Icons.Rounded.PlaylistAdd,
                                index = 3,
                                onClick = {
                                    showGridMenu = false
                                    onAddToPlaylistSong(song)
                                }
                            )
                        }
                        if (onShareSong != null) {
                            DropContextItem(
                                text = "Share Song",
                                icon = Icons.Rounded.Share,
                                index = 4,
                                onClick = {
                                    showGridMenu = false
                                    onShareSong(song)
                                }
                            )
                        }
                        if (onTrimAudioItem != null) {
                            DropContextItem(
                                text = "Trim & Set Ringtone",
                                icon = Icons.Rounded.ContentCut,
                                index = 5,
                                onClick = {
                                    showGridMenu = false
                                    onTrimAudioItem(song)
                                }
                            )
                        }
                        if (onOpenSongDetailsItem != null) {
                            DropContextItem(
                                text = "Song Info & Edit",
                                icon = Icons.Rounded.Info,
                                index = 6,
                                onClick = {
                                    showGridMenu = false
                                    onOpenSongDetailsItem(song)
                                }
                            )
                        }
                        if (onDeleteSongItem != null) {
                            DropContextItem(
                                text = "Delete",
                                icon = Icons.Rounded.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                index = 7,
                                onClick = {
                                    showGridMenu = false
                                    onDeleteSongItem(song)
                                }
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = song.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp).padding(start = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
