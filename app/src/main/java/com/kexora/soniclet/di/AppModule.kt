package com.kexora.soniclet.di

import android.content.Context
import androidx.room.Room
import com.kexora.soniclet.database.AppDatabase
import com.kexora.soniclet.database.ListeningHistoryDao
import com.kexora.soniclet.database.SongDao
import com.kexora.soniclet.database.SystemHiddenDao
import com.kexora.soniclet.library.AudioRepository
import com.kexora.soniclet.library.AudioRepositoryImpl
import com.kexora.soniclet.library.ListeningHistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "soniclet_db"
        )
        .addMigrations(AppDatabase.MIGRATION_3_4)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideSongDao(database: AppDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    @Singleton
    fun provideSystemHiddenDao(database: AppDatabase): SystemHiddenDao {
        return database.systemHiddenDao()
    }

    @Provides
    @Singleton
    fun provideListeningHistoryDao(database: AppDatabase): ListeningHistoryDao {
        return database.listeningHistoryDao()
    }

    @Provides
    @Singleton
    fun provideListeningHistoryRepository(
        listeningHistoryDao: ListeningHistoryDao
    ): ListeningHistoryRepository {
        return ListeningHistoryRepository(listeningHistoryDao)
    }

    @Provides
    @Singleton
    fun provideAudioRepository(
        @ApplicationContext context: Context,
        songDao: SongDao
    ): AudioRepository {
        return AudioRepositoryImpl(songDao, context)
    }
}
