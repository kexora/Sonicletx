package com.kexora.soniclet.utils

import com.kexora.soniclet.database.Song
import java.util.Locale

/**
 * Utility for safely detecting, parsing, splitting, and normalizing multiple artists across tracks.
 *
 * Uses a strict 4-step pipeline:
 * 1. Noise Stripping: removes trailing/bracketed non-artist tags (Official Video, Full Song, VEVO, SnapTube Video, etc.).
 * 2. Strict Word-Boundary Delimiters: splits ONLY on explicit punctuation or whole collaboration words
 *    surrounded by whitespace on BOTH sides (never slicing inside names like "Anirudh Ravichander").
 * 3. Length & Junk Guard: trims tokens, discards broken < 3 char fragments unless matching dot-separated
 *    stage initials (e.g. "A. R. Rahman"), and drops unknown placeholders when real artists exist.
 * 4. Case-Insensitive Merging: merges casing variants ("pawan singh" / "Pawan Singh") into one canonical entry.
 */
object ArtistParserUtil {

    // Step 1a: Bracketed/parenthesized non-artist noise tags, e.g. "(Official Video)", "[Full Song]", "(Lyrical Audio)"
    // Explicitly preserves collaboration tags like "(feat. Artist)" or "[ft. Artist]" or "(with Artist)".
    private val BRACKETED_NOISE_REGEX = Regex(
        "(?i)[\\(\\[](?!\\s*(?:feat\\.?|ft\\.?|featuring|with)\\b)[^\\)\\]]*(?:official|full\\s+song|video|audio|lyrical|lyrics|snaptube|vevo|records|4k|hd|mp3|320\\s*kbps|status|slowed|reverb|jukebox)[^\\)\\]]*[\\)\\]]"
    )

    // Step 1b: Trailing or standalone noise suffixes at the end of raw artist strings or tokens
    private val TRAILING_NOISE_REGEX = Regex(
        "(?i)(?:[\\s\\-–—|•]+(?:snaptube\\s+video|official\\s+video|official\\s+audio|music\\s+video|full\\s+song|lyrical\\s+video|lyrical|vevo|records|video|audio))+$"
    )

    // Attached VEVO suffix like "TaylorSwiftVEVO" or "TSeriesVEVO"
    private val ATTACHED_VEVO_REGEX = Regex("(?i)(?<=\\S)vevo$")

    // Step 2: Strict word-boundary delimiters ONLY:
    // - Explicit punctuation: , ; / | & × \
    // - Whole collaboration words surrounded by whitespace on BOTH sides: feat., ft., featuring, with, x, vs.
    // Never matches substrings inside words (e.g., "and" or "er" in "Anirudh Ravichander").
    private val DELIMITER_REGEX = Regex(
        "(?i)(?:\\s*[,;/|&×\\\\]\\s*)|(?:\\s+(?:feat\\.?|ft\\.?|featuring|with|x|vs\\.?)\\s+)"
    )

    // Extract featured artists from song titles like "Title (feat. Artist)" or "Title [ft. Artist]"
    private val TITLE_FEAT_REGEX = Regex(
        "(?i)[\\(\\[](?:feat\\.?|ft\\.?|featuring|with)\\s+([^\\]\\)]+)[\\)\\]]"
    )

    // Matches dot-separated stage initials like "A.R.", "A. R.", "A. R. Rahman", "K.J. Yesudas", "J. Balvin"
    private val DOT_INITIALS_REGEX = Regex(
        "^(?:[A-Za-z]\\.\\s*){2,}.*$"
    )

    // Standalone junk / placeholder tokens that are never real artists
    private val JUNK_TOKENS = setOf(
        "unknown",
        "<unknown>",
        "unknown artist",
        "various",
        "various artists",
        "none",
        "null",
        "n/a",
        "audio",
        "video",
        "lyrical",
        "records",
        "vevo",
        "snaptube",
        "snaptube video",
        "official video",
        "official audio",
        "full song"
    )

    private val MULTI_SPACE_REGEX = Regex("\\s+")

