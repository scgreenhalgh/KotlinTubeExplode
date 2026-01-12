package com.github.kotlintubeexplode.channels

import com.github.kotlintubeexplode.exceptions.ChannelUnavailableException
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.playlists.PlaylistClient
import com.github.kotlintubeexplode.playlists.PlaylistId
import com.github.kotlintubeexplode.playlists.PlaylistVideo
import com.github.kotlintubeexplode.common.Thumbnail
import kotlinx.coroutines.flow.Flow

/**
 * Client for retrieving YouTube channel information.
 */
class ChannelClient internal constructor(
    private val httpController: HttpController,
    private val playlistClient: PlaylistClient
) {
    companion object {
        // Special hardcoded channel for "Movies & TV"
        // This channel has a custom page that doesn't follow the standard format
        private const val MOVIES_TV_CHANNEL_ID = "UCuVPpxrm2VAgpH3Ktln4HXg"
        private const val MOVIES_TV_TITLE = "Movies & TV"
        private const val MOVIES_TV_THUMBNAIL_URL = "https://www.gstatic.com/youtube/img/tvfilm/clapperboard_profile.png"

        private val LOGO_SIZE_PATTERN = Regex("""\bs(\d+)\b""")
    }

    /**
     * Gets channel information by channel ID.
     *
     * @param channelId The channel ID
     * @return The channel information
     */
    suspend fun get(channelId: ChannelId): Channel {
        // Special case for Movies & TV channel which has a custom page
        if (channelId.value == MOVIES_TV_CHANNEL_ID) {
            return Channel(
                id = channelId,
                title = MOVIES_TV_TITLE,
                thumbnails = listOf(Thumbnail(MOVIES_TV_THUMBNAIL_URL, 1024, 1024))
            )
        }

        val channelUrl = "https://www.youtube.com/channel/${channelId.value}"
        val html = httpController.getWithRetry(channelUrl, maxRetries = 3)

        return parseChannelPage(channelId, html)
    }

    /**
     * Gets channel information by channel ID string or URL.
     */
    suspend fun get(channelIdOrUrl: String): Channel {
        return get(ChannelId.parse(channelIdOrUrl))
    }

    /**
     * Gets channel information by legacy user name.
     *
     * @param userName The user name
     * @return The channel information
     */
    suspend fun getByUser(userName: UserName): Channel {
        val url = "https://www.youtube.com/user/${userName.value}"
        val html = httpController.getWithRetry(url, maxRetries = 3)

        // Extract channel ID from the page
        val channelId = extractChannelIdFromPage(html)
            ?: throw ChannelUnavailableException("Could not find channel ID for user: ${userName.value}")

        return parseChannelPage(channelId, html)
    }

    /**
     * Gets channel information by legacy user name string or URL.
     */
    suspend fun getByUser(userNameOrUrl: String): Channel {
        return getByUser(UserName.parse(userNameOrUrl))
    }

    /**
     * Gets channel information by channel slug (custom URL).
     *
     * @param slug The channel slug
     * @return The channel information
     */
    suspend fun getBySlug(slug: ChannelSlug): Channel {
        val url = "https://www.youtube.com/c/${slug.value}"
        val html = httpController.getWithRetry(url, maxRetries = 3)

        val channelId = extractChannelIdFromPage(html)
            ?: throw ChannelUnavailableException("Could not find channel ID for slug: ${slug.value}")

        return parseChannelPage(channelId, html)
    }

    /**
     * Gets channel information by channel slug string or URL.
     */
    suspend fun getBySlug(slugOrUrl: String): Channel {
        return getBySlug(ChannelSlug.parse(slugOrUrl))
    }

    /**
     * Gets channel information by handle (@handle).
     *
     * @param handle The channel handle
     * @return The channel information
     */
    suspend fun getByHandle(handle: ChannelHandle): Channel {
        val url = "https://www.youtube.com/@${handle.value}"
        val html = httpController.getWithRetry(url, maxRetries = 3)

        val channelId = extractChannelIdFromPage(html)
            ?: throw ChannelUnavailableException("Could not find channel ID for handle: @${handle.value}")

        return parseChannelPage(channelId, html)
    }

    /**
     * Gets channel information by handle string or URL.
     */
    suspend fun getByHandle(handleOrUrl: String): Channel {
        return getByHandle(ChannelHandle.parse(handleOrUrl))
    }

    /**
     * Gets the uploads playlist videos for a channel.
     *
     * Every channel has an uploads playlist with ID "UU" + channel ID suffix.
     *
     * @param channelId The channel ID
     * @return Flow of uploaded videos
     */
    fun getUploads(channelId: ChannelId): Flow<PlaylistVideo> {
        // Convert channel ID to uploads playlist ID
        // Channel ID: UCxxxxx -> Uploads playlist: UUxxxxx
        val uploadsPlaylistId = PlaylistId("UU" + channelId.value.substring(2))
        return playlistClient.getVideos(uploadsPlaylistId)
    }

    /**
     * Gets the uploads playlist videos by channel ID string or URL.
     */
    fun getUploads(channelIdOrUrl: String): Flow<PlaylistVideo> {
        return getUploads(ChannelId.parse(channelIdOrUrl))
    }

    private fun parseChannelPage(channelId: ChannelId, html: String): Channel {
        val title = extractTitle(html) ?: "Unknown Channel"
        val logoUrl = extractLogoUrl(html)
        val thumbnails = if (logoUrl != null) {
            val size = extractLogoSize(logoUrl)
            listOf(Thumbnail(logoUrl, size, size))
        } else {
            emptyList()
        }

        return Channel(
            id = channelId,
            title = title,
            thumbnails = thumbnails
        )
    }

    private fun extractChannelIdFromPage(html: String): ChannelId? {
        // Try to find channel ID in various locations
        val patterns = listOf(
            Regex("""<meta\s+property="og:url"\s+content="https://www\.youtube\.com/channel/([^"]+)""""),
            Regex(""""channelId"\s*:\s*"([^"]+)""""),
            Regex("""youtube\.com/channel/([UC][a-zA-Z0-9_-]{22})""")
        )

        for (pattern in patterns) {
            pattern.find(html)?.groupValues?.get(1)?.let { id ->
                ChannelId.tryParse(id)?.let { return it }
            }
        }

        return null
    }

    private fun extractTitle(html: String): String? {
        // Try meta tag first
        Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)?.let {
            return it.groupValues[1]
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace(" - YouTube", "")
                .trim()
        }

        // Fallback to title tag
        Regex("""<title>([^<]+)</title>""").find(html)?.let {
            return it.groupValues[1]
                .replace(" - YouTube", "")
                .trim()
        }

        return null
    }

    private fun extractLogoUrl(html: String): String? {
        Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)?.let {
            return it.groupValues[1]
        }
        return null
    }

    private fun extractLogoSize(logoUrl: String): Int {
        LOGO_SIZE_PATTERN.find(logoUrl)?.groupValues?.get(1)?.toIntOrNull()?.let {
            return it
        }
        return 88 // Default avatar size
    }
}
