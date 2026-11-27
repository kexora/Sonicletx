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

    /**
     * Parses LRC text (standard or enhanced karaoke word-level format) into a chronological list
     * of [LyricLine] instances with millisecond precision and word-by-word timing breakdown.
     */
    fun parseLrc(lrcText: String, totalTrackDurationMs: Long = 0L): List<LyricLine> {
        if (lrcText.isBlank()) return emptyList()

        // 1. Check for global [offset:+/-ms] tag
        val offsetRegex = Regex("\\[offset:\\s*([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)
        val globalOffsetMs = offsetRegex.find(lrcText)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        // 2. Extract lines with timestamps: e.g. [01:23.45] or [01:23.456] or [01:23]
        val timestampRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[\\.:](\\d{1,3}))?\\]")
        val rawLines = lrcText.lines()
        val parsedEntries = mutableListOf<Pair<Long, String>>()

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // Skip standard ID3/LRC metadata tags
            val lower = trimmed.lowercase()
            if (lower.startsWith("[ti:") ||
                lower.startsWith("[ar:") ||
                lower.startsWith("[al:") ||
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

            // The lyric text following all timestamp tags on this line
            val lastMatch = matches.last()
            val lineContent = trimmed.substring(lastMatch.range.last + 1).trim()

            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when (msStr.length) {
                    1 -> (msStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (msStr.toLongOrNull() ?: 0L) * 10L
                    3 -> (msStr.toLongOrNull() ?: 0L)
                    else -> 0L
                }
                val totalTime = (min * 60 * 1000L) + (sec * 1000L) + ms + globalOffsetMs
                parsedEntries.add(Pair(totalTime.coerceAtLeast(0L), lineContent))
            }
        }

        // If no timestamped lines could be found, fall back to plain text line separation
        if (parsedEntries.isEmpty()) {
            return parsePlainTextLyrics(lrcText, totalTrackDurationMs)
        }

        val sorted = parsedEntries.sortedBy { it.first }
        val result = mutableListOf<LyricLine>()

        val wordTagRegex = Regex("<(\\d{1,2}):(\\d{2})(?:[\\.:](\\d{1,3}))?>([^<]*)")

        for (i in sorted.indices) {
            val (timeMs, rawText) = sorted[i]
            val nextTimeMs = if (i + 1 < sorted.size) sorted[i + 1].first else (timeMs + 4500L)
            val gap = (nextTimeMs - timeMs).coerceAtLeast(1000L)
            // Limit line duration so vocal lines don't stay active across long instrumentals
            val durationMs = if (gap > 7500L) 5000L else gap

            // Check if this line contains enhanced word timestamps: <mm:ss.xx>
            val wordMatches = wordTagRegex.findAll(rawText).toList()

            if (wordMatches.isNotEmpty()) {
                val words = mutableListOf<LyricWord>()
                val cleanTextBuilder = StringBuilder()

                for (wIdx in wordMatches.indices) {
                    val wm = wordMatches[wIdx]
                    val wMin = wm.groupValues[1].toLongOrNull() ?: 0L
                    val wSec = wm.groupValues[2].toLongOrNull() ?: 0L
                    val wMsStr = wm.groupValues[3]
                    val wMs = when (wMsStr.length) {
                        1 -> (wMsStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (wMsStr.toLongOrNull() ?: 0L) * 10L
                        3 -> (wMsStr.toLongOrNull() ?: 0L)
                        else -> 0L
                    }
                    val wordStart = ((wMin * 60 * 1000L) + (wSec * 1000L) + wMs + globalOffsetMs).coerceAtLeast(timeMs)
                    val wordText = wm.groupValues[4].trim()

                    val wordEnd = if (wIdx + 1 < wordMatches.size) {
                        val nextWm = wordMatches[wIdx + 1]
                        val nextMin = nextWm.groupValues[1].toLongOrNull() ?: 0L
                        val nextSec = nextWm.groupValues[2].toLongOrNull() ?: 0L
                        val nextMsStr = nextWm.groupValues[3]
                        val nextMs = when (nextMsStr.length) {
                            1 -> (nextMsStr.toLongOrNull() ?: 0L) * 100L
                            2 -> (nextMsStr.toLongOrNull() ?: 0L) * 10L
                            3 -> (nextMsStr.toLongOrNull() ?: 0L)
                            else -> 0L
                        }
                        val nextStart = ((nextMin * 60 * 1000L) + (nextSec * 1000L) + nextMs + globalOffsetMs)
                        nextStart.coerceAtLeast(wordStart + 80L)
                    } else {
                        (wordStart + 1200L).coerceAtMost(timeMs + durationMs)
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

                result.add(
                    LyricLine(
                        timeMs = timeMs,
                        text = fullText,
                        durationMs = durationMs,
                        words = words,
                        isKaraoke = true
                    )
                )
            } else {
                // Standard LRC line without explicit word timestamps:
                // Distribute words smoothly across the line duration for natural karaoke progression!
                val cleanText = rawText.replace(Regex("<[^>]+>"), "").trim()
                val splitWords = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }

                if (splitWords.isEmpty()) {
                    result.add(
                        LyricLine(
                            timeMs = timeMs,
                            text = cleanText.ifEmpty { "♪" },
                            durationMs = durationMs,
                            words = emptyList(),
                            isKaraoke = false
                        )
                    )
                } else {
                    val weights = splitWords.map { it.length.coerceAtLeast(1) + 2 }
                    val totalWeight = weights.sum().coerceAtLeast(1)
                    val words = mutableListOf<LyricWord>()
                    var currentWordStart = timeMs

                    for (wIdx in splitWords.indices) {
                        val wordDuration = ((weights[wIdx].toFloat() / totalWeight) * durationMs).toLong().coerceAtLeast(120L)
                        val wordEnd = (currentWordStart + wordDuration).coerceAtMost(timeMs + durationMs)
                        words.add(LyricWord(text = splitWords[wIdx], startMs = currentWordStart, endMs = wordEnd))
                        currentWordStart = wordEnd
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
