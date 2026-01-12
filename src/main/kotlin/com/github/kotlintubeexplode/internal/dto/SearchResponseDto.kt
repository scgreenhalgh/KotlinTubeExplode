package com.github.kotlintubeexplode.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTO for YouTube search API response.
 */
@Serializable
data class SearchResponseDto(
    @SerialName("contents")
    val contents: JsonObject? = null,

    @SerialName("onResponseReceivedCommands")
    val onResponseReceivedCommands: List<JsonObject>? = null,

    @SerialName("estimatedResults")
    val estimatedResults: String? = null
)

/**
 * DTO for a video in search results.
 */
@Serializable
data class SearchVideoDto(
    @SerialName("videoId")
    val videoId: String? = null,

    @SerialName("title")
    val title: TextRunsDto? = null,

    @SerialName("ownerText")
    val ownerText: TextRunsDto? = null,

    @SerialName("shortBylineText")
    val shortBylineText: TextRunsDto? = null,

    @SerialName("longBylineText")
    val longBylineText: TextRunsDto? = null,

    @SerialName("lengthText")
    val lengthText: TextRunsDto? = null,

    @SerialName("viewCountText")
    val viewCountText: TextRunsDto? = null,

    @SerialName("thumbnail")
    val thumbnail: ThumbnailContainerDto? = null,

    @SerialName("channelThumbnailSupportedRenderers")
    val channelThumbnailSupportedRenderers: JsonObject? = null
) {
    val titleText: String?
        get() = title?.text

    val channelName: String?
        get() = ownerText?.text ?: shortBylineText?.text ?: longBylineText?.text

    val channelId: String?
        get() = ownerText?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
            ?: shortBylineText?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
            ?: longBylineText?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId

    val durationSeconds: Long?
        get() {
            val text = lengthText?.text ?: return null
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
 * DTO for a playlist in search results.
 */
@Serializable
data class SearchPlaylistDto(
    @SerialName("playlistId")
    val playlistId: String? = null,

    @SerialName("title")
    val title: TextRunsDto? = null,

    @SerialName("shortBylineText")
    val shortBylineText: TextRunsDto? = null,

    @SerialName("longBylineText")
    val longBylineText: TextRunsDto? = null,

    @SerialName("thumbnails")
    val thumbnails: List<ThumbnailContainerDto>? = null,

    @SerialName("thumbnail")
    val thumbnail: ThumbnailContainerDto? = null,

    @SerialName("videoCount")
    val videoCount: String? = null,

    @SerialName("videoCountText")
    val videoCountText: TextRunsDto? = null
) {
    val titleText: String?
        get() = title?.text

    val channelName: String?
        get() = shortBylineText?.text ?: longBylineText?.text

    val channelId: String?
        get() = shortBylineText?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
            ?: longBylineText?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId

    val allThumbnails: List<ThumbnailDto>
        get() = thumbnail?.thumbnails.orEmpty() + thumbnails?.flatMap { it.thumbnails.orEmpty() }.orEmpty()
}

/**
 * DTO for a channel in search results.
 */
@Serializable
data class SearchChannelDto(
    @SerialName("channelId")
    val channelId: String? = null,

    @SerialName("title")
    val title: TextRunsDto? = null,

    @SerialName("thumbnail")
    val thumbnail: ThumbnailContainerDto? = null,

    @SerialName("descriptionSnippet")
    val descriptionSnippet: TextRunsDto? = null,

    @SerialName("subscriberCountText")
    val subscriberCountText: TextRunsDto? = null,

    @SerialName("videoCountText")
    val videoCountText: TextRunsDto? = null
) {
    val titleText: String?
        get() = title?.text
}
