package com.kexora.soniclet.utils

import com.kexora.soniclet.database.Song
import com.kexora.soniclet.library.formatFileSize
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

enum class DuplicateScanMode(val label: String) {
    SMART_MATCH("Smart Match"),
    EXACT_FILES("Exact Files")
}

enum class DuplicateMatchReason(val title: String, val description: String) {
    EXACT_CLONE("Exact File", "Identical file size and audio stream"),
    SAME_TRACK("Same Song", "Matching song title and artist"),
    SIMILAR_TITLE("Matching Song", "Same title and matching length"),
    FILENAME_CLONE("Duplicate Copy", "Matching cleaned filename and length")
}

data class DuplicateTrackItem(
    val song: Song,
    val fileSize: Long,
    val formattedSize: String,
    val fileExtension: String,
    val estimatedBitrateKbps: Int = 0,
    val hasCoverArt: Boolean = false,
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

    private val EXTRA_NOISE_PATTERNS = listOf(
        Regex("""\(\s*\d+\s*\)"""),          // (1), (2)
        Regex("""\[\s*\d+\s*\]"""),          // [1], [2]
        Regex("""_\d+$"""),                  // _1
        Regex("""-\d+$"""),                  // -1
        Regex("""\bcopy\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(remastered|remaster|visualizer|ringtone|status|slowed|reverb|bass\s*boosted)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(320|256|192|128|96|64)\s*k(?:bps)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\[.*?\]"""),                // remaining bracketed tags
        Regex("""\(.*?\)""")                 // remaining parenthesized tags
    )

    private val ARTIST_SPLIT_REGEX = Regex("""\s*(?:,|&|/|;|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\bx\b|\bprod\.?\b)\s*""", RegexOption.IGNORE_CASE)

    fun normalizeTitle(rawTitle: String, rawFilePath: String = ""): String {
        var cleaned = SmartTitleCleaner.cleanTitle(rawTitle, rawFilePath, applyTitleCase = false)
        if (cleaned.isBlank()) {
            cleaned = rawFilePath.substringAfterLast('/').substringBeforeLast('.')
        }

        // Remove trailing audio extensions if any remain
        cleaned = cleaned.replace(Regex("""\.(mp3|m4a|flac|wav|aac|ogg|opus|wma|3gp|amr|alac)$""", RegexOption.IGNORE_CASE), "")

        // If title contains "Title - Artist" and rawTitle had artist appended, keep main clean core when brackets stripped
        for (pattern in EXTRA_NOISE_PATTERNS) {
            cleaned = cleaned.replace(pattern, " ")
        }

        cleaned = cleaned.replace(Regex("""\b(feat\.|ft\.|featuring|with|prod\.|by)\b.*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace('_', ' ')
        cleaned = cleaned.replace(Regex("""[^a-zA-Z0-9\u0900-\u0D7F\s]"""), " ")

        return cleaned.replace(Regex("""\s+"""), " ").trim().lowercase(Locale.ROOT)
    }

    fun normalizePrimaryArtist(rawArtist: String): String {
        if (SmartTitleCleaner.isPlaceholderArtist(rawArtist)) return ""
        val cleaned = SmartTitleCleaner.cleanArtist(rawArtist, applyTitleCase = false)
        if (cleaned.isBlank() || SmartTitleCleaner.isPlaceholderArtist(cleaned)) return ""
        val primary = cleaned.split(ARTIST_SPLIT_REGEX).firstOrNull().orEmpty()
        return primary
            .replace(Regex("""[^a-zA-Z0-9\u0900-\u0D7F\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    fun normalizeArtist(rawArtist: String): String = normalizePrimaryArtist(rawArtist)

    fun normalizeFilename(filePath: String): String {
        val filename = filePath.substringAfterLast('/').substringBeforeLast('.')
        if (filename.isBlank()) return ""
        return normalizeTitle(filename, filePath)
    }

    /**
     * Finds duplicates in either SMART_MATCH (default) or EXACT_FILES mode.
     */
    fun analyzeDuplicates(
        allSongs: List<Song>,
        mode: DuplicateScanMode = DuplicateScanMode.SMART_MATCH
    ): List<DuplicateGroup> {
        if (allSongs.size < 2) return emptyList()

        val fileSizes = allSongs.associate { song ->
            val file = File(song.filePath)
            val size = if (song.filePath.isNotBlank() && file.exists()) file.length() else 0L
            song.id to size
        }

        return when (mode) {
            DuplicateScanMode.EXACT_FILES -> analyzeExactFileMatches(allSongs, fileSizes)
            DuplicateScanMode.SMART_MATCH -> analyzeSmartMatches(allSongs, fileSizes)
        }
    }

    /**
     * Mode 2 — Exact File Match:
     * Groups tracks with identical file size (>0) AND identical partial audio stream hash.
     */
    private fun analyzeExactFileMatches(
        allSongs: List<Song>,
        fileSizes: Map<Long, Long>
    ): List<DuplicateGroup> {
        val resultGroups = mutableListOf<DuplicateGroup>()
        val songsBySize = allSongs
            .filter { (fileSizes[it.id] ?: 0L) > 16_384L }
            .groupBy { fileSizes[it.id] ?: 0L }

        for ((size, sameSizeSongs) in songsBySize) {
            if (sameSizeSongs.size < 2) continue

            val byHash = sameSizeSongs.groupBy { song ->
                computePartialAudioHash(song.filePath, size)
                    ?: "fallback_${size}_${song.duration / 1000}"
            }

            for ((_, hashMatches) in byHash) {
                if (hashMatches.size > 1) {
                    resultGroups.add(
                        buildDuplicateGroup(
                            songs = hashMatches,
                            fileSizes = fileSizes,
                            reason = DuplicateMatchReason.EXACT_CLONE
                        )
                    )
                }
            }
        }

        return resultGroups.sortedWith(
            compareByDescending<DuplicateGroup> { it.potentialReclaimBytes }
                .thenBy { it.displayTitle.lowercase(Locale.ROOT) }
        )
    }

    /**
     * Mode 1 — Smart Duplicate Match (Default):
     * Groups tracks sharing the same normalized title (cleaned via SmartTitleCleaner) AND:
     * - matching primary artist, OR
     * - matching duration within ±3 seconds when artist is unknown/placeholder (or identical cleaned title & ±3s).
     * Also catches exact duplicate files even if their tags differ.
     */
    private fun analyzeSmartMatches(
        allSongs: List<Song>,
        fileSizes: Map<Long, Long>
    ): List<DuplicateGroup> {
        val visitedSongIds = mutableSetOf<Long>()
        val resultGroups = mutableListOf<DuplicateGroup>()

        // Precompute normalized title and primary artist
        val normTitleById = HashMap<Long, String>(allSongs.size)
        val normArtistById = HashMap<Long, String>(allSongs.size)
        for (song in allSongs) {
            normTitleById[song.id] = normalizeTitle(song.title, song.filePath)
            normArtistById[song.id] = normalizePrimaryArtist(song.artist)
        }

        // Pass 1: Normalized Title + Primary Artist (catches 128k MP3 vs 320k M4A vs FLAC even with slight intro/outro differences)
        val byTitle = allSongs.groupBy { normTitleById[it.id].orEmpty() }
        for ((normTitle, titleSongs) in byTitle) {
            if (normTitle.length < 2 || titleSongs.size < 2) continue

            // Sub-group by matching primary artist first
            val knownArtistSongs = titleSongs.filter { !normArtistById[it.id].isNullOrBlank() }
            val byArtist = knownArtistSongs.groupBy { normArtistById[it.id].orEmpty() }

            for ((_, artistGroup) in byArtist) {
                if (artistGroup.size < 2) continue
                // Allow ±12s for same exact title + same artist (different rip trims/bitrates)
                val clusters = clusterByDuration(artistGroup, maxDiffMs = 12_000L)
                for (cluster in clusters) {
                    val unvisited = cluster.filter { it.id !in visitedSongIds }
                    if (unvisited.size > 1) {
                        unvisited.forEach { visitedSongIds.add(it.id) }
                        resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.SAME_TRACK))
                    }
                }
            }

            // Next, check remaining tracks with the same normalized title where at least one has Unknown/placeholder artist (or duration within ±3s)
            val remainingInTitle = titleSongs.filter { it.id !in visitedSongIds }
            if (remainingInTitle.size > 1 && normTitle.length >= 3) {
                val clusters = clusterByDuration(remainingInTitle, maxDiffMs = 3_000L)
                for (cluster in clusters) {
                    val hasUnknownArtist = cluster.any { normArtistById[it.id].isNullOrBlank() }
                    val allSameArtist = cluster.mapNotNull { normArtistById[it.id]?.ifBlank { null } }.distinct().size <= 1
                    if ((hasUnknownArtist || allSameArtist || normTitle.length >= 4) && cluster.size > 1) {
                        val unvisited = cluster.filter { it.id !in visitedSongIds }
                        if (unvisited.size > 1) {
                            unvisited.forEach { visitedSongIds.add(it.id) }
                            resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.SIMILAR_TITLE))
                        }
                    }
                }
            }
        }

        // Pass 2: Cleaned Filename Match + Duration within ±3s (catches untagged downloads like "song.mp3" vs "song (1).m4a")
        val remainingAfterPass1 = allSongs.filter { it.id !in visitedSongIds }
        val byFilename = remainingAfterPass1.groupBy { normalizeFilename(it.filePath) }
        for ((normName, songs) in byFilename) {
            if (normName.length < 3 || songs.size < 2) continue
            val clusters = clusterByDuration(songs, maxDiffMs = 3_000L)
            for (cluster in clusters) {
                val unvisited = cluster.filter { it.id !in visitedSongIds }
                if (unvisited.size > 1) {
                    unvisited.forEach { visitedSongIds.add(it.id) }
                    resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.FILENAME_CLONE))
                }
            }
        }

        // Pass 3: Exact File Hash Clones for remaining tracks whose tags differed completely
        val remainingAfterPass2 = allSongs.filter { it.id !in visitedSongIds }
        val exactRemainder = analyzeExactFileMatches(remainingAfterPass2, fileSizes)
        for (group in exactRemainder) {
            val unvisited = group.tracks.map { it.song }.filter { it.id !in visitedSongIds }
            if (unvisited.size > 1) {
                unvisited.forEach { visitedSongIds.add(it.id) }
                resultGroups.add(buildDuplicateGroup(unvisited, fileSizes, DuplicateMatchReason.EXACT_CLONE))
            }
        }

        return resultGroups.sortedWith(
            compareByDescending<DuplicateGroup> { it.potentialReclaimBytes }
                .thenBy { it.displayTitle.lowercase(Locale.ROOT) }
        )
    }

    /**
     * Computes a fast SHA-256 fingerprint over the start, middle, and end 64KB chunks
     * of the file so identical copies match reliably without reading huge FLACs fully.
     */
    private fun computePartialAudioHash(filePath: String, fileSize: Long): String? {
        if (filePath.isBlank() || fileSize <= 0L) return null
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val chunkSize = 65_536
            val buffer = ByteArray(chunkSize)

            RandomAccessFile(file, "r").use { raf ->
                // Head chunk
                val readHead = raf.read(buffer, 0, minOf(chunkSize.toLong(), fileSize).toInt())
                if (readHead > 0) digest.update(buffer, 0, readHead)

                // Middle chunk
                if (fileSize > chunkSize * 2L) {
                    val midOffset = (fileSize / 2L) - (chunkSize / 2L)
                    raf.seek(midOffset.coerceAtLeast(0L))
                    val readMid = raf.read(buffer, 0, chunkSize)
                    if (readMid > 0) digest.update(buffer, 0, readMid)
                }

                // Tail chunk
                if (fileSize > chunkSize) {
                    val tailOffset = (fileSize - chunkSize).coerceAtLeast(0L)
                    raf.seek(tailOffset)
                    val readTail = raf.read(buffer, 0, chunkSize)
                    if (readTail > 0) digest.update(buffer, 0, readTail)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
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

    private fun estimateBitrateKbps(sizeBytes: Long, durationMs: Long, ext: String): Int {
        if (sizeBytes <= 0L || durationMs <= 1_000L) return 0
        val rawKbps = ((sizeBytes * 8L) / durationMs).toInt()
        if (ext in setOf("FLAC", "WAV", "ALAC", "AIFF", "AIF")) {
            return rawKbps.coerceIn(500, 4608)
        }
        // Snap common lossy bitrates cleanly for clear user display
        return when {
            rawKbps >= 300 -> 320
            rawKbps >= 235 -> 256
            rawKbps >= 175 -> 192
            rawKbps >= 145 -> 160
            rawKbps >= 112 -> 128
            rawKbps >= 80 -> 96
            rawKbps >= 48 -> 64
            else -> rawKbps.coerceAtLeast(32)
        }
    }

    private fun computeMetadataCleanScore(song: Song): Long {
        var score = 0L
        if (!SmartTitleCleaner.isPlaceholderArtist(song.artist)) score += 3_000L
        val albumClean = SmartTitleCleaner.cleanAlbum(song.album, applyTitleCase = false)
        if (albumClean.isNotBlank()) score += 1_500L

        val rawTitle = song.title
        val cleanTitle = SmartTitleCleaner.cleanTitle(rawTitle, song.filePath, applyTitleCase = false)
        if (rawTitle.isNotBlank() && rawTitle.equals(cleanTitle, ignoreCase = true)) {
            score += 2_000L
        }
        // Slight penalty for obvious duplicate suffixes like "(1)" or "copy"
        if (rawTitle.contains("(1)") || rawTitle.contains("(2)") || song.filePath.contains("(1)") || song.filePath.contains("(2)")) {
            score -= 1_000L
        }
        return score
    }

    private fun buildDuplicateGroup(
        songs: List<Song>,
        fileSizes: Map<Long, Long>,
        reason: DuplicateMatchReason
    ): DuplicateGroup {
        val tracks = songs.map { song ->
            val size = fileSizes[song.id] ?: 0L
            val ext = song.filePath.substringAfterLast('.', "mp3").uppercase(Locale.ROOT).ifBlank { "MP3" }
            val bitrateKbps = estimateBitrateKbps(size, song.duration, ext)
            val isLossless = ext in setOf("FLAC", "WAV", "ALAC", "AIFF", "AIF")
            val hasArt = song.albumArtUri != null

            // Tier 1: Audio Quality (Lossless > 320k > 256k > 192k > 128k)
            val audioQualityTier = when {
                isLossless -> 10_000_000L + bitrateKbps * 100L
                bitrateKbps >= 320 -> 5_000_000L
                bitrateKbps >= 256 -> 4_000_000L
                bitrateKbps >= 192 -> 3_000_000L
                bitrateKbps >= 160 -> 2_500_000L
                bitrateKbps >= 128 -> 2_000_000L
                else -> 1_000_000L + bitrateKbps * 1000L
            }
            val codecTieBreaker = when (ext) {
                "FLAC" -> 40_000L
                "WAV", "ALAC", "AIFF" -> 35_000L
                "M4A", "AAC", "OPUS" -> 20_000L
                "MP3" -> 15_000L
                else -> 5_000L
            }
            // Tier 2: Embedded Cover Art
            val artBonus = if (hasArt) 100_000L else 0L
            // Tier 3: Clean Metadata
            val metadataBonus = computeMetadataCleanScore(song)

            val qualityScore = audioQualityTier + artBonus + codecTieBreaker + metadataBonus
            val formattedSize = if (size > 0L) formatFileSize(size) else "--"
            val specLabel = when {
                isLossless -> "$ext Lossless • $formattedSize"
                bitrateKbps > 0 -> "$bitrateKbps kbps $ext • $formattedSize"
                else -> "$ext • $formattedSize"
            }

            DuplicateTrackItem(
                song = song,
                fileSize = size,
                formattedSize = formattedSize,
                fileExtension = ext,
                estimatedBitrateKbps = bitrateKbps,
                hasCoverArt = hasArt,
                isBestQuality = false,
                qualityScore = qualityScore,
                qualityBadge = specLabel
            )
        }.sortedWith(
            compareByDescending<DuplicateTrackItem> { it.qualityScore }
                .thenByDescending { it.fileSize }
                .thenBy { it.song.dateAdded }
        )

        val updatedTracks = tracks.mapIndexed { index, track ->
            track.copy(isBestQuality = index == 0)
        }

        val primarySong = updatedTracks.first().song
        val cleanDisplayTitle = SmartTitleCleaner.cleanTitle(primarySong.title, primarySong.filePath, applyTitleCase = true)
            .ifBlank { primarySong.title }
        val cleanDisplayArtist = if (SmartTitleCleaner.isPlaceholderArtist(primarySong.artist)) {
            "Unknown Artist"
        } else {
            primarySong.artist.trim()
        }

        val bestSize = updatedTracks.first().fileSize
        val totalGroupSize = updatedTracks.sumOf { it.fileSize }
        val potentialReclaim = (totalGroupSize - bestSize).coerceAtLeast(0L)

        return DuplicateGroup(
            groupId = "dup_${primarySong.id}_${updatedTracks.size}_${reason.name}",
            displayTitle = cleanDisplayTitle,
            displayArtist = cleanDisplayArtist,
            durationMs = primarySong.duration,
            matchReason = reason,
            tracks = updatedTracks,
            potentialReclaimBytes = potentialReclaim
        )
    }
}
