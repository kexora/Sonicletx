package com.kexora.soniclet.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.fuzzyMatch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.ui.text.style.TextAlign
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.ui.components.common.scrollFade
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState

import com.kexora.soniclet.ui.components.common.EmptyCategoryState

@Composable
fun AlbumsView(uiState: MusicUiState, viewModel: MusicViewModel) {
    val groupedItems = remember(uiState.songList) {
        uiState.songList
            .filter { it.album.isNotBlank() }
            .groupBy { it.album }
            .toSortedMap()
    }
    val albums = remember(groupedItems, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) groupedItems.keys.toList()
        else groupedItems.keys.filter { it.fuzzyMatch(uiState.searchQuery) }
    }
    val bottomPadding = LocalPlayerBottomPadding.current + 8.dp

    if (albums.isEmpty()) {
        if (uiState.isLoading) {
            com.kexora.soniclet.ui.components.common.SkeletonCategoryGrid(isGridMode = uiState.isGridMode)
        } else {
            EmptyCategoryState(
                icon = Icons.Rounded.Album,
                title = "No Albums Found",
                description = "Your music library doesn't have any albums categorized yet."
            )
        }
    } else {
        if (uiState.isGridMode) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().scrollFade(gridState),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                gridItems(albums, key = { it }) { album ->
                    val albumSongs = groupedItems[album] ?: emptyList()
                    val coverSong = albumSongs.firstOrNull { it.albumArtUri != null } ?: albumSongs.firstOrNull()
                    AlbumGridCard(
                        name = album,
                        songCount = albumSongs.size,
                        coverUri = coverSong?.albumArtUri,
                        onClick = { viewModel.openCategory(album, "ALBUM", albumSongs) }
                    )
                }

                item(
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                    key = "albums_grid_brand_footer"
                ) {
                    com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                }
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().scrollFade(listState),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                lazyItems(albums, key = { it }) { album ->
                    val albumSongs = groupedItems[album] ?: emptyList()
                    val coverSong = albumSongs.firstOrNull { it.albumArtUri != null } ?: albumSongs.firstOrNull()
                    AlbumListRow(
                        name = album,
                        songCount = albumSongs.size,
                        coverUri = coverSong?.albumArtUri,
                        onClick = { viewModel.openCategory(album, "ALBUM", albumSongs) }
                    )
                }

                item(key = "albums_list_brand_footer") {
                    com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                }
            }
        }
    }
}

@Composable
private fun AlbumGridCard(
    name: String,
    songCount: Int,
    coverUri: Any?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column {
            Box(modifier = Modifier.aspectRatio(1f).fillMaxWidth()) {
                if (coverUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = { FallbackAlbumArt(title = name, modifier = Modifier.fillMaxSize()) }
                    )
                } else {
                    FallbackAlbumArt(title = name, modifier = Modifier.fillMaxSize())
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$songCount tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlbumListRow(
    name: String,
    songCount: Int,
    coverUri: Any?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape)) {
                if (coverUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = { FallbackAlbumArt(title = name, modifier = Modifier.fillMaxSize()) }
                    )
                } else {
                    FallbackAlbumArt(title = name, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$songCount tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
