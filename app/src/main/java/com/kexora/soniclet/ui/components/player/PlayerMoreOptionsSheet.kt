package com.kexora.soniclet.ui.components.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.utils.SmartTitleCleaner
import com.kexora.soniclet.utils.StoryShareCardGenerator


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreOptionsSheet(
    song: Song,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit,
    onOpenAudioFx: (() -> Unit)? = null,
    onOpenTrackInfo: (() -> Unit)? = null,
    onOpenSleepTimer: (() -> Unit)? = null,
    onOpenSpeedPitch: (() -> Unit)? = null,
    onOpenMoodDialog: (() -> Unit)? = null,
    onOpenAudioInspector: (() -> Unit)? = null,
    onOpenMetadataEditor: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val cleanedTitle = remember(song.title, song.filePath) {
        SmartTitleCleaner.cleanTitle(song.title, song.filePath)
    }
    val subtitleLine = remember(song.artist, song.album) {
        val artist = SmartTitleCleaner.cleanArtist(song.artist).ifBlank { "Unknown Artist" }
        val album = SmartTitleCleaner.cleanAlbum(song.album)
        if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true)) {
            "$artist • $album"
        } else {
            artist
        }
    }

    val sheetSurface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheetSurface,
        contentColor = onSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .align(Alignment.CenterHorizontally)
                .drawBehind {
                    drawLine(
                        color = outlineVariant,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 16.dp)
        ) {
            // Clean centered drag handle pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(onSurfaceVariant.copy(alpha = 0.30f))
                )
            }

            // Borderless edge-to-edge song header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(song.albumArtUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover for $cleanedTitle",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize())
                            },
                            error = {
                                FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize())
                            }
                        )
                    } else {
                        FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanedTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = outlineVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 1. Play from Beginning
            FlatMoreOptionRow(
                icon = Icons.Rounded.Replay,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Play from Beginning",
                subtitle = "Restart current track from 0:00",
                testTag = "more_opt_play_beginning",
                onClick = {
                    onDismiss()
                    viewModel.playFromBeginning()
                }
            )

            // 2. Edit Metadata & ID3 Tags
            FlatMoreOptionRow(
                icon = Icons.Rounded.Edit,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Edit Metadata & ID3 Tags",
                subtitle = "Modify title, artist, album, lyrics & cover art",
                testTag = "more_opt_edit_metadata",
                onClick = {
                    onDismiss()
                    if (onOpenMetadataEditor != null) {
                        onOpenMetadataEditor()
                    } else {
                        viewModel.openSongDetails(song)
                    }
                }
            )

            // 3. Song Details & Audio Inspector
            FlatMoreOptionRow(
                icon = Icons.Rounded.GraphicEq,
                iconTint = Color(0xFF80CBC4),
                title = "Song Details & Audio Inspector",
                subtitle = "Codec, sample rate, bit depth & fidelity specs",
                testTag = "more_opt_audio_inspector",
                onClick = {
                    onDismiss()
                    if (onOpenAudioInspector != null) {
                        onOpenAudioInspector()
                    } else if (onOpenTrackInfo != null) {
                        onOpenTrackInfo()
                    } else {
                        viewModel.openSongDetails(song)
                    }
                }
            )

            // 4. Share to Story
            FlatMoreOptionRow(
                icon = Icons.Rounded.Share,
                iconTint = Color(0xFFFF6E9A),
                title = "Share to Story",
                subtitle = "Create musical card for Instagram or WhatsApp",
                testTag = "more_opt_share_story",
                onClick = {
                    onDismiss()
                    val currentLine = if (uiState.activeLyricIndex in uiState.currentLyrics.indices) {
                        uiState.currentLyrics[uiState.activeLyricIndex].text
                    } else null
                    StoryShareCardGenerator.generateAndShare(context, song, currentLine)
                }
            )

            // 5. Quick Share (Audio File)
            FlatMoreOptionRow(
                icon = Icons.Rounded.WifiTethering,
                iconTint = Color(0xFF4FC3F7),
                title = "Quick Share (Audio File)",
                subtitle = "Send original audio file via Nearby or messaging",
                testTag = "more_opt_quick_share",
                onClick = {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, song.contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Audio via"))
                }
            )

            // 6. Reload Lyrics
            FlatMoreOptionRow(
                icon = Icons.Rounded.Refresh,
                iconTint = onSurface.copy(alpha = 0.85f),
                title = "Reload Lyrics",
                subtitle = "Re-fetch synchronized LRC lyrics from storage",
                testTag = "more_opt_reload_lyrics",
                onClick = {
                    onDismiss()
                    viewModel.loadLyricsForSong(song)
                }
            )

            // 7. Set as Ringtone
            FlatMoreOptionRow(
                icon = Icons.Rounded.ContentCut,
                iconTint = Color(0xFFFFD54F),
                title = "Set as Ringtone",
                subtitle = "Trim & export favorite section as ringtone",
                testTag = "more_opt_ringtone",
                onClick = {
                    onDismiss()
                    viewModel.openTrimDialog(song)
                }
            )

            // 8. Add to Playlist
            FlatMoreOptionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Add to Playlist",
                subtitle = "Save track to an existing or new playlist",
                testTag = "more_opt_add_playlist",
                onClick = {
                    showAddToPlaylistDialog = true
                }
            )

            // Conditional: Reset Custom Lyrics (if imported)
            if (uiState.lyricsSourceType.contains("Imported", ignoreCase = true)) {
                FlatMoreOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Reset Custom Lyrics",
                    subtitle = "Revert to original file lyrics",
                    testTag = "more_opt_reset_lyrics",
                    onClick = {
                        onDismiss()
                        viewModel.clearImportedLyrics(song)
                    }
                )
            }
        }
    }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddToPlaylistDialog = false
                newPlaylistName = ""
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Add to Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.playlists.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            uiState.playlists.forEach { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.addSongsToPlaylist(playlist.id, listOf(song))
                                            Toast.makeText(context, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                                            showAddToPlaylistDialog = false
                                            onDismiss()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.songIds.size} tracks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Create new playlist") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPlaylistName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createPlaylist(trimmed, listOf(song))
                            Toast.makeText(context, "Created \"$trimmed\" & added track", Toast.LENGTH_SHORT).show()
                            showAddToPlaylistDialog = false
                            newPlaylistName = ""
                            onDismiss()
                        }
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    shape = CircleShape
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddToPlaylistDialog = false
                        newPlaylistName = ""
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun FlatMoreOptionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
