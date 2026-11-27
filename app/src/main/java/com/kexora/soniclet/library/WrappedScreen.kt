package com.kexora.soniclet.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.kexora.soniclet.LocalPlayerBottomPadding
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.MusicUiState
import com.kexora.soniclet.player.MusicViewModel
import com.kexora.soniclet.ui.components.common.MadeInIndiaFooter
import com.kexora.soniclet.ui.components.common.cleanDisplayTitle
import com.kexora.soniclet.ui.components.common.scrollFade
import com.kexora.soniclet.ui.components.player.FallbackAlbumArt
import com.kexora.soniclet.utils.ArtistParserUtil
import kotlinx.coroutines.delay
import java.util.Locale

private val WrappedGreenPillBg = Color(0xFF1DB954)
private val WrappedGreenPillText = Color(0xFF041D0D)
private val WrappedGoldBadge = Color(0xFFFFD54F)

private data class WrappedTrackEntry(
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val totalDurationMs: Long,
    val playCount: Int,
    val lastPlayedTimestamp: Long,
    val liveSong: Song?,
    val isRemoved: Boolean
)

private data class WrappedRankedGroup(
    val name: String,
    val subtitle: String,
    val totalDurationMs: Long,
    val artworkUri: Uri?,
    val fallbackTitle: String
)

