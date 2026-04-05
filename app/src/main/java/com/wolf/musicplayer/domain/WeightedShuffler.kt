package com.wolf.musicplayer.domain

import javax.inject.Inject

class WeightedShuffler @Inject constructor() {

    fun <T> pick(items: List<T>, weights: List<Float>): T {
        require(items.isNotEmpty()) { "Items list must not be empty" }
        require(items.size == weights.size) { "Items and weights must have equal size" }

        val total = weights.sum()
        var remaining = Math.random().toFloat() * total

        for (i in items.indices) {
            remaining -= weights[i]
            if (remaining <= 0f) return items[i]
        }

        return items.last()
    }
}
