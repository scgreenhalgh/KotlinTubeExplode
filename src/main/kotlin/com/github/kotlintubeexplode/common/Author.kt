package com.github.kotlintubeexplode.common

/**
 * Represents a YouTube channel/author.
 */
data class Author(
    /**
     * The channel's unique identifier.
     */
    val channelId: String,

    /**
     * The channel's display name.
     */
    val channelTitle: String
) {
    /**
     * The full URL to this channel on YouTube.
     */
    val channelUrl: String
        get() = "https://www.youtube.com/channel/$channelId"

    /**
     * Alias for channelTitle for compatibility.
     */
    @Deprecated("Use channelTitle instead", ReplaceWith("channelTitle"))
    val title: String
        get() = channelTitle
}
