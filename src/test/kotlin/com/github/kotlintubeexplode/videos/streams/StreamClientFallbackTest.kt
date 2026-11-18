package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.VideoRequiresPurchaseException
import com.github.kotlintubeexplode.exceptions.VideoUnavailableException
import com.github.kotlintubeexplode.exceptions.VideoUnplayableException
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.VideoPageParser
import com.github.kotlintubeexplode.internal.cipher.CipherManifest
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import com.github.kotlintubeexplode.internal.dto.VideoDetailsDto
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

@DisplayName("StreamClient client fallback")
class StreamClientFallbackTest {

    private val videoId = VideoId.parse("dQw4w9WgXcQ")

    @Nested
    @DisplayName("cipher-less client returns a playable response with no streams")
    inner class NoStreamsFallbackTests {

        @Test
        fun `should fall back to TV Embedded client when Android client yields zero streams`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()

            // Android (cipher-less) client: status OK + videoDetails present (so isAvailable==true),
            // but exposes NO streams. This is the upstream "does not contain any playable streams"
            // edge case. It is NOT age-restricted, so the current isAgeRestricted heuristic is false.
            val androidResponse = PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                videoDetails = VideoDetailsDto(videoId = videoId.value, title = "test"),
                streamingData = StreamingDataDto()
            )
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns androidResponse

