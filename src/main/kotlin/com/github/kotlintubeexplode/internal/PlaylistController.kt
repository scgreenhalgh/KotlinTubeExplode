package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.exceptions.PlaylistUnavailableException
import com.github.kotlintubeexplode.internal.dto.PlaylistNextResponseDto
import com.github.kotlintubeexplode.playlists.PlaylistId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Encapsulates HTTP traffic for playlist endpoints.
 *
 * Mirrors upstream `Playlists/PlaylistController.cs` separation:
 * - Browse endpoint for metadata on user-made playlists.
 * - Next endpoint for video pagination on all playlist types (including Mix /
 *   `RD…` auto-generated playlists, which the browse endpoint doesn't return).
 */
internal class PlaylistController(
    private val httpController: HttpController
) {
    companion object {
        private const val BROWSE_URL = "https://www.youtube.com/youtubei/v1/browse"
        private const val NEXT_URL = "https://www.youtube.com/youtubei/v1/next"
        private const val PLAYLIST_PAGE_URL_TEMPLATE = "https://youtube.com/playlist?list=%s"
        private const val NEXT_RETRIES_COUNT = 5

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /**
     * Fetches the browse-endpoint response for a playlist.
     * Used for metadata (title, author, description, etc.) on user-made playlists.
     * Does not work for Mix playlists (`RD…`) or some system playlists.
     */
    suspend fun getPlaylistBrowseResponse(playlistId: PlaylistId): JsonObject {
        val body = buildJsonObject {
            put("browseId", "VL${playlistId.value}")
            put("context", buildClientContext())
        }

        val responseText = httpController.postJson(BROWSE_URL, body.toString())
        return json.parseToJsonElement(responseText) as JsonObject
    }

    /**
     * Fetches the next-endpoint response for a playlist, used for video pagination.
     * Implements upstream YoutubeExplode's retry/warmup behavior:
     * - Cold-start warmup: open the playlist page once if the first request returns
     *   `IsAvailable=false` (some system playlists need this to materialize).
     * - Transient retry: up to 4 retries on subsequent unavailable responses if we've
     *   already paginated successfully (visitorData is set).
     * - Stop-on-unavailable: if retries exhaust but the response still has videos,
     *   return the partial response anyway. Caller decides whether to continue.
     */
    suspend fun getPlaylistNextResponse(
        playlistId: PlaylistId,
        videoId: String? = null,
        index: Int = 0,
        visitorData: String? = null
    ): PlaylistNextResponseDto {
        var retriesRemaining = NEXT_RETRIES_COUNT
        while (true) {
            val body = buildNextRequestBody(playlistId, videoId, index, visitorData)
            val responseText = httpController.postJson(NEXT_URL, body.toString())
            val response = json.decodeFromString(PlaylistNextResponseDto.serializer(), responseText)

            if (response.isAvailable) return response

            // First request, no visitor data, no retries used yet → try warming up the playlist.
            // Some system playlists don't materialize until someone opens the watch page.
            if (index <= 0 && visitorData.isNullOrBlank() && retriesRemaining >= NEXT_RETRIES_COUNT) {
                runCatching {
                    httpController.get(PLAYLIST_PAGE_URL_TEMPLATE.format(playlistId.value))
                }
                retriesRemaining--
                continue
            }

            // Mid-iteration unavailability: previous request succeeded (visitorData set), so this
            // is most likely transient. Retry until we run out of attempts.
            if (index > 0 && !visitorData.isNullOrBlank() && retriesRemaining > 0) {
                retriesRemaining--
                continue
            }

            // Retries exhausted. If the response still has videos, return it — the playlist itself
            // may be available even though IsAvailable is false (e.g., the target video is gone).
            if (retriesRemaining <= 0 && response.videos.isNotEmpty()) return response

            throw PlaylistUnavailableException("Playlist '${playlistId.value}' is not available.")
        }
    }

    private fun buildNextRequestBody(
        playlistId: PlaylistId,
        videoId: String?,
        index: Int,
        visitorData: String?
    ): JsonObject = buildJsonObject {
        put("playlistId", playlistId.value)
        // Emit JSON null when videoId is unset (first request) to match upstream's
        // Json.Encode(null) shape. YouTube treats both forms equivalently in practice,
        // but matching the wire format avoids any chance of subtle backend behavior drift.
        put("videoId", videoId)
        put("playlistIndex", index)
        put("context", buildClientContext(visitorData))
    }

    private fun buildClientContext(visitorData: String? = null): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB")
            put("clientVersion", "2.20210408.08.00")
            put("hl", "en")
            put("gl", "US")
            put("utcOffsetMinutes", 0)
            if (!visitorData.isNullOrBlank()) {
                put("visitorData", visitorData)
            }
        }
    }
}
