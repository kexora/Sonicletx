package com.kexora.soniclet.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.DatabaseProvider
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.library.SystemHiddenRepository
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.common.PlayingAudioWave
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HiddenFolderType(val label: String, val icon: ImageVector, val badgeColor: Color) {
    ALL("All Hidden", Icons.Rounded.FolderSpecial, Color(0xFF6366F1)),
    CALL_RECORDING("Call Recordings", Icons.Rounded.PhoneInTalk, Color(0xFF10B981)),
    VOICE_NOTE("Voice & Memos", Icons.Rounded.Mic, Color(0xFF06B6D4)),
    NOMEDIA(".nomedia Folders", Icons.Rounded.FolderOff, Color(0xFFEF4444)),
    DOT_FOLDER("Dot-Prefixed (.)", Icons.Rounded.VisibilityOff, Color(0xFFF59E0B))
}

enum class RecordingSortOption(val label: String, val icon: ImageVector) {
    DATE_NEWEST("Newest First", Icons.Rounded.Schedule),
    DATE_OLDEST("Oldest First", Icons.Rounded.History),
    NAME_AZ("Name (A-Z)", Icons.Rounded.SortByAlpha),
    TRACKS_COUNT("Most Tracks", Icons.Rounded.Audiotrack),
    SIZE_LARGEST("Largest Size", Icons.Rounded.Storage)
}

data class DiscoveredHiddenFolder(
    val path: String,
    val name: String,
    val type: HiddenFolderType,
    val hasNoMedia: Boolean,
    val isDotPrefixed: Boolean,
    val audioCount: Int,
    val totalSizeBytes: Long,
    val audioFiles: List<DiscoveredAudioFile>
)

