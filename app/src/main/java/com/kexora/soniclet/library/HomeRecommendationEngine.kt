package com.kexora.soniclet.library

import com.kexora.soniclet.database.Song
import com.kexora.soniclet.utils.ArtistParserUtil
import com.kexora.soniclet.utils.MusicMood
import com.kexora.soniclet.utils.SongSemanticTagEngine
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * Snapshot key used by [LibraryViewModel] to lock Home recommendations across the active session
 * so Home never reshuffles or flickers while music plays or playback ticks update.
 */
data class HomeRecommendationCacheKey(
    val calendarDate: String,
    val selectedCategory: String,
    val librarySignature: Int,
    val manualRefreshCounter: Int
)

data class HomeRecommendationResult(
    val eligibleSongs: List<Song>,
    val forYouSongs: List<Song>,
    val mixSongs: List<Song>,
    val topSingers: List<Pair<String, List<Song>>>
)

/**
 * 5-Layer Home Recommendation Engine for Soniclet:
 *
 * - Layer 1: Clean Music Gatekeeper (excludes call recordings, voice notes, WhatsApp/Telegram audio,
 *            < 45s clips, and user-hidden songs/folders).
 * - Layer 2: Real Listening Signals (uses >= 15s genuine listen stats, cumulative playtime,
 *            completion ratios, favorites, and < 5s rapid skip penalties).
 * - Layer 3: Weighted Multi-Factor Scoring (+35 Favorites, +30 Top Singer Affinity including
 *            unplayed tracks by favorite artists, +20 High Completion & Playtime, -25 Rapid Skip
 *            penalty, +15 Time-of-Day Vibe Alignment).
 * - Layer 4: Diversity & Freshness Mixer (70% high-affinity taste matches + 30% fresh/unplayed
 *            discoveries from matching singers/moods, zero back-to-back artist repetition, and
 *            accurate Hindi/Bhojpuri/English category classification).
 * - Layer 5: Deterministic session-locked output keyed by (calendarDate, selectedCategory,
 *            librarySignature, manualRefreshCounter) — zero random `.shuffled()` calls.
 */
object HomeRecommendationEngine {

    private const val MIN_MUSIC_DURATION_MS = 45_000L
    private const val MIN_REAL_LISTEN_MS = 15_000L

    fun todayDateKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    fun cleanDisplayArtist(rawArtist: String): String {
        val trimmed = rawArtist.trim()
        return if (trimmed.isEmpty() ||
            trimmed.equals("<unknown>", ignoreCase = true) ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("unknown artist", ignoreCase = true)
        ) {
            "Unknown Artist"
        } else {
            trimmed
        }
    }

    /**
     * Layer 1 — Clean Music Gatekeeper:
     * Excludes non-music recordings, voice notes, WhatsApp/Telegram audio, clips < 45s,
     * and user-excluded items/folders unless explicitly restored by the user.
     */
    fun filterEligibleMusic(
        songs: List<Song>,
        excludedSongIds: Set<Long>,
        excludedFolders: Set<String>,
        restoredAutoSongIds: Set<Long>
    ): List<Song> {
        if (songs.isEmpty()) return emptyList()
        return songs.filter { song ->
            if (excludedSongIds.contains(song.id)) return@filter false
            if (isInExcludedFolder(song, excludedFolders)) return@filter false
            if (restoredAutoSongIds.contains(song.id)) return@filter true

            if (song.duration < MIN_MUSIC_DURATION_MS) return@filter false
            !AutoHideAudioFilter.isUnwantedVoiceOrCallSong(song)
        }
    }

    private fun isInExcludedFolder(song: Song, excludedFolders: Set<String>): Boolean {
        if (excludedFolders.isEmpty()) return false
        val cleanFolder = song.folderPath.trimEnd('/')
        val cleanFile = song.filePath.trimEnd('/')
        return excludedFolders.any { excludedPath ->
            val cleanExcluded = excludedPath.trimEnd('/')
            cleanFolder == cleanExcluded ||
                cleanFolder.startsWith("$cleanExcluded/") ||
                cleanFile == cleanExcluded ||
                cleanFile.startsWith("$cleanExcluded/")
        }
    }

