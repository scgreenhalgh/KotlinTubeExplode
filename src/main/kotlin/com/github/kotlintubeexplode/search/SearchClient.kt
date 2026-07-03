package com.github.kotlintubeexplode.search

import com.github.kotlintubeexplode.channels.ChannelId
import com.github.kotlintubeexplode.common.Batch
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.playlists.PlaylistId
import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Thumbnail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

/**
 * Client for searching YouTube.
 */
class SearchClient internal constructor(
    private val httpController: HttpController
) {
    companion object {
        private const val SEARCH_URL = "https://www.youtube.com/youtubei/v1/search"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /**
     * Searches YouTube and returns results in batches.
     *
     * @param query The search query
     * @param filter Optional filter to limit result types
     * @return Flow of result batches
     */
    fun getResultBatches(
        query: String,
        filter: SearchFilter = SearchFilter.None
    ): Flow<Batch<ISearchResult>> = flow {
        val seenIds = mutableSetOf<String>()
        var continuation: String? = null

        // Initial search
        val initialResponse = performSearch(query, filter)
        val initialResults = extractResults(initialResponse, filter, seenIds)
        if (initialResults.isNotEmpty()) {
            emit(Batch(initialResults))
        }
        continuation = extractContinuation(initialResponse)

        // Paginate
        while (continuation != null) {
            val nextResponse = performSearch(query, filter, continuation)
            val results = extractResults(nextResponse, filter, seenIds)
            if (results.isNotEmpty()) {
                emit(Batch(results))
            }
            continuation = extractContinuation(nextResponse)
        }
    }

    /**
     * Searches YouTube and returns all results.
     *
     * @param query The search query
     * @return Flow of search results
     */
    fun getResults(query: String): Flow<ISearchResult> = flow {
        getResultBatches(query).collect { batch ->
            batch.items.forEach { emit(it) }
        }
    }

    /**
     * Searches for videos only.
     *
     * @param query The search query
     * @return Flow of video search results
     */
    fun getVideos(query: String): Flow<VideoSearchResult> = flow {
        getResultBatches(query, SearchFilter.Video).collect { batch ->
            batch.items.filterIsInstance<VideoSearchResult>().forEach { emit(it) }
        }
    }

    /**
     * Searches for playlists only.
     *
     * @param query The search query
     * @return Flow of playlist search results
     */
    fun getPlaylists(query: String): Flow<PlaylistSearchResult> = flow {
        getResultBatches(query, SearchFilter.Playlist).collect { batch ->
            batch.items.filterIsInstance<PlaylistSearchResult>().forEach { emit(it) }
        }
    }

    /**
     * Searches for channels only.
     *
     * @param query The search query
     * @return Flow of channel search results
     */
    fun getChannels(query: String): Flow<ChannelSearchResult> = flow {
        getResultBatches(query, SearchFilter.Channel).collect { batch ->
            batch.items.filterIsInstance<ChannelSearchResult>().forEach { emit(it) }
        }
    }

    /**
     * Sends a single search request. Upstream (SearchController.cs) uses one body shape for
     * both the initial and continuation calls: query + params + continuation + context, where
     * `continuation` is a literal null on the first call. A null [continuation] and a None
     * filter both serialize to JSON null, so the initial and paginated requests are identical
     * in structure.
     */
    private suspend fun performSearch(
        query: String,
        filter: SearchFilter,
        continuation: String? = null
    ): JsonObject {
        val body = buildJsonObject {
            put("query", query)
            put("params", filterParams(filter))
            put("continuation", continuation)
            put("context", buildClientContext())
        }

        val responseText = httpController.postJson(SEARCH_URL, body.toString())
        return json.parseToJsonElement(responseText).jsonObject
    }

    /**
     * Maps a [SearchFilter] to YouTube's opaque `params` token, or null for no filter.
     */
    private fun filterParams(filter: SearchFilter): String? = when (filter) {
        SearchFilter.Video -> "EgIQAQ%3D%3D" // Videos only
        SearchFilter.Playlist -> "EgIQAw%3D%3D" // Playlists only
        SearchFilter.Channel -> "EgIQAg%3D%3D" // Channels only
        SearchFilter.None -> null
    }

    private fun buildClientContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB")
            put("clientVersion", "2.20210408.08.00")
            put("hl", "en")
            put("gl", "US")
            put("utcOffsetMinutes", 0)
        }
    }

    private fun extractResults(
        response: JsonObject,
        filter: SearchFilter,
        seenIds: MutableSet<String>
    ): List<ISearchResult> {
        val results = mutableListOf<ISearchResult>()

        // Extract videos
        if (filter == SearchFilter.None || filter == SearchFilter.Video) {
            response.findAllByKey("videoRenderer").forEach { renderer ->
                parseVideoResult(renderer, seenIds)?.let { results.add(it) }
            }
        }

        // Extract playlists. YouTube is migrating playlist results to the newer
        // lockupViewModel renderer; keep the legacy playlistRenderer path too so results
        // aren't dropped in regions still serving the old shape. seenIds dedups any overlap.
        if (filter == SearchFilter.None || filter == SearchFilter.Playlist) {
            response.findAllByKey("lockupViewModel").forEach { renderer ->
                parseLockupPlaylistResult(renderer, seenIds)?.let { results.add(it) }
            }
            response.findAllByKey("playlistRenderer").forEach { renderer ->
                parsePlaylistResult(renderer, seenIds)?.let { results.add(it) }
            }
        }

        // Extract channels
        if (filter == SearchFilter.None || filter == SearchFilter.Channel) {
            response.findAllByKey("channelRenderer").forEach { renderer ->
                parseChannelResult(renderer, seenIds)?.let { results.add(it) }
            }
        }

        return results
    }

    private fun parseVideoResult(renderer: JsonObject, seenIds: MutableSet<String>): VideoSearchResult? {
        val videoId = renderer.findString("videoId") ?: return null
        if (!seenIds.add("video:$videoId")) return null

        val title = renderer.findString("title", "runs", "0", "text")
            ?: renderer.findString("title", "simpleText")
            ?: return null

        val authorName = renderer.findString("ownerText", "runs", "0", "text")
            ?: renderer.findString("shortBylineText", "runs", "0", "text")
            ?: "Unknown"

        val authorId = renderer.findString(
            "ownerText", "runs", "0", "navigationEndpoint", "browseEndpoint", "browseId"
        ) ?: renderer.findString(
            "shortBylineText", "runs", "0", "navigationEndpoint", "browseEndpoint", "browseId"
        ) ?: ""

        // Some videos have invalid channel IDs (e.g. just "UC"). These videos are
        // generally unplayable anyway, so skip them.
        if (authorId.isNotEmpty() && ChannelId.tryParse(authorId) == null) return null

        val durationText = renderer.findString("lengthText", "simpleText")
        val durationSeconds = parseDurationText(durationText)

        val thumbnails = extractThumbnails(renderer)

        return VideoSearchResult(
            id = VideoId(videoId),
            title = title,
            author = Author(authorId, authorName),
            duration = durationSeconds?.seconds,
            thumbnails = thumbnails
        )
    }

    private fun parsePlaylistResult(renderer: JsonObject, seenIds: MutableSet<String>): PlaylistSearchResult? {
        val playlistId = renderer.findString("playlistId") ?: return null
        if (!seenIds.add("playlist:$playlistId")) return null

        val title = renderer.findString("title", "simpleText")
            ?: renderer.findString("title", "runs", "0", "text")
            ?: return null

        val authorName = renderer.findString("shortBylineText", "runs", "0", "text")
            ?: renderer.findString("longBylineText", "runs", "0", "text")

        val authorId = renderer.findString(
            "shortBylineText", "runs", "0", "navigationEndpoint", "browseEndpoint", "browseId"
        ) ?: renderer.findString(
            "longBylineText", "runs", "0", "navigationEndpoint", "browseEndpoint", "browseId"
        )

        val author = if (authorName != null && authorId != null) {
            Author(authorId, authorName)
        } else null

        val thumbnails = extractThumbnails(renderer)

        return PlaylistSearchResult(
            id = PlaylistId(playlistId),
            title = title,
            author = author,
            thumbnails = thumbnails
        )
    }

    /**
     * Parses a playlist from YouTube's newer `lockupViewModel` renderer. lockupViewModel is a
     * generic collection renderer, so entries whose contentType clearly names a video / channel /
     * short are skipped rather than emitted as playlists. Unknown or absent contentTypes are let
     * through so a newly introduced playlist type isn't silently dropped. Field layout mirrors
     * upstream SearchResponse.PlaylistData.
     */
    private fun parseLockupPlaylistResult(renderer: JsonObject, seenIds: MutableSet<String>): PlaylistSearchResult? {
        val contentType = renderer.findString("contentType")
        if (contentType != null &&
            (contentType.contains("VIDEO") || contentType.contains("CHANNEL") || contentType.contains("SHORT"))
        ) return null

        val playlistId = renderer.findString("contentId")
            ?: renderer.findString("playlistId")
            ?: return null
        if (PlaylistId.tryParse(playlistId) == null) return null

        val metadata = (renderer["metadata"] as? JsonObject)
            ?.get("lockupMetadataViewModel") as? JsonObject
            ?: return null

        val title = metadata.findString("title", "content") ?: return null

        if (!seenIds.add("playlist:$playlistId")) return null

        // Author is carried by the first metadataParts entry's text node.
        val authorText = metadata.findAllByKey("metadataParts").firstOrNull()
            ?.get("text") as? JsonObject
        val authorName = authorText?.findString("content")
        val authorId = authorText?.findString(
            "commandRuns", "0", "onTap", "innertubeCommand", "browseEndpoint", "browseId"
        ) ?: authorText?.findString("navigationEndpoint", "browseEndpoint", "browseId")

        val author = if (authorName != null && authorId != null) {
            Author(authorId, authorName)
        } else null

        return PlaylistSearchResult(
            id = PlaylistId(playlistId),
            title = title,
            author = author,
            thumbnails = extractLockupThumbnails(renderer)
        )
    }

    private fun extractLockupThumbnails(renderer: JsonObject): List<Thumbnail> {
        val sources = renderer.findArray(
            "contentImage", "collectionThumbnailViewModel", "primaryThumbnail",
            "thumbnailViewModel", "image", "sources"
        ) ?: return emptyList()

        return sources.mapNotNull { source ->
            val obj = source.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
            val height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
            Thumbnail(url, width, height)
        }
    }

    private fun parseChannelResult(renderer: JsonObject, seenIds: MutableSet<String>): ChannelSearchResult? {
        val channelId = renderer.findString("channelId") ?: return null
        if (!seenIds.add("channel:$channelId")) return null

        val title = renderer.findString("title", "simpleText")
            ?: renderer.findString("title", "runs", "0", "text")
            ?: return null

        val thumbnails = extractThumbnails(renderer)

        return ChannelSearchResult(
            id = ChannelId(channelId),
            title = title,
            thumbnails = thumbnails
        )
    }

    private fun extractThumbnails(renderer: JsonObject): List<Thumbnail> {
        val thumbnailArray = renderer.findArray("thumbnail", "thumbnails")
            ?: renderer.findArray("thumbnails", "0", "thumbnails")
            ?: return emptyList()

        return thumbnailArray.mapNotNull { thumb ->
            val obj = thumb.jsonObject
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
            val height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
            Thumbnail(url, width, height)
        }
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

    // JSON navigation helpers (same as PlaylistClient)
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
