package com.kexora.soniclet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import coil.Coil
import com.kexora.soniclet.database.Song
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages strict per-song artwork storage, resolution, and Coil cache isolation.
 * Guarantees that every song resolves artwork strictly from:
 *  1. Its own user-saved custom artwork (keyed by unique song.id)
 *  2. Its own embedded audio picture via MediaMetadataRetriever (with safe release)
 *  3. Clean default fallback (NEVER shared MediaStore albumart/$albumId or folder photos)
 */
object CustomArtworkManager {

    private const val TAG = "CustomArtworkManager"
    private const val PREFS_NAME = "soniclet_custom_artwork_prefs"
    private const val DIR_NAME = "custom_artwork"
    private const val KEY_COIL_PURGED_V2 = "coil_art_cache_purged_v2"
    private const val VERSION_KEY_PREFIX = "ver_song_"
    private const val MAX_DECODE_DIM_PX = 640

    private val versionCache = ConcurrentHashMap<Long, Long>()

    /**
     * Returns true if the URI points to Android's legacy shared MediaStore albumart table,
     * which mixes unrelated tracks under a shared albumId and pollutes artwork across folders.
     */
    fun isLegacySharedAlbumArtUri(uri: Uri?): Boolean {
        val raw = uri?.toString() ?: return false
        return raw.contains("media/external/audio/albumart", ignoreCase = true) ||
            raw.contains("media/internal/audio/albumart", ignoreCase = true)
    }

    /**
     * Returns true if the URI points directly to a MediaStore audio media item.
     */
    fun isAudioMediaUri(uri: Uri?): Boolean {
        val raw = uri?.toString() ?: return false
        return raw.contains("media/external/audio/media", ignoreCase = true) ||
            raw.contains("media/internal/audio/media", ignoreCase = true)
    }

    /**
     * Returns the current per-song custom artwork version token used in Coil cache keys.
     */
    fun getCustomArtworkVersion(context: Context, songId: Long): Long {
        if (songId <= 0L) return 0L
        versionCache[songId]?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getLong("$VERSION_KEY_PREFIX$songId", 0L)
        val resolved = if (stored > 0L) {
            stored
        } else {
            val file = File(File(context.filesDir, DIR_NAME), "art_${songId}.jpg")
            if (file.exists() && file.length() > 0L) file.lastModified() else 0L
        }
        versionCache[songId] = resolved
        return resolved
    }

    /**
     * Builds a globally unique per-song Coil memory/disk cache key that never collides by albumId.
     */
    fun buildSongArtCacheKey(context: Context, song: Song): String {
        val customVer = getCustomArtworkVersion(context, song.id)
        return "song_art_${song.id}_${song.dateAdded}_$customVer"
    }

    /**
     * Builds a globally unique per-song Coil memory/disk cache key from explicit components.
     */
    fun buildSongArtCacheKey(context: Context, songId: Long, dateModified: Long): String {
        val customVer = getCustomArtworkVersion(context, songId)
        return "song_art_${songId}_${dateModified}_$customVer"
    }

    private fun bumpCustomArtworkVersion(context: Context, songId: Long): Long {
        val next = System.currentTimeMillis()
        versionCache[songId] = next
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("$VERSION_KEY_PREFIX$songId", next)
            .apply()
        return next
    }