data class DiscoveredAudioFile(
    val file: File,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val extension: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHiddenFoldersScreen(
    onBack: () -> Unit,
    onPlaySong: ((Song) -> Unit)? = null,
    viewModel: MusicViewModel? = null,
    onScanFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val systemHiddenRepo = remember(context) {
        val db = DatabaseProvider.getDatabase(context)
        SystemHiddenRepository(db.systemHiddenDao())
    }

    val cachedFolders by systemHiddenRepo.cachedHiddenFoldersFlow.collectAsState(initial = emptyList())
    val uiState by viewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    var isSyncingInBackground by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(HiddenFolderType.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(RecordingSortOption.DATE_NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var fileForDetails by remember { mutableStateOf<DiscoveredAudioFile?>(null) }

    fun syncScan(force: Boolean = false) {
        coroutineScope.launch {
            isSyncingInBackground = true
            systemHiddenRepo.syncSystemHiddenFolders(context, forceFullScan = force)
            isSyncingInBackground = false
            onScanFinished()
        }
    }

    LaunchedEffect(Unit) {
        syncScan(force = false)
    }

    fun createSongFromDiscoveredFile(folder: DiscoveredHiddenFolder, file: DiscoveredAudioFile): Song {
        val badge = when (folder.type) {
            HiddenFolderType.CALL_RECORDING -> "🎙️ Call Recordings - ${folder.name}"
            HiddenFolderType.VOICE_NOTE -> "🎙️ Voice Memos - ${folder.name}"
            HiddenFolderType.NOMEDIA -> "🔒 Hidden Folder - ${folder.name}"
            HiddenFolderType.DOT_FOLDER -> "🔒 System Hidden - ${folder.name}"
            else -> "🔒 System Hidden - ${folder.name}"
        }
        return Song(
            id = file.file.absolutePath.hashCode().toLong(),
            title = file.title,
            artist = folder.name,
            album = badge,
            duration = file.durationMs,
            contentUri = Uri.fromFile(file.file),
            albumArtUri = null,
            folderPath = folder.path,
            folderName = folder.name,
            filePath = file.file.absolutePath,
            dateAdded = file.file.lastModified() / 1000
        )
    }

    fun shareAudioFile(audioFile: DiscoveredAudioFile) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, audioFile.file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share: ${audioFile.title}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val discoveredFolders = cachedFolders
    val isScanning = cachedFolders.isEmpty() && isSyncingInBackground

    fun getSortedAudioFiles(files: List<DiscoveredAudioFile>): List<DiscoveredAudioFile> {
        return when (sortOption) {
            RecordingSortOption.DATE_NEWEST -> files.sortedByDescending { it.file.lastModified() }
            RecordingSortOption.DATE_OLDEST -> files.sortedBy { it.file.lastModified() }
            RecordingSortOption.NAME_AZ -> files.sortedBy { it.title.lowercase() }
            RecordingSortOption.TRACKS_COUNT -> files
            RecordingSortOption.SIZE_LARGEST -> files.sortedByDescending { it.sizeBytes }
        }
    }

    val filteredFolders = remember(discoveredFolders, selectedType, searchQuery, sortOption) {
        val filtered = discoveredFolders.mapNotNull { folder ->
            val matchesType = when (selectedType) {
                HiddenFolderType.ALL -> true
                else -> folder.type == selectedType ||
                        (selectedType == HiddenFolderType.NOMEDIA && folder.hasNoMedia) ||
                        (selectedType == HiddenFolderType.DOT_FOLDER && folder.isDotPrefixed)
            }
            if (!matchesType) return@mapNotNull null

            val matchingFiles = if (searchQuery.isBlank()) {
                getSortedAudioFiles(folder.audioFiles)
            } else {
                getSortedAudioFiles(folder.audioFiles.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.file.name.contains(searchQuery, ignoreCase = true)
                })
            }

            val matchesQuery = searchQuery.isBlank() ||
                    folder.name.contains(searchQuery, ignoreCase = true) ||
                    folder.path.contains(searchQuery, ignoreCase = true) ||
                    matchingFiles.isNotEmpty()

            if (matchesQuery) {
                folder.copy(audioFiles = matchingFiles)
            } else null
        }

        when (sortOption) {
            RecordingSortOption.NAME_AZ -> filtered.sortedBy { it.name.lowercase() }
            RecordingSortOption.TRACKS_COUNT -> filtered.sortedByDescending { it.audioCount }
            RecordingSortOption.SIZE_LARGEST -> filtered.sortedByDescending { it.totalSizeBytes }
            RecordingSortOption.DATE_NEWEST -> filtered.sortedByDescending { f -> f.audioFiles.maxOfOrNull { it.file.lastModified() } ?: 0L }
            RecordingSortOption.DATE_OLDEST -> filtered.sortedBy { f -> f.audioFiles.minOfOrNull { it.file.lastModified() } ?: 0L }
        }
    }

    val totalAudioFiles = remember(discoveredFolders) {
        discoveredFolders.sumOf { it.audioCount }
    }

    val currentSong = uiState?.currentSong
    val isCurrentlyPlaying = uiState?.isPlaying == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Controls Header Row (Sub-info stats, Search toggle, Sort menu, Rescan)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isScanning) "Scanning storage..." else "${discoveredFolders.size} Folders • $totalAudioFiles Audio Files",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search toggle button
                IconButton(
                    onClick = {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) searchQuery = ""
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSearchVisible || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sort Options Menu
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = sortOption.icon,
                            contentDescription = "Sort Options",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        Text(
                            text = "Sort Folders & Files",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        RecordingSortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    sortOption = option
                                    showSortMenu = false
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            )
                        }
                    }
                }

                // Rescan Storage Button
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        syncScan(force = true)
                    },
                    enabled = !isSyncingInBackground,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Rescan",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Optional Search Bar (Collapsible)
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically(tween(200)) + fadeIn(),
            exit = shrinkVertically(tween(180)) + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .height(50.dp),
                placeholder = {
                    Text(
                        "Search recordings, numbers, or names...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            )
        }

        // Category Filter Pills (Simple and clean)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HiddenFolderType.values().forEach { type ->
                val isSelected = selectedType == type
                val count = remember(discoveredFolders, type) {
                    when (type) {
                        HiddenFolderType.ALL -> discoveredFolders.size
                        HiddenFolderType.NOMEDIA -> discoveredFolders.count { it.hasNoMedia }
                        HiddenFolderType.DOT_FOLDER -> discoveredFolders.count { it.isDotPrefixed }
                        else -> discoveredFolders.count { it.type == type }
                    }
                }

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedType = type
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else type.badgeColor
                        )
                    },
                    label = {
                        Text(
                            text = "${type.label} ($count)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            )
                        )
                    },
                    shape = RoundedCornerShape(100.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Content Area
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isSyncingInBackground) "Scanning hidden folders & recordings..." else "Loading storage...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching recordings found" else "No hidden folders found",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try searching with a different term." else "All storage directories are visible in regular library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val bottomPadding = LocalPlayerBottomPadding.current + 16.dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)
            ) {
                items(filteredFolders, key = { it.path }) { folder ->
                    val isExpanded = expandedFolderPath == folder.path || searchQuery.isNotBlank()

                    val folderSongs = remember(folder) {
                        folder.audioFiles.map { file ->
                            createSongFromDiscoveredFile(folder, file)
                        }
                    }

                    DiscoveredHiddenFolderCard(
                        folder = folder,
                        isExpanded = isExpanded,
                        currentSongFilePath = currentSong?.filePath,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        onToggleExpand = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            expandedFolderPath = if (isExpanded) null else folder.path
                        },
                        onPlayAllFolder = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            if (folderSongs.isNotEmpty()) {
                                viewModel?.playQueue(folderSongs, 0, folder.name)
                                Toast.makeText(context, "Playing: ${folder.name}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onPlayFile = { audioFile ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val clickedIndex = folder.audioFiles.indexOfFirst { it.file.absolutePath == audioFile.file.absolutePath }.coerceAtLeast(0)
                            if (viewModel != null) {
                                viewModel.playQueue(folderSongs, clickedIndex, folder.name)
                            } else {
                                onPlaySong?.invoke(folderSongs[clickedIndex])
                            }
                        },
                        onPlayNext = { audioFile ->
                            val song = createSongFromDiscoveredFile(folder, audioFile)
                            viewModel?.insertNext(song)
                            Toast.makeText(context, "Playing next: ${audioFile.title}", Toast.LENGTH_SHORT).show()
                        },
                        onAddToQueue = { audioFile ->
                            val song = createSongFromDiscoveredFile(folder, audioFile)
                            viewModel?.addToQueue(song)
                            Toast.makeText(context, "Added to queue: ${audioFile.title}", Toast.LENGTH_SHORT).show()
                        },
                        onShareFile = { audioFile ->
                            shareAudioFile(audioFile)
                        },
                        onShowDetails = { audioFile ->
                            fileForDetails = audioFile
                        },
                        onRemoveNoMedia = {
                            coroutineScope.launch {
                                val removed = systemHiddenRepo.removeNoMedia(folder.path)
                                if (removed) {
                                    Toast.makeText(context, ".nomedia removed! Folder is now unhidden.", Toast.LENGTH_SHORT).show()
                                    syncScan(force = true)
                                } else {
                                    Toast.makeText(context, "Could not delete .nomedia (Permission denied)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Audio File Details Dialog
    if (fileForDetails != null) {
        val file = fileForDetails!!
        AlertDialog(
            onDismissRequest = { fileForDetails = null },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(file.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📍 Path: ${file.file.absolutePath}", style = MaterialTheme.typography.bodySmall)
                    Text("⏱️ Duration: ${formatTimeMs(file.durationMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("💾 Size: ${formatFileSize(file.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("🏷️ Format: ${file.extension.uppercase()}", style = MaterialTheme.typography.bodySmall)
                    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(file.file.lastModified()))
                    Text("📅 Modified: $dateStr", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { fileForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DiscoveredHiddenFolderCard(
    folder: DiscoveredHiddenFolder,
    isExpanded: Boolean,
    currentSongFilePath: String?,
    isCurrentlyPlaying: Boolean,
    onToggleExpand: () -> Unit,
    onPlayAllFolder: () -> Unit,
    onPlayFile: (DiscoveredAudioFile) -> Unit,
    onPlayNext: (DiscoveredAudioFile) -> Unit,
    onAddToQueue: (DiscoveredAudioFile) -> Unit,
    onShareFile: (DiscoveredAudioFile) -> Unit,
    onShowDetails: (DiscoveredAudioFile) -> Unit,
    onRemoveNoMedia: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            0.5.dp,
            folder.type.badgeColor.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Folder Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(folder.type.badgeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = folder.type.icon,
                        contentDescription = null,
                        tint = folder.type.badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Clean Type Badge
                        if (folder.type != HiddenFolderType.ALL) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = folder.type.badgeColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = folder.type.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    color = folder.type.badgeColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${folder.audioCount} audio files • ${formatFileSize(folder.totalSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Quick Play All Folder Button & Expand Chevron
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayAllFolder,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play Folder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded List of Audio Files
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(200)) + fadeIn(),
                exit = shrinkVertically(tween(180)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Sub-toolbar for folder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tracks in folder (${folder.audioFiles.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (folder.hasNoMedia) {
                            TextButton(
                                onClick = onRemoveNoMedia,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteForever,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Unhide (.nomedia)",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    folder.audioFiles.forEach { audioFile ->
                        val isPlayingThisFile = currentSongFilePath == audioFile.file.absolutePath

                        DiscoveredAudioFileRow(
                            audioFile = audioFile,
                            isPlaying = isPlayingThisFile && isCurrentlyPlaying,
                            isLoadedInPlayer = isPlayingThisFile,
                            onPlay = { onPlayFile(audioFile) },
                            onPlayNext = { onPlayNext(audioFile) },
                            onAddToQueue = { onAddToQueue(audioFile) },
                            onShare = { onShareFile(audioFile) },
                            onDetails = { onShowDetails(audioFile) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredAudioFileRow(
    audioFile: DiscoveredAudioFile,
    isPlaying: Boolean,
    isLoadedInPlayer: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val rowBackground = if (isLoadedInPlayer) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else Color.Transparent

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = rowBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play / Wave Indicator
            Surface(
                shape = CircleShape,
                color = if (isLoadedInPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (isLoadedInPlayer) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isPlaying) {
                        PlayingAudioWave(modifier = Modifier.height(12.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Audio Information
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isLoadedInPlayer) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.5.sp
                    ),
                    color = if (isLoadedInPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateFormatted = remember(audioFile.file) {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(audioFile.file.lastModified()))
                }

                Text(
                    text = "${formatTimeMs(audioFile.durationMs)} • ${formatFileSize(audioFile.sizeBytes)} • $dateFormatted",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3-dot Overflow Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onPlayNext()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onAddToQueue()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Audio") },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onShare()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("File Details") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            onDetails()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