    /**
     * Step 1: Strips bracketed and trailing non-artist noise tags from a raw string.
     */
    fun stripNoiseTags(input: String): String {
        var cleaned = input.trim()
        if (cleaned.isEmpty()) return ""

        cleaned = BRACKETED_NOISE_REGEX.replace(cleaned, " ")
        cleaned = TRAILING_NOISE_REGEX.replace(cleaned, "")
        cleaned = ATTACHED_VEVO_REGEX.replace(cleaned, "")
        cleaned = MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
        return cleaned
    }

    /**
     * Splits a raw artist string (and optional song title) into a clean list of distinct individual artist names.
     */
    fun splitArtists(rawArtist: String?, songTitle: String? = null): List<String> {
        val tokens = mutableListOf<String>()

        if (!rawArtist.isNullOrBlank()) {
            val cleanedRaw = stripNoiseTags(rawArtist)
            if (cleanedRaw.isNotBlank()) {
                val rawParts = cleanedRaw.split(DELIMITER_REGEX)
                for (part in rawParts) {
                    val clean = sanitizeArtistToken(part)
                    if (isValidArtistToken(clean)) {
                        tokens.add(clean)
                    }
                }
            }
        }

        // Also extract featured artists from song title if present
        if (!songTitle.isNullOrBlank()) {
            val titleMatches = TITLE_FEAT_REGEX.findAll(songTitle)
            for (match in titleMatches) {
                val featContent = stripNoiseTags(match.groupValues.getOrNull(1) ?: continue)
                if (featContent.isBlank()) continue
                val featParts = featContent.split(DELIMITER_REGEX)
                for (fp in featParts) {
                    val clean = sanitizeArtistToken(fp)
                    if (isValidArtistToken(clean)) {
                        tokens.add(clean)
                    }
                }
            }
        }

        // Step 3: Ignore "<unknown>" / "Unknown Artist" whenever real named artists exist on this track
        val realTokens = tokens.filterNot { isUnknownPlaceholder(it) }
        val effectiveTokens = realTokens.ifEmpty { emptyList() }

        // Deduplicate case-insensitively while preserving order
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (token in effectiveTokens) {
            val lower = token.lowercase(Locale.ROOT)
            if (seen.add(lower)) {
                result.add(token)
            }
        }

        if (result.isNotEmpty()) return result

        // Safe fallback when track has no parseable real artist
        val fallback = rawArtist?.let { sanitizeArtistToken(stripNoiseTags(it)) }.orEmpty()
        return if (fallback.isBlank() || isUnknownPlaceholder(fallback)) {
            listOf("Unknown Artist")
        } else {
            listOf(fallback)
        }
    }

    /**
     * Determines whether the track or raw artist text has multiple distinct real artists.
     */
    fun hasMultipleArtists(rawArtist: String?, songTitle: String? = null): Boolean {
        if (rawArtist.isNullOrBlank() && songTitle.isNullOrBlank()) return false
        val list = splitArtists(rawArtist, songTitle).filterNot { isUnknownPlaceholder(it) }
        return list.size > 1
    }

    /**
     * Detects the primary delimiter used in a raw artist string.
     */
    fun detectPrimaryDelimiter(rawArtist: String): String {
        val lower = rawArtist.lowercase(Locale.ROOT)
        return when {
            lower.contains(" feat. ") -> " feat. "
            lower.contains(" ft. ") -> " ft. "
            lower.contains(" featuring ") -> " featuring "
            lower.contains(" & ") -> " & "
            lower.contains(" with ") -> " with "
            lower.contains(" x ") || lower.contains(" × ") -> " x "
            lower.contains(" vs. ") || lower.contains(" vs ") -> " vs. "
            lower.contains(" / ") -> " / "
            lower.contains(" | ") -> " | "
            lower.contains(";") -> "; "
            lower.contains(",") -> ", "
            else -> ", "
        }
    }

    /**
     * Combines multiple artist names into a formatted string using the given delimiter.
     */
    fun combineArtists(artists: List<String>, delimiter: String = ", "): String {
        return artists.filter { it.isNotBlank() }.joinToString(separator = delimiter)
    }

