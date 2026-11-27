package com.kexora.soniclet.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Permanent listening history record stored in Room for Soniclet Wrapped.
 * Records persist even if the underlying audio file is moved, renamed, or deleted from the device.
 */
@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey val songKey: String,
    val lastKnownSongId: Long = -1L,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val filePath: String = "",
    val totalDurationListenedMs: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L
)
