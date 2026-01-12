package com.github.kotlintubeexplode.videos

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.VideoController

/**
 * Client for retrieving YouTube video information.
 */
class VideoClient internal constructor(
    private val videoController: VideoController
) {
    /**
     * Gets video metadata.
     *
     * @param videoId The video ID
     * @return The video metadata
     */
    suspend fun get(videoId: VideoId): Video {
        return videoController.getVideo(videoId)
    }

    /**
     * Gets video metadata by URL or ID string.
     *
     * @param videoIdOrUrl The video URL or ID. Supports:
     *   - Raw video IDs: `dQw4w9WgXcQ`
     *   - Full URLs: `https://www.youtube.com/watch?v=dQw4w9WgXcQ`
     *   - Short URLs: `https://youtu.be/dQw4w9WgXcQ`
     *   - Embed URLs: `https://www.youtube.com/embed/dQw4w9WgXcQ`
     *   - Shorts URLs: `https://www.youtube.com/shorts/dQw4w9WgXcQ`
     *
     * @return The video metadata
     */
    suspend fun get(videoIdOrUrl: String): Video {
        return get(VideoId.parse(videoIdOrUrl))
    }

    /**
     * Checks if a video exists and is available.
     *
     * @param videoId The video ID
     * @return true if the video exists and is available
     */
    suspend fun isAvailable(videoId: VideoId): Boolean {
        return try {
            get(videoId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a video exists and is available.
     *
     * @param videoIdOrUrl The video URL or ID
     * @return true if the video exists and is available
     */
    suspend fun isAvailable(videoIdOrUrl: String): Boolean {
        return try {
            get(videoIdOrUrl)
            true
        } catch (e: Exception) {
            false
        }
    }
}
