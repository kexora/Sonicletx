package com.kexora.soniclet.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import coil.Coil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Manages persistent storage and resolution of user-customized album artwork.
 * Guarantees that any custom cover set in Soniclet permanently overrides
 * system MediaStore caches and survives rescans, restarts, and file system indexing.
 */
object CustomArtworkManager {

    private const val TAG = "CustomArtworkManager"
    private const val PREFS_NAME = "soniclet_custom_artwork_prefs"
    private const val DIR_NAME = "custom_artwork"

    /**
     * Persistently saves custom artwork for a song into internal app storage.
     * Overwrites any previous custom artwork for this track.
     */
    fun saveCustomArtwork(context: Context, songId: Long, sourceUri: Uri): Uri? {
        return try {
            val artDir = File(context.filesDir, DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(artDir, "art_${songId}.jpg")

            // Read source bitmap
            val inputStream = if (sourceUri.scheme == "file") {
                FileInputStream(File(sourceUri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(sourceUri)
            } ?: return null

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            // Scale to high quality 1080x1080 max for crisp rendering
            val maxDim = 1080
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(width, height)
                val targetW = (width * scale).toInt().coerceAtLeast(1)
                val targetH = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
                out.flush()
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            val savedUri = Uri.fromFile(targetFile)

            // Save reference in preferences for instant fast lookup
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("song_$songId", targetFile.absolutePath)
                .apply()

            Log.d(TAG, "Saved custom artwork for song $songId -> ${targetFile.absolutePath}")
            savedUri
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving custom artwork for song $songId", e)
            null
        }
    }

    /**
     * Retrieves the persistent custom artwork URI for a song if one has been set.
     */
    fun getCustomArtworkUri(context: Context, songId: Long): Uri? {
        val artDir = File(context.filesDir, DIR_NAME)
        val file = File(artDir, "art_${songId}.jpg")
        return if (file.exists() && file.length() > 0) {
            Uri.fromFile(file)
        } else {
            null
        }
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
}