    /**
     * Cleans up leading/trailing punctuation, quotes, noise suffixes, or accidental brackets from an artist token.
     */
    private fun sanitizeArtistToken(token: String): String {
        var str = stripNoiseTags(token)
        var prev: String
        do {
            prev = str
            str = str.trim()
                .removePrefix("\"").removeSuffix("\"")
                .removePrefix("'").removeSuffix("'")
                .removePrefix("(").removeSuffix(")")
                .removePrefix("[").removeSuffix("]")
                .removePrefix("-").removeSuffix("-")
                .removePrefix("–").removeSuffix("–")
                .removePrefix("—").removeSuffix("—")
                .removePrefix("•").removeSuffix("•")
                .trim()
        } while (str != prev)
        return MULTI_SPACE_REGEX.replace(str, " ").trim()
    }

    private fun isUnknownPlaceholder(token: String): Boolean {
        val lower = token.trim().lowercase(Locale.ROOT)
        return lower.isEmpty() || lower == "<unknown>" || lower == "unknown" || lower == "unknown artist"
    }

    /**
     * Step 3: Length & Junk Guard.
     * Discards fragments shorter than 3 characters (e.g. "ER", "R", "S", "A") unless matching
     * dot-separated stage initials (e.g. "A. R. Rahman", "A.R.").
     */
    private fun isValidArtistToken(token: String): Boolean {
        if (token.isBlank()) return false
        val lower = token.lowercase(Locale.ROOT)
        if (lower in JUNK_TOKENS) return false
        if (DOT_INITIALS_REGEX.matches(token)) return true

        // Strip spaces/dots when checking substantive character length
        val compact = token.replace(" ", "")
        if (compact.length < 3) return false

        // Must contain at least one letter or digit (reject pure punctuation)
        return compact.any { it.isLetterOrDigit() }
    }

