package com.kexora.soniclet.ui.components.common

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.FolderInfo
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.ArtistParserUtil
import com.kexora.soniclet.utils.SearchRankingEngine
import com.kexora.soniclet.utils.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class SearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists")
}

/**
 * Full-Screen Frosted Blur, Flat Box-Free Global Search Overlay.
 *
 * - Consumes all pointer events over a full-screen frosted dark glass backdrop so underlying
 *   Home/Library content never bleeds through or intercepts taps.
 * - Executes 5-stage fuzzy, Hinglish phonetic, acronym, and Levenshtein typo-tolerant search
 *   asynchronously on [Dispatchers.Default] with 120ms debounce.
 */
@Composable
fun GlobalSearchOverlay(
    query: String,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    onCloseSearch: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenArtist: (String, List<Song>) -> Unit,
    onOpenAlbum: (String, List<Song>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenFolder: (FolderInfo) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onCloseSearch()
    }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var debouncedQuery by remember { mutableStateOf(query) }
    var searchResults by remember {
        mutableStateOf(
            SearchResults(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
        )
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    // 120ms debounced background search on Dispatchers.Default
    LaunchedEffect(
        query,
        uiState.songList,
        uiState.playlists,
        uiState.discoveredFolders,
        uiState.playCounts,
        uiState.excludedSongIds,
        uiState.excludedFolders
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            debouncedQuery = ""
            searchResults = SearchResults(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
        } else {
            delay(120L)
            val computed = withContext(Dispatchers.Default) {
                SearchRankingEngine.executeSearch(
                    query = trimmed,
                    allSongs = uiState.songList,
                    allPlaylists = uiState.playlists,
                    allFolders = uiState.discoveredFolders,
                    playCounts = uiState.playCounts,
                    excludedSongIds = uiState.excludedSongIds,
                    excludedFolders = uiState.excludedFolders
                )
            }
            debouncedQuery = trimmed
            searchResults = computed
        }
    }

    val playerBottomPad = LocalPlayerBottomPadding.current
    val bottomPadding = remember(playerBottomPad) {
        maxOf(110.dp, playerBottomPad + 16.dp)
    }

    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val frostedScrimColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { if (!it.isConsumed) it.consume() }
                    }
                }
            }
            .testTag("global_search_overlay")
    ) {
        // Layer 1: Subtle blurred current album-art backdrop (Android 12+ RenderEffect blur)
        val currentArtworkUri = uiState.currentSong?.albumArtUri
        if (currentArtworkUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentArtworkUri)
                    .size(256)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(24.dp)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer { alpha = if (isDarkSurface) 0.24f else 0.10f }
            )
        }

        // Layer 2: Rich opaque frosted glass scrim so Home never bleeds through
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(frostedScrimColor)
        )

        // Layer 3: Search Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Top 46.dp CircleShape Pill Search Bar (12.dp horizontal margin)
            SearchTopPillBar(
                query = query,
                focusRequester = focusRequester,
                onQueryChange = onSearchQueryChange,
                onClear = { onSearchQueryChange("") },
                onBack = {
                    keyboardController?.hide()
                    onCloseSearch()
                },
                onSearchAction = {
                    if (query.isNotBlank()) {
                        viewModel.addRecentSearch(query.trim())
                    }
                    keyboardController?.hide()
                }
            )

            // Edge-to-Edge Scope Chips (All, Songs, Artists, Albums, Playlists)
            SearchScopeChipsRow(
                selectedFilter = selectedFilter,
                hasActiveQuery = debouncedQuery.isNotBlank(),
                searchResults = searchResults,
                onSelectFilter = { selectedFilter = it }
            )

            if (query.isBlank()) {
                EmptyQueryDiscoveryView(
                    uiState = uiState,
                    viewModel = viewModel,
                    bottomPadding = bottomPadding,
                    onSelectQuery = { selected ->
                        onSearchQueryChange(selected)
                    },
                    onPlaySong = { song, contextList ->
                        viewModel.addRecentSearch(cleanDisplayTitle(song.title, song.filePath))
                        keyboardController?.hide()
                        onPlaySong(song, contextList)
                    },
                    onOpenArtist = { artistName, artistSongs ->
                        viewModel.addRecentSearch(artistName)
                        keyboardController?.hide()
                        onOpenArtist(artistName, artistSongs)
                    }
                )
            } else if (debouncedQuery.isNotBlank() && searchResults.isEmpty) {
                EmptySearchResultsView(
                    query = debouncedQuery,
                    onClear = { onSearchQueryChange("") }
                )
            } else {
                FlatSearchResultsList(
                    query = debouncedQuery,
                    selectedFilter = selectedFilter,
                    searchResults = searchResults,
                    uiState = uiState,
                    viewModel = viewModel,
                    bottomPadding = bottomPadding,
                    onPlaySong = { song, matchedList ->
                        viewModel.addRecentSearch(debouncedQuery)
                        keyboardController?.hide()
                        onPlaySong(song, matchedList)
                    },
                    onOpenArtist = { artistName, artistSongs ->
                        viewModel.addRecentSearch(debouncedQuery)
                        keyboardController?.hide()
                        onOpenArtist(artistName, artistSongs)
                    },
                    onOpenAlbum = { albumName, albumSongs ->
                        viewModel.addRecentSearch(debouncedQuery)
                        keyboardController?.hide()
                        onOpenAlbum(albumName, albumSongs)
                    },
                    onOpenPlaylist = { playlistId ->
                        viewModel.addRecentSearch(debouncedQuery)
                        keyboardController?.hide()
                        onOpenPlaylist(playlistId)
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchTopPillBar(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSearchAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                .testTag("search_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp)
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search songs, artists, albums...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("top_header_search_input")
                    )
                }

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("search_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScopeChipsRow(
    selectedFilter: SearchFilter,
    hasActiveQuery: Boolean,
    searchResults: SearchResults,
    onSelectFilter: (SearchFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(SearchFilter.entries, key = { it.name }) { filter ->
            val count = when (filter) {
                SearchFilter.ALL -> searchResults.songs.size + searchResults.artists.size + searchResults.albums.size + searchResults.playlists.size
                SearchFilter.SONGS -> searchResults.songs.size
                SearchFilter.ARTISTS -> searchResults.artists.size
                SearchFilter.ALBUMS -> searchResults.albums.size
                SearchFilter.PLAYLISTS -> searchResults.playlists.size
            }
            val isSelected = selectedFilter == filter
            val label = if (hasActiveQuery && count > 0 && filter != SearchFilter.ALL) {
                "${filter.label} ($count)"
            } else {
                filter.label
            }

            Surface(
                onClick = { onSelectFilter(filter) },
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.testTag("search_scope_${filter.name.lowercase()}")
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyQueryDiscoveryView(
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    bottomPadding: Dp,
    onSelectQuery: (String) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenArtist: (String, List<Song>) -> Unit
) {
    val visibleSongs = remember(uiState.songList, uiState.excludedSongIds, uiState.excludedFolders) {
        uiState.songList.filter { song ->
            !uiState.excludedSongIds.contains(song.id) &&
                uiState.excludedFolders.none { f -> f.isNotBlank() && song.folderPath.equals(f, ignoreCase = true) }
        }
    }

    val recentSearches = uiState.recentSearches

    val topArtists = remember(visibleSongs) {
        ArtistParserUtil.getIndividualArtistsMap(visibleSongs)
            .filterKeys { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            .toList()
            .sortedByDescending { it.second.size }
            .take(12)
    }

    val quickSuggestions = remember(visibleSongs, uiState.recentlyPlayedSongIds, uiState.playCounts, uiState.playDurations) {
        val songMap = visibleSongs.associateBy { it.id }
        val recent = uiState.recentlyPlayedSongIds.mapNotNull { songMap[it] }
        val frequent = visibleSongs
            .filter { (uiState.playCounts[it.id] ?: 0) > 0 || (uiState.playDurations[it.id] ?: 0L) > 0L }
            .sortedByDescending { (uiState.playCounts[it.id] ?: 0).toLong() * 1000L + (uiState.playDurations[it.id] ?: 0L) }
        val merged = LinkedHashMap<Long, Song>()
        recent.forEach { merged[it.id] = it }
        frequent.forEach { merged.putIfAbsent(it.id, it) }
        if (merged.size < 10) {
            visibleSongs.take(15).forEach { merged.putIfAbsent(it.id, it) }
        }
        merged.values.take(12).toList()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_discovery_list"),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Recent Searches (Removable CircleShape Pill Chips)
        if (recentSearches.isNotEmpty()) {
            item(key = "discovery_recent_searches") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { viewModel.clearRecentSearches() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentSearches, key = { "recent_q_$it" }) { searchItem ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSelectQuery(searchItem) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = searchItem,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.removeRecentSearch(searchItem) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove $searchItem",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Top Artists (Edge-to-Edge LazyRow of 64.dp Circular Avatars)
        if (topArtists.isNotEmpty()) {
            item(key = "discovery_top_artists") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Top Artists",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(topArtists, key = { "top_art_${it.first}" }) { (artistName, songs) ->
                            CompactArtistAvatar(
                                artistName = artistName,
                                songs = songs,
                                onClick = { onOpenArtist(artistName, songs) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Suggestions (Flat Box-Free Song Rows)
        if (quickSuggestions.isNotEmpty()) {
            item(key = "discovery_quick_suggestions_header") {
                Text(
                    text = "Quick Suggestions",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                )
            }

            items(quickSuggestions, key = { "qs_${it.id}" }) { song ->
                SongItemRow(
                    song = song,
                    isSelected = song.id == uiState.currentSong?.id,
                    isPlaying = song.id == uiState.currentSong?.id && uiState.isPlaying,
                    isFavorite = uiState.favoriteSongIds.contains(song.id),
                    horizontalPadding = 12.dp,
                    verticalPadding = 4.dp,
                    onSongClick = { onPlaySong(song, quickSuggestions) },
                    onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                    onInsertNext = { viewModel.insertNext(song) },
                    onAddToQueue = { viewModel.addToQueue(song) },
                    onOpenSongDetails = { viewModel.openSongDetails(song) }
                )
            }
        }
    }
}

@Composable
private fun FlatSearchResultsList(
    query: String,
    selectedFilter: SearchFilter,
    searchResults: SearchResults,
    uiState: MusicUiState,
    viewModel: MusicViewModel,
    bottomPadding: Dp,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenArtist: (String, List<Song>) -> Unit,
    onOpenAlbum: (String, List<Song>) -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    val showSongs = (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.SONGS) && searchResults.songs.isNotEmpty()
    val showArtists = (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.ARTISTS) && searchResults.artists.isNotEmpty()
    val showAlbums = (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.ALBUMS) && searchResults.albums.isNotEmpty()
    val showPlaylists = (selectedFilter == SearchFilter.ALL || selectedFilter == SearchFilter.PLAYLISTS) && searchResults.playlists.isNotEmpty()

    if (!showSongs && !showArtists && !showAlbums && !showPlaylists) {
        EmptySearchResultsView(query = query, onClear = {})
        return
    }

    val allMatchedSongs = remember(searchResults.songs) { searchResults.songs.map { it.song } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_results_list"),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Songs (Flat, Box-Free Rows with Query Highlighting)
        if (showSongs) {
            item(key = "res_header_songs") {
                SearchSectionHeader(title = "Songs", count = searchResults.songs.size)
            }
            val displayedSongs = if (selectedFilter == SearchFilter.SONGS) {
                searchResults.songs
            } else {
                searchResults.songs.take(8)
            }
            items(displayedSongs, key = { "res_song_${it.song.id}" }) { scoredSong ->
                val song = scoredSong.song
                SongItemRow(
                    song = song,
                    isSelected = song.id == uiState.currentSong?.id,
                    isPlaying = song.id == uiState.currentSong?.id && uiState.isPlaying,
                    isFavorite = uiState.favoriteSongIds.contains(song.id),
                    horizontalPadding = 12.dp,
                    verticalPadding = 4.dp,
                    highlightQuery = query,
                    onSongClick = { onPlaySong(song, allMatchedSongs) },
                    onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                    onInsertNext = { viewModel.insertNext(song) },
                    onAddToQueue = { viewModel.addToQueue(song) },
                    onOpenSongDetails = { viewModel.openSongDetails(song) }
                )
            }
        }

        // 2. Artists (Flat, Box-Free Rows)
        if (showArtists) {
            item(key = "res_header_artists") {
                SearchSectionHeader(
                    title = "Artists",
                    count = searchResults.artists.size,
                    modifier = Modifier.padding(top = if (showSongs) 12.dp else 0.dp)
                )
            }
            val displayedArtists = if (selectedFilter == SearchFilter.ARTISTS) {
                searchResults.artists
            } else {
                searchResults.artists.take(5)
            }
            items(displayedArtists, key = { "res_artist_${it.artistName}" }) { scoredArtist ->
                FlatSearchEntityRow(
                    title = scoredArtist.artistName,
                    subtitle = "${scoredArtist.songs.size} ${if (scoredArtist.songs.size == 1) "song" else "songs"}",
                    artworkUri = scoredArtist.songs.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                    fallbackTitle = scoredArtist.artistName,
                    fallbackIcon = Icons.Rounded.Person,
                    highlightQuery = query,
                    isCircularArt = true,
                    onClick = { onOpenArtist(scoredArtist.artistName, scoredArtist.songs) }
                )
            }
        }

        // 3. Albums (Flat, Box-Free Rows)
        if (showAlbums) {
            item(key = "res_header_albums") {
                SearchSectionHeader(
                    title = "Albums",
                    count = searchResults.albums.size,
                    modifier = Modifier.padding(top = if (showSongs || showArtists) 12.dp else 0.dp)
                )
            }
            val displayedAlbums = if (selectedFilter == SearchFilter.ALBUMS) {
                searchResults.albums
            } else {
                searchResults.albums.take(5)
            }
            items(displayedAlbums, key = { "res_album_${it.albumName}" }) { scoredAlbum ->
                FlatSearchEntityRow(
                    title = scoredAlbum.albumName,
                    subtitle = "${scoredAlbum.artist} • ${scoredAlbum.songs.size} ${if (scoredAlbum.songs.size == 1) "track" else "tracks"}",
                    artworkUri = scoredAlbum.songs.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                    fallbackTitle = scoredAlbum.albumName,
                    fallbackIcon = Icons.Rounded.Album,
                    highlightQuery = query,
                    isCircularArt = false,
                    onClick = { onOpenAlbum(scoredAlbum.albumName, scoredAlbum.songs) }
                )
            }
        }

        // 4. Playlists (Flat, Box-Free Rows)
        if (showPlaylists) {
            item(key = "res_header_playlists") {
                SearchSectionHeader(
                    title = "Playlists",
                    count = searchResults.playlists.size,
                    modifier = Modifier.padding(top = if (showSongs || showArtists || showAlbums) 12.dp else 0.dp)
                )
            }
            items(searchResults.playlists, key = { "res_pl_${it.playlist.id}" }) { scoredPlaylist ->
                FlatSearchEntityRow(
                    title = scoredPlaylist.playlist.name,
                    subtitle = "${scoredPlaylist.songs.size} ${if (scoredPlaylist.songs.size == 1) "track" else "tracks"}",
                    artworkUri = scoredPlaylist.songs.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                    fallbackTitle = scoredPlaylist.playlist.name,
                    fallbackIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                    highlightQuery = query,
                    isCircularArt = false,
                    onClick = { onOpenPlaylist(scoredPlaylist.playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompactArtistAvatar(
    artistName: String,
    songs: List<Song>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val coverUri = remember(songs) { songs.firstOrNull { it.albumArtUri != null }?.albumArtUri }

    Column(
        modifier = Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            if (coverUri != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUri)
                        .size(160)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { FallbackAlbumArt(title = artistName, modifier = Modifier.fillMaxSize()) },
                    error = { FallbackAlbumArt(title = artistName, modifier = Modifier.fillMaxSize()) }
                )
            } else {
                FallbackAlbumArt(title = artistName, modifier = Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artistName,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FlatSearchEntityRow(
    title: String,
    subtitle: String,
    artworkUri: android.net.Uri?,
    fallbackTitle: String,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    highlightQuery: String,
    isCircularArt: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val highlightColor = MaterialTheme.colorScheme.primary
    val titleAnnotated = remember(title, highlightQuery, highlightColor) {
        buildHighlightedText(title, highlightQuery, highlightColor)
    }
    val subtitleAnnotated = remember(subtitle, highlightQuery, highlightColor) {
        buildHighlightedText(subtitle, highlightQuery, highlightColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val shape = if (isCircularArt) CircleShape else RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (artworkUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkUri)
                            .size(120)
                            .crossfade(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            Icon(
                                imageVector = fallbackIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                } else {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = titleAnnotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleAnnotated,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 68.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun EmptySearchResultsView(
    query: String,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = "No results for \"$query\"",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Try searching by song title, artist name, initials, or Hinglish spelling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