            // TV Embedded (cipher) client: returns one plain-URL audio stream.
            val tvResponse = PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                videoDetails = VideoDetailsDto(videoId = videoId.value, title = "test"),
                streamingData = StreamingDataDto(
                    adaptiveFormats = listOf(
                        StreamFormatDto(
                            itag = 140,
                            url = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=140",
                            mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                            bitrate = 128_000,
                            contentLength = "1000"
                        )
                    )
                )
            )
            coEvery { videoController.getCipherManifest() } returns CipherManifest.EMPTY
            coEvery { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) } returns tvResponse

            // Stream-URL verification (verifyStreamUrl range probe) succeeds.
            coEvery { http.getContentLength(any()) } returns 2L

            // The dead web-client fallback must NOT be reached; fail loudly if it is.
            coEvery { http.getWithRetry(any(), any(), any()) } throws
                IOException("web-client fallback should not be reached")

            val streamClient = StreamClient(http, videoController)

            val manifest = streamClient.getManifest(videoId)

            manifest.streams shouldHaveSize 1
            manifest.streams.first().shouldBeInstanceOf<AudioOnlyStreamInfo>()
            coVerify(exactly = 1) { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) }
        }
    }

    @Nested
    @DisplayName("cipher-less client reports the video unavailable")
    inner class UnavailableTests {

        @Test
        fun `should throw VideoUnavailableException carrying the reason for an unavailable video`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()

            // Android (cipher-less) client: status "error" + no videoDetails => isAvailable == false
            // (deleted / private / region-blocked), carrying the real reason. Upstream surfaces this
            // as the specific VideoUnavailableException with the reason, not a generic "no streams".
            val androidResponse = PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "error", reason = "This video is private"),
                videoDetails = null,
                streamingData = StreamingDataDto()
            )
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns androidResponse

            // An unavailable video must NOT trigger the TV-embedded fallback or the dead web path.
            coEvery { videoController.getCipherManifest() } returns CipherManifest.EMPTY
            coEvery { http.getWithRetry(any(), any(), any()) } throws
                IOException("web-client fallback should not be reached")

            val streamClient = StreamClient(http, videoController)

            val ex = shouldThrow<VideoUnavailableException> { streamClient.getManifest(videoId) }
            ex.message shouldContain "This video is private"
            coVerify(exactly = 0) { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) }
        }
    }

    private fun emptyPlayable() = PlayerResponseDto(
        playabilityStatus = PlayabilityStatusDto(status = "OK"),
        videoDetails = VideoDetailsDto(videoId = videoId.value, title = "test"),
        streamingData = StreamingDataDto()
    )

    private fun oneAudioStream() = PlayerResponseDto(
        playabilityStatus = PlayabilityStatusDto(status = "OK"),
        videoDetails = VideoDetailsDto(videoId = videoId.value, title = "test"),
        streamingData = StreamingDataDto(
            adaptiveFormats = listOf(
                StreamFormatDto(
                    itag = 140,
                    url = "https://rr1---sn-test.googlevideo.com/videoplayback?itag=140",
                    mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                    bitrate = 128_000,
                    contentLength = "1000"
                )
            )
        )
    )

    /** A deleted/private video: status "error" and no videoDetails, so isAvailable == false. */
    private fun privateVideo() = PlayerResponseDto(
        playabilityStatus = PlayabilityStatusDto(status = "error", reason = "This video is private"),
        videoDetails = null,
        streamingData = StreamingDataDto()
    )

    /**
     * YouTube's bot check, as captured live from ANDROID_VR on 2026-11-18: LOGIN_REQUIRED with no
     * videoDetails and no streamingData, so isAvailable == false even though the video exists.
     */
    private fun botChecked() = PlayerResponseDto(
        playabilityStatus = PlayabilityStatusDto(
            status = "LOGIN_REQUIRED",
            reason = "Sign in to confirm you’re not a bot"
        ),
        videoDetails = null,
        streamingData = null
    )

    @Nested
    @DisplayName("VISIONOS and iOS fallback ordering")
    inner class VisionosIosFallbackTests {

        @Test
        fun `should fall back to VISIONOS when Android yields zero streams`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns emptyPlayable()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns oneAudioStream()
            coEvery { http.getContentLength(any()) } returns 2L

            val streamClient = StreamClient(http, videoController)
            val manifest = streamClient.getManifest(videoId)

            manifest.streams shouldHaveSize 1
            coVerify(exactly = 1) { videoController.getPlayerResponseViaVisionosClient(any()) }
            // VISIONOS succeeded, so iOS and TV-embedded must not be tried.
            coVerify(exactly = 0) { videoController.getPlayerResponseViaIosClient(any()) }
            coVerify(exactly = 0) { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) }
        }

        @Test
        fun `should fall back to iOS when Android and VISIONOS both yield zero streams`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns emptyPlayable()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns emptyPlayable()
            coEvery { videoController.getPlayerResponseViaIosClient(any()) } returns oneAudioStream()
            coEvery { http.getContentLength(any()) } returns 2L

            val streamClient = StreamClient(http, videoController)
            val manifest = streamClient.getManifest(videoId)

            manifest.streams shouldHaveSize 1
            coVerify(exactly = 1) { videoController.getPlayerResponseViaIosClient(any()) }
            coVerify(exactly = 0) { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) }
        }

        // Spec change (v1.3.2, upstream 6.6.2): VISIONOS is now the primary client. This test used
        // to assert ANDROID_VR -> VISIONOS -> iOS -> TV.
        @Test
        fun `should try clients in order VISIONOS, ANDROID_VR, ANDROID, iOS, then TV embedded`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns emptyPlayable()
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns emptyPlayable()
            coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns emptyPlayable()
            coEvery { videoController.getPlayerResponseViaIosClient(any()) } returns emptyPlayable()
            coEvery { videoController.getCipherManifest() } returns CipherManifest.EMPTY
            coEvery { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) } returns emptyPlayable()
            coEvery { http.getWithRetry(any(), any(), any()) } throws
                IOException("web-client fallback should not be reached")

            val streamClient = StreamClient(http, videoController)

            shouldThrow<VideoUnplayableException> { streamClient.getManifest(videoId) }

            coVerifyOrder {
                videoController.getPlayerResponseViaVisionosClient(any())
                videoController.getPlayerResponseViaAndroidClient(any(), any())
                videoController.getPlayerResponseViaAndroidMobileClient(any())
                videoController.getPlayerResponseViaIosClient(any())
                videoController.getPlayerResponseViaTVEmbeddedClient(any(), any())
            }
        }

        // Spec change (v1.3.2): the primary client is VISIONOS, so it's the one whose success
        // short-circuits the chain. This test used to assert the same for ANDROID_VR.
        @Test
        fun `should not try any other client when VISIONOS yields streams`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns oneAudioStream()
            coEvery { http.getContentLength(any()) } returns 2L

            val streamClient = StreamClient(http, videoController)
            streamClient.getManifest(videoId)

            coVerify(exactly = 0) { videoController.getPlayerResponseViaAndroidClient(any(), any()) }
            coVerify(exactly = 0) { videoController.getPlayerResponseViaAndroidMobileClient(any()) }
            coVerify(exactly = 0) { videoController.getPlayerResponseViaIosClient(any()) }
            coVerify(exactly = 0) { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) }
        }

        // Spec change (v1.3.2, upstream 6.6.2): one client reporting "unavailable" is no longer
        // trusted on its own, because YouTube's bot check looks exactly like that. This test used
        // to assert VideoUnavailableException was thrown before VISIONOS or iOS were tried.
        @Test
        fun `should throw VideoUnavailableException only after every cipher-less client reports unavailable`() =
            runTest {
                val http = mockk<HttpController>()
                val videoController = mockk<VideoController>()
                coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns privateVideo()
                coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns privateVideo()
                coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns privateVideo()
                coEvery { videoController.getPlayerResponseViaIosClient(any()) } returns privateVideo()

                val streamClient = StreamClient(http, videoController)

                val ex = shouldThrow<VideoUnavailableException> { streamClient.getManifest(videoId) }
                ex.message shouldContain "This video is private"
                coVerify(exactly = 1) { videoController.getPlayerResponseViaVisionosClient(any()) }
                coVerify(exactly = 1) { videoController.getPlayerResponseViaAndroidClient(any(), any()) }
                coVerify(exactly = 1) { videoController.getPlayerResponseViaAndroidMobileClient(any()) }
                coVerify(exactly = 1) { videoController.getPlayerResponseViaIosClient(any()) }
                coVerify(exactly = 0) { videoController.getPlayerResponseViaTVEmbeddedClient(any(), any()) }
            }
    }

    @Nested
    @DisplayName("VISIONOS-primary chain (upstream 6.6.2)")
    inner class VisionosPrimaryTests {
        // ANDROID_VR now hits YouTube's bot check for many videos. That response has no
        // videoDetails, so isAvailable is false, and the old chain threw VideoUnavailableException
        // on it before trying VISIONOS/ANDROID/iOS, which still serve those videos.

        private val degraded = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(status = "OK"),
            videoDetails = VideoDetailsDto(videoId = videoId.value, title = "test"),
            streamingData = StreamingDataDto(
                formats = listOf(
                    StreamFormatDto(
                        itag = 18,
                        url = "https://rr---sn.googlevideo.com/videoplayback?itag=18",
                        mimeType = "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                        bitrate = 500_000, width = 640, height = 360, contentLength = "1000",
                        audioQuality = "AUDIO_QUALITY_LOW"
                    )
                ),
                adaptiveFormats = emptyList()
            )
        )

        private val healthy = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(status = "OK"),
            videoDetails = VideoDetailsDto(videoId = videoId.value, title = "test"),
            streamingData = StreamingDataDto(
                adaptiveFormats = listOf(
                    StreamFormatDto(
                        itag = 140,
                        url = "https://rr---sn.googlevideo.com/videoplayback?itag=140",
                        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"", bitrate = 128_000, contentLength = "1000"
                    ),
                    StreamFormatDto(
                        itag = 137,
                        url = "https://rr---sn.googlevideo.com/videoplayback?itag=137",
                        mimeType = "video/mp4; codecs=\"avc1.640028\"",
                        bitrate = 2_500_000, width = 1920, height = 1080, contentLength = "5000"
                    )
                )
            )
        )

        @Test
        fun `should fall through a bot-checked ANDROID_VR response to the next client`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } throws IOException("visionos down")
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns botChecked()
            coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns oneAudioStream()
            coEvery { http.getContentLength(any()) } returns 2L

            val streamClient = StreamClient(http, videoController)
            val manifest = shouldNotThrowAny { streamClient.getManifest(videoId) }

            manifest.streams shouldHaveSize 1
            coVerify(exactly = 1) { videoController.getPlayerResponseViaAndroidMobileClient(any()) }
        }

        @Test
        fun `should retry a SABR-degraded VISIONOS response`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returnsMany listOf(degraded, healthy)
            coEvery { http.getContentLength(any()) } returns 2L

            val streamClient = StreamClient(http, videoController)
            val manifest = streamClient.getManifest(videoId)

            manifest.streams.filterIsInstance<AudioOnlyStreamInfo>().size shouldBe 1
            manifest.streams.filterIsInstance<VideoOnlyStreamInfo>().size shouldBe 1
            manifest.streams.filterIsInstance<MuxedStreamInfo>().size shouldBe 0
            coVerify(exactly = 2) { videoController.getPlayerResponseViaVisionosClient(any()) }
            coVerify(exactly = 0) { videoController.getPlayerResponseViaAndroidClient(any(), any()) }
        }

        @Test
        fun `should throw VideoRequiresPurchaseException when VISIONOS exposes a preview id`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            val payToPlayResponse = VideoPageParser().parsePlayerResponse(
                """
                {
                  "playabilityStatus": {
                    "status": "UNPLAYABLE",
                    "reason": "This video requires payment to watch.",
                    "errorScreen": {
                      "playerLegacyDesktopYpcTrailerRenderer": { "trailerVideoId": "9bZkp7q19f0" }
                    }
                  }
                }
                """.trimIndent()
            )
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns payToPlayResponse

            val streamClient = StreamClient(http, videoController)

            val ex = shouldThrow<VideoRequiresPurchaseException> { streamClient.getManifest(videoId) }
            ex.previewVideoId.toString() shouldBe "9bZkp7q19f0"
            coVerify(exactly = 0) { videoController.getPlayerResponseViaAndroidClient(any(), any()) }
        }
    }
}
