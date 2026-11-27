package com.kexora.soniclet.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt

@Composable
fun DynamicPlaylistCover(
    songs: List<Song>,
    modifier: Modifier = Modifier,
    defaultIcon: ImageVector = Icons.Default.QueueMusic
) {
    val topSongs = remember(songs) {
        val withArt = songs.filter { it.albumArtUri != null }
        if (withArt.isNotEmpty()) withArt.take(4) else songs.take(4)
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (topSongs.size) {
            0 -> {
                Icon(
                    imageVector = defaultIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
            1 -> {
                CoverImage(song = topSongs[0], modifier = Modifier.fillMaxSize())
            }
            2 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    CoverImage(song = topSongs[0], modifier = Modifier.weight(1f).fillMaxHeight())
                    CoverImage(song = topSongs[1], modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            3 -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    CoverImage(song = topSongs[0], modifier = Modifier.weight(1f).fillMaxHeight())
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        CoverImage(song = topSongs[1], modifier = Modifier.weight(1f).fillMaxWidth())
                        CoverImage(song = topSongs[2], modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CoverImage(song = topSongs[0], modifier = Modifier.weight(1f).fillMaxHeight())
                        CoverImage(song = topSongs[1], modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CoverImage(song = topSongs[2], modifier = Modifier.weight(1f).fillMaxHeight())
                        CoverImage(song = topSongs[3], modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverImage(song: Song, modifier: Modifier) {
    val context = LocalContext.current
    if (song.albumArtUri != null) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            error = {
                FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize())
            }
        )
    } else {
        FallbackAlbumArt(title = song.title, modifier = modifier)
    }
}
