package com.kexora.soniclet.tools

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class RingtoneTarget(val displayName: String) {
    PHONE_CALL("Phone Ringtone"),
    NOTIFICATION("Notification Sound"),
    ALARM("Alarm Sound"),
    SAVE_ONLY("Save to Ringtones Folder")
}

object RingtoneCutterUtil {
    private const val TAG = "RingtoneCutterUtil"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasWriteSettingsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun openWriteSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open write settings intent: ${e.message}")
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }
    }

    fun trimAndSetRingtone(
        context: Context,
        inputUri: Uri,
        title: String,
        startMs: Long,
        endMs: Long,
        target: RingtoneTarget,
        onSuccess: (target: RingtoneTarget, uri: Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().ifBlank { "Trimmed_Sound" }
        val outputDir = File(context.cacheDir, "ringtones").apply { mkdirs() }
        val tempOutputFile = File(outputDir, "trimmed_${System.currentTimeMillis()}.m4a")

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                    .setEndPositionMs(endMs.coerceAtLeast(startMs + 500L))
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

        val transformer = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    mainHandler.post {
                        saveAndApplySound(
                            context = context,
                            tempFile = tempOutputFile,
                            title = sanitizedTitle,
                            target = target,
                            onSuccess = { uri -> onSuccess(target, uri) },
                            onError = onError
                        )
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    mainHandler.post {
                        exportException.printStackTrace()
                        onError("Failed to trim audio: ${exportException.message ?: "Unknown error"}")
                    }
                }
            })
            .build()

        try {
            transformer.start(editedMediaItem, tempOutputFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            onError("Error starting audio trimmer: ${e.message ?: "Unknown error"}")
        }
    }

    private fun saveAndApplySound(
        context: Context,
        tempFile: File,
        title: String,
        target: RingtoneTarget,
        onSuccess: (Uri?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val finalUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStoreScoped(context, tempFile, title, target)
            } else {
                saveToMediaStoreLegacy(context, tempFile, title, target)
            }

            // Clean up temporary cache file
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}

            if (finalUri == null) {
                onError("Failed to save audio into system storage.")
                return
            }

            // If user only wanted to save the sound file, we're done!
            if (target == RingtoneTarget.SAVE_ONLY) {
                onSuccess(finalUri)
                return
            }

            // Apply as system ringtone / notification / alarm
            if (!hasWriteSettingsPermission(context)) {
                onError("Sound saved! However, 'Modify System Settings' permission is required to set as default.")
                return
            }

            val ringtoneType = when (target) {
                RingtoneTarget.PHONE_CALL -> RingtoneManager.TYPE_RINGTONE
                RingtoneTarget.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
                RingtoneTarget.ALARM -> RingtoneManager.TYPE_ALARM
                RingtoneTarget.SAVE_ONLY -> null
            }

            if (ringtoneType != null) {
                RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, finalUri)
            }

            onSuccess(finalUri)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException setting ringtone: ${e.message}")
            onError("Permission denied: Please grant 'Modify System Settings' permission in Android Settings.")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting ringtone: ${e.message}", e)
            onError("Failed to set sound: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Modern Android 10+ (API 29+) MediaStore insertion via Scoped Storage.
     * Places the file in standard public directories (Ringtones, Notifications, Alarms).
     */
    private fun saveToMediaStoreScoped(
        context: Context,
        tempFile: File,
        title: String,
        target: RingtoneTarget
    ): Uri? {
        val relativeFolder = when (target) {
            RingtoneTarget.PHONE_CALL -> Environment.DIRECTORY_RINGTONES
            RingtoneTarget.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            RingtoneTarget.ALARM -> Environment.DIRECTORY_ALARMS
            RingtoneTarget.SAVE_ONLY -> Environment.DIRECTORY_RINGTONES
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.m4a")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "$relativeFolder/")
            
            // Set all flags to 0 first then enable the target one
            put(MediaStore.Audio.Media.IS_RINGTONE, if (target == RingtoneTarget.PHONE_CALL || target == RingtoneTarget.SAVE_ONLY) 1 else 0)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, if (target == RingtoneTarget.NOTIFICATION) 1 else 0)
            put(MediaStore.Audio.Media.IS_ALARM, if (target == RingtoneTarget.ALARM) 1 else 0)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        // Use external volume
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        val itemUri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(itemUri).use { out ->
                if (out == null) throw Exception("Could not open output stream")
                FileInputStream(tempFile).use { input ->
                    input.copyTo(out)
                }
            }

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed streaming bytes to MediaStore: ${e.message}", e)
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Exception) {}
            null
        }
    }

    /**
     * Legacy Android 9 and below MediaStore insertion.
     */
    private fun saveToMediaStoreLegacy(
        context: Context,
        tempFile: File,
        title: String,
        target: RingtoneTarget
    ): Uri? {
        val publicDirType = when (target) {
            RingtoneTarget.PHONE_CALL -> Environment.DIRECTORY_RINGTONES
            RingtoneTarget.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            RingtoneTarget.ALARM -> Environment.DIRECTORY_ALARMS
            RingtoneTarget.SAVE_ONLY -> Environment.DIRECTORY_RINGTONES
        }

        val destDir = Environment.getExternalStoragePublicDirectory(publicDirType).apply { mkdirs() }
        val destFile = File(destDir, "$title.m4a")

        FileInputStream(tempFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        var resultUri: Uri? = null
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf("audio/mp4")
        ) { _, uri ->
            resultUri = uri
        }

        // Wait brief moment for scanner or fallback to file content uri
        var attempts = 0
        while (resultUri == null && attempts < 10) {
            try { Thread.sleep(100) } catch (_: Exception) {}
            attempts++
        }

        return resultUri ?: Uri.fromFile(destFile)
    }
}
