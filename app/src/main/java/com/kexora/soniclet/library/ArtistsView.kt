package com.kexora.soniclet.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.common.EmptyCategoryState
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.ArtistParserUtil
import com.kexora.soniclet.utils.fuzzyMatch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState

enum class ArtistDisplayMode(val label: String, val description: String) {
    INDIVIDUAL("Individual", "Each artist identified separately with solo & collab tracks"),
    COLLABORATIONS("Collaborations", "Multi-artist songs together (Feat, &, With, Duos)"),
    ALL_COMBINED("All Combined", "Original metadata artist groupings as tagged")
}

@Composable
fun ArtistsView(uiState: MusicUiState, viewModel: MusicViewModel) {
    var displayMode by remember { mutableStateOf(ArtistDisplayMode.INDIVIDUAL) }

    // Multi-artist parsing based on active mode
    val groupedItems: Map<String, List<Song>> = remember(uiState.songList, displayMode) {
        when (displayMode) {
            ArtistDisplayMode.INDIVIDUAL -> ArtistParserUtil.getIndividualArtistsMap(uiState.songList)
            ArtistDisplayMode.COLLABORATIONS -> ArtistParserUtil.getCollaborationArtistsMap(uiState.songList)
            ArtistDisplayMode.ALL_COMBINED -> ArtistParserUtil.getAllTogetherArtistsMap(uiState.songList)
        }
    }

    val itemsList = remember(groupedItems, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) groupedItems.keys.toList()
        else groupedItems.keys.filter { it.fuzzyMatch(uiState.searchQuery) }
    }

    val individualCount = remember(uiState.songList) {
        ArtistParserUtil.getIndividualArtistsMap(uiState.songList).size
    }
    val collabCount = remember(uiState.songList) {
        ArtistParserUtil.getCollaborationArtistsMap(uiState.songList).size
    }
    val allCount = remember(uiState.songList) {
        ArtistParserUtil.getAllTogetherArtistsMap(uiState.songList).size
    }

    val bottomPadding = LocalPlayerBottomPadding.current + 8.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        if (uiState.isGridMode) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .scrollFade(gridState),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Filter pills scroll naturally edge-to-edge with list
                item(key = "filter_pills", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    ArtistFilterPills(
                        displayMode = displayMode,
                        onModeChange = { displayMode = it },
                        individualCount = individualCount,
                        collabCount = collabCount,
                        allCount = allCount
                    )
                }

                if (itemsList.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        if (uiState.isLoading) {
                            com.kexora.soniclet.ui.components.common.SkeletonCategoryGrid(isGridMode = true)
                        } else {
                            EmptyCategoryState(
                                icon = if (displayMode == ArtistDisplayMode.COLLABORATIONS) Icons.Rounded.Groups else Icons.Rounded.Person,
                                title = if (displayMode == ArtistDisplayMode.COLLABORATIONS) "No Collaborations Found" else "No Artists Found",
                                description = if (displayMode == ArtistDisplayMode.COLLABORATIONS)
                                    "No songs with multiple artists (feat, &, with) detected in library."
                                else
                                    "Your library doesn't seem to have any artists yet."
                            )
                        }
                    }
                } else {
                    gridItems(itemsList, key = { it }) { itemStr ->
                        val itemSongs = groupedItems[itemStr] ?: emptyList()
                        val coverSong = itemSongs.firstOrNull { it.albumArtUri != null } ?: itemSongs.firstOrNull()
                        val stats = remember(itemStr, itemSongs, displayMode) {
                            if (displayMode == ArtistDisplayMode.INDIVIDUAL) {
                                ArtistParserUtil.getArtistStats(itemStr, itemSongs)
                            } else null
                        }

                        ArtistGridCard(
                            name = itemStr,
                            songCount = itemSongs.size,
                            stats = stats,
                            isCollaboration = displayMode == ArtistDisplayMode.COLLABORATIONS || ArtistParserUtil.hasMultipleArtists(itemStr),
                            coverUri = coverSong?.albumArtUri,
                            onClick = { viewModel.openCategory(itemStr, "ARTIST", itemSongs) }
                        )
                    }

                    item(
                        span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                        key = "artists_grid_brand_footer"
                    ) {
                        com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollFade(listState),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Filter pills scroll naturally edge-to-edge with list
                item(key = "filter_pills") {
                    ArtistFilterPills(
                        displayMode = displayMode,
                        onModeChange = { displayMode = it },
                        individualCount = individualCount,
                        collabCount = collabCount,
                        allCount = allCount
                    )
                }

                if (itemsList.isEmpty()) {
                    item {
                        if (uiState.isLoading) {
                            com.kexora.soniclet.ui.components.common.SkeletonCategoryGrid(isGridMode = false)
                        } else {
                            EmptyCategoryState(
                                icon = if (displayMode == ArtistDisplayMode.COLLABORATIONS) Icons.Rounded.Groups else Icons.Rounded.Person,
                                title = if (displayMode == ArtistDisplayMode.COLLABORATIONS) "No Collaborations Found" else "No Artists Found",
                                description = if (displayMode == ArtistDisplayMode.COLLABORATIONS)
                                    "No songs with multiple artists (feat, &, with) detected in library."
                                else
                                    "Your library doesn't seem to have any artists yet."
                            )
                        }
                    }
                } else {
                    lazyItems(itemsList, key = { it }) { itemStr ->
                        val itemSongs = groupedItems[itemStr] ?: emptyList()
                        val coverSong = itemSongs.firstOrNull { it.albumArtUri != null } ?: itemSongs.firstOrNull()
                        val stats = remember(itemStr, itemSongs, displayMode) {
                            if (displayMode == ArtistDisplayMode.INDIVIDUAL) {
                                ArtistParserUtil.getArtistStats(itemStr, itemSongs)
                            } else null
                        }

                        ArtistListRow(
                            name = itemStr,
                            songCount = itemSongs.size,
                            stats = stats,
                            isCollaboration = displayMode == ArtistDisplayMode.COLLABORATIONS || ArtistParserUtil.hasMultipleArtists(itemStr),
                            coverUri = coverSong?.albumArtUri,
                            onClick = { viewModel.openCategory(itemStr, "ARTIST", itemSongs) }
                        )
                    }

                    item(key = "artists_list_brand_footer") {
                        com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistFilterPills(
    displayMode: ArtistDisplayMode,
    onModeChange: (ArtistDisplayMode) -> Unit,
    individualCount: Int,
    collabCount: Int,
    allCount: Int
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Surface(
                onClick = { onModeChange(ArtistDisplayMode.INDIVIDUAL) },
                shape = CircleShape,
                color = if (displayMode == ArtistDisplayMode.INDIVIDUAL) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (displayMode == ArtistDisplayMode.INDIVIDUAL) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Individual ($individualCount)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
            }
        }

        item {
            Surface(
                onClick = { onModeChange(ArtistDisplayMode.COLLABORATIONS) },
                shape = CircleShape,
                color = if (displayMode == ArtistDisplayMode.COLLABORATIONS) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (displayMode == ArtistDisplayMode.COLLABORATIONS) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Collaborations ($collabCount)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
            }
        }

        item {
            Surface(
                onClick = { onModeChange(ArtistDisplayMode.ALL_COMBINED) },
                shape = CircleShape,
                color = if (displayMode == ArtistDisplayMode.ALL_COMBINED) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (displayMode == ArtistDisplayMode.ALL_COMBINED) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Together ($allCount)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistGridCard(
    name: String,
    songCount: Int,
    stats: ArtistParserUtil.ArtistStats?,
    isCollaboration: Boolean,
    coverUri: Any?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(105.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
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

                if (isCollaboration) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = "Collaboration",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = when {
                stats != null && stats.collabTracks > 0 -> {
                    val totalWord = if (stats.totalTracks == 1) "track" else "tracks"
                    "${stats.totalTracks} $totalWord (${stats.soloTracks} solo • ${stats.collabTracks} collab)"
                }
                isCollaboration -> "$songCount ${if (songCount == 1) "track" else "tracks"} • Collab"
                else -> "$songCount ${if (songCount == 1) "track" else "tracks"}"
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistListRow(
    name: String,
    songCount: Int,
    stats: ArtistParserUtil.ArtistStats?,
    isCollaboration: Boolean,
    coverUri: Any?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
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

                if (isCollaboration) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = "Collab",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCollaboration) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Collab",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                val subtitle = when {
                    stats != null && stats.collabTracks > 0 -> {
                        val totalWord = if (stats.totalTracks == 1) "track" else "tracks"
                        "${stats.totalTracks} $totalWord (${stats.soloTracks} solo • ${stats.collabTracks} collab)"
                    }
                    isCollaboration -> "$songCount ${if (songCount == 1) "track" else "tracks"} • Multiple Artists Together"
                    else -> "$songCount ${if (songCount == 1) "track" else "tracks"}"
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
