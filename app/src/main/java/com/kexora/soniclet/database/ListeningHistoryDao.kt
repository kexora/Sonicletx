package com.kexora.soniclet.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningHistoryDao {
    @Query("SELECT * FROM listening_history ORDER BY totalDurationListenedMs DESC, playCount DESC, lastPlayedTimestamp DESC")
    fun observeAllHistory(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history ORDER BY totalDurationListenedMs DESC, playCount DESC")
    suspend fun getAllHistorySync(): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history WHERE songKey = :songKey LIMIT 1")
    suspend fun getBySongKey(songKey: String): ListeningHistoryEntity?

    @Query("SELECT * FROM listening_history WHERE lastKnownSongId = :songId LIMIT 1")
    suspend fun getBySongId(songId: Long): ListeningHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ListeningHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ListeningHistoryEntity>)

    @Query("DELETE FROM listening_history WHERE songKey = :songKey")
    suspend fun deleteBySongKey(songKey: String)

    @Query("DELETE FROM listening_history")
    suspend fun clearAll()
}
