package com.kexora.soniclet.utils

import java.util.Locale

data class CleanedTrackMetadata(
    val title: String,
    val artist: String,
    val album: String
)

data class SplitTitleArtistResult(
    val cleanTitle: String,
    val cleanArtist: String,
    val wasSplit: Boolean
)

/**
 * Multi-stage Smart Name Filter & Auto-Clean Engine for Soniclet.
 *
 * - Stage 1: Strips junk prefixes/suffixes, video/audio noise tags, website watermarks,
 *            bitrate tags, label spam, and media file extensions.
 * - Stage 2: Normalizes underscores & whitespace, removes empty leftover brackets,
 *            trims dangling delimiters, and applies smart Latin Title Case while
 *            preserving Devanagari / Indic Unicode scripts and recognized acronyms.
 * - Stage 3: Smartly splits downloaded "Title - Artist" / "Artist - Title" / "Title | Artist"
 *            patterns into separate title and artist fields when appropriate.
 */
object SmartTitleCleaner {

    private val PRESERVED_ACRONYMS = setOf(
        "DJ", "HD", "UHD", "4K", "8K", "EDM", "OST", "BGM", "VIP", "OG", "MC", "KK",
        "AP", "AR", "A.R.", "RD", "R.D.", "SD", "S.D.", "II", "III", "IV", "VI", "VII",
        "BTS", "NCR", "UK", "US", "USA", "NYC", "LA", "TV", "FM", "AM", "EP", "LP", "ID",
        "LO-FI", "R&B", "K-POP", "J-POP", "DHH"
    )

    private val SMALL_LATIN_WORDS = setOf(
        "a", "an", "and", "as", "at", "but", "by", "for", "from", "in", "into",
        "nor", "of", "on", "or", "per", "the", "to", "vs", "via", "with", "feat.", "ft."
    )

    private val EXTENSION_REGEX = Regex(
        "\\.(mp3|m4a|flac|wav|ogg|opus|aac|wma|mp4|mkv|webm|3gp)\\s*$",
        RegexOption.IGNORE_CASE
    )

    private val LEADING_PREFIX_REGEX = Regex(
        "^\\s*(?:" +
            "(?:full\\s+|official\\s+|lyrical\\s+|hd\\s+|4k\\s+|new\\s+|latest\\s+)?(?:video|audio|song|music\\s+video)\\s*[:\\-_|•]+\\s*" +
            "|\\[(?:video|audio|hd|4k|full\\s*video|official\\s*video|official\\s*audio)\\]\\s*" +
            "|\\((?:video|audio|hd|4k|full\\s*video|official\\s*video|official\\s*audio)\\)\\s*" +
            ")",
        RegexOption.IGNORE_CASE
    )

    private val BRACKETED_JUNK_REGEX = Regex(
        "[\\(\\[\\{]\\s*(?:" +
            "official\\s*(?:music\\s*|lyrical\\s*|full\\s*)?(?:video|audio|song|track|teaser|trailer)" +
            "|full\\s*(?:video|audio|song|hd|4k)" +
            "|lyrical\\s*(?:video|audio)?" +
            "|lyrics?\\s*(?:video)?" +
            "|audio\\s*(?:song|only)?" +
            "|video\\s*(?:song)?" +
            "|4k\\s*(?:video|uhd|hd)?" +
            "|1080p|720p|480p|hd|uhd|hq|mp4|m4a|mp3|flac|wav" +
            "|\\d{2,3}\\s*kbps" +
            "|snaptube(?:\\s*video)?" +
            "|pagalworld(?:\\.com|\\.in|\\.pw|\\.org|\\.info)?" +
            "|mr[\\s\\-]?jatt(?:\\.com)?" +
            "|djpunjab(?:\\.com|\\.fm)?" +
            "|songs\\.?pk" +
            "|y2mate(?:\\.com)?" +
            "|vevo|t[\\s\\-]?series|worldwide\\s*records(?:\\s*bhojpuri)?" +
            "|status|whatsapp\\s*status|ringtone" +
            ")[^\\)\\]\\}]*[\\)\\]\\}]",
        RegexOption.IGNORE_CASE
    )

