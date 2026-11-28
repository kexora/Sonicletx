package com.kexora.soniclet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Strict Per-Song Coil Fetcher — completely eliminates Android MediaStore shared albumId pollution.
 *
 * Resolution Order (100% isolated per track):
 *  1. User custom artwork saved strictly for this songId in CustomArtworkManager.
 *  2. Track's own embedded picture via MediaMetadataRetriever on exact audio URI (safe sampled decode <= 640px, always released in finally).
 *  3. Clean failure -> Coil triggers FallbackAlbumArt. NEVER opens content://media/external/audio/albumart/$albumId or ContentResolver.loadThumbnail().
 */
class AudioArtFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // Hard block: NEVER resolve legacy MediaStore shared albumart URIs
        if (CustomArtworkManager.isLegacySharedAlbumArtUri(uri)) {
            throw FileNotFoundException("Blocked shared MediaStore albumart URI: $uri")
        }

        val songId = extractSongId(uri)
        val bitmapConfig = if (options.allowRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

        // Strict 3-Step per-song resolution (Step 1: CustomArtworkManager, Step 2: MediaMetadataRetriever)
        val bitmap = CustomArtworkManager.loadStrictSongArtworkBitmap(
            context = context,
            songId = songId,
            audioUri = uri,
            maxDimPx = MAX_ART_DIMENSION_PX,
            preferredConfig = bitmapConfig
        )

        if (bitmap != null) {
            return@withContext DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }

        // Step 3: No per-song custom art and no embedded picture -> clean fallback
        throw FileNotFoundException("No embedded or custom artwork for track: $uri")
    }

    private fun extractSongId(targetUri: Uri): Long {
        if (CustomArtworkManager.isAudioMediaUri(targetUri)) {
            return targetUri.lastPathSegment?.toLongOrNull() ?: -1L
        }
        return -1L
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (CustomArtworkManager.isLegacySharedAlbumArtUri(data)) {
                // Intercept and reject so Coil's ContentUriFetcher never loads shared albumart
                return AudioArtFetcher(context, data, options)
            }
            if (CustomArtworkManager.isAudioMediaUri(data)) {
                return AudioArtFetcher(context, data, options)
            }
            if (isDirectAudioFileUri(data)) {
                return AudioArtFetcher(context, data, options)
            }
            return null
        }

        private fun isDirectAudioFileUri(uri: Uri): Boolean {
            if (uri.scheme != "file") return false
            val lower = uri.path?.lowercase() ?: return false
            return lower.endsWith(".mp3") ||
                lower.endsWith(".m4a") ||
                lower.endsWith(".flac") ||
                lower.endsWith(".ogg") ||
                lower.endsWith(".opus") ||
                lower.endsWith(".wav") ||
                lower.endsWith(".aac") ||
                lower.endsWith(".wma")
        }
    }

    companion object {
        private const val MAX_ART_DIMENSION_PX = 640
    }
}

/**
 * Forces unique per-song Coil cache keys for every audio media URI and custom artwork file URI,
 * ensuring Coil never reuses cached artwork across different songs or stale custom covers.
 */
class AudioArtKeyer(private val context: Context) : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        if (CustomArtworkManager.isLegacySharedAlbumArtUri(data)) {
            return null
        }
        if (CustomArtworkManager.isAudioMediaUri(data)) {
            val songId = data.lastPathSegment?.toLongOrNull() ?: return "song_uri_${data}"
            val customVer = CustomArtworkManager.getCustomArtworkVersion(context, songId)
            return "song_art_${songId}_v${customVer}"
        }
        if (data.scheme == "file") {
            val path = data.path ?: return null
            if (path.contains("custom_artwork/art_")) {
                val file = File(path)
                val name = file.nameWithoutExtension // art_<songId>
                val songId = name.removePrefix("art_").toLongOrNull() ?: -1L
                val customVer = if (songId > 0L) CustomArtworkManager.getCustomArtworkVersion(context, songId) else 0L
                val mod = if (file.exists()) file.lastModified() else 0L
                return "song_custom_art_${songId}_${mod}_v${customVer}"
            }
        }
        return null
    }
}
