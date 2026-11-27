package com.kexora.soniclet.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemHiddenDao {

    @Query("SELECT * FROM system_hidden_folders ORDER BY audioCount DESC")
    fun getAllFoldersFlow(): Flow<List<SystemHiddenFolderEntity>>

    @Query("SELECT * FROM system_hidden_folders ORDER BY audioCount DESC")
    suspend fun getFoldersSync(): List<SystemHiddenFolderEntity>

    @Query("SELECT * FROM system_hidden_audios ORDER BY title ASC")
    fun getAllAudiosFlow(): Flow<List<SystemHiddenAudioEntity>>

    @Query("SELECT * FROM system_hidden_audios ORDER BY title ASC")
    suspend fun getAllAudiosSync(): List<SystemHiddenAudioEntity>

    @Query("SELECT * FROM system_hidden_audios WHERE folderPath = :folderPath ORDER BY title ASC")
    fun getAudiosForFolderFlow(folderPath: String): Flow<List<SystemHiddenAudioEntity>>

    @Query("SELECT * FROM system_hidden_audios WHERE folderPath = :folderPath ORDER BY title ASC")
    suspend fun getAudiosForFolderSync(folderPath: String): List<SystemHiddenAudioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<SystemHiddenFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudios(audios: List<SystemHiddenAudioEntity>)

    @Query("DELETE FROM system_hidden_folders")
    suspend fun clearFolders()

    @Query("DELETE FROM system_hidden_audios")
    suspend fun clearAudios()

    @Query("DELETE FROM system_hidden_folders WHERE path = :path")
    suspend fun deleteFolderByPath(path: String)

    @Query("DELETE FROM system_hidden_audios WHERE folderPath = :folderPath")
    suspend fun deleteAudiosInFolder(folderPath: String)

    @Query("DELETE FROM system_hidden_audios WHERE filePath = :filePath")
    suspend fun deleteAudioByPath(filePath: String)

    @Transaction
    suspend fun replaceAll(folders: List<SystemHiddenFolderEntity>, audios: List<SystemHiddenAudioEntity>) {
        clearFolders()
        clearAudios()
        if (folders.isNotEmpty()) insertFolders(folders)
        if (audios.isNotEmpty()) insertAudios(audios)
    }
}
