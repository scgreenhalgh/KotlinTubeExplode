package com.github.kotlintubeexplode.common

/**
 * Marker interface for items that can be returned in batches.
 *
 * Used for pagination support in playlist videos, search results, etc.
 */
interface IBatchItem

/**
 * Represents a batch of items from a paginated API response.
 *
 * @param T The type of items in the batch
 * @param items The list of items in this batch
 */
data class Batch<T : IBatchItem>(
    val items: List<T>
) {
    /**
     * Returns true if this batch contains no items.
     */
    val isEmpty: Boolean get() = items.isEmpty()

    /**
     * Returns the number of items in this batch.
     */
    val size: Int get() = items.size
}

/**
 * Extension function to flatten a sequence of batches into individual items.
 */
fun <T : IBatchItem> Sequence<Batch<T>>.flatten(): Sequence<T> =
    flatMap { it.items.asSequence() }

/**
 * Extension function to collect items from a sequence into a list.
 */
suspend fun <T : IBatchItem> Sequence<T>.collectAsync(): List<T> = toList()

/**
 * Extension function to collect up to [count] items from a sequence.
 */
suspend fun <T : IBatchItem> Sequence<T>.collectAsync(count: Int): List<T> =
    take(count).toList()