    /**
     * Picks the best canonical display name among case-insensitive variants
     * (e.g. prefers "Pawan Singh" over "pawan singh" or "PAWAN SINGH").
     */
    private fun chooseCanonicalName(variants: Map<String, Int>): String {
        return variants.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { casingQualityScore(it.key) }
                    .thenByDescending { it.value }
                    .thenBy { it.key }
            )
            .first()
            .key
    }

    private fun casingQualityScore(name: String): Int {
        val letters = name.filter { it.isLetter() }
        if (letters.isEmpty()) return 0
        val hasUpper = letters.any { it.isUpperCase() }
        val hasLower = letters.any { it.isLowerCase() }
        return when {
            hasUpper && hasLower && name.first().isUpperCase() -> 3 // Proper Title/Mixed Case ("Pawan Singh")
            hasUpper && hasLower -> 2
            hasUpper -> 1 // ALL CAPS ("PAWAN SINGH")
            else -> 0 // all lowercase ("pawan singh")
        }
    }

    /**
     * Step 4: Groups all library songs by individual parsed artists with case-insensitive merging.
     * "pawan singh" and "Pawan Singh" merge cleanly into a single entry preserving every song.
     */
    fun getIndividualArtistsMap(songs: List<Song>): Map<String, List<Song>> {
        val songsByKey = mutableMapOf<String, LinkedHashSet<Song>>()
        val variantCounts = mutableMapOf<String, MutableMap<String, Int>>()

        for (song in songs) {
            val artists = splitArtists(song.artist, song.title)
            for (artist in artists) {
                val key = artist.lowercase(Locale.ROOT)
                songsByKey.getOrPut(key) { LinkedHashSet() }.add(song)
                val counts = variantCounts.getOrPut(key) { mutableMapOf() }
                counts[artist] = (counts[artist] ?: 0) + 1
            }
        }

        val canonicalEntries = songsByKey.map { (key, songSet) ->
            val displayName = chooseCanonicalName(variantCounts[key] ?: mapOf(key to 1))
            displayName to songSet.toList()
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })

        return LinkedHashMap<String, List<Song>>(canonicalEntries.size).apply {
            for ((name, list) in canonicalEntries) {
                put(name, list)
            }
        }
    }

    /**
     * Resolves the canonical library display name for a parsed artist token using case-insensitive merging.
     */
    fun resolveCanonicalArtistName(artistName: String, allSongs: List<Song>): String {
        val targetLower = artistName.trim().lowercase(Locale.ROOT)
        val counts = mutableMapOf<String, Int>()
        for (song in allSongs) {
            for (parsed in splitArtists(song.artist, song.title)) {
                if (parsed.lowercase(Locale.ROOT) == targetLower) {
                    counts[parsed] = (counts[parsed] ?: 0) + 1
                }
            }
        }
        return if (counts.isNotEmpty()) chooseCanonicalName(counts) else artistName
    }

    /**
     * Groups songs that contain collaborations / multiple artists together (case-insensitively merged).
     */
    fun getCollaborationArtistsMap(songs: List<Song>): Map<String, List<Song>> {
        val songsByKey = mutableMapOf<String, LinkedHashSet<Song>>()
        val variantCounts = mutableMapOf<String, MutableMap<String, Int>>()

        for (song in songs) {
            if (hasMultipleArtists(song.artist, song.title)) {
                val cleanDisplay = stripNoiseTags(song.artist).ifBlank {
                    splitArtists(song.artist, song.title).joinToString(", ")
                }.ifBlank { "Unknown Collaboration" }
                val key = cleanDisplay.lowercase(Locale.ROOT)
                songsByKey.getOrPut(key) { LinkedHashSet() }.add(song)
                val counts = variantCounts.getOrPut(key) { mutableMapOf() }
                counts[cleanDisplay] = (counts[cleanDisplay] ?: 0) + 1
            }
        }

        val canonicalEntries = songsByKey.map { (key, songSet) ->
            val displayName = chooseCanonicalName(variantCounts[key] ?: mapOf(key to 1))
            displayName to songSet.toList()
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })

        return LinkedHashMap<String, List<Song>>(canonicalEntries.size).apply {
            for ((name, list) in canonicalEntries) {
                put(name, list)
            }
        }
    }

    /**
     * Groups songs by their cleaned combined artist string ("Together as released", case-insensitively merged).
     */
    fun getAllTogetherArtistsMap(songs: List<Song>): Map<String, List<Song>> {
        val songsByKey = mutableMapOf<String, LinkedHashSet<Song>>()
        val variantCounts = mutableMapOf<String, MutableMap<String, Int>>()

        for (song in songs) {
            val cleaned = stripNoiseTags(song.artist)
            val display = if (cleaned.isBlank() || isUnknownPlaceholder(cleaned)) {
                "Unknown Artist"
            } else {
                cleaned
            }
            val key = display.lowercase(Locale.ROOT)
            songsByKey.getOrPut(key) { LinkedHashSet() }.add(song)
            val counts = variantCounts.getOrPut(key) { mutableMapOf() }
            counts[display] = (counts[display] ?: 0) + 1
        }

        val canonicalEntries = songsByKey.map { (key, songSet) ->
            val displayName = chooseCanonicalName(variantCounts[key] ?: mapOf(key to 1))
            displayName to songSet.toList()
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })

        return LinkedHashMap<String, List<Song>>(canonicalEntries.size).apply {
            for ((name, list) in canonicalEntries) {
                put(name, list)
            }
        }
    }

    data class ArtistStats(
        val totalTracks: Int,
        val soloTracks: Int,
        val collabTracks: Int,
        val collaborators: List<String>
    )

    /**
     * Calculates statistics for a given individual artist among a list of songs.
     */
    fun getArtistStats(artistName: String, songs: List<Song>): ArtistStats {
        var solo = 0
        var collab = 0
        val collabByKey = mutableMapOf<String, String>()

        for (song in songs) {
            val artists = splitArtists(song.artist, song.title)
            if (artists.any { it.equals(artistName, ignoreCase = true) }) {
                if (artists.size == 1) {
                    solo++
                } else {
                    collab++
                    for (a in artists) {
                        if (!a.equals(artistName, ignoreCase = true) && !isUnknownPlaceholder(a)) {
                            val lower = a.lowercase(Locale.ROOT)
                            val existing = collabByKey[lower]
                            if (existing == null || casingQualityScore(a) > casingQualityScore(existing)) {
                                collabByKey[lower] = a
                            }
                        }
                    }
                }
            }
        }

        return ArtistStats(
            totalTracks = solo + collab,
            soloTracks = solo,
            collabTracks = collab,
            collaborators = collabByKey.values.sortedWith(String.CASE_INSENSITIVE_ORDER)
        )
    }
}
