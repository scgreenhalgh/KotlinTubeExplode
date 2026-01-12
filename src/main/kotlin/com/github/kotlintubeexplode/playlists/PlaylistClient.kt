package com.github.kotlintubeexplode.playlists

import com.github.kotlintubeexplode.common.Batch
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoPageParser
import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Thumbnail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

/**
 * Client for retrieving YouTube playlist information.
 */
class PlaylistClient internal constructor(
    private val httpController: HttpController
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
     * Gets videos in a playlist in batches.
     *
     * @param playlistId The playlist ID
     * @return Flow of video batches
     */
    fun getVideoBatches(playlistId: PlaylistId): Flow<Batch<PlaylistVideo>> = flow {
        val seenIds = mutableSetOf<String>()
        var continuation: String? = null

        // Initial fetch
        val initialResponse = fetchPlaylistBrowse(playlistId)
        val initialVideos = extractVideos(initialResponse, playlistId, seenIds)
        if (initialVideos.isNotEmpty()) {
            emit(Batch(initialVideos))
        }
        continuation = extractContinuation(initialResponse)

        // Paginate
        while (continuation != null) {
            val nextResponse = fetchPlaylistContinuation(continuation)
            val videos = extractVideos(nextResponse, playlistId, seenIds)
            if (videos.isNotEmpty()) {
                emit(Batch(videos))
            }
            continuation = extractContinuation(nextResponse)
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

    private suspend fun fetchPlaylistContinuation(continuation: String): JsonObject {
        val body = buildJsonObject {
            put("continuation", continuation)
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

    private fun extractVideos(
        response: JsonObject,
        playlistId: PlaylistId,
        seenIds: MutableSet<String>
    ): List<PlaylistVideo> {
        val videos = mutableListOf<PlaylistVideo>()

        // Find video renderers in various locations
        response.findAllByKey("playlistVideoRenderer").forEach { renderer ->
            val videoId = renderer.findString("videoId") ?: return@forEach
            if (!seenIds.add(videoId)) return@forEach // Skip duplicates

            val title = renderer.findString("title", "runs", "0", "text")
                ?: renderer.findString("title", "simpleText")
                ?: "Untitled"

            val authorName = renderer.findString("shortBylineText", "runs", "0", "text") ?: "Unknown"
            val authorId = renderer.findString(
                "shortBylineText", "runs", "0", "navigationEndpoint", "browseEndpoint", "browseId"
            ) ?: ""

            val durationSeconds = renderer.findString("lengthSeconds")?.toLongOrNull()
                ?: parseDurationText(renderer.findString("lengthText", "simpleText"))

            val thumbnails = renderer.findArray("thumbnail", "thumbnails")?.mapNotNull { thumb ->
                val obj = thumb.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
                val height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
                Thumbnail(url, width, height)
            } ?: emptyList()

            val index = renderer.findString("index", "simpleText")?.toIntOrNull()

            videos.add(
                PlaylistVideo(
                    playlistId = playlistId,
                    id = VideoId(videoId),
                    title = title,
                    author = Author(authorId, authorName),
                    duration = durationSeconds?.seconds,
                    thumbnails = thumbnails,
                    index = index
                )
            )
        }

        return videos
    }

    private fun extractContinuation(response: JsonObject): String? {
        return response.findAllByKey("continuationCommand").firstOrNull()
            ?.findString("token")
            ?: response.findAllByKey("continuationEndpoint").firstOrNull()
                ?.findString("continuationCommand", "token")
    }

    private fun parseDurationText(text: String?): Long? {
        if (text == null) return null
        val parts = text.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
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

    private fun JsonObject.findAllByKey(key: String): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        findAllByKeyRecursive(this, key, results)
        return results
    }

    private fun findAllByKeyRecursive(element: JsonElement, key: String, results: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element[key]?.let { value ->
                    if (value is JsonObject) results.add(value)
                    else if (value is JsonArray) {
                        value.filterIsInstance<JsonObject>().forEach { results.add(it) }
                    }
                }
                element.values.forEach { findAllByKeyRecursive(it, key, results) }
            }
            is JsonArray -> element.forEach { findAllByKeyRecursive(it, key, results) }
            else -> {}
        }
    }
}
