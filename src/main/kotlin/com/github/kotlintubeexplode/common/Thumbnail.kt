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

    companion object {
        /**
         * The default thumbnail set YouTube guarantees for any video, derived from the
         * video ID alone. Appended to API-provided thumbnails so callers always have at
         * least low/medium/high resolution images. Mirrors upstream YoutubeExplode's
         * internal `Thumbnail.GetDefaultSet`.
         */
        internal fun getDefaultSet(videoId: String): List<Thumbnail> = listOf(
            Thumbnail("https://img.youtube.com/vi/$videoId/default.jpg", 120, 90),
            Thumbnail("https://img.youtube.com/vi/$videoId/mqdefault.jpg", 320, 180),
            Thumbnail("https://img.youtube.com/vi/$videoId/hqdefault.jpg", 480, 360)
        )
    }
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
