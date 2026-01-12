package com.github.kotlintubeexplode.common

/**
 * Represents an image thumbnail.
 */
data class Thumbnail(
    /**
     * The URL to the thumbnail image.
     */
    val url: String,

    /**
     * The thumbnail width in pixels.
     */
    val width: Int,

    /**
     * The thumbnail height in pixels.
     */
    val height: Int
) {
    /**
     * The resolution (width x height).
     */
    val resolution: Resolution
        get() = Resolution(width, height)
}

/**
 * Extension function to get the thumbnail with the highest resolution.
 */
fun List<Thumbnail>.getWithHighestResolution(): Thumbnail? =
    maxByOrNull { it.width * it.height }

/**
 * Extension function to get the thumbnail with the lowest resolution.
 */
fun List<Thumbnail>.getWithLowestResolution(): Thumbnail? =
    minByOrNull { it.width * it.height }

/**
 * Extension function to find a thumbnail closest to the target resolution.
 */
fun List<Thumbnail>.getClosestTo(targetWidth: Int, targetHeight: Int): Thumbnail? {
    val targetArea = targetWidth * targetHeight
    return minByOrNull { kotlin.math.abs(it.width * it.height - targetArea) }
}
