package com.kexora.soniclet.settings

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.testTag
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
import com.kexora.soniclet.library.DeleteFileOutcome
import com.kexora.soniclet.library.SystemHiddenRepository
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.common.PlayingAudioWave
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HiddenFolderType(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Rounded.FolderSpecial),
    CALL_RECORDING("Call Recordings", Icons.Rounded.PhoneInTalk),
    VOICE_NOTE("Voice Notes", Icons.Rounded.Mic),
    HIDDEN("Hidden", Icons.Rounded.FolderOff)
}

enum class RecordingSortOption(val label: String, val icon: ImageVector) {
    DATE_NEWEST("Newest First", Icons.Rounded.Schedule),
    DATE_OLDEST("Oldest First", Icons.Rounded.History),
    NAME_AZ("Name (A-Z)", Icons.Rounded.SortByAlpha),
    TRACKS_COUNT("Most Files", Icons.Rounded.Audiotrack),
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
    val globalSearchQuery = uiState?.searchQuery.orEmpty().trim()

    var isSyncingInBackground by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(HiddenFolderType.ALL) }
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(RecordingSortOption.DATE_NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var fileForDetails by remember { mutableStateOf<DiscoveredAudioFile?>(null) }
    var filePendingDelete by remember { mutableStateOf<Pair<DiscoveredHiddenFolder, DiscoveredAudioFile>?>(null) }
    var pendingSystemDeleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // filePath to folderPath

    // Inline Play Preview controller (does NOT overwrite user's main playback queue)
    var previewMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingFilePath by remember { mutableStateOf<String?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    fun stopInlinePreview() {
        try {
            previewMediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            previewMediaPlayer?.release()
        } catch (_: Exception) {}
        previewMediaPlayer = null
        previewingFilePath = null
        isPreviewPlaying = false
    }

    DisposableEffect(Unit) {
        onDispose {
            stopInlinePreview()
        }
    }

    fun toggleInlinePreview(audioFile: DiscoveredAudioFile) {
        val targetPath = audioFile.file.absolutePath
        if (previewingFilePath == targetPath && previewMediaPlayer != null) {
            val mp = previewMediaPlayer!!
            if (isPreviewPlaying) {
                try {
                    mp.pause()
                    isPreviewPlaying = false
                } catch (_: Exception) {
                    stopInlinePreview()
                }
            } else {
                if (uiState?.isPlaying == true) {
                    viewModel?.togglePlayPause()
                }
                try {
                    mp.start()
                    isPreviewPlaying = true
                } catch (_: Exception) {
                    stopInlinePreview()
                }
            }
            return
        }

        // Pause main music player if currently playing, without modifying its queue
        if (uiState?.isPlaying == true) {
            viewModel?.togglePlayPause()
        }
        stopInlinePreview()

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, Uri.fromFile(audioFile.file))
                setOnCompletionListener {
                    isPreviewPlaying = false
                    previewingFilePath = null
                    try { release() } catch (_: Exception) {}
                    previewMediaPlayer = null
                }
                setOnErrorListener { player, _, _ ->
                    isPreviewPlaying = false
                    previewingFilePath = null
                    try { player.release() } catch (_: Exception) {}
                    previewMediaPlayer = null
                    Toast.makeText(context, "Could not preview this audio file", Toast.LENGTH_SHORT).show()
                    true
                }
                prepare()
                start()
            }
            previewMediaPlayer = mp
            previewingFilePath = targetPath
            isPreviewPlaying = true
        } catch (e: Exception) {
            stopInlinePreview()
            Toast.makeText(context, "Could not preview this audio file", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncScan(force: Boolean = false) {
        coroutineScope.launch {
            isSyncingInBackground = true
            systemHiddenRepo.syncSystemHiddenFolders(context, forceFullScan = force)
            isSyncingInBackground = false
            onScanFinished()
        }
    }

    // Android 10+/11+ Scoped Storage delete consent launcher
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val target = pendingSystemDeleteTarget
        pendingSystemDeleteTarget = null
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            val (filePath, folderPath) = target
            if (previewingFilePath == filePath) stopInlinePreview()
            coroutineScope.launch {
                systemHiddenRepo.onExternalDeleteConfirmed(filePath, folderPath)
                viewModel?.loadAudioFiles(forceSync = true)
                Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        syncScan(force = false)
    }

    fun createSongFromDiscoveredFile(folder: DiscoveredHiddenFolder, file: DiscoveredAudioFile): Song {
        val badge = when (folder.type) {
            HiddenFolderType.CALL_RECORDING -> "Call Recordings - ${folder.name}"
            HiddenFolderType.VOICE_NOTE -> "Voice Notes - ${folder.name}"
            else -> "Hidden Folder - ${folder.name}"
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
            context.startActivity(Intent.createChooser(shareIntent, "Share ${audioFile.title}"))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not share this file", Toast.LENGTH_SHORT).show()
        }
    }

    val discoveredFolders = cachedFolders
    val isScanning = cachedFolders.isEmpty() && isSyncingInBackground

    fun getSortedAudioFiles(files: List<DiscoveredAudioFile>): List<DiscoveredAudioFile> {
        return when (sortOption) {
            RecordingSortOption.DATE_NEWEST -> files.sortedByDescending { it.file.lastModified() }
            RecordingSortOption.DATE_OLDEST -> files.sortedBy { it.file.lastModified() }
            RecordingSortOption.NAME_AZ -> files.sortedBy { it.title.lowercase(Locale.ROOT) }
            RecordingSortOption.TRACKS_COUNT -> files
            RecordingSortOption.SIZE_LARGEST -> files.sortedByDescending { it.sizeBytes }
        }
    }

    val filteredFolders = remember(discoveredFolders, selectedType, globalSearchQuery, sortOption) {
        val filtered = discoveredFolders.mapNotNull { folder ->
            val matchesType = when (selectedType) {
                HiddenFolderType.ALL -> true
                else -> folder.type == selectedType
            }
            if (!matchesType) return@mapNotNull null

            val matchingFiles = if (globalSearchQuery.isBlank()) {
                getSortedAudioFiles(folder.audioFiles)
            } else {
                getSortedAudioFiles(
                    folder.audioFiles.filter {
                        it.title.contains(globalSearchQuery, ignoreCase = true) ||
                            it.file.name.contains(globalSearchQuery, ignoreCase = true)
                    }
                )
            }

            val matchesQuery = globalSearchQuery.isBlank() ||
                folder.name.contains(globalSearchQuery, ignoreCase = true) ||
                folder.path.contains(globalSearchQuery, ignoreCase = true) ||
                matchingFiles.isNotEmpty()

            if (matchesQuery) folder.copy(audioFiles = matchingFiles) else null
        }

        when (sortOption) {
            RecordingSortOption.NAME_AZ -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
            RecordingSortOption.TRACKS_COUNT -> filtered.sortedByDescending { it.audioCount }
            RecordingSortOption.SIZE_LARGEST -> filtered.sortedByDescending { it.totalSizeBytes }
            RecordingSortOption.DATE_NEWEST -> filtered.sortedByDescending { f -> f.audioFiles.maxOfOrNull { it.file.lastModified() } ?: 0L }
            RecordingSortOption.DATE_OLDEST -> filtered.sortedBy { f -> f.audioFiles.minOfOrNull { it.file.lastModified() } ?: 0L }
        }
    }

    val totalAudioFiles = remember(discoveredFolders) {
        discoveredFolders.sumOf { it.audioCount }
    }

    val folderSummaryLabel = remember(discoveredFolders.size, totalAudioFiles, isScanning) {
        if (isScanning) {
            "Scanning storage..."
        } else {
            val folderWord = if (discoveredFolders.size == 1) "Folder" else "Folders"
            val fileWord = if (totalAudioFiles == 1) "Audio File" else "Audio Files"
            "${discoveredFolders.size} $folderWord • $totalAudioFiles $fileWord"
        }
    }

    val hairlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val pillBorder = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Compact Summary Row (No duplicate Search icon; TopHeaderBar search filters directly)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = folderSummaryLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sort toggle circle
                Box {
                    Surface(
                        onClick = { showSortMenu = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = pillBorder,
                        modifier = Modifier
                            .requiredSize(36.dp)
                            .testTag("recordings_sort_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Sort recordings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        RecordingSortOption.values().forEach { option ->
                            val isChosen = sortOption == option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

                // Refresh / Rescan circle
                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        syncScan(force = true)
                    },
                    enabled = !isSyncingInBackground,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = pillBorder,
                    modifier = Modifier
                        .requiredSize(36.dp)
                        .testTag("recordings_refresh_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSyncingInBackground) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Rescan folders",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Edge-to-Edge Filter Pills (LazyRow so zero pills ever clip at the right edge)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(HiddenFolderType.values()) { type ->
                val isSelected = selectedType == type
                val count = remember(discoveredFolders, type) {
                    when (type) {
                        HiddenFolderType.ALL -> discoveredFolders.size
                        else -> discoveredFolders.count { it.type == type }
                    }
                }

                Surface(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedType = type
                    },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isSelected) null else pillBorder,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("recordings_filter_${type.name.lowercase(Locale.ROOT)}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${type.label} ($count)",
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = hairlineColor)

        // Main Content Area
        if (isScanning) {
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
                        text = "Scanning recordings & hidden folders...",
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
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = selectedType.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = when {
                            globalSearchQuery.isNotEmpty() -> "No matching recordings"
                            selectedType == HiddenFolderType.CALL_RECORDING -> "No Call Recordings Found"
                            selectedType == HiddenFolderType.VOICE_NOTE -> "No Voice Notes Found"
                            selectedType == HiddenFolderType.HIDDEN -> "No Hidden Folders Found"
                            else -> "No Recordings or Hidden Folders"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (globalSearchQuery.isNotEmpty()) {
                            "Try searching with a different word."
                        } else {
                            "All audio folders on your device are already visible in your main library."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val bottomPadding = maxOf(110.dp, LocalPlayerBottomPadding.current + 16.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("recordings_folder_list"),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                itemsIndexed(filteredFolders, key = { _, folder -> folder.path }) { index, folder ->
                    val isExpanded = expandedFolderPath == folder.path || globalSearchQuery.isNotBlank()
                    val isFolderPreviewActive = isPreviewPlaying &&
                        folder.audioFiles.any { it.file.absolutePath == previewingFilePath }

                    FlatHiddenFolderRow(
                        folder = folder,
                        isExpanded = isExpanded,
                        isFolderPreviewActive = isFolderPreviewActive,
                        previewingFilePath = previewingFilePath,
                        isPreviewPlaying = isPreviewPlaying,
                        onToggleExpand = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            expandedFolderPath = if (isExpanded) null else folder.path
                        },
                        onPreviewFolder = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val activeInFolder = folder.audioFiles.find { it.file.absolutePath == previewingFilePath }
                            val targetFile = activeInFolder ?: folder.audioFiles.firstOrNull()
                            if (targetFile != null) {
                                toggleInlinePreview(targetFile)
                            }
                        },
                        onPreviewFile = { audioFile ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            toggleInlinePreview(audioFile)
                        },
                        onPlayNext = { audioFile ->
                            val song = createSongFromDiscoveredFile(folder, audioFile)
                            viewModel?.insertNext(song)
                            Toast.makeText(context, "Playing next: ${audioFile.title}", Toast.LENGTH_SHORT).show()
                        },
                        onAddToQueue = { audioFile ->
                            val song = createSongFromDiscoveredFile(folder, audioFile)
                            viewModel?.addToQueue(song)
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        },
                        onShareFile = { audioFile -> shareAudioFile(audioFile) },
                        onShowDetails = { audioFile -> fileForDetails = audioFile },
                        onRequestDeleteFile = { audioFile ->
                            filePendingDelete = folder to audioFile
                        },
                        onUnhideToLibrary = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            coroutineScope.launch {
                                val (ok, message) = systemHiddenRepo.unhideFolderToLibrary(context, folder)
                                if (ok) {
                                    viewModel?.restoreFolder(folder.path)
                                    viewModel?.loadAudioFiles(forceSync = true)
                                    syncScan(force = true)
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    if (index < filteredFolders.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = hairlineColor,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // Permanent Delete Confirmation Dialog (supports Android 10+ MediaStore.createDeleteRequest)
    if (filePendingDelete != null) {
        val (folder, audioFile) = filePendingDelete!!
        AlertDialog(
            onDismissRequest = { filePendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Recording?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete \"${audioFile.title}\"? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        filePendingDelete = null
                        coroutineScope.launch {
                            when (val outcome = systemHiddenRepo.deleteAudioFile(context, audioFile, folder.path)) {
                                is DeleteFileOutcome.Deleted -> {
                                    if (previewingFilePath == audioFile.file.absolutePath) {
                                        stopInlinePreview()
                                    }
                                    viewModel?.loadAudioFiles(forceSync = true)
                                    Toast.makeText(context, "Deleted permanently", Toast.LENGTH_SHORT).show()
                                }
                                is DeleteFileOutcome.RequiresUserConsent -> {
                                    pendingSystemDeleteTarget = outcome.filePath to outcome.folderPath
                                    try {
                                        deleteConsentLauncher.launch(
                                            IntentSenderRequest.Builder(outcome.intentSender).build()
                                        )
                                    } catch (_: Exception) {
                                        pendingSystemDeleteTarget = null
                                        Toast.makeText(context, "Could not request delete permission", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is DeleteFileOutcome.Failed -> {
                                    Toast.makeText(context, outcome.reason, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { filePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Audio File Info Dialog
    if (fileForDetails != null) {
        val file = fileForDetails!!
        val dateStr = remember(file.file) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(file.file.lastModified()))
        }
        AlertDialog(
            onDismissRequest = { fileForDetails = null },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = file.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Location: ${formatReadableStoragePath(file.file.parent.orEmpty())}", style = MaterialTheme.typography.bodySmall)
                    Text("Length: ${formatTimeMs(file.durationMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("Size: ${formatFileSize(file.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("Format: ${file.extension.uppercase(Locale.ROOT)}", style = MaterialTheme.typography.bodySmall)
                    Text("Saved: $dateStr", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = { fileForDetails = null },
                    shape = CircleShape
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun FlatHiddenFolderRow(
    folder: DiscoveredHiddenFolder,
    isExpanded: Boolean,
    isFolderPreviewActive: Boolean,
    previewingFilePath: String?,
    isPreviewPlaying: Boolean,
    onToggleExpand: () -> Unit,
    onPreviewFolder: () -> Unit,
    onPreviewFile: (DiscoveredAudioFile) -> Unit,
    onPlayNext: (DiscoveredAudioFile) -> Unit,
    onAddToQueue: (DiscoveredAudioFile) -> Unit,
    onShareFile: (DiscoveredAudioFile) -> Unit,
    onShowDetails: (DiscoveredAudioFile) -> Unit,
    onRequestDeleteFile: (DiscoveredAudioFile) -> Unit,
    onUnhideToLibrary: () -> Unit
) {
    val fileCountLabel = if (folder.audioCount == 1) "1 file" else "${folder.audioCount} files"
    val readablePath = remember(folder.path) { formatReadableStoragePath(folder.path) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clean 38.dp primary-tinted folder icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = folder.type.icon,
                    contentDescription = folder.type.label,
                    tint = MaterialTheme.colorScheme.primary,
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
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (folder.type != HiddenFolderType.ALL) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = folder.type.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$fileCountLabel • ${formatFileSize(folder.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = readablePath,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Circular Play Preview button
            Surface(
                onClick = onPreviewFolder,
                shape = CircleShape,
                color = if (isFolderPreviewActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = if (isFolderPreviewActive) null else BorderStroke(
                    0.6.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.requiredSize(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFolderPreviewActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isFolderPreviewActive) "Pause preview" else "Play preview",
                        tint = if (isFolderPreviewActive) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse folder" else "Expand folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        // Smooth inline expand/collapse of individual files
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(200)) + fadeIn(),
            exit = shrinkVertically(tween(180)) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.25f))
                    .padding(bottom = 8.dp)
            ) {
                // Sub-toolbar with file count and Unhide / Add to Library pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val itemText = if (folder.audioFiles.size == 1) "1 audio file" else "${folder.audioFiles.size} audio files"
                    Text(
                        text = itemText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        onClick = onUnhideToLibrary,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("unhide_folder_to_library_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Add to Library",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                folder.audioFiles.forEachIndexed { idx, audioFile ->
                    val isPreviewed = previewingFilePath == audioFile.file.absolutePath
                    FlatDiscoveredAudioRow(
                        audioFile = audioFile,
                        isPlaying = isPreviewed && isPreviewPlaying,
                        isSelected = isPreviewed,
                        onPreview = { onPreviewFile(audioFile) },
                        onPlayNext = { onPlayNext(audioFile) },
                        onAddToQueue = { onAddToQueue(audioFile) },
                        onShare = { onShareFile(audioFile) },
                        onDetails = { onShowDetails(audioFile) },
                        onDelete = { onRequestDeleteFile(audioFile) }
                    )
                    if (idx < folder.audioFiles.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(start = 60.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlatDiscoveredAudioRow(
    audioFile: DiscoveredAudioFile,
    isPlaying: Boolean,
    isSelected: Boolean,
    onPreview: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val dateFormatted = remember(audioFile.file) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(audioFile.file.lastModified()))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
            )
            .clickable(onClick = onPreview)
            .padding(start = 16.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    PlayingAudioWave(modifier = Modifier.height(13.dp))
                } else {
                    Icon(
                        imageVector = if (isSelected) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audioFile.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatTimeMs(audioFile.durationMs)} • ${formatFileSize(audioFile.sizeBytes)} • $dateFormatted",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Recording options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
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
                        showMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("File Info") },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMenu = false
                        onDetails()
                    }
                )
                HorizontalDivider(thickness = 0.5.dp)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Delete Permanently",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

internal fun formatReadableStoragePath(rawPath: String): String {
    if (rawPath.isBlank()) return "Internal Storage"
    val normalized = rawPath.replace('\\', '/').trimEnd('/')
    val stripped = when {
        normalized.startsWith("/storage/emulated/0/") ->
            "Internal Storage › " + normalized.removePrefix("/storage/emulated/0/").replace("/", " › ")
        normalized == "/storage/emulated/0" -> "Internal Storage"
        normalized.startsWith("/sdcard/") ->
            "Internal Storage › " + normalized.removePrefix("/sdcard/").replace("/", " › ")
        normalized.startsWith("/storage/") ->
            normalized.removePrefix("/storage/").replace("/", " › ")
        else -> normalized.trimStart('/').replace("/", " › ")
    }
    return stripped.ifBlank { "Internal Storage" }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
