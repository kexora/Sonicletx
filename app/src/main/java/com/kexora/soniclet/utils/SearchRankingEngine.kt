package com.kexora.soniclet.utils

import com.kexora.soniclet.database.FolderInfo
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.ui.components.common.cleanDisplayTitle
import java.util.Locale
import kotlin.math.abs

data class ScoredSong(
    val song: Song,
    val score: Int,
    val matchReason: String
)

data class ScoredArtist(
    val artistName: String,
    val songs: List<Song>,
    val score: Int,
    val matchReason: String
)

data class ScoredAlbum(
    val albumName: String,
    val artist: String,
    val songs: List<Song>,
    val score: Int,
    val matchReason: String
)

data class ScoredPlaylist(
    val playlist: Playlist,
    val songs: List<Song>,
    val score: Int,
    val matchReason: String
)

data class ScoredFolder(
    val folder: FolderInfo,
    val songs: List<Song>,
    val score: Int,
    val matchReason: String
)

sealed class TopMatchResult {
    data class SongTopMatch(val scored: ScoredSong) : TopMatchResult()
    data class ArtistTopMatch(val scored: ScoredArtist) : TopMatchResult()
    data class AlbumTopMatch(val scored: ScoredAlbum) : TopMatchResult()
    data class PlaylistTopMatch(val scored: ScoredPlaylist) : TopMatchResult()
    data class FolderTopMatch(val scored: ScoredFolder) : TopMatchResult()

    fun getScore(): Int = when (this) {
        is SongTopMatch -> scored.score
        is ArtistTopMatch -> scored.score
        is AlbumTopMatch -> scored.score
        is PlaylistTopMatch -> scored.score
        is FolderTopMatch -> scored.score
    }
}

data class SearchResults(
    val topMatch: TopMatchResult?,
    val songs: List<ScoredSong>,
    val artists: List<ScoredArtist>,
    val albums: List<ScoredAlbum>,
    val folders: List<ScoredFolder>,
    val playlists: List<ScoredPlaylist>,
    val totalMatches: Int
) {
    val isEmpty: Boolean get() = totalMatches == 0
}

/**
 * 5-Stage Multi-Factor Fuzzy & Typo-Tolerant Search Engine:
 * - Stage 1 (100 pts): Exact & Word-Prefix Match
 * - Stage 2 (80 pts): Multi-Token & Cross-Field Substring Match
 * - Stage 3 (65 pts): Initials / Acronym Match (e.g., "rdb" -> "Raate Diya Buta")
 * - Stage 4 (55 pts): Phonetic / Hinglish Spelling Normalization (ee->i, oo->u, aa->a, ph->f, sh->s, w->v, z->j)
 * - Stage 5 (40 pts): Bounded Levenshtein Edit-Distance Typo Tolerance (1 typo for 4-5 letters, 2 typos for 6+ letters)
 */
object SearchRankingEngine {

    private val TOKEN_SPLIT_REGEX = Regex("[\\s_\\./\\-|,()\\[\\]{}:;!?'\"]+")
    private val NON_ALNUM_SPACE_REGEX = Regex("[^\\p{L}\\p{N}\\s]")

    private data class PreparedQuery(
        val lower: String,
        val compact: String,
        val tokens: List<String>,
        val phonetic: String,
        val phoneticTokens: List<String>
    )

    fun executeSearch(
        query: String,
        allSongs: List<Song>,
        allPlaylists: List<Playlist>,
        allFolders: List<FolderInfo>,
        playCounts: Map<Long, Int> = emptyMap(),
        excludedSongIds: Set<Long> = emptySet(),
        excludedFolders: Set<String> = emptySet()
    ): SearchResults {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return SearchResults(
                topMatch = null,
                songs = emptyList(),
                artists = emptyList(),
                albums = emptyList(),
                folders = emptyList(),
                playlists = emptyList(),
                totalMatches = 0
            )
        }

        val visibleSongs = if (excludedSongIds.isEmpty() && excludedFolders.isEmpty()) {
            allSongs
        } else {
            allSongs.filter { song ->
                !excludedSongIds.contains(song.id) &&
                    excludedFolders.none { folder ->
                        folder.isNotBlank() && song.folderPath.equals(folder, ignoreCase = true)
                    }
            }
        }

