package com.kexora.soniclet.library

import com.kexora.soniclet.database.ListeningHistoryDao
import com.kexora.soniclet.database.ListeningHistoryEntity
import com.kexora.soniclet.database.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Standalone permanent Room-backed repository for Soniclet Wrapped listening history.
 *
 * - Persists every track genuinely listened to for >= 15 seconds.
 * - Survives device file moves, renames, and physical deletions (never auto-erased on scan).
 * - Supports explicit user removal of individual entries from Wrapped history.
 */
@Singleton
class ListeningHistoryRepository @Inject constructor(
    private val dao: ListeningHistoryDao
) {
    private val writeMutex = Mutex()

    val allHistory: Flow<List<ListeningHistoryEntity>> = dao.observeAllHistory()

    suspend fun getAllHistorySync(): List<ListeningHistoryEntity> = withContext(Dispatchers.IO) {
        dao.getAllHistorySync()
    }

    /**
     * Records a qualified listen (>= 15s) into permanent Wrapped history.
     * When [incrementPlayCount] is true, increments [ListeningHistoryEntity.playCount] by 1.
     */
    suspend fun recordQualifiedListen(
        song: Song,
        listenedMs: Long,
        incrementPlayCount: Boolean
    ) = withContext(Dispatchers.IO) {
        if (listenedMs <= 0L && !incrementPlayCount) return@withContext
        if (incrementPlayCount && listenedMs < MIN_QUALIFIED_LISTEN_MS) return@withContext

        writeMutex.withLock {
            val key = buildCanonicalSongKey(song)
            val existing = dao.getBySongKey(key) ?: dao.getBySongId(song.id)
            val cleanedArtist = normalizeDisplayArtist(song.artist)
            val cleanedAlbum = normalizeDisplayAlbum(song.album)
            val now = System.currentTimeMillis()

            val updated = if (existing != null) {
                existing.copy(
                    lastKnownSongId = song.id,
                    title = song.title.ifBlank { existing.title },
                    artist = if (isMeaningfulLabel(cleanedArtist)) cleanedArtist else existing.artist,
                    album = if (isMeaningfulLabel(cleanedAlbum)) cleanedAlbum else existing.album,
                    genre = song.genre.ifBlank { existing.genre },
                    filePath = song.filePath.ifBlank { existing.filePath },
                    totalDurationListenedMs = existing.totalDurationListenedMs + listenedMs.coerceAtLeast(0L),
                    playCount = existing.playCount + if (incrementPlayCount) 1 else 0,
                    lastPlayedTimestamp = now
                )
            } else {
                ListeningHistoryEntity(
                    songKey = key,
                    lastKnownSongId = song.id,
                    title = song.title.ifBlank { "Untitled Track" },
                    artist = cleanedArtist,
                    album = cleanedAlbum,
                    genre = song.genre.trim(),
                    filePath = song.filePath,
                    totalDurationListenedMs = listenedMs.coerceAtLeast(0L),
                    playCount = if (incrementPlayCount) 1 else 0,
                    lastPlayedTimestamp = now
                )
            }
            dao.upsert(updated)
        }
    }

    /**
     * Adds incremental listening duration to a track that has already crossed the 15s threshold
     * in the current playback session, without double-incrementing playCount.
     */
    suspend fun addListeningDurationDelta(song: Song, deltaMs: Long) {
        if (deltaMs <= 0L) return
        recordQualifiedListen(song = song, listenedMs = deltaMs, incrementPlayCount = false)
    }

    /**
     * One-time non-destructive backfill from legacy SharedPreferences stats so existing users
     * retain their listening stats after upgrading to Room-backed Wrapped history.
     */
    suspend fun backfillFromLegacyStats(
        songs: List<Song>,
        playDurations: Map<Long, Long>,
        playCounts: Map<Long, Int>,
        lastPlayedTimestamps: Map<Long, Long>
    ) = withContext(Dispatchers.IO) {
        if (songs.isEmpty() || (playDurations.isEmpty() && playCounts.isEmpty())) return@withContext
        writeMutex.withLock {
            val existingAll = dao.getAllHistorySync()
            val existingByKey = existingAll.associateBy { it.songKey }
            val existingById = existingAll.associateBy { it.lastKnownSongId }
            val toUpsert = mutableListOf<ListeningHistoryEntity>()

            for (song in songs) {
                val dur = playDurations[song.id] ?: 0L
                val count = playCounts[song.id] ?: 0
                if (dur < MIN_QUALIFIED_LISTEN_MS && count <= 0) continue

                val key = buildCanonicalSongKey(song)
                val existing = existingByKey[key] ?: existingById[song.id]
                val effectiveDur = if (dur >= MIN_QUALIFIED_LISTEN_MS) dur else (count.toLong() * song.duration.coerceAtLeast(30_000L))
                val effectiveCount = count.coerceAtLeast(1)
                val ts = lastPlayedTimestamps[song.id] ?: System.currentTimeMillis()

                if (existing == null) {
                    toUpsert.add(
                        ListeningHistoryEntity(
                            songKey = key,
                            lastKnownSongId = song.id,
                            title = song.title.ifBlank { "Untitled Track" },
                            artist = normalizeDisplayArtist(song.artist),
                            album = normalizeDisplayAlbum(song.album),
                            genre = song.genre.trim(),
                            filePath = song.filePath,
                            totalDurationListenedMs = effectiveDur,
                            playCount = effectiveCount,
                            lastPlayedTimestamp = ts
                        )
                    )
                } else if (existing.lastKnownSongId != song.id || effectiveDur > existing.totalDurationListenedMs) {
                    toUpsert.add(
                        existing.copy(
                            lastKnownSongId = song.id,
                            filePath = song.filePath.ifBlank { existing.filePath },
                            totalDurationListenedMs = maxOf(existing.totalDurationListenedMs, effectiveDur),
                            playCount = maxOf(existing.playCount, effectiveCount),
                            lastPlayedTimestamp = maxOf(existing.lastPlayedTimestamp, ts)
                        )
                    )
                }
            }

            if (toUpsert.isNotEmpty()) {
                dao.upsertAll(toUpsert)
            }
        }
    }

    /**
     * Keeps lastKnownSongId aligned if a file moved or was renamed, without ever deleting history
     * entries whose files were deleted from the device.
     */
    suspend fun syncMovedFileIds(currentSongs: List<Song>) = withContext(Dispatchers.IO) {
        if (currentSongs.isEmpty()) return@withContext
        writeMutex.withLock {
            val allRecords = dao.getAllHistorySync()
            if (allRecords.isEmpty()) return@withContext

            val songByKey = currentSongs.associateBy { buildCanonicalSongKey(it) }
            val songByTitleArtist = currentSongs.associateBy {
                "${normalizeToken(it.title)}|${normalizeToken(normalizeDisplayArtist(it.artist))}"
            }
            val updates = mutableListOf<ListeningHistoryEntity>()

            for (record in allRecords) {
                val matched = songByKey[record.songKey]
                    ?: songByTitleArtist["${normalizeToken(record.title)}|${normalizeToken(normalizeDisplayArtist(record.artist))}"]
                if (matched != null && (record.lastKnownSongId != matched.id || record.filePath != matched.filePath)) {
                    updates.add(
                        record.copy(
                            lastKnownSongId = matched.id,
                            filePath = matched.filePath.ifBlank { record.filePath }
                        )
                    )
                }
            }

            if (updates.isNotEmpty()) {
                dao.upsertAll(updates)
            }
        }
    }

    suspend fun removeHistoryEntry(songKey: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            dao.deleteBySongKey(songKey)
        }
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            dao.clearAll()
        }
    }

    companion object {
        const val MIN_QUALIFIED_LISTEN_MS = 15_000L

        // 6-Step Smart Resume Logic Engine canonical thresholds
        const val COMPLETION_THRESHOLD_RATIO = 0.92f
        const val NEAR_END_GUARD_MS = 10_000L
        const val EARLY_START_GUARD_MS = 10_000L
        const val MIN_RESUME_TRACK_DURATION_MS = 60_000L
        const val CONTEXT_REWIND_MS = 2_000L

        /**
         * Step 2: Returns true when a track has reached >= 92% of its duration or is within
         * the final 10 seconds, treating it as completed so near-end timestamps are never saved.
         */
        fun isTrackCompletedOrNearEnd(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0L) return false
            val completionCutoffMs = (durationMs * COMPLETION_THRESHOLD_RATIO).toLong()
            return positionMs >= completionCutoffMs || (durationMs - positionMs) <= NEAR_END_GUARD_MS
        }

        /**
         * Steps 2 & 3: Sanitizes a track's position before persisting it for resume.
         * Returns 0L (clear any saved entry) for short tracks (< 60s), early starts (< 10s),
         * unknown durations, or tracks at/past the near-end completion threshold (>= 92% or <= 10s remaining).
         */
        fun sanitizePositionForResumeSave(positionMs: Long, durationMs: Long): Long {
            if (durationMs in 1L until MIN_RESUME_TRACK_DURATION_MS) return 0L
            if (positionMs < EARLY_START_GUARD_MS) return 0L
            if (durationMs <= 0L) return 0L
            if (isTrackCompletedOrNearEnd(positionMs, durationMs)) return 0L
            return positionMs
        }

        /**
         * Step 4: Strictly validates a stored timestamp before seeking on track load and applies
         * a smooth 2-second context rewind cushion. Returns 0L when invalid/stale.
         */
        fun resolveSmartResumePosition(savedPos: Long, durationMs: Long): Long {
            if (durationMs < MIN_RESUME_TRACK_DURATION_MS) return 0L
            val completionCutoffMs = (durationMs * COMPLETION_THRESHOLD_RATIO).toLong()
            val isValid = savedPos > EARLY_START_GUARD_MS &&
                savedPos < (durationMs - NEAR_END_GUARD_MS) &&
                savedPos < completionCutoffMs
            return if (isValid) {
                (savedPos - CONTEXT_REWIND_MS).coerceAtLeast(0L)
            } else {
                0L
            }
        }

        fun buildCanonicalSongKey(song: Song): String {
            return buildCanonicalSongKey(
                title = song.title,
                artist = song.artist,
                durationMs = song.duration,
                filePath = song.filePath
            )
        }

        fun buildCanonicalSongKey(
            title: String,
            artist: String,
            durationMs: Long,
            filePath: String
        ): String {
            val normTitle = normalizeToken(title)
            val normArtist = normalizeToken(normalizeDisplayArtist(artist))
            val durationBucket = if (durationMs > 0L) (durationMs / 3000L) else 0L
            return if (normTitle.isNotBlank()) {
                "${normTitle}_${normArtist}_${durationBucket}"
            } else {
                val fileStem = normalizeToken(filePath.substringAfterLast('/').substringBeforeLast('.'))
                "file_${fileStem}_${durationBucket}"
            }
        }

        fun normalizeDisplayArtist(raw: String): String {
            val trimmed = raw.trim()
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

        fun normalizeDisplayAlbum(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty() ||
                trimmed.equals("<unknown>", ignoreCase = true) ||
                trimmed.equals("unknown", ignoreCase = true)
            ) {
                "Unknown Album"
            } else {
                trimmed
            }
        }

        fun isPlaceholderMetadata(value: String): Boolean {
            val norm = value.trim().lowercase(Locale.ROOT)
            if (norm.isBlank()) return true
            val exactPlaceholders = setOf(
                "<unknown>",
                "unknown",
                "unknown artist",
                "unknown album",
                "snaptube video",
                "snaptube",
                "snaptube audio",
                "voice recording",
                "voice recordings",
                "voice recorder",
                "whatsapp audio",
                "whatsapp voice notes",
                "telegram audio",
                "call recording",
                "call recordings",
                "audio",
                "music",
                "download",
                "downloads",
                "record",
                "recording",
                "recordings",
                "various",
                "none",
                "null"
            )
            if (norm in exactPlaceholders) return true
            return norm.contains("snaptube") ||
                norm.contains("whatsapp audio") ||
                norm.contains("whatsapp voice") ||
                norm.contains("voice recording") ||
                norm.contains("call recording") ||
                norm.startsWith("aud-") ||
                norm.startsWith("ptt-")
        }

        private fun isMeaningfulLabel(value: String): Boolean {
            return value.isNotBlank() && !isPlaceholderMetadata(value)
        }

        private fun normalizeToken(raw: String): String {
            return raw.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[^a-z0-9\\u0900-\\u097f]+"), "")
        }
    }
}
