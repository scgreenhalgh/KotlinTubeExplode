package com.github.kotlintubeexplode.channels

import com.github.kotlintubeexplode.exceptions.ChannelUnavailableException
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.parseXmlSecurely
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

        // Upstream ChannelController retries the fetch+parse when a channel page returns HTTP 200
        // but has no parseable og:url (a transient "broken page"). getWithRetry only covers
        // HTTP/network/5xx, so this handles the broken-200 case.
        private const val CHANNEL_PAGE_RETRIES = 5
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
    suspend fun getByUser(userName: UserName): Channel =
        resolveChannelFromPage(
            "https://www.youtube.com/user/${userName.value}",
            "Could not find channel ID for user: ${userName.value}"
        )

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
    suspend fun getBySlug(slug: ChannelSlug): Channel =
        resolveChannelFromPage(
            "https://www.youtube.com/c/${slug.value}",
            "Could not find channel ID for slug: ${slug.value}"
        )

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
    suspend fun getByHandle(handle: ChannelHandle): Channel =
        resolveChannelFromPage(
            "https://www.youtube.com/@${handle.value}",
            "Could not find channel ID for handle: @${handle.value}"
        )

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

    /**
     * Fetches a handle/slug/user channel page and resolves it to a [Channel], retrying when the
     * page comes back HTTP 200 but without a parseable og:url. Mirrors upstream ChannelController,
     * which retries the whole fetch+parse on a "broken page" (getWithRetry only retries
     * HTTP/network/5xx). After [CHANNEL_PAGE_RETRIES] attempts a still-broken page throws.
     */
    private suspend fun resolveChannelFromPage(url: String, notFoundMessage: String): Channel {
        repeat(CHANNEL_PAGE_RETRIES) {
            val html = httpController.getWithRetry(url, maxRetries = 3)
            val channelId = extractChannelIdFromPage(html)
            if (channelId != null) {
                return parseChannelPage(channelId, html)
            }
        }
        throw ChannelUnavailableException(notFoundMessage)
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
        // Order-agnostic og:url meta tag (DOM-parsed), mirroring upstream's AngleSharp read.
        parseMetaTags(html)["og:url"]
            ?.substringAfter("channel/", "")
            ?.let { ChannelId.tryParse(it) }
            ?.let { return it }

        // Non-meta fallbacks (unchanged): embedded channelId JSON, then any channel URL.
        val patterns = listOf(
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
        // Order-agnostic og:title meta tag (DOM-parsed).
        parseMetaTags(html)["og:title"]?.let {
            return decodeHtmlEntities(it)
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

    private fun extractLogoUrl(html: String): String? =
        parseMetaTags(html)["og:image"]

    private fun extractLogoSize(logoUrl: String): Int = parseLogoSize(logoUrl)
}

/**
 * Order-agnostic extraction of `<meta>` key→content pairs from channel-page HTML.
 *
 * Upstream reads og:url/og:title/og:image through AngleSharp's DOM, which doesn't care about
 * attribute order. Our old regex was order-locked (`property` before `content`), so a reordered
 * `<meta content="..." property="og:url">` slipped through. This slices each isolated meta tag,
 * normalizes it to well-formed self-closing XML, and hands it to the XXE-hardened parser, reading
 * `property` (or, as a fallback, `name`) as the key and `content` as the value via order-agnostic
 * getAttribute.
 *
 * A tag that isn't well-formed XML on its own (e.g. an unescaped `&` in an attribute value) is
 * skipped, matching the old regex's failure tolerance. Blank keys/values are skipped; first
 * occurrence of a key wins.
 */
internal fun parseMetaTags(html: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (match in Regex("""<meta\b[^>]*>""").findAll(html)) {
        try {
            val attrs = match.value.trim().removePrefix("<meta").removeSuffix(">").trim().removeSuffix("/").trim()
            val document = parseXmlSecurely("<meta $attrs/>")
            val el = document.documentElement
            val key = el.getAttribute("property").ifBlank { el.getAttribute("name") }
            val content = el.getAttribute("content")
            if (key.isBlank() || content.isBlank()) continue
            result.putIfAbsent(key, content)
        } catch (e: Exception) {
            // Malformed meta tag (e.g. an unescaped '&' in an attribute value); skip like the old regex.
        }
    }
    return result
}

/**
 * Parses the avatar size out of a YouTube channel logo URL.
 *
 * Channel logo URLs commonly contain multiple `\bs(\d+)\b` tokens — typically a
 * crop coordinate followed by the actual size. Upstream picks the LAST match
 * (the size). Default of 100 matches upstream's `Thumbnail.GetDefaultSet`.
 */
internal fun parseLogoSize(logoUrl: String): Int {
    val pattern = Regex("""\bs(\d+)\b""")
    return pattern.findAll(logoUrl).lastOrNull()
        ?.groupValues?.get(1)
        ?.toIntOrNull()
        ?: 100
}

/**
 * Decodes HTML character entities in text pulled from channel page metadata
 * (e.g. the raw `og:title` attribute).
 *
 * Handles the common named entities plus numeric references — decimal `&#nn;` and
 * hex `&#xhh;`, including code points above U+FFFF (emitted as surrogate pairs).
 * Upstream reads the title through a full HTML parser (AngleSharp), which decodes the
 * complete entity table; this covers the cases that realistically show up in a channel
 * title. Decoding is a single left-to-right pass, so produced output is never
 * re-interpreted (e.g. `&amp;#39;` stays `&#39;`), matching WebUtility.HtmlDecode.
 * Unrecognized or malformed entities are left verbatim.
 */
internal fun decodeHtmlEntities(text: String): String {
    if (text.indexOf('&') < 0) return text

    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c != '&') {
            sb.append(c)
            i++
            continue
        }

        val semicolon = text.indexOf(';', i + 1)
        // Entity bodies are short; a distant (or missing) ';' means this '&' is literal.
        if (semicolon < 0 || semicolon - i > MAX_HTML_ENTITY_LENGTH) {
            sb.append(c)
            i++
            continue
        }

        val decoded = decodeHtmlEntityBody(text.substring(i + 1, semicolon))
        if (decoded != null) {
            sb.append(decoded)
            i = semicolon + 1
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private const val MAX_HTML_ENTITY_LENGTH = 32

private fun decodeHtmlEntityBody(body: String): String? {
    if (body.isEmpty()) return null

    if (body[0] == '#') {
        if (body.length < 2) return null
        val codePoint = if (body[1] == 'x' || body[1] == 'X') {
            body.substring(2).toIntOrNull(16)
        } else {
            body.substring(1).toIntOrNull()
        } ?: return null

        if (!Character.isValidCodePoint(codePoint)) return null
        return String(Character.toChars(codePoint))
    }

    return NAMED_HTML_ENTITIES[body]
}

private val NAMED_HTML_ENTITIES: Map<String, String> = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "hellip" to "…",
    "mdash" to "—",
    "ndash" to "–",
    "lsquo" to "‘",
    "rsquo" to "’",
    "ldquo" to "“",
    "rdquo" to "”",
    "laquo" to "«",
    "raquo" to "»",
    "middot" to "·",
    "bull" to "•",
    "deg" to "°",
    "times" to "×",
    "divide" to "÷",
    "hearts" to "♥",
    "euro" to "€",
    "pound" to "£",
    "cent" to "¢",
    "yen" to "¥",
    "sect" to "§",
    "para" to "¶",
    "plusmn" to "±",
    "frac12" to "½",
    "frac14" to "¼",
    "frac34" to "¾"
)
