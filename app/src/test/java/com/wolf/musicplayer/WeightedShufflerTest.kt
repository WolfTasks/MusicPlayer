package com.wolf.musicplayer

import com.wolf.musicplayer.domain.WeightedShuffler
import org.junit.Assert.*
import org.junit.Test

class WeightedShufflerTest {

    private val shuffler = WeightedShuffler()

    @Test
    fun `picks single item when list has one element`() {
        val result = shuffler.pick(listOf("only"), listOf(1.0f))
        assertEquals("only", result)
    }

    @Test
    fun `throws when items list is empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            shuffler.pick(emptyList<String>(), emptyList())
        }
    }

    @Test
    fun `throws when sizes mismatch`() {
        assertThrows(IllegalArgumentException::class.java) {
            shuffler.pick(listOf("a", "b"), listOf(1.0f))
        }
    }

    @Test
    fun `heavily weighted item is picked much more often`() {
        val items = listOf("rare", "common")
        val weights = listOf(0.1f, 1.0f)
        val picks = (1..1000).map { shuffler.pick(items, weights) }
        val rareCount = picks.count { it == "rare" }
        val commonCount = picks.count { it == "common" }
        // common should be picked ~10x more often
        assertTrue("common picked at least 5x more than rare",
            commonCount > rareCount * 4)
    }

    @Test
    fun `equal weights produce roughly equal distribution`() {
        val items = listOf("a", "b")
        val weights = listOf(1.0f, 1.0f)
        val picks = (1..1000).map { shuffler.pick(items, weights) }
        val aCount = picks.count { it == "a" }
        // With equal weights, each should be picked ~500 times. Allow 15% deviation.
        assertTrue("a picked between 425 and 575 times", aCount in 425..575)
    }
}
