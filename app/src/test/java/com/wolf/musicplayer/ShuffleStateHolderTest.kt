package com.wolf.musicplayer

import com.wolf.musicplayer.domain.ShuffleStateHolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleStateHolderTest {
    @Test
    fun `defaults to false`() {
        val holder = ShuffleStateHolder()
        assertFalse(holder.shuffleEnabled)
    }

    @Test
    fun `setShuffleEnabled true updates flag`() {
        val holder = ShuffleStateHolder()
        holder.setShuffleEnabled(true)
        assertTrue(holder.shuffleEnabled)
    }

    @Test
    fun `setShuffleEnabled false updates flag`() {
        val holder = ShuffleStateHolder()
        holder.setShuffleEnabled(true)
        holder.setShuffleEnabled(false)
        assertFalse(holder.shuffleEnabled)
    }
}