private data class WrappedStatCapsule(
    val id: String,
    val label: String,
    val value: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * Dedicated Persistent Soniclet Wrapped screen backed by Room [ListeningHistoryRepository].
 *
 * - Survives device file deletion (deleted tracks stay visible with a subtle "Removed" pill badge
 *   and can be manually removed from Wrapped history).
 * - Uses modern stadium-pill & capsule geometry throughout.
 * - Filters placeholder metadata ("Unknown Artist", "<unknown>", "SnapTube Video", "Voice Recording",
 *   "WhatsApp Audio") from #1 Top Artist and Top Album rankings whenever real metadata exists.
 */
@Composable
fun WrappedScreen(
    uiState: MusicUiState,
    viewModel: MusicViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isStoryOpen by remember { mutableStateOf(false) }
    var entryPendingRemoval by remember { mutableStateOf<WrappedTrackEntry?>(null) }

    val availableSongs = remember(uiState.allSongsList, uiState.songList) {
        if (uiState.allSongsList.isNotEmpty()) uiState.allSongsList else uiState.songList
    }

    // Merge Room persistent listening history with live library songs (never erasing deleted files)
    val wrappedEntries = remember(
        uiState.listeningHistory,
        availableSongs,
        uiState.playDurations,
        uiState.playCounts,
        uiState.lastPlayedTimestamps
    ) {
        val songById = availableSongs.associateBy { it.id }
        val songByCanonicalKey = availableSongs.associateBy { ListeningHistoryRepository.buildCanonicalSongKey(it) }

        if (uiState.listeningHistory.isNotEmpty()) {
            uiState.listeningHistory
                .filter { it.totalDurationListenedMs >= ListeningHistoryRepository.MIN_QUALIFIED_LISTEN_MS || it.playCount > 0 }
                .map { entity ->
                    val matchedSong = songById[entity.lastKnownSongId] ?: songByCanonicalKey[entity.songKey]
                    WrappedTrackEntry(
                        songKey = entity.songKey,
                        title = matchedSong?.title?.ifBlank { entity.title } ?: entity.title,
                        artist = ListeningHistoryRepository.normalizeDisplayArtist(
                            matchedSong?.artist?.ifBlank { entity.artist } ?: entity.artist
                        ),
                        album = ListeningHistoryRepository.normalizeDisplayAlbum(
                            matchedSong?.album?.ifBlank { entity.album } ?: entity.album
                        ),
                        genre = matchedSong?.genre?.ifBlank { entity.genre } ?: entity.genre,
                        totalDurationMs = entity.totalDurationListenedMs,
                        playCount = entity.playCount.coerceAtLeast(1),
                        lastPlayedTimestamp = entity.lastPlayedTimestamp,
                        liveSong = matchedSong,
                        isRemoved = matchedSong == null
                    )
                }
                .sortedWith(
                    compareByDescending<WrappedTrackEntry> { it.totalDurationMs }
                        .thenByDescending { it.playCount }
                        .thenByDescending { it.lastPlayedTimestamp }
                )
        } else {
            // Immediate non-empty view while Room backfill finishes
            availableSongs.mapNotNull { song ->
                val dur = uiState.playDurations[song.id] ?: 0L
                val count = uiState.playCounts[song.id] ?: 0
                if (dur < ListeningHistoryRepository.MIN_QUALIFIED_LISTEN_MS && count <= 0) return@mapNotNull null
                val effectiveDur = if (dur >= ListeningHistoryRepository.MIN_QUALIFIED_LISTEN_MS) {
                    dur
                } else {
                    count.toLong() * song.duration.coerceAtLeast(30_000L)
                }
                WrappedTrackEntry(
                    songKey = ListeningHistoryRepository.buildCanonicalSongKey(song),
                    title = song.title,
                    artist = ListeningHistoryRepository.normalizeDisplayArtist(song.artist),
                    album = ListeningHistoryRepository.normalizeDisplayAlbum(song.album),
                    genre = song.genre,
                    totalDurationMs = effectiveDur,
                    playCount = count.coerceAtLeast(1),
                    lastPlayedTimestamp = uiState.lastPlayedTimestamps[song.id] ?: 0L,
                    liveSong = song,
                    isRemoved = false
                )
            }.sortedWith(
                compareByDescending<WrappedTrackEntry> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
        }
    }

    val totalListeningTimeMs = remember(wrappedEntries) {
        wrappedEntries.sumOf { it.totalDurationMs }
    }
    val totalPlaysCount = remember(wrappedEntries) {
        wrappedEntries.sumOf { it.playCount }
    }
    val uniqueTracksCount = wrappedEntries.size
    val topTrack = wrappedEntries.firstOrNull()

    // Top Artists — strictly exclude placeholder metadata whenever valid named artists exist
    val topArtists = remember(wrappedEntries) {
        data class ArtistBucket(
            var displayName: String,
            var durationMs: Long = 0L,
            var plays: Int = 0,
            var trackCount: Int = 0,
            var artworkUri: Uri? = null,
            var fallbackTitle: String = ""
        )

        val validBuckets = linkedMapOf<String, ArtistBucket>()
        val fallbackBuckets = linkedMapOf<String, ArtistBucket>()

        for (entry in wrappedEntries) {
            val splitNames = ArtistParserUtil.splitArtists(entry.artist, entry.title)
                .map { ListeningHistoryRepository.normalizeDisplayArtist(it) }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .ifEmpty { listOf(entry.artist) }

            val validNames = splitNames.filter { !ListeningHistoryRepository.isPlaceholderMetadata(it) }
            val targetMap = if (validNames.isNotEmpty()) validBuckets else fallbackBuckets
            val chosenNames = if (validNames.isNotEmpty()) validNames else splitNames

            for (name in chosenNames) {
                val key = name.lowercase(Locale.ROOT)
                val bucket = targetMap.getOrPut(key) {
                    ArtistBucket(displayName = name, fallbackTitle = name)
                }
                bucket.durationMs += entry.totalDurationMs
                bucket.plays += entry.playCount
                bucket.trackCount += 1
                if (bucket.artworkUri == null && entry.liveSong?.albumArtUri != null) {
                    bucket.artworkUri = entry.liveSong.albumArtUri
                }
            }
        }

        val activeBuckets = if (validBuckets.isNotEmpty()) validBuckets.values else fallbackBuckets.values
        activeBuckets
            .sortedWith(compareByDescending<ArtistBucket> { it.durationMs }.thenByDescending { it.plays })
            .take(6)
            .map { bucket ->
                WrappedRankedGroup(
                    name = bucket.displayName,
                    subtitle = "${bucket.trackCount} ${if (bucket.trackCount == 1) "track" else "tracks"} • ${bucket.plays} plays",
                    totalDurationMs = bucket.durationMs,
                    artworkUri = bucket.artworkUri,
                    fallbackTitle = bucket.displayName
                )
            }
    }

    // Top Albums — strictly exclude placeholder metadata whenever valid named albums exist
    val topAlbums = remember(wrappedEntries) {
        data class AlbumBucket(
            var displayName: String,
            var primaryArtist: String,
            var durationMs: Long = 0L,
            var trackCount: Int = 0,
            var artworkUri: Uri? = null
        )

        val validBuckets = linkedMapOf<String, AlbumBucket>()
        val fallbackBuckets = linkedMapOf<String, AlbumBucket>()

        for (entry in wrappedEntries) {
            val albumName = ListeningHistoryRepository.normalizeDisplayAlbum(entry.album)
            val isPlaceholder = ListeningHistoryRepository.isPlaceholderMetadata(albumName)
            val targetMap = if (!isPlaceholder) validBuckets else fallbackBuckets
            val key = albumName.lowercase(Locale.ROOT)
            val bucket = targetMap.getOrPut(key) {
                AlbumBucket(
                    displayName = albumName,
                    primaryArtist = entry.artist
                )
            }
            bucket.durationMs += entry.totalDurationMs
            bucket.trackCount += 1
            if (ListeningHistoryRepository.isPlaceholderMetadata(bucket.primaryArtist) &&
                !ListeningHistoryRepository.isPlaceholderMetadata(entry.artist)
            ) {
                bucket.primaryArtist = entry.artist
            }
            if (bucket.artworkUri == null && entry.liveSong?.albumArtUri != null) {
                bucket.artworkUri = entry.liveSong.albumArtUri
            }
        }

        val activeBuckets = if (validBuckets.isNotEmpty()) validBuckets.values else fallbackBuckets.values
        activeBuckets
            .sortedByDescending { it.durationMs }
            .take(5)
            .map { bucket ->
                val artistLabel = if (ListeningHistoryRepository.isPlaceholderMetadata(bucket.primaryArtist)) {
                    "${bucket.trackCount} ${if (bucket.trackCount == 1) "track" else "tracks"}"
                } else {
                    "${bucket.primaryArtist} • ${bucket.trackCount} ${if (bucket.trackCount == 1) "track" else "tracks"}"
                }
                WrappedRankedGroup(
                    name = bucket.displayName,
                    subtitle = artistLabel,
                    totalDurationMs = bucket.durationMs,
                    artworkUri = bucket.artworkUri,
                    fallbackTitle = bucket.displayName
                )
            }
    }

    val topGenresCount = remember(wrappedEntries) {
        wrappedEntries
            .map { it.genre.trim() }
            .filter { !ListeningHistoryRepository.isPlaceholderMetadata(it) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .size
    }

    val sonicPersona = remember(totalListeningTimeMs, topArtists, topGenresCount) {
        when {
            totalListeningTimeMs >= 7_200_000L -> "Sonic Marathoner"
            topArtists.isNotEmpty() && totalListeningTimeMs > 0L &&
                (topArtists.first().totalDurationMs.toFloat() / totalListeningTimeMs.toFloat()) > 0.45f -> "Artist Devotee"
            topGenresCount >= 3 -> "Genre Explorer"
            totalListeningTimeMs >= 1_800_000L -> "Audio Enthusiast"
            else -> "Sound Discoverer"
        }
    }

    val personaTagline = remember(sonicPersona) {
        when (sonicPersona) {
            "Sonic Marathoner" -> "Non-stop musical voyages spanning hours of pure immersion."
            "Artist Devotee" -> "Unshakable loyalty to your favorite voices and signature sound."
            "Genre Explorer" -> "Fearlessly roaming across acoustic, rhythmic, and soulful vibes."
            "Audio Enthusiast" -> "A refined ear for timeless melodies and rich fidelity."
            else -> "Unlocking fresh rhythms and building your permanent audio story."
        }
    }

    val personaEmoji = remember(sonicPersona) {
        when (sonicPersona) {
            "Sonic Marathoner" -> "🎧"
            "Artist Devotee" -> "🌟"
            "Genre Explorer" -> "🚀"
            "Audio Enthusiast" -> "🎶"
            else -> "✨"
        }
    }

    val topArtistName = topArtists.firstOrNull()?.name ?: "—"
    val playableTopSongs = remember(wrappedEntries) { wrappedEntries.mapNotNull { it.liveSong } }

    val statCapsules = remember(
        sonicPersona,
        totalListeningTimeMs,
        uniqueTracksCount,
        topArtistName,
        totalPlaysCount
    ) {
        listOf(
            WrappedStatCapsule(
                id = "dna",
                label = "Audio DNA",
                value = sonicPersona,
                icon = Icons.Rounded.GraphicEq,
                accent = Color(0xFF7C4DFF)
            ),
            WrappedStatCapsule(
                id = "playtime",
                label = "Playtime",
                value = formatWrappedDuration(totalListeningTimeMs),
                icon = Icons.Rounded.Schedule,
                accent = WrappedGreenPillBg
            ),
            WrappedStatCapsule(
                id = "tracks",
                label = "Tracks",
                value = uniqueTracksCount.toString(),
                icon = Icons.Rounded.LibraryMusic,
                accent = Color(0xFF00B8D4)
            ),
            WrappedStatCapsule(
                id = "top_artist",
                label = "Top Artist",
                value = topArtistName,
                icon = Icons.Rounded.Person,
                accent = Color(0xFFFF6D00)
            ),
            WrappedStatCapsule(
                id = "total_plays",
                label = "Total Plays",
                value = totalPlaysCount.toString(),
                icon = Icons.Rounded.Repeat,
                accent = Color(0xFFE91E63)
            )
        )
    }

    val listState = rememberLazyListState()
    val playerBottomPad = LocalPlayerBottomPadding.current
    val resolvedBottomPadding = remember(playerBottomPad) {
        maxOf(110.dp, playerBottomPad + 20.dp)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .scrollFade(listState)
            .testTag("wrapped_screen"),
        contentPadding = PaddingValues(top = 0.dp, bottom = resolvedBottomPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Borderless Compact Edge-to-Edge Hero Header
        item(key = "wrapped_hero_banner") {
            WrappedHeroBanner(
                persona = sonicPersona,
                tagline = personaTagline,
                totalDurationMs = totalListeningTimeMs,
                tracksCount = uniqueTracksCount,
                hasPlayableTracks = playableTopSongs.isNotEmpty(),
                hasHistory = wrappedEntries.isNotEmpty(),
                onViewStory = { isStoryOpen = true },
                onShare = {
                    shareWrappedRecap(
                        context = context,
                        totalListeningTime = totalListeningTimeMs,
                        tracksCount = uniqueTracksCount,
                        topTrack = topTrack,
                        topArtist = topArtistName,
                        persona = sonicPersona
                    )
                },
                onPlayTop = {
                    if (playableTopSongs.isNotEmpty()) {
                        viewModel?.playQueue(playableTopSongs, 0, "Soniclet Wrapped")
                    }
                }
            )
        }

        // 2. Edge-to-Edge Horizontal Stat Strip (36.dp height pills)
        item(key = "wrapped_stat_capsules") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(statCapsules, key = { it.id }) { capsule ->
                    WrappedStatPillCapsule(capsule = capsule)
                }
            }
        }

        if (wrappedEntries.isEmpty()) {
            item(key = "wrapped_empty_state") {
                WrappedEmptyHistoryCard(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else {
            // 3. #1 Top Track Flat Row
            if (topTrack != null) {
                item(key = "wrapped_top_track_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        WrappedSectionTitle(
                            title = "#1 Top Track",
                            badge = "${topTrack.playCount} plays",
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        WrappedTrackPillRow(
                            rank = 1,
                            entry = topTrack,
                            isCurrentSong = topTrack.liveSong?.id != null && topTrack.liveSong.id == uiState.currentSong?.id,
                            onClick = {
                                if (topTrack.liveSong != null) {
                                    val idx = playableTopSongs.indexOfFirst { it.id == topTrack.liveSong.id }.coerceAtLeast(0)
                                    viewModel?.playQueue(playableTopSongs, idx, "Soniclet Wrapped")
                                } else {
                                    entryPendingRemoval = topTrack
                                }
                            },
                            onLongClick = { entryPendingRemoval = topTrack },
                            onRemoveClick = { entryPendingRemoval = topTrack }
                        )
                    }
                }
            }

            // 4. Most Played Flat Rows (Retains removed files with subtle "Removed" pill badge)
            item(key = "wrapped_most_played_header") {
                WrappedSectionTitle(
                    title = "Most Played",
                    badge = "${wrappedEntries.size} saved",
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            itemsIndexed(
                items = wrappedEntries.take(20),
                key = { _, entry -> "wrapped_track_${entry.songKey}" }
            ) { index, entry ->
                WrappedTrackPillRow(
                    rank = index + 1,
                    entry = entry,
                    isCurrentSong = entry.liveSong?.id != null && entry.liveSong.id == uiState.currentSong?.id,
                    onClick = {
                        if (entry.liveSong != null) {
                            val idx = playableTopSongs.indexOfFirst { it.id == entry.liveSong.id }.coerceAtLeast(0)
                            viewModel?.playQueue(playableTopSongs, idx, "Most Played")
                        } else {
                            entryPendingRemoval = entry
                        }
                    },
                    onLongClick = { entryPendingRemoval = entry },
                    onRemoveClick = { entryPendingRemoval = entry }
                )
            }

            // 5. Top Artists Flat Rows
            if (topArtists.isNotEmpty()) {
                item(key = "wrapped_top_artists_header") {
                    WrappedSectionTitle(
                        title = "Top Artists",
                        badge = "${topArtists.size}",
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
                    )
                }

                itemsIndexed(
                    items = topArtists,
                    key = { idx, item -> "wrapped_artist_${idx}_${item.name}" }
                ) { index, artistItem ->
                    WrappedRankedGroupPillRow(
                        rank = index + 1,
                        item = artistItem,
                        iconFallback = Icons.Rounded.Person
                    )
                }
            }

            // 6. Top Albums Flat Rows
            if (topAlbums.isNotEmpty()) {
                item(key = "wrapped_top_albums_header") {
                    WrappedSectionTitle(
                        title = "Top Albums",
                        badge = "${topAlbums.size}",
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
                    )
                }

                itemsIndexed(
                    items = topAlbums,
                    key = { idx, item -> "wrapped_album_${idx}_${item.name}" }
                ) { index, albumItem ->
                    WrappedRankedGroupPillRow(
                        rank = index + 1,
                        item = albumItem,
                        iconFallback = Icons.Rounded.Album
                    )
                }
            }
        }

        item(key = "wrapped_footer") {
            Spacer(modifier = Modifier.height(16.dp))
            MadeInIndiaFooter()
        }
    }

    // Manual Removal Dialog (Allows removing deleted or any entry from permanent Wrapped history)
    entryPendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { entryPendingRemoval = null },
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    text = if (target.isRemoved) "Track Removed From Device" else "Remove From Wrapped?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (target.isRemoved) {
                        "\"${target.title}\" by ${target.artist} is no longer on this device, but its listening stats (${formatWrappedDuration(target.totalDurationMs)}) are preserved in Wrapped. Remove it permanently from Wrapped history?"
                    } else {
                        "Remove \"${target.title}\" (${formatWrappedDuration(target.totalDurationMs)}) from your Wrapped listening history?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel?.removeWrappedHistoryItem(target.songKey)
                        entryPendingRemoval = null
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("wrapped_confirm_remove_btn")
                ) {
                    Text("Remove from History", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingRemoval = null }) {
                    Text("Keep")
                }
            }
        )
    }

    // Interactive Full-Screen Wrapped Recap Story
    if (isStoryOpen) {
        WrappedStoryDialog(
            totalListeningTime = totalListeningTimeMs,
            trackCount = uniqueTracksCount,
            topTrack = topTrack,
            topArtists = topArtists.map { it.name to it.totalDurationMs },
            persona = sonicPersona,
            personaEmoji = personaEmoji,
            personaTagline = personaTagline,
            onDismiss = { isStoryOpen = false },
            onShare = {
                shareWrappedRecap(
                    context = context,
                    totalListeningTime = totalListeningTimeMs,
                    tracksCount = uniqueTracksCount,
                    topTrack = topTrack,
                    topArtist = topArtistName,
                    persona = sonicPersona
                )
            }
        )
    }
}

@Composable
private fun WrappedHeroBanner(
    persona: String,
    tagline: String,
    totalDurationMs: Long,
    tracksCount: Int,
    hasPlayableTracks: Boolean,
    hasHistory: Boolean,
    onViewStory: () -> Unit,
    onShare: () -> Unit,
    onPlayTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val ambientWash = remember(primary) {
        Brush.verticalGradient(
            colors = listOf(
                primary.copy(alpha = 0.16f),
                primary.copy(alpha = 0.05f),
                Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ambientWash)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .testTag("wrapped_hero_banner")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "SONICLET WRAPPED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                fontSize = 10.sp
                            ),
                            color = primary
                        )
                    }
                }

                if (totalDurationMs > 0L) {
                    Text(
                        text = "${formatWrappedDuration(totalDurationMs)} • $tracksCount tracks",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = persona,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasHistory) {
                        tagline
                    } else {
                        "Listen to any track for 15+ seconds to unlock your persistent listening story."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onViewStory,
                    enabled = hasHistory,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WrappedGreenPillBg,
                        contentColor = WrappedGreenPillText,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("wrapped_view_recap_story_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View Recap",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )
                }

                // Circular Play Top Tracks Button (40.dp)
                Surface(
                    onClick = onPlayTop,
                    enabled = hasPlayableTracks,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (hasPlayableTracks) 0.55f else 0.25f),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("wrapped_play_top_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play Top Tracks",
                            tint = if (hasPlayableTracks) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                // Circular Share Button (40.dp)
                Surface(
                    onClick = onShare,
                    enabled = hasHistory,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (hasHistory) 0.55f else 0.25f),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("wrapped_share_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share Wrapped Recap",
                            tint = if (hasHistory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedStatPillCapsule(
    capsule: WrappedStatCapsule,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(capsule.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = capsule.icon,
                    contentDescription = null,
                    tint = capsule.accent,
                    modifier = Modifier.size(13.dp)
                )
            }

            Text(
                text = capsule.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Text(
                text = capsule.value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WrappedSectionTitle(
    title: String,
    badge: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WrappedTrackPillRow(
    rank: Int,
    entry: WrappedTrackEntry,
    isCurrentSong: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cleanedTitle = remember(entry.title, entry.liveSong?.filePath) {
        cleanDisplayTitle(entry.title, entry.liveSong?.filePath ?: "")
    }
    val rowBg = if (isCurrentSong) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("wrapped_track_row_$rank")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBg)
        ) {
            if (isCurrentSong) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(vertical = 8.dp)
                        .width(3.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular Artwork (44.dp) with overlaid Rank Badge
                Box(modifier = Modifier.size(46.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .border(
                                width = if (rank == 1) 1.dp else 0.5.dp,
                                color = if (rank == 1) WrappedGoldBadge.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                                shape = CircleShape
                            )
                    ) {
                        if (entry.liveSong?.albumArtUri != null) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(entry.liveSong.albumArtUri)
                                    .size(128)
                                    .crossfade(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = cleanedTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = { FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize()) },
                                error = { FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize()) }
                            )
                        } else {
                            FallbackAlbumArt(title = cleanedTitle, modifier = Modifier.fillMaxSize())
                        }
                    }

                    // Compact circular Rank Badge
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(if (rank == 1) WrappedGoldBadge else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$rank",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = if (rank == 1) Color.Black else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = cleanedTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (entry.isRemoved) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "Removed",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${entry.artist} • ${entry.playCount} ${if (entry.playCount == 1) "play" else "plays"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Compact Emerald Listening-Time Pill
                Surface(
                    shape = CircleShape,
                    color = WrappedGreenPillBg.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = formatWrappedDuration(entry.totalDurationMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = WrappedGreenPillBg,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                if (entry.isRemoved) {
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("wrapped_remove_deleted_$rank")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Remove from history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 68.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f))
        )
    }
}

@Composable
private fun WrappedRankedGroupPillRow(
    rank: Int,
    item: WrappedRankedGroup,
    iconFallback: ImageVector,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(46.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.artworkUri != null) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.artworkUri)
                                .size(128)
                                .crossfade(false)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Icon(
                                    imageVector = iconFallback,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            error = {
                                FallbackAlbumArt(title = item.fallbackTitle, modifier = Modifier.fillMaxSize())
                            }
                        )
                    } else {
                        FallbackAlbumArt(title = item.fallbackTitle, modifier = Modifier.fillMaxSize())
                    }
                }

                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(if (rank == 1) WrappedGoldBadge else MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = if (rank == 1) Color.Black else MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = WrappedGreenPillBg.copy(alpha = 0.16f)
            ) {
                Text(
                    text = formatWrappedDuration(item.totalDurationMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = WrappedGreenPillBg,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 68.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f))
        )
    }
}

@Composable
private fun WrappedEmptyHistoryCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(WrappedGreenPillBg.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = WrappedGreenPillBg,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "Your Listening Story Starts Here",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Play any track for at least 15 seconds and Soniclet will permanently record your playtime, top artists, and albums — even if files are later moved or removed.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WrappedStoryDialog(
    totalListeningTime: Long,
    trackCount: Int,
    topTrack: WrappedTrackEntry?,
    topArtists: List<Pair<String, Long>>,
    persona: String,
    personaEmoji: String,
    personaTagline: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val totalStories = 5
    var currentStoryIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    LaunchedEffect(currentStoryIndex, isPaused) {
        if (!isPaused) {
            delay(5000L)
            if (currentStoryIndex < totalStories - 1) {
                currentStoryIndex++
            } else {
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val backgroundBrush = when (currentStoryIndex) {
            0 -> Brush.verticalGradient(listOf(Color(0xFF0A3D2E), Color(0xFF1DB954), Color(0xFF061E16)))
            1 -> Brush.verticalGradient(listOf(Color(0xFF880E4F), Color(0xFFE91E63), Color(0xFF2B071B)))
            2 -> Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF00ACC1), Color(0xFF0D1B2A)))
            3 -> Brush.verticalGradient(listOf(Color(0xFF4A148C), Color(0xFF7C4DFF), Color(0xFF1A0933)))
            else -> Brush.verticalGradient(listOf(Color(0xFF101820), Color(0xFF1B3B2B), Color(0xFF090D12)))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width * 0.35f) {
                                if (currentStoryIndex > 0) currentStoryIndex--
                            } else {
                                if (currentStoryIndex < totalStories - 1) {
                                    currentStoryIndex++
                                } else {
                                    onDismiss()
                                }
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    for (i in 0 until totalStories) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        i <= currentStoryIndex -> Color.White
                                        else -> Color.White.copy(alpha = 0.28f)
                                    }
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = CircleShape) {
                        Text(
                            text = "SONICLET RECAP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Story",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                AnimatedContent(
                    targetState = currentStoryIndex,
                    transitionSpec = {
                        fadeIn(tween(280)) + slideInHorizontally { it / 3 } togetherWith
                            fadeOut(tween(180)) + slideOutHorizontally { -it / 3 }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "wrapped_story_slide"
                ) { slideIndex ->
                    when (slideIndex) {
                        0 -> WrappedStorySlideOverview(totalListeningTime, trackCount)
                        1 -> WrappedStorySlideTopTrack(topTrack)
                        2 -> WrappedStorySlideTopArtists(topArtists)
                        3 -> WrappedStorySlidePersona(persona, personaEmoji, personaTagline)
                        else -> WrappedStorySlideSummary(
                            totalListeningTime = totalListeningTime,
                            topTrack = topTrack,
                            persona = persona,
                            personaEmoji = personaEmoji,
                            topArtist = topArtists.firstOrNull()?.first ?: "—",
                            onShare = onShare
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (currentStoryIndex == totalStories - 1) "Tap to finish • Hold to pause" else "Tap right to skip • Hold to pause",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun WrappedStorySlideOverview(totalDuration: Long, trackCount: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YOUR TOTAL VIBE",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.88f),
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = formatWrappedDuration(totalDuration),
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Saved permanently in Soniclet Wrapped",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Surface(color = Color.White.copy(alpha = 0.2f), shape = CircleShape) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Color.White)
                Text(
                    text = "$trackCount Tracks Explored",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun WrappedStorySlideTopTrack(topTrack: WrappedTrackEntry?) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = WrappedGoldBadge, shape = CircleShape) {
            Text(
                text = "#1 TOP TRACK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (topTrack != null) {
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            ) {
                if (topTrack.liveSong?.albumArtUri != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(topTrack.liveSong.albumArtUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = topTrack.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = { FallbackAlbumArt(title = topTrack.title, modifier = Modifier.fillMaxSize()) }
                    )
                } else {
                    FallbackAlbumArt(title = topTrack.title, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = topTrack.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = topTrack.artist,
                fontSize = 15.5.sp,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(color = Color.White.copy(alpha = 0.22f), shape = CircleShape) {
                Text(
                    text = "Listened for ${formatWrappedDuration(topTrack.totalDurationMs)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun WrappedStorySlideTopArtists(topArtists: List<Pair<String, Long>>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YOUR TOP ARTISTS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        topArtists.take(5).forEachIndexed { idx, (artist, dur) ->
            Surface(
                color = if (idx == 0) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${idx + 1}. $artist",
                        fontWeight = if (idx == 0) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = if (idx == 0) 15.5.sp else 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatWrappedDuration(dur),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WrappedStorySlidePersona(persona: String, emoji: String, tagline: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YOUR AUDIO DNA",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 46.sp)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = persona,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = tagline,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

@Composable
private fun WrappedStorySlideSummary(
    totalListeningTime: Long,
    topTrack: WrappedTrackEntry?,
    persona: String,
    personaEmoji: String,
    topArtist: String,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.16f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SONICLET WRAPPED",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.5.sp,
                    letterSpacing = 1.1.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Playtime", fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
                        Text(formatWrappedDuration(totalListeningTime), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Audio DNA", fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
                        Text("$persona $personaEmoji", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
                if (topTrack != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Top Track", fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
                        Text(topTrack.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(topTrack.artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (topArtist.isNotBlank() && topArtist != "—") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Top Artist", fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f))
                        Text(topArtist, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onShare,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Recap", fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}

private fun formatWrappedDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    if (totalSeconds <= 0L) return "0m"
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun shareWrappedRecap(
    context: Context,
    totalListeningTime: Long,
    tracksCount: Int,
    topTrack: WrappedTrackEntry?,
    topArtist: String,
    persona: String
) {
    val summaryText = buildString {
        appendLine("My Soniclet Wrapped")
        appendLine("=======================")
        appendLine("Playtime: ${formatWrappedDuration(totalListeningTime)}")
        appendLine("Tracks Explored: $tracksCount")
        if (topTrack != null && topTrack.totalDurationMs > 0L) {
            appendLine("#1 Track: ${topTrack.title} by ${topTrack.artist}")
        }
        if (topArtist.isNotBlank() && topArtist != "—") {
            appendLine("Top Artist: $topArtist")
        }
        appendLine("Audio DNA: $persona")
        appendLine("=======================")
        appendLine("Crafted with Soniclet")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "My Soniclet Wrapped")
        putExtra(Intent.EXTRA_TEXT, summaryText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Soniclet Wrapped"))
}
