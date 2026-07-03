package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.common.Language
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.VideoRequiresPurchaseException
import com.github.kotlintubeexplode.exceptions.VideoUnplayableException
import com.github.kotlintubeexplode.internal.*
import com.github.kotlintubeexplode.internal.cipher.CipherManifest
import com.github.kotlintubeexplode.internal.cipher.PlayerScriptParser
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import com.github.kotlintubeexplode.common.Resolution
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
            } catch (e: java.io.IOException) {
                // Only retry transient network failures (IOException, incl. our HttpException).
                // Non-IOException failures — parser regressions (VideoParseException),
                // deserialization errors, and coroutine CancellationException — must surface
                // immediately instead of being retried 5x and masked. Mirrors upstream
                // StreamClient.GetManifestAsync, which retries only when
                // `ex is HttpRequestException or IOException`.
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
            ?: throw VideoUnplayableException(
                "HLS manifest URL not available for video '${videoId.value}': " +
                    (playerResponse.playabilityStatus?.reason ?: "unplayable")
            )
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
        // Sanitize basename only (preserves caller-controlled directory). Defends against
        // common consumer pattern `download(stream, "$baseDir/$videoTitle.mp4")` where the
        // title is derived from YouTube metadata and may contain `/`, `..`, `:`, etc.
        val file = toSafeFilePath(filePath)

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
     * 2. TV Embedded client - For age-restricted and otherwise-unplayable videos (requires cipher)
     *
     * If neither client yields a stream the video is reported unplayable. Matching upstream,
     * there is no third watch-page/web-client stream fallback.
     */
    private suspend fun getStreamInfos(videoId: VideoId): List<IStreamInfo> {
        // 1. Try Android client first (no cipher needed for most streams)
        val androidResponse = tryAndroidClient(videoId)

        // Pay-to-play videos aren't "OK": YouTube advertises a free preview/trailer id inside
        // playabilityStatus.errorScreen. Upstream (StreamClient.cs GetStreamInfosAsync, L214-221)
        // throws VideoRequiresPurchaseException for these before any stream extraction.
        androidResponse?.playabilityStatus?.previewVideoId?.let { previewId ->
            throw VideoRequiresPurchaseException(
                "Video '${videoId.value}' requires purchase and cannot be played.",
                VideoId.parse(previewId)
            )
        }

        if (androidResponse != null) {
            val androidStreams = tryProcessAndroidStreams(androidResponse)
            if (androidStreams.isNotEmpty()) {
                return androidStreams
            }
        }

        // The cipher-less Android client yielded no usable streams. Upstream (StreamClient.cs
        // GetStreamInfosAsync) falls back to the TVHTML5 embedded (cipher) client on ANY
        // VideoUnplayableException that is NOT a VideoUnavailableException — i.e. whenever the
        // video isn't deleted/private/region-blocked. That covers age-restricted videos AND
        // videos that report playable but expose no streams to the cipher-less client (the
        // "does not contain any playable streams" case). The old isAgeRestricted string
        // heuristic missed the latter and dropped those videos straight to the dead web path.
        // isAvailable mirrors upstream PlayerResponse.IsAvailable (status != "error" && details);
        // age-restricted responses stay isAvailable == true, so this preserves that fallback.
        val isUnavailable = androidResponse != null && !androidResponse.isAvailable

        // 2. Fall back to the TV Embedded (cipher) client unless the video is definitively unavailable.
        if (!isUnavailable) {
            val tvStreams = tryTVEmbeddedClient(videoId)
            if (tvStreams.isNotEmpty()) {
                return tvStreams
            }
        }

        // Upstream stops here. Its stream extraction (StreamClient.cs GetStreamInfosAsync,
        // tag 6.6 AND branch prime) tries exactly two clients — cipher-less ANDROID_VR, then
        // TVHTML5_SIMPLY_EMBEDDED_PLAYER on VideoUnplayableException — and then reports the
        // video unplayable. There is no third watch-page/web-client stream fallback: the WEB
        // client's stream URLs now require a PO token and 403 on download, so it can only ever
        // return undownloadable streams (or, once verifyStreamUrl drops them, an empty manifest
        // that masks the real failure). Fail loudly to match upstream. See KNOWN_DRIFT #9.
        throw VideoUnplayableException(
            "Video '${videoId.value}' does not contain any playable streams."
        )
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

        // Process formats in parallel — each format may issue HEAD + range-fetch verifications
        // (verifyStreamUrl), so serial processing makes manifest fetch O(n) HTTP round trips.
        val processedFormats = coroutineScope {
            streamingData.allFormats
                .filter { it.url != null }
                .map { format -> async { processFormatWithoutCipher(format) } }
                .awaitAll()
        }
        streams.addAll(processedFormats.filterNotNull())

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

            // TV Embedded returns ciphered streams. Process in parallel — see Android-client
            // comment above for rationale.
            val processedFormats = coroutineScope {
                streamingData.allFormats
                    .map { format -> async { processFormat(format, cipherManifest) { cipherManifest } } }
                    .awaitAll()
            }
            streams.addAll(processedFormats.filterNotNull())

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
     * Process a format that already has a plain URL (no cipher needed).
     * Used for Android client responses.
     */
    private suspend fun processFormatWithoutCipher(format: StreamFormatDto): IStreamInfo? {
        val itag = format.itag ?: return null
        val streamUrl = format.url ?: return null

        // Parse stream properties
        val container = format.container?.let { Container(it) } ?: Container.Mp4
        // Verify the URL is fetchable and resolve the actual content length per upstream (drift #33).
        // Drops streams with stale/expired URLs or mismatched length metadata.
        val contentLength = verifyStreamUrl(httpController, streamUrl, format.contentLengthLong)
            ?: return null
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
                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                // Upstream keeps streams with missing width/height, falling back to the
                // quality's default resolution rather than dropping them (StreamClient.cs).
                val resolution = if (width != null && height != null) {
                    Resolution(width, height)
                } else {
                    quality.getDefaultResolution()
                }

                VideoOnlyStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    videoCodec = videoCodec ?: "unknown",
                    videoQuality = quality,
                    videoResolution = resolution,
                    isVideoUpscaled = format.isVideoUpscaled
                )
            }

            // Muxed stream (both audio and video)
            videoCodec != null && audioCodec != null -> {
                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                // Upstream keeps streams with missing width/height, falling back to the
                // quality's default resolution rather than dropping them (StreamClient.cs).
                val resolution = if (width != null && height != null) {
                    Resolution(width, height)
                } else {
                    quality.getDefaultResolution()
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
                    videoResolution = resolution,
                    isVideoUpscaled = format.isVideoUpscaled
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
        // Verify the URL is fetchable and resolve the actual content length per upstream (drift #33).
        // Drops streams with stale/expired URLs or mismatched length metadata.
        val contentLength = verifyStreamUrl(httpController, streamUrl, format.contentLengthLong)
            ?: return null
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
                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                // Upstream keeps streams with missing width/height, falling back to the
                // quality's default resolution rather than dropping them (StreamClient.cs).
                val resolution = if (width != null && height != null) {
                    Resolution(width, height)
                } else {
                    quality.getDefaultResolution()
                }

                VideoOnlyStreamInfo(
                    url = streamUrl,
                    container = container,
                    size = FileSize(contentLength),
                    bitrate = Bitrate(bitrate),
                    videoCodec = videoCodec ?: "unknown",
                    videoQuality = quality,
                    videoResolution = resolution,
                    isVideoUpscaled = format.isVideoUpscaled
                )
            }

            // Muxed stream (both audio and video)
            videoCodec != null && audioCodec != null -> {
                val quality = if (qualityLabel != null) {
                    VideoQuality.fromLabel(qualityLabel, framerate)
                } else {
                    VideoQuality.fromItag(itag, framerate)
                }

                // Upstream keeps streams with missing width/height, falling back to the
                // quality's default resolution rather than dropping them (StreamClient.cs).
                val resolution = if (width != null && height != null) {
                    Resolution(width, height)
                } else {
                    quality.getDefaultResolution()
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
                    videoResolution = resolution,
                    isVideoUpscaled = format.isVideoUpscaled
                )
            }

            else -> null
        }
    }

}
