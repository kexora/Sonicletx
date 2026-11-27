package com.kexora.soniclet.utils

import com.kexora.soniclet.database.Song
import com.kexora.soniclet.library.formatFileSize
import java.io.File
import kotlin.math.abs

enum class DuplicateMatchReason(val title: String, val description: String) {
    EXACT_CLONE("Exact File Clone", "Identical file size and duration across folders"),
    SAME_TRACK("Same Track & Artist", "Matching title, artist metadata, and song duration"),
    SIMILAR_TITLE("Similar Song Title", "Normalized title match with matching duration"),
    FILENAME_CLONE("Filename Variant", "Identical filename copy (e.g. download copies)")
}

data class DuplicateTrackItem(
    val song: Song,
    val fileSize: Long,
    val formattedSize: String,
    val fileExtension: String,
    val isBestQuality: Boolean,
    val qualityScore: Long,
    val qualityBadge: String
)

data class DuplicateGroup(
    val groupId: String,
    val displayTitle: String,
    val displayArtist: String,
    val durationMs: Long,
    val matchReason: DuplicateMatchReason,
    val tracks: List<DuplicateTrackItem>,
    val potentialReclaimBytes: Long
)

object DuplicateDetectorEngine {

    private val NOISE_PATTERNS = listOf(
        Regex("""\b(official\s+music\s+video|official\s+video|official\s+audio|lyric\s+video|lyrics\s+video|lyrics|audio|visualizer|full\s+song|full\s+audio|remastered|remaster|hd|hq|4k|1080p|720p)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(320kbps|128kbps|256kbps|64kbps|kbps|mp3|m4a|flac|wav|aac|ogg|opus)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(pagalworld|djpunjab|wapking|songspk|yt1s|y2mate|spotifydown|ringtone|downloaded\s+from|mr\s+jatt|mp3clan)\b""", RegexOption.IGNORE_CASE),
        Regex("""\(\s*\d+\s*\)"""), // (1), (2)
        Regex("""_\d+$"""),          // _1
        Regex("""\bcopy\b""", RegexOption.IGNORE_CASE),
        Regex("""\[.*?\]"""),       // brackets content
        Regex("""\(.*?\)""")        // parentheses content
    )

