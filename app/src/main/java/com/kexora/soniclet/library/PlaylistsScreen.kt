package com.kexora.soniclet.library

import androidx.compose.foundation.BorderStroke
import com.kexora.soniclet.utils.fuzzyMatch
import com.kexora.soniclet.settings.*
import com.kexora.soniclet.player.*
import com.kexora.soniclet.ui.components.player.*
import com.kexora.soniclet.ui.components.common.*
import com.kexora.soniclet.ui.components.navigation.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.scrollFade
import java.util.Locale

data class SmartPlaylist(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val songs: List<Song>
)

@Composable
fun Playlists(
    playlists: List<Playlist>,
    allSongs: List<Song>,
    favoriteSongIds: Set<Long> = emptySet(),
    playCounts: Map<Long, Int> = emptyMap(),
    songMoods: Map<Long, String> = emptyMap(),
    recentlyPlayedSongIds: List<Long> = emptyList(),
    searchQuery: String = "",
    isGridMode: Boolean = false,
    isLoading: Boolean = false,
    isCreateDialogVisible: Boolean = false,
    onCreateDialogDismiss: () -> Unit = {},
    onCreatePlaylist: (String, List<Song>) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onPlayQueue: (List<Song>, Int, String) -> Unit = { _, _, _ -> },
    onPlaySong: (Song, String) -> Unit = { _, _ -> },
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onAddSongsToPlaylist: (String, List<Song>) -> Unit,
    onOpenPlaylistDetails: (String) -> Unit = {},
    onOpenSmartPlaylistDetails: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 1. Favorites
    val favoritesSongs = remember(allSongs, favoriteSongIds) {
        allSongs.filter { favoriteSongIds.contains(it.id) }
    }

    // 2. Recently added (sorted by dateAdded / id descending)
    val recentlyAddedSongs = remember(allSongs) {
        allSongs.sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id }).take(50)
    }

    // 3. Recently played
    val recentlyPlayedSongs = remember(allSongs, recentlyPlayedSongIds) {
        recentlyPlayedSongIds.mapNotNull { id -> allSongs.find { it.id == id } }
    }

    // 4. Most played (songs played > 0 times, sorted by play count descending)
    val mostPlayedSongs = remember(allSongs, playCounts) {
        allSongs.filter { (playCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { playCounts[it.id] ?: 0 }
            .take(25)
    }

    // 5. Forgotten Favorites (high play count, but not recently played)
    val forgottenFavorites = remember(allSongs, playCounts, recentlyPlayedSongIds) {
        val recentIds = recentlyPlayedSongIds.take(40).toSet()
        allSongs.filter { (playCounts[it.id] ?: 0) >= 3 && it.id !in recentIds }
            .sortedByDescending { playCounts[it.id] ?: 0 }
            .take(25)
    }

    // Mood Playlists
    val moodPlaylists = remember(allSongs, songMoods) {
        val lateNight = allSongs.filter { songMoods[it.id] == "Late Night" }
        val workout = allSongs.filter { songMoods[it.id] == "Workout" }
        val chill = allSongs.filter { songMoods[it.id] == "Chill" }
        val romantic = allSongs.filter { songMoods[it.id] == "Romantic" }
        
        val list = mutableListOf<SmartPlaylist>()
        if (lateNight.isNotEmpty()) list.add(SmartPlaylist("mood_late_night", "Late Night", Icons.Filled.NightsStay, lateNight))
        if (workout.isNotEmpty()) list.add(SmartPlaylist("mood_workout", "Workout", Icons.Filled.FitnessCenter, workout))
        if (chill.isNotEmpty()) list.add(SmartPlaylist("mood_chill", "Chill", Icons.Filled.SelfImprovement, chill))
        if (romantic.isNotEmpty()) list.add(SmartPlaylist("mood_romantic", "Romantic", Icons.Filled.Favorite, romantic))
        list
    }

    val defaultSmartPlaylists = remember(
        favoritesSongs, recentlyAddedSongs, recentlyPlayedSongs, mostPlayedSongs, forgottenFavorites, moodPlaylists
    ) {
        val baseList = mutableListOf(
            SmartPlaylist(
                id = "smart_favorites",
                title = "Favorites",
                icon = Icons.Filled.Favorite,
                songs = favoritesSongs
            ),
            SmartPlaylist(
                id = "smart_recently_added",
                title = "Recently Added",
                icon = Icons.Filled.MoreTime,
                songs = recentlyAddedSongs
            ),
            SmartPlaylist(
                id = "smart_recently_played",
                title = "Recently Played",
                icon = Icons.Filled.History,
                songs = recentlyPlayedSongs
            ),
            SmartPlaylist(
                id = "smart_most_played",
                title = "Most Played",
                icon = Icons.Filled.Whatshot,
                songs = mostPlayedSongs
            ),
            SmartPlaylist(
                id = "smart_forgotten_favorites",
                title = "Forgotten Favorites",
                icon = Icons.Filled.AutoAwesome,
                songs = forgottenFavorites
            )
        )
        baseList.addAll(moodPlaylists)
        baseList
    }

    val filteredSmartPlaylists = remember(defaultSmartPlaylists, searchQuery) {
        if (searchQuery.isBlank()) defaultSmartPlaylists
        else defaultSmartPlaylists.filter { it.title.fuzzyMatch(searchQuery) }
    }

    val filteredCustomPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else playlists.filter { it.name.fuzzyMatch(searchQuery) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (isLoading) {
                com.kexora.soniclet.ui.components.common.SkeletonPlaylistsView(isGridMode = isGridMode)
            } else if (isGridMode) {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "SMART PLAYLISTS",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp, top = 8.dp)
                        )
                    }

                    items(filteredSmartPlaylists.size, key = { filteredSmartPlaylists[it].id }) { index ->
                        val smartPlaylist = filteredSmartPlaylists[index]
                        SmartPlaylistGridCard(
                            smartPlaylist = smartPlaylist,
                            onClick = { onOpenSmartPlaylistDetails(smartPlaylist.title) },
                            onPlayAll = {
                                if (smartPlaylist.songs.isNotEmpty()) {
                                    onPlayQueue(smartPlaylist.songs, 0, smartPlaylist.title)
                                }
                            }
                        )
                    }

                    if (filteredCustomPlaylists.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "CUSTOM PLAYLISTS",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 12.dp)
                            )
                        }

                        items(filteredCustomPlaylists.size, key = { filteredCustomPlaylists[it].id }) { index ->
                            val playlist = filteredCustomPlaylists[index]
                            CustomPlaylistGridCard(
                                playlist = playlist,
                                allSongs = allSongs,
                                onClick = { onOpenPlaylistDetails(playlist.id) },
                                onPlay = { onPlayPlaylist(playlist) }
                            )
                        }

                        item(
                            span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            key = "playlists_grid_brand_footer"
                        ) {
                            com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val bottomPadding = com.kexora.soniclet.LocalPlayerBottomPadding.current + 8.dp
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollFade(listState),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 5 Default Smart Playlists Section
                    item {
                        Text(
                            text = "SMART PLAYLISTS",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp)
                        )
                    }

                    items(
                        items = filteredSmartPlaylists,
                        key = { it.id }
                    ) { smartPlaylist ->
                        SmartPlaylistCard(
                            smartPlaylist = smartPlaylist,
                            onClick = { onOpenSmartPlaylistDetails(smartPlaylist.title) },
                            onPlayAll = {
                                if (smartPlaylist.songs.isNotEmpty()) {
                                    onPlayQueue(smartPlaylist.songs, 0, smartPlaylist.title)
                                }
                            }
                        )
                    }

                    // Custom User-Created Playlists Header
                    if (filteredCustomPlaylists.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "CUSTOM PLAYLISTS",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )
                        }

                        items(
                            items = filteredCustomPlaylists,
                            key = { it.id }
                        ) { playlist ->
                            CustomPlaylistCard(
                                playlist = playlist,
                                allSongs = allSongs,
                                onClick = { onOpenPlaylistDetails(playlist.id) },
                                onPlay = { onPlayPlaylist(playlist) },
                                onDelete = { onDeletePlaylist(playlist.id) }
                            )
                        }
                    }

                    item(key = "playlists_list_brand_footer") {
                        com.kexora.soniclet.ui.components.common.MadeInIndiaFooter()
                    }
                }
            }
        }
    }

    if (isCreateDialogVisible) {
        CreatePlaylistDialog(
            onDismiss = { onCreateDialogDismiss() },
            onCreate = { name ->
                onCreatePlaylist(name, emptyList())
                onCreateDialogDismiss()
            }
        )
    }
}

