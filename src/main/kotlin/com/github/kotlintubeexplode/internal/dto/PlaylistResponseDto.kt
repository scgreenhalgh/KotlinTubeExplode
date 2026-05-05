package com.github.kotlintubeexplode.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * DTO for playlist browse API response.
 */
@Serializable
data class PlaylistBrowseResponseDto(
    @SerialName("header")
    val header: JsonObject? = null,

    @SerialName("sidebar")
    val sidebar: JsonObject? = null,

    @SerialName("contents")
    val contents: JsonObject? = null,

    @SerialName("alerts")
    val alerts: List<JsonObject>? = null
)

/**
 * DTO for the `youtubei/v1/next` API response, used for playlist video pagination.
 *
 * Path of interest:
 *   contents.twoColumnWatchNextResults.playlist.playlist.contents[].playlistPanelVideoRenderer
 *   responseContext.visitorData
 */
@Serializable
data class PlaylistNextResponseDto(
    @SerialName("contents") val contents: NextContentsDto? = null,
    @SerialName("responseContext") val responseContext: NextResponseContextDto? = null
) {
    val visitorData: String? get() = responseContext?.visitorData

    private val playlistRoot: PlaylistPanelDto?
        get() = contents?.twoColumnWatchNextResults?.playlist?.playlist

    val isAvailable: Boolean get() = playlistRoot != null

    val title: String? get() = playlistRoot?.title

    val authorName: String? get() = playlistRoot?.ownerName?.simpleText

    val videos: List<PlaylistPanelVideoRendererDto>
        get() = playlistRoot?.contents?.mapNotNull { it.playlistPanelVideoRenderer }
            ?: emptyList()
}

@Serializable
data class NextContentsDto(
    @SerialName("twoColumnWatchNextResults")
    val twoColumnWatchNextResults: TwoColumnWatchNextResultsDto? = null
)

@Serializable
data class TwoColumnWatchNextResultsDto(
    @SerialName("playlist") val playlist: NextPlaylistContainerDto? = null
)

@Serializable
data class NextPlaylistContainerDto(
    @SerialName("playlist") val playlist: PlaylistPanelDto? = null
)

@Serializable
data class PlaylistPanelDto(
    @SerialName("title") val title: String? = null,
    @SerialName("ownerName") val ownerName: TextRunsDto? = null,
    @SerialName("contents") val contents: List<PlaylistPanelEntryDto> = emptyList()
)

@Serializable
data class PlaylistPanelEntryDto(
    @SerialName("playlistPanelVideoRenderer")
    val playlistPanelVideoRenderer: PlaylistPanelVideoRendererDto? = null
)

@Serializable
data class NextResponseContextDto(
    @SerialName("visitorData") val visitorData: String? = null
)

/**
 * Individual video within a Next-API playlist panel response.
 * Distinct shape from browse-endpoint `playlistVideoRenderer`:
 *   - index lives at `navigationEndpoint.watchEndpoint.index`, not as a top-level field
 *   - prefers `longBylineText` over `shortBylineText` for author info
 */
@Serializable
data class PlaylistPanelVideoRendererDto(
    @SerialName("videoId") val videoId: String? = null,
    @SerialName("title") val title: TextRunsDto? = null,
    @SerialName("longBylineText") val longBylineText: TextRunsDto? = null,
    @SerialName("shortBylineText") val shortBylineText: TextRunsDto? = null,
    @SerialName("lengthSeconds") val lengthSeconds: String? = null,
    @SerialName("lengthText") val lengthText: TextRunsDto? = null,
    @SerialName("thumbnail") val thumbnail: ThumbnailContainerDto? = null,
    @SerialName("navigationEndpoint") val navigationEndpoint: NavigationEndpointDto? = null
) {
    val titleText: String? get() = title?.text

    private val authorRun: TextRunDto?
        get() = longBylineText?.runs?.firstOrNull()
            ?: shortBylineText?.runs?.firstOrNull()

    val authorName: String? get() = authorRun?.text

    val authorChannelId: String?
        get() = authorRun?.navigationEndpoint?.browseEndpoint?.browseId

    val index: Int? get() = navigationEndpoint?.watchEndpoint?.index

    val durationSeconds: Long?
        get() {
            // Match upstream's double.ParseOrNull tolerance for fractional seconds (e.g., "8.5").
            // We truncate to Long since our public type is integer seconds; upstream uses TimeSpan
            // which preserves fractional, but for video duration this loses sub-second precision
            // that's not consumer-relevant.
            lengthSeconds?.toDoubleOrNull()?.toLong()?.let { return it }
            val text = lengthText?.text ?: return null
            return parseDuration(text)
        }

    private fun parseDuration(text: String): Long? {
        val parts = text.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
        }
    }
}

/**
 * Common DTO for text runs pattern used throughout YouTube API.
 */
@Serializable
data class TextRunsDto(
    @SerialName("simpleText")
    val simpleText: String? = null,

    @SerialName("runs")
    val runs: List<TextRunDto>? = null
) {
    val text: String?
        get() = simpleText ?: runs?.joinToString("") { it.text ?: "" }?.takeIf { it.isNotEmpty() }
}

@Serializable
data class TextRunDto(
    @SerialName("text")
    val text: String? = null,

    @SerialName("navigationEndpoint")
    val navigationEndpoint: NavigationEndpointDto? = null
)

@Serializable
data class NavigationEndpointDto(
    @SerialName("browseEndpoint")
    val browseEndpoint: BrowseEndpointDto? = null,

    @SerialName("watchEndpoint")
    val watchEndpoint: WatchEndpointDto? = null
)

@Serializable
data class BrowseEndpointDto(
    @SerialName("browseId")
    val browseId: String? = null,

    @SerialName("canonicalBaseUrl")
    val canonicalBaseUrl: String? = null
)

@Serializable
data class WatchEndpointDto(
    @SerialName("videoId")
    val videoId: String? = null,

    @SerialName("playlistId")
    val playlistId: String? = null,

    @SerialName("index")
    val index: Int? = null
)
