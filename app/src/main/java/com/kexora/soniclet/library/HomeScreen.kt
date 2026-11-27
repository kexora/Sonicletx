package com.kexora.soniclet.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.easteregg.SpecialDateInfo
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.player.NavigationTab
import com.kexora.soniclet.tools.TagEditorDialog
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.cleanDisplayTitle
import com.kexora.soniclet.ui.components.common.formatMillisToTime
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.fuzzyMatch
import kotlinx.coroutines.launch

private fun cleanDisplayArtist(rawArtist: String): String =
    HomeRecommendationEngine.cleanDisplayArtist(rawArtist)

private data class HomeMixConfig(
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val gradientColors: List<Color>,
    val icon: ImageVector
)

private fun getMixConfigForCategory(category: String): HomeMixConfig {
    return when (category) {
        "Workout" -> HomeMixConfig(
            title = "Workout Mix",
            subtitle = "High-energy beats & power tracks",
            badgeText = "WORKOUT",
            gradientColors = listOf(Color(0xFF991B1B), Color(0xFFDC2626), Color(0xFF450A0A)),
            icon = Icons.Rounded.FitnessCenter
        )
        "Devotional" -> HomeMixConfig(
            title = "Devotional Mix",
            subtitle = "Peaceful bhajans, aartis & spiritual tracks",
            badgeText = "DEVOTIONAL",
            gradientColors = listOf(Color(0xFF9A3412), Color(0xFFEA580C), Color(0xFF431407)),
            icon = Icons.Rounded.SelfImprovement
        )
        "Chill" -> HomeMixConfig(
            title = "Chill Mix",
            subtitle = "Relaxing acoustic, lo-fi & mellow vibes",
            badgeText = "CHILL",
            gradientColors = listOf(Color(0xFF0F766E), Color(0xFF14B8A6), Color(0xFF042F2E)),
            icon = Icons.Rounded.Spa
        )
        "Romantic" -> HomeMixConfig(
            title = "Romantic Mix",
            subtitle = "Soulful love songs & timeless melodies",
            badgeText = "ROMANTIC",
            gradientColors = listOf(Color(0xFF9D174D), Color(0xFFEC4899), Color(0xFF500724)),
            icon = Icons.Rounded.Favorite
        )
        "Party" -> HomeMixConfig(
            title = "Party Mix",
            subtitle = "Upbeat dance hits, remixes & club bangers",
            badgeText = "PARTY",
            gradientColors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF1E1B4B)),
            icon = Icons.Rounded.Celebration
        )
        else -> HomeMixConfig(
            title = "For You Mix",
            subtitle = "Personalized picks based on your taste",
            badgeText = "FOR YOU",
            gradientColors = listOf(Color(0xFF065F46), Color(0xFF059669), Color(0xFF022C22)),
            icon = Icons.Rounded.AutoAwesome
        )
    }
}