@Composable
private fun SmartPlaylistCard(
    smartPlaylist: SmartPlaylist,
    onClick: () -> Unit,
    onPlayAll: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.kexora.soniclet.ui.components.library.DynamicPlaylistCover(
                songs = smartPlaylist.songs,
                defaultIcon = smartPlaylist.icon,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = smartPlaylist.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${smartPlaylist.songs.size} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (smartPlaylist.songs.isNotEmpty()) {
                IconButton(onClick = onPlayAll) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play All",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomPlaylistCard(
    playlist: Playlist,
    allSongs: List<Song>,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val validSongIds = remember(allSongs) { allSongs.map { it.id }.toSet() }
    val playlistSongs = remember(allSongs, playlist.songIds) {
        val songMap = allSongs.associateBy { it.id }
        playlist.songIds.mapNotNull { songMap[it] }
    }
    val validTrackCount = remember(validSongIds, playlist.songIds) {
        playlist.songIds.count { id -> validSongIds.contains(id) }
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.kexora.soniclet.ui.components.library.DynamicPlaylistCover(
                songs = playlistSongs,
                defaultIcon = Icons.Default.QueueMusic,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$validTrackCount tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (validTrackCount > 0) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartPlaylistGridCard(
    smartPlaylist: SmartPlaylist,
    onClick: () -> Unit,
    onPlayAll: () -> Unit
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
                com.kexora.soniclet.ui.components.library.DynamicPlaylistCover(
                    songs = smartPlaylist.songs,
                    defaultIcon = smartPlaylist.icon,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = smartPlaylist.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${smartPlaylist.songs.size} tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomPlaylistGridCard(
    playlist: Playlist,
    allSongs: List<Song>,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val validSongIds = remember(allSongs) { allSongs.map { it.id }.toSet() }
    val playlistSongs = remember(allSongs, playlist.songIds) {
        val songMap = allSongs.associateBy { it.id }
        playlist.songIds.mapNotNull { songMap[it] }
    }
    val validTrackCount = remember(validSongIds, playlist.songIds) {
        playlist.songIds.count { id -> validSongIds.contains(id) }
    }

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
                com.kexora.soniclet.ui.components.library.DynamicPlaylistCover(
                    songs = playlistSongs,
                    defaultIcon = Icons.Default.QueueMusic,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$validTrackCount tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Create Playlist", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
