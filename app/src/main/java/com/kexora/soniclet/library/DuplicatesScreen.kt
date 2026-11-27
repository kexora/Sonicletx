package com.kexora.soniclet.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.formatMillisToTime
import com.kexora.soniclet.utils.DuplicateDetectorEngine
import com.kexora.soniclet.utils.DuplicateGroup
import com.kexora.soniclet.utils.DuplicateMatchReason
import com.kexora.soniclet.utils.DuplicateTrackItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsState()
    val allDuplicateGroups = remember(uiState.allSongsList) {
        viewModel.analyzeDuplicateGroups()
    }

    var selectedReasonFilter by remember { mutableStateOf<DuplicateMatchReason?>(null) }
    val filteredGroups = remember(allDuplicateGroups, selectedReasonFilter) {
        if (selectedReasonFilter == null) allDuplicateGroups
        else allDuplicateGroups.filter { it.matchReason == selectedReasonFilter }
    }

    // Selected song IDs for bulk deletion
    val selectedSongIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var singleSongToDelete by remember { mutableStateOf<Song?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Calculations
    val totalRedundantCount = remember(allDuplicateGroups) {
        allDuplicateGroups.sumOf { (it.tracks.size - 1).coerceAtLeast(0) }
    }
    val totalReclaimBytes = remember(allDuplicateGroups) {
        allDuplicateGroups.sumOf { it.potentialReclaimBytes }
    }

    // Selected reclaim bytes
    val selectedReclaimBytes = remember(selectedSongIds.toList(), allDuplicateGroups) {
        val allTrackItems = allDuplicateGroups.flatMap { it.tracks }
        val map = allTrackItems.associate { it.song.id to it.fileSize }
        selectedSongIds.sumOf { map[it] ?: 0L }
    }

    // Function to auto-select all redundant tracks
    fun autoSelectAllRedundant() {
        selectedSongIds.clear()
        for (group in filteredGroups) {
            // Keep the best quality track (tracks[0]), select the rest
            for (i in 1 until group.tracks.size) {
                selectedSongIds.add(group.tracks[i].song.id)
            }
        }
        if (selectedSongIds.isNotEmpty()) {
            Toast.makeText(context, "Selected ${selectedSongIds.size} redundant copies", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Duplicate Cleaner",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (allDuplicateGroups.isEmpty()) "Smart Storage Optimizer"
                            else "${allDuplicateGroups.size} duplicate groups • $totalRedundantCount copies",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (filteredGroups.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedSongIds.isNotEmpty()) {
                                    selectedSongIds.clear()
                                } else {
                                    autoSelectAllRedundant()
                                }
                            }
                        ) {
                            Text(
                                if (selectedSongIds.isNotEmpty()) "Deselect" else "Select All Copies",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedSongIds.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "${selectedSongIds.size} files selected",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Reclaims ${formatFileSize(selectedReclaimBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }

                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Copies", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                com.kexora.soniclet.ui.components.common.SkeletonSongList(
                    count = 8,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Stats Cards (Always present)
                    item(key = "stats_banner") {
                        DuplicateStatsBanner(
                            totalGroups = allDuplicateGroups.size,
                            totalRedundant = totalRedundantCount,
                            totalReclaimBytes = totalReclaimBytes
                        )
                    }

                    // Category Filter Chips (Always present so user can filter or see zero-counts)
                    if (allDuplicateGroups.isNotEmpty()) {
                        item(key = "filter_chips") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedReasonFilter == null,
                                        onClick = { selectedReasonFilter = null },
                                        label = { Text("All (${allDuplicateGroups.size})", fontSize = 12.sp) },
                                        leadingIcon = if (selectedReasonFilter == null) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null
                                    )
                                }
                                items(DuplicateMatchReason.values()) { reason ->
                                    val count = allDuplicateGroups.count { it.matchReason == reason }
                                    if (count > 0) {
                                        FilterChip(
                                            selected = selectedReasonFilter == reason,
                                            onClick = { selectedReasonFilter = if (selectedReasonFilter == reason) null else reason },
                                            label = { Text("${reason.title} ($count)", fontSize = 12.sp) },
                                            leadingIcon = if (selectedReasonFilter == reason) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Groups List or Empty State
                    if (allDuplicateGroups.isEmpty()) {
                        item(key = "empty_duplicates") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        "No Duplicates Found",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Your music library is clean and storage-optimized. No duplicate tracks or clones detected.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else if (filteredGroups.isEmpty()) {
                        item(key = "empty_filtered_duplicates") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Rounded.FilterListOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "No items in this category",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredGroups, key = { it.groupId }) { group ->
                            CleanDuplicateGroupCard(
                                group = group,
                                currentPlayingSongId = uiState.currentSong?.id,
                                isPlaying = uiState.isPlaying,
                                selectedSongIds = selectedSongIds,
                                onToggleSongSelection = { songId ->
                                    if (selectedSongIds.contains(songId)) {
                                        selectedSongIds.remove(songId)
                                    } else {
                                        selectedSongIds.add(songId)
                                    }
                                },
                                onPlaySong = { song ->
                                    if (uiState.currentSong?.id == song.id) {
                                        viewModel.togglePlayPause()
                                    } else {
                                        viewModel.playSong(song, songsContext = group.tracks.map { it.song })
                                    }
                                },
                                onDeleteSingle = { song ->
                                    singleSongToDelete = song
                                }
                            )
                        }
                    }

                    item(key = "duplicates_footer") {
                        MadeInIndiaFooter()
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Bulk Delete
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedSongIds.size} Duplicate Files?") },
            text = {
                Text(
                    "These redundant copies will be permanently deleted to free up ${formatFileSize(selectedReclaimBytes)} of storage. The best quality original tracks will be kept safely.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isDeleting = true
                            var deletedCount = 0
                            val allTracks = allDuplicateGroups.flatMap { it.tracks }.map { it.song }
                            val songsToDelete = allTracks.filter { selectedSongIds.contains(it.id) }

                            for (song in songsToDelete) {
                                val success = viewModel.deleteSongPhysical(song)
                                if (success) deletedCount++
                            }

                            selectedSongIds.clear()
                            isDeleting = false
                            showDeleteConfirmDialog = false

                            if (deletedCount > 0) {
                                Toast.makeText(context, "Successfully deleted $deletedCount duplicate files", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not delete files automatically. System storage permission required.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "Delete",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleting
                ) {
                    Text(
                        text = "Cancel",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }

    // Single Song Delete Confirmation Dialog
    if (singleSongToDelete != null) {
        val song = singleSongToDelete!!
        AlertDialog(
            onDismissRequest = { singleSongToDelete = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete This Copy?") },
            text = {
                Column {
                    Text(song.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Folder: ${song.folderName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(song.filePath, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = song
                        singleSongToDelete = null
                        coroutineScope.launch {
                            val success = viewModel.deleteSongPhysical(target)
                            if (success) {
                                Toast.makeText(context, "Deleted: ${target.title}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to delete file.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { singleSongToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Top Summary Stats Banner for Duplicates (Clean, without circle background clutter)
 */
@Composable
private fun DuplicateStatsBanner(
    totalGroups: Int,
    totalRedundant: Int,
    totalReclaimBytes: Long
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Smart Duplicate Analysis",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Safe detection via Audio Hash, Title, Bitrate & Duration",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge(
                    label = "Groups",
                    value = "$totalGroups",
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    label = "Redundant",
                    value = "$totalRedundant files",
                    modifier = Modifier.weight(1.2f)
                )
                StatBadge(
                    label = "Save Storage",
                    value = formatFileSize(totalReclaimBytes),
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.3f)
                )
            }
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = valueColor,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Clean & Simple Duplicate Group Card
 */
@Composable
private fun CleanDuplicateGroupCard(
    group: DuplicateGroup,
    currentPlayingSongId: Long?,
    isPlaying: Boolean,
    selectedSongIds: List<Long>,
    onToggleSongSelection: (Long) -> Unit,
    onPlaySong: (Song) -> Unit,
    onDeleteSingle: (Song) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.displayArtist} • ${formatMillisToTime(group.durationMs)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = group.matchReason.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tracks within group
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.tracks.forEach { trackItem ->
                    CleanDuplicateTrackRow(
                        item = trackItem,
                        isCurrentlyPlaying = currentPlayingSongId == trackItem.song.id && isPlaying,
                        isSelectedForDeletion = selectedSongIds.contains(trackItem.song.id),
                        onToggleSelect = { onToggleSongSelection(trackItem.song.id) },
                        onPlay = { onPlaySong(trackItem.song) },
                        onDelete = { onDeleteSingle(trackItem.song) }
                    )
                }
            }
        }
    }
}

/**
 * Individual track row inside a duplicate group
 */
@Composable
private fun CleanDuplicateTrackRow(
    item: DuplicateTrackItem,
    isCurrentlyPlaying: Boolean,
    isSelectedForDeletion: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val isBest = item.isBestQuality

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isBest) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        } else if (isSelectedForDeletion) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        border = if (isBest) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else if (isSelectedForDeletion) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (!isBest) onToggleSelect else onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Best Quality indicator OR Checkbox
            if (isBest) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = "Best Quality Original",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Checkbox(
                    checked = isSelectedForDeletion,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // File Information
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBest) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                "KEEP BEST",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = "${item.fileExtension} • ${item.formattedSize}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                        color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.song.folderName,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Actions: Play Preview & Delete Icon
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Preview",
                    tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (!isBest) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete Copy",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
