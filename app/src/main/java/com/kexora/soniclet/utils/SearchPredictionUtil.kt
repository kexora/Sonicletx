package com.kexora.soniclet.utils

import com.kexora.soniclet.database.Song
import com.kexora.soniclet.database.Playlist
import com.kexora.soniclet.database.FolderInfo
import com.kexora.soniclet.player.NavigationTab

object SearchPredictionUtil {

    /**
     * Gets a list of unique strings to suggest for the current page context.
     */
    fun getSuggestionCandidates(
        tab: NavigationTab,
        songs: List<Song>,
        playlists: List<Playlist>,
        folders: List<FolderInfo>
    ): List<String> {
        return when (tab) {
            NavigationTab.PLAYER -> {
                // Suggest titles and artists
                val titles = songs.map { it.title }.distinct()
                val artists = songs.flatMap { ArtistParserUtil.splitArtists(it.artist, it.title) }.distinct()
                (titles + artists).distinct()
            }
            NavigationTab.ARTISTS -> {
                // Suggest individual artists
                ArtistParserUtil.getIndividualArtistsMap(songs).keys.toList()
            }
            NavigationTab.ALBUMS -> {
                // Suggest album names
                songs.map { it.album }.filter { it.isNotBlank() }.distinct()
            }
            NavigationTab.PLAYLISTS -> {
                playlists.map { it.name }.distinct()
            }
            NavigationTab.FOLDERS -> {
                folders.map { it.name }.distinct()
            }
            NavigationTab.GENRES -> {
                songs.map { it.genre }.filter { it.isNotBlank() }.distinct()
            }
            else -> emptyList()
        }
    }

    /**
     * Filters candidates to find the most relevant suggestions based on query.
     */
    fun getPredictions(query: String, candidates: List<String>, maxResults: Int = 5): List<String> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase().trim()
        
        return candidates.asSequence()
            .filter { it.isNotBlank() }
            .map { it to calculateScore(q, it.lowercase().trim()) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(maxResults)
            .toList()
    }

    private fun calculateScore(query: String, candidate: String): Int {
        if (candidate.startsWith(query)) return 100
        if (candidate.contains(query)) return 80
        
        // Split candidate into tokens
        val tokens = candidate.split(Regex("[\\s_\\.-]+")).filter { it.isNotBlank() }
        if (tokens.any { it.startsWith(query) }) return 90
        
        // Fuzzy match check
        if (candidate.fuzzyMatch(query)) return 50
        
        return 0
    }
}
