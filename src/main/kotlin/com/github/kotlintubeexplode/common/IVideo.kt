package com.github.kotlintubeexplode.common

import com.github.kotlintubeexplode.core.VideoId
import kotlin.time.Duration

/**
 * Properties shared by video metadata resolved from different sources.
 *
 * This interface provides a common contract for video data, whether it comes
 * from a full video page, a playlist entry, a search result, or other sources.
 */
interface IVideo {
    /**
     * Video ID.
     */
    val id: VideoId

    /**
     * Video URL.
     */
    val url: String

    /**
     * Video title.
     */
    val title: String

    /**
     * Video author/channel.
     */
    val author: Author

    /**
     * Video duration.
     *
     * May be null if the video is a currently ongoing live stream.
     */
    val duration: Duration?

    /**
     * Video thumbnails.
     */
    val thumbnails: List<Thumbnail>
}
