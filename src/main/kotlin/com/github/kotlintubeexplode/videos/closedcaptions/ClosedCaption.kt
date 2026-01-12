package com.github.kotlintubeexplode.videos.closedcaptions

import kotlin.time.Duration

/**
 * Individual closed caption contained within a track.
 *
 * @param text Text displayed by the caption
 * @param offset Time at which the caption starts displaying
 * @param duration Duration of time for which the caption is displayed
 * @param parts Caption parts, usually representing individual words. May be empty.
 */
data class ClosedCaption(
    val text: String,
    val offset: Duration,
    val duration: Duration,
    val parts: List<ClosedCaptionPart> = emptyList()
) {
    /**
     * Gets the caption part displayed at the specified point in time,
     * relative to the caption's own offset.
     *
     * @param time The time relative to the caption's offset
     * @return The caption part at that time, or null if not found
     */
    fun tryGetPartByTime(time: Duration): ClosedCaptionPart? =
        parts.firstOrNull { it.offset >= time }

    /**
     * Gets the caption part displayed at the specified point in time,
     * relative to the caption's own offset.
     *
     * @param time The time relative to the caption's offset
     * @return The caption part at that time
     * @throws NoSuchElementException if no part is found at the specified time
     */
    fun getPartByTime(time: Duration): ClosedCaptionPart =
        tryGetPartByTime(time)
            ?: throw NoSuchElementException("No closed caption part found at $time")

    override fun toString(): String = text
}
