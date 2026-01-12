package com.github.kotlintubeexplode.common

import com.github.kotlintubeexplode.channels.ChannelId

/**
 * Properties shared by channel metadata resolved from different sources.
 *
 * This interface provides a common contract for channel data, whether it comes
 * from a full channel page, a search result, or other sources.
 */
interface IChannel {
    /**
     * Channel ID.
     */
    val id: ChannelId

    /**
     * Channel URL.
     */
    val url: String

    /**
     * Channel title/name.
     */
    val title: String

    /**
     * Channel thumbnails/avatar images.
     */
    val thumbnails: List<Thumbnail>
}
