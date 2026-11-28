package com.kexora.soniclet.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.settings.formatReadableStoragePath
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.formatMillisToTime
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.fuzzyMatch
import kotlinx.coroutines.launch

/**
 * Box-free, edge-to-edge Hidden Items screen with instant library sync and 1-tap Undo support.
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
    onExcludeFolder: (String) -> Unit = {},
    onExcludeSong: (Long) -> Unit = {},
    isLoading: Boolean = false,
    onOpenSystemHiddenVault: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    val activeTabCount = if (currentTab == 0) filteredHiddenFolders.size else filteredHiddenSongs.size
    val bottomPadding = maxOf(110.dp, LocalPlayerBottomPadding.current + 16.dp)
    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndo()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sleek 36.dp CircleShape pill tabs + clear Unhide All pill button
            HiddenPillControlsRow(
                currentTab = currentTab,
                foldersCount = filteredHiddenFolders.size,
                songsCount = filteredHiddenSongs.size,
                showUnhideAll = activeTabCount > 0,
                onSelectTab = { currentTab = it },
                onRestoreAll = { showRestoreAllConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Box-free edge-to-edge Recordings & Hidden Vault link row
            if (onOpenSystemHiddenVault != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSystemHiddenVault() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("open_recordings_hidden_vault_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recordings & Hidden Vault",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "Browse call recordings, voice notes & hidden folders",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Open vault",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = hairlineColor
                )
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
                            if (filteredHiddenFolders.isEmpty()) {
                                EmptyVaultState(
                                    icon = Icons.Rounded.FolderOff,
                                    title = "No Hidden Folders",
                                    description = if (searchQuery.isNotEmpty()) {
                                        "No hidden folders match \"$searchQuery\"."
                                    } else {
                                        "Folders you hide from your library will appear here."
                                    }
                                )
                            } else {
                                val listState = rememberLazyListState()
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scrollFade(listState)
                                        .testTag("hidden_folders_list"),
                                    contentPadding = PaddingValues(bottom = bottomPadding)
                                ) {
                                    itemsIndexed(filteredHiddenFolders, key = { _, path -> path }) { index, path ->
                                        val cleanTarget = path.trimEnd('/')
                                        val count = remember(path, allSongs) {
                                            allSongs.count { song ->
                                                val cf = song.folderPath.trimEnd('/')
                                                val fp = song.filePath.trimEnd('/')
                                                cf == cleanTarget || cf.startsWith("$cleanTarget/") ||
                                                    fp == cleanTarget || fp.startsWith("$cleanTarget/")
                                            }
                                        }
                                        val folderName = path.substringAfterLast('/').ifBlank { "Folder" }

                                        FlatHiddenFolderItemRow(
                                            path = path,
                                            songsCount = count,
                                            onRestore = {
                                                onRestoreFolder(path)
                                                showUndoSnackbar("Unhid \"$folderName\"") {
                                                    onExcludeFolder(path)
                                                }
                                            }
                                        )

                                        if (index < filteredHiddenFolders.lastIndex) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = hairlineColor,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    }

                                    item(key = "hf_list_footer") {
                                        MadeInIndiaFooter()
                                    }
                                }
                            }
                        } else {
                            if (filteredHiddenSongs.isEmpty()) {
                                EmptyVaultState(
                                    icon = Icons.Rounded.MusicOff,
                                    title = "No Hidden Songs",
                                    description = if (searchQuery.isNotEmpty()) {
                                        "No hidden songs match \"$searchQuery\"."
                                    } else {
                                        "Songs you hide from your library will appear here."
                                    }
                                )
                            } else {
                                val listState = rememberLazyListState()
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scrollFade(listState)
                                        .testTag("hidden_songs_list"),
                                    contentPadding = PaddingValues(bottom = bottomPadding)
                                ) {
                                    itemsIndexed(filteredHiddenSongs, key = { _, song -> song.id }) { index, song ->
                                        FlatHiddenSongItemRow(
                                            song = song,
                                            onRestore = {
                                                onRestoreSong(song.id)
                                                showUndoSnackbar("Unhid \"${song.title}\"") {
                                                    onExcludeSong(song.id)
                                                }
                                            }
                                        )

                                        if (index < filteredHiddenSongs.lastIndex) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = hairlineColor,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
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

        // Undo Snackbar cleanly anchored above MiniPlayer
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = maxOf(108.dp, LocalPlayerBottomPadding.current + 12.dp))
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Confirmation Dialog for Unhide All
    if (showRestoreAllConfirmDialog) {
        val folderSnapshot = excludedFolders.toList()
        val songSnapshot = excludedSongIds.toList()
        val targetLabel = if (currentTab == 0) {
            val count = folderSnapshot.size
            if (count == 1) "1 hidden folder" else "all $count hidden folders"
        } else {
            val count = hiddenSongs.size
            if (count == 1) "1 hidden song" else "all $count hidden songs"
        }

        AlertDialog(
            onDismissRequest = { showRestoreAllConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Unhide All?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Show $targetLabel in your music library again?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreAllConfirmDialog = false
                        if (currentTab == 0) {
                            onRestoreAllFolders()
                            val count = folderSnapshot.size
                            val unit = if (count == 1) "folder" else "folders"
                            showUndoSnackbar("Unhid $count $unit") {
                                folderSnapshot.forEach { onExcludeFolder(it) }
                            }
                        } else {
                            onRestoreAllSongs()
                            val count = hiddenSongs.size
                            val unit = if (count == 1) "song" else "songs"
                            showUndoSnackbar("Unhid $count $unit") {
                                songSnapshot.forEach { onExcludeSong(it) }
                            }
                        }
                    },
                    shape = CircleShape
                ) {
                    Text("Unhide All", fontWeight = FontWeight.Bold)
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

@Composable
private fun HiddenPillControlsRow(
    currentTab: Int,
    foldersCount: Int,
    songsCount: Int,
    showUnhideAll: Boolean,
    onSelectTab: (Int) -> Unit,
    onRestoreAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inactiveBorder = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folders Pill (36.dp CircleShape)
            val foldersActive = currentTab == 0
            Surface(
                onClick = { onSelectTab(0) },
                shape = CircleShape,
                color = if (foldersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (foldersActive) null else inactiveBorder,
                modifier = Modifier
                    .height(36.dp)
                    .testTag("hidden_tab_folders")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = if (foldersActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Folders ($foldersCount)",
                        fontSize = 12.5.sp,
                        fontWeight = if (foldersActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (foldersActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Songs Pill (36.dp CircleShape)
            val songsActive = currentTab == 1
            Surface(
                onClick = { onSelectTab(1) },
                shape = CircleShape,
                color = if (songsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (songsActive) null else inactiveBorder,
                modifier = Modifier
                    .height(36.dp)
                    .testTag("hidden_tab_songs")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicOff,
                        contentDescription = null,
                        tint = if (songsActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Songs ($songsCount)",
                        fontSize = 12.5.sp,
                        fontWeight = if (songsActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (songsActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Clear Unhide All pill button (replaces ambiguous clock icon)
        if (showUnhideAll) {
            Surface(
                onClick = onRestoreAll,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("hidden_unhide_all_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = "Unhide all",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Unhide All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun FlatHiddenFolderItemRow(
    path: String,
    songsCount: Int,
    onRestore: () -> Unit
) {
    val name = path.substringAfterLast('/').ifBlank { "Folder" }
    val readableParent = remember(path) {
        val parentPath = path.substringBeforeLast('/', "")
        formatReadableStoragePath(parentPath)
    }
    val subtitle = when {
        songsCount == 1 -> "1 song • $readableParent"
        songsCount > 1 -> "$songsCount songs • $readableParent"
        else -> readableParent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRestore)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Crisp unmistakable Unhide pill button
        Surface(
            onClick = onRestore,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier
                .height(32.dp)
                .testTag("unhide_folder_button")
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Unhide",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun FlatHiddenSongItemRow(
    song: Song,
    onRestore: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRestore)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (song.albumArtUri != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover art for ${song.title}",
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
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${formatMillisToTime(song.duration)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Crisp unmistakable Unhide pill button
        Surface(
            onClick = onRestore,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier
                .height(32.dp)
                .testTag("unhide_song_button")
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Unhide",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun EmptyVaultState(
    icon: ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
