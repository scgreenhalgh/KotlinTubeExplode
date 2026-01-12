package com.github.kotlintubeexplode.common

import com.github.kotlintubeexplode.playlists.PlaylistId

/**
 * Properties shared by playlist metadata resolved from different sources.
 *
 * This interface provides a common contract for playlist data, whether it comes
 * from a full playlist page, a search result, or other sources.
 */
interface IPlaylist {
    /**
     * Playlist ID.
     */
    val id: PlaylistId

    /**
     * Playlist URL.
     */
    val url: String

    /**
     * Playlist title.
     */
    val title: String

    /**
     * Playlist author/channel.
     *
     * May be null in case of auto-generated playlists (e.g. mixes, topics, etc).
     */
    val author: Author?

    /**
     * Playlist thumbnails.
     */
    val thumbnails: List<Thumbnail>
}
