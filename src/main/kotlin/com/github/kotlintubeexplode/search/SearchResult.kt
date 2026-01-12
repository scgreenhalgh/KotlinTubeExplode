package com.github.kotlintubeexplode.search

import com.github.kotlintubeexplode.channels.ChannelId
import com.github.kotlintubeexplode.common.IBatchItem
import com.github.kotlintubeexplode.common.IChannel
import com.github.kotlintubeexplode.common.IPlaylist
import com.github.kotlintubeexplode.common.IVideo
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.playlists.PlaylistId
import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Thumbnail
import kotlin.time.Duration

/**
 * Filter for search results.
 */
enum class SearchFilter {
    /** No filter - return all result types. */
    None,

    /** Only return video results. */
    Video,

    /** Only return playlist results. */
    Playlist,

    /** Only return channel results. */
    Channel
}

/**
 * Base interface for all search result types.
 */
sealed interface ISearchResult : IBatchItem {
    /** The URL to the result on YouTube. */
    val url: String

    /** The title of the result. */
    val title: String
}

/**
 * Represents a video in search results.
 */
data class VideoSearchResult(
    /**
     * The video's unique identifier.
     */
    override val id: VideoId,

    /**
     * The video title.
     */
    override val title: String,

    /**
     * The video author/channel.
     */
    override val author: Author,

    /**
     * The video duration, or null for live streams.
     */
    override val duration: Duration?,

    /**
     * Available thumbnail images for the video.
     */
    override val thumbnails: List<Thumbnail>
) : ISearchResult, IVideo {
    override val url: String get() = "https://www.youtube.com/watch?v=${id.value}"
}

/**
 * Represents a playlist in search results.
 */
data class PlaylistSearchResult(
    /**
     * The playlist's unique identifier.
     */
    override val id: PlaylistId,

    /**
     * The playlist title.
     */
    override val title: String,

    /**
     * The playlist author/creator.
     * May be null for auto-generated playlists.
     */
    override val author: Author?,

    /**
     * Available thumbnail images for the playlist.
     */
    override val thumbnails: List<Thumbnail>
) : ISearchResult, IPlaylist {
    override val url: String get() = id.url
}

/**
 * Represents a channel in search results.
 */
data class ChannelSearchResult(
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
) : ISearchResult, IChannel {
    override val url: String get() = id.url
}
