package com.wolf.musicplayer.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges shuffle state between [com.wolf.musicplayer.ui.screen.player.PlayerViewModel]
 * (main thread) and [com.wolf.musicplayer.service.AutoLibraryCallback] (Binder/IO thread).
 *
 * Threading contract: writes always happen on the main thread via [setShuffleEnabled];
 * reads may happen from any thread. [shuffleEnabled] is a Boolean, which the JVM assigns
 * atomically, so [@Volatile] is sufficient here. Do not add compound read-modify-write
 * operations to this class without re-evaluating the synchronisation strategy.
 */
@Singleton
class ShuffleStateHolder
    @Inject
    constructor() {
        @Volatile
        var shuffleEnabled: Boolean = false
            private set

        fun setShuffleEnabled(enabled: Boolean) {
            shuffleEnabled = enabled
        }
    }