    private val INLINE_WATERMARK_REGEX = Regex(
        "(?i)(?:" +
            "https?://\\S+" +
            "|www\\.[a-z0-9.-]+\\.[a-z]{2,5}" +
            "|\\b(?:pagalworld(?:\\.(?:com|in|pw|org|info|co))?|mr[\\-\\s]?jatt(?:\\.com)?|djpunjab(?:\\.(?:com|fm))?|songs\\.?pk|y2mate(?:\\.com)?|mp3pajji(?:\\.com)?|snaptube\\s*video|snaptube)\\b" +
            "|\\b(?:128|192|256|320)\\s*kbps\\b" +
            "|\\b(?:official\\s+(?:music\\s+)?video|official\\s+audio|lyrical\\s+video|full\\s+video|full\\s+song|audio\\s+song|4k\\s+video|1080p|720p)\\b" +
            ")"
    )

    private val TRAILING_LABEL_REGEX = Regex(
        "(?i)(?:\\s*[-|_•:]+\\s*|\\s+)(?:vevo|t[\\-\\s]?series|worldwide\\s+records(?:\\s+bhojpuri)?|snaptube\\s*video|snaptube|official\\s+video|official\\s+audio|lyrical\\s+video|full\\s+video|full\\s+song|audio\\s+song|4k\\s+video|hd\\s+video|hd|mp4|m4a)\\s*$"
    )

    private val EMPTY_BRACKETS_REGEX = Regex("\\(\\s*\\)|\\[\\s*\\]|\\{\\s*\\}")

    private val UNKNOWN_ARTIST_TOKENS = setOf(
        "", "<unknown>", "unknown", "unknown artist", "snaptube video", "snaptube",
        "pagalworld", "pagalworld.com", "mr-jatt", "djpunjab", "songspk", "songs.pk",
        "various", "audio", "music", "none", "null"
    )

    fun isPlaceholderArtist(artist: String?): Boolean {
        if (artist.isNullOrBlank()) return true
        val norm = artist.trim().lowercase(Locale.ROOT)
        if (norm in UNKNOWN_ARTIST_TOKENS) return true
        if (norm.contains("snaptube") || norm.contains("pagalworld") || norm.contains("y2mate") || norm.contains("www.")) {
            return true
        }
        return false
    }