        val prepared = prepareQuery(cleanQuery)
        if (prepared.tokens.isEmpty() && prepared.lower.isBlank()) {
            return SearchResults(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
        }

        // 1. Score Songs across cleaned title, artist, and album (including cross-field matching)
        val scoredSongs = ArrayList<ScoredSong>()
        for (song in visibleSongs) {
            val scoreInfo = scoreSong(song, prepared, playCounts[song.id] ?: 0)
            if (scoreInfo.first > 0) {
                scoredSongs.add(ScoredSong(song, scoreInfo.first, scoreInfo.second))
            }
        }
        scoredSongs.sortByDescending { it.score }

        // 2. Score Artists (parsed cleanly via ArtistParserUtil)
        val artistGroups = ArtistParserUtil.getIndividualArtistsMap(visibleSongs)
        val scoredArtists = ArrayList<ScoredArtist>()
        artistGroups.forEach { (artistName, songs) ->
            if (artistName.isNotBlank() && !artistName.equals("<unknown>", ignoreCase = true)) {
                val (baseScore, reason) = scoreField(artistName, prepared)
                if (baseScore > 0) {
                    val bonus = songs.size.coerceAtMost(8)
                    scoredArtists.add(ScoredArtist(artistName, songs, baseScore + bonus, reason))
                }
            }
        }
        scoredArtists.sortByDescending { it.score }

        // 3. Score Albums
        val albumGroups = visibleSongs
            .filter { it.album.isNotBlank() && !it.album.equals("<unknown>", ignoreCase = true) }
            .groupBy { it.album }
        val scoredAlbums = ArrayList<ScoredAlbum>()
        albumGroups.forEach { (albumName, songs) ->
            val primaryArtist = songs.firstOrNull()?.artist ?: "Unknown Artist"
            val albumScore = scoreField(albumName, prepared)
            val artistFallback = if (albumScore.first == 0) scoreField(primaryArtist, prepared) else Pair(0, "")
            val bestScore = if (albumScore.first > 0) {
                albumScore.first + songs.size.coerceAtMost(6) to albumScore.second
            } else if (artistFallback.first >= 55) {
                (artistFallback.first - 15).coerceAtLeast(35) to "Album by Artist"
            } else {
                0 to ""
            }
            if (bestScore.first > 0) {
                scoredAlbums.add(ScoredAlbum(albumName, primaryArtist, songs, bestScore.first, bestScore.second))
            }
        }
        scoredAlbums.sortByDescending { it.score }

        // 4. Score Playlists
        val scoredPlaylists = ArrayList<ScoredPlaylist>()
        val songMap = visibleSongs.associateBy { it.id }
        allPlaylists.forEach { playlist ->
            val (baseScore, reason) = scoreField(playlist.name, prepared)
            if (baseScore > 0) {
                val plSongs = playlist.songIds.mapNotNull { songMap[it] }
                scoredPlaylists.add(ScoredPlaylist(playlist, plSongs, baseScore, reason))
            }
        }
        scoredPlaylists.sortByDescending { it.score }

        // 5. Score Folders (excluding hidden folders)
        val scoredFolders = ArrayList<ScoredFolder>()
        allFolders.filter { !it.isExcluded && !excludedFolders.contains(it.path) }.forEach { folder ->
            val (baseScore, reason) = scoreField(folder.name, prepared)
            if (baseScore > 0) {
                val folderSongs = visibleSongs.filter { it.folderPath.startsWith(folder.path) }
                scoredFolders.add(ScoredFolder(folder, folderSongs, baseScore, reason))
            }
        }
        scoredFolders.sortByDescending { it.score }

        val topCandidates = buildList {
            scoredSongs.firstOrNull()?.let { add(TopMatchResult.SongTopMatch(it)) }
            scoredArtists.firstOrNull()?.let { add(TopMatchResult.ArtistTopMatch(it)) }
            scoredAlbums.firstOrNull()?.let { add(TopMatchResult.AlbumTopMatch(it)) }
            scoredPlaylists.firstOrNull()?.let { add(TopMatchResult.PlaylistTopMatch(it)) }
            scoredFolders.firstOrNull()?.let { add(TopMatchResult.FolderTopMatch(it)) }
        }
        val bestCandidate = topCandidates.maxByOrNull { it.getScore() }
        val topMatch = if (bestCandidate != null && bestCandidate.getScore() >= 80) bestCandidate else null

        val totalMatches = scoredSongs.size + scoredArtists.size + scoredAlbums.size + scoredPlaylists.size + scoredFolders.size

        return SearchResults(
            topMatch = topMatch,
            songs = scoredSongs,
            artists = scoredArtists,
            albums = scoredAlbums,
            folders = scoredFolders,
            playlists = scoredPlaylists,
            totalMatches = totalMatches
        )
    }