    fun normalizeTitle(rawTitle: String, rawFilePath: String = ""): String {
        var title = rawTitle.trim()
        if (title.isBlank() || title.equals("unknown", ignoreCase = true) || title.startsWith("track", ignoreCase = true) || title.startsWith("audio", ignoreCase = true)) {
            val filename = rawFilePath.substringAfterLast('/').substringBeforeLast('.')
            if (filename.isNotBlank()) {
                title = filename
            }
        }

        // Remove trailing extensions
        title = title.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus|wma)$""", RegexOption.IGNORE_CASE), "")

        var cleaned = title
        for (pattern in NOISE_PATTERNS) {
            cleaned = cleaned.replace(pattern, " ")
        }

        // Remove featuring tags
        cleaned = cleaned.replace(Regex("""\b(feat\.|ft\.|featuring|with|prod\.|by)\b.*""", RegexOption.IGNORE_CASE), "")

        // Clean punctuation
        cleaned = cleaned.replace(Regex("""[^a-zA-Z0-9\u0900-\u097F\s]"""), " ")

        return cleaned.replace(Regex("""\s+"""), " ").trim().lowercase()
    }

    fun normalizeArtist(rawArtist: String): String {
        var artist = rawArtist.trim()
        if (artist.isBlank() || artist.equals("<unknown>", ignoreCase = true) || artist.equals("unknown", ignoreCase = true) || artist.equals("unknown artist", ignoreCase = true) || artist.equals("various artists", ignoreCase = true)) {
            return ""
        }
        artist = artist.replace(Regex("""\b(feat\.|ft\.|featuring|with|prod\.|by)\b.*""", RegexOption.IGNORE_CASE), "")
        artist = artist.replace(Regex("""[^a-zA-Z0-9\u0900-\u097F\s]"""), " ")
        return artist.replace(Regex("""\s+"""), " ").trim().lowercase()
    }

    fun normalizeFilename(filePath: String): String {
        val filename = filePath.substringAfterLast('/').substringBeforeLast('.')
        var cleaned = filename
        for (pattern in NOISE_PATTERNS) {
            cleaned = cleaned.replace(pattern, " ")
        }
        cleaned = cleaned.replace(Regex("""[^a-zA-Z0-9\u0900-\u097F\s]"""), " ")
        return cleaned.replace(Regex("""\s+"""), " ").trim().lowercase()
    }

    /**
     * Finds all duplicates across audio library with multi-pass intelligent heuristics.
     */
    fun analyzeDuplicates(allSongs: List<Song>): List<DuplicateGroup> {
        if (allSongs.size < 2) return emptyList()

        // Precompute file info
        val fileSizes = allSongs.associate { song ->
            val file = File(song.filePath)
            val size = if (file.exists()) file.length() else 0L
            song.id to size
        }

        val visitedSongIds = mutableSetOf<Long>()
        val resultGroups = mutableListOf<DuplicateGroup>()

        // Pass 1: Exact File Clones (Same size > 100KB & Duration within 1.5s)
        val songsBySize = allSongs.filter { (fileSizes[it.id] ?: 0L) > 100_000L }
            .groupBy { fileSizes[it.id] ?: 0L }

        for ((size, sameSizeSongs) in songsBySize) {
            if (sameSizeSongs.size > 1) {
                val subClusters = clusterByDuration(sameSizeSongs, maxDiffMs = 1500L)
                for (cluster in subClusters) {
                    if (cluster.size > 1) {
                        val unvisited = cluster.filter { !visitedSongIds.contains(it.id) }
                        if (unvisited.size > 1) {
                            unvisited.forEach { visitedSongIds.add(it.id) }
                            resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.EXACT_CLONE))
                        }
                    }
                }
            }
        }

        // Pass 2: Same Track (Normalized Title + Normalized Artist + Duration within 3.5s)
        val remainingSongs = allSongs.filter { !visitedSongIds.contains(it.id) }
        val byTitleAndArtist = remainingSongs.groupBy { song ->
            val normTitle = normalizeTitle(song.title, song.filePath)
            val normArtist = normalizeArtist(song.artist)
            if (normTitle.isNotBlank() && normArtist.isNotBlank()) {
                "$normTitle|$normArtist"
            } else {
                ""
            }
        }

        for ((key, songs) in byTitleAndArtist) {
            if (key.isNotBlank() && songs.size > 1) {
                val subClusters = clusterByDuration(songs, maxDiffMs = 3500L)
                for (cluster in subClusters) {
                    if (cluster.size > 1) {
                        val unvisited = cluster.filter { !visitedSongIds.contains(it.id) }
                        if (unvisited.size > 1) {
                            unvisited.forEach { visitedSongIds.add(it.id) }
                            resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.SAME_TRACK))
                        }
                    }
                }
            }
        }

        // Pass 3: Same Filename Variant + Duration within 3.5s
        val remainingAfterPass2 = allSongs.filter { !visitedSongIds.contains(it.id) }
        val byFilename = remainingAfterPass2.groupBy { song ->
            normalizeFilename(song.filePath)
        }

        for ((normName, songs) in byFilename) {
            if (normName.length >= 3 && songs.size > 1) {
                val subClusters = clusterByDuration(songs, maxDiffMs = 3500L)
                for (cluster in subClusters) {
                    if (cluster.size > 1) {
                        val unvisited = cluster.filter { !visitedSongIds.contains(it.id) }
                        if (unvisited.size > 1) {
                            unvisited.forEach { visitedSongIds.add(it.id) }
                            resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.FILENAME_CLONE))
                        }
                    }
                }
            }
        }

        // Pass 4: Similar Title (Title length >= 4 + Duration within 2.5s)
        val remainingAfterPass3 = allSongs.filter { !visitedSongIds.contains(it.id) }
        val byTitle = remainingAfterPass3.groupBy { song ->
            normalizeTitle(song.title, song.filePath)
        }

        for ((normTitle, songs) in byTitle) {
            if (normTitle.length >= 4 && songs.size > 1) {
                val subClusters = clusterByDuration(songs, maxDiffMs = 2500L)
                for (cluster in subClusters) {
                    if (cluster.size > 1) {
                        val unvisited = cluster.filter { !visitedSongIds.contains(it.id) }
                        if (unvisited.size > 1) {
                            unvisited.forEach { visitedSongIds.add(it.id) }
                            resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.SIMILAR_TITLE))
                        }
                    }
                }
            }
        }

        // Sort groups by potential space reclaimable descending
        return resultGroups.sortedWith(
            compareByDescending<DuplicateGroup> { it.potentialReclaimBytes }
                .thenBy { it.displayTitle }
        )
    }

    private fun clusterByDuration(songs: List<Song>, maxDiffMs: Long): List<List<Song>> {
        val clusters = mutableListOf<MutableList<Song>>()
        val sorted = songs.sortedBy { it.duration }

        for (song in sorted) {
            var added = false
            for (cluster in clusters) {
                val avgDuration = cluster.sumOf { it.duration } / cluster.size
                if (abs(song.duration - avgDuration) <= maxDiffMs) {
                    cluster.add(song)
                    added = true
                    break
                }
            }
            if (!added) {
                clusters.add(mutableListOf(song))
            }
        }
        return clusters
    }

    private fun buildDuplicateGroup(
        songs: List<Song>,
        fileSizes: Map<Long, Long>,
        reason: DuplicateMatchReason
    ): DuplicateGroup {
        val tracks = songs.map { song ->
            val size = fileSizes[song.id] ?: 0L
            val ext = song.filePath.substringAfterLast('.', "mp3").uppercase()
            
            // Calculate quality score
            val formatBonus = when (ext) {
                "FLAC", "WAV" -> 500_000L
                "M4A", "AAC" -> 300_000L
                "MP3" -> 150_000L
                else -> 50_000L
            }
            val sizeRate = if (song.duration > 0) (size * 1000L) / song.duration else size
            val artBonus = if (song.albumArtUri != null) 50_000L else 0L
            val qualityScore = formatBonus + sizeRate + artBonus

            DuplicateTrackItem(
                song = song,
                fileSize = size,
                formattedSize = if (size > 0) formatFileSize(size) else "--",
                fileExtension = ext,
                isBestQuality = false, // will update below
                qualityScore = qualityScore,
                qualityBadge = ext
            )
        }.sortedByDescending { it.qualityScore }

        // Mark highest quality track as best
        val updatedTracks = tracks.mapIndexed { index, track ->
            if (index == 0) {
                track.copy(isBestQuality = true, qualityBadge = "${track.fileExtension} • Best Quality")
            } else {
                track.copy(isBestQuality = false, qualityBadge = "${track.fileExtension} • Redundant")
            }
        }

        val primarySong = updatedTracks.first().song
        val bestSize = updatedTracks.first().fileSize
        val totalGroupSize = updatedTracks.sumOf { it.fileSize }
        val potentialReclaim = (totalGroupSize - bestSize).coerceAtLeast(0L)

        return DuplicateGroup(
            groupId = "dup_${primarySong.id}_${updatedTracks.size}",
            displayTitle = primarySong.title,
            displayArtist = primarySong.artist.ifBlank { "Unknown Artist" },
            durationMs = primarySong.duration,
            matchReason = reason,
            tracks = updatedTracks,
            potentialReclaimBytes = potentialReclaim
        )
    }
}
