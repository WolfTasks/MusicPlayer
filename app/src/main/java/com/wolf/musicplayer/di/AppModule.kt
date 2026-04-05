package com.wolf.musicplayer.di

import com.wolf.musicplayer.domain.WeightedShuffler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideWeightedShuffler(): WeightedShuffler = WeightedShuffler()
}
