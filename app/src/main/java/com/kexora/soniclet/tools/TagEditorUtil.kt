package com.kexora.soniclet.tools

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.kexora.soniclet.database.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.logging.Level
import java.util.logging.Logger

data class ExtendedAudioTags(
    val albumArtist: String = "",
    val trackNumber: String = "",
    val composer: String = "",
    val comment: String = "",
    val lyrics: String = ""
)

object TagEditorUtil {

    private const val TAG = "TagEditorUtil"

    init {
        try {
            TagOptionSingleton.getInstance().isAndroid = true
            TagOptionSingleton.getInstance().isId3v2Save = true
            // Silence noisy JAudiotagger logging on Android
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        } catch (e: Throwable) {
            Log.w(TAG, "TagOptionSingleton init exception: ${e.message}")
        }
    }

    /**
     * Reads extended ID3 / Vorbis / MP4 metadata tags directly from the physical audio file.
     * Safe to invoke on Dispatchers.IO.
     */
    fun readExtendedTags(context: Context, song: Song): ExtendedAudioTags {
        var resolvedPath = song.filePath
        if (resolvedPath.isBlank() || !File(resolvedPath).exists()) {
            resolvedPath = queryRealPathFromUri(context, song.contentUri) ?: song.filePath
        }
        val directFile = if (resolvedPath.isNotBlank()) File(resolvedPath) else null
        var tempFile: File? = null

        return try {
            val readableFile = when {
                directFile != null && directFile.exists() && directFile.canRead() -> directFile
                song.contentUri != Uri.EMPTY -> {
                    val ext = directFile?.extension?.ifBlank { "mp3" } ?: "mp3"
                    val tmp = File(context.cacheDir, "tag_read_${System.currentTimeMillis()}.$ext")
                    tempFile = tmp
                    if (copySourceToTemp(context, song.contentUri, directFile, tmp) && tmp.length() > 0) tmp else null
                }
                else -> null
            } ?: return ExtendedAudioTags()

            val audioFile = AudioFileIO.read(readableFile)
            val tag: Tag? = if (audioFile is MP3File) {
                audioFile.iD3v2Tag ?: audioFile.tag
            } else {
                audioFile.tag
            }

            if (tag == null) return ExtendedAudioTags()

            ExtendedAudioTags(
                albumArtist = safeGetField(tag, FieldKey.ALBUM_ARTIST),
                trackNumber = safeGetField(tag, FieldKey.TRACK),
                composer = safeGetField(tag, FieldKey.COMPOSER),
                comment = safeGetField(tag, FieldKey.COMMENT),
                lyrics = safeGetField(tag, FieldKey.LYRICS)
            )
        } catch (e: Throwable) {
            Log.w(TAG, "readExtendedTags failed for '${song.title}': ${e.message}")
            ExtendedAudioTags()
        } finally {
            tempFile?.let { if (it.exists()) it.delete() }
        }
    }

