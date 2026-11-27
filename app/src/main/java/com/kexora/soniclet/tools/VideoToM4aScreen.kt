package com.kexora.soniclet.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import kotlinx.coroutines.launch

/**
 * Modern, High-Performance Video to M4A (AAC) Converter Screen.
 *
 * Clean, fluid, borderless UI matching Soniclet Studio aesthetics.
 * Features dual-engine extraction:
 * 1. Instant Lossless Direct Stream Copy
 * 2. Custom Bitrate (128k - 320k) & Precision Trimming Size Control
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

    // Mode & Bitrate options
    var selectedMode by remember { mutableStateOf(VideoToM4aConverterUtil.ConversionMode.DIRECT_LOSSLESS) }
    var selectedBitrate by remember { mutableStateOf(VideoToM4aConverterUtil.AudioBitrate.BITRATE_256) }

    // Output Form state
    var songTitle by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("Soniclet Extract") }
    var albumName by remember { mutableStateOf("Video Audio") }
    var embedCoverArt by remember { mutableStateOf(true) }

    // Trimming range (0 to durationMs)
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var isTrimEnabled by remember { mutableStateOf(false) }

    // Conversion state
    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableIntStateOf(0) }
    var activeJob by remember { mutableStateOf<VideoToM4aConverterUtil.TransformerJob?>(null) }
    var conversionResult by remember { mutableStateOf<VideoToM4aConverterUtil.ConversionResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load video info
    fun loadVideo(uri: Uri) {
        selectedVideoUri = uri
        conversionResult = null
        errorMessage = null
        isLoadingMetadata = true

        coroutineScope.launch {
            val meta = VideoToM4aConverterUtil.inspectVideo(context, uri)
            isLoadingMetadata = false
            if (meta != null) {
                videoMetadata = meta
                songTitle = meta.title
                trimStartMs = 0L
                trimEndMs = meta.durationMs
                isTrimEnabled = false
                selectedMode = if (meta.isAacDirectCompatible) {
                    VideoToM4aConverterUtil.ConversionMode.DIRECT_LOSSLESS
                } else {
                    VideoToM4aConverterUtil.ConversionMode.CUSTOM_QUALITY
                }
            } else {
                Toast.makeText(context, "Could not inspect video file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Modern Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) loadVideo(uri)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) loadVideo(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Error banner if any
        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // 1. SUCCESS STATE
        if (conversionResult != null) {
            val res = conversionResult!!
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Conversion Complete!",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Saved to Music/Soniclet folder in pristine M4A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Details Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val thumb = videoMetadata?.thumbnail
                            if (thumb != null && !thumb.isRecycled && embedCoverArt) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Audiotrack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = res.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${res.artist} • ${res.fileSizeFormatted} • ${res.bitrateInfo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Play Button
                    Button(
                        onClick = {
                            try {
                                val targetUri: Uri? = res.outputUri ?: if (res.outputFile != null && res.outputFile.exists()) {
                                    Uri.fromFile(res.outputFile)
                                } else {
                                    null
                                }

                                if (targetUri == null) {
                                    Toast.makeText(context, "Audio file is not ready", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val playSong = Song(
                                    id = System.currentTimeMillis(),
                                    title = res.title,
                                    artist = res.artist,
                                    album = res.album,
                                    duration = if (res.durationMs > 0) res.durationMs else (videoMetadata?.durationMs ?: 0L),
                                    contentUri = targetUri,
                                    filePath = res.outputFile?.absolutePath ?: "",
                                    folderName = "Soniclet",
                                    folderPath = res.outputFile?.parent ?: "Music/Soniclet",
                                    dateAdded = System.currentTimeMillis() / 1000L
                                )
                                onRefreshLibrary()
                                onPlayConvertedSong(playSong)
                                onBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Now in Soniclet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val shareUri: Uri = res.outputUri ?: if (res.outputFile != null && res.outputFile.exists()) {
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            res.outputFile
                                        )
                                    } else {
                                        Toast.makeText(context, "Audio file not available to share", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/*"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share M4A Audio"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not share audio: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }

                        FilledTonalButton(
                            onClick = {
                                selectedVideoUri = null
                                videoMetadata = null
                                conversionResult = null
                                onRefreshLibrary()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Convert More")
                        }
                    }
                }
            }
        }

        // 2. CONVERTING PROGRESS STATE
        else if (isConverting) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { conversionProgress / 100f },
                        modifier = Modifier.size(76.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Extracting Audio • $conversionProgress%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Hardware accelerated processing in progress...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    LinearProgressIndicator(
                        progress = { conversionProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            activeJob?.cancelJob()
                            isConverting = false
                            Toast.makeText(context, "Conversion cancelled", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }
            }
        }

        // 3. INITIAL VIDEO PICKER / HERO STATE
        else if (videoMetadata == null) {
            // Fluid Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .padding(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.22f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MovieFilter,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "STUDIO EXTRACTOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Video to M4A Audio",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Extract crisp studio-quality M4A from any video (MP4, MKV, WebM, MOV) with instant speed & size control.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Pick Gallery Button
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Select Video from Gallery",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fallback File Explorer
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("video/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse Files / Storage", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            if (isLoadingMetadata) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Clean Feature Highlights (Fluid & Borderless)
            Text(
                text = "Key Capabilities",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            ConverterFeatureItem(
                icon = Icons.Rounded.FlashOn,
                iconTint = Color(0xFFEAB308),
                title = "Hardware Demuxing & Dual Engine",
                desc = "Instant 1-second lossless audio stream copy with zero battery drain."
            )

            ConverterFeatureItem(
                icon = Icons.Rounded.Tune,
                iconTint = Color(0xFF3B82F6),
                title = "Bitrate & Size Control (128k - 320k)",
                desc = "Control file size for low-end devices or extract studio-grade HD clarity."
            )

            ConverterFeatureItem(
                icon = Icons.Rounded.ContentCut,
                iconTint = Color(0xFFEC4899),
                title = "Precision Audio Trimmer",
                desc = "Trim intros, conversations, and outro sections seamlessly."
            )

            ConverterFeatureItem(
                icon = Icons.Rounded.Image,
                iconTint = Color(0xFF10B981),
                title = "Auto ID3 Tags & Video Artwork",
                desc = "Saves video frame as album artwork for the lock screen & player."
            )

        } else {
            // 4. VIDEO LOADED - CONFIGURE CONVERSION & QUALITY
            val meta = videoMetadata!!

            // Video Info Spotlight Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (meta.thumbnail != null && !meta.thumbnail.isRecycled) {
                        Image(
                            bitmap = meta.thumbnail.asImageBitmap(),
                            contentDescription = "Video preview",
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meta.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = meta.formattedDuration,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "• ${meta.formattedSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Change Video", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Conversion Mode Selection (Two Options)
            Text(
                text = "Extraction Mode & Quality",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mode 1: Fast Direct Lossless
                FilterChip(
                    selected = selectedMode == VideoToM4aConverterUtil.ConversionMode.DIRECT_LOSSLESS,
                    onClick = { selectedMode = VideoToM4aConverterUtil.ConversionMode.DIRECT_LOSSLESS },
                    label = {
                        Text(
                            text = "⚡ Fast Lossless",
                            fontWeight = if (selectedMode == VideoToM4aConverterUtil.ConversionMode.DIRECT_LOSSLESS) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                // Mode 2: Custom Bitrate & Size
                FilterChip(
                    selected = selectedMode == VideoToM4aConverterUtil.ConversionMode.CUSTOM_QUALITY,
                    onClick = { selectedMode = VideoToM4aConverterUtil.ConversionMode.CUSTOM_QUALITY },
                    label = {
                        Text(
                            text = "🎚️ Bitrate & Size",
                            fontWeight = if (selectedMode == VideoToM4aConverterUtil.ConversionMode.CUSTOM_QUALITY) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Bitrate & Size Controls (If Custom Quality Mode)
            if (selectedMode == VideoToM4aConverterUtil.ConversionMode.CUSTOM_QUALITY) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Audio Bitrate",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Live Estimated Output Size
                            val effectiveDuration = if (isTrimEnabled) (trimEndMs - trimStartMs).coerceAtLeast(0L) else meta.durationMs
                            val estimatedSize = VideoToM4aConverterUtil.calculateEstimatedSize(effectiveDuration, selectedBitrate)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Est. $estimatedSize",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Bitrate options grid
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            VideoToM4aConverterUtil.AudioBitrate.values().forEach { bitrate ->
                                val isSelected = selectedBitrate == bitrate
                                Surface(
                                    onClick = { selectedBitrate = bitrate },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bitrate.label,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = bitrate.desc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedBitrate = bitrate }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Direct mode info card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (meta.isAacDirectCompatible) "Original audio track will be copied directly in <1 sec without transcoding." else "Transcoding audio directly to studio M4A AAC format.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Audio Trimmer Card (Optional)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.ContentCut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Trim Audio (Optional)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Switch(
                            checked = isTrimEnabled,
                            onCheckedChange = { isTrimEnabled = it }
                        )
                    }

                    if (isTrimEnabled && meta.durationMs > 1000) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Start: ${VideoToM4aConverterUtil.formatDuration(trimStartMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "End: ${VideoToM4aConverterUtil.formatDuration(trimEndMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = trimStartMs.toFloat(),
                            onValueChange = {
                                trimStartMs = it.toLong().coerceAtMost(trimEndMs - 1000L)
                            },
                            valueRange = 0f..(meta.durationMs.toFloat()),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Slider(
                            value = trimEndMs.toFloat(),
                            onValueChange = {
                                trimEndMs = it.toLong().coerceAtLeast(trimStartMs + 1000L)
                            },
                            valueRange = 0f..(meta.durationMs.toFloat()),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val trimmedDuration = (trimEndMs - trimStartMs).coerceAtLeast(0L)
                        Text(
                            text = "Extracting ${VideoToM4aConverterUtil.formatDuration(trimmedDuration)} of audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Output Metadata Inputs Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Song & Tag Details",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = songTitle,
                        onValueChange = { songTitle = it },
                        label = { Text("Song Title") },
                        leadingIcon = { Icon(Icons.Rounded.Title, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = artistName,
                        onValueChange = { artistName = it },
                        label = { Text("Artist Name") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { embedCoverArt = !embedCoverArt }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = embedCoverArt,
                            onCheckedChange = { embedCoverArt = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Embed Video Thumbnail as Album Art",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Big Action Button
            Button(
                onClick = {
                    if (songTitle.isBlank()) {
                        Toast.makeText(context, "Please enter a song title", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val config = VideoToM4aConverterUtil.ConversionConfig(
                        inputUri = meta.uri,
                        outputTitle = songTitle.trim(),
                        outputArtist = artistName.ifBlank { "Soniclet Extract" }.trim(),
                        outputAlbum = albumName.ifBlank { "Video Audio" }.trim(),
                        mode = selectedMode,
                        bitrate = selectedBitrate,
                        startMs = if (isTrimEnabled) trimStartMs else 0L,
                        endMs = if (isTrimEnabled) trimEndMs else meta.durationMs,
                        embedThumbnail = embedCoverArt,
                        thumbnailBitmap = meta.thumbnail
                    )

                    isConverting = true
                    conversionProgress = 0
                    errorMessage = null

                    activeJob = VideoToM4aConverterUtil.convert(
                        context = context,
                        config = config,
                        isAacDirectCompatible = meta.isAacDirectCompatible,
                        onProgress = { progress ->
                            conversionProgress = progress
                        },
                        onSuccess = { res ->
                            isConverting = false
                            conversionResult = res
                            onRefreshLibrary()
                        },
                        onError = { err ->
                            isConverting = false
                            errorMessage = err
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.Audiotrack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Convert & Save M4A",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            MadeInIndiaFooter(
                topPadding = 12.dp,
                bottomPadding = 20.dp,
                fontSize = 9.5.sp,
                logoHeight = 12.dp,
                logoWidth = 64.dp,
                watermarkAlpha = 0.45f
            )
        }
    }
}

@Composable
private fun ConverterFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
