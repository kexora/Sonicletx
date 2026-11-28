package com.kexora.soniclet.library

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.kexora.soniclet.permissions.StoragePermissionHelper
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.common.DropContext
import com.kexora.soniclet.ui.components.common.DropContextItem
import com.kexora.soniclet.ui.components.common.EmptyCategoryState
import com.kexora.soniclet.ui.components.common.FileOrganizerDialog
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.utils.fuzzyMatch
import java.io.File
import java.util.Locale

enum class FolderSort {
    NAME_ASC, NAME_DESC, TRACKS_COUNT, DURATION
}

@Composable
fun Folders(
    discoveredFolders: List<FolderInfo>,
    allSongs: List<Song>,
    searchQuery: String = "",
    isGridMode: Boolean = false,
    viewModel: MusicViewModel? = null,
    onExcludeFolder: (String) -> Unit,
    onRemoveManualFolder: (String) -> Unit = {},
    manualFolderUris: Set<String> = emptySet(),
    onAddMissingFolder: (Uri) -> Unit = {},
    onPlayFolder: (String, String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onExcludeSong: (Long) -> Unit,
    isLoading: Boolean = false,
    onInsertNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Navigation state: which folder is open
    var selectedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOrder by rememberSaveable { mutableStateOf(FolderSort.NAME_ASC) }
    var gridViewEnabled by rememberSaveable { mutableStateOf(isGridMode) }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var createParentPath by remember { mutableStateOf("") }
    var folderToRename by remember { mutableStateOf<FolderInfo?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderInfo?>(null) }
    var folderProperties by remember { mutableStateOf<FolderInfo?>(null) }

    // Transfer dialog states
    var transferSongs by remember { mutableStateOf<List<Song>?>(null) }
    var transferIsCopy by remember { mutableStateOf(false) }

    // Android 11+ storage permission check
    var hasStorageAccess by remember { mutableStateOf(StoragePermissionHelper.hasAllFilesAccess()) }
    var showStorageNotice by rememberSaveable { mutableStateOf(!hasStorageAccess) }

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onAddMissingFolder(it) }
    }

    // Refresh permission state on resume/interaction
    LaunchedEffect(Unit) {
        hasStorageAccess = StoragePermissionHelper.hasAllFilesAccess()
        if (hasStorageAccess) showStorageNotice = false
    }

    // Handle back button when drilled into a folder
    BackHandler(enabled = selectedFolderPath != null) {
        selectedFolderPath = null
    }

    val selectedFolder = remember(selectedFolderPath, discoveredFolders) {
        if (selectedFolderPath == null) null
        else discoveredFolders.find { it.path == selectedFolderPath }
            ?: FolderInfo(path = selectedFolderPath!!, name = File(selectedFolderPath!!).name, songCount = 0, isExcluded = false)
    }

    // Calculate durations per folder
    val folderDurations = remember(discoveredFolders, allSongs) {
        val map = mutableMapOf<String, Long>()
        discoveredFolders.forEach { folder ->
            val d = allSongs.filter { it.folderPath.startsWith(folder.path) }.sumOf { it.duration }
            map[folder.path] = d
        }
        map
    }

    // Filtered & sorted folders
    val displayedFolders = remember(discoveredFolders, searchQuery, sortOrder) {
        val visible = discoveredFolders.filter { !it.isExcluded }
        val filtered = if (searchQuery.isBlank()) visible
        else visible.filter { it.name.fuzzyMatch(searchQuery) || it.path.fuzzyMatch(searchQuery) }

        when (sortOrder) {
            FolderSort.NAME_ASC -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            FolderSort.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            FolderSort.TRACKS_COUNT -> filtered.sortedByDescending { it.songCount }
            FolderSort.DURATION -> filtered.sortedByDescending { folderDurations[it.path] ?: 0L }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedFolder != null) {
            // Inside a specific folder
            val folderSongs = remember(allSongs, selectedFolder) {
                allSongs.filter { it.folderPath.startsWith(selectedFolder.path) }
            }
            FolderSongsDetailView(
                folder = selectedFolder,
                songs = folderSongs,
                allDiscoveredFolders = discoveredFolders,
                searchQuery = searchQuery,
                viewModel = viewModel,
                onBack = { selectedFolderPath = null },
                onNavigateToFolder = { newPath -> selectedFolderPath = newPath },
                onPlaySong = { song -> onPlaySong(song, folderSongs) },
                onPlayFolder = { onPlayFolder(selectedFolder.path, selectedFolder.name) },
                onExcludeSong = onExcludeSong,
                onInsertNext = onInsertNext,
                onAddToQueue = onAddToQueue,
                onRenameFolder = { folderToRename = selectedFolder },
                onDeleteFolder = { folderToDelete = selectedFolder },
                onNewSubfolder = {
                    createParentPath = selectedFolder.path
                    showCreateDialog = true
                },
                onShowProperties = { folderProperties = selectedFolder },
                onTransferSongs = { songs, isCopy ->
                    transferSongs = songs
                    transferIsCopy = isCopy
                }
            )
        } else {
            // Root Folders screen
            val bottomPadding = LocalPlayerBottomPadding.current + 8.dp

            if (displayedFolders.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showStorageNotice && !hasStorageAccess) {
                        StorageAccessBanner(
                            onGrant = {
                                StoragePermissionHelper.requestAllFilesAccess(context)
                            },
                            onDismiss = { showStorageNotice = false }
                        )
                    }

                    FolderControlsBar(
                        folderCount = displayedFolders.size,
                        sortOrder = sortOrder,
                        onSortChanged = { sortOrder = it },
                        isGrid = gridViewEnabled,
                        onToggleGrid = { gridViewEnabled = !gridViewEnabled },
                        onRescan = { viewModel?.rescanStorage() },
                        onAddSaf = { documentTreeLauncher.launch(null) }
                    )

                    if (isLoading) {
                        com.kexora.soniclet.ui.components.common.SkeletonFoldersView()
                    } else {
                        EmptyCategoryState(
                            icon = Icons.Rounded.FolderOff,
                            title = if (searchQuery.isNotEmpty()) "No Folders Found" else "No Music Folders",
                            description = if (searchQuery.isNotEmpty()) 
                                "No folders match \"$searchQuery\"." 
                                else "Add folders from your storage to see them here."
                        )
                    }
                }
            } else {
                if (gridViewEnabled) {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollFade(gridState)
                    ) {
                        // SCROLLABLE CONTROLS HEADER (Scrolls with the grid!)
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }, key = "folder_header") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (showStorageNotice && !hasStorageAccess) {
                                    StorageAccessBanner(
                                        onGrant = {
                                            StoragePermissionHelper.requestAllFilesAccess(context)
                                        },
                                        onDismiss = { showStorageNotice = false }
                                    )
                                }

                                FolderControlsBar(
                                    folderCount = displayedFolders.size,
                                    sortOrder = sortOrder,
                                    onSortChanged = { sortOrder = it },
                                    isGrid = gridViewEnabled,
                                    onToggleGrid = { gridViewEnabled = !gridViewEnabled },
                                    onRescan = { viewModel?.rescanStorage() },
                                    onAddSaf = { documentTreeLauncher.launch(null) }
                                )
                            }
                        }

                        items(displayedFolders, key = { it.path }) { folder ->
                            ModernFolderGridCard(
                                folder = folder,
                                durationStr = formatFolderDuration(folderDurations[folder.path] ?: 0L),
                                onClick = { selectedFolderPath = folder.path },
                                onPlay = { onPlayFolder(folder.path, folder.name) },
                                onRename = { folderToRename = folder },
                                onDelete = { folderToDelete = folder },
                                onHide = {
                                    onExcludeFolder(folder.path)
                                    Toast.makeText(context, "Folder hidden", Toast.LENGTH_SHORT).show()
                                },
                                onMove = {
                                    val songs = allSongs.filter { it.folderPath.startsWith(folder.path) }
                                    if (songs.isNotEmpty()) {
                                        transferSongs = songs
                                        transferIsCopy = false
                                    } else {
                                        Toast.makeText(context, "Folder has no songs to move", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onProperties = { folderProperties = folder }
                            )
                        }

                        item(
                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            key = "folder_grid_brand_footer"
                        ) {
                            com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollFade(listState),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 2.dp, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // SCROLLABLE CONTROLS HEADER (Scrolls with the list, not sticky!)
                        item(key = "folder_header") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (showStorageNotice && !hasStorageAccess) {
                                    StorageAccessBanner(
                                        onGrant = {
                                            StoragePermissionHelper.requestAllFilesAccess(context)
                                        },
                                        onDismiss = { showStorageNotice = false }
                                    )
                                }

                                FolderControlsBar(
                                    folderCount = displayedFolders.size,
                                    sortOrder = sortOrder,
                                    onSortChanged = { sortOrder = it },
                                    isGrid = gridViewEnabled,
                                    onToggleGrid = { gridViewEnabled = !gridViewEnabled },
                                    onRescan = { viewModel?.rescanStorage() },
                                    onAddSaf = { documentTreeLauncher.launch(null) }
                                )
                            }
                        }

                        items(displayedFolders, key = { it.path }) { folder ->
                            ModernFolderListRow(
                                folder = folder,
                                durationStr = formatFolderDuration(folderDurations[folder.path] ?: 0L),
                                onClick = { selectedFolderPath = folder.path },
                                onPlay = { onPlayFolder(folder.path, folder.name) },
                                onRename = { folderToRename = folder },
                                onDelete = { folderToDelete = folder },
                                onHide = {
                                    onExcludeFolder(folder.path)
                                    Toast.makeText(context, "Folder hidden", Toast.LENGTH_SHORT).show()
                                },
                                onMove = {
                                    val songs = allSongs.filter { it.folderPath.startsWith(folder.path) }
                                    if (songs.isNotEmpty()) {
                                        transferSongs = songs
                                        transferIsCopy = false
                                    } else {
                                        Toast.makeText(context, "Folder has no songs to move", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onProperties = { folderProperties = folder }
                            )
                        }

                        item(key = "folder_list_brand_footer") {
                            com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                        }
                    }
                }
            }

            // Sleek Floating Action Button for creating a new folder
            FloatingActionButton(
                onClick = {
                    createParentPath = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MUSIC
                    ).absolutePath
                    showCreateDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = LocalPlayerBottomPadding.current + 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Rounded.CreateNewFolder,
                    contentDescription = "Create New Folder",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        CreateFolderDialog(
            parentPath = createParentPath,
            onDismiss = { showCreateDialog = false },
            onCreate = { folderName ->
                viewModel?.createFolder(createParentPath, folderName) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) showCreateDialog = false
                }
            }
        )
    }

    if (folderToRename != null) {
        RenameFolderDialog(
            currentFolder = folderToRename!!,
            onDismiss = { folderToRename = null },
            onRename = { newName ->
                val old = folderToRename!!
                viewModel?.renameFolder(old.path, newName) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        if (selectedFolderPath == old.path) {
                            val parent = File(old.path).parentFile?.absolutePath ?: old.path
                            selectedFolderPath = File(parent, newName).absolutePath
                        }
                        folderToRename = null
                    }
                }
            }
        )
    }

    if (folderToDelete != null) {
        DeleteFolderDialog(
            folder = folderToDelete!!,
            onDismiss = { folderToDelete = null },
            onConfirmDelete = {
                val target = folderToDelete!!
                viewModel?.deleteFolder(target.path) { success ->
                    if (success) {
                        Toast.makeText(context, "Folder deleted", Toast.LENGTH_SHORT).show()
                        if (selectedFolderPath == target.path) selectedFolderPath = null
                    } else {
                        Toast.makeText(context, "Could not delete folder. Check permissions.", Toast.LENGTH_SHORT).show()
                    }
                    folderToDelete = null
                }
            }
        )
    }

    if (folderProperties != null) {
        FolderPropertiesDialog(
            folder = folderProperties!!,
            allSongs = allSongs,
            onDismiss = { folderProperties = null }
        )
    }

    if (transferSongs != null && viewModel != null) {
        FileOrganizerDialog(
            songs = transferSongs!!,
            folders = discoveredFolders,
            isCopy = transferIsCopy,
            viewModel = viewModel,
            onDismiss = { transferSongs = null }
        )
    }
}

