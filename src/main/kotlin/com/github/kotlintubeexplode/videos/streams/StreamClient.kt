package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.common.Language
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.BotCheckException
import com.github.kotlintubeexplode.exceptions.RequestLimitExceededException
import com.github.kotlintubeexplode.exceptions.VideoRequiresPurchaseException
import com.github.kotlintubeexplode.exceptions.VideoUnavailableException
import com.github.kotlintubeexplode.exceptions.VideoUnplayableException
import com.github.kotlintubeexplode.internal.*
import com.github.kotlintubeexplode.internal.cipher.CipherManifest
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.common.Resolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    companion object {
        /**
         * The VR-family clients (VISIONOS, ANDROID_VR) intermittently return a SABR-degraded
         * response: YouTube strips the adaptive formats, leaving only the muxed 360p (itag 18).
         * The degradation is an intermittent, session-based experiment, so a fresh request usually
         * recovers the full set (yt-dlp #16150). Retry a degraded response up to this many times
         * before accepting it. See KNOWN_DRIFT.md (SABR degradation).
         */
        private const val MAX_SABR_DEGRADED_RETRIES = 3

        /**
         * Most stream URL checks (verifyStreamUrl) to run at once while building a manifest.
         * Matches upstream's MaxDegreeOfParallelism = 8 (YoutubeExplode prime, #978).
         */
        private const val MAX_CONCURRENT_STREAM_CHECKS = 8
    }

    /**
     * Gets the stream manifest for a video.
     *
     * @param videoId The video ID
     * @return The stream manifest containing all available streams
     * @throws BotCheckException if YouTube's bot check blocked every client (not retried)
     * @throws VideoUnavailableException if the video is unavailable (deleted, private, etc.)
     * @throws VideoUnplayableException if no client returned a playable stream
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
     * @throws BotCheckException if YouTube's bot check blocked every client
     * @throws VideoUnplayableException if no client has an HLS URL (not a live stream)
     */
    suspend fun getHttpLiveStreamUrl(videoId: VideoId): String {
        // The web watch page no longer carries hlsManifestUrl for live streams, so read it from
        // the cipher-less player responses, as upstream 6.6.2 does (GetPlayerResponseAsync).
        // Any client's HLS URL will do, so a failed request moves on to the next client. If none
        // has a URL and a request failed on the network, that failure is the real answer; a rate
        // limit applies to the whole IP, so it stops the chain at once. If every client that
        // answered was bot-checked, we don't know whether it's live, so that's a BotCheckException.
        val responses = mutableListOf<PlayerResponseDto>()
        var networkFailure: Exception? = null
        for (client in cipherlessClients(videoId)) {
            val response = try {
                client.request()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RequestLimitExceededException) {
                throw e
            } catch (e: Exception) {
                if (e.isNetworkFailure()) networkFailure = networkFailure ?: e
                continue
            }
            response.streamingData?.hlsManifestUrl?.let { return it }
            responses += response
        }

        networkFailure?.let { throw it }
        val firstReason = responses.firstNotNullOfOrNull { it.playabilityStatus?.reason }
        val message = "HLS manifest URL not available for video '${videoId.value}': " +
            (firstReason ?: "not a live stream")
        if (responses.allBotChecked()) throw BotCheckException(message)
        throw VideoUnplayableException(message)
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
        // Cleans only the basename. NOTE: this does NOT make an arbitrary caller-built path
        // traversal-safe — a title containing `/../` shoved into `filePath` can still escape (see
        // toSafeFilePath). For untrusted YouTube titles, prefer the
        // download(streamInfo, directory, fileName) overload, which sanitizes the name.
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
     * Downloads a stream into [directory] under a traversal-safe [fileName].
     *
     * Unlike the string-path overload, [fileName] is sanitized and cannot escape [directory], so it
     * is safe to pass an untrusted YouTube-derived title (e.g. the video title plus ".mp4").
     * [directory] is trusted and used as-is.
     */
    suspend fun download(
        streamInfo: IStreamInfo,
        directory: java.io.File,
        fileName: String,
        onProgress: ((Double) -> Unit)? = null
    ) {
        download(streamInfo, safeFileIn(directory, fileName).path, onProgress)
    }

    /**
     * Gets stream information using a multi-client approach (following C# YoutubeExplode 6.6.2).
     *
     * Order of attempts (the first client that yields usable streams wins):
     * 1. VISIONOS - Primary. poToken-free plain URLs. Upstream 6.6.2 made it primary because
     *    ANDROID_VR now hits YouTube's bot check for many videos.
     * 2. ANDROID_VR - Former primary, kept as a plain-URL fallback (it still serves many videos)
     * 3. ANDROID (mobile) - Legacy muxed itag-18 fallback; the only poToken-free audio path for
     *    made-for-kids videos (VISIONOS/ANDROID_VR report those UNPLAYABLE; iOS is video-only)
     * 4. iOS - Plain-URL client from a different client family
     * 5. TV Embedded client - For age-restricted videos (requires cipher)
     *
     * A failed request just moves on to the next client. Stream-processing errors are not caught
     * here, so a parser regression surfaces instead of being masked. The video is reported
     * unavailable only when every cipher-less client says so: YouTube's bot check (LOGIN_REQUIRED
     * with no videoDetails) is indistinguishable from an unavailable video in a single response.
     * If no client yields a stream the video is reported unplayable. Matching upstream, there is no
     * watch-page/web-client stream fallback.
     */
    private suspend fun getStreamInfos(videoId: VideoId): List<IStreamInfo> {
        val responses = mutableListOf<PlayerResponseDto>()
        for (client in cipherlessClients(videoId)) {
            val response = (if (client.sabrRetry) fetchWithSabrRetry(client.request) else fetchOrNull(client.request))
                ?: continue

            // Pay-to-play videos aren't "OK": YouTube advertises a free preview/trailer id inside
            // playabilityStatus.errorScreen. Upstream (StreamClient.cs GetStreamInfosAsync) throws
            // VideoRequiresPurchaseException for these before any stream extraction.
            response.playabilityStatus?.previewVideoId?.let { previewId ->
                throw VideoRequiresPurchaseException(
                    "Video '${videoId.value}' requires purchase and cannot be played.",
                    VideoId.parse(previewId)
                )
            }

            responses += response
            val streams = tryProcessAndroidStreams(response)
            if (streams.isNotEmpty()) {
                return streams
            }
        }

        // A genuinely unavailable video (deleted/private/region-blocked) is reported as the specific
        // VideoUnavailableException carrying playabilityStatus.reason, not a generic "no streams".
        // isAvailable mirrors upstream PlayerResponse.IsAvailable (status != "error" && videoDetails
        // present). Like upstream 6.6.2, only trust it when no client saw the video as available:
        // a bot-checked response fails isAvailable too. If every client was bot-checked, it's a
        // BotCheckException instead (beyond upstream). Throw before the TV fallback, which has
        // nothing to recover for such a video.
        if (responses.isNotEmpty() && responses.none { it.isAvailable }) {
            throw unavailableVideoException(videoId, responses)
        }

        // The video is available but no cipher-less client exposed usable streams: either
        // age-restricted, or the upstream "playable but no streams" case. Fall back to the TVHTML5
        // embedded (cipher) client.
        val tvStreams = tryTVEmbeddedClient(videoId)
        if (tvStreams.isNotEmpty()) {
            return tvStreams
        }

        // There is no further watch-page/web-client stream fallback: the WEB client's stream URLs
        // now require a PO token and 403 on download. Fail loudly to match upstream. See
        // KNOWN_DRIFT #9.
        throw VideoUnplayableException(
            "Video '${videoId.value}' does not contain any playable streams."
        )
    }

    /**
     * A cipher-less player request. [sabrRetry] marks the VR-family clients, whose responses
     * YouTube's SABR experiment can degrade (see [fetchWithSabrRetry]).
     */
    private class CipherlessClient(val request: suspend () -> PlayerResponseDto, val sabrRetry: Boolean)

    /** The cipher-less clients in chain order: VISIONOS, ANDROID_VR, ANDROID, iOS. */
    private fun cipherlessClients(videoId: VideoId): List<CipherlessClient> = listOf(
        CipherlessClient({ videoController.getPlayerResponseViaVisionosClient(videoId) }, sabrRetry = true),
        CipherlessClient({ videoController.getPlayerResponseViaAndroidClient(videoId) }, sabrRetry = true),
        CipherlessClient({ videoController.getPlayerResponseViaAndroidMobileClient(videoId) }, sabrRetry = false),
        CipherlessClient({ videoController.getPlayerResponseViaIosClient(videoId) }, sabrRetry = false),
    )

    /**
     * Runs a player request for the stream chain. A failure that's specific to this client (HTTP
     * 4xx, unreadable response) returns null so the chain moves to the next client.
     *
     * A network failure, 5xx or rate limit is rethrown ([isNetworkFailure]). It says nothing about
     * the video, the other clients share the host, and falling past VISIONOS on a blip would quietly
     * hand back a worse manifest (ANDROID_VR bot-checked, ANDROID 360p only). Rethrowing lets
     * getManifest retry it, or surface RequestLimitExceededException, instead of the chain ending
     * in a misleading VideoUnplayableException or VideoUnavailableException.
     */
    private suspend fun fetchOrNull(fetch: suspend () -> PlayerResponseDto): PlayerResponseDto? {
        return try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isNetworkFailure()) throw e
            null
        }
    }

    /**
     * Runs a player request, retrying when YouTube returns a SABR-degraded response (adaptive
     * formats stripped, only the muxed 360p left). The degradation is an intermittent,
     * session-based experiment, so a fresh request usually recovers the full format set; if every
     * retry stays degraded we return the last response (360p playback beats none).
     * See [isSabrDegraded] and KNOWN_DRIFT.md.
     */
    private suspend fun fetchWithSabrRetry(fetch: suspend () -> PlayerResponseDto): PlayerResponseDto? {
        var response = fetchOrNull(fetch)
        var attempt = 0
        while (response != null && response.isSabrDegraded() && attempt < MAX_SABR_DEGRADED_RETRIES) {
            attempt++
            kotlinx.coroutines.delay(200L * attempt)
            val retry = fetchOrNull(fetch)
            if (retry != null) {
                response = retry
            }
        }
        return response
    }

    /**
     * True when a playable VR-family response has had its adaptive formats stripped by YouTube's
     * SABR experiment, leaving only a muxed format (typically 360p itag 18). A healthy response
     * always carries adaptive formats, so "playable, muxed present, but no adaptive formats" is the
     * degradation signal — distinct from a genuinely stream-less response (which the normal
     * empty-streams fallback already handles).
     */
    private fun PlayerResponseDto.isSabrDegraded(): Boolean {
        if (playabilityStatus?.isPlayable != true) return false
        val sd = streamingData ?: return false
        return sd.adaptiveFormats.isNullOrEmpty() && !sd.formats.isNullOrEmpty()
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
        // Capped at MAX_CONCURRENT_STREAM_CHECKS: googlevideo is HTTP/1.1, so each in-flight check
        // is its own connection, and an uncapped burst of ~25 is what weak networks drop.
        val permits = Semaphore(MAX_CONCURRENT_STREAM_CHECKS)
        val processedFormats = coroutineScope {
            streamingData.allFormats
                .filter { it.url != null }
                .map { format -> async { permits.withPermit { processFormatWithoutCipher(format) } } }
                .awaitAll()
        }
        streams.addAll(processedFormats.filterNotNull())

        // Also try DASH manifest if available
        streamingData.dashManifestUrl?.let { dashUrl ->
            try {
                val manifestXml = httpController.get(requireGoogleHttpsUrl(dashUrl))
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

            // TV Embedded returns ciphered streams. Process in parallel, with the same cap — see
            // the Android-client comment above for rationale.
            val permits = Semaphore(MAX_CONCURRENT_STREAM_CHECKS)
            val processedFormats = coroutineScope {
                streamingData.allFormats
                    .map { format ->
                        async { permits.withPermit { processFormat(format, cipherManifest) { cipherManifest } } }
                    }
                    .awaitAll()
            }
            streams.addAll(processedFormats.filterNotNull())

            // Also try DASH manifest
            streamingData.dashManifestUrl?.let { dashUrl ->
                try {
                    val manifestXml = httpController.get(requireGoogleHttpsUrl(dashUrl))
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