    /**
     * Generates deterministic, session-locked recommendations for the Home screen.
     */
    fun generate(
        allSongs: List<Song>,
        excludedSongIds: Set<Long>,
        excludedFolders: Set<String>,
        restoredAutoSongIds: Set<Long>,
        selectedCategory: String,
        favoriteSongIds: Set<Long>,
        playCounts: Map<Long, Int>,
        playDurations: Map<Long, Long>,
        completionRatios: Map<Long, Float>,
        rapidSkipCounts: Map<Long, Int>,
        songMoods: Map<Long, String>,
        calendarDate: String,
        manualRefreshCounter: Int,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): HomeRecommendationResult {
        // Layer 1: Clean Music Gatekeeper
        val eligibleSongs = filterEligibleMusic(
            songs = allSongs,
            excludedSongIds = excludedSongIds,
            excludedFolders = excludedFolders,
            restoredAutoSongIds = restoredAutoSongIds
        )
        if (eligibleSongs.isEmpty()) {
            return HomeRecommendationResult(
                eligibleSongs = emptyList(),
                forYouSongs = emptyList(),
                mixSongs = emptyList(),
                topSingers = emptyList()
            )
        }

        // Compute Top Singer Affinity map from Layer 2 real listening signals (>= 15s) & favorites
        val artistRawScore = mutableMapOf<String, Float>()
        val artistSongsMap = mutableMapOf<String, MutableList<Song>>()

        for (song in eligibleSongs) {
            val artists = extractCleanArtists(song)
            val listenedMs = playDurations[song.id] ?: 0L
            val realPlays = if (listenedMs >= MIN_REAL_LISTEN_MS) (playCounts[song.id] ?: 1) else 0
            val isFav = favoriteSongIds.contains(song.id)
            val songAffinitySignal =
                (if (isFav) 12f else 0f) +
                    (if (listenedMs >= MIN_REAL_LISTEN_MS) (listenedMs / 30_000f).coerceAtMost(25f) else 0f) +
                    (realPlays * 3f).coerceAtMost(20f)

            for (artist in artists) {
                artistSongsMap.getOrPut(artist) { mutableListOf() }.add(song)
                val key = artist.lowercase(Locale.ROOT)
                artistRawScore[key] = (artistRawScore[key] ?: 0f) + songAffinitySignal + 0.5f
            }
        }

        val maxArtistScore = artistRawScore.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val topArtistNormalized = artistRawScore.mapValues { (_, v) ->
            (v / maxArtistScore).coerceIn(0f, 1f)
        }

        // Top Singers list for Home section (clean real artist names only, excluding placeholders)
        val topSingers = artistSongsMap.entries
            .filter { (name, _) -> !ListeningHistoryRepository.isPlaceholderMetadata(name) }
            .sortedWith(
                compareByDescending<Map.Entry<String, MutableList<Song>>> { entry ->
                    val listenWeight = entry.value.sumOf { s ->
                        val dur = playDurations[s.id] ?: 0L
                        val favBoost = if (favoriteSongIds.contains(s.id)) 25L else 0L
                        val realDurBoost = if (dur >= MIN_REAL_LISTEN_MS) dur / 10_000L else 0L
                        val countBoost = if (dur >= MIN_REAL_LISTEN_MS) (playCounts[s.id] ?: 0) * 4L else 0L
                        favBoost + realDurBoost + countBoost
                    }
                    entry.value.size * 2L + listenWeight
                }.thenBy { it.key.lowercase(Locale.ROOT) }
            )
            .take(15)
            .map { (name, songs) -> name to songs.distinctBy { it.id } }

        // Determine user's preferred moods from favorites & real listens (>= 15s)
        val moodAffinity = mutableMapOf<MusicMood, Float>()
        val analysisCache = HashMap<Long, com.kexora.soniclet.utils.SongMoodScore>(eligibleSongs.size)
        for (song in eligibleSongs) {
            val analysis = SongSemanticTagEngine.analyzeSong(song)
            analysisCache[song.id] = analysis
            val listenedMs = playDurations[song.id] ?: 0L
            val isFav = favoriteSongIds.contains(song.id)
            if (isFav || listenedMs >= MIN_REAL_LISTEN_MS) {
                val boost = (if (isFav) 3f else 0f) + (if (listenedMs >= MIN_REAL_LISTEN_MS) 2f else 0f)
                moodAffinity[analysis.primaryMood] = (moodAffinity[analysis.primaryMood] ?: 0f) + boost
            }
        }
        val preferredMoods = moodAffinity.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
            .toSet()

        // Filter pool by selected category chip (All, Workout, Devotional, Chill, Romantic, Party)
        val categoryPool: List<Song> = if (selectedCategory.equals("All", ignoreCase = true)) {
            eligibleSongs
        } else {
            val matched = SongSemanticTagEngine.filterSongsForHomeCategory(
                category = selectedCategory,
                songs = eligibleSongs,
                userMoods = songMoods
            )
            if (matched.size >= 4) {
                matched
            } else {
                // Soft fallback if library has very few explicit matches for the chip
                val matchedIds = matched.map { it.id }.toSet()
                val extras = eligibleSongs
                    .filter { it.id !in matchedIds }
                    .sortedByDescending { song ->
                        val analysis = analysisCache[song.id] ?: SongSemanticTagEngine.analyzeSong(song)
                        categoryFallbackAffinity(selectedCategory, song, analysis.primaryMood) +
                            deterministicTieBreaker(calendarDate, selectedCategory, manualRefreshCounter, song.id) * 0.5f
                    }
                    .take((20 - matched.size).coerceAtLeast(0))
                matched + extras
            }
        }

        // Layer 3 — Weighted Multi-Factor Scoring
        val maxPlayDurationMs = playDurations.values.maxOrNull()?.coerceAtLeast(60_000L) ?: 60_000L
        val isMorningOrAfternoon = hourOfDay in 5..16

        data class ScoredTrack(
            val song: Song,
            val score: Float,
            val isFreshDiscovery: Boolean,
            val primaryArtistKey: String
        )

        val scoredTracks = categoryPool.map { song ->
            val analysis = analysisCache[song.id] ?: SongSemanticTagEngine.analyzeSong(song)
            val isFavorite = favoriteSongIds.contains(song.id)
            val listenedMs = playDurations[song.id] ?: 0L
            val realPlayCount = if (listenedMs >= MIN_REAL_LISTEN_MS) (playCounts[song.id] ?: 1) else 0
            val completion = completionRatios[song.id] ?: 0f
            val rapidSkips = rapidSkipCounts[song.id] ?: 0

            // 1. Favorite / Liked Boost (+35 pts)
            val favoritePoints = if (isFavorite) 35f else 0f

            // 2. Top Singer Affinity (+30 pts, including unplayed songs by favorite artists)
            val songArtists = extractCleanArtists(song)
            val bestArtistAffinity = songArtists
                .maxOfOrNull { topArtistNormalized[it.lowercase(Locale.ROOT)] ?: 0f } ?: 0f
            val singerPoints = bestArtistAffinity * 30f

            // 3. High Completion & Playtime (+20 pts) & Rapid Skip Penalty (-25 pts)
            val completionAndPlayPoints = if (listenedMs >= MIN_REAL_LISTEN_MS) {
                val completionPart = completion.coerceIn(0f, 1f) * 12f
                val playtimePart = ((listenedMs.toFloat() / maxPlayDurationMs.toFloat()).coerceIn(0f, 1f) * 5f) +
                    ((realPlayCount.toFloat() / 6f).coerceIn(0f, 1f) * 3f)
                (completionPart + playtimePart).coerceAtMost(20f)
            } else {
                0f
            }

            val rapidSkipPenalty = when {
                rapidSkips >= 2 -> -25f
                rapidSkips == 1 -> -12f
                else -> 0f
            }

            // 4. Time-of-Day Vibe Alignment (+15 pts)
            val timeVibePoints = computeTimeOfDayVibePoints(
                primaryMood = analysis.primaryMood,
                secondaryMoods = analysis.secondaryMoods,
                isMorningOrAfternoon = isMorningOrAfternoon
            )

            // Small deterministic daily + manual-refresh tie-breaker (0..4.5 pts) so order is stable all day
            val deterministicJitter = deterministicTieBreaker(
                calendarDate = calendarDate,
                selectedCategory = selectedCategory,
                manualRefreshCounter = manualRefreshCounter,
                songId = song.id
            ) * 4.5f

            val totalScore = favoritePoints + singerPoints + completionAndPlayPoints + rapidSkipPenalty + timeVibePoints + deterministicJitter

            val isUnplayedOrFresh = listenedMs < MIN_REAL_LISTEN_MS && !isFavorite
            val matchesTasteForDiscovery = isUnplayedOrFresh && (
                bestArtistAffinity >= 0.25f ||
                    analysis.primaryMood in preferredMoods ||
                    !selectedCategory.equals("All", ignoreCase = true)
                )

            ScoredTrack(
                song = song,
                score = totalScore,
                isFreshDiscovery = isUnplayedOrFresh && (matchesTasteForDiscovery || rapidSkips == 0),
                primaryArtistKey = songArtists.firstOrNull()?.lowercase(Locale.ROOT) ?: "unknown_${song.id}"
            )
        }

        // Layer 4 — Diversity & Freshness Mixer:
        // Blend 70% high-affinity taste matches + 30% fresh/unplayed discoveries
        val targetSize = minOf(30, scoredTracks.size)
        val affinitySorted = scoredTracks
            .filter { !it.isFreshDiscovery }
            .sortedByDescending { it.score }
        val discoverySorted = scoredTracks
            .filter { it.isFreshDiscovery }
            .sortedByDescending { it.score }

        val desiredDiscoveryCount = (targetSize * 0.30f).toInt().coerceAtLeast(if (discoverySorted.isNotEmpty() && targetSize >= 4) 1 else 0)
        val desiredAffinityCount = targetSize - desiredDiscoveryCount

        val selectedAffinity = affinitySorted.take(desiredAffinityCount).toMutableList()
        val selectedDiscovery = discoverySorted.take(desiredDiscoveryCount).toMutableList()

        // Fill any remaining slots cleanly if one bucket had fewer tracks than requested
        val usedIds = HashSet<Long>(targetSize * 2)
        selectedAffinity.forEach { usedIds.add(it.song.id) }
        selectedDiscovery.forEach { usedIds.add(it.song.id) }

        val combinedPool = mutableListOf<ScoredTrack>()
        var aIdx = 0
        var dIdx = 0
        // Interleave at 70/30 cadence (2-3 affinity tracks followed by 1 fresh discovery)
        while (aIdx < selectedAffinity.size || dIdx < selectedDiscovery.size) {
            repeat(2) {
                if (aIdx < selectedAffinity.size) {
                    combinedPool.add(selectedAffinity[aIdx++])
                }
            }
            if (dIdx < selectedDiscovery.size) {
                combinedPool.add(selectedDiscovery[dIdx++])
            }
            if (aIdx < selectedAffinity.size) {
                combinedPool.add(selectedAffinity[aIdx++])
            }
        }

        if (combinedPool.size < targetSize) {
            val remainder = scoredTracks
                .filter { it.song.id !in usedIds }
                .sortedByDescending { it.score }
                .take(targetSize - combinedPool.size)
            combinedPool.addAll(remainder)
        }

        // Prevent back-to-back artist repetition
        val diversified = ArrayList<Song>(combinedPool.size)
        val remaining = combinedPool.toMutableList()
        var lastArtistKey: String? = null

        while (remaining.isNotEmpty()) {
            val pickIndex = remaining.indexOfFirst { track ->
                lastArtistKey == null || track.primaryArtistKey != lastArtistKey
            }.let { idx -> if (idx >= 0) idx else 0 }

            val chosen = remaining.removeAt(pickIndex)
            diversified.add(chosen.song)
            lastArtistKey = chosen.primaryArtistKey
        }

        // Top mix card tracks (aligned with selected category & diversified taste picks)
        val mixSongs = diversified.take(25)

        return HomeRecommendationResult(
            eligibleSongs = eligibleSongs,
            forYouSongs = diversified,
            mixSongs = mixSongs,
            topSingers = topSingers
        )
    }

