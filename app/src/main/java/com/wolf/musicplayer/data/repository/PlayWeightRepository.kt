package com.wolf.musicplayer.data.repository

import com.wolf.musicplayer.data.db.dao.PlayWeightDao
import com.wolf.musicplayer.data.db.entity.PlayWeightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayWeightRepository
    @Inject
    constructor(
        private val dao: PlayWeightDao,
    ) {
        fun observeAll(): Flow<Map<Long, Float>> = dao.observeAll().map { list -> list.associate { it.songId to it.weight } }

        suspend fun recordSkip(songId: Long) {
            val current = dao.getForSong(songId) ?: PlayWeightEntity(songId = songId)
            dao.upsert(
                current.copy(
                    weight = (current.weight * 0.75f).coerceAtLeast(0.1f),
                    skipCount = current.skipCount + 1,
                ),
            )
        }

        suspend fun recordFullPlay(songId: Long) {
            val current = dao.getForSong(songId) ?: PlayWeightEntity(songId = songId)
            dao.upsert(
                current.copy(
                    weight = (current.weight * 1.1f).coerceAtMost(1.0f),
                    playCount = current.playCount + 1,
                ),
            )
        }

        suspend fun resetWeight(songId: Long) {
            dao.upsert(PlayWeightEntity(songId = songId))
        }

        suspend fun getWeight(songId: Long): Float = dao.getForSong(songId)?.weight ?: 1.0f
    }
