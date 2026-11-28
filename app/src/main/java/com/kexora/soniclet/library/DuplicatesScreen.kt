package com.kexora.soniclet.library

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.settings.formatReadableStoragePath
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.formatMillisToTime
import com.kexora.soniclet.utils.DuplicateGroup
import com.kexora.soniclet.utils.DuplicateScanMode
import com.kexora.soniclet.utils.DuplicateTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var scanMode by remember { mutableStateOf(DuplicateScanMode.SMART_MATCH) }
    var scanNonce by remember { mutableIntStateOf(0) }
    var isScanning by remember { mutableStateOf(true) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }

    val selectedSongIds = remember { mutableStateListOf<Long>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var pendingScopedDeleteSongIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    // Android 11+ MediaStore.createDeleteRequest consent launcher for Scoped Storage bulk delete
    val bulkDeleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pendingIds = pendingScopedDeleteSongIds
        pendingScopedDeleteSongIds = emptyList()
        isDeleting = false
        showDeleteConfirmDialog = false
        if (result.resultCode == Activity.RESULT_OK && pendingIds.isNotEmpty()) {
            selectedSongIds.removeAll(pendingIds.toSet())
            viewModel.onSongsExternallyDeleted(pendingIds)
            val unit = if (pendingIds.size == 1) "file" else "files"
            Toast.makeText(context, "Deleted ${pendingIds.size} duplicate $unit", Toast.LENGTH_SHORT).show()
            scanNonce++
        } else if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, "Deletion canceled", Toast.LENGTH_SHORT).show()
        }
    }

    // Run analysis off the main thread whenever library, mode, or rescan changes
    LaunchedEffect(uiState.allSongsList, scanMode, scanNonce) {
        isScanning = true
        val computed = withContext(Dispatchers.Default) {
            viewModel.analyzeDuplicateGroups(scanMode)
        }
        duplicateGroups = computed
        // Automatically pre-select ONLY the lower-quality redundant copies (keeping index 0 safe)
        selectedSongIds.clear()
        computed.forEach { group ->
            group.tracks.drop(1).forEach { redundant ->
                selectedSongIds.add(redundant.song.id)
            }
        }
        isScanning = false
    }

    val totalRedundantCount = remember(duplicateGroups) {
        duplicateGroups.sumOf { (it.tracks.size - 1).coerceAtLeast(0) }
    }
    val totalReclaimBytes = remember(duplicateGroups) {
        duplicateGroups.sumOf { it.potentialReclaimBytes }
    }
    val selectedReclaimBytes = remember(selectedSongIds.toList(), duplicateGroups) {
        val sizeById = duplicateGroups.flatMap { it.tracks }.associate { it.song.id to it.fileSize }
        selectedSongIds.sumOf { sizeById[it] ?: 0L }
    }

    fun executeDeleteSelected() {
        val allTracks = duplicateGroups.flatMap { it.tracks }.map { it.song }
        val songsToDelete = allTracks.filter { selectedSongIds.contains(it.id) }
        if (songsToDelete.isEmpty()) return

        // On Android 11+, request batch deletion via MediaStore.createDeleteRequest for content:// URIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contentUris = songsToDelete.mapNotNull { song ->
                song.contentUri.takeIf { it.scheme == "content" }
            }
            if (contentUris.isNotEmpty() && contentUris.size == songsToDelete.size) {
                try {
                    isDeleting = true
                    pendingScopedDeleteSongIds = songsToDelete.map { it.id }
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, contentUris)
                    bulkDeleteConsentLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                    return
                } catch (_: Exception) {
                    pendingScopedDeleteSongIds = emptyList()
                    // Fall through to physical delete loop below
                }
            }
        }

        coroutineScope.launch {
            isDeleting = true
            var deletedCount = 0
            for (song in songsToDelete) {
                if (viewModel.deleteSongPhysical(song)) {
                    deletedCount++
                }
            }
            selectedSongIds.clear()
            isDeleting = false
            showDeleteConfirmDialog = false

            if (deletedCount > 0) {
                val unit = if (deletedCount == 1) "file" else "files"
                Toast.makeText(context, "Deleted $deletedCount duplicate $unit", Toast.LENGTH_SHORT).show()
                scanNonce++
            } else {
                Toast.makeText(context, "Could not delete selected files", Toast.LENGTH_LONG).show()
            }
        }
    }

    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val pillBorder = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val miniPlayerClearance = maxOf(115.dp, LocalPlayerBottomPadding.current + 12.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Standard Transparent Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("duplicates_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Duplicate Cleaner",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Circular Rescan button
                Surface(
                    onClick = {
                        viewModel.loadAudioFiles(forceSync = true)
                        scanNonce++
                    },
                    enabled = !isScanning,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = pillBorder,
                    modifier = Modifier
                        .requiredSize(36.dp)
                        .testTag("duplicates_rescan_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Rescan duplicates",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Sleek CircleShape Mode Toggle Pill (Smart Match | Exact Files)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = pillBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DuplicateScanMode.values().forEach { mode ->
                        val active = scanMode == mode
                        Surface(
                            onClick = { scanMode = mode },
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("duplicates_mode_${mode.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = mode.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Box-Free Inline 3-Column Summary Strip
            HorizontalDivider(thickness = 0.5.dp, color = hairlineColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryMetricColumn(
                    label = "Groups",
                    value = "${duplicateGroups.size}",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(30.dp)
                        .background(hairlineColor)
                )
                SummaryMetricColumn(
                    label = "Extra Copies",
                    value = "$totalRedundantCount",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(30.dp)
                        .background(hairlineColor)
                )
                SummaryMetricColumn(
                    label = "Space to Free",
                    value = formatFileSize(totalReclaimBytes),
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.1f)
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = hairlineColor)

            // Main Body: Loading, Empty State, or Edge-to-Edge Duplicate Group List
            if (uiState.isLoading || isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(34.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (scanMode == DuplicateScanMode.SMART_MATCH) {
                                "Checking songs for duplicate copies..."
                            } else {
                                "Comparing file sizes and audio data..."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (duplicateGroups.isEmpty()) {
                // Clean Borderless Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp)
                        .padding(bottom = miniPlayerClearance * 0.5f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Duplicate Songs Found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All your songs are unique and clean.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(
                            onClick = {
                                viewModel.loadAudioFiles(forceSync = true)
                                scanNonce++
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = pillBorder,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("duplicates_scan_again_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Scan Again",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                val listBottomClearance = if (selectedSongIds.isNotEmpty()) {
                    miniPlayerClearance + 76.dp
                } else {
                    miniPlayerClearance
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("duplicates_group_list"),
                    contentPadding = PaddingValues(bottom = listBottomClearance)
                ) {
                    itemsIndexed(duplicateGroups, key = { _, group -> group.groupId }) { index, group ->
                        FlatDuplicateGroupSection(
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
                            }
                        )

                        if (index < duplicateGroups.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = hairlineColor
                            )
                        }
                    }

                    item(key = "duplicates_footer") {
                        MadeInIndiaFooter()
                    }
                }
            }
        }

        // Full-Pill Delete Selected Action Button cleanly above MiniPlayer (115.dp+ clearance)
        AnimatedVisibility(
            visible = selectedSongIds.isNotEmpty() && !isScanning,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = miniPlayerClearance)
        ) {
            val fileWord = if (selectedSongIds.size == 1) "file" else "files"
            Button(
                onClick = { showDeleteConfirmDialog = true },
                enabled = !isDeleting,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("duplicates_delete_selected_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Selected (${selectedSongIds.size} $fileWord • ${formatFileSize(selectedReclaimBytes)})",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Bulk Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val count = selectedSongIds.size
        val fileWord = if (count == 1) "file" else "files"
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete $count Duplicate ${if (count == 1) "File" else "Files"}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove $count extra $fileWord to free up ${formatFileSize(selectedReclaimBytes)}? Your best copy of each song will be kept safe.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { executeDeleteSelected() },
                    enabled = !isDeleting,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Delete", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

@Composable
private fun SummaryMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun FlatDuplicateGroupSection(
    group: DuplicateGroup,
    currentPlayingSongId: Long?,
    isPlaying: Boolean,
    selectedSongIds: List<Long>,
    onToggleSongSelection: (Long) -> Unit,
    onPlaySong: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        // Group Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.displayTitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${group.displayArtist} • ${formatMillisToTime(group.durationMs)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = group.matchReason.title,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Individual copies in this duplicate group
        group.tracks.forEachIndexed { idx, trackItem ->
            FlatDuplicateTrackRow(
                item = trackItem,
                isCurrentlyPlaying = currentPlayingSongId == trackItem.song.id && isPlaying,
                isSelectedForDeletion = selectedSongIds.contains(trackItem.song.id),
                onToggleSelect = { onToggleSongSelection(trackItem.song.id) },
                onPlay = { onPlaySong(trackItem.song) }
            )
            if (idx < group.tracks.lastIndex) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun FlatDuplicateTrackRow(
    item: DuplicateTrackItem,
    isCurrentlyPlaying: Boolean,
    isSelectedForDeletion: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val isBest = item.isBestQuality
    val folderDisplay = remember(item.song.folderPath, item.song.folderName) {
        if (item.song.folderPath.isNotBlank()) {
            formatReadableStoragePath(item.song.folderPath)
        } else {
            item.song.folderName.ifBlank { "Internal Storage" }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isBest -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                    isSelectedForDeletion -> MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = if (isBest) onPlay else onToggleSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Best Indicator or Selection Checkbox
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isBest) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Best copy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Checkbox(
                    checked = isSelectedForDeletion,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.error,
                        checkmarkColor = MaterialTheme.colorScheme.onError,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Spec Badge + Folder Location
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isBest) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Keep Best",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = item.qualityBadge,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = folderDisplay,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Play Preview Circle Button
        Surface(
            onClick = onPlay,
            shape = CircleShape,
            color = if (isCurrentlyPlaying) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.requiredSize(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isCurrentlyPlaying) "Pause preview" else "Play preview",
                    tint = if (isCurrentlyPlaying) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
