package com.kexora.soniclet.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "system_hidden_folders")
data class SystemHiddenFolderEntity(
    @PrimaryKey val path: String,
    val name: String,
    val type: String, // CALL_RECORDING, VOICE_NOTE, NOMEDIA, DOT_FOLDER, ALL
    val hasNoMedia: Boolean,
    val isDotPrefixed: Boolean,
    val audioCount: Int,
    val totalSizeBytes: Long,
    val lastScannedMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "system_hidden_audios",
    indices = [Index(value = ["folderPath"])]
)
data class SystemHiddenAudioEntity(
    @PrimaryKey val filePath: String,
    val folderPath: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val extension: String,
    val lastModifiedMs: Long
)
