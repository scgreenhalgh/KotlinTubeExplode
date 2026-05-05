package com.github.kotlintubeexplode.playlists

import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Batch
import com.github.kotlintubeexplode.common.Thumbnail
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.KotlinTubeExplodeException
import com.github.kotlintubeexplode.exceptions.PlaylistUnavailableException
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.PlaylistController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

/**
 * Client for retrieving YouTube playlist information.
 */
class PlaylistClient internal constructor(
    private val httpController: HttpController,
    private val playlistController: PlaylistController = PlaylistController(httpController)
) {
    companion object {
        private const val BROWSE_URL = "https://www.youtube.com/youtubei/v1/browse"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /**
     * Gets playlist metadata.
     *
     * @param playlistId The playlist ID
     * @return The playlist metadata
     */
    suspend fun get(playlistId: PlaylistId): Playlist {
        val response = fetchPlaylistBrowse(playlistId)

        val title = extractTitle(response) ?: "Untitled Playlist"
        val author = extractAuthor(response)
        val description = extractDescription(response) ?: ""
        val count = extractCount(response)
        val thumbnails = extractThumbnails(response)

        return Playlist(
            id = playlistId,
            title = title,
            author = author,
            description = description,
            count = count,
            thumbnails = thumbnails
        )
    }

    /**
     * Gets playlist metadata by URL or ID string.
     */
    suspend fun get(playlistIdOrUrl: String): Playlist {
        return get(PlaylistId.parse(playlistIdOrUrl))
    }

    /**
     * Gets videos in a playlist in batches via the `youtubei/v1/next` endpoint.
     *
     * Supports all playlist types including Mix (`RD…`) and system playlists.
     * Mirrors upstream YoutubeExplode's pagination model: each iteration carries
     * the last seen video ID + index + visitorData forward into the next request.
     *
     * Stops cleanly if the playlist becomes unavailable mid-iteration after at
     * least one batch was emitted (upstream PR #922).
     *
     * @param playlistId The playlist ID
     * @return Flow of video batches
     */
    fun getVideoBatches(playlistId: PlaylistId): Flow<Batch<PlaylistVideo>> = flow {
        val seenIds = mutableSetOf<String>()
        var lastVideoId: String? = null
        var lastVideoIndex = 0
        var visitorData: String? = null
        var hasEmittedBatch = false

        while (true) {
            val response = try {
                playlistController.getPlaylistNextResponse(
                    playlistId = playlistId,
                    videoId = lastVideoId,
                    index = lastVideoIndex,
                    visitorData = visitorData
                )
            } catch (e: PlaylistUnavailableException) {
                // Mid-iteration unavailability after we've already streamed some videos —
                // treat as end of playlist instead of bubbling up the exception.
                if (hasEmittedBatch) break
                throw e
            }

            val cursorBefore = lastVideoId
            val newVideos = mutableListOf<PlaylistVideo>()
            for (videoData in response.videos) {
                val videoId = videoData.videoId
                    ?: throw KotlinTubeExplodeException("Failed to extract the video ID.")
                lastVideoId = videoId

                val idx = videoData.index
                    ?: throw KotlinTubeExplodeException("Failed to extract the video index.")
                lastVideoIndex = idx

                // Skip duplicates, but cursor still advances above.
                if (!seenIds.add(videoId)) continue

                // Skip entries where author info is unrecoverable (multi-author videos with
                // dialog-panel author renderers, deleted/private videos with no byline, etc.).
                // Upstream throws here, but YouTube's multi-author dialog shape has shifted
                // and chasing the deep fallback path is fragile; permissive skip is more robust.
                val authorName = videoData.authorName ?: continue
                val authorChannelId = videoData.authorChannelId ?: continue

                val thumbnails = videoData.thumbnail?.thumbnails?.mapNotNull { thumb ->
                    val url = thumb.url ?: return@mapNotNull null
                    Thumbnail(url, thumb.width ?: 0, thumb.height ?: 0)
                } ?: emptyList()

                newVideos += PlaylistVideo(
                    playlistId = playlistId,
                    id = VideoId(videoId),
                    title = videoData.titleText ?: "",
                    author = Author(authorChannelId, authorName),
                    duration = videoData.durationSeconds?.seconds,
                    thumbnails = thumbnails,
                    index = idx
                )
            }

            // Stop only when the cursor didn't advance — i.e., the response itself had no new
            // entries. A batch where all entries were skipped (deleted videos, multi-author
            // dialog renderers we can't parse) still moved the cursor; keep iterating.
            if (lastVideoId == cursorBefore) break

            if (visitorData == null) visitorData = response.visitorData
            if (newVideos.isNotEmpty()) {
                emit(Batch(newVideos))
                hasEmittedBatch = true
            }
        }
    }

    /**
     * Gets all videos in a playlist.
     *
     * @param playlistId The playlist ID
     * @return Flow of playlist videos
     */
    fun getVideos(playlistId: PlaylistId): Flow<PlaylistVideo> = flow {
        getVideoBatches(playlistId).collect { batch ->
            batch.items.forEach { emit(it) }
        }
    }

    /**
     * Gets all videos in a playlist by URL or ID string.
     */
    fun getVideos(playlistIdOrUrl: String): Flow<PlaylistVideo> =
        getVideos(PlaylistId.parse(playlistIdOrUrl))

    private suspend fun fetchPlaylistBrowse(playlistId: PlaylistId): JsonObject {
        val body = buildJsonObject {
            put("browseId", "VL${playlistId.value}")
            put("context", buildClientContext())
        }

        val responseText = httpController.postJson(BROWSE_URL, body.toString())
        return json.parseToJsonElement(responseText).jsonObject
    }

    private fun buildClientContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB")
            put("clientVersion", "2.20231219.04.00")
            put("hl", "en")
            put("gl", "US")
        }
    }

    private fun extractTitle(response: JsonObject): String? {
        // Try multiple paths for title
        return response.findString("header", "playlistHeaderRenderer", "title", "simpleText")
            ?: response.findString("header", "playlistHeaderRenderer", "title", "runs", "0", "text")
            ?: response.findString("metadata", "playlistMetadataRenderer", "title")
    }

    private fun extractAuthor(response: JsonObject): Author? {
        val name = response.findString("header", "playlistHeaderRenderer", "ownerText", "runs", "0", "text")
            ?: return null
        val channelId = response.findString(
            "header", "playlistHeaderRenderer", "ownerText", "runs", "0",
            "navigationEndpoint", "browseEndpoint", "browseId"
        ) ?: return null

        return Author(channelId, name)
    }

    private fun extractDescription(response: JsonObject): String? {
        return response.findString("header", "playlistHeaderRenderer", "descriptionText", "simpleText")
            ?: response.findString("metadata", "playlistMetadataRenderer", "description")
    }

    private fun extractCount(response: JsonObject): Int? {
        val countText = response.findString("header", "playlistHeaderRenderer", "numVideosText", "runs", "0", "text")
            ?: response.findString("header", "playlistHeaderRenderer", "stats", "0", "runs", "0", "text")
            ?: return null

        return countText.filter { it.isDigit() }.toIntOrNull()
    }

    private fun extractThumbnails(response: JsonObject): List<Thumbnail> {
        val thumbnailArray = response.findArray(
            "header", "playlistHeaderRenderer", "playlistHeaderBanner", "heroPlaylistThumbnailRenderer", "thumbnail", "thumbnails"
        ) ?: response.findArray("header", "playlistHeaderRenderer", "thumbnails", "0", "thumbnails")
        ?: return emptyList()

        return thumbnailArray.mapNotNull { thumb ->
            val obj = thumb.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
            val height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
            Thumbnail(url, width, height)
        }
    }

    // JSON navigation helpers
    private fun JsonObject.findString(vararg path: String): String? {
        var current: JsonElement? = this
        for (key in path) {
            current = when (current) {
                is JsonObject -> current[key]
                is JsonArray -> key.toIntOrNull()?.let { current.getOrNull(it) }
                else -> null
            }
            if (current == null) return null
        }
        return (current as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.findArray(vararg path: String): JsonArray? {
        var current: JsonElement? = this
        for (key in path.dropLast(1)) {
            current = when (current) {
                is JsonObject -> current[key]
                is JsonArray -> key.toIntOrNull()?.let { current.getOrNull(it) }
                else -> null
            }
            if (current == null) return null
        }
        return when (current) {
            is JsonObject -> current[path.last()] as? JsonArray
            else -> null
        }
    }

    private fun JsonElement.findString(vararg path: String): String? =
        (this as? JsonObject)?.findString(*path)

    private fun JsonElement.findArray(vararg path: String): JsonArray? =
        (this as? JsonObject)?.findArray(*path)
}