@Composable
fun HomeScreen(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onOpenPlaylist: (String) -> Unit,
    onOpenCategory: (String, String, List<Song>) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Layer 1 & Layer 5: Read session-locked eligible music & recommendations directly from LibraryViewModel cache.
    // Never call .shuffled() inside a Composable or recompute on currentPosition playback ticks!
    val cleanLibrarySongs = remember(
        uiState.homeEligibleSongs,
        uiState.songList,
        uiState.excludedSongIds,
        uiState.excludedFolders,
        uiState.restoredAutoSongIds
    ) {
        if (uiState.homeEligibleSongs.isNotEmpty() || uiState.songList.isEmpty()) {
            uiState.homeEligibleSongs
        } else {
            HomeRecommendationEngine.filterEligibleMusic(
                songs = uiState.songList,
                excludedSongIds = uiState.excludedSongIds,
                excludedFolders = uiState.excludedFolders,
                restoredAutoSongIds = uiState.restoredAutoSongIds
            )
        }
    }

    val searchQuery = uiState.searchQuery
    val allSongs = remember(cleanLibrarySongs, searchQuery) {
        if (searchQuery.isBlank()) cleanLibrarySongs
        else cleanLibrarySongs.filter {
            it.title.fuzzyMatch(searchQuery) ||
                it.artist.fuzzyMatch(searchQuery) ||
                it.album.fuzzyMatch(searchQuery)
        }
    }

    val selectedCategory = uiState.selectedHomeCategory
    val categories = remember { listOf("All", "Workout", "Devotional", "Chill", "Romantic", "Party") }

    val displayedForYouSongs = remember(uiState.homeForYouSongs, allSongs, selectedCategory, searchQuery) {
        val base = if (selectedCategory.equals("All", ignoreCase = true)) {
            uiState.homeForYouSongs.ifEmpty { allSongs.take(25) }
        } else {
            // Stage 3 Strict Honest Threshold: ZERO unrelated backfilling for specific categories!
            uiState.homeForYouSongs
        }
        if (searchQuery.isBlank()) base
        else base.filter {
            it.title.fuzzyMatch(searchQuery) ||
                it.artist.fuzzyMatch(searchQuery) ||
                it.album.fuzzyMatch(searchQuery)
        }
    }

    val displayedMixSongs = remember(uiState.homeMixSongs, displayedForYouSongs, selectedCategory, searchQuery) {
        val base = if (selectedCategory.equals("All", ignoreCase = true)) {
            uiState.homeMixSongs.ifEmpty { displayedForYouSongs }
        } else {
            uiState.homeMixSongs
        }
        if (searchQuery.isBlank()) base
        else base.filter {
            it.title.fuzzyMatch(searchQuery) ||
                it.artist.fuzzyMatch(searchQuery) ||
                it.album.fuzzyMatch(searchQuery)
        }
    }

    // 1. Recently Played (only shown when genuine >= 15s listening history exists)
    val recentlyPlayedSongs = remember(allSongs, uiState.recentlyPlayedSongIds) {
        if (uiState.recentlyPlayedSongIds.isEmpty()) emptyList()
        else {
            val songMap = allSongs.associateBy { it.id }
            uiState.recentlyPlayedSongIds.mapNotNull { songMap[it] }.take(25)
        }
    }

    // 2. Most Played (ONLY shown after at least 3 eligible songs have >= 30 seconds of real playback)
    val mostPlayedSongs = remember(allSongs, uiState.playDurations, uiState.playCounts) {
        val qualifiedSongs = allSongs.filter { song ->
            (uiState.playDurations[song.id] ?: 0L) >= 30_000L
        }
        if (qualifiedSongs.size < 3) {
            emptyList()
        } else {
            qualifiedSongs.sortedByDescending { song ->
                (uiState.playDurations[song.id] ?: 0L) + ((uiState.playCounts[song.id] ?: 0) * 15_000L)
            }.take(25)
        }
    }

    // 3. Old Favorites (ONLY shown when older liked songs haven't been played in the last 7 days)
    val oldFavoritesSongs = remember(allSongs, uiState.favoriteSongIds, uiState.lastPlayedTimestamps, uiState.recentlyPlayedSongIds) {
        if (uiState.favoriteSongIds.isEmpty()) {
            emptyList()
        } else {
            val nowMs = System.currentTimeMillis()
            val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
            val sevenDaysAgoSec = (nowMs - sevenDaysMs) / 1000L
            val recentPlayWindow = uiState.recentlyPlayedSongIds.take(10).toSet()

            allSongs.filter { song ->
                if (!uiState.favoriteSongIds.contains(song.id)) return@filter false
                val lastPlayedMs = uiState.lastPlayedTimestamps[song.id]
                if (lastPlayedMs != null) {
                    (nowMs - lastPlayedMs) >= sevenDaysMs
                } else {
                    val isAddedAtLeast7DaysAgo = song.dateAdded in 1L..sevenDaysAgoSec
                    isAddedAtLeast7DaysAgo && !recentPlayWindow.contains(song.id)
                }
            }.take(25)
        }
    }

    // 4. New Songs (Recently added clean music tracks)
    val newSongs = remember(allSongs) {
        allSongs.sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id }).take(25)
    }

    // 5. Top Singers (Session-locked clean real artist names from engine, excluding placeholders & voice notes)
    val topSingers = remember(uiState.homeTopSingers, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.homeTopSingers
        } else {
            uiState.homeTopSingers.filter { (name, songs) ->
                name.fuzzyMatch(searchQuery) || songs.any { it.title.fuzzyMatch(searchQuery) }
            }
        }
    }

    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForRingtone by remember { mutableStateOf<Song?>(null) }
    var songToEditTags by remember { mutableStateOf<Song?>(null) }

    val playerBottomPad = LocalPlayerBottomPadding.current
    val bottomPadding = remember(playerBottomPad) {
        maxOf(110.dp, playerBottomPad + 16.dp)
    }

    if (uiState.isLoading && uiState.songList.isEmpty()) {
        HomeSkeletonLoading(
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (allSongs.isEmpty()) {
        EmptyHomeLibraryState(
            searchQuery = uiState.searchQuery,
            onRescan = { viewModel.rescanStorage() },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val activeMixConfig = remember(selectedCategory) { getMixConfigForCategory(selectedCategory) }
    val forYouSectionTitle = if (selectedCategory == "All") "For You" else "$selectedCategory Tracks"

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .scrollFade(listState),
        contentPadding = PaddingValues(top = 6.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Special Celebration Banner (Only when Moh Mode is activated)
        if (uiState.isMohModeActivated && uiState.todaySpecialDate != null) {
            item(key = "home_special_celebration") {
                SpecialCelebrationHomeBanner(
                    specialDate = uiState.todaySpecialDate,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        // 1. Smart Category Filter Chips (All, Workout, Devotional, Chill, Romantic, Party)
        item(key = "home_category_chips") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories, key = { it }) { category ->
                    val isSelected = selectedCategory == category
                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        animationSpec = tween(180),
                        label = "chipBg"
                    )
                    val chipContent by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(180),
                        label = "chipContent"
                    )
                    Surface(
                        onClick = { viewModel.setHomeCategory(category) },
                        shape = RoundedCornerShape(12.dp),
                        color = chipBg,
                        contentColor = chipContent,
                        border = if (isSelected) null else BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag("home_category_chip_${category.lowercase()}")
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 2. Compact, Box-Free Editorial Mix Header (zero heavy card container)
        if (displayedMixSongs.isNotEmpty()) {
            item(key = "home_featured_mix_card") {
                EditorialMixHeaderStrip(
                    mixConfig = activeMixConfig,
                    songs = displayedMixSongs,
                    onOpenMixPlaylist = {
                        viewModel.openOrCreateDynamicPlaylist(activeMixConfig.title, displayedMixSongs)
                    },
                    modifier = Modifier.testTag("home_featured_mix_card")
                )
            }
        } else if (!selectedCategory.equals("All", ignoreCase = true)) {
            item(key = "home_empty_category_state") {
                EmptyCategoryHeaderState(
                    category = selectedCategory,
                    icon = activeMixConfig.icon,
                    onShowAll = { viewModel.setHomeCategory("All") }
                )
            }
        }

        // 3. For You (or active Category section)
        if (displayedForYouSongs.isNotEmpty()) {
            item(key = "home_for_you_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeSectionHeader(
                        title = forYouSectionTitle,
                        count = displayedForYouSongs.size,
                        onViewAll = {
                            val targetPlaylistName = if (selectedCategory == "All") "For You" else "$selectedCategory Mix"
                            viewModel.openOrCreateDynamicPlaylist(targetPlaylistName, displayedForYouSongs)
                        },
                        onManualRefresh = { viewModel.refreshHomeRecommendations() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(displayedForYouSongs.take(16), key = { _, s -> "foryou_${s.id}" }) { index, song ->
                            MediumHorizontalSongCard(
                                song = song,
                                isCurrentSong = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.isPlaying,
                                onClick = {
                                    viewModel.playQueue(displayedForYouSongs, index, forYouSectionTitle)
                                },
                                onLongClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }

        // 4. Recently Played (Only when real listening history exists)
        if (recentlyPlayedSongs.isNotEmpty()) {
            item(key = "home_recently_played_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeSectionHeader(
                        title = "Recently Played",
                        count = recentlyPlayedSongs.size,
                        onViewAll = {
                            viewModel.openOrCreateDynamicPlaylist("Recently Played", recentlyPlayedSongs)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(recentlyPlayedSongs.take(16), key = { _, s -> "recent_${s.id}" }) { index, song ->
                            MediumHorizontalSongCard(
                                song = song,
                                isCurrentSong = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.isPlaying,
                                onClick = {
                                    viewModel.playQueue(recentlyPlayedSongs, index, "Recently Played")
                                },
                                onLongClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }

        // 5. Most Played (Only shown after >= 3 songs have >= 30s of real playback)
        if (mostPlayedSongs.isNotEmpty()) {
            item(key = "home_most_played_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeSectionHeader(
                        title = "Most Played",
                        count = mostPlayedSongs.size,
                        onViewAll = {
                            viewModel.openOrCreateDynamicPlaylist("Most Played", mostPlayedSongs)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(mostPlayedSongs.take(16), key = { _, s -> "most_${s.id}" }) { index, song ->
                            MediumHorizontalSongCard(
                                song = song,
                                isCurrentSong = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.isPlaying,
                                onClick = {
                                    viewModel.playQueue(mostPlayedSongs, index, "Most Played")
                                },
                                onLongClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }

        // 6. Old Favorites (Only shown when liked songs haven't been played in the last 7 days)
        if (oldFavoritesSongs.isNotEmpty()) {
            item(key = "home_old_favorites_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeSectionHeader(
                        title = "Old Favorites",
                        count = oldFavoritesSongs.size,
                        onViewAll = {
                            viewModel.openOrCreateDynamicPlaylist("Old Favorites", oldFavoritesSongs)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(oldFavoritesSongs.take(16), key = { _, s -> "oldfav_${s.id}" }) { index, song ->
                            MediumHorizontalSongCard(
                                song = song,
                                isCurrentSong = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.isPlaying,
                                onClick = {
                                    viewModel.playQueue(oldFavoritesSongs, index, "Old Favorites")
                                },
                                onLongClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }

        // 7. New Songs
        if (newSongs.isNotEmpty()) {
            item(key = "home_new_songs_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeSectionHeader(
                        title = "New Songs",
                        count = newSongs.size,
                        onViewAll = {
                            viewModel.openOrCreateDynamicPlaylist("New Songs", newSongs)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(newSongs.take(16), key = { _, s -> "new_${s.id}" }) { index, song ->
                            MediumHorizontalSongCard(
                                song = song,
                                isCurrentSong = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.isPlaying,
                                onClick = {
                                    viewModel.playQueue(newSongs, index, "New Songs")
                                },
                                onLongClick = { selectedSongForMenu = song }
                            )
                        }
                    }
                }
            }
        }

        // 8. Top Singers (72.dp circular avatars)
        if (topSingers.isNotEmpty()) {
            item(key = "home_top_singers_section") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HomeSectionHeader(
                        title = "Top Singers",
                        count = topSingers.size,
                        onViewAll = {
                            viewModel.selectTab(NavigationTab.ARTISTS)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(topSingers, key = { "singer_${it.first}" }) { (singerName, singerSongs) ->
                            TopSingerAvatarItem(
                                singerName = singerName,
                                songs = singerSongs,
                                onClick = {
                                    viewModel.openOrCreateDynamicPlaylist(singerName, singerSongs)
                                }
                            )
                        }
                    }
                }
            }
        }

        item(key = "home_made_in_india_footer") {
            MadeInIndiaFooter()
        }
    }

    // Song Options Bottom Sheet
    selectedSongForMenu?.let { targetSong ->
        HomeSongOptionsBottomSheet(
            song = targetSong,
            isFavorite = uiState.favoriteSongIds.contains(targetSong.id),
            onDismiss = { selectedSongForMenu = null },
            onPlayNow = {
                selectedSongForMenu = null
                viewModel.playSong(targetSong, "For You")
            },
            onPlayNext = {
                selectedSongForMenu = null
                viewModel.insertNext(targetSong)
            },
            onAddToQueue = {
                selectedSongForMenu = null
                viewModel.addToQueue(targetSong)
            },
            onToggleFavorite = {
                selectedSongForMenu = null
                viewModel.toggleFavorite(targetSong.id)
            },
            onAddToPlaylist = {
                selectedSongForMenu = null
                songToAddToPlaylist = targetSong
            },
            onEditTags = {
                selectedSongForMenu = null
                songToEditTags = targetSong
            },
            onTrimAudio = {
                selectedSongForMenu = null
                viewModel.openTrimDialog(targetSong)
            },
            onSetAsRingtone = {
                selectedSongForMenu = null
                songForRingtone = targetSong
            },
            onSongDetails = {
                selectedSongForMenu = null
                viewModel.openSongDetails(targetSong)
            },
            onHideSong = {
                selectedSongForMenu = null
                viewModel.excludeSong(targetSong.id)
            },
            onShare = {
                selectedSongForMenu = null
                viewModel.shareSong(context, targetSong)
            }
        )
    }

    var showCreatePlaylistForSong by remember { mutableStateOf(false) }

    if (songToAddToPlaylist != null && !showCreatePlaylistForSong) {
        val targetSong = songToAddToPlaylist!!
        val validSongIds = remember(uiState.songList) { uiState.songList.map { it.id }.toSet() }
        AlertDialog(
            onDismissRequest = { songToAddToPlaylist = null },
            title = { Text("Add to Playlist", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        TextButton(
                            onClick = { showCreatePlaylistForSong = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Playlist", maxLines = 1)
                        }
                    }
                    items(uiState.playlists) { playlist ->
                        val validCount = playlist.songIds.count { id -> validSongIds.contains(id) }
                        TextButton(
                            onClick = {
                                viewModel.addSongsToPlaylist(playlist.id, listOf(targetSong))
                                songToAddToPlaylist = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = playlist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "$validCount tracks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songToAddToPlaylist = null }) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    if (showCreatePlaylistForSong && songToAddToPlaylist != null) {
        var newPlaylistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistForSong = false },
            title = { Text("Create Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank() && songToAddToPlaylist != null) {
                            viewModel.createPlaylist(newPlaylistName.trim(), listOf(songToAddToPlaylist!!))
                            showCreatePlaylistForSong = false
                            songToAddToPlaylist = null
                        }
                    }
                ) {
                    Text("Create & Add", maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistForSong = false }) {
                    Text("Cancel", maxLines = 1)
                }
            }
        )
    }

    songForRingtone?.let { song ->
        LaunchedEffect(song) {
            viewModel.openTrimDialog(song)
            songForRingtone = null
        }
    }

    songToEditTags?.let { song ->
        TagEditorDialog(
            song = song,
            onDismissRequest = { songToEditTags = null },
            onSave = { newTitle, newArtist, newAlbum, newYear, newGenre ->
                coroutineScope.launch {
                    viewModel.updateSongMetadata(
                        songId = song.id,
                        newTitle = newTitle,
                        newArtist = newArtist,
                        newAlbum = newAlbum,
                        newYear = newYear,
                        newGenre = newGenre
                    )
                    songToEditTags = null
                }
            }
        )
    }
}

/**
 * Compact, Box-Free Editorial Mix Header Strip:
 * Zero heavy rectangular card container or thick border — uses only a whisper-subtle ambient
 * radial color wash behind clean edge-to-edge typography and a crisp 68.dp squircle lead artwork.
 */
@Composable
private fun EditorialMixHeaderStrip(
    mixConfig: HomeMixConfig,
    songs: List<Song>,
    onOpenMixPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val leadSong = remember(songs) {
        songs.firstOrNull { it.albumArtUri != null } ?: songs.firstOrNull()
    }
    val accentTint = if (mixConfig.badgeText == "FOR YOU") {
        MaterialTheme.colorScheme.primary
    } else {
        mixConfig.gradientColors.getOrElse(1) { MaterialTheme.colorScheme.primary }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMixPlaylist)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 14.dp)
            ) {
                // Compact pill tag ("FOR YOU • 25 SONGS" or category mix) bound strictly to dynamic accent
                Surface(
                    shape = CircleShape,
                    color = accentTint.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = mixConfig.icon,
                            contentDescription = null,
                            tint = accentTint,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${mixConfig.badgeText} • ${songs.size} SONGS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.75.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = true)
                            ),
                            color = accentTint,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = mixConfig.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = mixConfig.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Sleek compact "View All →" pill button (32.dp height, CircleShape)
                Surface(
                    onClick = onOpenMixPlaylist,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = "View All →",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Crisp 68.dp x 68.dp squircle lead artwork with subtle 0.8.dp white/15% hairline rim
            if (leadSong != null) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(leadSong.albumArtUri)
                            .size(200)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = leadSong.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            FallbackAlbumArt(title = leadSong.title, modifier = Modifier.fillMaxSize())
                        },
                        error = {
                            FallbackAlbumArt(title = leadSong.title, modifier = Modifier.fillMaxSize())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCategoryHeaderState(
    category: String,
    icon: ImageVector,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = "No $category tracks found in your library yet",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Surface(
            onClick = onShowAll,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
            modifier = Modifier.height(32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                Text(
                    text = "Back to All",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Clean Section Header: 16.sp SemiBold title, subtle count badge, and minimal "View All →" button.
 */
@Composable
private fun HomeSectionHeader(
    title: String,
    count: Int,
    onViewAll: () -> Unit,
    onManualRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (onManualRefresh != null) {
                IconButton(
                    onClick = onManualRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_refresh_recommendations_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh recommendations",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "View All →",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Clean Compact Horizontal Song Card:
 * 118.dp x 118.dp rounded 14.dp artwork with glowing emerald active border, subtle bottom scrim,
 * animated 4-bar Live Audio Equalizer Waveform badge anchored at BottomStart, and non-clipped
 * typography with safe vertical breathing room for Latin & Devanagari scripts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediumHorizontalSongCard(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cleanedTitle = remember(song.title, song.filePath) { cleanDisplayTitle(song.title, song.filePath) }
    val artistName = remember(song.artist) { cleanDisplayArtist(song.artist) }

    Column(
        modifier = modifier
            .width(118.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(
                width = if (isCurrentSong) 1.5.dp else 0.8.dp,
                color = if (isCurrentSong) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                }
            ),
            modifier = Modifier.size(118.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .size(256)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = cleanedTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize())
                    },
                    error = {
                        FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize())
                    }
                )

                if (isCurrentSong) {
                    // Subtle bottom gradient scrim so faces on cover art are never obscured
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.48f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )

                    // Compact frosted dark pill badge with 4-bar Live Audio Equalizer Waveform
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC0A0F0D))
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LiveEqualizerWaveform(
                            isPlaying = isPlaying,
                            barColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = cleanedTitle,
            style = TextStyle(
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artistName,
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}

/**
 * Flagship 4-bar Live Audio Equalizer Waveform:
 * Animates bar heights asynchronously (420ms / 560ms / 380ms / 500ms) while playing,
 * and collapses gently to calm resting bars with a tiny pause dot when paused.
 */
@Composable
private fun LiveEqualizerWaveform(
    isPlaying: Boolean,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "live_eq_waveform")
        val bar1 by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar_1"
        )
        val bar2 by transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar_2"
        )
        val bar3 by transition.animateFloat(
            initialValue = 0.30f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar_3"
        )
        val bar4 by transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_bar_4"
        )
        val fractions = listOf(bar1, bar2, bar3, bar4)
        Row(
            modifier = modifier.height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            fractions.forEach { frac ->
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .fillMaxHeight(frac.coerceIn(0.25f, 1.0f))
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }
    } else {
        Row(
            modifier = modifier.height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val restingHeights = listOf(0.32f, 0.48f, 0.36f, 0.28f)
            restingHeights.forEach { frac ->
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .fillMaxHeight(frac)
                        .clip(CircleShape)
                        .background(barColor.copy(alpha = 0.65f))
                )
            }
            Spacer(modifier = Modifier.width(1.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(3.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.78f))
            )
        }
    }
}

/**
 * Top Singers circular avatar at 72.dp with crisp 1-line singer name & track count below.
 */
@Composable
private fun TopSingerAvatarItem(
    singerName: String,
    songs: List<Song>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val representativeSong = remember(songs) {
        songs.firstOrNull { it.albumArtUri != null } ?: songs.firstOrNull()
    }

    Column(
        modifier = modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(
                0.8.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            ),
            modifier = Modifier.size(72.dp)
        ) {
            if (representativeSong?.albumArtUri != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(representativeSong.albumArtUri)
                        .size(200)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = singerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        FallbackAlbumArt(title = singerName, modifier = Modifier.fillMaxSize())
                    },
                    error = {
                        FallbackAlbumArt(title = singerName, modifier = Modifier.fillMaxSize())
                    }
                )
            } else {
                FallbackAlbumArt(title = singerName, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = singerName,
            style = TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = "${songs.size} songs",
            style = TextStyle(
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = true)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Smooth shimmer skeleton placeholders matching the exact balanced medium UI dimensions.
 */
@Composable
private fun HomeSkeletonLoading(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "home_skeleton")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 250f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Category chips placeholder
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .width(74.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerBrush)
                )
            }
        }

        // Top featured mix card placeholder (132.dp height, 16.dp radius)
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(shimmerBrush)
        )

        // Horizontal song card row placeholders (118.dp x 118.dp, 14.dp radius, 10.dp spacing)
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                }

                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(3) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(118.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(shimmerBrush)
                            )
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            }
        }

        // Top Singers circular placeholders (76.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(110.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(4) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(shimmerBrush)
                        )
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialCelebrationHomeBanner(
    specialDate: SpecialDateInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF881337).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = specialDate.celebrationIcon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = specialDate.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = specialDate.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeLibraryState(
    searchQuery: String,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (searchQuery.isNotBlank()) "No Matching Songs" else "No Songs Found",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (searchQuery.isNotBlank()) {
                    "No songs match \"$searchQuery\"."
                } else {
                    "Scan your device storage to load your music library."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRescan,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Music", maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSongOptionsBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onTrimAudio: () -> Unit,
    onSetAsRingtone: () -> Unit,
    onSongDetails: () -> Unit,
    onHideSong: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val artistName = remember(song.artist) { cleanDisplayArtist(song.artist) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(song.albumArtUri)
                            .size(144)
                            .crossfade(false)
                            .build(),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize()) },
                        error = { FallbackAlbumArt(title = song.title, modifier = Modifier.fillMaxSize()) }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$artistName • ${formatMillisToTime(song.duration)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            HomeSheetOptionRow(Icons.Rounded.PlayArrow, "Play Now", onPlayNow)
            HomeSheetOptionRow(Icons.Rounded.QueuePlayNext, "Play Next", onPlayNext)
            HomeSheetOptionRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to Queue", onAddToQueue)
            HomeSheetOptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to Playlist", onAddToPlaylist)
            HomeSheetOptionRow(Icons.Rounded.Edit, "Edit Song Info", onEditTags)
            HomeSheetOptionRow(Icons.Rounded.ContentCut, "Trim / Ringtone Cutter", onTrimAudio)
            HomeSheetOptionRow(Icons.Rounded.RingVolume, "Set as Ringtone", onSetAsRingtone)
            HomeSheetOptionRow(Icons.Rounded.Info, "Song Details", onSongDetails)
            HomeSheetOptionRow(Icons.Rounded.VisibilityOff, "Hide Song", onHideSong)
            HomeSheetOptionRow(Icons.Rounded.Share, "Share Song", onShare)
        }
    }
}

@Composable
private fun HomeSheetOptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
