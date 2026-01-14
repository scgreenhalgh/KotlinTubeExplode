package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.common.Language
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.VideoUnavailableException
import com.github.kotlintubeexplode.internal.*
import com.github.kotlintubeexplode.internal.cipher.CipherManifest
import com.github.kotlintubeexplode.internal.cipher.PlayerScriptParser
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import com.github.kotlintubeexplode.common.Resolution
import java.io.InputStream
import java.io.OutputStream

/**
 * Client for retrieving video stream information and downloading streams.
 */
class StreamClient internal constructor(
    private val httpController: HttpController,
    private val videoController: VideoController
) {
    private val dashParser = DashManifestParser()

    /**
     * Gets the stream manifest for a video.
     *
     * @param videoId The video ID
     * @return The stream manifest containing all available streams
     */
    suspend fun getManifest(videoId: VideoId): StreamManifest {
        var lastException: Exception? = null

        // Retry logic for transient failures
        repeat(5) { attempt ->
            try {
                val streams = getStreamInfos(videoId)
                return StreamManifest(streams)
            } catch (e: com.github.kotlintubeexplode.exceptions.VideoUnplayableException) {
                // Don't retry unplayable videos (unavailable, age-restricted, etc.)
                throw e
            } catch (e: com.github.kotlintubeexplode.exceptions.RequestLimitExceededException) {
                // Don't retry rate limit errors
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < 4) {
                    kotlinx.coroutines.delay(100L * (1 shl attempt))
                }
            }
        }

        throw lastException ?: IllegalStateException("Failed to get stream manifest")
    }

    /**
     * Gets the stream manifest by URL or ID string.
     */
    suspend fun getManifest(videoIdOrUrl: String): StreamManifest {
        return getManifest(VideoId.parse(videoIdOrUrl))
    }

    /**
     * Gets the HLS (HTTP Live Streaming) manifest URL for a video.
     *
     * Only available for live streams.
     *
     * @param videoId The video ID
     * @return The HLS manifest URL
     * @throws IllegalArgumentException if the video is not a live stream
     * @throws IllegalStateException if HLS URL is not available
     */
    suspend fun getHttpLiveStreamUrl(videoId: VideoId): String {
        val watchUrl = "https://www.youtube.com/watch?v=${videoId.value}&bpctr=9999999999"
        val html = httpController.getWithRetry(watchUrl, maxRetries = 3)

        val pageParser = VideoPageParser()
        val playerResponse = pageParser.parseWatchPage(html)

        return playerResponse.streamingData?.hlsManifestUrl
            ?: throw IllegalStateException("HLS manifest URL not available for video ${videoId.value}")
    }

    /**
     * Gets the HLS manifest URL by URL or ID string.
     */
    suspend fun getHttpLiveStreamUrl(videoIdOrUrl: String): String {
        return getHttpLiveStreamUrl(VideoId.parse(videoIdOrUrl))
    }

    /**
     * Opens a stream for reading.
     *
     * Uses [MediaStream] for throttled streams to handle YouTube's rate limiting
     * by downloading in segments.
     *
     * @param streamInfo The stream info to open
     * @return An input stream for reading the media data
     */
    suspend fun getStream(streamInfo: IStreamInfo): InputStream {
        // Use MediaStream for better throttle handling
        return MediaStream(httpController, streamInfo)
    }

    /**
     * Copies a stream to an output stream with progress reporting.
     *
     * Handles throttled streams by downloading in segments using [MediaStream].
     *
     * @param streamInfo The stream to download
     * @param output The destination output stream
     * @param onProgress Progress callback (0.0 to 1.0)
     */
    suspend fun copyTo(
        streamInfo: IStreamInfo,
        output: OutputStream,
        onProgress: ((Double) -> Unit)? = null
    ) {
        val totalBytes = streamInfo.size.bytes
        var bytesRead = 0L

        val mediaStream = MediaStream(httpController, streamInfo)
        mediaStream.use { input ->
            val buffer = ByteArray(81920) // 80KB buffer for better throughput
            var read: Int

            while (true) {
                read = input.readAsync(buffer, 0, buffer.size)
                if (read == -1) break

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    output.write(buffer, 0, read)
                }
                bytesRead += read

                if (totalBytes > 0) {
                    onProgress?.invoke(bytesRead.toDouble() / totalBytes)
                }
            }
        }

        onProgress?.invoke(1.0)
    }

    /**
     * Downloads a stream to a file.
     *
     * **Security Note:** This method does not validate the [filePath] parameter.
     * Callers are responsible for ensuring the path is safe and does not allow
     * path traversal attacks (e.g., paths containing "../"). When accepting
     * file paths from user input, always validate and sanitize the path first.
     *
     * @param streamInfo The stream to download
     * @param filePath The destination file path
     * @param onProgress Progress callback (0.0 to 1.0)
     * @throws IllegalArgumentException if the path points to an existing directory
     */
    suspend fun download(
        streamInfo: IStreamInfo,
        filePath: String,
        onProgress: ((Double) -> Unit)? = null
    ) {
        val file = java.io.File(filePath)

        // Security check: Ensure we are not writing to a directory
        // Note: Full path traversal prevention is the caller's responsibility
        // as we accept absolute paths here.
        if (file.exists() && file.isDirectory) {
            throw IllegalArgumentException("Path is a directory: $filePath")
        }

        try {
            file.outputStream().use { output ->
                copyTo(streamInfo, output, onProgress)
            }
        } catch (e: Exception) {
            // Clean up partial file on failure
            if (file.exists()) {
                file.delete()
            }
            throw e
        }
    }

    /**
     * Gets stream information using a multi-client approach (following C# YoutubeExplode).
     *
     * Order of attempts:
     * 1. Android client - Primary, returns plain URLs without cipher (fastest)
     * 2. TV Embedded client - For age-restricted videos (requires cipher)
     * 3. Web client - Fallback (requires cipher)
     */
    private suspend fun getStreamInfos(videoId: VideoId): List<IStreamInfo> {
        // 1. Try Android client first (no cipher needed for most streams)
        val androidResponse = tryAndroidClient(videoId)

        if (androidResponse != null) {
            val androidStreams = tryProcessAndroidStreams(androidResponse)
            if (androidStreams.isNotEmpty()) {
                return androidStreams
            }
        }

        // Check if age-restricted (Android client will indicate this)
        val isAgeRestricted = androidResponse?.playabilityStatus?.isAgeRestricted == true

        // 2. If age-restricted, try TV Embedded client
        if (isAgeRestricted) {
            val tvStreams = tryTVEmbeddedClient(videoId)
            if (tvStreams.isNotEmpty()) {
                return tvStreams
            }
        }

        // 3. Fallback to web client (current implementation with cipher)
        return getStreamInfosViaWebClient(videoId)
    }

    /**
     * Tries to get player response via Android client.
     * Returns null on any failure.
     */
    private suspend fun tryAndroidClient(videoId: VideoId): PlayerResponseDto? {
        return try {
            videoController.getPlayerResponseViaAndroidClient(videoId)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Tries to process streams from Android client response.
     * Android client returns plain URLs without cipher, so no decryption needed.
     */
    private suspend fun tryProcessAndroidStreams(response: PlayerResponseDto): List<IStreamInfo> {
        // Check if response is playable
        if (response.playabilityStatus?.isPlayable != true) {
            return emptyList()
        }

        val streamingData = response.streamingData ?: return emptyList()
        val streams = mutableListOf<IStreamInfo>()

        // Process formats - Android client typically returns plain URLs
        for (format in streamingData.allFormats) {
            // Skip formats that require decryption (shouldn't happen with Android client)
            if (format.url == null) continue

            val streamInfo = processFormatWithoutCipher(format)
            if (streamInfo != null) {
                streams.add(streamInfo)
            }
        }

        // Also try DASH manifest if available
        streamingData.dashManifestUrl?.let { dashUrl ->
            try {
                val manifestXml = httpController.get(dashUrl)
                val dashStreams = dashParser.parse(manifestXml)
                streams.addAll(dashStreams)
            } catch (e: Exception) {
                // DASH manifest might not be available, ignore
            }
        }

        return streams
    }

    /**
     * Tries to get streams via TV Embedded client for age-restricted videos.
     * TV Embedded requires cipher decryption.
     */
    private suspend fun tryTVEmbeddedClient(videoId: VideoId): List<IStreamInfo> {
        return try {
            // Get cipher manifest first (needed for signatureTimestamp and decryption)
            val cipherManifest = videoController.getCipherManifest()

            val response = videoController.getPlayerResponseViaTVEmbeddedClient(
                videoId,
                cipherManifest.signatureTimestamp
            )

            if (response.playabilityStatus?.isPlayable != true) {
                return emptyList()
            }

            val streamingData = response.streamingData ?: return emptyList()
            val streams = mutableListOf<IStreamInfo>()

            // TV Embedded returns ciphered streams, so we need to decrypt
            for (format in streamingData.allFormats) {
                val streamInfo = processFormat(format, cipherManifest) { cipherManifest }
                if (streamInfo != null) {
                    streams.add(streamInfo)
                }
            }

            // Also try DASH manifest
            streamingData.dashManifestUrl?.let { dashUrl ->
                try {
                    val manifestXml = httpController.get(dashUrl)
                    val dashStreams = dashParser.parse(manifestXml)
                    streams.addAll(dashStreams)
                } catch (e: Exception) {
                    // DASH manifest might not be available, ignore
                }
            }

            streams
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets streams via web client (original implementation with cipher).
     * Used as fallback when Android and TV Embedded clients fail.
     */
    private suspend fun getStreamInfosViaWebClient(videoId: VideoId): List<IStreamInfo> {
        val streams = mutableListOf<IStreamInfo>()

        // Get video page and player response
        val watchUrl = "https://www.youtube.com/watch?v=${videoId.value}&bpctr=9999999999"
        val html = httpController.getWithRetry(watchUrl, maxRetries = 3)

        val pageParser = VideoPageParser()
        val playerResponse = pageParser.parseWatchPage(html)

        // Check playability
        if (playerResponse.playabilityStatus?.isPlayable != true) {
            val reason = playerResponse.playabilityStatus?.reason ?: "Unknown error"
            throw VideoUnavailableException("Video '${videoId.value}' is unavailable: $reason")
        }

        // Get cipher manifest if needed
        var cipherManifest: CipherManifest? = null

        // Process streaming data
        playerResponse.streamingData?.let { streamingData ->
            // Process all formats
            for (format in streamingData.allFormats) {
                val streamInfo = processFormat(format, cipherManifest) {
                    // Lazy load cipher manifest only when needed
                    if (cipherManifest == null) {
                        cipherManifest = videoController.getCipherManifest()
                    }
                    cipherManifest!!
                }
                if (streamInfo != null) {
                    streams.add(streamInfo)
                }
            }

            // Try to get DASH manifest streams
            streamingData.dashManifestUrl?.let { dashUrl ->
                try {
                    val manifestXml = httpController.get(dashUrl)
                    val dashStreams = dashParser.parse(manifestXml)
                    streams.addAll(dashStreams)
                } catch (e: Exception) {
                    // DASH manifest might not be available, ignore
                }
            }
        }

        return streams
    }

    /**
     * Process a format that already has a plain URL (no cipher needed).
     * Used for Android client responses.
     */
    private fun processFormatWithoutCipher(format: StreamFormatDto): IStreamInfo? {
        val itag = format.itag ?: return null
        val streamUrl = format.url ?: return null

        // Parse stream properties
        val container = format.container?.let { Container(it) } ?: Container.Mp4
        val contentLength = format.contentLengthLong ?: 0L
        val bitrate = format.bitrate ?: 0L

        val videoCodec = format.videoCodec
        val audioCodec = format.audioCodec

        val width = format.width
        val height = format.height
        val framerate = format.fps ?: 30
        val qualityLabel = format.qualityLabel

        // Determine stream type
        return when {
            // Audio-only stream
            format.isAudioOnly || (audioCodec != null && videoCodec == null) -> {
                AudioOnlyStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    audioCodec = audioCodec ?: "unknown",
                    audioLanguage = format.audioLanguageCode?.let { code ->
                        Language(code, format.audioLanguageName ?: code)
                    },
                    isAudioLanguageDefault = format.isDefaultAudioTrack.takeIf { it }
                )
            }

            // Video-only stream
            format.isVideoOnly || (videoCodec != null && audioCodec == null) -> {
                if (width == null || height == null) return null

                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                VideoOnlyStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    videoCodec = videoCodec ?: "unknown",
                    videoQuality = quality,
                    videoResolution = Resolution(width, height)
                )
            }

            // Muxed stream (both audio and video)
            videoCodec != null && audioCodec != null -> {
                if (width == null || height == null) return null

                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                MuxedStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    audioCodec = audioCodec,
                    audioLanguage = format.audioLanguageCode?.let { code ->
                        Language(code, format.audioLanguageName ?: code)
                    },
                    isAudioLanguageDefault = format.isDefaultAudioTrack.takeIf { it },
                    videoCodec = videoCodec,
                    videoQuality = quality,
                    videoResolution = Resolution(width, height)
                )
            }

            else -> null
        }
    }

    private suspend fun processFormat(
        format: StreamFormatDto,
        cipherManifest: CipherManifest?,
        getCipherManifest: suspend () -> CipherManifest
    ): IStreamInfo? {
        val itag = format.itag ?: return null

        // Get stream URL
        var streamUrl = format.url

        // Handle cipher-protected streams
        if (streamUrl == null && format.requiresDecryption) {
            val cipherData = format.cipherData ?: return null
            val params = cipherData.parseQueryParameters()

            streamUrl = params["url"] ?: return null
            val signature = params["s"] ?: return null
            val signatureParam = params["sp"] ?: "sig"

            // Decipher the signature
            val manifest = getCipherManifest()
            val decipheredSignature = manifest.decipher(signature)

            // Append deciphered signature to URL
            streamUrl = streamUrl.setQueryParameter(signatureParam, decipheredSignature)
        }

        if (streamUrl == null) return null

        // Parse stream properties
        val container = format.container?.let { Container(it) } ?: Container.Mp4
        val contentLength = format.contentLengthLong ?: 0L
        val bitrate = format.bitrate ?: 0L

        val videoCodec = format.videoCodec
        val audioCodec = format.audioCodec

        val width = format.width
        val height = format.height
        val framerate = format.fps ?: 30
        val qualityLabel = format.qualityLabel

        // Determine stream type
        return when {
            // Audio-only stream
            format.isAudioOnly || (audioCodec != null && videoCodec == null) -> {
                AudioOnlyStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    audioCodec = audioCodec ?: "unknown",
                    audioLanguage = format.audioLanguageCode?.let { code ->
                        Language(code, format.audioLanguageName ?: code)
                    },
                    isAudioLanguageDefault = format.isDefaultAudioTrack.takeIf { it }
                )
            }

            // Video-only stream
            format.isVideoOnly || (videoCodec != null && audioCodec == null) -> {
                if (width == null || height == null) return null

                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                VideoOnlyStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    videoCodec = videoCodec ?: "unknown",
                    videoQuality = quality,
                    videoResolution = Resolution(width, height)
                )
            }

            // Muxed stream (both audio and video)
            videoCodec != null && audioCodec != null -> {
                if (width == null || height == null) return null

                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                MuxedStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    audioCodec = audioCodec,
                    audioLanguage = format.audioLanguageCode?.let { code ->
                        Language(code, format.audioLanguageName ?: code)
                    },
                    isAudioLanguageDefault = format.isDefaultAudioTrack.takeIf { it },
                    videoCodec = videoCodec,
                    videoQuality = quality,
                    videoResolution = Resolution(width, height)
                )
            }

            else -> null
        }
    }

    private fun String.parseQueryParameters(): Map<String, String> {
        return split("&").mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                java.net.URLDecoder.decode(parts[0], "UTF-8") to
                    java.net.URLDecoder.decode(parts[1], "UTF-8")
            } else null
        }.toMap()
    }

    private fun String.setQueryParameter(name: String, value: String): String {
        val hasQuery = contains("?")
        val separator = if (hasQuery) "&" else "?"
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        val encodedValue = java.net.URLEncoder.encode(value, "UTF-8")
        return "$this$separator$encodedName=$encodedValue"
    }
}