    private fun prepareQuery(rawQuery: String): PreparedQuery {
        val lower = rawQuery.lowercase(Locale.ROOT)
            .replace(NON_ALNUM_SPACE_REGEX, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val tokens = tokenize(lower)
        val compact = lower.replace(" ", "")
        val phonetic = normalizePhonetic(lower)
        val phoneticTokens = tokenize(phonetic)
        return PreparedQuery(
            lower = lower,
            compact = compact,
            tokens = tokens,
            phonetic = phonetic,
            phoneticTokens = phoneticTokens
        )
    }

    private fun scoreSong(song: Song, query: PreparedQuery, playCount: Int): Pair<Int, String> {
        val cleanedTitle = cleanDisplayTitle(song.title, song.filePath)
        val artist = song.artist.trim()
        val album = song.album.trim()

        val titleRes = scoreField(cleanedTitle, query)
        val artistRes = if (artist.isNotBlank() && !artist.equals("<unknown>", ignoreCase = true)) {
            scoreField(artist, query)
        } else Pair(0, "")
        val albumRes = if (album.isNotBlank() && !album.equals("<unknown>", ignoreCase = true)) {
            scoreField(album, query)
        } else Pair(0, "")

        // Cross-field multi-token matching (e.g. "khesari diya" across artist + title)
        val combinedText = "$cleanedTitle $artist $album"
        val crossFieldRes = if (query.tokens.size > 1) {
            scoreCrossField(combinedText, query)
        } else Pair(0, "")

        // Slight field preference weighting while preserving strict 5-stage score tiers
        val weightedTitle = if (titleRes.first > 0) titleRes.first + 12 to titleRes.second else Pair(0, "")
        val weightedArtist = if (artistRes.first > 0) artistRes.first + 5 to "Artist • ${artistRes.second}" else Pair(0, "")
        val weightedAlbum = if (albumRes.first > 0) albumRes.first to "Album • ${albumRes.second}" else Pair(0, "")
        val weightedCross = if (crossFieldRes.first > 0) crossFieldRes.first + 8 to crossFieldRes.second else Pair(0, "")

        val best = listOf(weightedTitle, weightedCross, weightedArtist, weightedAlbum)
            .maxByOrNull { it.first } ?: Pair(0, "")

        if (best.first <= 0) return Pair(0, "")
        val playTieBreaker = playCount.coerceAtMost(10)
        return Pair(best.first + playTieBreaker, best.second)
    }

    /**
     * Core 5-stage scoring function:
     * Stage 1: Exact & Word-Prefix Match (100+ pts)
     * Stage 2: Multi-Token & Substring Match (80+ pts)
     * Stage 3: Initials / Acronym Match (65+ pts)
     * Stage 4: Phonetic / Hinglish Spelling Normalization (55+ pts)
     * Stage 5: Bounded Levenshtein Edit-Distance Typo Tolerance (40+ pts)
     */
    private fun scoreField(rawText: String, query: PreparedQuery): Pair<Int, String> {
        if (rawText.isBlank()) return Pair(0, "")
        val fieldLower = rawText.lowercase(Locale.ROOT)
            .replace(NON_ALNUM_SPACE_REGEX, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (fieldLower.isBlank()) return Pair(0, "")
        val fieldTokens = tokenize(fieldLower)

        // Stage 1: Exact & Word-Prefix Match (100 pts)
        if (fieldLower == query.lower) {
            return Pair(115, "Exact Match")
        }
        if (fieldLower.startsWith(query.lower)) {
            return Pair(108, "Prefix Match")
        }
        if (fieldTokens.any { it.startsWith(query.lower) }) {
            return Pair(100, "Word Prefix")
        }

        // Stage 2: Multi-Token & Substring Match (80 pts)
        if (query.tokens.size > 1 && query.tokens.all { qt -> fieldTokens.any { ft -> ft.startsWith(qt) || ft.contains(qt) } }) {
            return Pair(86, "Multi-Word Match")
        }
        if (fieldLower.contains(query.lower)) {
            return Pair(80, "Substring Match")
        }

        // Stage 3: Initials / Acronym Match (65 pts) — e.g. "rdb" -> "Raate Diya Buta", "skm" -> "Sanso Ki Mala"
        if (query.compact.length >= 2 && fieldTokens.size >= 2) {
            val initials = buildString(fieldTokens.size) {
                for (tok in fieldTokens) {
                    if (tok.isNotEmpty()) append(tok[0])
                }
            }
            if (initials == query.compact) {
                return Pair(69, "Acronym Match")
            }
            if (initials.startsWith(query.compact) || initials.contains(query.compact)) {
                return Pair(65, "Acronym Match")
            }
        }

        // Stage 4: Phonetic / Hinglish Spelling Normalization (55 pts)
        val fieldPhonetic = normalizePhonetic(fieldLower)
        if (query.phonetic.length >= 2) {
            if (fieldPhonetic == query.phonetic || fieldPhonetic.startsWith(query.phonetic)) {
                return Pair(58, "Phonetic Match")
            }
            if (fieldPhonetic.contains(query.phonetic)) {
                return Pair(55, "Phonetic Match")
            }
            val fieldPhoneticTokens = tokenize(fieldPhonetic)
            if (query.phoneticTokens.size > 1 && query.phoneticTokens.all { qpt ->
                    fieldPhoneticTokens.any { fpt -> fpt.startsWith(qpt) || fpt.contains(qpt) }
                }
            ) {
                return Pair(55, "Phonetic Match")
            }
        }

        // Stage 5: Bounded Levenshtein Edit-Distance Typo Tolerance (40 pts)
        val typoScore = scoreLevenshteinTokens(fieldTokens, query.tokens, fieldPhonetic, query.phoneticTokens)
        if (typoScore > 0) {
            return Pair(typoScore, "Typo Match")
        }

        return Pair(0, "")
    }

    private fun scoreCrossField(combinedText: String, query: PreparedQuery): Pair<Int, String> {
        val fieldLower = combinedText.lowercase(Locale.ROOT)
            .replace(NON_ALNUM_SPACE_REGEX, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val fieldTokens = tokenize(fieldLower)
        if (fieldTokens.isEmpty()) return Pair(0, "")

        // Stage 2 cross-field multi-token substring match (80 pts)
        if (query.tokens.all { qt -> fieldTokens.any { ft -> ft.startsWith(qt) || ft.contains(qt) } }) {
            return Pair(80, "Cross-Field Match")
        }

        // Stage 4 cross-field phonetic match (55 pts)
        val fieldPhonetic = normalizePhonetic(fieldLower)
        val fieldPhoneticTokens = tokenize(fieldPhonetic)
        if (query.phoneticTokens.isNotEmpty() && query.phoneticTokens.all { qpt ->
                fieldPhoneticTokens.any { fpt -> fpt.startsWith(qpt) || fpt.contains(qpt) }
            }
        ) {
            return Pair(55, "Phonetic Cross-Field")
        }

        // Stage 5 cross-field bounded Levenshtein typo tolerance (40 pts)
        val typoScore = scoreLevenshteinTokens(fieldTokens, query.tokens, fieldPhonetic, query.phoneticTokens)
        if (typoScore > 0) {
            return Pair(typoScore, "Fuzzy Cross-Field")
        }

        return Pair(0, "")
    }

    /**
     * Stage 5: Bounded Levenshtein Edit-Distance Typo Tolerance (max 40 pts).
     * Allows max 1 edit for 4-5 letter tokens and max 2 edits for 6+ letter tokens.
     */
    private fun scoreLevenshteinTokens(
        fieldTokens: List<String>,
        queryTokens: List<String>,
        fieldPhonetic: String,
        queryPhoneticTokens: List<String>
    ): Int {
        if (fieldTokens.isEmpty() || queryTokens.isEmpty()) return 0
        if (queryTokens.none { it.length >= 4 }) return 0

        val phoneticTokens = tokenize(fieldPhonetic)
        var totalEdits = 0

        for (index in queryTokens.indices) {
            val qt = queryTokens[index]
            val qpt = queryPhoneticTokens.getOrNull(index) ?: qt
            val maxDist = when {
                qt.length <= 3 -> 0
                qt.length <= 5 -> 1
                else -> 2
            }

            var bestDist = Int.MAX_VALUE
            for (ft in fieldTokens) {
                if (ft.startsWith(qt) || ft.contains(qt)) {
                    bestDist = 0
                    break
                }
                if (maxDist > 0) {
                    // Compare full word
                    val fullDist = boundedLevenshtein(ft, qt, maxDist)
                    if (fullDist < bestDist) bestDist = fullDist

                    // Also compare prefix slice if field word is longer than query token
                    if (bestDist > 0 && ft.length > qt.length) {
                        val prefixLen = qt.length.coerceAtMost(ft.length)
                        val prefDist = boundedLevenshtein(ft.substring(0, prefixLen), qt, maxDist)
                        if (prefDist < bestDist) bestDist = prefDist
                    }
                }
            }

            // Also check phonetic tokens with bounded Levenshtein (handles e.g. "shilpy" -> "shilpi", "khesri" -> "khesari")
            if (bestDist > maxDist && maxDist > 0) {
                for (fpt in phoneticTokens) {
                    if (fpt.startsWith(qpt) || fpt.contains(qpt)) {
                        bestDist = 1
                        break
                    }
                    val pDist = boundedLevenshtein(fpt, qpt, maxDist)
                    if (pDist < bestDist) bestDist = pDist
                }
            }

            if (bestDist > maxDist) return 0
            totalEdits += bestDist
        }

        // Base 40 pts for Stage 5, rewarding fewer typos slightly within tier
        return (44 - totalEdits.coerceAtMost(4)).coerceAtLeast(40)
    }

    /**
     * Stage 4 Phonetic / Hinglish Normalization:
     * ee -> i, oo -> u, aa -> a, ph -> f, sh -> s, w -> v, z -> j
     */
    fun normalizePhonetic(input: String): String {
        if (input.isBlank()) return ""
        var s = input.lowercase(Locale.ROOT)
        s = s.replace("ee", "i")
            .replace("oo", "u")
            .replace("aa", "a")
            .replace("ph", "f")
            .replace("sh", "s")
            .replace('w', 'v')
            .replace('z', 'j')
        return s
    }

    private fun tokenize(text: String): List<String> =
        text.split(TOKEN_SPLIT_REGEX).filter { it.isNotBlank() }

    /**
     * Fast bounded Levenshtein edit distance with early exit when distance exceeds [maxDist].
     */
    private fun boundedLevenshtein(a: String, b: String, maxDist: Int): Int {
        val lenA = a.length
        val lenB = b.length
        if (abs(lenA - lenB) > maxDist) return maxDist + 1
        if (a == b) return 0
        if (lenA == 0) return lenB
        if (lenB == 0) return lenA

        var prev = IntArray(lenB + 1) { it }
        var curr = IntArray(lenB + 1)

        for (i in 1..lenA) {
            curr[0] = i
            var rowMin = curr[0]
            val ca = a[i - 1]
            for (j in 1..lenB) {
                val cost = if (ca == b[j - 1]) 0 else 1
                val v = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxDist) return maxDist + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[lenB]
    }
}