    /**
     * Runs Stage 1 + Stage 2 Smart Name Cleaning on a track title (falling back to filename if title is junk/generic).
     */
    fun cleanTitle(rawTitle: String, filePath: String = "", applyTitleCase: Boolean = false): String {
        val rawFileName = if (filePath.isNotBlank()) {
            filePath.substringAfterLast('/').substringBeforeLast('.')
        } else ""

        var text = rawTitle.trim()

        val isGenericTitle = text.isBlank() ||
            text.equals("unknown", ignoreCase = true) ||
            text.equals("unknown track", ignoreCase = true) ||
            text.equals("<unknown>", ignoreCase = true) ||
            text.equals("audio", ignoreCase = true) ||
            text.matches(Regex("^(track|audio|sound|recording)[_\\s-]*\\d+$", RegexOption.IGNORE_CASE)) ||
            text.matches(Regex("^\\d+$"))

        val hasWebSpamInTitle = text.contains("www.", ignoreCase = true) ||
            text.contains("y2mate", ignoreCase = true) ||
            text.contains("pagalworld", ignoreCase = true) ||
            text.contains("songs.pk", ignoreCase = true) ||
            text.contains("snaptube", ignoreCase = true) ||
            text.contains("mp3pajji", ignoreCase = true)

        val filenameHasIndicScript = rawFileName.any { it.code in 0x0900..0x0D7F }
        val titleHasIndicScript = text.any { it.code in 0x0900..0x0D7F }

        if (rawFileName.isNotBlank() && (isGenericTitle || (hasWebSpamInTitle && !rawFileName.contains("pagalworld", ignoreCase = true)) || (filenameHasIndicScript && !titleHasIndicScript))) {
            text = rawFileName
        }

        text = runStage1AndStage2(text, applySmartCase = applyTitleCase)

        return if (text.isBlank()) {
            val fallback = (if (rawFileName.isNotBlank()) rawFileName else rawTitle)
                .replace(EXTENSION_REGEX, "")
                .replace('_', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            fallback.ifBlank { rawTitle.trim() }
        } else {
            text
        }
    }

    /**
     * Cleans artist metadata by removing download watermarks, label noise, and extra whitespace.
     */
    fun cleanArtist(rawArtist: String, applyTitleCase: Boolean = true): String {
        if (isPlaceholderArtist(rawArtist)) return ""
        val cleaned = runStage1AndStage2(rawArtist, applySmartCase = applyTitleCase)
        return if (isPlaceholderArtist(cleaned)) "" else cleaned
    }

    /**
     * Cleans album metadata by removing download watermarks, bitrate tags, and extensions.
     */
    fun cleanAlbum(rawAlbum: String, applyTitleCase: Boolean = true): String {
        val norm = rawAlbum.trim()
        if (norm.isBlank() || norm.equals("<unknown>", ignoreCase = true) || norm.equals("snaptube video", ignoreCase = true)) {
            return ""
        }
        return runStage1AndStage2(norm, applySmartCase = applyTitleCase)
    }

    /**
     * 1-tap full metadata cleaning across Title, Artist, and Album.
     */
    fun cleanAll(
        rawTitle: String,
        rawArtist: String,
        rawAlbum: String,
        filePath: String = ""
    ): CleanedTrackMetadata {
        val cleanedTitle = cleanTitle(rawTitle, filePath, applyTitleCase = true)
        var cleanedArtist = cleanArtist(rawArtist, applyTitleCase = true)
        val cleanedAlbum = cleanAlbum(rawAlbum, applyTitleCase = true)

        // If artist is missing/junk, try Stage 3 extraction from the title automatically
        if (cleanedArtist.isBlank()) {
            val split = splitTitleAndArtist(cleanedTitle, rawArtist, filePath)
            if (split.wasSplit) {
                return CleanedTrackMetadata(
                    title = split.cleanTitle,
                    artist = split.cleanArtist,
                    album = cleanedAlbum
                )
            }
        }

        if (cleanedArtist.isBlank()) {
            cleanedArtist = rawArtist.trim().takeUnless { isPlaceholderArtist(it) } ?: ""
        }

        return CleanedTrackMetadata(
            title = cleanedTitle,
            artist = cleanedArtist,
            album = cleanedAlbum
        )
    }

    /**
     * Stage 3 — Smart Artist/Title Extraction:
     * Detects common downloaded filename patterns like "Song Name - Singer Name",
     * "Singer Name - Song Name", or "Song Name | Singer Name", splitting them cleanly.
     */
    fun splitTitleAndArtist(
        rawTitle: String,
        existingArtist: String = "",
        filePath: String = ""
    ): SplitTitleArtistResult {
        val workingTitle = cleanTitle(rawTitle, filePath, applyTitleCase = false)
        val cleanedExistingArtist = cleanArtist(existingArtist, applyTitleCase = false)

        // Check standard delimiters in priority order: " - ", " | ", " – ", " — ", " _ "
        val separators = listOf(" - ", " | ", " – ", " — ", " : ")
        for (sep in separators) {
            if (workingTitle.contains(sep)) {
                val parts = workingTitle.split(sep).map { it.trim() }.filter { it.isNotBlank() }
                if (parts.size >= 2) {
                    val left = smartTitleCase(parts[0])
                    val right = smartTitleCase(parts.drop(1).joinToString(" - "))

                    // Heuristic: if left matches existing artist, left is artist and right is title
                    val leftIsExistingArtist = cleanedExistingArtist.isNotBlank() &&
                        (left.equals(cleanedExistingArtist, ignoreCase = true) ||
                            cleanedExistingArtist.contains(left, ignoreCase = true))

                    // Heuristic: if right contains common artist connectors (",", "&", "feat", "ft.")
                    // or looks shorter/name-like while left is longer, right is artist
                    val rightLooksLikeArtists = right.contains(",") ||
                        right.contains("&") ||
                        right.contains("feat", ignoreCase = true) ||
                        right.contains("ft.", ignoreCase = true)

                    val (finalTitle, finalArtist) = when {
                        leftIsExistingArtist -> right to left
                        rightLooksLikeArtists -> left to right
                        else -> left to right
                    }

                    val mergedArtist = if (cleanedExistingArtist.isBlank() || isPlaceholderArtist(existingArtist)) {
                        finalArtist
                    } else if (cleanedExistingArtist.contains(finalArtist, ignoreCase = true)) {
                        cleanedExistingArtist
                    } else {
                        finalArtist
                    }

                    return SplitTitleArtistResult(
                        cleanTitle = finalTitle,
                        cleanArtist = mergedArtist,
                        wasSplit = true
                    )
                }
            }
        }

        // Also handle single hyphen with non-empty sides if no spaces around it (e.g., "Kesariya-Arijit Singh")
        val compactDashParts = workingTitle.split(Regex("\\s*[-|–—]\\s*")).map { it.trim() }.filter { it.isNotBlank() }
        if (compactDashParts.size == 2 && compactDashParts[0].length >= 2 && compactDashParts[1].length >= 2) {
            return SplitTitleArtistResult(
                cleanTitle = smartTitleCase(compactDashParts[0]),
                cleanArtist = smartTitleCase(compactDashParts[1]),
                wasSplit = true
            )
        }

        return SplitTitleArtistResult(
            cleanTitle = smartTitleCase(workingTitle),
            cleanArtist = cleanedExistingArtist.ifBlank { existingArtist.trim() },
            wasSplit = false
        )
    }

    private fun runStage1AndStage2(input: String, applySmartCase: Boolean): String {
        var t = input.trim()
        if (t.isBlank()) return ""

        // Stage 1: Strip file extension
        t = t.replace(EXTENSION_REGEX, "")

        // Strip leading noise prefixes repeatedly ("Video - ", "VIDEO _ ", "Full Video _ ")
        var prev: String
        do {
            prev = t
            t = t.replace(LEADING_PREFIX_REGEX, "")
        } while (t != prev)

        // Strip bracketed noise tags and website watermarks
        t = t.replace(BRACKETED_JUNK_REGEX, " ")
        t = t.replace(INLINE_WATERMARK_REGEX, " ")

        // Strip trailing label/channel noise ("- T-Series", "| Worldwide Records", "- VEVO")
        do {
            prev = t
            t = t.replace(TRAILING_LABEL_REGEX, "")
        } while (t != prev)

        // Call recording date/time suffix cleaning
        val isRecording = t.startsWith("Call", ignoreCase = true) ||
            t.startsWith("REC", ignoreCase = true) ||
            t.startsWith("Record", ignoreCase = true)
        if (isRecording) {
            t = t.replace(Regex("(_|-)\\d{6,8}(_|-)\\d{4,6}$"), "")
            t = t.replace(Regex("(_|-)\\d{6,8}$"), "")
            t = t.replace(Regex("(_|-)\\d{4,}$"), "")
        }

        // Stage 2: Underscores, empty brackets, whitespace & dangling delimiters
        t = t.replace('_', ' ')
        t = t.replace(EMPTY_BRACKETS_REGEX, " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        t = t.trim('-', '|', ':', '_', '•', '–', '—', ' ')
        t = t.replace(Regex("\\s+"), " ").trim()

        if (applySmartCase && t.isNotBlank()) {
            t = smartTitleCase(t)
        }
        return t
    }

    /**
     * Applies intelligent Title Case to Latin words while preserving Devanagari / Hindi / Punjabi
     * Unicode characters and well-known acronyms untouched.
     */
    fun smartTitleCase(text: String): String {
        if (text.isBlank()) return text

        val latinChars = text.filter { it in 'a'..'z' || it in 'A'..'Z' }
        if (latinChars.isEmpty()) return text // Pure Devanagari / Indic / numeric — leave untouched

        val allUpper = latinChars.all { it.isUpperCase() }
        val allLower = latinChars.all { it.isLowerCase() }
        // Only force recasing per-word if all-caps, all-lowercase, or needs normalization
        val words = text.split(" ")
        return words.mapIndexed { index, rawWord ->
            if (rawWord.isEmpty()) return@mapIndexed rawWord
            // Preserve Devanagari / non-Latin words untouched
            if (rawWord.any { it.code in 0x0900..0x0D7F }) return@mapIndexed rawWord

            val strippedUpper = rawWord.trim('(', ')', '[', ']', ',', '.', '!', '?').uppercase(Locale.ROOT)
            if (strippedUpper in PRESERVED_ACRONYMS) {
                return@mapIndexed rawWord.uppercase(Locale.ROOT)
            }

            val lowerWord = rawWord.lowercase(Locale.ROOT)
            if (index > 0 && lowerWord in SMALL_LATIN_WORDS) {
                return@mapIndexed lowerWord
            }

            if (allUpper || allLower || rawWord.first().isLowerCase()) {
                // Capitalize first Latin letter (handles leading '(' like "(feat." or "(Remix)")
                val chars = lowerWord.toCharArray()
                for (i in chars.indices) {
                    if (chars[i].isLetter()) {
                        chars[i] = chars[i].uppercaseChar()
                        break
                    }
                }
                String(chars)
            } else {
                rawWord
            }
        }.joinToString(" ")
    }
}
