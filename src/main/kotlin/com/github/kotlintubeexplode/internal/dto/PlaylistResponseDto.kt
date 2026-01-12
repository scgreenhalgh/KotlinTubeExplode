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
 * DTO for playlist continuation/next API response (pagination).
 */
@Serializable
data class PlaylistNextResponseDto(
    @SerialName("contents")
    val contents: JsonObject? = null,

    @SerialName("currentVideoEndpoint")
    val currentVideoEndpoint: JsonObject? = null,

    @SerialName("trackingParams")
    val trackingParams: String? = null
)

/**
 * DTO for a video within a playlist.
 */
@Serializable
data class PlaylistVideoDto(
    @SerialName("videoId")
    val videoId: String? = null,

    @SerialName("title")
    val title: TextRunsDto? = null,

    @SerialName("index")
    val index: TextRunsDto? = null,

    @SerialName("shortBylineText")
    val shortBylineText: TextRunsDto? = null,

    @SerialName("lengthText")
    val lengthText: TextRunsDto? = null,

    @SerialName("lengthSeconds")
    val lengthSeconds: String? = null,

    @SerialName("thumbnail")
    val thumbnail: ThumbnailContainerDto? = null,

    @SerialName("navigationEndpoint")
    val navigationEndpoint: JsonObject? = null,

    @SerialName("isPlayable")
    val isPlayable: Boolean? = null
) {
    /**
     * Extracts the video title as a plain string.
     */
    val titleText: String?
        get() = title?.simpleText ?: title?.runs?.firstOrNull()?.text

    /**
     * Extracts the channel name.
     */
    val channelName: String?
        get() = shortBylineText?.runs?.firstOrNull()?.text

    /**
     * Extracts the channel ID from navigation endpoint.
     */
    val channelId: String?
        get() = shortBylineText?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId

    /**
     * Parses the duration in seconds.
     */
    val durationSeconds: Long?
        get() {
            // Try lengthSeconds first
            lengthSeconds?.toLongOrNull()?.let { return it }

            // Parse from lengthText (format: "3:45" or "1:23:45")
            val text = lengthText?.simpleText ?: lengthText?.runs?.firstOrNull()?.text ?: return null
            return parseDuration(text)
        }

    /**
     * Extracts the playlist index.
     */
    val indexValue: Int?
        get() {
            val text = index?.simpleText ?: index?.runs?.firstOrNull()?.text ?: return null
            return text.filter { it.isDigit() }.toIntOrNull()
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
