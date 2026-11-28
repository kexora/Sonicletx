package com.kexora.soniclet.library

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.kexora.soniclet.database.*
import com.kexora.soniclet.settings.DiscoveredAudioFile
import com.kexora.soniclet.settings.DiscoveredHiddenFolder
import com.kexora.soniclet.settings.HiddenFolderType
import com.kexora.soniclet.ui.components.common.cleanSongTitle
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local hidden audio vault, Storage Access Framework (SAF) manual folder scanning,
 * and MediaStore-backed file/folder organization.
 */
internal class VaultRepository(
    private val context: Context,
    private val songDao: SongDao,
    private val prefs: SharedPreferences
) {
    fun scanDocumentFileRecursively(
        doc: DocumentFile,
        songsList: MutableList<Song>,
        existingSignatures: MutableSet<String>,
        excluded: Set<String>,
        minDuration: Long
    ) {
        doc.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDocumentFileRecursively(file, songsList, existingSignatures, excluded, minDuration)
            } else {
                val mime = file.type ?: ""
                val name = file.name ?: ""
                val isAudioExt = name.endsWith(".mp3", true) ||
                    name.endsWith(".m4a", true) ||
                    name.endsWith(".flac", true) ||
                    name.endsWith(".wav", true) ||
                    name.endsWith(".ogg", true) ||
                    name.endsWith(".opus", true) ||
                    name.endsWith(".aac", true) ||
                    name.endsWith(".wma", true) ||
                    name.endsWith(".mka", true) ||
                    name.endsWith(".aiff", true) ||
                    name.endsWith(".aif", true) ||
                    name.endsWith(".alac", true) ||
                    name.endsWith(".amr", true) ||
                    name.endsWith(".mid", true) ||
                    name.endsWith(".midi", true)
                if (mime.startsWith("audio/") || isAudioExt) {
                    val folderPath = doc.uri.toString()
                    val isFolderExcluded = excluded.contains(folderPath) || excluded.any { folderPath.startsWith("$it/") }
                    if (isFolderExcluded) return@forEach

                    val mmr = MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(context, file.uri)
                        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val duration = durationStr?.toLongOrNull() ?: 0L
                        if (duration < minDuration) return@forEach

                        val rawTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name.substringBeforeLast(".")
                        val title = cleanSongTitle(rawTitle, name)
                        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                        val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                        val folderName = doc.name ?: "Unknown Folder"

                        val signature = "${title.trim().lowercase()}_${artist.trim().lowercase()}_${duration / 1000}"
                        if (existingSignatures.contains(signature)) return@forEach

                        val id = file.uri.toString().hashCode().toLong()

                        songsList.add(
                            Song(
                                id = id,
                                title = title,
                                artist = artist.ifEmpty { "Unknown Artist" },
                                album = album.ifEmpty { "Unknown Album" },
                                duration = duration,
                                contentUri = file.uri,
                                albumArtUri = null,
                                folderPath = folderPath,
                                folderName = folderName,
                                dateAdded = file.lastModified() / 1000,
                                filePath = ""
                            )
                        )
                        existingSignatures.add(signature)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        try { mmr.release() } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun addTrackedFolder(folderPath: String) {
        val customFolders = (prefs.getStringSet("tracked_custom_folders", emptySet()) ?: emptySet()).toMutableSet()
        customFolders.add(folderPath)
        prefs.edit().putStringSet("tracked_custom_folders", customFolders).apply()
    }

    suspend fun deleteSongPhysical(song: Song): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        try {
            val file = File(song.filePath)
            if (file.exists()) {
                deleted = file.delete()
            }
            if (!deleted) {
                val deletedRows = context.contentResolver.delete(song.contentUri, null, null)
                deleted = deletedRows > 0
            }
            if (deleted) {
                songDao.deleteSongById(song.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        deleted
    }

    suspend fun moveSongPhysical(song: Song, targetFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(song.filePath)
            val targetDir = File(targetFolderPath)
            if (!targetDir.exists()) targetDir.mkdirs()

            val targetFile = File(targetDir, sourceFile.name)
            if (targetFile.exists()) return@withContext false

            var moved = false
            try {
                moved = sourceFile.renameTo(targetFile)
            } catch (_: Exception) {}

            if (!moved) {
                sourceFile.copyTo(targetFile, overwrite = false)
                moved = sourceFile.delete()
                if (!moved) {
                    context.contentResolver.delete(song.contentUri, null, null)
                    moved = true
                }
            }

            if (moved) {
                context.contentResolver.delete(song.contentUri, null, null)
                songDao.deleteSongById(song.id)
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("audio/*"), null)
            }
            moved
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun copySongPhysical(song: Song, targetFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(song.filePath)
            val targetDir = File(targetFolderPath)
            if (!targetDir.exists()) targetDir.mkdirs()

            val targetFile = File(targetDir, sourceFile.name)
            if (targetFile.exists()) return@withContext false

            sourceFile.copyTo(targetFile, overwrite = false)
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("audio/*"), null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveSongsBatch(songs: List<Song>, targetFolderPath: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        songs.forEach { song ->
            val ok = moveSongPhysical(song, targetFolderPath)
            if (ok) success++ else failed++
        }
        addTrackedFolder(targetFolderPath)
        Pair(success, failed)
    }

    suspend fun copySongsBatch(songs: List<Song>, targetFolderPath: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        songs.forEach { song ->
            val ok = copySongPhysical(song, targetFolderPath)
            if (ok) success++ else failed++
        }
        addTrackedFolder(targetFolderPath)
        Pair(success, failed)
    }

    suspend fun createNewFolder(parentPath: String, folderName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanName = folderName.trim()
            if (cleanName.isBlank() || cleanName.contains("/") || cleanName.contains("\\")) {
                return@withContext Result.failure(IllegalArgumentException("Invalid folder name"))
            }
            val parentDir = if (parentPath.isNotBlank()) {
                File(parentPath)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            val newDir = File(parentDir, cleanName)
            if (newDir.exists()) {
                return@withContext Result.failure(IllegalStateException("Folder '$cleanName' already exists"))
            }
            val created = newDir.mkdirs()
            if (!created && !newDir.exists()) {
                return@withContext Result.failure(Exception("Failed to create folder on storage. Check permissions."))
            }
            addTrackedFolder(newDir.absolutePath)
            Result.success(newDir.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun renameFolderPhysical(
        oldPath: String,
        newName: String,
        currentExcluded: Set<String>,
        onUpdateExcluded: (Set<String>) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val oldDir = File(oldPath)
            if (!oldDir.exists() || !oldDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Folder does not exist on disk"))
            }
            val parent = oldDir.parentFile ?: return@withContext Result.failure(IllegalArgumentException("Cannot rename root directory"))
            val cleanName = newName.trim()
            if (cleanName.isBlank() || cleanName.contains("/") || cleanName.contains("\\")) {
                return@withContext Result.failure(IllegalArgumentException("Invalid folder name"))
            }
            val newDir = File(parent, cleanName)
            if (newDir.exists()) {
                return@withContext Result.failure(IllegalStateException("Folder '$cleanName' already exists"))
            }
            val renamed = oldDir.renameTo(newDir)
            if (!renamed) {
                return@withContext Result.failure(Exception("Failed to rename folder. Ensure storage permissions are granted."))
            }

            val newPath = newDir.absolutePath

            val customFolders = (prefs.getStringSet("tracked_custom_folders", emptySet()) ?: emptySet()).toMutableSet()
            if (customFolders.remove(oldPath)) {
                customFolders.add(newPath)
                prefs.edit().putStringSet("tracked_custom_folders", customFolders).apply()
            }

            val excluded = currentExcluded.toMutableSet()
            if (excluded.remove(oldPath)) {
                excluded.add(newPath)
                onUpdateExcluded(excluded)
            }

            val allSongs = songDao.getAllSongsSync()
            val songsToUpdate = allSongs.filter { it.folderPath == oldPath || it.filePath.startsWith(oldPath) }
            if (songsToUpdate.isNotEmpty()) {
                val updatedSongs = songsToUpdate.map { song ->
                    val updatedFilePath = if (song.filePath.startsWith(oldPath)) {
                        song.filePath.replaceFirst(oldPath, newPath)
                    } else song.filePath
                    val updatedFolderPath = if (song.folderPath == oldPath) newPath else song.folderPath.replaceFirst(oldPath, newPath)
                    val updatedFolderName = if (song.folderPath == oldPath) cleanName else song.folderName
                    song.copy(
                        filePath = updatedFilePath,
                        folderPath = updatedFolderPath,
                        folderName = updatedFolderName
                    )
                }
                songDao.insertSongs(updatedSongs)
                val scanPaths = updatedSongs.map { it.filePath }.toTypedArray()
                MediaScannerConnection.scanFile(context, scanPaths, arrayOf("audio/*"), null)
            }

            Result.success(newPath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteFolderPhysical(
        folderPath: String,
        currentExcluded: Set<String>,
        onUpdateExcluded: (Set<String>) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(folderPath)
            val allSongs = songDao.getAllSongsSync()
            val folderSongs = allSongs.filter { it.folderPath == folderPath || it.filePath.startsWith(folderPath) }
            folderSongs.forEach { song ->
                songDao.deleteSongById(song.id)
                try {
                    context.contentResolver.delete(song.contentUri, null, null)
                } catch (_: Exception) {}
            }

            var deleted = false
            if (dir.exists()) {
                deleted = dir.deleteRecursively()
            }

            val customFolders = (prefs.getStringSet("tracked_custom_folders", emptySet()) ?: emptySet()).toMutableSet()
            customFolders.remove(folderPath)
            prefs.edit().putStringSet("tracked_custom_folders", customFolders).apply()

            val excluded = currentExcluded.toMutableSet()
            if (excluded.remove(folderPath)) {
                onUpdateExcluded(excluded)
            }

            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

sealed class DeleteFileOutcome {
    data object Deleted : DeleteFileOutcome()
    data class RequiresUserConsent(
        val intentSender: IntentSender,
        val filePath: String,
        val folderPath: String
    ) : DeleteFileOutcome()
    data class Failed(val reason: String) : DeleteFileOutcome()
}

@Singleton
class SystemHiddenRepository @Inject constructor(
    private val dao: SystemHiddenDao
) {
    companion object {
        private val CALL_KEYWORD_REGEX = Regex(
            """(?:\bcall\b|callrec|call_rec|call\s*record|phone_record|phone\s*record|miui/sound_recorder/call_rec|\bacr\b|\bcube\b|cubecall)""",
            RegexOption.IGNORE_CASE
        )

        // Matches phone-number patterns like +919876543210, +1-555-234-5678, or 10–13 digit subscriber numbers,
        // while avoiding standard YYYYMMDD / YYYYMMDD_HHMMSS date-time recording stamps.
        private val PHONE_NUMBER_REGEX = Regex("""(?:\+\d[\d\s\-()]{7,15}\d|(?<!\d)(?!20\d{6})\d{10,13}(?!\d))""")

        private val VOICE_NOTE_KEYWORDS = listOf(
            "whatsapp audio",
            "whatsapp voice",
            "voice notes",
            "voice_note",
            "voicenote",
            "voice recorder",
            "voicerecorder",
            "ptt-",
            "telegram",
            "opus",
            "memo",
            "sound_recorder"
        )

        fun classifyFolder(
            folderPath: String,
            folderName: String,
            audioFiles: List<DiscoveredAudioFile>,
            hasNoMedia: Boolean,
            isDotPrefixed: Boolean
        ): HiddenFolderType {
            val normalizedPath = folderPath.replace('\\', '/').lowercase(Locale.ROOT)
            val normalizedName = folderName.lowercase(Locale.ROOT)

            // 1. Call Recordings check (folder path/name or contained file names)
            if (CALL_KEYWORD_REGEX.containsMatchIn(normalizedPath) ||
                CALL_KEYWORD_REGEX.containsMatchIn(normalizedName)
            ) {
                return HiddenFolderType.CALL_RECORDING
            }

            val hasCallFileMatch = audioFiles.any { audio ->
                val fn = audio.file.name.lowercase(Locale.ROOT)
                CALL_KEYWORD_REGEX.containsMatchIn(fn) || PHONE_NUMBER_REGEX.containsMatchIn(audio.file.nameWithoutExtension)
            }
            if (hasCallFileMatch) {
                return HiddenFolderType.CALL_RECORDING
            }

            // 2. Voice Notes check (folder path/name or contained file names/extensions)
            if (VOICE_NOTE_KEYWORDS.any { normalizedPath.contains(it) || normalizedName.contains(it) }) {
                return HiddenFolderType.VOICE_NOTE
            }

            val hasVoiceFileMatch = audioFiles.any { audio ->
                val fn = audio.file.name.lowercase(Locale.ROOT)
                audio.extension.equals("opus", ignoreCase = true) ||
                    VOICE_NOTE_KEYWORDS.any { fn.contains(it) }
            }
            if (hasVoiceFileMatch) {
                return HiddenFolderType.VOICE_NOTE
            }

            // 3. Hidden Folders (.nomedia, dot-prefixed, or generic recordings)
            return HiddenFolderType.HIDDEN
        }
    }

    val cachedHiddenFoldersFlow: Flow<List<DiscoveredHiddenFolder>> = dao.getAllFoldersFlow().map { folderEntities ->
        folderEntities.mapNotNull { entity ->
            val audios = dao.getAudiosForFolderSync(entity.path)
                .map { audioEntity ->
                    DiscoveredAudioFile(
                        file = File(audioEntity.filePath),
                        title = audioEntity.title,
                        durationMs = audioEntity.durationMs,
                        sizeBytes = audioEntity.sizeBytes,
                        extension = audioEntity.extension
                    )
                }
                .filter { it.file.exists() }

            if (audios.isEmpty()) return@mapNotNull null

            val smartType = classifyFolder(
                folderPath = entity.path,
                folderName = entity.name,
                audioFiles = audios,
                hasNoMedia = entity.hasNoMedia,
                isDotPrefixed = entity.isDotPrefixed
            )
            DiscoveredHiddenFolder(
                path = entity.path,
                name = entity.name,
                type = smartType,
                hasNoMedia = entity.hasNoMedia,
                isDotPrefixed = entity.isDotPrefixed,
                audioCount = audios.size,
                totalSizeBytes = audios.sumOf { it.sizeBytes },
                audioFiles = audios
            )
        }
    }

    suspend fun syncSystemHiddenFolders(context: Context, forceFullScan: Boolean = false): List<DiscoveredHiddenFolder> = withContext(Dispatchers.IO) {
        val existingFolders = dao.getFoldersSync()
        val now = System.currentTimeMillis()

        if (!forceFullScan && existingFolders.isNotEmpty()) {
            var isDirty = false
            for (f in existingFolders) {
                val dir = File(f.path)
                if (!dir.exists() || dir.lastModified() > f.lastScannedMs) {
                    isDirty = true
                    break
                }
            }
            if (!isDirty) {
                return@withContext existingFolders.mapNotNull { entity ->
                    val audios = dao.getAudiosForFolderSync(entity.path)
                        .map { audioEntity ->
                            DiscoveredAudioFile(
                                file = File(audioEntity.filePath),
                                title = audioEntity.title,
                                durationMs = audioEntity.durationMs,
                                sizeBytes = audioEntity.sizeBytes,
                                extension = audioEntity.extension
                            )
                        }
                        .filter { it.file.exists() }
                    if (audios.isEmpty()) return@mapNotNull null
                    val smartType = classifyFolder(
                        folderPath = entity.path,
                        folderName = entity.name,
                        audioFiles = audios,
                        hasNoMedia = entity.hasNoMedia,
                        isDotPrefixed = entity.isDotPrefixed
                    )
                    DiscoveredHiddenFolder(
                        path = entity.path,
                        name = entity.name,
                        type = smartType,
                        hasNoMedia = entity.hasNoMedia,
                        isDotPrefixed = entity.isDotPrefixed,
                        audioCount = audios.size,
                        totalSizeBytes = audios.sumOf { it.sizeBytes },
                        audioFiles = audios
                    )
                }
            }
        }

        val freshlyScanned = scanSystemStorage()
        val folderEntities = mutableListOf<SystemHiddenFolderEntity>()
        val audioEntities = mutableListOf<SystemHiddenAudioEntity>()

        freshlyScanned.forEach { folder ->
            folderEntities.add(
                SystemHiddenFolderEntity(
                    path = folder.path,
                    name = folder.name,
                    type = folder.type.name,
                    hasNoMedia = folder.hasNoMedia,
                    isDotPrefixed = folder.isDotPrefixed,
                    audioCount = folder.audioCount,
                    totalSizeBytes = folder.totalSizeBytes,
                    lastScannedMs = now
                )
            )
            folder.audioFiles.forEach { audio ->
                audioEntities.add(
                    SystemHiddenAudioEntity(
                        filePath = audio.file.absolutePath,
                        folderPath = folder.path,
                        title = audio.title,
                        durationMs = audio.durationMs,
                        sizeBytes = audio.sizeBytes,
                        extension = audio.extension,
                        lastModifiedMs = audio.file.lastModified()
                    )
                )
            }
        }

        dao.replaceAll(folderEntities, audioEntities)
        freshlyScanned
    }

    private fun scanSystemStorage(): List<DiscoveredHiddenFolder> {
        val discovered = mutableListOf<DiscoveredHiddenFolder>()
        val visitedPaths = mutableSetOf<String>()
        val supportedExtensions = setOf("mp3", "m4a", "wav", "aac", "ogg", "opus", "amr", "flac", "3gp", "wma")
        val storageRoot = Environment.getExternalStorageDirectory()

        val potentialTargetDirs = listOf(
            File(storageRoot, "Recordings"),
            File(storageRoot, "Recordings/Call"),
            File(storageRoot, "Recordings/Call recordings"),
            File(storageRoot, "Recordings/Voice"),
            File(storageRoot, "Music/Recordings"),
            File(storageRoot, "Music/Recordings/Call Recordings"),
            File(storageRoot, "CallRecordings"),
            File(storageRoot, "call_recordings"),
            File(storageRoot, "ACRCalls"),
            File(storageRoot, "CubeCallRecorder/All"),
            File(storageRoot, "MIUI/sound_recorder"),
            File(storageRoot, "MIUI/sound_recorder/call_rec"),
            File(storageRoot, "Samsung/Voice Recorder"),
            File(storageRoot, "VoiceRecorder"),
            File(storageRoot, "SoundRecorder"),
            File(storageRoot, "Sounds"),
            File(storageRoot, "Sounds/CallRecord"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Audio"),
            File(storageRoot, "WhatsApp/Media/WhatsApp Voice Notes"),
            File(storageRoot, "Telegram/Telegram Audio")
        )

        potentialTargetDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory && !visitedPaths.contains(dir.absolutePath)) {
                val folderObj = inspectFolderForAudio(dir, supportedExtensions)
                if (folderObj != null && folderObj.audioCount > 0) {
                    discovered.add(folderObj)
                    visitedPaths.add(dir.absolutePath)
                }
            }
        }

        fun crawlDirectory(dir: File, depth: Int) {
            if (depth > 5) return
            val children = dir.listFiles() ?: return
            var hasNoMedia = false
            val subdirs = mutableListOf<File>()

            for (child in children) {
                if (child.name == ".nomedia") {
                    hasNoMedia = true
                } else if (child.isDirectory) {
                    val abs = child.absolutePath
                    if (!abs.contains("/Android/data") && !abs.contains("/Android/obb")) {
                        subdirs.add(child)
                    }
                }
            }

            val isDotPrefixed = dir.name.startsWith(".") && dir != storageRoot
            val lowerName = dir.name.lowercase(Locale.ROOT)
            val isTarget = hasNoMedia ||
                isDotPrefixed ||
                lowerName.contains("record") ||
                lowerName.contains("voice") ||
                lowerName.contains("call") ||
                lowerName.contains("memo") ||
                lowerName.contains("whatsapp") ||
                lowerName.contains("telegram") ||
                lowerName.contains("acr") ||
                lowerName.contains("cube")

            if (isTarget && !visitedPaths.contains(dir.absolutePath)) {
                val folderObj = inspectFolderForAudio(dir, supportedExtensions)
                if (folderObj != null && folderObj.audioCount > 0) {
                    discovered.add(folderObj)
                    visitedPaths.add(dir.absolutePath)
                }
            }

            for (sub in subdirs) {
                crawlDirectory(sub, depth + 1)
            }
        }

        try {
            if (storageRoot.exists() && storageRoot.isDirectory) {
                crawlDirectory(storageRoot, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return discovered.sortedByDescending { it.audioCount }
    }

    private fun inspectFolderForAudio(dir: File, supportedExtensions: Set<String>): DiscoveredHiddenFolder? {
        val files = dir.listFiles() ?: return null
        val audioList = mutableListOf<DiscoveredAudioFile>()
        var totalSize = 0L
        var hasNoMedia = false

        for (file in files) {
            if (file.name == ".nomedia") {
                hasNoMedia = true
                continue
            }
            if (file.isFile) {
                val ext = file.extension.lowercase(Locale.ROOT)
                if (supportedExtensions.contains(ext)) {
                    totalSize += file.length()
                    audioList.add(
                        DiscoveredAudioFile(
                            file = file,
                            title = file.nameWithoutExtension,
                            durationMs = estimateDuration(file),
                            sizeBytes = file.length(),
                            extension = ext
                        )
                    )
                }
            }
        }

        if (audioList.isEmpty()) return null

        val isDotPrefixed = dir.name.startsWith(".")
        val type = classifyFolder(
            folderPath = dir.absolutePath,
            folderName = dir.name,
            audioFiles = audioList,
            hasNoMedia = hasNoMedia,
            isDotPrefixed = isDotPrefixed
        )

        return DiscoveredHiddenFolder(
            path = dir.absolutePath,
            name = dir.name,
            type = type,
            hasNoMedia = hasNoMedia,
            isDotPrefixed = isDotPrefixed,
            audioCount = audioList.size,
            totalSizeBytes = totalSize,
            audioFiles = audioList
        )
    }

    private fun estimateDuration(file: File): Long {
        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                durationMs = durStr.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            durationMs = (file.length() / 16000L).coerceIn(1000L, 3600000L)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        return durationMs
    }

    suspend fun removeNoMedia(folderPath: String): Boolean = withContext(Dispatchers.IO) {
        val noMediaFile = File(folderPath, ".nomedia")
        if (noMediaFile.exists() && noMediaFile.delete()) {
            dao.deleteFolderByPath(folderPath)
            dao.deleteAudiosInFolder(folderPath)
            return@withContext true
        }
        return@withContext false
    }

    /**
     * Unhides a hidden/recording folder so its audio files appear in Soniclet's main library.
     * - Removes `.nomedia` if present & writable and rescans files into MediaStore.
     * - If `.nomedia` is protected by Android Scoped Storage, safely copies audio files into
     *   `Music/Soniclet Restored/<folderName>` via MediaStore so they appear immediately.
     * - If there is no `.nomedia` (e.g., auto-hidden recording folder), scans files into MediaStore
     *   so whitelisting via `restoreFolder` surfaces them immediately.
     */
    suspend fun unhideFolderToLibrary(
        context: Context,
        folder: DiscoveredHiddenFolder
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val noMediaFile = File(folder.path, ".nomedia")
            if (noMediaFile.exists()) {
                if (noMediaFile.delete()) {
                    val paths = folder.audioFiles.map { it.file.absolutePath }.toTypedArray()
                    if (paths.isNotEmpty()) {
                        MediaScannerConnection.scanFile(context, paths, null, null)
                    }
                    dao.deleteFolderByPath(folder.path)
                    dao.deleteAudiosInFolder(folder.path)
                    val count = folder.audioFiles.size
                    val unit = if (count == 1) "file" else "files"
                    return@withContext true to "Unhid folder • $count $unit added to your library"
                }

                // Scoped Storage blocked deleting `.nomedia` -> export copies to Music/Soniclet Restored
                val safeFolderName = folder.name.replace(Regex("""[^\w\s\-]"""), "").ifBlank { "Restored" }
                var copiedCount = 0
                val scannedPaths = mutableListOf<String>()

                for (audio in folder.audioFiles) {
                    if (!audio.file.exists() || !audio.file.canRead()) continue
                    val mime = mimeTypeForExtension(audio.extension)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, audio.file.name)
                            put(MediaStore.Audio.Media.TITLE, audio.title)
                            put(MediaStore.Audio.Media.MIME_TYPE, mime)
                            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Soniclet Restored/$safeFolderName")
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        }
                        val insertedUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        if (insertedUri != null) {
                            try {
                                context.contentResolver.openOutputStream(insertedUri)?.use { out ->
                                    audio.file.inputStream().use { input -> input.copyTo(out) }
                                }
                                values.clear()
                                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                                context.contentResolver.update(insertedUri, values, null, null)
                                copiedCount++
                            } catch (_: Exception) {
                                context.contentResolver.delete(insertedUri, null, null)
                            }
                        }
                    } else {
                        val musicDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            "Soniclet Restored/$safeFolderName"
                        )
                        if (!musicDir.exists()) musicDir.mkdirs()
                        val target = File(musicDir, audio.file.name)
                        if (!target.exists()) {
                            audio.file.copyTo(target, overwrite = false)
                        }
                        scannedPaths.add(target.absolutePath)
                        copiedCount++
                    }
                }
                if (scannedPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, scannedPaths.toTypedArray(), null, null)
                }
                return@withContext if (copiedCount > 0) {
                    val unit = if (copiedCount == 1) "file" else "files"
                    true to "Added $copiedCount $unit to your Music library"
                } else {
                    false to "Could not add files from this protected folder"
                }
            } else {
                // Non-.nomedia folder (e.g., Recordings) -> scan files and remove from hidden vault cache
                val paths = folder.audioFiles.map { it.file.absolutePath }.toTypedArray()
                if (paths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(context, paths, null, null)
                }
                dao.deleteFolderByPath(folder.path)
                dao.deleteAudiosInFolder(folder.path)
                val count = folder.audioFiles.size
                val unit = if (count == 1) "file" else "files"
                return@withContext true to "Added $count $unit to your library"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false to "Could not add folder to library"
        }
    }

    /**
     * Permanently deletes a discovered audio file with Android 10+ Scoped Storage consent support
     * (`MediaStore.createDeleteRequest` on Android 11+, `RecoverableSecurityException` on Android 10).
     */
    suspend fun deleteAudioFile(
        context: Context,
        audioFile: DiscoveredAudioFile,
        folderPath: String
    ): DeleteFileOutcome = withContext(Dispatchers.IO) {
        val file = audioFile.file
        try {
            if (!file.exists()) {
                onExternalDeleteConfirmed(file.absolutePath, folderPath)
                return@withContext DeleteFileOutcome.Deleted
            }

            if (file.delete()) {
                // Also remove MediaStore row if indexed
                findMediaStoreAudioUri(context, file)?.let { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {}
                }
                onExternalDeleteConfirmed(file.absolutePath, folderPath)
                return@withContext DeleteFileOutcome.Deleted
            }

            // Direct delete failed (Android 10+ Scoped Storage) -> look up MediaStore URI
            val mediaUri = findMediaStoreAudioUri(context, file)
                ?: return@withContext DeleteFileOutcome.Failed("Android restricted deleting this file directly")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(mediaUri))
                return@withContext DeleteFileOutcome.RequiresUserConsent(
                    intentSender = pendingIntent.intentSender,
                    filePath = file.absolutePath,
                    folderPath = folderPath
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val rows = context.contentResolver.delete(mediaUri, null, null)
                    if (rows > 0) {
                        onExternalDeleteConfirmed(file.absolutePath, folderPath)
                        return@withContext DeleteFileOutcome.Deleted
                    }
                } catch (rse: RecoverableSecurityException) {
                    return@withContext DeleteFileOutcome.RequiresUserConsent(
                        intentSender = rse.userAction.actionIntent.intentSender,
                        filePath = file.absolutePath,
                        folderPath = folderPath
                    )
                }
            }
            DeleteFileOutcome.Failed("Could not delete this file")
        } catch (e: Exception) {
            e.printStackTrace()
            DeleteFileOutcome.Failed("Could not delete this file")
        }
    }

    suspend fun onExternalDeleteConfirmed(filePath: String, folderPath: String) = withContext(Dispatchers.IO) {
        dao.deleteAudioByPath(filePath)
        val remaining = dao.getAudiosForFolderSync(folderPath).filter { File(it.filePath).exists() }
        if (remaining.isEmpty()) {
            dao.deleteFolderByPath(folderPath)
        } else {
            val existingFolder = dao.getFoldersSync().find { it.path == folderPath }
            if (existingFolder != null) {
                dao.insertFolders(
                    listOf(
                        existingFolder.copy(
                            audioCount = remaining.size,
                            totalSizeBytes = remaining.sumOf { it.sizeBytes }
                        )
                    )
                )
            }
        }
    }

    private fun findMediaStoreAudioUri(context: Context, file: File): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        // Primary query: exact path
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(file.absolutePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}

        // Fallback query: display name + size
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?",
                arrayOf(file.name, file.length().toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext.lowercase(Locale.ROOT)) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "amr" -> "audio/amr"
        "3gp" -> "audio/3gpp"
        else -> "audio/*"
    }
}

