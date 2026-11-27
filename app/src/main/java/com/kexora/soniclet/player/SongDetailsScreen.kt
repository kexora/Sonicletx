package com.kexora.soniclet.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.permissions.StoragePermissionHelper
import com.kexora.soniclet.tools.CoverArtEditorDialog
import com.kexora.soniclet.tools.ExtendedAudioTags
import com.kexora.soniclet.tools.TagEditorUtil
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.SkeletonSongDetailsScreen
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.ui.components.player.CompactAudioBadge
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.ui.components.player.HiResAudioBadge
import com.kexora.soniclet.utils.ArtistParserUtil
import com.kexora.soniclet.utils.AudioInspectorUtil
import com.kexora.soniclet.utils.SmartTitleCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Core ID3 fields
    var editableTitle by remember(song.id) { mutableStateOf(song.title) }
    var editableArtist by remember(song.id) { mutableStateOf(song.artist) }
    var editableAlbum by remember(song.id) { mutableStateOf(song.album) }
    var editableYear by remember(song.id) { mutableStateOf(song.year) }
    var editableGenre by remember(song.id) { mutableStateOf(song.genre) }

    // Extended Pro ID3 Studio fields (loaded asynchronously from physical audio file)
    var loadedExtendedTags by remember(song.id) { mutableStateOf(ExtendedAudioTags()) }
    var editableAlbumArtist by remember(song.id) { mutableStateOf("") }
    var editableTrackNumber by remember(song.id) { mutableStateOf("") }
    var editableComposer by remember(song.id) { mutableStateOf("") }
    var editableComment by remember(song.id) { mutableStateOf("") }
    var editableLyrics by remember(song.id) { mutableStateOf("") }
    var isLyricsEditorExpanded by remember(song.id) { mutableStateOf(false) }

    LaunchedEffect(song.id) {
        val ext = withContext(Dispatchers.IO) {
            TagEditorUtil.readExtendedTags(context, song)
        }
        loadedExtendedTags = ext
        editableAlbumArtist = ext.albumArtist
        editableTrackNumber = ext.trackNumber
        editableComposer = ext.composer
        editableComment = ext.comment
        editableLyrics = ext.lyrics
    }

    var activeDelimiter by remember(song.id) { mutableStateOf(ArtistParserUtil.detectPrimaryDelimiter(song.artist)) }
    var showAddArtistDialog by remember { mutableStateOf(false) }
    var newArtistNameInput by remember { mutableStateOf("") }
    var newAlbumArtUri by remember(song.id) { mutableStateOf<Uri?>(null) }
    var editingArtUri by remember { mutableStateOf<Uri?>(null) }
    var showCoverArtEditor by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessBanner by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = remember(
        editableTitle, editableArtist, editableAlbum, editableYear, editableGenre,
        editableAlbumArtist, editableTrackNumber, editableComposer, editableComment, editableLyrics,
        newAlbumArtUri, song, loadedExtendedTags
    ) {
        editableTitle != song.title ||
            editableArtist != song.artist ||
            editableAlbum != song.album ||
            editableYear != song.year ||
            editableGenre != song.genre ||
            editableAlbumArtist != loadedExtendedTags.albumArtist ||
            editableTrackNumber != loadedExtendedTags.trackNumber ||
            editableComposer != loadedExtendedTags.composer ||
            editableComment != loadedExtendedTags.comment ||
            editableLyrics != loadedExtendedTags.lyrics ||
            newAlbumArtUri != null
    }

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

    val quickGenres = remember {
        listOf("Bhojpuri", "Bollywood", "Punjabi", "Devotional", "Lo-Fi", "Romantic", "Hip-Hop", "Classical", "Pop")
    }

    fun proceedWithSave() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            val success = viewModel.updateSongMetadata(
                songId = song.id,
                newTitle = editableTitle,
                newArtist = editableArtist,
                newAlbum = editableAlbum,
                newYear = editableYear,
                newGenre = editableGenre,
                newAlbumArtUri = newAlbumArtUri,
                newAlbumArtist = editableAlbumArtist,
                newTrackNumber = editableTrackNumber,
                newComposer = editableComposer,
                newComment = editableComment,
                newLyrics = editableLyrics
            )
            val savedArt = com.kexora.soniclet.utils.CustomArtworkManager.getCustomArtworkUri(context, song.id)
            if (savedArt != null) {
                newAlbumArtUri = savedArt
            }
            if (success) {
                loadedExtendedTags = ExtendedAudioTags(
                    albumArtist = editableAlbumArtist,
                    trackNumber = editableTrackNumber,
                    composer = editableComposer,
                    comment = editableComment,
                    lyrics = editableLyrics
                )
            }
            isSaving = false
            if (success) {
                showSuccessBanner = true
                Toast.makeText(context, "Metadata & ID3 tags saved into audio file!", Toast.LENGTH_SHORT).show()
                delay(3200)
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
                    imageVector = Icons.Rounded.Storage,
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
                    text = "To permanently write metadata and cover art into the physical audio file on Android, Soniclet needs file write permission.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        StoragePermissionHelper.requestAllFilesAccess(context)
                    },
                    shape = CircleShape
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
                Toast.makeText(context, "Cover artwork updated — tap Save to embed into file.", Toast.LENGTH_SHORT).show()
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
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Add Artist Credit",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = newArtistNameInput,
                    onValueChange = { newArtistNameInput = it },
                    label = { Text("Artist Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    enabled = newArtistNameInput.isNotBlank(),
                    shape = CircleShape
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

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)
    val miniPlayerClearance = maxOf(110.dp, LocalPlayerBottomPadding.current + 16.dp)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Song Info & ID3 Studio",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (hasUnsavedChanges) "Unsaved changes ready to write" else "Physical File Metadata & Audio Specs",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("song_details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .size(22.dp),
                            strokeWidth = 2.4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = { triggerSave() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("song_details_save_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = "Save Metadata",
                                    tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasUnsavedChanges) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-3).dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            SkeletonSongDetailsScreen(modifier = Modifier.padding(innerPadding))
        } else {
            val listState = rememberLazyListState()
            val listenedMs = uiState.playDurations[song.id] ?: 0L
            val effectiveListened = if (listenedMs > 0L) listenedMs else ((uiState.playCounts[song.id] ?: 0).toLong() * song.duration.coerceAtLeast(30000L))
            val totalPlays = uiState.playCounts[song.id] ?: 0
            val audioSpecs = remember(song.id, uiState.songSpecsCache) {
                uiState.songSpecsCache[song.id] ?: AudioInspectorUtil.getAudioSpecs(context, song)
            }
            val currentParsedArtists = remember(editableArtist) {
                ArtistParserUtil.splitArtists(editableArtist)
            }
            val displayHeroTitle = remember(editableTitle, song.filePath) {
                SmartTitleCleaner.cleanTitle(editableTitle.ifBlank { song.title }, song.filePath)
            }
            val displayHeroArtist = remember(editableArtist) {
                SmartTitleCleaner.cleanArtist(editableArtist).ifBlank { editableArtist.ifBlank { "Unknown Artist" } }
            }
            val displayHeroAlbum = remember(editableAlbum) {
                SmartTitleCleaner.cleanAlbum(editableAlbum).ifBlank { editableAlbum.ifBlank { "Single / Unknown Album" } }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .scrollFade(listState),
                contentPadding = PaddingValues(top = 6.dp, bottom = miniPlayerClearance)
            ) {
                // Inline Success Notice
                if (showSuccessBanner) {
                    item(key = "success_banner") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "ID3 tags & artwork written permanently to audio file",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 1. COMPACT BORDERLESS HERO HEADER
                item(key = "hero_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 84dp Artwork with Emerald Camera Badge
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                    .testTag("song_details_artwork_picker"),
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

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoCamera,
                                        contentDescription = "Change Cover Art",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayHeroTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = displayHeroArtist,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        val primaryArtist = ArtistParserUtil.splitArtists(song.artist, song.title).firstOrNull() ?: song.artist
                                        val allS = uiState.allSongsList.ifEmpty { uiState.songList }
                                        val matching = allS.filter { s ->
                                            ArtistParserUtil.splitArtists(s.artist, s.title).any { it.equals(primaryArtist, ignoreCase = true) }
                                        }
                                        if (matching.isNotEmpty()) {
                                            viewModel.openCategory(primaryArtist, "ARTIST", matching)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = displayHeroAlbum,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Full-Pill Action Buttons (Play, Queue, Favorite)
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
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Play", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }

                            FilledTonalButton(
                                onClick = {
                                    viewModel.addToQueue(song)
                                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("song_details_queue_button"),
                                shape = CircleShape
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Queue", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                            }

                            Surface(
                                onClick = { viewModel.toggleFavorite(song.id) },
                                shape = CircleShape,
                                color = if (isFavorite) Color(0xFFFF3366).copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isFavorite) Color(0xFFFF3366).copy(alpha = 0.45f) else dividerColor
                                ),
                                modifier = Modifier
                                    .size(42.dp)
                                    .testTag("song_details_favorite_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFavorite) Color(0xFFFF3366) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "div_hero") { HorizontalDivider(thickness = 0.5.dp, color = dividerColor) }

                // 2. INLINE ACTIVITY STATS ROW (Borderless 3-column strip)
                item(key = "activity_stats") {
                    val totalSec = effectiveListened / 1000
                    val h = totalSec / 3600
                    val m = (totalSec % 3600) / 60
                    val s = totalSec % 60
                    val timeText = when {
                        h > 0 -> "${h}h ${m}m"
                        m > 0 -> "${m}m ${s}s"
                        else -> "${s}s"
                    }
                    val moodTag = uiState.songMoods[song.id] ?: "Standard"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlatStatColumn(label = "Listened", value = timeText, accent = MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.width(0.5.dp).height(26.dp).background(dividerColor))
                        FlatStatColumn(label = "Plays", value = "$totalPlays", accent = MaterialTheme.colorScheme.onBackground)
                        Box(modifier = Modifier.width(0.5.dp).height(26.dp).background(dividerColor))
                        FlatStatColumn(label = "Mood", value = moodTag, accent = MaterialTheme.colorScheme.secondary)
                    }
                }

                item(key = "div_stats") { HorizontalDivider(thickness = 0.5.dp, color = dividerColor) }

                // 3. PRO ID3 METADATA STUDIO SECTION (Box-Free, Flat, Edge-to-Edge)
                item(key = "studio_header") {
                    FlatSectionHeader(title = "METADATA & ID3 STUDIO")
                }

                // Quick Metadata Action Strip (Edge-to-Edge LazyRow)
                item(key = "quick_action_strip") {
                    val activeArtUri = newAlbumArtUri ?: song.albumArtUri
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            StudioQuickChip(
                                icon = Icons.Rounded.AutoFixHigh,
                                label = "Smart Clean",
                                isHighlighted = true,
                                testTag = "studio_smart_clean_chip",
                                onClick = {
                                    val cleaned = SmartTitleCleaner.cleanAll(
                                        rawTitle = editableTitle,
                                        rawArtist = editableArtist,
                                        rawAlbum = editableAlbum,
                                        filePath = song.filePath
                                    )
                                    editableTitle = cleaned.title
                                    if (cleaned.artist.isNotBlank()) editableArtist = cleaned.artist
                                    editableAlbum = cleaned.album
                                    Toast.makeText(context, "Smart Clean applied to Title, Artist & Album", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item {
                            StudioQuickChip(
                                icon = Icons.Rounded.SwapHoriz,
                                label = "Swap Title & Artist",
                                testTag = "studio_swap_chip",
                                onClick = {
                                    val prev = editableTitle
                                    editableTitle = editableArtist
                                    editableArtist = prev
                                    Toast.makeText(context, "Swapped Title & Artist", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item {
                            StudioQuickChip(
                                icon = Icons.Rounded.ContentCut,
                                label = "Extract Artist",
                                testTag = "studio_extract_artist_chip",
                                onClick = {
                                    val split = SmartTitleCleaner.splitTitleAndArtist(
                                        rawTitle = editableTitle,
                                        existingArtist = editableArtist,
                                        filePath = song.filePath
                                    )
                                    if (split.wasSplit) {
                                        editableTitle = split.cleanTitle
                                        editableArtist = split.cleanArtist
                                        Toast.makeText(context, "Extracted artist from title", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No 'Title - Artist' delimiter found to split", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        item {
                            StudioQuickChip(
                                icon = if (activeArtUri != null) Icons.Rounded.Crop else Icons.Rounded.Image,
                                label = if (activeArtUri != null) "Cover Art" else "Pick Cover",
                                testTag = "studio_cover_art_chip",
                                onClick = {
                                    if (activeArtUri != null) {
                                        editingArtUri = activeArtUri
                                        showCoverArtEditor = true
                                    } else {
                                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                }
                            )
                        }
                        item {
                            StudioQuickChip(
                                icon = Icons.Rounded.RestartAlt,
                                label = "Reset",
                                testTag = "studio_reset_chip",
                                onClick = {
                                    editableTitle = song.title
                                    editableArtist = song.artist
                                    editableAlbum = song.album
                                    editableYear = song.year
                                    editableGenre = song.genre
                                    editableAlbumArtist = loadedExtendedTags.albumArtist
                                    editableTrackNumber = loadedExtendedTags.trackNumber
                                    editableComposer = loadedExtendedTags.composer
                                    editableComment = loadedExtendedTags.comment
                                    editableLyrics = loadedExtendedTags.lyrics
                                    newAlbumArtUri = null
                                    Toast.makeText(context, "Reverted unsaved edits", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Pro ID3 Form Fields (Flat over dark background, 14dp horizontal padding)
                item(key = "id3_form_fields") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Track Title
                        FlatStudioTextField(
                            value = editableTitle,
                            onValueChange = { editableTitle = it },
                            label = "Track Title",
                            testTag = "edit_song_title_field",
                            leadingIcon = Icons.Rounded.MusicNote
                        )

                        // 2. Artist(s) Text Field + Parsed Chips + Delimiter Chips
                        FlatStudioTextField(
                            value = editableArtist,
                            onValueChange = { editableArtist = it },
                            label = "Artist(s)",
                            testTag = "edit_song_artist_field",
                            leadingIcon = Icons.Rounded.Person
                        )

                        // Parsed removable artist chips + Add Artist
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(currentParsedArtists, key = { it }) { artistName ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    val remaining = currentParsedArtists.filter { it != artistName }
                                                    editableArtist = ArtistParserUtil.combineArtists(remaining, activeDelimiter)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove $artistName",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(
                                    onClick = { showAddArtistDialog = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    border = BorderStroke(0.8.dp, dividerColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Add Artist",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "Add",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Delimiter Chips (,  /  &  feat.)
                        if (currentParsedArtists.size > 1) {
                            val delimiters = listOf(", " to ", Comma", " / " to "/ Slash", " & " to "& Ampersand", " feat. " to "feat.")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(delimiters) { (delim, label) ->
                                    val selected = activeDelimiter == delim
                                    Surface(
                                        onClick = {
                                            activeDelimiter = delim
                                            editableArtist = ArtistParserUtil.combineArtists(currentParsedArtists, delim)
                                        },
                                        shape = CircleShape,
                                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.Transparent,
                                        border = BorderStroke(
                                            0.8.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else dividerColor
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Album Name
                        FlatStudioTextField(
                            value = editableAlbum,
                            onValueChange = { editableAlbum = it },
                            label = "Album Name",
                            testTag = "edit_song_album_field",
                            leadingIcon = Icons.Rounded.Album
                        )

                        // 4. Album Artist
                        FlatStudioTextField(
                            value = editableAlbumArtist,
                            onValueChange = { editableAlbumArtist = it },
                            label = "Album Artist",
                            testTag = "edit_song_album_artist_field",
                            leadingIcon = Icons.Rounded.Groups
                        )

                        // 5. Genre Field + Quick Select Chips
                        FlatStudioTextField(
                            value = editableGenre,
                            onValueChange = { editableGenre = it },
                            label = "Genre",
                            testTag = "edit_song_genre_field",
                            leadingIcon = Icons.Rounded.Category
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(quickGenres) { g ->
                                val isSelected = editableGenre.equals(g, ignoreCase = true)
                                Surface(
                                    onClick = { editableGenre = g },
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else dividerColor
                                    )
                                ) {
                                    Text(
                                        text = g,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // 6. Release Year & Track Number (2-column row, numeric keyboard)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FlatStudioTextField(
                                value = editableYear,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) editableYear = it },
                                label = "Release Year",
                                testTag = "edit_song_year_field",
                                leadingIcon = Icons.Rounded.CalendarToday,
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            FlatStudioTextField(
                                value = editableTrackNumber,
                                onValueChange = { if (it.length <= 7 && it.all { c -> c.isDigit() || c == '/' }) editableTrackNumber = it },
                                label = "Track #",
                                testTag = "edit_song_track_number_field",
                                leadingIcon = Icons.Rounded.Tag,
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 7. Composer / Lyricist
                        FlatStudioTextField(
                            value = editableComposer,
                            onValueChange = { editableComposer = it },
                            label = "Composer / Lyricist",
                            testTag = "edit_song_composer_field",
                            leadingIcon = Icons.Rounded.Brush
                        )

                        // 8. Comment / Mood Tag
                        FlatStudioTextField(
                            value = editableComment,
                            onValueChange = { editableComment = it },
                            label = "Comment / Mood Tag",
                            testTag = "edit_song_comment_field",
                            leadingIcon = Icons.Rounded.Comment
                        )

                        // 9. Expandable Embedded Lyrics / LRC Editor
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isLyricsEditorExpanded = !isLyricsEditorExpanded }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .testTag("studio_lyrics_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lyrics,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Embedded Lyrics / LRC Editor",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = if (editableLyrics.isBlank()) "No embedded lyrics — tap to add or paste LRC"
                                            else "${editableLyrics.lines().size} lines embedded",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (isLyricsEditorExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (isLyricsEditorExpanded) "Collapse lyrics editor" else "Expand lyrics editor",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = isLyricsEditorExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StudioQuickChip(
                                            icon = Icons.Rounded.ContentPaste,
                                            label = "Paste from Clipboard",
                                            isHighlighted = true,
                                            testTag = "studio_lyrics_paste_chip",
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val pasted = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                                if (!pasted.isNullOrBlank()) {
                                                    editableLyrics = pasted
                                                    Toast.makeText(context, "Pasted lyrics from clipboard", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                        if (editableLyrics.isNotEmpty()) {
                                            StudioQuickChip(
                                                icon = Icons.Rounded.DeleteOutline,
                                                label = "Clear",
                                                testTag = "studio_lyrics_clear_chip",
                                                onClick = { editableLyrics = "" }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = editableLyrics,
                                        onValueChange = { editableLyrics = it },
                                        placeholder = {
                                            Text(
                                                "[00:12.40] Paste plain or time-synced LRC lyrics...",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        minLines = 5,
                                        maxLines = 12,
                                        shape = RoundedCornerShape(14.dp),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_song_lyrics_field")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Full-Pill Primary Save Button
                        Button(
                            onClick = { triggerSave() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_metadata_btn"),
                            shape = CircleShape,
                            enabled = !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Writing ID3 Tags...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save Changes to Audio File", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item(key = "div_studio") { HorizontalDivider(thickness = 0.5.dp, color = dividerColor) }

                // 4. AUDIO CODEC & FIDELITY (Flat Spec Key-Value Rows)
                item(key = "codec_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        FlatSectionHeader(title = "AUDIO CODEC & FIDELITY")

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Fidelity Tier",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (audioSpecs.isHiRes) {
                                HiResAudioBadge()
                            } else {
                                CompactAudioBadge(specs = audioSpecs)
                            }
                        }

                        FlatSpecRow("Format & Codec", audioSpecs.fullCodecName)
                        FlatSpecRow(
                            "Exact Bitrate",
                            "${audioSpecs.bitrateFormatted} (${if (audioSpecs.isLossless) "Lossless PCM" else if (audioSpecs.bitrateKbps >= 300) "320 kbps Studio" else "CBR/VBR"})"
                        )
                        FlatSpecRow("Sample Rate", "${audioSpecs.sampleRateFormatted} (${audioSpecs.sampleRateHz} Hz)")
                        FlatSpecRow("Bit Depth", audioSpecs.bitDepthFormatted)
                        FlatSpecRow("Channel Layout", audioSpecs.channelLayout)
                        FlatSpecRow("File Size", audioSpecs.fileSizeFormatted)
                    }
                }

                item(key = "div_codec") { HorizontalDivider(thickness = 0.5.dp, color = dividerColor) }

                // 5. STORAGE & FILE SYSTEM (Flat Spec Key-Value Rows with tap-to-copy Path)
                item(key = "storage_section") {
                    val displayPath = song.filePath.ifBlank { "MediaStore Internal Stream" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        FlatSectionHeader(title = "STORAGE & FILE SYSTEM")

                        FlatSpecRow("Track ID", song.id.toString())
                        FlatSpecRow("Duration", formattedDuration)
                        FlatSpecRow("Date Added", formattedDate)
                        FlatSpecRow("Folder", song.folderName.ifBlank { "Unknown" })
                        FlatSpecRow(
                            label = "Physical Path",
                            value = displayPath,
                            isCopyable = song.filePath.isNotBlank(),
                            onClick = if (song.filePath.isNotBlank()) {
                                {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Audio File Path", song.filePath))
                                    Toast.makeText(context, "Copied path to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            } else null
                        )
                    }
                }

                item(key = "song_details_brand_footer") {
                    MadeInIndiaFooter()
                }
            }
        }
    }
}

@Composable
private fun FlatSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun FlatStatColumn(
    label: String,
    value: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = accent,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StudioQuickChip(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean = false,
    testTag: String = "",
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        border = BorderStroke(
            0.8.dp,
            if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        modifier = Modifier.then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FlatStudioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    testTag: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            keyboardType = keyboardType
        ),
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    )
}

@Composable
private fun FlatSpecRow(
    label: String,
    value: String,
    isCopyable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.95f)
        )
        Row(
            modifier = Modifier.weight(1.55f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = if (isCopyable) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isCopyable) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
