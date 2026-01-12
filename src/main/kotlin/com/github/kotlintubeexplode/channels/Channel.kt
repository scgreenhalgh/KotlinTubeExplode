package com.github.kotlintubeexplode.channels

import com.github.kotlintubeexplode.common.IChannel
import com.github.kotlintubeexplode.common.Thumbnail

/**
 * Represents a YouTube channel.
 */
data class Channel(
    /**
     * The channel's unique identifier.
     */
    override val id: ChannelId,

    /**
     * The channel title/name.
     */
    override val title: String,

    /**
     * Available thumbnail/logo images for the channel.
     */
    override val thumbnails: List<Thumbnail>
) : IChannel {
    /**
     * The full URL to this channel on YouTube.
     */
    override val url: String get() = id.url
}
