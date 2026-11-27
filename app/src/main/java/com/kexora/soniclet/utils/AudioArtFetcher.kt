package com.kexora.soniclet.utils

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom Coil Fetcher that intelligently resolves audio album art on modern Android.
 * If the legacy MediaStore albumart URI cannot be opened directly, this fetcher extracts
 * the embedded cover art from the underlying audio file via ContentResolver.loadThumbnail
 * (API 29+) or MediaMetadataRetriever embedded picture bytes.
 */
class AudioArtFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        val isAlbumArtUri = uriStr.contains("media/external/audio/albumart")
        val isAudioMediaUri = uriStr.contains("media/external/audio/media")

        if (!isAlbumArtUri && !isAudioMediaUri) {
            return@withContext null
        }

        // 1. If it's an albumart URI, first check if a custom artwork was saved by user
        if (isAlbumArtUri) {
            val albumId = uri.lastPathSegment?.toLongOrNull()
            if (albumId != null && albumId > 0) {
                val songId = findSongIdForAlbum(albumId)
                if (songId != null) {
                    val customArt = CustomArtworkManager.getCustomArtworkUri(context, songId)
                    if (customArt?.path != null) {
                        try {
                            val bitmap = BitmapFactory.decodeFile(customArt.path)
                            if (bitmap != null) {
                                return@withContext DrawableResult(
                                    drawable = BitmapDrawable(context.resources, bitmap),
                                    isSampled = false,
                                    dataSource = DataSource.DISK
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // Direct input stream opening
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(context.resources, bitmap),
                            isSampled = false,
                            dataSource = DataSource.DISK
                        )
                    }
                }
            } catch (_: Exception) {
                // MediaStore albumart file does not exist or threw FileNotFoundException on Android 10+
            }

            // 2. Find a song belonging to this albumId to retrieve embedded picture
            if (albumId != null && albumId > 0) {
                val songUri = findSongUriForAlbum(albumId)
                if (songUri != null) {
                    val artResult = extractArtFromAudioUri(songUri)
                    if (artResult != null) return@withContext artResult
                }
            }
        }

        // 3. If it's a direct audio media URI
        if (isAudioMediaUri) {
            val artResult = extractArtFromAudioUri(uri)
            if (artResult != null) return@withContext artResult
        }

        null
    }

    private fun findSongIdForAlbum(albumId: Long): Long? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.ALBUM_ID} = ?"
            val selectionArgs = arrayOf(albumId.toString())

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media._ID} ASC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findSongUriForAlbum(albumId: Long): Uri? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.ALBUM_ID} = ?"
            val selectionArgs = arrayOf(albumId.toString())

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media._ID} ASC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val songId = cursor.getLong(0)
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractArtFromAudioUri(audioUri: Uri): FetchResult? {
        // Option A: API 29+ loadThumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val bitmap = context.contentResolver.loadThumbnail(
                    audioUri,
                    Size(512, 512),
                    null
                )
                if (bitmap != null) {
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            } catch (_: Exception) {
                // loadThumbnail failed or not supported by provider, try MediaMetadataRetriever
            }
        }

        // Option B: MediaMetadataRetriever embeddedPicture
        try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, audioUri)
            val rawBytes = mmr.embeddedPicture
            mmr.release()

            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                if (bitmap != null) {
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (_: Exception) {
            // No embedded picture or unreadable format
        }

        return null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uriStr = data.toString()
            if (uriStr.contains("media/external/audio/albumart") || uriStr.contains("media/external/audio/media")) {
                return AudioArtFetcher(context, data, options)
            }
            return null
        }
    }
}
