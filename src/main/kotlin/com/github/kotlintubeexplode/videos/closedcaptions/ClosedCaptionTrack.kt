package com.github.kotlintubeexplode.videos.closedcaptions

import kotlin.time.Duration

/**
 * Contains closed captions in a specific language.
 *
 * @param captions Closed captions included in the track
 */
data class ClosedCaptionTrack(
    val captions: List<ClosedCaption>
) {
    /**
     * Gets the caption displayed at the specified point in time.
     *
     * @param time The absolute time in the video
     * @return The caption at that time, or null if not found
     */
    fun tryGetByTime(time: Duration): ClosedCaption? =
        captions.firstOrNull { caption ->
            time >= caption.offset && time <= caption.offset + caption.duration
        }

    /**
     * Gets the caption displayed at the specified point in time.
     *
     * @param time The absolute time in the video
     * @return The caption at that time
     * @throws NoSuchElementException if no caption is found at the specified time
     */
    fun getByTime(time: Duration): ClosedCaption =
        tryGetByTime(time)
            ?: throw NoSuchElementException("No closed caption found at $time")

    /**
     * Returns the number of captions in this track.
     */
    val size: Int get() = captions.size

    /**
     * Returns true if this track has no captions.
     */
    val isEmpty: Boolean get() = captions.isEmpty()
}
