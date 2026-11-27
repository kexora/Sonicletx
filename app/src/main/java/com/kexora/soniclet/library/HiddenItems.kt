package com.kexora.soniclet.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.formatMillisToTime
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.fuzzyMatch

/**
 * Clean, lightweight Hidden Folders & Songs screen following the app's circular/pill design system.
 * The top pill header and menus stay permanently visible so switching between Folders and Songs is instant and smooth.
 */
@Composable
fun HiddenItems(
    excludedFolders: Set<String>,
    excludedSongIds: Set<Long>,
    allSongs: List<Song>,
    restoredAutoSongIds: Set<Long> = emptySet(),
    searchQuery: String = "",
    isGridMode: Boolean = false,
    onRestoreFolder: (String) -> Unit,
    onRestoreAllFolders: () -> Unit,
    onRestoreSong: (Long) -> Unit,
    onRestoreAllSongs: () -> Unit,
    isLoading: Boolean = false,
    onOpenSystemHiddenVault: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var gridViewEnabled by rememberSaveable { mutableStateOf(isGridMode) }
    var currentTab by rememberSaveable { mutableStateOf(0) } // 0: Folders, 1: Songs
    var showRestoreAllConfirmDialog by remember { mutableStateOf(false) }

    val hiddenSongs = remember(allSongs, excludedSongIds, excludedFolders, restoredAutoSongIds) {
        allSongs.filter { song ->
            if (excludedSongIds.contains(song.id)) return@filter true
            if (restoredAutoSongIds.contains(song.id)) return@filter false
            excludedFolders.any { excluded ->
                val cleanExcluded = excluded.trimEnd('/')
                val cleanFolder = song.folderPath.trimEnd('/')
                val cleanFile = song.filePath.trimEnd('/')
                cleanFolder == cleanExcluded || cleanFolder.startsWith("$cleanExcluded/") ||
                cleanFile == cleanExcluded || cleanFile.startsWith("$cleanExcluded/")
            }
        }
    }

    val filteredHiddenFolders = remember(excludedFolders, searchQuery) {
        if (searchQuery.isBlank()) excludedFolders.toList().sorted()
        else excludedFolders.filter { path ->
            val name = path.substringAfterLast('/')
            name.fuzzyMatch(searchQuery) || path.fuzzyMatch(searchQuery)
        }.toList().sorted()
    }

    val filteredHiddenSongs = remember(hiddenSongs, searchQuery) {
        if (searchQuery.isBlank()) hiddenSongs
        else hiddenSongs.filter {
            it.title.fuzzyMatch(searchQuery) ||
            it.artist.fuzzyMatch(searchQuery) ||
            it.album.fuzzyMatch(searchQuery)
        }
    }

    val totalHiddenCount = excludedFolders.size + hiddenSongs.size
    val bottomPadding = LocalPlayerBottomPadding.current + 8.dp

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // ALWAYS VISIBLE TOP PILL CONTROLS ROW (Never disappears when switching tabs or when a tab is empty)
        HiddenPillControlsRow(
            currentTab = currentTab,
            foldersCount = filteredHiddenFolders.size,
            songsCount = filteredHiddenSongs.size,
            isGrid = gridViewEnabled,
            showGridToggle = currentTab == 0 && filteredHiddenFolders.isNotEmpty(),
            totalHiddenCount = totalHiddenCount,
            onSelectTab = { currentTab = it },
            onToggleGrid = { gridViewEnabled = !gridViewEnabled },
            onRestoreAll = { showRestoreAllConfirmDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        // System Hidden & Recordings Vault Direct Shortcut Card
        if (onOpenSystemHiddenVault != null) {
            Surface(
                onClick = { onOpenSystemHiddenVault() },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF6366F1).copy(alpha = 0.5f), Color(0xFF10B981).copy(alpha = 0.5f))
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System Hidden & Recordings Vault",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Browse call recordings, voice notes & .nomedia files",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Dynamic Content Area (Smooth transition between Folders and Songs)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading) {
                com.kexora.soniclet.ui.components.common.SkeletonSongList(
                    count = 6,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(160))
                    },
                    label = "HiddenContentTabs"
                ) { tab ->
                    if (tab == 0) {
                        // FOLDERS TAB
                        if (filteredHiddenFolders.isEmpty()) {
                            EmptyVaultState(
                                icon = Icons.Rounded.FolderOff,
                                title = "No Hidden Folders",
                                description = if (searchQuery.isNotEmpty()) "No hidden folders match \"$searchQuery\"." else "You haven't hidden any folders. Excluded folders will appear here."
                            )
                        } else if (gridViewEnabled) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottomPadding),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredHiddenFolders, key = { it }) { path ->
                                    val count = remember(path, allSongs) {
                                        allSongs.count { it.folderPath == path || it.filePath.startsWith(path) }
                                    }
                                    CircularHiddenFolderGridCard(
                                        path = path,
                                        songsCount = count,
                                        onRestore = { onRestoreFolder(path) }
                                    )
                                }

                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, key = "hf_grid_footer") {
                                    MadeInIndiaFooter()
                                }
                            }
                        } else {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scrollFade(listState),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottomPadding),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredHiddenFolders, key = { it }) { path ->
                                    val count = remember(path, allSongs) {
                                        allSongs.count { it.folderPath == path || it.filePath.startsWith(path) }
                                    }
                                    CircularHiddenFolderListRow(
                                        path = path,
                                        songsCount = count,
                                        onRestore = { onRestoreFolder(path) }
                                    )
                                }

                                item(key = "hf_list_footer") {
                                    MadeInIndiaFooter()
                                }
                            }
                        }
                    } else {
                        // SONGS TAB
                        if (filteredHiddenSongs.isEmpty()) {
                            EmptyVaultState(
                                icon = Icons.Rounded.MusicOff,
                                title = "No Hidden Songs",
                                description = if (searchQuery.isNotEmpty()) "No hidden songs match \"$searchQuery\"." else "You haven't hidden any songs. Hidden individual tracks will appear here."
                            )
                        } else {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scrollFade(listState),
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottomPadding),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredHiddenSongs, key = { it.id }) { song ->
                                    CircularHiddenSongRow(
                                        song = song,
                                        onRestore = { onRestoreSong(song.id) }
                                    )
                                }

                                item(key = "hs_list_footer") {
                                    MadeInIndiaFooter()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Restore All
    if (showRestoreAllConfirmDialog) {
        val targetName = if (currentTab == 0) "all ${excludedFolders.size} hidden folders" else "all ${hiddenSongs.size} hidden songs"
        AlertDialog(
            onDismissRequest = { showRestoreAllConfirmDialog = false },
            icon = { Icon(Icons.Rounded.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unhide All Items?") },
            text = {
                Text(
                    "Are you sure you want to restore $targetName? They will immediately reappear in your library and player.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreAllConfirmDialog = false
                        if (currentTab == 0) onRestoreAllFolders() else onRestoreAllSongs()
                    },
                    shape = CircleShape
                ) {
                    Text("Unhide All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreAllConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Top Pill Control Row with Folders & Songs Filter Pills + Clean Action Icons
 * Simple, minimalist, no bulky circle background behind action icons.
 */
@Composable
private fun HiddenPillControlsRow(
    currentTab: Int,
    foldersCount: Int,
    songsCount: Int,
    isGrid: Boolean,
    showGridToggle: Boolean,
    totalHiddenCount: Int,
    onSelectTab: (Int) -> Unit,
    onToggleGrid: () -> Unit,
    onRestoreAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Pill Tabs: Folders vs Songs
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folders Pill
            Surface(
                onClick = { onSelectTab(0) },
                shape = CircleShape,
                color = if (currentTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                contentColor = if (currentTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOff,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Folders ($foldersCount)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Songs Pill
            Surface(
                onClick = { onSelectTab(1) },
                shape = CircleShape,
                color = if (currentTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                contentColor = if (currentTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicOff,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Songs ($songsCount)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        // Action Icons (Clean simple icons, NO circle bg behind icon)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showGridToggle) {
                IconButton(
                    onClick = onToggleGrid,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = "Toggle Grid/List",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (totalHiddenCount > 0) {
                IconButton(
                    onClick = onRestoreAll,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = "Unhide All",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modern Circular Folder List Row (Clean UI, simple unhide icon button)
 */
@Composable
private fun CircularHiddenFolderListRow(
    path: String,
    songsCount: Int,
    onRestore: () -> Unit
) {
    val name = path.substringAfterLast('/')
    val parent = path.substringBeforeLast('/', "Internal Storage")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .clickable(onClick = onRestore)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Disc icon
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (songsCount > 0) "$songsCount tracks • $parent" else parent,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Simple Unhide Icon Button (NO circle background behind icon)
        IconButton(
            onClick = onRestore,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = "Unhide Folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Modern Folder Grid Card (Clean UI, simple unhide icon button)
 */
@Composable
private fun CircularHiddenFolderGridCard(
    path: String,
    songsCount: Int,
    onRestore: () -> Unit
) {
    val name = path.substringAfterLast('/')
    val parent = path.substringBeforeLast('/', "Storage")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .clickable(onClick = onRestore)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (songsCount > 0) "$songsCount tracks" else parent,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Simple Unhide Icon Button
        IconButton(
            onClick = onRestore,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = "Unhide",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * Modern Circular Song Row for Hidden Songs (Clean UI, simple unhide icon button)
 */
@Composable
private fun CircularHiddenSongRow(
    song: Song,
    onRestore: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .clickable(onClick = onRestore)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular / Pill Album Art
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    error = { FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize()) }
                )
            } else {
                FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${formatMillisToTime(song.duration)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Simple Unhide Icon Button (NO circle background behind icon)
        IconButton(
            onClick = onRestore,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = "Unhide Song",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Clean Minimalist Empty State (No heavy circular background boxes)
 */
@Composable
private fun EmptyVaultState(
    icon: ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