    private fun extractCleanArtists(song: Song): List<String> {
        val parsed = ArtistParserUtil.splitArtists(song.artist, song.title)
        return parsed
            .map { cleanDisplayArtist(it) }
            .filter { !it.equals("Unknown Artist", ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun computeTimeOfDayVibePoints(
        primaryMood: MusicMood,
        secondaryMoods: List<MusicMood>,
        isMorningOrAfternoon: Boolean
    ): Float {
        val targetMoods = if (isMorningOrAfternoon) {
            setOf(
                MusicMood.ENERGETIC_POP,
                MusicMood.GYM_WORKOUT_PHONK,
                MusicMood.PARTY_DANCE,
                MusicMood.HIPHOP_RAP,
                MusicMood.REGIONAL_FOLK
            )
        } else {
            setOf(
                MusicMood.CHILL_ACOUSTIC,
                MusicMood.ROMANTIC_LOVE,
                MusicMood.DEVOTIONAL_SPIRITUAL,
                MusicMood.SAD_EMOTIONAL,
                MusicMood.RETRO_CLASSIC
            )
        }

        return when {
            primaryMood in targetMoods -> 15f
            secondaryMoods.any { it in targetMoods } -> 8f
            else -> 0f
        }
    }

    private fun categoryFallbackAffinity(category: String, song: Song, mood: MusicMood): Float {
        return when (category) {
            "Workout" -> if (mood == MusicMood.GYM_WORKOUT_PHONK || mood == MusicMood.HIPHOP_RAP || song.duration in 90_000L..220_000L) 3f else 0f
            "Party" -> if (mood == MusicMood.PARTY_DANCE || mood == MusicMood.ENERGETIC_POP || mood == MusicMood.REGIONAL_FOLK) 3f else 0f
            "Devotional" -> if (mood == MusicMood.DEVOTIONAL_SPIRITUAL || song.duration >= 240_000L) 3f else 0f
            "Chill" -> if (mood == MusicMood.CHILL_ACOUSTIC || mood == MusicMood.RETRO_CLASSIC || song.duration >= 210_000L) 3f else 0f
            "Romantic" -> if (mood == MusicMood.ROMANTIC_LOVE || mood == MusicMood.SAD_EMOTIONAL) 3f else 0f
            else -> 0f
        }
    }

    /**
     * Pure deterministic hash in [0f, 1f] based on (calendarDate, selectedCategory, manualRefreshCounter, songId).
     */
    private fun deterministicTieBreaker(
        calendarDate: String,
        selectedCategory: String,
        manualRefreshCounter: Int,
        songId: Long
    ): Float {
        var h = 1125899906842597L
        val seedStr = "$calendarDate|$selectedCategory|$manualRefreshCounter|$songId"
        for (ch in seedStr) {
            h = 31L * h + ch.code.toLong()
        }
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return (abs(h % 10_000L).toFloat() / 10_000f)
    }
}
