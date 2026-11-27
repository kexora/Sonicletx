package com.kexora.soniclet.lyrics

import android.content.Context
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.LyricLine
import com.kexora.soniclet.player.LyricWord
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Result representing extracted lyrics, source origin, and parsed word-by-word line data.
 */
data class LyricsResult(
    val rawLyrics: String,
    val source: String,
    val lines: List<LyricLine>
)

object LyricsUtil {

    private const val LYRICS_FOLDER = "lyrics"
    private const val SYNC_OFFSET_PREFS = "soniclet_lyrics_sync_offsets"

    /**
     * Loads the user's persisted per-song lyrics sync offset (in milliseconds).
     */
    fun getSyncOffsetMs(context: Context, songId: Long): Long {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(SYNC_OFFSET_PREFS, Context.MODE_PRIVATE)
            prefs.getLong("offset_$songId", 0L).coerceIn(-15000L, 15000L)
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Persists a per-song custom lyrics sync offset (in milliseconds) in SharedPreferences.
     */
    fun saveSyncOffsetMs(context: Context, songId: Long, offsetMs: Long) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(SYNC_OFFSET_PREFS, Context.MODE_PRIVATE)
            val clamped = offsetMs.coerceIn(-15000L, 15000L)
            if (clamped == 0L) {
                prefs.edit().remove("offset_$songId").apply()
            } else {
                prefs.edit().putLong("offset_$songId", clamped).apply()
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * High-precision O(log n) Binary Search active lyric line resolver.
     * Returns the index of the latest lyric line whose timestamp <= effectivePositionMs,
     * or -1 if playback is before the first timestamped line.
     */
    fun findActiveLyricIndex(lyrics: List<LyricLine>, effectivePositionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        if (effectivePositionMs < lyrics.first().timeMs) return -1
        if (effectivePositionMs >= lyrics.last().timeMs) return lyrics.lastIndex

        var low = 0
        var high = lyrics.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lyrics[mid].timeMs <= effectivePositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Tries to find time-synced or embedded lyrics for a given song:
     * 1. Checks app internal imported lyrics for this song ID.
     * 2. Checks local .lrc file in the same directory as the song file.
     * 3. Checks embedded ID3 / Vorbis / MP4 lyrics tag via jaudiotagger.
     */
    fun getLyrics(context: Context, song: Song): LyricsResult? {
        // 1. Check user-imported lyrics stored locally
        try {
            val customFile = File(context.filesDir, "$LYRICS_FOLDER/song_${song.id}.lrc")
            if (customFile.exists() && customFile.canRead()) {
                val text = customFile.readText()
                if (text.isNotBlank()) {
                    val lines = parseLrc(text, song.duration)
                    if (lines.isNotEmpty()) {
                        return LyricsResult(rawLyrics = text, source = "Imported .LRC", lines = lines)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Check external .lrc file in same directory or subfolders
        if (song.filePath.isNotBlank()) {
            try {
                val audioFile = File(song.filePath)
                if (audioFile.exists()) {
                    val parent = audioFile.parentFile
                    if (parent != null && parent.exists()) {
                        val baseName = audioFile.nameWithoutExtension
                        val candidates = listOf(
                            File(parent, "$baseName.lrc"),
                            File(parent, "$baseName.LRC"),
                            File(parent, "${song.title}.lrc"),
                            File(File(parent, "lyrics"), "$baseName.lrc"),
                            File(File(parent, "Lyrics"), "$baseName.lrc")
                        )

                        for (candidate in candidates) {
                            if (candidate.exists() && candidate.canRead()) {
                                val text = candidate.readText()
                                if (text.isNotBlank()) {
                                    val lines = parseLrc(text, song.duration)
                                    if (lines.isNotEmpty()) {
                                        return LyricsResult(rawLyrics = text, source = "Offline .LRC", lines = lines)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Check embedded lyrics tag via jaudiotagger
        if (song.filePath.isNotBlank()) {
            try {
                val audioFile = File(song.filePath)
                if (audioFile.exists() && audioFile.canRead()) {
                    val jaudioFile = AudioFileIO.read(audioFile)
                    val tag = jaudioFile.tag
                    if (tag != null) {
                        val embeddedLyrics = tag.getFirst(FieldKey.LYRICS)
                        if (!embeddedLyrics.isNullOrBlank()) {
                            val lines = parseLrc(embeddedLyrics, song.duration)
                            if (lines.isNotEmpty()) {
                                return LyricsResult(rawLyrics = embeddedLyrics, source = "Embedded Tag", lines = lines)
                            }
                        }

                        // Check comment tag fallback (some encoders store LRC in comment)
                        val comment = tag.getFirst(FieldKey.COMMENT)
                        if (!comment.isNullOrBlank() && comment.contains("[00:") && comment.contains("]")) {
                            val lines = parseLrc(comment, song.duration)
                            if (lines.isNotEmpty()) {
                                return LyricsResult(rawLyrics = comment, source = "Embedded Tag", lines = lines)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Backward-compatible simple file path lookup.
     */
    fun getLyrics(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) return null

        val lrcFile = File(file.parentFile, file.nameWithoutExtension + ".lrc")
        if (lrcFile.exists() && lrcFile.canRead()) {
            try {
                return lrcFile.readText()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            if (tag != null) {
                val lyrics = tag.getFirst(FieldKey.LYRICS)
                if (!lyrics.isNullOrBlank()) {
                    return lyrics
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Saves user-imported .lrc content into internal app storage for a specific song.
     */
    fun saveLyrics(context: Context, songId: Long, lrcContent: String): Boolean {
        return try {
            val folder = File(context.filesDir, LYRICS_FOLDER)
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, "song_$songId.lrc")
            file.writeText(lrcContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes custom imported lyrics file for a song.
     */
    fun clearCustomLyrics(context: Context, songId: Long): Boolean {
        return try {
            val file = File(context.filesDir, "$LYRICS_FOLDER/song_$songId.lrc")
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseTimestampParts(minStr: String, secStr: String, fracStr: String): Long {
        val min = minStr.toLongOrNull() ?: 0L
        val sec = secStr.toLongOrNull() ?: 0L
        val ms = when (fracStr.length) {
            0 -> 0L
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> (fracStr.take(3).toLongOrNull() ?: 0L)
        }
        return (min * 60_000L) + (sec * 1_000L) + ms
    }

    /**
     * Full LRC & Enhanced Karaoke Parser:
     * - Supports [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], and [mm:ss] timestamp formats.
     * - Expands multi-timestamp lines ([00:15.20][01:22.40]Chorus) into sorted individual entries.
     * - Respects [offset:+/-ms] LRC header tags automatically.
     * - Supports enhanced word-level timestamps (<mm:ss.xx>word) and estimates smooth
     *   character/word progress across (nextLineTime - currentLineTime) in Karaoke mode.
     */
    fun parseLrc(lrcText: String, totalTrackDurationMs: Long = 0L): List<LyricLine> {
        if (lrcText.isBlank()) return emptyList()

        // Strip UTF-8 BOM if present
        val cleanedInput = lrcText.removePrefix("\uFEFF")

        // 1. Check for global [offset:+/-ms] tag (positive offset means lyrics appear earlier -> subtract or add per spec)
        val offsetRegex = Regex("\\[offset:\\s*([+-]?\\d+)\\s*\\]", RegexOption.IGNORE_CASE)
        val globalOffsetMs = offsetRegex.find(cleanedInput)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        // 2. Match [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], and [mm:ss]
        val timestampRegex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
        val rawLines = cleanedInput.lines()
        val parsedEntries = mutableListOf<Pair<Long, String>>()

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val lower = trimmed.lowercase()
            if (lower.startsWith("[ti:") ||
                lower.startsWith("[ar:") ||
                lower.startsWith("[al:") ||
                lower.startsWith("[au:") ||
                lower.startsWith("[by:") ||
                lower.startsWith("[offset:") ||
                lower.startsWith("[re:") ||
                lower.startsWith("[ve:") ||
                lower.startsWith("[length:")
            ) {
                continue
            }

            val matches = timestampRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val lastMatch = matches.last()
            val lineContent = trimmed.substring(lastMatch.range.last + 1).trim()

            // Expand every timestamp tag on the line into its own sorted entry
            for (match in matches) {
                val baseTimeMs = parseTimestampParts(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues[3]
                )
                val adjustedTimeMs = (baseTimeMs + globalOffsetMs).coerceAtLeast(0L)
                parsedEntries.add(Pair(adjustedTimeMs, lineContent))
            }
        }

        if (parsedEntries.isEmpty()) {
            return parsePlainTextLyrics(cleanedInput, totalTrackDurationMs)
        }

        // Sort chronologically and filter consecutive empty instrumental breaks neatly
        val sorted = parsedEntries.sortedBy { it.first }
        val result = mutableListOf<LyricLine>()

        // Match enhanced word-level timestamps: <mm:ss.xx>word or <mm:ss:xx>word or <mm:ss>word
        val wordTagRegex = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>([^<]*)")

        for (i in sorted.indices) {
            val (timeMs, rawText) = sorted[i]
            val fallbackEnd = if (totalTrackDurationMs > timeMs) {
                minOf(totalTrackDurationMs, timeMs + 6000L)
            } else {
                timeMs + 4500L
            }
            val nextTimeMs = if (i + 1 < sorted.size) sorted[i + 1].first else fallbackEnd
            val rawSpan = (nextTimeMs - timeMs).coerceAtLeast(600L)
            // Cap overly long instrumental pauses so word sweep completes naturally while vocals sing
            val durationMs = rawSpan.coerceAtMost(8500L)

            val wordMatches = wordTagRegex.findAll(rawText).toList()

            if (wordMatches.isNotEmpty()) {
                val words = mutableListOf<LyricWord>()
                val cleanTextBuilder = StringBuilder()

                // Handle any leading untagged text before the first <mm:ss.xx> tag
                val prefixText = rawText.substring(0, wordMatches.first().range.first).trim()
                if (prefixText.isNotEmpty()) {
                    val firstWordStart = (parseTimestampParts(
                        wordMatches.first().groupValues[1],
                        wordMatches.first().groupValues[2],
                        wordMatches.first().groupValues[3]
                    ) + globalOffsetMs).coerceAtLeast(timeMs + 100L)
                    words.add(LyricWord(text = prefixText, startMs = timeMs, endMs = firstWordStart))
                    cleanTextBuilder.append(prefixText)
                }

                for (wIdx in wordMatches.indices) {
                    val wm = wordMatches[wIdx]
                    val wordStart = (parseTimestampParts(
                        wm.groupValues[1],
                        wm.groupValues[2],
                        wm.groupValues[3]
                    ) + globalOffsetMs).coerceAtLeast(timeMs)
                    val wordText = wm.groupValues[4].trim()

                    val wordEnd = if (wIdx + 1 < wordMatches.size) {
                        val nextWm = wordMatches[wIdx + 1]
                        val nextStart = parseTimestampParts(
                            nextWm.groupValues[1],
                            nextWm.groupValues[2],
                            nextWm.groupValues[3]
                        ) + globalOffsetMs
                        nextStart.coerceAtLeast(wordStart + 60L)
                    } else {
                        (timeMs + durationMs).coerceAtLeast(wordStart + 250L)
                    }

                    if (wordText.isNotEmpty()) {
                        words.add(LyricWord(text = wordText, startMs = wordStart, endMs = wordEnd))
                        if (cleanTextBuilder.isNotEmpty()) cleanTextBuilder.append(" ")
                        cleanTextBuilder.append(wordText)
                    }
                }

                val fullText = cleanTextBuilder.toString().ifEmpty {
                    rawText.replace(Regex("<[^>]+>"), "").trim()
                }

                if (fullText.isNotBlank()) {
                    result.add(
                        LyricLine(
                            timeMs = timeMs,
                            text = fullText,
                            durationMs = durationMs,
                            words = words,
                            isKaraoke = true
                        )
                    )
                }
            } else {
                val cleanText = rawText.replace(Regex("<[^>]+>"), "").trim()
                if (cleanText.isBlank()) {
                    // Preserve subtle instrumental marker only if previous wasn't also a marker
                    if (result.isNotEmpty() && result.last().text != "•••") {
                        result.add(
                            LyricLine(
                                timeMs = timeMs,
                                text = "•••",
                                durationMs = durationMs,
                                words = emptyList(),
                                isKaraoke = false
                            )
                        )
                    }
                    continue
                }

                val splitWords = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (splitWords.isEmpty()) continue

                // Distribute word timing proportionally by character length across the line's duration
                val weights = splitWords.map { it.length.coerceAtLeast(1) + 1 }
                val totalWeight = weights.sum().coerceAtLeast(1)
                val words = mutableListOf<LyricWord>()
                var cursorMs = timeMs

                for (wIdx in splitWords.indices) {
                    val wordEnd = if (wIdx == splitWords.lastIndex) {
                        timeMs + durationMs
                    } else {
                        val wordDur = ((weights[wIdx].toDouble() / totalWeight) * durationMs).toLong().coerceAtLeast(80L)
                        (cursorMs + wordDur).coerceAtMost(timeMs + durationMs)
                    }
                    words.add(LyricWord(text = splitWords[wIdx], startMs = cursorMs, endMs = wordEnd.coerceAtLeast(cursorMs + 1L)))
                    cursorMs = wordEnd
                }

                result.add(
                    LyricLine(
                        timeMs = timeMs,
                        text = cleanText,
                        durationMs = durationMs,
                        words = words,
                        isKaraoke = true
                    )
                )
            }
        }

        return result
    }

    /**
     * Fallback parser for plain text lyrics without timestamps.
     */
    private fun parsePlainTextLyrics(plainText: String, totalTrackDurationMs: Long): List<LyricLine> {
        val rawLines = plainText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") }

        if (rawLines.isEmpty()) return emptyList()

        val lineDuration = if (totalTrackDurationMs > 0L) {
            (totalTrackDurationMs / rawLines.size).coerceIn(2500L, 8000L)
        } else {
            3500L
        }

        return rawLines.mapIndexed { index, line ->
            val timeMs = index * lineDuration
            val splitWords = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            val words = if (splitWords.isNotEmpty()) {
                val wordDur = (lineDuration / splitWords.size).coerceAtLeast(150L)
                splitWords.mapIndexed { wIdx, w ->
                    val start = timeMs + (wIdx * wordDur)
                    LyricWord(text = w, startMs = start, endMs = start + wordDur)
                }
            } else emptyList()

            LyricLine(
                timeMs = timeMs,
                text = line,
                durationMs = lineDuration,
                words = words,
                isKaraoke = false
            )
        }
    }
}
