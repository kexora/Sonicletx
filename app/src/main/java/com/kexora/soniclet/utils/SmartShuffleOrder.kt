package com.kexora.soniclet.utils

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * Custom ExoPlayer [ShuffleOrder] implementation for Smart Shuffle.
 *
 * It routes track transitions according to intelligent song scores (play count,
 * recency, and genre diversity) without recreating MediaItems or restarting
 * playback, ensuring 100% gapless, seamless playback transitions with zero
 * pausing/buffering glitches.
 */
class SmartShuffleOrder(
    val shuffledIndices: IntArray
) : ShuffleOrder {

    private val indexToPosition: HashMap<Int, Int> = HashMap<Int, Int>(shuffledIndices.size).apply {
        for (i in shuffledIndices.indices) {
            put(shuffledIndices[i], i)
        }
    }

    override fun getLength(): Int = shuffledIndices.size

    override fun getNextIndex(index: Int): Int {
        val pos = indexToPosition[index] ?: return C.INDEX_UNSET
        val nextPos = pos + 1
        return if (nextPos < shuffledIndices.size) shuffledIndices[nextPos] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val pos = indexToPosition[index] ?: return C.INDEX_UNSET
        val prevPos = pos - 1
        return if (prevPos >= 0) shuffledIndices[prevPos] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int =
        if (shuffledIndices.isNotEmpty()) shuffledIndices.last() else C.INDEX_UNSET

    override fun getFirstIndex(): Int =
        if (shuffledIndices.isNotEmpty()) shuffledIndices.first() else C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val newIndices = IntArray(shuffledIndices.size + insertionCount)
        for (i in shuffledIndices.indices) {
            val item = shuffledIndices[i]
            newIndices[i] = if (item >= insertionIndex) item + insertionCount else item
        }
        for (k in 0 until insertionCount) {
            newIndices[shuffledIndices.size + k] = insertionIndex + k
        }
        return SmartShuffleOrder(newIndices)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val remaining = mutableListOf<Int>()
        for (item in shuffledIndices) {
            if (item in indexFrom until indexToExclusive) continue
            if (item >= indexToExclusive) {
                remaining.add(item - count)
            } else {
                remaining.add(item)
            }
        }
        return SmartShuffleOrder(remaining.toIntArray())
    }

    override fun cloneAndClear(): ShuffleOrder = SmartShuffleOrder(IntArray(0))
}
