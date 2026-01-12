package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Thumbnail
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.VideoUnavailableException
import com.github.kotlintubeexplode.internal.cipher.CipherManifest
import com.github.kotlintubeexplode.internal.cipher.PlayerScriptParser
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.videos.*
import kotlinx.serialization.json.*

/**
 * Internal controller that orchestrates video data retrieval.
 *
 * Coordinates between HTTP requests, page parsing, and cipher decryption
 * to produce the final Video domain model.
 */
internal class VideoController(
    private val httpController: HttpController = HttpController(),
    private val pageParser: VideoPageParser = VideoPageParser(),
    private val cipherParser: PlayerScriptParser = PlayerScriptParser()
) {
    companion object {
        /**
         * Watch page URL template.
         * The bpctr parameter bypasses some consent checks.
         */
        private const val WATCH_URL = "https://www.youtube.com/watch?v=%s&bpctr=9999999999"

        /**
         * Iframe API URL for extracting player version.
         */
        private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"

        /**
         * Player script URL template.
         */
        private const val PLAYER_SCRIPT_URL = "https://www.youtube.com/s/player/%s/player_ias.vflset/en_US/base.js"

        /**
         * Service worker data URL for visitor data extraction.
         */
        private const val SW_JS_DATA_URL = "https://www.youtube.com/sw.js_data"

        /**
         * YouTube player API endpoint.
         */
        private const val PLAYER_API_URL = "https://www.youtube.com/youtubei/v1/player"

        /**
         * Android client user agent.
         */
        private const val ANDROID_USER_AGENT = "com.google.android.youtube/20.10.38 (Linux; U; ANDROID 11) gzip"

        /**
         * Android client version.
         */
        private const val ANDROID_CLIENT_VERSION = "20.10.38"

        /**
         * TV Embedded client name - used for age-restricted videos.
         * This is the only client that can handle age-restricted videos without authentication.
         */
        private const val TV_EMBEDDED_CLIENT_NAME = "TVHTML5_SIMPLY_EMBEDDED_PLAYER"

        /**
         * TV Embedded client version.
         */
        private const val TV_EMBEDDED_CLIENT_VERSION = "2.0"
    }

    /**
     * Cached cipher manifest to avoid re-parsing for multiple videos.
     */
    @Volatile
    private var cachedCipherManifest: CipherManifest? = null

    /**
     * Cached player script URL.
     */
    @Volatile
    private var cachedPlayerScriptUrl: String? = null

    /**
     * Cached visitor data for Android client requests.
     */
    @Volatile
    private var cachedVisitorData: String? = null

    /**
     * Gets the raw player response for a video.
     *
     * This is primarily used internally by other clients (e.g., ClosedCaptionClient)
     * that need access to the full player response data.
     *
     * @param videoId The validated video ID
     * @return The player response DTO
     * @throws VideoParseException if parsing fails
     */
    suspend fun getPlayerResponse(videoId: VideoId): PlayerResponseDto {
        val watchUrl = WATCH_URL.format(videoId.value)
        val html = httpController.getWithRetry(watchUrl, maxRetries = 3)

        // Extract and cache player script URL
        pageParser.extractPlayerScriptUrl(html)?.let { url ->
            cachedPlayerScriptUrl = url
        }

        return pageParser.parseWatchPage(html)
    }

    /**
     * Retrieves video metadata for the given video ID.
     *
     * @param videoId The validated video ID
     * @return The Video domain model
     * @throws VideoUnavailableException if the video is not available
     * @throws VideoParseException if parsing fails
     */
    suspend fun getVideo(videoId: VideoId): Video {
        // Step 1: Fetch the watch page
        val watchUrl = WATCH_URL.format(videoId.value)
        val html = httpController.getWithRetry(watchUrl, maxRetries = 3)

        // Step 2: Extract and cache player script URL
        pageParser.extractPlayerScriptUrl(html)?.let { url ->
            cachedPlayerScriptUrl = url
        }

        // Step 3: Parse the player response
        val playerResponse = pageParser.parseWatchPage(html)

        // Step 4: Validate video availability
        validatePlayability(videoId, playerResponse)

        // Step 5: Build domain model
        return buildVideo(videoId, playerResponse)
    }

    /**
     * Gets the cipher manifest, fetching and parsing if needed.
     *
     * @return The cipher manifest for signature decryption
     */
    suspend fun getCipherManifest(): CipherManifest {
        // Return cached if available
        cachedCipherManifest?.let { return it }

        // Determine player script URL
        val scriptUrl = cachedPlayerScriptUrl ?: run {
            // Fetch iframe API to get player version
            val iframeContent = httpController.get(IFRAME_API_URL)
            val version = pageParser.extractPlayerVersion(iframeContent)
                ?: throw VideoParseException("Could not extract player version")
            PLAYER_SCRIPT_URL.format(version)
        }

        // Fetch and parse player script
        val playerScript = httpController.getWithRetry(scriptUrl, maxRetries = 3)
        val manifest = cipherParser.parse(playerScript)

        // Cache and return
        cachedCipherManifest = manifest
        return manifest
    }

    /**
     * Resolves visitor data required for Android client API requests.
     *
     * Visitor data is fetched from YouTube's service worker data endpoint.
     * This is required for certain API calls to work properly.
     *
     * @return The visitor data string
     * @throws VideoParseException if visitor data cannot be resolved
     */
    suspend fun resolveVisitorData(): String {
        // Return cached if available
        cachedVisitorData?.let { return it }

        val headers = mapOf(
            "User-Agent" to ANDROID_USER_AGENT,
            "Accept" to "application/json"
        )

        val response = httpController.get(SW_JS_DATA_URL, headers)

        // Strip XSSI prefix if present (")]}'" at the start)
        val jsonString = if (response.startsWith(")]}'")) {
            response.substring(4)
        } else {
            response
        }

        try {
            val json = Json.parseToJsonElement(jsonString).jsonArray

            // Navigate to json[0][2][0][0][13] to get visitor data
            val visitorData = json.getOrNull(0)
                ?.jsonArray?.getOrNull(2)
                ?.jsonArray?.getOrNull(0)
                ?.jsonArray?.getOrNull(0)
                ?.jsonArray?.getOrNull(13)
                ?.jsonPrimitive?.contentOrNull

            if (visitorData.isNullOrBlank()) {
                throw VideoParseException("Visitor data not found in sw.js_data response")
            }

            cachedVisitorData = visitorData
            return visitorData
        } catch (e: Exception) {
            if (e is VideoParseException) throw e
            throw VideoParseException("Failed to parse visitor data: ${e.message}")
        }
    }

    /**
     * Gets player response using the Android client.
     *
     * The Android client can access streams that the web client cannot,
     * and typically provides higher quality streams without cipher protection.
     *
     * @param videoId The video ID
     * @return The player response
     */
    suspend fun getPlayerResponseViaAndroidClient(
        videoId: VideoId,
        signatureTimestamp: String? = null
    ): PlayerResponseDto {
        val visitorData = try {
            resolveVisitorData()
        } catch (e: Exception) {
            // Fallback to empty visitor data if resolution fails
            ""
        }

        val requestBody = buildJsonObject {
            put("videoId", videoId.value)
            put("contentCheckOk", true)
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", ANDROID_CLIENT_VERSION)
                    put("osName", "Android")
                    put("osVersion", "11")
                    put("platform", "MOBILE")
                    put("visitorData", visitorData)
                    put("hl", "en")
                    put("gl", "US")
                    put("utcOffsetMinutes", 0)
                }
            }
            // Include signatureTimestamp if provided (helps with some edge cases)
            signatureTimestamp?.let { sts ->
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("signatureTimestamp", sts)
                    }
                }
            }
        }.toString()

        val headers = mapOf(
            "User-Agent" to ANDROID_USER_AGENT
        )

        val response = httpController.postJson(PLAYER_API_URL, requestBody, headers)
        return pageParser.parsePlayerResponse(response)
    }

    /**
     * Gets player response using the TV Embedded client.
     *
     * The TV Embedded client is the only client that can handle age-restricted videos
     * without authentication. It requires a signatureTimestamp for proper functionality.
     *
     * Note: Unlike the Android client, TV Embedded returns obfuscated URLs that require
     * cipher decryption.
     *
     * @param videoId The video ID
     * @param signatureTimestamp The signature timestamp from the cipher manifest
     * @return The player response
     */
    suspend fun getPlayerResponseViaTVEmbeddedClient(
        videoId: VideoId,
        signatureTimestamp: String
    ): PlayerResponseDto {
        val requestBody = buildJsonObject {
            put("videoId", videoId.value)
            put("contentCheckOk", true)
            put("racyCheckOk", true)  // Required for age-restricted content
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", TV_EMBEDDED_CLIENT_NAME)
                    put("clientVersion", TV_EMBEDDED_CLIENT_VERSION)
                    put("hl", "en")
                    put("gl", "US")
                }
                putJsonObject("thirdParty") {
                    // Required for bypassing embed restrictions
                    put("embedUrl", "https://www.youtube.com")
                }
            }
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("signatureTimestamp", signatureTimestamp)
                }
            }
        }.toString()

        val response = httpController.postJson(PLAYER_API_URL, requestBody)
        return pageParser.parsePlayerResponse(response)
    }

    /**
     * Validates that the video is playable.
     */
    private fun validatePlayability(videoId: VideoId, response: PlayerResponseDto) {
        val status = response.playabilityStatus

        if (status == null || !status.isPlayable) {
            val reason = status?.reason ?: "Unknown error"
            throw VideoUnavailableException("Video '${videoId.value}' is unavailable: $reason")
        }
    }

    /**
     * Builds the Video domain model from the parsed response.
     */
    private fun buildVideo(videoId: VideoId, response: PlayerResponseDto): Video {
        val details = response.videoDetails
            ?: throw VideoParseException("Video details not found")

        val microformat = response.microformat?.playerMicroformatRenderer

        val channelId = details.channelId
            ?: throw VideoParseException("Channel ID not found")

        val channelTitle = details.author
            ?: microformat?.ownerChannelName
            ?: "Unknown"

        val thumbnails = details.thumbnail?.thumbnails?.mapNotNull { thumb ->
            if (thumb.url != null && thumb.width != null && thumb.height != null) {
                Thumbnail(thumb.url, thumb.width, thumb.height)
            } else null
        } ?: createDefaultThumbnails(videoId.value)

        return Video.create(
            id = videoId,
            title = details.title ?: "",
            author = Author(channelId, channelTitle),
            uploadDate = microformat?.uploadDate ?: microformat?.publishDate,
            description = details.shortDescription ?: "",
            durationSeconds = details.durationSeconds,
            thumbnails = thumbnails,
            keywords = details.keywords ?: emptyList(),
            engagement = Engagement(
                viewCount = details.viewCountLong ?: 0,
                likeCount = 0,  // Like count requires additional parsing from watch page
                dislikeCount = 0
            )
        )
    }

    /**
     * Creates default thumbnail URLs for a video ID.
     */
    private fun createDefaultThumbnails(videoId: String): List<Thumbnail> = listOf(
        Thumbnail("https://i.ytimg.com/vi/$videoId/default.jpg", 120, 90),
        Thumbnail("https://i.ytimg.com/vi/$videoId/mqdefault.jpg", 320, 180),
        Thumbnail("https://i.ytimg.com/vi/$videoId/hqdefault.jpg", 480, 360),
        Thumbnail("https://i.ytimg.com/vi/$videoId/sddefault.jpg", 640, 480),
        Thumbnail("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg", 1280, 720)
    )
}
