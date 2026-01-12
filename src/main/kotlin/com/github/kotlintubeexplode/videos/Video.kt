package com.github.kotlintubeexplode.videos

import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.IVideo
import com.github.kotlintubeexplode.common.Resolution
import com.github.kotlintubeexplode.common.Thumbnail
import com.github.kotlintubeexplode.core.VideoId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Represents a YouTube video with its metadata.
 *
 * This is the main domain model returned by [YoutubeClient.getVideo].
 */
data class Video(
    /**
     * The video's unique identifier.
     */
    override val id: VideoId,

    /**
     * The video title.
     */
    override val title: String,

    /**
     * The channel/author information.
     */
    override val author: Author,

    /**
     * The date when the video was uploaded.
     * Format: ISO 8601 date string (e.g., "2023-01-15")
     */
    val uploadDate: String?,

    /**
     * The video description.
     */
    val description: String,

    /**
     * The video duration, or null for live streams.
     */
    override val duration: Duration?,

    /**
     * Available thumbnail images in various resolutions.
     */
    override val thumbnails: List<Thumbnail>,

    /**
     * Keywords/tags associated with the video.
     */
    val keywords: List<String>,

    /**
     * Engagement metrics (view count, likes, etc.).
     */
    val engagement: Engagement
) : IVideo {
    /**
     * The full URL to this video on YouTube.
     */
    override val url: String
        get() = "https://www.youtube.com/watch?v=${id.value}"

    /**
     * Returns true if this is a live stream.
     */
    val isLiveStream: Boolean
        get() = duration == null

    companion object {
        /**
         * Constructs a Video from duration in seconds.
         */
        fun create(
            id: VideoId,
            title: String,
            author: Author,
            uploadDate: String?,
            description: String,
            durationSeconds: Long?,
            thumbnails: List<Thumbnail>,
            keywords: List<String>,
            engagement: Engagement
        ): Video = Video(
            id = id,
            title = title,
            author = author,
            uploadDate = uploadDate,
            description = description,
            duration = durationSeconds?.seconds,
            thumbnails = thumbnails,
            keywords = keywords,
            engagement = engagement
        )
    }
}

/**
 * Engagement metrics for a video.
 */
data class Engagement(
    /**
     * The total view count.
     */
    val viewCount: Long,

    /**
     * The like count.
     */
    val likeCount: Long,

    /**
     * The dislike count (usually 0 as YouTube hides dislikes).
     */
    val dislikeCount: Long = 0
) {
    /**
     * Calculates the average rating (1.0 to 5.0).
     *
     * Uses the formula: 1 + 4 * likes / (likes + dislikes)
     * Returns 0.0 if there are no ratings.
     */
    val averageRating: Double
        get() {
            val total = likeCount + dislikeCount
            return if (total > 0) 1.0 + 4.0 * likeCount / total else 0.0
        }
}
