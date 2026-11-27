package com.kexora.soniclet.player

import android.net.Uri
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.kexora.soniclet.utils.ArtistParserUtil
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.kexora.soniclet.permissions.StoragePermissionHelper
import com.kexora.soniclet.tools.CoverArtEditorDialog
import com.kexora.soniclet.tools.TagEditorUtil
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.player.CompactAudioBadge
import com.kexora.soniclet.ui.components.player.HiResAudioBadge
import com.kexora.soniclet.ui.components.common.SkeletonSongDetailsScreen
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.AudioInspectorUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailsScreen(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editableTitle by remember(song.id) { mutableStateOf(song.title) }
    var editableArtist by remember(song.id) { mutableStateOf(song.artist) }
    var editableAlbum by remember(song.id) { mutableStateOf(song.album) }
    var editableYear by remember(song.id) { mutableStateOf(song.year) }
    var editableGenre by remember(song.id) { mutableStateOf(song.genre) }
    var activeDelimiter by remember(song.id) { mutableStateOf(ArtistParserUtil.detectPrimaryDelimiter(song.artist)) }
    var showAddArtistDialog by remember { mutableStateOf(false) }
    var newArtistNameInput by remember { mutableStateOf("") }
    var newAlbumArtUri by remember(song.id) { mutableStateOf<Uri?>(null) }
    var editingArtUri by remember { mutableStateOf<Uri?>(null) }
    var showCoverArtEditor by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessBanner by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            editingArtUri = uri
            showCoverArtEditor = true
        }
    }

    val imageRequest = remember(newAlbumArtUri ?: song.albumArtUri, context) {
        ImageRequest.Builder(context)
            .data(newAlbumArtUri ?: song.albumArtUri)
            .size(400, 400)
            .scale(Scale.FILL)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val isFavorite = uiState.favoriteSongIds.contains(song.id)
    val isPlaying = song.id == uiState.currentSong?.id && uiState.isPlaying

    val formattedDate = remember(song.dateAdded) {
        if (song.dateAdded > 0L) {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(song.dateAdded * 1000L))
        } else {
            "Unknown"
        }
    }

    val formattedDuration = remember(song.duration) {
        val totalSec = song.duration / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        String.format(Locale.getDefault(), "%d:%02d", min, sec)
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    fun proceedWithSave() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            val success = viewModel.updateSongMetadata(
                song.id,
                editableTitle,
                editableArtist,
                editableAlbum,
                editableYear,
                editableGenre,
                newAlbumArtUri
            )
            val savedArt = com.kexora.soniclet.utils.CustomArtworkManager.getCustomArtworkUri(context, song.id)
            if (savedArt != null) {
                newAlbumArtUri = savedArt
            }
            isSaving = false
            if (success) {
                showSuccessBanner = true
                Toast.makeText(context, "Artwork & metadata permanently written into audio file!", Toast.LENGTH_LONG).show()
                delay(3500)
                showSuccessBanner = false
            } else {
                Toast.makeText(context, "Could not overwrite physical file. Storage permission required.", Toast.LENGTH_LONG).show()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !StoragePermissionHelper.hasAllFilesAccess()) {
                    showPermissionDialog = true
                }
            }
        }
    }

    val writeRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            proceedWithSave()
        } else {
            Toast.makeText(context, "Storage write permission denied. File could not be overwritten.", Toast.LENGTH_LONG).show()
        }
    }

    fun triggerSave() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !StoragePermissionHelper.hasAllFilesAccess()) {
            val intentSender = TagEditorUtil.createWriteRequestIntentSender(context, song)
            if (intentSender != null) {
                try {
                    writeRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    return
                } catch (e: Exception) {
                    android.util.Log.w("SongDetails", "Failed launching write request: ${e.message}")
                }
            }
            showPermissionDialog = true
        } else {
            proceedWithSave()
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Storage Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "To permanently overwrite the physical audio file with the new artwork and tags on Android, Soniclet needs file write permission. Tap 'Allow All Files' to grant permission, or try the 1-tap single file prompt.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        StoragePermissionHelper.requestAllFilesAccess(context)
                    }
                ) {
                    Text("Allow All Files Access")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val intentSender = TagEditorUtil.createWriteRequestIntentSender(context, song)
                        if (intentSender != null) {
                            try {
                                writeRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            } catch (e: Exception) {
                                proceedWithSave()
                            }
                        } else {
                            proceedWithSave()
                        }
                    }
                ) {
                    Text("Single File Prompt")
                }
            }
        )
    }

    if (showCoverArtEditor && editingArtUri != null) {
        CoverArtEditorDialog(
            imageUri = editingArtUri!!,
            onDismissRequest = { showCoverArtEditor = false },
            onApplyEditedArt = { editedUri ->
                newAlbumArtUri = editedUri
                showCoverArtEditor = false
                Toast.makeText(context, "Cover artwork updated! Tap Save to embed into file.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddArtistDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddArtistDialog = false
                newArtistNameInput = ""
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Add Artist / Credit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter artist name to add to this track's credits:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newArtistNameInput,
                        onValueChange = { newArtistNameInput = it },
                        label = { Text("Artist Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newArtistNameInput.trim()
                        if (clean.isNotBlank()) {
                            val currentList = ArtistParserUtil.splitArtists(editableArtist).toMutableList()
                            if (!currentList.any { it.equals(clean, ignoreCase = true) }) {
                                currentList.add(clean)
                                editableArtist = ArtistParserUtil.combineArtists(currentList, activeDelimiter)
                            }
                        }
                        showAddArtistDialog = false
                        newArtistNameInput = ""
                    },
                    enabled = newArtistNameInput.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddArtistDialog = false
                        newArtistNameInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Song Info & Metadata",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Physical ID3 Tag & Audio Specs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("song_details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = { triggerSave() },
                            modifier = Modifier.testTag("song_details_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Metadata",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            SkeletonSongDetailsScreen(
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .scrollFade(listState),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = LocalPlayerBottomPadding.current + 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Success banner
                if (showSuccessBanner) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Saved into physical file!",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Cover art & ID3 tags updated in file & system player",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Modern Compact Header Card (Artwork + Info + Quick Actions)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Clickable Artwork Thumbnail with camera badge
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (newAlbumArtUri != null || song.albumArtUri != null) {
                                        SubcomposeAsyncImage(
                                            model = imageRequest,
                                            contentDescription = "Song Artwork",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            error = { FallbackAlbumArt(title = song.title) }
                                        )
                                    } else {
                                        FallbackAlbumArt(title = song.title)
                                    }

                                    // Compact Camera Overlay Badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = "Change Cover Art",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Track details summary
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = editableTitle.ifBlank { song.title },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = editableArtist.ifBlank { song.artist },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = editableAlbum.ifBlank { song.album },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap art to replace custom cover",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons row (Play, Add Queue, Favorite)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.playSong(song, "Song Details") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("song_details_play_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play", style = MaterialTheme.typography.labelLarge)
                                }

                                Button(
                                    onClick = {
                                        viewModel.addToQueue(song)
                                        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("song_details_queue_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Queue", style = MaterialTheme.typography.labelLarge)
                                }

                                IconButton(
                                    onClick = { viewModel.toggleFavorite(song.id) },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .testTag("song_details_favorite_button")
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Listening Activity & Stats
                val listenedMs = uiState.playDurations[song.id] ?: 0L
                val effectiveListened = if (listenedMs > 0L) listenedMs else ((uiState.playCounts[song.id] ?: 0).toLong() * song.duration.coerceAtLeast(30000L))
                val totalPlays = uiState.playCounts[song.id] ?: 0

                item {
                    SectionHeader(title = "ACTIVITY & STATS", icon = Icons.Default.GraphicEq)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            val totalSec = effectiveListened / 1000
                            val h = totalSec / 3600
                            val m = (totalSec % 3600) / 60
                            val s = totalSec % 60
                            val timeText = when {
                                h > 0 -> "${h}h ${m}m"
                                m > 0 -> "${m}m ${s}s"
                                else -> "${s}s"
                            }
                            StatItem(label = "Listened", value = timeText, color = MaterialTheme.colorScheme.primary)
                            StatItem(label = "Plays", value = "$totalPlays", color = MaterialTheme.colorScheme.secondary)
                            val moodTag = uiState.songMoods[song.id] ?: "Standard"
                            StatItem(label = "Mood", value = moodTag, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }

                // Direct Physical File ID3 Tag Editor
                item {
                    SectionHeader(title = "METADATA & ID3 STUDIO", icon = Icons.Default.Edit)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Top bar of editor: Clean Title action & info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Physical File Metadata",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                FilledTonalButton(
                                    onClick = {
                                        var clean = editableTitle
                                        val junkRegexes = listOf(
                                            Regex("(?i)\\s*\\(official\\s*(video|audio|music\\s*video)?\\)"),
                                            Regex("(?i)\\s*\\[official\\s*(video|audio|music\\s*video)?\\]"),
                                            Regex("(?i)\\s*\\(lyrics?\\)"),
                                            Regex("(?i)\\s*\\[lyrics?\\]"),
                                            Regex("(?i)\\s*\\[hd\\]|\\(hd\\)"),
                                            Regex("(?i)\\s*320\\s*kbps"),
                                            Regex("(?i)\\.mp3|\\.flac|\\.m4a|\\.wav|\\.ogg")
                                        )
                                        for (rx in junkRegexes) {
                                            clean = clean.replace(rx, "")
                                        }
                                        editableTitle = clean.trim()
                                        Toast.makeText(context, "Title cleaned of noise tags", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clean Title", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                                }
                            }

                            // 1. Track Title Field
                            CompactTextField(
                                value = editableTitle,
                                onValueChange = { editableTitle = it },
                                label = "Track Title",
                                testTag = "edit_song_title_field",
                                leadingIcon = {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = if (editableTitle.isNotBlank()) {
                                    {
                                        IconButton(onClick = { editableTitle = "" }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else null
                            )

                            // 2. Multiple Artists Studio Section
                            val currentParsedArtists = remember(editableArtist) {
                                ArtistParserUtil.splitArtists(editableArtist)
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (currentParsedArtists.size > 1) Icons.Rounded.Groups else Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Artists Identified (${currentParsedArtists.size})",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (currentParsedArtists.size > 1 || ArtistParserUtil.hasMultipleArtists(editableArtist)) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "Multi-Artist Track",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Direct Artist String input field
                                    CompactTextField(
                                        value = editableArtist,
                                        onValueChange = { editableArtist = it },
                                        label = "Artist Tag (Editable Text)",
                                        testTag = "edit_song_artist_field",
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        },
                                        trailingIcon = if (editableArtist.isNotBlank()) {
                                            {
                                                IconButton(onClick = { editableArtist = "" }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        } else null
                                    )

                                    // Parsed Artist Chips Row
                                    Text(
                                        text = "Individual Artists (Tap ✕ to remove):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(currentParsedArtists) { artistName ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = artistName,
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    IconButton(
                                                        onClick = {
                                                            val remaining = currentParsedArtists.filter { it != artistName }
                                                            editableArtist = ArtistParserUtil.combineArtists(remaining, activeDelimiter)
                                                        },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove $artistName",
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // + Add Artist Chip
                                        item {
                                            FilledTonalButton(
                                                onClick = { showAddArtistDialog = true },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Artist",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Add Artist", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }

                                    // Delimiter Selector Bar
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        Text(
                                            text = "Format Delimiter:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        val delimiterOptions = listOf(
                                            ", " to ", Comma",
                                            " & " to "& Ampersand",
                                            " feat. " to "feat. Feature",
                                            " / " to "/ Slash",
                                            "; " to "; Semicolon",
                                            " x " to "x Cross"
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            items(delimiterOptions) { (delim, label) ->
                                                val isSelected = activeDelimiter == delim
                                                Surface(
                                                    onClick = {
                                                        activeDelimiter = delim
                                                        editableArtist = ArtistParserUtil.combineArtists(currentParsedArtists, delim)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                                    )
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        ),
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Live Tag Preview Banner
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Preview Tag: \"${editableArtist.ifBlank { "Unknown Artist" }}\"",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            // 3. Album Field
                            CompactTextField(
                                value = editableAlbum,
                                onValueChange = { editableAlbum = it },
                                label = "Album Name",
                                leadingIcon = {
                                    Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            )

                            // 4. Year & Genre Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CompactTextField(
                                        value = editableYear,
                                        onValueChange = { editableYear = it },
                                        label = "Year",
                                        testTag = "edit_song_year_field",
                                        leadingIcon = {
                                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                }
                                Box(modifier = Modifier.weight(1.4f)) {
                                    CompactTextField(
                                        value = editableGenre,
                                        onValueChange = { editableGenre = it },
                                        label = "Genre",
                                        testTag = "edit_song_genre_field",
                                        leadingIcon = {
                                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    )
                                }
                            }

                            // 5. Artwork picker & editor controls card
                            val activeArtUri = newAlbumArtUri ?: song.albumArtUri
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (newAlbumArtUri != null) "New Artwork Ready ✓" else "Cover Artwork",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (newAlbumArtUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Crop, adjust & embed directly into physical audio file",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val changeBtnContainerColor = if (activeArtUri != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                    val changeBtnContentColor = if (activeArtUri != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (activeArtUri != null) {
                                            FilledTonalButton(
                                                onClick = {
                                                    editingArtUri = activeArtUri
                                                    showCoverArtEditor = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Crop,
                                                    contentDescription = "Adjust",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Adjust", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = changeBtnContainerColor,
                                                contentColor = changeBtnContentColor
                                            )
                                        ) {
                                            Text(
                                                text = if (activeArtUri != null) "Change" else "Browse",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }

                            // 6. Save Button
                            Button(
                                onClick = { triggerSave() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_metadata_btn"),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Writing physical tags...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save Changes to Audio File", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Audio Codec & Fidelity Card
                item {
                    val audioSpecs = remember(song.id, uiState.songSpecsCache) {
                        uiState.songSpecsCache[song.id] ?: AudioInspectorUtil.getAudioSpecs(context, song)
                    }
                    SectionHeader(title = "AUDIO CODEC & FIDELITY", icon = Icons.Default.MusicNote)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fidelity Tier",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (audioSpecs.isHiRes) {
                                    HiResAudioBadge()
                                } else {
                                    CompactAudioBadge(specs = audioSpecs)
                                }
                            }

                            CompactInfoRow("Format & Codec", audioSpecs.fullCodecName)
                            CompactInfoRow("Exact Bitrate", "${audioSpecs.bitrateFormatted} (${if (audioSpecs.isLossless) "Lossless PCM" else if (audioSpecs.bitrateKbps >= 300) "320 kbps Studio" else "CBR/VBR"})")
                            CompactInfoRow("Sample Rate", "${audioSpecs.sampleRateFormatted} (${audioSpecs.sampleRateHz} Hz)")
                            CompactInfoRow("Bit Depth", audioSpecs.bitDepthFormatted)
                            CompactInfoRow("Channel Layout", audioSpecs.channelLayout)
                            CompactInfoRow("Audio File Size", audioSpecs.fileSizeFormatted)
                        }
                    }
                }

                // Technical File & Storage Details Card
                item {
                    SectionHeader(title = "STORAGE & FILE SYSTEM", icon = Icons.Default.Storage)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactInfoRow("Track ID", song.id.toString())
                            CompactInfoRow("Duration", formattedDuration)
                            CompactInfoRow("Date Added", formattedDate)
                            CompactInfoRow("Folder", song.folderName)
                            CompactInfoRow("Physical Path", song.filePath.ifBlank { "MediaStore Internal Stream" })
                            CompactInfoRow("URI", song.contentUri.toString())
                        }
                    }
                }

                item(key = "song_details_brand_footer") {
                    com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    )
}

@Composable
private fun CompactInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
