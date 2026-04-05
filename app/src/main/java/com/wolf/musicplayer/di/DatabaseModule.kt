package com.wolf.musicplayer.di

import android.content.Context
import androidx.room.Room
import com.wolf.musicplayer.data.db.AppDatabase
import com.wolf.musicplayer.data.db.dao.PlaylistDao
import com.wolf.musicplayer.data.db.dao.PlayWeightDao
import com.wolf.musicplayer.data.db.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "music_player.db").build()

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides
    fun providePlayWeightDao(db: AppDatabase): PlayWeightDao = db.playWeightDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
}
