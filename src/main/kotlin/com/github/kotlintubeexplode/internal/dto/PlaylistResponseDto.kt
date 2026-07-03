package com.github.kotlintubeexplode.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

    val title: String? get() = playlistRoot?.titleText

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
    @SerialName("title") val title: JsonElement? = null,
    @SerialName("ownerName") val ownerName: TextRunsDto? = null,
    @SerialName("contents") val contents: List<PlaylistPanelEntryDto> = emptyList()
) {
    /**
     * Panel title read defensively. YouTube normally sends a bare string here, but has a long
     * history of migrating string fields to {simpleText}/{runs} objects. Reading it as a raw
     * JsonElement keeps an unexpected shape from failing deserialization of the entire Next
     * response — which would wipe out every video in the batch. Mirrors upstream's GetStringOrNull
     * tolerance (null on non-string), and goes one step further by extracting simpleText/runs.
     */
    val titleText: String?
        get() = when (val t = title) {
            null -> null
            is JsonPrimitive -> if (t.isString) t.contentOrNull else null
            is JsonObject ->
                (t["simpleText"] as? JsonPrimitive)?.contentOrNull
                    ?: (t["runs"] as? JsonArray)
                        ?.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }
                        ?.joinToString("")
                        ?.takeIf { it.isNotEmpty() }
            else -> null
        }
}

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
        get() {
            // Primary path (single-author videos): first byline run -> browseEndpoint.browseId.
            authorRun?.navigationEndpoint?.browseEndpoint?.browseId?.let { return it }
            // Fallback (multi-author videos, e.g. music tracks with featured artists): the uploader
            // channel link is hidden behind a "..." dialog instead of a plain browseEndpoint. Mirror
            // upstream PlaylistVideoData.ChannelId's second branch: dialog view-model -> first list
            // item -> onTap -> browseId. Fully null-guarded; if the shape shifts this returns null
            // and the caller's skip-on-missing (PlaylistClient) still applies (no throw).
            var current: JsonElement? = authorRun?.navigationEndpoint?.showDialogCommand
            for (key in listOf(
                "panelLoadingStrategy", "inlineContent", "dialogViewModel",
                "customContent", "listViewModel", "listItems", "0",
                "listItemViewModel", "rendererContext", "commandContext",
                "onTap", "innertubeCommand", "browseEndpoint", "browseId"
            )) {
                current = when (val c = current) {
                    is JsonObject -> c[key]
                    is JsonArray -> key.toIntOrNull()?.let { c.getOrNull(it) }
                    else -> null
                }
                if (current == null) return null
            }
            // contentOrNull (not content): an explicit JSON null lands here as JsonNull, which is
            // a JsonPrimitive whose .content is the literal string "null". contentOrNull yields null.
            return (current as? JsonPrimitive)?.contentOrNull
        }

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
    val watchEndpoint: WatchEndpointDto? = null,

    @SerialName("showDialogCommand")
    val showDialogCommand: JsonElement? = null
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