// -------------------------------------------------------------
// CONTROLS HEADER BAR
// -------------------------------------------------------------
@Composable
private fun FolderControlsBar(
    folderCount: Int,
    sortOrder: FolderSort,
    onSortChanged: (FolderSort) -> Unit,
    isGrid: Boolean,
    onToggleGrid: () -> Unit,
    onRescan: () -> Unit,
    onAddSaf: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Folders",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$folderCount",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Grid / List View Toggle
            IconButton(
                onClick = onToggleGrid,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = "Toggle View",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Sort Menu Button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = "Sort Folders",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropContext(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropContextItem(
                        text = "Name (A to Z)",
                        icon = Icons.Rounded.SortByAlpha,
                        iconTint = if (sortOrder == FolderSort.NAME_ASC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        index = 0,
                        onClick = { onSortChanged(FolderSort.NAME_ASC); showSortMenu = false }
                    )
                    DropContextItem(
                        text = "Name (Z to A)",
                        icon = Icons.Rounded.SortByAlpha,
                        iconTint = if (sortOrder == FolderSort.NAME_DESC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        index = 1,
                        onClick = { onSortChanged(FolderSort.NAME_DESC); showSortMenu = false }
                    )
                    DropContextItem(
                        text = "Most Tracks",
                        icon = Icons.Rounded.Numbers,
                        iconTint = if (sortOrder == FolderSort.TRACKS_COUNT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        index = 2,
                        onClick = { onSortChanged(FolderSort.TRACKS_COUNT); showSortMenu = false }
                    )
                    DropContextItem(
                        text = "Longest Duration",
                        icon = Icons.Rounded.Schedule,
                        iconTint = if (sortOrder == FolderSort.DURATION) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        index = 3,
                        onClick = { onSortChanged(FolderSort.DURATION); showSortMenu = false }
                    )
                }
            }

            // More Options (Scan & Add)
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropContext(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropContextItem(text = "Rescan Library", icon = Icons.Rounded.Refresh, index = 0, onClick = { onRescan(); showMoreMenu = false })
                    DropContextItem(text = "Add Storage Folder", icon = Icons.Rounded.FolderOpen, index = 1, onClick = { onAddSaf(); showMoreMenu = false })
                }
            }
        }
    }
}

