package com.kexora.soniclet.library

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import com.kexora.soniclet.database.DatabaseProvider
import com.kexora.soniclet.database.Song
import com.kexora.soniclet.player.*
import com.kexora.soniclet.utils.DuplicateDetectorEngine
import com.kexora.soniclet.utils.DuplicateGroup
import com.kexora.soniclet.utils.fuzzyMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayList

/**
 * Library loading, MediaStore auto-scan observer, folders, playlists, search, sorting,
 * favorites, multi-select actions, and duplicate detection for [MusicViewModel].
 */
internal class LibraryViewModel(
    private val vm: MusicViewModel,
    private val scope: CoroutineScope
) {
    private var mediaStoreObserver: ContentObserver? = null
    private var autoScanDebounceJob: Job? = null
    private var lastHomeCacheKey: HomeRecommendationCacheKey? = null
    private var homeCacheJob: Job? = null
    private var hasBackfilledLegacyWrapped: Boolean = false

    private fun computeLibrarySignature(
        songs: List<Song>,
        excludedSongIds: Set<Long>,
        excludedFolders: Set<String>,
        restoredAutoSongIds: Set<Long>
    ): Int {
        var hash = songs.size * 31
        for (song in songs) {
            hash = 31 * hash + song.id.hashCode()
        }
        hash = 31 * hash + excludedSongIds.hashCode()
        hash = 31 * hash + excludedFolders.hashCode()
        hash = 31 * hash + restoredAutoSongIds.hashCode()
        return hash
    }

    fun setHomeCategory(category: String) {
        if (vm._uiState.value.selectedHomeCategory == category) return
        vm._uiState.update { it.copy(selectedHomeCategory = category) }
        recomputeHomeRecommendationsIfNeeded(force = false)
    }

    fun refreshHomeRecommendations() {
        vm._uiState.update { it.copy(homeManualRefreshCounter = it.homeManualRefreshCounter + 1) }
        recomputeHomeRecommendationsIfNeeded(force = true)
    }

    fun recomputeHomeRecommendationsIfNeeded(force: Boolean = false) {
        val state = vm._uiState.value
        val sourceSongs = state.songList
        val todayKey = HomeRecommendationEngine.todayDateKey()
        val signature = computeLibrarySignature(
            songs = sourceSongs,
            excludedSongIds = state.excludedSongIds,
            excludedFolders = state.excludedFolders,
            restoredAutoSongIds = state.restoredAutoSongIds
        )
        val cacheKey = HomeRecommendationCacheKey(
            calendarDate = todayKey,
            selectedCategory = state.selectedHomeCategory,
            librarySignature = signature,
            manualRefreshCounter = state.homeManualRefreshCounter
        )

        if (!force && cacheKey == lastHomeCacheKey && state.homeEligibleSongs.isNotEmpty()) {
            return
        }
        lastHomeCacheKey = cacheKey

        homeCacheJob?.cancel()
        homeCacheJob = scope.launch(Dispatchers.Default) {
            val result = HomeRecommendationEngine.generate(
                allSongs = sourceSongs,
                excludedSongIds = state.excludedSongIds,
                excludedFolders = state.excludedFolders,
                restoredAutoSongIds = state.restoredAutoSongIds,
                selectedCategory = cacheKey.selectedCategory,
                favoriteSongIds = vm.audioRepository.favoriteSongIds.value,
                playCounts = vm.audioRepository.playCounts.value,
                playDurations = vm.audioRepository.playDurations.value,
                completionRatios = vm.audioRepository.completionRatios.value,
                rapidSkipCounts = vm.audioRepository.rapidSkipCounts.value,
                songMoods = vm.audioRepository.songMoods.value,
                calendarDate = cacheKey.calendarDate,
                manualRefreshCounter = cacheKey.manualRefreshCounter
            )
            withContext(Dispatchers.Main) {
                if (lastHomeCacheKey == cacheKey) {
                    vm._uiState.update {
                        it.copy(
                            homeEligibleSongs = result.eligibleSongs,
                            homeForYouSongs = result.forYouSongs,
                            homeMixSongs = result.mixSongs,
                            homeTopSingers = result.topSingers
                        )
                    }
                }
            }
        }
    }

    fun registerMediaStoreObserver() {
        if (mediaStoreObserver != null) return
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    triggerAutoScanDebounced()
                }
            }

            val externalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            vm.context.contentResolver.registerContentObserver(externalUri, true, observer)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val volumeExternalUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    if (volumeExternalUri != externalUri) {
                        vm.context.contentResolver.registerContentObserver(volumeExternalUri, true, observer)
                    }
                } catch (_: Exception) {}
            }

            mediaStoreObserver = observer
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregisterMediaStoreObserver() {
        try {
            autoScanDebounceJob?.cancel()
            autoScanDebounceJob = null
            mediaStoreObserver?.let {
                vm.context.contentResolver.unregisterContentObserver(it)
            }
            mediaStoreObserver = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkForNewSongsAndNotify(latestSongs: List<Song>) {
        val currentSongs = vm._uiState.value.allSongsList.ifEmpty { vm._uiState.value.songList }
        if (currentSongs.isEmpty()) return

        val previousIds = currentSongs.map { it.id }.toSet()
        val newlyAddedSongs = latestSongs.filter { !previousIds.contains(it.id) }

        if (newlyAddedSongs.isNotEmpty()) {
            val count = newlyAddedSongs.size
            val msg = if (count == 1) {
                "🎵 1 New Song Added: ${newlyAddedSongs.first().title}"
            } else {
                "🎵 $count New Songs Added to Library!"
            }
            vm.showNotificationBanner(msg)
        }
    }

    fun onAppResumeCheckNewSongs() {
        scope.launch(Dispatchers.IO) {
            try {
                val currentSongs = vm._uiState.value.allSongsList.ifEmpty { vm._uiState.value.songList }
                val (hasChanged, latestSongs) = vm.audioRepository.syncMediaStoreIfChanged(currentSongs)
                if (hasChanged) {
                    withContext(Dispatchers.Main) {
                        checkForNewSongsAndNotify(latestSongs)
                        updateLoadedSongsState(latestSongs)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerAutoScanDebounced(delayMs: Long = 1500L) {
        autoScanDebounceJob?.cancel()
        autoScanDebounceJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            try {
                val currentSongs = vm._uiState.value.allSongsList.ifEmpty { vm._uiState.value.songList }
                val (hasChanged, latestSongs) = vm.audioRepository.syncMediaStoreIfChanged(currentSongs)
                if (hasChanged) {
                    withContext(Dispatchers.Main) {
                        checkForNewSongsAndNotify(latestSongs)
                        updateLoadedSongsState(latestSongs)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isSongInExcludedFolder(song: Song, excludedFolders: Set<String>): Boolean {
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

    suspend fun updateLoadedSongsState(allSongs: List<Song>) {
        // Automatically hide call recordings, voice notes, WhatsApp/Telegram audio, and clips < 45s (unless user restored them)
        vm.audioRepository.syncAutoHiddenItems(allSongs)

        val folders = vm.audioRepository.getAllDiscoveredFolders()
        val includeHidden = vm._uiState.value.includeSystemHiddenInLibrary
        val combinedSongs = if (includeHidden) {
            try {
                val db = DatabaseProvider.getDatabase(vm.context)
                val hiddenAudios = db.systemHiddenDao().getAllAudiosSync().map { audio ->
                    val folderName = audio.folderPath.substringAfterLast('/').ifBlank { "Hidden" }
                    val isCall = audio.filePath.contains("call", ignoreCase = true) || audio.folderPath.contains("call", ignoreCase = true)
                    val isVoice = audio.filePath.contains("voice", ignoreCase = true) || audio.folderPath.contains("voice", ignoreCase = true)
                    Song(
                        id = audio.filePath.hashCode().toLong(),
                        title = audio.title,
                        artist = folderName,
                        album = if (isCall) "Call Recordings" else if (isVoice) "Voice Memos" else "Hidden Audio",
                        duration = audio.durationMs,
                        contentUri = Uri.fromFile(File(audio.filePath)),
                        albumArtUri = null,
                        folderPath = audio.folderPath,
                        folderName = folderName,
                        filePath = audio.filePath,
                        dateAdded = audio.lastModifiedMs / 1000
                    )
                }
                (allSongs + hiddenAudios).distinctBy { it.filePath.ifBlank { it.id.toString() } }
            } catch (_: Exception) {
                allSongs
            }
        } else {
            allSongs
        }

        // Automatically reconcile moved/renamed audio file IDs and prune deleted dead IDs
        val remappedIds = if (combinedSongs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                vm.audioRepository.reconcileMovedOrDeletedSongs(combinedSongs)
            }
        } else {
            emptyMap()
        }

        val currentExcludedFolders = vm.audioRepository.excludedFolderPaths.value
        val currentExcludedSongs = vm.audioRepository.excludedSongIds.value
        val currentRestoredSongs = vm.audioRepository.restoredAutoSongIds.value

        vm._uiState.update { currentState ->
            val visibleSongs = combinedSongs.filter { song ->
                if (currentExcludedSongs.contains(song.id)) return@filter false
                if (currentRestoredSongs.contains(song.id)) return@filter true
                if (currentState.includeSystemHiddenInLibrary) {
                    val cleanFolder = song.folderPath.trimEnd('/')
                    val cleanFile = song.filePath.trimEnd('/')
                    val isUserExplicitlyExcluded = currentExcludedFolders.any { excludedPath ->
                        val cleanEx = excludedPath.trimEnd('/')
                        cleanFolder == cleanEx || cleanFile == cleanEx
                    }
                    !isUserExplicitlyExcluded
                } else {
                    !isSongInExcludedFolder(song, currentExcludedFolders)
                }
            }
            val visibleById = visibleSongs.associateBy { it.id }
            val combinedById = combinedSongs.associateBy { it.id }
            val newFiltered = filterAndSortSongs(visibleSongs, currentState.searchQuery, currentState.sortOption, currentState.sortAscending)
            val updatedQueue = if (currentState.activeQueue.isEmpty()) visibleSongs else {
                currentState.activeQueue.mapNotNull { qSong ->
                    val resolvedId = remappedIds[qSong.id] ?: qSong.id
                    visibleById[resolvedId]
                }.distinctBy { it.id }
            }

            val updatedCurrentSong = currentState.currentSong?.let { curr ->
                val resolvedId = remappedIds[curr.id] ?: curr.id
                combinedById[resolvedId] ?: curr
            }

            currentState.copy(
                allSongsList = combinedSongs,
                songList = visibleSongs,
                filteredSongList = newFiltered,
                activeQueue = updatedQueue,
                currentSong = updatedCurrentSong,
                discoveredFolders = folders,
                excludedFolders = currentExcludedFolders,
                excludedSongIds = currentExcludedSongs,
                restoredAutoSongIds = currentRestoredSongs,
                playlists = vm.audioRepository.playlists.value,
                favoriteSongIds = vm.audioRepository.favoriteSongIds.value,
                recentlyPlayedSongIds = vm.audioRepository.recentlyPlayedSongIds.value,
                playCounts = vm.audioRepository.playCounts.value,
                playDurations = vm.audioRepository.playDurations.value,
                lastPlayedTimestamps = vm.audioRepository.lastPlayedTimestamps.value,
                songMoods = vm.audioRepository.songMoods.value,
                savedQueues = vm.audioRepository.savedQueues.value,
                isLoading = false,
                errorMessage = null
            )
        }

        // Refresh session-locked 5-layer Home recommendation cache only when librarySignature/date/category changes
        recomputeHomeRecommendationsIfNeeded(force = false)

        // Keep permanent Room Wrapped history synced with moved file IDs (never deleting records of deleted files)
        if (combinedSongs.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    if (!hasBackfilledLegacyWrapped) {
                        hasBackfilledLegacyWrapped = true
                        vm.listeningHistoryRepository.backfillFromLegacyStats(
                            songs = combinedSongs,
                            playDurations = vm.audioRepository.playDurations.value,
                            playCounts = vm.audioRepository.playCounts.value,
                            lastPlayedTimestamps = vm.audioRepository.lastPlayedTimestamps.value
                        )
                    }
                    vm.listeningHistoryRepository.syncMovedFileIds(combinedSongs)
                } catch (_: Exception) {}
            }
        }
    }

    fun loadAudioFiles(forceSync: Boolean = false) {
        if (mediaStoreObserver == null) {
            registerMediaStoreObserver()
        }
        scope.launch {
            if (forceSync) {
                vm._uiState.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val allSongs = vm.audioRepository.syncMediaStore()
                    updateLoadedSongsState(allSongs)
                } catch (e: Exception) {
                    vm._uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load music files: ${e.message}"
                        )
                    }
                }
            } else {
                val cachedSongs = try {
                    vm.audioRepository.fetchAudioFiles()
                } catch (_: Exception) {
                    emptyList()
                }

                if (cachedSongs.isNotEmpty()) {
                    updateLoadedSongsState(cachedSongs)
                } else {
                    vm._uiState.update { it.copy(isLoading = true, errorMessage = null) }
                }

                launch(Dispatchers.IO) {
                    try {
                        val (hasChanged, latestSongs) = vm.audioRepository.syncMediaStoreIfChanged(cachedSongs)
                        if (hasChanged || cachedSongs.isEmpty()) {
                            val newAddedCount = if (cachedSongs.isNotEmpty()) (latestSongs.size - cachedSongs.size).coerceAtLeast(0) else 0
                            withContext(Dispatchers.Main) {
                                updateLoadedSongsState(latestSongs)
                                if (newAddedCount > 0) {
                                    try {
                                        val msg = if (newAddedCount == 1) "1 new song added to library!" else "$newAddedCount new songs added to library!"
                                        vm.showNotificationBanner(msg)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (cachedSongs.isEmpty()) {
                            vm._uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Failed to load music files: ${e.message}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        vm._uiState.update { currentState ->
            val filtered = filterAndSortSongs(currentState.songList, query, currentState.sortOption, currentState.sortAscending)
            currentState.copy(
                searchQuery = query,
                filteredSongList = filtered
            )
        }
    }

    fun addRecentSearch(query: String) = vm.audioRepository.addRecentSearch(query)
    fun removeRecentSearch(query: String) = vm.audioRepository.removeRecentSearch(query)
    fun clearRecentSearches() = vm.audioRepository.clearRecentSearches()

    fun setSortOption(option: SortOption) {
        vm._uiState.update { currentState ->
            val ascending = if (currentState.sortOption == option) !currentState.sortAscending else true
            currentState.copy(
                sortOption = option,
                sortAscending = ascending,
                filteredSongList = filterAndSortSongs(
                    currentState.songList,
                    currentState.searchQuery,
                    option,
                    ascending,
                    currentState.formatFilter,
                    currentState.durationCategoryFilter,
                    currentState.attributeFilter,
                    currentState.favoriteSongIds
                )
            )
        }
    }

    fun toggleSortDirection() {
        vm._uiState.update { currentState ->
            val ascending = !currentState.sortAscending
            currentState.copy(
                sortAscending = ascending,
                filteredSongList = filterAndSortSongs(
                    currentState.songList,
                    currentState.searchQuery,
                    currentState.sortOption,
                    ascending,
                    currentState.formatFilter,
                    currentState.durationCategoryFilter,
                    currentState.attributeFilter,
                    currentState.favoriteSongIds
                )
            )
        }
    }

    fun setFormatFilter(format: String) {
        vm._uiState.update { currentState ->
            currentState.copy(
                formatFilter = format,
                filteredSongList = filterAndSortSongs(
                    currentState.songList,
                    currentState.searchQuery,
                    currentState.sortOption,
                    currentState.sortAscending,
                    format,
                    currentState.durationCategoryFilter,
                    currentState.attributeFilter,
                    currentState.favoriteSongIds
                )
            )
        }
    }

    fun setDurationCategoryFilter(category: String) {
        vm._uiState.update { currentState ->
            currentState.copy(
                durationCategoryFilter = category,
                filteredSongList = filterAndSortSongs(
                    currentState.songList,
                    currentState.searchQuery,
                    currentState.sortOption,
                    currentState.sortAscending,
                    currentState.formatFilter,
                    category,
                    currentState.attributeFilter,
                    currentState.favoriteSongIds
                )
            )
        }
    }

    fun setAttributeFilter(attribute: String) {
        vm._uiState.update { currentState ->
            currentState.copy(
                attributeFilter = attribute,
                filteredSongList = filterAndSortSongs(
                    currentState.songList,
                    currentState.searchQuery,
                    currentState.sortOption,
                    currentState.sortAscending,
                    currentState.formatFilter,
                    currentState.durationCategoryFilter,
                    attribute,
                    currentState.favoriteSongIds
                )
            )
        }
    }

    fun resetAllSongFilters() {
        vm._uiState.update { currentState ->
            currentState.copy(
                formatFilter = "ALL",
                durationCategoryFilter = "ALL",
                attributeFilter = "ALL",
                sortOption = SortOption.TIME_ADDED,
                sortAscending = false,
                filteredSongList = filterAndSortSongs(
                    currentState.songList,
                    currentState.searchQuery,
                    SortOption.TIME_ADDED,
                    false,
                    "ALL",
                    "ALL",
                    "ALL",
                    currentState.favoriteSongIds
                )
            )
        }
    }

    fun applySongFiltersAndSort(
        sortOption: SortOption,
        sortAscending: Boolean,
        formatFilter: String,
        durationCategoryFilter: String,
        attributeFilter: String,
        isGridMode: Boolean? = null
    ) {
        vm._uiState.update { currentState ->
            currentState.copy(
                sortOption = sortOption,
                sortAscending = sortAscending,
                formatFilter = formatFilter,
                durationCategoryFilter = durationCategoryFilter,
                attributeFilter = attributeFilter,
                isGridMode = isGridMode ?: currentState.isGridMode,
                filteredSongList = filterAndSortSongs(
                    currentState.songList,
                    currentState.searchQuery,
                    sortOption,
                    sortAscending,
                    formatFilter,
                    durationCategoryFilter,
                    attributeFilter,
                    currentState.favoriteSongIds
                )
            )
        }
    }

    fun filterAndSortSongs(
        songs: List<Song>,
        query: String,
        sortOption: SortOption,
        sortAscending: Boolean,
        formatFilter: String = vm._uiState.value.formatFilter,
        durationCategoryFilter: String = vm._uiState.value.durationCategoryFilter,
        attributeFilter: String = vm._uiState.value.attributeFilter,
        favoriteSongIds: Set<Long> = vm._uiState.value.favoriteSongIds
    ): List<Song> {
        var filtered = if (query.isBlank()) songs else {
            songs.filter { song ->
                song.title.fuzzyMatch(query) ||
                    song.artist.fuzzyMatch(query) ||
                    song.album.fuzzyMatch(query) ||
                    song.folderName.fuzzyMatch(query)
            }
        }

        if (formatFilter != "ALL") {
            val ext = formatFilter.lowercase()
            filtered = filtered.filter { song ->
                song.filePath.endsWith(".$ext", ignoreCase = true) || song.title.endsWith(".$ext", ignoreCase = true)
            }
        }

        filtered = when (durationCategoryFilter) {
            "SHORT" -> filtered.filter { it.duration in 1..60000 }
            "MEDIUM" -> filtered.filter { it.duration in 60001..300000 }
            "LONG" -> filtered.filter { it.duration > 300000 }
            else -> filtered
        }

        filtered = when (attributeFilter) {
            "FAVORITES" -> filtered.filter { favoriteSongIds.contains(it.id) }
            "RECENT" -> {
                val thirtyDaysAgoSec = (System.currentTimeMillis() / 1000) - (30L * 24 * 3600)
                filtered.filter { it.dateAdded >= thirtyDaysAgoSec }
            }
            "LOSSLESS" -> filtered.filter {
                it.filePath.endsWith(".flac", true) ||
                    it.filePath.endsWith(".wav", true) ||
                    it.filePath.endsWith(".alac", true) ||
                    it.filePath.endsWith(".aiff", true)
            }
            else -> filtered
        }

        var sorted = when (sortOption) {
            SortOption.SONG_NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOption.ARTIST_NAME -> filtered.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM_NAME -> filtered.sortedBy { it.album.lowercase() }
            SortOption.TIME_ADDED -> filtered.sortedBy { it.dateAdded }
            SortOption.DURATION -> filtered.sortedBy { it.duration }
            SortOption.FOLDER_NAME -> filtered.sortedBy { it.folderName.lowercase() }
            SortOption.YEAR -> filtered.sortedBy { it.year }
            SortOption.FILE_SIZE -> filtered.sortedBy {
                try { File(it.filePath).length() } catch (_: Exception) { 0L }
            }
            SortOption.RANDOM -> filtered.shuffled()
        }

        if (!sortAscending && sortOption != SortOption.RANDOM) {
            sorted = sorted.reversed()
        }
        return sorted
    }

    fun toggleGridMode() {
        vm._uiState.update { it.copy(isGridMode = !it.isGridMode) }
    }

    fun addManualFolder(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                vm.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                vm.audioRepository.addManualFolder(uri.toString())
                loadAudioFiles(forceSync = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeManualFolder(uriString: String) {
        scope.launch(Dispatchers.IO) {
            vm.audioRepository.removeManualFolder(uriString)
            loadAudioFiles(forceSync = true)
        }
    }

    fun setIncludeSystemHiddenInLibrary(enabled: Boolean) {
        vm.audioRepository.setIncludeSystemHiddenInLibrary(enabled)
    }

    fun excludeFolder(folderPath: String) {
        vm.audioRepository.excludeDirectory(folderPath)
    }

    fun excludeJunkFolders() {
        val junkKeywords = listOf("WhatsApp", "Telegram", "Voice Recorder", "Call Recordings", "Voice Notes", "Audiobooks", "Podcasts", "Game", "Sound", "sfx")
        vm._uiState.value.discoveredFolders.forEach { folder ->
            val pathLower = folder.path.lowercase()
            if (junkKeywords.any { pathLower.contains(it.lowercase()) }) {
                vm.audioRepository.excludeDirectory(folder.path)
            }
        }
    }

    fun restoreFolder(folderPath: String) {
        val cleanTarget = folderPath.trimEnd('/')
        vm.audioRepository.restoreDirectory(folderPath)
        vm._uiState.value.allSongsList.forEach { song ->
            val cleanFolder = song.folderPath.trimEnd('/')
            if (cleanFolder == cleanTarget || cleanFolder.startsWith("$cleanTarget/")) {
                vm.audioRepository.restoreSong(song.id)
            }
        }
    }

    fun restoreAllExcludedFolders() {
        val excluded = vm._uiState.value.excludedFolders
        vm.audioRepository.restoreAllExcludedDirectories()
        vm._uiState.value.allSongsList.forEach { song ->
            if (isSongInExcludedFolder(song, excluded)) {
                vm.audioRepository.restoreSong(song.id)
            }
        }
    }

    fun excludeSong(songId: Long) = vm.audioRepository.excludeSong(songId)
    fun restoreSong(songId: Long) = vm.audioRepository.restoreSong(songId)

    fun restoreAllExcludedSongs() {
        vm._uiState.value.allSongsList.forEach { song ->
            if (vm._uiState.value.excludedSongIds.contains(song.id) ||
                isSongInExcludedFolder(song, vm._uiState.value.excludedFolders)
            ) {
                vm.audioRepository.restoreSong(song.id)
            }
        }
        vm.audioRepository.restoreAllExcludedSongs()
    }

    fun setShowExcludedFoldersDialog(show: Boolean) {
        vm._uiState.update { it.copy(showExcludedFoldersDialog = show) }
    }

    fun toggleFavorite(songId: Long) {
        val currentFavs = vm._uiState.value.favoriteSongIds
        val isAdding = !currentFavs.contains(songId)
        val updatedFavs = if (isAdding) currentFavs + songId else currentFavs - songId
        if (isAdding) {
            val targetSong = vm._uiState.value.allSongsList.find { it.id == songId }
                ?: vm._uiState.value.songList.find { it.id == songId }
                ?: vm._uiState.value.currentSong?.takeIf { it.id == songId }
            if (targetSong != null) {
                vm.audioRepository.recordTrackFingerprint(targetSong)
            }
        }
        vm._uiState.update { it.copy(favoriteSongIds = updatedFavs) }
        vm.audioRepository.toggleFavorite(songId)
        vm.updateNotification()
    }

    fun setSongMood(songId: Long, mood: String?) = vm.audioRepository.setSongMood(songId, mood)

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) {
        if (initialSongs.isNotEmpty()) {
            vm.audioRepository.recordTrackFingerprints(initialSongs)
        }
        vm.audioRepository.createPlaylist(name, initialSongs.map { it.id })
    }

    fun openOrCreateDynamicPlaylist(name: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        vm.audioRepository.recordTrackFingerprints(songs)
        val playlist = vm.audioRepository.upsertPlaylistByName(name, songs.map { it.id })
        vm._uiState.update {
            it.copy(
                playlists = vm.audioRepository.playlists.value,
                selectedPlaylistId = playlist.id,
                selectedSmartPlaylistType = null
            )
        }
        vm.selectTab(NavigationTab.PLAYLIST_DETAILS)
    }

    fun renamePlaylist(playlistId: String, newName: String) = vm.audioRepository.renamePlaylist(playlistId, newName)
    fun deletePlaylist(playlistId: String) = vm.audioRepository.deletePlaylist(playlistId)
    fun addSongsToPlaylist(playlistId: String, songs: List<Song>) {
        if (songs.isNotEmpty()) {
            vm.audioRepository.recordTrackFingerprints(songs)
        }
        vm.audioRepository.addSongsToPlaylist(playlistId, songs.map { it.id })
    }
    fun removeSongFromPlaylist(playlistId: String, songId: Long) = vm.audioRepository.removeSongFromPlaylist(playlistId, songId)
    fun updatePlaylistCover(playlistId: String, coverUri: String?) = vm.audioRepository.updatePlaylistCover(playlistId, coverUri)
    fun updatePlaylistGradient(playlistId: String, gradientIndex: Int) = vm.audioRepository.updatePlaylistGradient(playlistId, gradientIndex)
    fun reorderPlaylistSongs(playlistId: String, newSongIds: List<Long>) = vm.audioRepository.reorderPlaylistSongs(playlistId, newSongIds)

    fun openPlaylistDetails(playlistId: String) {
        vm._uiState.update { it.copy(selectedPlaylistId = playlistId, selectedSmartPlaylistType = null) }
        vm.selectTab(NavigationTab.PLAYLIST_DETAILS)
    }

    fun openSmartPlaylistDetails(smartType: String) {
        vm._uiState.update { it.copy(selectedSmartPlaylistType = smartType, selectedPlaylistId = null) }
        vm.selectTab(NavigationTab.PLAYLIST_DETAILS)
    }

    fun openTrimDialog(song: Song) {
        vm._uiState.update { it.copy(songToTrim = song) }
    }

    fun closeTrimDialog() {
        vm._uiState.update { it.copy(songToTrim = null) }
    }

    fun openSongDetails(song: Song) {
        vm._uiState.update { it.copy(selectedSongForDetails = song) }
        vm.selectTab(NavigationTab.SONG_DETAILS)
    }

    fun openCategory(name: String, type: String, songs: List<Song>) {
        vm._uiState.update { it.copy(selectedCategoryName = name, selectedCategoryType = type, selectedCategorySongs = songs) }
        vm.selectTab(NavigationTab.CATEGORY_DETAILS)
    }

    fun openCategoryAndClosePlayer(name: String, type: String, songs: List<Song>) {
        vm._uiState.update { it.copy(selectedCategoryName = name, selectedCategoryType = type, selectedCategorySongs = songs) }
        vm.removeTabFromBackStack(NavigationTab.NOW_PLAYING)
        vm.selectTab(NavigationTab.CATEGORY_DETAILS)
    }

    fun closeCategory() {
        vm._uiState.update { it.copy(selectedCategoryName = null, selectedCategoryType = null, selectedCategorySongs = emptyList()) }
    }

    suspend fun updateSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String,
        newAlbumArtUri: Uri? = null,
        newAlbumArtist: String? = null,
        newTrackNumber: String? = null,
        newComposer: String? = null,
        newComment: String? = null,
        newLyrics: String? = null
    ): Boolean {
        val writeSuccess = vm.audioRepository.updateSongMetadata(
            songId = songId,
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newYear = newYear,
            newGenre = newGenre,
            newAlbumArtUri = newAlbumArtUri,
            newAlbumArtist = newAlbumArtist,
            newTrackNumber = newTrackNumber,
            newComposer = newComposer,
            newComment = newComment,
            newLyrics = newLyrics
        )
        val updatedSongs = vm.audioRepository.fetchAudioFiles()
        val refreshedTarget = updatedSongs.find { it.id == songId }
        vm._uiState.update { currentState ->
            val visibleSongs = updatedSongs.filter {
                !isSongInExcludedFolder(it, currentState.excludedFolders) && !currentState.excludedSongIds.contains(it.id)
            }
            val newFiltered = filterAndSortSongs(visibleSongs, currentState.searchQuery, currentState.sortOption, currentState.sortAscending)
            val updatedCurrentSong = if (currentState.currentSong?.id == songId) {
                refreshedTarget ?: visibleSongs.find { it.id == songId } ?: currentState.currentSong
            } else {
                currentState.currentSong
            }
            val updatedSelectedSong = if (currentState.selectedSongForDetails?.id == songId) {
                refreshedTarget ?: visibleSongs.find { it.id == songId } ?: currentState.selectedSongForDetails
            } else {
                currentState.selectedSongForDetails
            }
            val updatedQueue = if (refreshedTarget != null) {
                currentState.activeQueue.map { qSong -> if (qSong.id == songId) refreshedTarget else qSong }
            } else {
                currentState.activeQueue
            }
            currentState.copy(
                allSongsList = updatedSongs,
                songList = visibleSongs,
                filteredSongList = newFiltered,
                activeQueue = updatedQueue,
                currentSong = updatedCurrentSong,
                selectedSongForDetails = updatedSelectedSong
            )
        }
        refreshedTarget?.let { updatedSong ->
            vm.audioRepository.recordTrackFingerprint(updatedSong)
            if (vm._uiState.value.currentSong?.id == songId) {
                if (newLyrics != null) {
                    vm.loadLyricsForSong(updatedSong)
                }
                vm.updateNotification()
            }
        }
        return writeSuccess
    }

    fun setMinDurationFilter(durationMs: Long) = vm.audioRepository.setMinDurationFilter(durationMs)

    fun analyzeDuplicateGroups(): List<DuplicateGroup> {
        return DuplicateDetectorEngine.analyzeDuplicates(vm._uiState.value.allSongsList)
    }

    fun findDuplicateSongs(): List<List<Song>> {
        return analyzeDuplicateGroups().map { group -> group.tracks.map { it.song } }
    }

    suspend fun deleteSongPhysical(song: Song): Boolean {
        val deleted = vm.audioRepository.deleteSongPhysical(song)
        if (deleted) {
            loadAudioFiles(forceSync = true)
        }
        return deleted
    }

    fun deleteSong(song: Song, onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val deleted = deleteSongPhysical(song)
            onResult?.invoke(deleted)
        }
    }

    suspend fun moveSongPhysical(song: Song, targetFolderPath: String): Boolean {
        // Save fingerprint before move so MediaStore ID change is healed automatically after rescan
        vm.audioRepository.recordTrackFingerprint(song)
        val moved = vm.audioRepository.moveSongPhysical(song, targetFolderPath)
        if (moved) {
            loadAudioFiles(forceSync = true)
        }
        return moved
    }

    suspend fun copySongPhysical(song: Song, targetFolderPath: String): Boolean {
        val copied = vm.audioRepository.copySongPhysical(song, targetFolderPath)
        if (copied) {
            loadAudioFiles(forceSync = true)
        }
        return copied
    }

    fun createFolder(parentPath: String, folderName: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val result = vm.audioRepository.createNewFolder(parentPath, folderName)
            if (result.isSuccess) {
                loadAudioFiles(forceSync = true)
                onResult(true, "Folder created")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to create folder")
            }
        }
    }

    fun renameFolder(oldPath: String, newName: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val result = vm.audioRepository.renameFolderPhysical(oldPath, newName)
            if (result.isSuccess) {
                loadAudioFiles(forceSync = true)
                onResult(true, "Folder renamed")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to rename folder")
            }
        }
    }

    fun deleteFolder(folderPath: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            val success = vm.audioRepository.deleteFolderPhysical(folderPath)
            loadAudioFiles(forceSync = true)
            onResult(success)
        }
    }

    fun moveSongsBatch(songs: List<Song>, targetFolderPath: String, onComplete: (Int, Int) -> Unit) {
        scope.launch {
            // Save fingerprints before batch move so MediaStore ID changes heal automatically after rescan
            vm.audioRepository.recordTrackFingerprints(songs)
            val (success, failed) = vm.audioRepository.moveSongsBatch(songs, targetFolderPath)
            loadAudioFiles(forceSync = true)
            onComplete(success, failed)
        }
    }

    fun copySongsBatch(songs: List<Song>, targetFolderPath: String, onComplete: (Int, Int) -> Unit) {
        scope.launch {
            val (success, failed) = vm.audioRepository.copySongsBatch(songs, targetFolderPath)
            loadAudioFiles(forceSync = true)
            onComplete(success, failed)
        }
    }

    fun rescanStorage() {
        scope.launch {
            loadAudioFiles(forceSync = true)
        }
    }

    fun setShowCreatePlaylistDialog(show: Boolean) {
        vm._uiState.update { it.copy(showCreatePlaylistDialog = show) }
    }

    fun toggleSongSelection(songId: Long) {
        vm._uiState.update { state ->
            val current = state.selectedSongIds
            val updated = if (current.contains(songId)) current - songId else current + songId
            state.copy(selectedSongIds = updated)
        }
    }

    fun selectAllSongs(songIds: Collection<Long>) {
        vm._uiState.update { state ->
            state.copy(selectedSongIds = state.selectedSongIds + songIds)
        }
    }

    fun clearSelectedSongs() {
        vm._uiState.update { state ->
            state.copy(selectedSongIds = emptySet())
        }
    }

    fun getSelectedSongs(): List<Song> {
        val selectedIds = vm._uiState.value.selectedSongIds
        val allMap = vm._uiState.value.songList.associateBy { it.id }
        return selectedIds.mapNotNull { allMap[it] }
    }

    fun playSelectedSongs() {
        val selectedSongs = getSelectedSongs()
        if (selectedSongs.isNotEmpty()) {
            vm.playSong(selectedSongs.first(), songsContext = selectedSongs)
            clearSelectedSongs()
        }
    }

    fun insertSelectedSongsNext() {
        val selectedSongs = getSelectedSongs().reversed()
        for (s in selectedSongs) {
            vm.insertNext(s)
        }
        clearSelectedSongs()
    }

    fun addSelectedSongsToQueue() {
        val selectedSongs = getSelectedSongs()
        for (s in selectedSongs) {
            vm.addToQueue(s)
        }
        clearSelectedSongs()
    }

    fun addSelectedSongsToPlaylist(playlistId: String) {
        val selectedSongs = getSelectedSongs()
        if (selectedSongs.isNotEmpty()) {
            addSongsToPlaylist(playlistId, selectedSongs)
            clearSelectedSongs()
        }
    }

    fun toggleFavoriteSelectedSongs() {
        val selectedIds = vm._uiState.value.selectedSongIds
        for (id in selectedIds) {
            toggleFavorite(id)
        }
        clearSelectedSongs()
    }

    fun excludeSelectedSongs() {
        val selectedIds = vm._uiState.value.selectedSongIds
        for (id in selectedIds) {
            excludeSong(id)
        }
        clearSelectedSongs()
    }

    fun removeSelectedSongsFromPlaylist(playlistId: String) {
        val selectedIds = vm._uiState.value.selectedSongIds
        for (id in selectedIds) {
            removeSongFromPlaylist(playlistId, id)
        }
        clearSelectedSongs()
    }

    fun removeSelectedSongsFromQueue() {
        val selectedIds = vm._uiState.value.selectedSongIds
        if (selectedIds.isEmpty()) return
        vm._uiState.update { state ->
            val updated = state.activeQueue.filter { it.id !in selectedIds }
            state.copy(activeQueue = updated, selectedSongIds = emptySet())
        }
    }

    fun shareSong(context: Context, song: Song) {
        try {
            val uri = song.contentUri
            if (uri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share \"${song.title}\""))
            } else {
                Toast.makeText(context, "Cannot share track: URI unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareSongs(context: Context, songs: List<Song>) {
        if (songs.isEmpty()) return
        try {
            val uris = songs.mapNotNull { it.contentUri }
            if (uris.size == 1) {
                val singleSong = songs.firstOrNull()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    if (singleSong != null) {
                        putExtra(Intent.EXTRA_TEXT, "${singleSong.title} - ${singleSong.artist}")
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share track"))
            } else if (uris.size > 1) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${uris.size} tracks"))
            } else {
                Toast.makeText(context, "No shareable audio files found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareSelectedSongs(context: Context) {
        val selectedSongs = getSelectedSongs()
        if (selectedSongs.isEmpty()) return
        shareSongs(context, selectedSongs)
    }

    fun deleteSelectedSongs(onComplete: ((Int) -> Unit)? = null) {
        val selectedSongs = getSelectedSongs()
        if (selectedSongs.isEmpty()) return
        scope.launch {
            var count = 0
            for (song in selectedSongs) {
                try {
                    val success = vm.audioRepository.deleteSongPhysical(song)
                    if (success) count++
                } catch (_: Exception) {}
            }
            if (count > 0) {
                loadAudioFiles(forceSync = true)
            }
            clearSelectedSongs()
            onComplete?.invoke(count)
        }
    }
}
