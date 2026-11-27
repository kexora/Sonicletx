package com.kexora.soniclet.ui.components.player

import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.NavigationTab
import com.kexora.soniclet.player.SleepTimerOption
import com.kexora.soniclet.library.*
import com.kexora.soniclet.equalizer.*
import com.kexora.soniclet.settings.*
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

    val sleepTimerActive = uiState.sleepTimerRemainingMs > 0 && uiState.sleepTimerOption != SleepTimerOption.OFF
    val sleepTimerText = if (sleepTimerActive) {
        val totalSecs = uiState.sleepTimerRemainingMs / 1000
        "%02d:%02d left".format(totalSecs / 60, totalSecs % 60)
    } else {
        uiState.sleepTimerOption.label
    }

    val currentMood = uiState.songMoods[song.id]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Song preview card header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(song.albumArtUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Cover for ${song.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                            },
                            error = {
                                FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                            }
                        )
                    } else {
                        FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Extended Actions & Sharing",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Play from Beginning
            MoreOptionTile(
                icon = Icons.Rounded.Replay,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Play from Beginning",
                subtitle = "Restart current track from 0:00",
                onClick = {
                    onDismiss()
                    viewModel.playFromBeginning()
                }
            )

            // Edit Track Metadata & ID3 Tags
            MoreOptionTile(
                icon = Icons.Rounded.Edit,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Edit Metadata & ID3 Tags",
                subtitle = "Modify song title, artist, album & cover art",
                onClick = {
                    onDismiss()
                    if (onOpenMetadataEditor != null) {
                        onOpenMetadataEditor()
                    } else {
                        viewModel.openSongDetails(song)
                    }
                }
            )

            // Share to Story
            MoreOptionTile(
                icon = Icons.Rounded.Share,
                iconTint = Color(0xFFFF4081),
                title = "Share to Story",
                subtitle = "Create musical card for Instagram or WhatsApp",
                onClick = {
                    onDismiss()
                    val currentLine = if (uiState.activeLyricIndex in uiState.currentLyrics.indices) uiState.currentLyrics[uiState.activeLyricIndex].text else null
                    StoryShareCardGenerator.generateAndShare(context, song, currentLine)
                }
            )

            // Quick Share (Audio File)
            MoreOptionTile(
                icon = Icons.Rounded.WifiTethering,
                iconTint = Color(0xFF29B6F6),
                title = "Quick Share (Audio)",
                subtitle = "Send original audio file via Nearby or messaging",
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

            // Reload Lyrics
            MoreOptionTile(
                icon = Icons.Rounded.Refresh,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Reload Lyrics",
                subtitle = "Re-fetch synchronized LRC lyrics from storage",
                onClick = {
                    onDismiss()
                    viewModel.loadLyricsForSong(song)
                }
            )

            // Reset Custom Lyrics (if imported)
            if (uiState.lyricsSourceType.contains("Imported", ignoreCase = true)) {
                MoreOptionTile(
                    icon = Icons.Rounded.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Reset Custom Lyrics",
                    subtitle = "Revert to original file lyrics",
                    onClick = {
                        onDismiss()
                        viewModel.clearImportedLyrics(song)
                    }
                )
            }
        }
    }
}

@Composable
private fun MoreOptionTile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.16f),
                            contentColor = badgeColor
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
