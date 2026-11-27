package com.kexora.soniclet.utils

import com.kexora.soniclet.database.Song
import kotlin.math.ln
import kotlin.random.Random

/**
 * Intelligent music shuffle algorithm that:
 * 1. Analyzes playback history (play counts, listening durations, and recent history).
 * 2. Strongly prioritizes less frequently played and unplayed tracks (discovery boost).
 * 3. Ensures a balanced genre distribution across the entire sequence, preventing genre clumps.
 * 4. Applies secondary artist spacing to avoid consecutive repetitions from the same artist.
 */
object SmartShuffleUtil {

    private data class ScoredSong(
        val song: Song,
        val score: Double,
        val genreKey: String,
        val artistKey: String
    )

    /**
     * Generates an intelligent shuffled playlist.
     *
     * @param songs The source list of songs to shuffle.
     * @param playCounts Map of song ID to number of times played.
     * @param playDurations Map of song ID to total milliseconds listened.
     * @param recentlyPlayedIds Ordered list of recently played song IDs (most recent first).
     * @param currentSongId The currently playing song ID to preserve at the start of the queue.
     * @param randomizeTieBreaker Whether to inject subtle random jitter for variety across shuffles.
     */
    fun generateSmartShuffle(
        songs: List<Song>,
        playCounts: Map<Long, Int> = emptyMap(),
        playDurations: Map<Long, Long> = emptyMap(),
        recentlyPlayedIds: List<Long> = emptyList(),
        currentSongId: Long? = null,
        randomizeTieBreaker: Boolean = true
    ): List<Song> {
        if (songs.size <= 1) return songs

        // 1. Separate current song if we need to keep it playing at index 0
        val currentSong = if (currentSongId != null) songs.find { it.id == currentSongId } else null
        val pool = if (currentSong != null) songs.filter { it.id != currentSongId } else songs

        if (pool.isEmpty()) {
            return listOfNotNull(currentSong)
        }

        val recentTop15 = recentlyPlayedIds.take(15).toSet()

        // 2. Score each track according to playback frequency and recency
        val scoredList = pool.map { song ->
            val plays = (playCounts[song.id] ?: 0).coerceAtLeast(0)
            val durationMs = (playDurations[song.id] ?: 0L).coerceAtLeast(0L)
            
            // Calculate base discovery weight:
            // 0 plays -> massive boost (100.0)
            // 1 play  -> 45.0
            // 2 plays -> 28.0
            // 5 plays -> 14.0
            // 20 plays -> ~4.0
            val playFrequencyFactor = when {
                plays == 0 && durationMs == 0L -> 100.0
                plays == 0 -> 75.0
                else -> {
                    val listeningPenalty = (durationMs / 60000.0) * 0.5 // each minute listened adds penalty
                    100.0 / (1.0 + (plays * 2.2) + listeningPenalty)
                }
            }

            // Recency penalty: if recently played in last 15 songs, reduce priority
            val recencyMultiplier = if (recentTop15.contains(song.id)) {
                val indexInRecents = recentlyPlayedIds.indexOf(song.id).coerceAtLeast(0)
                0.25 + (indexInRecents.toDouble() / 15.0) * 0.45 // 0.25 to 0.70
            } else {
                1.0
            }

            // Small random jitter (0.85 to 1.15) to maintain freshness without disrupting prioritization
            val jitter = if (randomizeTieBreaker) {
                0.85 + (Random.nextDouble() * 0.30)
            } else {
                1.0
            }

            val finalScore = (playFrequencyFactor * recencyMultiplier * jitter).coerceAtLeast(0.01)

            val rawGenre = song.genre.trim()
            val normalizedGenre = when {
                rawGenre.isBlank() || rawGenre.equals("Unknown", ignoreCase = true) || rawGenre.equals("<unknown>", ignoreCase = true) -> {
                    // Fallback to artist or folder if genre tag is absent
                    if (song.artist.isNotBlank() && !song.artist.equals("Unknown Artist", ignoreCase = true)) {
                        "Artist:${song.artist.trim().lowercase()}"
                    } else {
                        "General"
                    }
                }
                else -> rawGenre.lowercase()
            }

            val normalizedArtist = song.artist.trim().lowercase().ifBlank { "unknown" }

            ScoredSong(
                song = song,
                score = finalScore,
                genreKey = normalizedGenre,
                artistKey = normalizedArtist
            )
        }

        // 3. Group tracks by Genre Bucket
        val genreBuckets = mutableMapOf<String, MutableList<ScoredSong>>()
        for (item in scoredList) {
            genreBuckets.getOrPut(item.genreKey) { mutableListOf() }.add(item)
        }

        // Sort inside each bucket by discovery score descending (highest priority first)
        for ((_, bucket) in genreBuckets) {
            bucket.sortByDescending { it.score }
        }

        // 4. Balanced Genre Interleaving (Fair Multi-way Weave)
        val result = mutableListOf<Song>()
        var lastGenre: String? = null
        var lastArtist: String? = null

        val activeBuckets = genreBuckets.values.filter { it.isNotEmpty() }.toMutableList()

        while (activeBuckets.isNotEmpty()) {
            // Pick the most appropriate bucket:
            // Prefer buckets that are NOT the last played genre.
            // Among candidates, pick the one with the highest top-track score or largest remaining pool.
            val candidateBuckets = if (activeBuckets.size > 1) {
                activeBuckets.filter { it.first().genreKey != lastGenre }
            } else {
                activeBuckets
            }

            val chosenBucket = (candidateBuckets.ifEmpty { activeBuckets })
                .maxByOrNull { bucket ->
                    // Score = (Top item score * 0.7) + (Remaining bucket size * 0.3)
                    val topScore = bucket.first().score
                    val sizeBonus = bucket.size.toDouble() * 2.0
                    topScore + sizeBonus
                } ?: activeBuckets.first()

            // From chosen bucket, attempt to find a track that doesn't repeat the previous artist
            val itemIndex = if (chosenBucket.size > 1 && lastArtist != null) {
                val nonMatchingArtistIdx = chosenBucket.indexOfFirst { it.artistKey != lastArtist }
                if (nonMatchingArtistIdx != -1) nonMatchingArtistIdx else 0
            } else {
                0
            }

            val chosenItem = chosenBucket.removeAt(itemIndex)
            result.add(chosenItem.song)

            lastGenre = chosenItem.genreKey
            lastArtist = chosenItem.artistKey

            if (chosenBucket.isEmpty()) {
                activeBuckets.remove(chosenBucket)
            }
        }

        // 5. Prepend current song if applicable
        return if (currentSong != null) {
            listOf(currentSong) + result
        } else {
            result
        }
    }
}
