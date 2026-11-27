package com.kexora.soniclet.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Song::class,
        SystemHiddenFolderEntity::class,
        SystemHiddenAudioEntity::class,
        ListeningHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(UriConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun systemHiddenDao(): SystemHiddenDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `listening_history` (
                        `songKey` TEXT NOT NULL,
                        `lastKnownSongId` INTEGER NOT NULL DEFAULT -1,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `genre` TEXT NOT NULL DEFAULT '',
                        `filePath` TEXT NOT NULL DEFAULT '',
                        `totalDurationListenedMs` INTEGER NOT NULL DEFAULT 0,
                        `playCount` INTEGER NOT NULL DEFAULT 0,
                        `lastPlayedTimestamp` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`songKey`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
