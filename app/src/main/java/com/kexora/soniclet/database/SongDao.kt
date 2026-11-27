package com.kexora.soniclet.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
// Yeh Room Database ka DAO (Data Access Object) hai, yahan SQL queries likhi hain
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongsSync(): List<Song>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @androidx.room.Update
    suspend fun updateSong(song: Song)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: Long)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Transaction
    suspend fun replaceAllSongs(songs: List<Song>) {
        deleteAllSongs()
        insertSongs(songs)
    }
}