    /**
     * Persistently saves custom artwork from a Uri for a specific songId into internal app storage.
     */
    fun saveCustomArtwork(context: Context, songId: Long, sourceUri: Uri): Uri? {
        if (songId <= 0L) return null
        return try {
            val inputStream = if (sourceUri.scheme == "file") {
                FileInputStream(File(sourceUri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(sourceUri)
            } ?: return null

            val originalBitmap = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val saved = saveCustomArtworkBitmap(
                context = context,
                songId = songId,
                bitmap = originalBitmap,
                maxDim = 1080,
                jpegQuality = 94
            )
            if (!originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }
            saved
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving custom artwork for song $songId", e)
            null
        }
    }

    /**
     * Persistently saves a Bitmap as per-song custom artwork (strictly keyed by songId).
     * Does NOT recycle the caller's input bitmap.
     */
    fun saveCustomArtworkBitmap(
        context: Context,
        songId: Long,
        bitmap: Bitmap,
        maxDim: Int = 600,
        jpegQuality: Int = 90
    ): Uri? {
        if (songId <= 0L || bitmap.isRecycled) return null
        return try {
            val artDir = File(context.filesDir, DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(artDir, "art_${songId}.jpg")

            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(width, height).coerceAtLeast(1)
                val targetW = (width * scale).toInt().coerceAtLeast(1)
                val targetH = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                out.flush()
            }

            if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            val savedUri = Uri.fromFile(targetFile)
            val version = bumpCustomArtworkVersion(context, songId)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("song_$songId", targetFile.absolutePath)
                .putLong("$VERSION_KEY_PREFIX$songId", version)
                .apply()

            Log.d(TAG, "Saved per-song artwork for song $songId -> ${targetFile.absolutePath}")
            savedUri
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving bitmap artwork for song $songId", e)
            null
        }
    }

    /**
     * Removes any saved custom artwork for this specific songId and bumps its cache version.
     */
    fun clearCustomArtwork(context: Context, songId: Long) {
        if (songId <= 0L) return
        try {
            val file = File(File(context.filesDir, DIR_NAME), "art_${songId}.jpg")
            if (file.exists()) file.delete()
            val version = bumpCustomArtworkVersion(context, songId)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove("song_$songId")
                .putLong("$VERSION_KEY_PREFIX$songId", version)
                .apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to clear custom artwork for song $songId: ${e.message}")
        }
    }

    /**
     * Retrieves the persistent custom artwork URI strictly for this songId, or null if none exists.
     */
    fun getCustomArtworkUri(context: Context, songId: Long): Uri? {
        if (songId <= 0L) return null
        val artDir = File(context.filesDir, DIR_NAME)
        val file = File(artDir, "art_${songId}.jpg")
        return if (file.exists() && file.length() > 0L) {
            Uri.fromFile(file)
        } else {
            null
        }
    }

    /**
     * Strict 3-Step Per-Song Artwork Bitmap Loader (IO-safe):
     *  1. Per-song custom artwork from internal storage (keyed strictly by songId)
     *  2. Track's own embedded picture via MediaMetadataRetriever on exact audioUri (always released)
     *  3. Null (caller renders FallbackAlbumArt) — NEVER queries MediaStore albumart or loadThumbnail
     */
    fun loadStrictSongArtworkBitmap(
        context: Context,
        songId: Long,
        audioUri: Uri?,
        maxDimPx: Int = MAX_DECODE_DIM_PX,
        preferredConfig: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap? {
        // Step 1: Per-song user custom artwork
        if (songId > 0L) {
            val customUri = getCustomArtworkUri(context, songId)
            val customPath = customUri?.path
            if (!customPath.isNullOrBlank()) {
                decodeFileSampled(customPath, maxDimPx, preferredConfig)?.let { return it }
            }
        }

        // Step 2: Track's own embedded artwork via MediaMetadataRetriever
        if (audioUri != null && !isLegacySharedAlbumArtUri(audioUri)) {
            val retriever = MediaMetadataRetriever()
            try {
                if (audioUri.scheme == "file" && !audioUri.path.isNullOrBlank()) {
                    retriever.setDataSource(audioUri.path)
                } else {
                    retriever.setDataSource(context, audioUri)
                }
                val rawBytes = retriever.embeddedPicture
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    decodeBytesSampled(rawBytes, maxDimPx, preferredConfig)?.let { return it }
                }
            } catch (_: Throwable) {
                // Track has no embedded art or unsupported container
            } finally {
                try {
                    retriever.release()
                } catch (_: Throwable) {}
            }
        }

        // Step 3: Clean fallback (no shared folder or albumId lookup)
        return null
    }

    fun decodeFileSampled(
        filePath: String,
        maxDimPx: Int = MAX_DECODE_DIM_PX,
        preferredConfig: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists() || file.length() <= 0L) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
                inPreferredConfig = preferredConfig
            }
            BitmapFactory.decodeFile(filePath, options)
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeBytesSampled(
        bytes: ByteArray,
        maxDimPx: Int = MAX_DECODE_DIM_PX,
        preferredConfig: Bitmap.Config = Bitmap.Config.RGB_565
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
                inPreferredConfig = preferredConfig
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Throwable) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimPx: Int): Int {
        var inSampleSize = 1
        val target = maxDimPx.coerceAtLeast(64)
        while ((width / inSampleSize) > target || (height / inSampleSize) > target) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /**
     * Invalidates Coil memory & disk cache for a specific song after saving/removing custom art.
     */
    fun invalidateSongArtCache(context: Context, songId: Long) {
        if (songId > 0L) {
            bumpCustomArtworkVersion(context, songId)
        }
        invalidateCoilCache(context)
    }

    /**
     * Invalidates Coil memory and disk caches so that updated cover arts
     * instantly re-render across the entire app (player, queue, lists, widgets).
     */
    fun invalidateCoilCache(context: Context) {
        try {
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to invalidate Coil cache: ${e.message}")
        }
    }

    /**
     * One-time migration purge that wipes any pre-existing polluted Coil disk & memory
     * artwork caches so shared albumId/folder photos disappear immediately on first launch.
     */
    fun purgeStaleArtworkCacheOnce(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_COIL_PURGED_V2, false)) return

            // Wipe disk cache folder directly if present (safe even before Coil initializes)
            val cacheFolder = File(context.cacheDir, "soniclet_art_cache")
            if (cacheFolder.exists()) {
                cacheFolder.deleteRecursively()
            }

            // Also clear active Coil caches if already initialized
            try {
                val loader = Coil.imageLoader(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            } catch (_: Throwable) {}

            prefs.edit().putBoolean(KEY_COIL_PURGED_V2, true).apply()
            Log.d(TAG, "Purged legacy Coil artwork cache (v2 per-song isolation)")
        } catch (e: Throwable) {
            Log.w(TAG, "Error purging legacy artwork cache: ${e.message}")
        }
    }
}
