package com.kexora.soniclet.tools

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.settings.EdgeHairlineDivider
import com.kexora.soniclet.settings.EdgeSectionHeader
import com.kexora.soniclet.settings.EdgeSettingsBottomSpacer
import com.kexora.soniclet.settings.sonicletSwitchColors
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class QualityOption(val label: String, val bitrate: VideoToM4aConverterUtil.AudioBitrate?) {
    ORIGINAL("Original Quality", null),
    KBPS_128("128 kbps", VideoToM4aConverterUtil.AudioBitrate.BITRATE_128),
    KBPS_192("192 kbps", VideoToM4aConverterUtil.AudioBitrate.BITRATE_192),
    KBPS_256("256 kbps", VideoToM4aConverterUtil.AudioBitrate.BITRATE_256),
    KBPS_320("320 kbps", VideoToM4aConverterUtil.AudioBitrate.BITRATE_320)
}

/**
 * Clean, modern, everyday-English Audio Extractor screen.
 * Turns any picked video into a playable audio song without requiring photo/video storage permissions.
 */
@Composable
fun VideoToM4aScreen(
    onBack: () -> Unit,
    onPlayConvertedSong: (Song) -> Unit,
    onRefreshLibrary: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoMetadata by remember { mutableStateOf<VideoToM4aConverterUtil.VideoMetadata?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(false) }

    // Form & audio quality state
    var songTitle by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var embedCoverArt by remember { mutableStateOf(true) }
    var selectedQuality by remember { mutableStateOf(QualityOption.ORIGINAL) }

    // Trimming state
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }

    // Clip Preview Audio Player state
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    // Extraction state
    var isSaving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableIntStateOf(0) }
    var activeJob by remember { mutableStateOf<VideoToM4aConverterUtil.TransformerJob?>(null) }
    var savedResult by remember { mutableStateOf<VideoToM4aConverterUtil.ConversionResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun stopPreview() {
        try { previewPlayer?.stop() } catch (_: Exception) {}
        try { previewPlayer?.release() } catch (_: Exception) {}
        previewPlayer = null
        isPreviewPlaying = false
    }

    // Release preview player and cancel active job if leaving screen
    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
            activeJob?.cancelJob()
        }
    }

    // Stop clip preview automatically when trimEndMs is reached
    LaunchedEffect(isPreviewPlaying, trimEndMs) {
        while (isPreviewPlaying) {
            delay(150)
            val player = previewPlayer
            if (player == null || !player.isPlaying) {
                isPreviewPlaying = false
                break
            }
            if (trimEndMs > trimStartMs && player.currentPosition >= trimEndMs) {
                stopPreview()
                break
            }
        }
    }

    fun togglePreview() {
        val uri = selectedVideoUri ?: return
        if (isPreviewPlaying) {
            stopPreview()
            return
        }
        try {
            stopPreview()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                setOnPreparedListener { readyPlayer ->
                    if (trimStartMs > 0L) {
                        readyPlayer.seekTo(trimStartMs.toInt())
                    }
                    readyPlayer.start()
                    isPreviewPlaying = true
                }
                setOnCompletionListener {
                    stopPreview()
                }
                setOnErrorListener { _, _, _ ->
                    stopPreview()
                    Toast.makeText(context, "Couldn't preview this video's sound", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            previewPlayer = mp
        } catch (_: Exception) {
            stopPreview()
            Toast.makeText(context, "Couldn't preview this video's sound", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadVideo(uri: Uri) {
        stopPreview()
        selectedVideoUri = uri
        savedResult = null
        errorMessage = null
        isLoadingMetadata = true

        coroutineScope.launch {
            val meta = VideoToM4aConverterUtil.inspectVideo(context, uri)
            isLoadingMetadata = false
            if (meta == null) {
                errorMessage = "Couldn't read this video file. Please pick another video."
                videoMetadata = null
            } else if (!meta.hasAudio) {
                errorMessage = "This video has no sound"
                videoMetadata = null
            } else {
                videoMetadata = meta
                songTitle = meta.title
                artistName = ""
                trimStartMs = 0L
                trimEndMs = meta.durationMs
                selectedQuality = if (meta.isAacDirectCompatible) QualityOption.ORIGINAL else QualityOption.KBPS_256
            }
        }
    }

    // Zero-permission Android system pickers
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) loadVideo(uri) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) loadVideo(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .testTag("audio_extractor_screen")
    ) {
        // Error banner (simple everyday English)
        if (!errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            // 1. COMPLETED STATE
            savedResult != null -> {
                val res = savedResult!!
                AudioSavedSuccessSection(
                    result = res,
                    thumbnail = if (embedCoverArt) videoMetadata?.thumbnail else null,
                    onPlayNow = {
                        val targetUri: Uri? = res.outputUri ?: res.outputFile?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
                        if (targetUri == null) {
                            Toast.makeText(context, "Saved song is not ready yet", Toast.LENGTH_SHORT).show()
                            return@AudioSavedSuccessSection
                        }
                        val song = Song(
                            id = if (res.mediaStoreSongId > 0L) res.mediaStoreSongId else System.currentTimeMillis(),
                            title = res.title,
                            artist = res.artist,
                            album = res.album,
                            duration = if (res.durationMs > 0L) res.durationMs else (videoMetadata?.durationMs ?: 0L),
                            contentUri = targetUri,
                            albumArtUri = if (embedCoverArt) (res.albumArtUri ?: targetUri) else null,
                            filePath = res.outputFile?.absolutePath ?: "",
                            folderName = "Soniclet",
                            folderPath = res.outputFile?.parent ?: "Music/Soniclet",
                            dateAdded = System.currentTimeMillis() / 1000L
                        )
                        onRefreshLibrary()
                        onPlayConvertedSong(song)
                        onBack()
                    },
                    onShare = {
                        try {
                            val shareUri: Uri? = res.outputUri ?: res.outputFile?.takeIf { it.exists() }?.let {
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                            }
                            if (shareUri == null) {
                                Toast.makeText(context, "Couldn't share this audio file", Toast.LENGTH_SHORT).show()
                                return@AudioSavedSuccessSection
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Audio"))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Couldn't share this audio file", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDone = {
                        selectedVideoUri = null
                        videoMetadata = null
                        savedResult = null
                        errorMessage = null
                        onRefreshLibrary()
                    }
                )
            }

            // 2. SAVING IN PROGRESS STATE
            isSaving -> {
                AudioSavingProgressSection(
                    progress = saveProgress,
                    songName = songTitle.ifBlank { "Saved Audio" },
                    onCancel = {
                        activeJob?.cancelJob()
                        activeJob = null
                        isSaving = false
                        saveProgress = 0
                        Toast.makeText(context, "Cancelled", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 3. IDLE / HERO PICKER STATE
            videoMetadata == null -> {
                AudioExtractorHeroPicker(
                    isLoading = isLoadingMetadata,
                    onPickFromGallery = {
                        galleryPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onChooseFile = {
                        filePicker.launch(arrayOf("video/*"))
                    }
                )
            }

            // 4. VIDEO LOADED — INTERACTIVE AUDIO SAVER
            else -> {
                val meta = videoMetadata!!
                val effectiveDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
                val isTrimmed = meta.durationMs > 0L && (trimStartMs > 0L || trimEndMs < meta.durationMs)

                // Compact Video Thumbnail Strip + Change Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumb = meta.thumbnail
                        if (thumb != null && !thumb.isRecycled) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = "Video thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        if (meta.durationMs > 0L) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = meta.formattedDuration,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meta.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${meta.formattedDuration} • ${meta.formattedSize}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = {
                            stopPreview()
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.testTag("extractor_change_video_button")
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

                // SONG DETAILS
                EdgeSectionHeader(title = "SONG DETAILS")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = songTitle,
                        onValueChange = { songTitle = it },
                        label = { Text("Song Name") },
                        placeholder = { Text("Enter song name") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Title,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("extractor_song_name_input")
                    )

                    OutlinedTextField(
                        value = artistName,
                        onValueChange = { artistName = it },
                        label = { Text("Singer / Artist") },
                        placeholder = { Text("Optional") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("extractor_artist_input")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Use video photo as cover toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { embedCoverArt = !embedCoverArt }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("extractor_cover_photo_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Use video photo as cover",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Shows a frame from the video as the song's cover art",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = embedCoverArt,
                        onCheckedChange = { embedCoverArt = it },
                        colors = sonicletSwitchColors()
                    )
                }

                EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)

                // TRIM CLIP SECTION (Dual-Thumb Pill Range Slider + Preview Clip)
                if (meta.durationMs > 2_000L) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TRIM CLIP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                letterSpacing = 1.0.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            onClick = { togglePreview() },
                            shape = CircleShape,
                            color = if (isPreviewPlaying) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.testTag("extractor_preview_clip_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPreviewPlaying) "Stop preview" else "Preview clip",
                                    tint = if (isPreviewPlaying) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isPreviewPlaying) "Stop Preview" else "Preview Clip",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = if (isPreviewPlaying) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }

                    DualThumbRangeTrimmer(
                        durationMs = meta.durationMs,
                        startMs = trimStartMs,
                        endMs = trimEndMs,
                        onRangeChange = { newStart, newEnd ->
                            trimStartMs = newStart
                            trimEndMs = newEnd
                            if (isPreviewPlaying) stopPreview()
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = VideoToM4aConverterUtil.formatDuration(trimStartMs),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Clip: ${VideoToM4aConverterUtil.formatDuration(effectiveDurationMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = VideoToM4aConverterUtil.formatDuration(trimEndMs),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)
                }

                // SOUND QUALITY PILLS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SOUND QUALITY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.0.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    val estBitrate = selectedQuality.bitrate ?: VideoToM4aConverterUtil.AudioBitrate.BITRATE_192
                    val estSize = VideoToM4aConverterUtil.calculateEstimatedSize(
                        if (effectiveDurationMs > 0L) effectiveDurationMs else meta.durationMs,
                        estBitrate
                    )
                    Text(
                        text = "Est. $estSize",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(QualityOption.entries) { option ->
                        val isSelected = selectedQuality == option
                        val bg by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = tween(180),
                            label = "quality_pill_bg"
                        )
                        val fg by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            animationSpec = tween(180),
                            label = "quality_pill_fg"
                        )

                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable { selectedQuality = option }
                                .padding(horizontal = 16.dp)
                                .testTag("extractor_quality_${option.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = fg
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // PRIMARY SAVE CTA
                Button(
                    onClick = {
                        stopPreview()
                        errorMessage = null
                        isSaving = true
                        saveProgress = 0

                        val mode = if (selectedQuality == QualityOption.ORIGINAL) {
                            VideoToM4aConverterUtil.ConversionMode.DIRECT_LOSSLESS
                        } else {
                            VideoToM4aConverterUtil.ConversionMode.CUSTOM_QUALITY
                        }
                        val bitrate = selectedQuality.bitrate ?: VideoToM4aConverterUtil.AudioBitrate.BITRATE_256
                        val finalTitle = songTitle.trim().ifEmpty { meta.title.ifBlank { "Saved Audio" } }
                        val finalArtist = artistName.trim().ifEmpty { "Soniclet" }

                        val config = VideoToM4aConverterUtil.ConversionConfig(
                            inputUri = meta.uri,
                            outputTitle = finalTitle,
                            outputArtist = finalArtist,
                            outputAlbum = finalTitle,
                            mode = mode,
                            bitrate = bitrate,
                            startMs = if (isTrimmed) trimStartMs else 0L,
                            endMs = if (isTrimmed) trimEndMs else 0L,
                            embedThumbnail = embedCoverArt,
                            thumbnailBitmap = meta.thumbnail
                        )

                        activeJob = VideoToM4aConverterUtil.convert(
                            context = context,
                            config = config,
                            isAacDirectCompatible = meta.isAacDirectCompatible,
                            onProgress = { progress -> saveProgress = progress },
                            onSuccess = { result ->
                                isSaving = false
                                activeJob = null
                                savedResult = result
                                onRefreshLibrary()
                            },
                            onError = { friendlyError ->
                                isSaving = false
                                activeJob = null
                                errorMessage = friendlyError
                            }
                        )
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(48.dp)
                        .testTag("extractor_save_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Audio Song",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
        MadeInIndiaFooter()
        EdgeSettingsBottomSpacer()
    }
}

@Composable
private fun AudioExtractorHeroPicker(
    isLoading: Boolean,
    onPickFromGallery: () -> Unit,
    onChooseFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        // High-Contrast, Box-Free Hero Block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "VIDEO TO AUDIO",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.0.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Save Audio from Video",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Turn any video into a clear audio song you can play offline",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Pill CTA: Pick Video from Gallery
            Button(
                onClick = onPickFromGallery,
                enabled = !isLoading,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("extractor_pick_gallery_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pick Video from Gallery",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Pill CTA: Choose Video File
            Surface(
                onClick = { if (!isLoading) onChooseFile() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("extractor_choose_file_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Choose Video File",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Reading video...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        EdgeHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)
        EdgeSectionHeader(title = "WHAT YOU CAN DO")

        ExtractorFeatureRow(
            icon = Icons.Rounded.Bolt,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Fast Audio Saving",
            subtitle = "Saves audio cleanly in seconds without draining your battery"
        )
        EdgeHairlineDivider(startIndent = 68.dp, endIndent = 16.dp)

        ExtractorFeatureRow(
            icon = Icons.Rounded.GraphicEq,
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Choose Sound Quality",
            subtitle = "Keep original sound or pick a smaller size to save storage"
        )
        EdgeHairlineDivider(startIndent = 68.dp, endIndent = 16.dp)

        ExtractorFeatureRow(
            icon = Icons.Rounded.ContentCut,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Cut & Trim Clips",
            subtitle = "Keep only the part of the video you want to listen to"
        )
        EdgeHairlineDivider(startIndent = 68.dp, endIndent = 16.dp)

        ExtractorFeatureRow(
            icon = Icons.Rounded.Image,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Auto Cover Photo",
            subtitle = "Uses a frame from the video so your song looks great everywhere"
        )
    }
}

@Composable
private fun ExtractorFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DualThumbRangeTrimmer(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (Long, Long) -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var draggingStart by remember { mutableStateOf(true) }

    val minGapMs = minOf(1000L, durationMs / 10L).coerceAtLeast(250L)
    val startFrac = (startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val endFrac = (endMs.toFloat() / durationMs.toFloat()).coerceIn(startFrac, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(40.dp)
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs, startMs, endMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        val distStart = kotlin.math.abs(frac - startFrac)
                        val distEnd = kotlin.math.abs(frac - endFrac)
                        draggingStart = distStart <= distEnd
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                        val targetMs = (frac * durationMs).toLong()
                        if (draggingStart) {
                            val clampedStart = targetMs.coerceIn(0L, (endMs - minGapMs).coerceAtLeast(0L))
                            onRangeChange(clampedStart, endMs)
                        } else {
                            val clampedEnd = targetMs.coerceIn((startMs + minGapMs).coerceAtMost(durationMs), durationMs)
                            onRangeChange(startMs, clampedEnd)
                        }
                    }
                )
            }
            .testTag("extractor_trim_slider"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val trackHeight = 6.dp.toPx()
            val trackTop = (size.height - trackHeight) / 2f
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            val thumbRadius = 10.dp.toPx()
            val usableWidth = (size.width - thumbRadius * 2f).coerceAtLeast(1f)

            val startX = thumbRadius + usableWidth * startFrac
            val endX = thumbRadius + usableWidth * endFrac

            // Full inactive track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = corner
            )
            // Selected active range
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(startX, trackTop),
                size = Size((endX - startX).coerceAtLeast(trackHeight), trackHeight),
                cornerRadius = corner
            )
            // Start thumb + subtle halo
            drawCircle(color = activeColor.copy(alpha = 0.24f), radius = thumbRadius + 4.dp.toPx(), center = Offset(startX, size.height / 2f))
            drawCircle(color = activeColor, radius = thumbRadius, center = Offset(startX, size.height / 2f))
            // End thumb + subtle halo
            drawCircle(color = activeColor.copy(alpha = 0.24f), radius = thumbRadius + 4.dp.toPx(), center = Offset(endX, size.height / 2f))
            drawCircle(color = activeColor, radius = thumbRadius, center = Offset(endX, size.height / 2f))
        }
    }
}

@Composable
private fun AudioSavingProgressSection(
    progress: Int,
    songName: String,
    onCancel: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(activeColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { (progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.size(64.dp),
                strokeWidth = 5.dp,
                color = activeColor,
                trackColor = activeColor.copy(alpha = 0.18f)
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = activeColor
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Saving audio... $progress%",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = songName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(18.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
            val fill = (size.width * (progress / 100f).coerceIn(0f, 1f)).coerceAtLeast(
                if (progress > 0) size.height else 0f
            )
            if (fill > 0f) {
                drawRoundRect(color = activeColor, size = Size(fill, size.height), cornerRadius = radius)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onCancel,
            shape = CircleShape,
            modifier = Modifier
                .height(44.dp)
                .testTag("extractor_cancel_button")
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AudioSavedSuccessSection(
    result: VideoToM4aConverterUtil.ConversionResult,
    thumbnail: android.graphics.Bitmap?,
    onPlayNow: () -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Audio saved",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Audio Saved!",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Saved to your Music folder and added to Soniclet",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Saved song preview strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbnail != null && !thumbnail.isRecycled) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Saved song cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${result.artist} • ${result.fileSizeFormatted} • ${result.bitrateInfo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Primary Pill: Play Now
        Button(
            onClick = onPlayNow,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("extractor_play_now_button")
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Play Now",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Row: Share Audio + Done
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onShare,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("extractor_share_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share Audio",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                onClick = onDone,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("extractor_done_button")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