    private fun safeGetField(tag: Tag, key: FieldKey): String {
        return try {
            tag.getFirst(key)?.trim() ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Updates ID3 / Vorbis / FLAC / MP4 audio metadata tags directly inside the physical audio file
     * and syncs the changes to Android's system MediaStore so every music player sees the updated
     * title, artist, album, year, genre, extended studio tags, embedded lyrics, and custom artwork.
     */
    fun editSongTags(
        context: Context,
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String,
        newAlbumArtUri: Uri?,
        newAlbumArtist: String? = null,
        newTrackNumber: String? = null,
        newComposer: String? = null,
        newComment: String? = null,
        newLyrics: String? = null
    ): Boolean {
        var resolvedPath = song.filePath
        if (resolvedPath.isBlank() || !File(resolvedPath).exists()) {
            resolvedPath = queryRealPathFromUri(context, song.contentUri) ?: song.filePath
        }

        val targetFile = if (resolvedPath.isNotBlank()) File(resolvedPath) else null
        val extension = targetFile?.extension?.ifBlank { "mp3" } ?: "mp3"
        val tempWorkingFile = File(context.cacheDir, "tag_work_${System.currentTimeMillis()}.$extension")

        var writeSuccess = false

        try {
            // Step 1: Copy audio data to safe app-private temp file where JAudiotagger can work with full IO permissions
            val copied = copySourceToTemp(context, song.contentUri, targetFile, tempWorkingFile)
            if (copied && tempWorkingFile.exists() && tempWorkingFile.length() > 0) {
                // Step 2: Modify ID3/audio tags and embed custom artwork inside the temp audio file
                val tagged = embedTagsAndArtwork(
                    context = context,
                    file = tempWorkingFile,
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
                if (tagged) {
                    // Step 3: Overwrite physical target file directly with the newly tagged file
                    writeSuccess = overwriteTargetAudioFile(context, song.contentUri, targetFile, tempWorkingFile)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed editing audio tags for song '${song.title}'", e)
        } finally {
            if (tempWorkingFile.exists()) {
                tempWorkingFile.delete()
            }
        }

        // Update MediaStore columns directly so changes take effect immediately across Android system
        updateMediaStoreMeta(
            context = context,
            song = song,
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newYear = newYear,
            newGenre = newGenre,
            newAlbumArtist = newAlbumArtist,
            newTrackNumber = newTrackNumber,
            newComposer = newComposer
        )

        // Rescan file with MediaScanner so Android MediaStore indexer reads freshly embedded physical ID3 tags
        val scanPath = if (targetFile != null && targetFile.exists()) targetFile.absolutePath else song.filePath
        if (scanPath.isNotBlank() && File(scanPath).exists()) {
            try {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(scanPath),
                    arrayOf("audio/*")
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner indexed: $path -> $uri")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "MediaScanner rescan failed: ${e.message}")
            }
        }

        Log.d(TAG, "editSongTags finished for '${song.title}', success=$writeSuccess, path='$resolvedPath'")
        return writeSuccess
    }

    private fun copySourceToTemp(
        context: Context,
        contentUri: Uri,
        targetFile: File?,
        tempFile: File
    ): Boolean {
        return try {
            val inStream = (if (targetFile != null && targetFile.exists() && targetFile.canRead()) {
                FileInputStream(targetFile)
            } else if (contentUri != Uri.EMPTY) {
                context.contentResolver.openInputStream(contentUri)
            } else null) ?: return false

            inStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error copying to temp file", e)
            false
        }
    }

    private fun applyOptionalTagField(tag: Tag, key: FieldKey, value: String?) {
        if (value == null) return
        try {
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                tag.setField(key, trimmed)
            } else {
                tag.deleteField(key)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Skipped unsupported tag field $key: ${e.message}")
        }
    }

    private fun embedTagsAndArtwork(
        context: Context,
        file: File,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String,
        newAlbumArtUri: Uri?,
        newAlbumArtist: String?,
        newTrackNumber: String?,
        newComposer: String?,
        newComment: String?,
        newLyrics: String?
    ): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            if (audioFile is MP3File) {
                val mp3 = audioFile
                var id3v2 = mp3.iD3v2Tag
                if (id3v2 == null) {
                    id3v2 = ID3v23Tag()
                    mp3.setID3v2Tag(id3v2)
                }

                if (newTitle.isNotBlank()) id3v2.setField(FieldKey.TITLE, newTitle.trim())
                if (newArtist.isNotBlank()) id3v2.setField(FieldKey.ARTIST, newArtist.trim())
                if (newAlbum.isNotBlank()) id3v2.setField(FieldKey.ALBUM, newAlbum.trim())
                if (newYear.isNotBlank()) id3v2.setField(FieldKey.YEAR, newYear.trim())
                if (newGenre.isNotBlank()) id3v2.setField(FieldKey.GENRE, newGenre.trim())

                applyOptionalTagField(id3v2, FieldKey.ALBUM_ARTIST, newAlbumArtist)
                applyOptionalTagField(id3v2, FieldKey.TRACK, newTrackNumber)
                applyOptionalTagField(id3v2, FieldKey.COMPOSER, newComposer)
                applyOptionalTagField(id3v2, FieldKey.COMMENT, newComment)
                applyOptionalTagField(id3v2, FieldKey.LYRICS, newLyrics)

                if (newAlbumArtUri != null) {
                    val artBytes = extractOptimizedArtBytes(context, newAlbumArtUri)
                    if (artBytes != null && artBytes.isNotEmpty()) {
                        try {
                            id3v2.deleteArtworkField()
                            val artwork = ArtworkFactory.getNew().apply {
                                binaryData = artBytes
                                mimeType = "image/jpeg"
                                pictureType = org.jaudiotagger.tag.reference.PictureTypes.DEFAULT_ID
                            }
                            id3v2.setField(artwork)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Failed embedding artwork into MP3 tag: ${e.message}")
                        }
                    }
                }
                mp3.save()
            } else {
                val tag = audioFile.tag ?: audioFile.createDefaultTag().also { audioFile.tag = it }
                if (newTitle.isNotBlank()) tag.setField(FieldKey.TITLE, newTitle.trim())
                if (newArtist.isNotBlank()) tag.setField(FieldKey.ARTIST, newArtist.trim())
                if (newAlbum.isNotBlank()) tag.setField(FieldKey.ALBUM, newAlbum.trim())
                if (newYear.isNotBlank()) tag.setField(FieldKey.YEAR, newYear.trim())
                if (newGenre.isNotBlank()) tag.setField(FieldKey.GENRE, newGenre.trim())

                applyOptionalTagField(tag, FieldKey.ALBUM_ARTIST, newAlbumArtist)
                applyOptionalTagField(tag, FieldKey.TRACK, newTrackNumber)
                applyOptionalTagField(tag, FieldKey.COMPOSER, newComposer)
                applyOptionalTagField(tag, FieldKey.COMMENT, newComment)
                applyOptionalTagField(tag, FieldKey.LYRICS, newLyrics)

                if (newAlbumArtUri != null) {
                    val artBytes = extractOptimizedArtBytes(context, newAlbumArtUri)
                    if (artBytes != null && artBytes.isNotEmpty()) {
                        try {
                            tag.deleteArtworkField()
                            val artwork = ArtworkFactory.getNew().apply {
                                binaryData = artBytes
                                mimeType = "image/jpeg"
                                pictureType = org.jaudiotagger.tag.reference.PictureTypes.DEFAULT_ID
                            }
                            tag.setField(artwork)
                        } catch (e: Throwable) {
                            Log.w(TAG, "Failed embedding artwork into audio tag: ${e.message}")
                        }
                    }
                }
                audioFile.commit()
            }
            Log.d(TAG, "Successfully embedded metadata & artwork into temp audio file")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error embedding tags into temp file: ${file.absolutePath}", e)
            false
        }
    }

    private fun overwriteTargetAudioFile(
        context: Context,
        contentUri: Uri,
        targetFile: File?,
        tempFile: File
    ): Boolean {
        // Method 1: Direct FileOutputStream overwrite
        if (targetFile != null && targetFile.exists()) {
            try {
                FileOutputStream(targetFile, false).use { out ->
                    FileInputStream(tempFile).use { inStream ->
                        inStream.copyTo(out)
                    }
                    out.flush()
                }
                Log.d(TAG, "Direct FileOutputStream overwrite succeeded for ${targetFile.absolutePath}")
                return true
            } catch (e: Throwable) {
                Log.w(TAG, "Direct FileOutputStream failed, trying RandomAccessFile: ${e.message}")
            }

            try {
                RandomAccessFile(targetFile, "rw").use { raf ->
                    raf.setLength(0)
                    FileInputStream(tempFile).use { inStream ->
                        val inChannel = inStream.channel
                        val outChannel = raf.channel
                        outChannel.transferFrom(inChannel, 0, inChannel.size())
                    }
                }
                Log.d(TAG, "RandomAccessFile overwrite succeeded for ${targetFile.absolutePath}")
                return true
            } catch (e: Throwable) {
                Log.w(TAG, "RandomAccessFile failed: ${e.message}")
            }

            // Method 1c: Sibling atomic replacement
            try {
                val parent = targetFile.parentFile
                if (parent != null && parent.canWrite()) {
                    val sibling = File(parent, ".tmp_tag_${System.currentTimeMillis()}.${targetFile.extension}")
                    tempFile.copyTo(sibling, overwrite = true)
                    if (sibling.exists() && sibling.length() > 0) {
                        if (targetFile.delete() && sibling.renameTo(targetFile)) {
                            Log.d(TAG, "Sibling atomic overwrite succeeded for ${targetFile.absolutePath}")
                            return true
                        }
                        sibling.delete()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Sibling atomic overwrite failed: ${e.message}")
            }
        }

        // Method 2: ContentResolver openOutputStream with standard valid write modes ("wt", "w")
        if (contentUri != Uri.EMPTY) {
            for (mode in listOf("wt", "w")) {
                try {
                    context.contentResolver.openOutputStream(contentUri, mode)?.use { outStream ->
                        FileInputStream(tempFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                        outStream.flush()
                        Log.d(TAG, "openOutputStream $mode succeeded for $contentUri")
                        return true
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "openOutputStream $mode failed: ${e.message}")
                }
            }

            // Method 3: ContentResolver openFileDescriptor write-back with channel truncation
            for (mode in listOf("wt", "w", "rwt", "rw")) {
                try {
                    context.contentResolver.openFileDescriptor(contentUri, mode)?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { outStream ->
                            outStream.channel.position(0)
                            FileInputStream(tempFile).use { inStream ->
                                inStream.copyTo(outStream)
                                outStream.channel.truncate(inStream.channel.size())
                            }
                            outStream.flush()
                        }
                        Log.d(TAG, "openFileDescriptor $mode overwrite succeeded for $contentUri")
                        return true
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "openFileDescriptor $mode failed: ${e.message}")
                }
            }
        }

        return false
    }

    /**
     * Creates an IntentSender on Android 11+ (API 30+) requesting scoped storage write
     * permission from the user for this specific audio file, without requiring the broad
     * All Files Access permission.
     */
    fun createWriteRequestIntentSender(context: Context, song: Song): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && song.contentUri != Uri.EMPTY) {
            return try {
                MediaStore.createWriteRequest(context.contentResolver, listOf(song.contentUri)).intentSender
            } catch (e: Throwable) {
                Log.w(TAG, "Failed creating write request: ${e.message}")
                null
            }
        }
        return null
    }

    private fun extractOptimizedArtBytes(context: Context, artUri: Uri): ByteArray? {
        return try {
            val inputStream = try {
                context.contentResolver.openInputStream(artUri)
            } catch (e: Throwable) {
                if (artUri.scheme == "file" && artUri.path != null) {
                    FileInputStream(File(artUri.path!!))
                } else null
            } ?: (if (artUri.scheme == "file" && artUri.path != null) {
                FileInputStream(File(artUri.path!!))
            } else null) ?: return null

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return null

            // Scale to high quality 800x800 maximum to embed cleanly in ID3 tags
            val maxDim = 800
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(width, height)
                val targetW = (width * scale).toInt().coerceAtLeast(1)
                val targetH = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            baos.toByteArray()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed reading artwork bytes from uri $artUri", e)
            null
        }
    }

    private fun updateMediaStoreMeta(
        context: Context,
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: String,
        newGenre: String,
        newAlbumArtist: String?,
        newTrackNumber: String?,
        newComposer: String?
    ) {
        try {
            val values = ContentValues().apply {
                if (newTitle.isNotBlank()) put(MediaStore.Audio.Media.TITLE, newTitle.trim())
                if (newArtist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, newArtist.trim())
                if (newAlbum.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, newAlbum.trim())
                if (newYear.isNotBlank()) {
                    newYear.trim().toIntOrNull()?.let {
                        put(MediaStore.Audio.Media.YEAR, it)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && newGenre.isNotBlank()) {
                    put(MediaStore.Audio.Media.GENRE, newGenre.trim())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !newAlbumArtist.isNullOrBlank()) {
                    put(MediaStore.Audio.Media.ALBUM_ARTIST, newAlbumArtist.trim())
                }
                if (!newComposer.isNullOrBlank()) {
                    put(MediaStore.Audio.Media.COMPOSER, newComposer.trim())
                }
                if (!newTrackNumber.isNullOrBlank()) {
                    newTrackNumber.trim().substringBefore('/').toIntOrNull()?.let {
                        put(MediaStore.Audio.Media.TRACK, it)
                    }
                }
            }

            if (song.contentUri != Uri.EMPTY) {
                context.contentResolver.update(song.contentUri, values, null, null)
            } else if (song.id > 0) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                context.contentResolver.update(uri, values, null, null)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "MediaStore metadata contentResolver update ignored/deferred: ${e.message}")
        }
    }

    private fun queryRealPathFromUri(context: Context, uri: Uri): String? {
        return try {
            val proj = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    cursor.getString(idx)
                } else null
            }
        } catch (e: Throwable) {
            null
        }
    }
}
