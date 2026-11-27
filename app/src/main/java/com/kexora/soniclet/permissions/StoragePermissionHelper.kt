package com.kexora.soniclet.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Google Play compliant MediaStore & Storage Access Framework (SAF) permission helper.
 * Relies strictly on READ_MEDIA_AUDIO / READ_MEDIA_VIDEO (API 33+) and READ_EXTERNAL_STORAGE (API <= 32).
 */
object StoragePermissionHelper {

    fun hasMediaAudioAccess(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasMediaVideoAccess(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Kept for source compatibility with folder/organizer helpers.
     * MediaStore + SAF operations do not require All Files Access.
     */
    fun hasAllFilesAccess(context: Context? = null): Boolean {
        return context?.let { hasMediaAudioAccess(it) } ?: true
    }

    /**
     * Opens standard application details settings for MediaStore permissions.
     */
    fun requestAllFilesAccess(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open application settings", Toast.LENGTH_SHORT).show()
        }
    }
}
