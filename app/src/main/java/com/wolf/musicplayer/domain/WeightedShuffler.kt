package com.wolf.musicplayer.domain

import javax.inject.Inject

class WeightedShuffler
    @Inject
    constructor() {
        fun <T> pick(
            items: List<T>,
            weights: List<Float>,
        ): T {
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

        fun <T> permute(
            items: List<T>,
            weights: List<Float>,
        ): List<T> {
            require(items.isNotEmpty()) { "Items list must not be empty" }
            require(items.size == weights.size) { "Items and weights must have equal size" }
            val remaining = items.toMutableList()
            val remainingWeights = weights.toMutableList()
            val result = mutableListOf<T>()
            while (remaining.isNotEmpty()) {
                // Inline the draw step to track the index directly. Using pick() + indexOf() would be unreliable
                // when the list contains duplicate items.
                val weightSum = remainingWeights.sum()
                var randomValue = kotlin.random.Random.nextFloat() * weightSum
                var pickedIdx = remaining.size - 1
                for (i in remaining.indices) {
                    randomValue -= remainingWeights[i]
                    if (randomValue <= 0f) {
                        pickedIdx = i
                        break
                    }
                }
                val picked = remaining[pickedIdx]
                result.add(picked)
                remaining.removeAt(pickedIdx)
                remainingWeights.removeAt(pickedIdx)
            }
            return result
        }
    }