@Composable
private fun StorageAccessBanner(onGrant: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.FolderSpecial,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Storage Management Access",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Enable access to organize and manage music folders directly.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onGrant,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Enable", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun ModernFolderGridCard(
    folder: FolderInfo,
    durationStr: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onMove: () -> Unit,
    onProperties: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modern circular folder artwork container (CircleShape) with floating play action
        Box(
            modifier = Modifier.size(92.dp),
            contentAlignment = Alignment.Center
        ) {
            // Circular Artwork Disc (CircleShape)
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            // Quick Play Floating Action Button (CircleShape on bottom right)
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play folder",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                val trackLabel = if (folder.songCount == 1) "1 track" else "${folder.songCount} tracks"
                Text(
                    text = "$trackLabel • $durationStr",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Options menu button
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropContext(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropContextItem(text = "Rename", icon = Icons.Rounded.Edit, index = 0, onClick = { onRename(); showMenu = false })
                    DropContextItem(text = "Move Songs", icon = Icons.Rounded.DriveFileMove, index = 1, onClick = { onMove(); showMenu = false })
                    DropContextItem(text = "Hide Folder", icon = Icons.Rounded.VisibilityOff, index = 2, onClick = { onHide(); showMenu = false })
                    DropContextItem(text = "Properties", icon = Icons.Rounded.Info, index = 3, onClick = { onProperties(); showMenu = false })
                    DropContextItem(text = "Delete Folder", icon = Icons.Rounded.Delete, iconTint = MaterialTheme.colorScheme.error, index = 4, onClick = { onDelete(); showMenu = false })
                }
            }
        }
    }
}

