package com.github.kotlintubeexplode.playlists

import com.github.kotlintubeexplode.common.IBatchItem
import com.github.kotlintubeexplode.common.IPlaylist
import com.github.kotlintubeexplode.common.IVideo
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Thumbnail
import kotlin.time.Duration

/**
 * Represents a YouTube playlist.
 */
data class Playlist(
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
     * May be null for auto-generated playlists (e.g., "Watch Later", "Liked Videos").
     */
    override val author: Author?,

    /**
     * The playlist description.
     */
    val description: String,

    /**
     * The total number of videos in the playlist.
     * May be null for infinite/auto-generated playlists.
     */
    val count: Int?,

    /**
     * Available thumbnail images for the playlist.
     */
    override val thumbnails: List<Thumbnail>
) : IPlaylist {
    /**
     * The full URL to this playlist on YouTube.
     */
    override val url: String get() = id.url
}

/**
 * Represents a video within a playlist.
 *
 * Contains additional context about the video's position in the playlist.
 */
data class PlaylistVideo(
    /**
     * The ID of the playlist this video belongs to.
     */
    val playlistId: PlaylistId,

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
    override val thumbnails: List<Thumbnail>,

    /**
     * The index/position of this video in the playlist (0-based).
     */
    val index: Int? = null
) : IBatchItem, IVideo {
    /**
     * The URL to this video within the playlist context.
     */
    override val url: String get() = "https://www.youtube.com/watch?v=${id.value}&list=${playlistId.value}"
}
