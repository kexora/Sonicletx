package com.kexora.soniclet.database

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data class representing an audio track scanned from local storage via MediaStore.
 */
@Immutable
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val year: String = "",
    val genre: String = "",
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri? = null,
    val folderPath: String = "",
    val folderName: String = "Internal Storage",
    val filePath: String = "",
    val dateAdded: Long = 0L
)