@Composable
private fun ModernFolderListRow(
    folder: FolderInfo,
    durationStr: String,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onMove: () -> Unit,
    onProperties: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Modern circular folder artwork container (CircleShape)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            val isRecordingFolder = folder.name.contains("record", ignoreCase = true) || folder.path.contains("record", ignoreCase = true) || folder.path.contains("call", ignoreCase = true)
            val isHiddenFolder = folder.path.contains("/.") || folder.name.startsWith(".") || folder.path.contains(".nomedia")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isRecordingFolder || isHiddenFolder) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isRecordingFolder) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF6366F1).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isRecordingFolder) "🎙️ Rec" else "🔒 Hidden",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isRecordingFolder) Color(0xFF10B981) else Color(0xFF6366F1),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val trackLabel = if (folder.songCount == 1) "1 track" else "${folder.songCount} tracks"
            Text(
                text = "$trackLabel • $durationStr",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Quick Play Button (ghost icon)
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Essential Actions 3-dot menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
            DropContext(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropContextItem(text = "Rename", icon = Icons.Rounded.Edit, index = 0, onClick = { onRename(); showMenu = false })
                DropContextItem(text = "Move Songs", icon = Icons.Rounded.DriveFileMove, index = 1, onClick = { onMove(); showMenu = false })
                DropContextItem(text = "Hide Folder", icon = Icons.Rounded.VisibilityOff, index = 2, onClick = { onHide(); showMenu = false })
                DropContextItem(text = "Properties", icon = Icons.Rounded.Info, index = 3, onClick = { onProperties(); showMenu = false })
                DropContextItem(text = "Delete Folder", icon = Icons.Rounded.Delete, iconTint = MaterialTheme.colorScheme.error, index = 4, onClick = { onDelete(); showMenu = false })
            }
        }
    }
}

