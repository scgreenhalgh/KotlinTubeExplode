package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.BotCheckException
import com.github.kotlintubeexplode.exceptions.RequestLimitExceededException
import com.github.kotlintubeexplode.exceptions.VideoRequiresPurchaseException
import com.github.kotlintubeexplode.exceptions.VideoUnplayableException
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.VideoPageParser
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import com.github.kotlintubeexplode.internal.dto.VideoDetailsDto
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException

@DisplayName("StreamClient")
class StreamClientTest {

    @Nested
    @DisplayName("pay-to-play")
    inner class PayToPlayTests {
        // Mirror of upstream StreamClient.cs GetStreamInfosAsync (6.6/prime L214-221): a player
        // response advertising a free preview/trailer id inside playabilityStatus.errorScreen
        // must surface as VideoRequiresPurchaseException, not a generic unplayable/parse error.
        // KNOWN_DRIFT #5.

        @Test
        fun `getManifest throws VideoRequiresPurchaseException when errorScreen exposes a preview id`() =
            runTest {
                val http = mockk<HttpController>(relaxed = true)
                val videoController = mockk<VideoController>()
                val streamClient = StreamClient(http, videoController)

                // Parse via the production parser so this compiles before the errorScreen field
                // exists (ignoreUnknownKeys) and only yields a preview id once the port lands.
                val payToPlayJson = """
                    {
                      "playabilityStatus": {
                        "status": "UNPLAYABLE",
                        "reason": "This video requires payment to watch.",
                        "errorScreen": {
                          "playerLegacyDesktopYpcTrailerRenderer": {
                            "trailerVideoId": "9bZkp7q19f0"
                          }
                        }
                      }
                    }
                """.trimIndent()
                val payToPlayResponse = VideoPageParser().parsePlayerResponse(payToPlayJson)

                coEvery {
                    videoController.getPlayerResponseViaAndroidClient(any(), any())
                } returns payToPlayResponse

                val ex = shouldThrow<VideoRequiresPurchaseException> {
                    streamClient.getManifest(VideoId("p3dDcKOFXQg"))
                }

                // previewVideoId is String today, VideoId after the fix; toString() bridges both.
                ex.previewVideoId.toString() shouldBe "9bZkp7q19f0"
            }
    }

    @Nested
    @DisplayName("HLS live stream URL (upstream 6.6.2)")
    inner class HttpLiveStreamTests {
        // The web watch page no longer carries hlsManifestUrl for live streams (checked live
        // 2026-11-18 on 4xDzrJKXOOY: status OK, isLive, no HLS URL), so getHttpLiveStreamUrl failed
        // for every live video. Upstream reads it from its VISIONOS -> ANDROID player responses.

        private val videoId = VideoId("4xDzrJKXOOY")
        private val hlsUrl = "https://manifest.googlevideo.com/api/manifest/hls_variant/id/x/file/index.m3u8"

        private fun live(hls: String?) = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(status = "OK"),
            videoDetails = VideoDetailsDto(videoId = videoId.value, title = "live"),
            streamingData = StreamingDataDto(hlsManifestUrl = hls)
        )

        private fun botChecked() = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(
                status = "LOGIN_REQUIRED",
                reason = "Sign in to confirm you’re not a bot"
            )
        )

        @Test
        fun `should read the HLS URL from the VISIONOS player response`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns live(hlsUrl)
            coEvery { http.getWithRetry(any(), any(), any()) } throws IOException("watch page has no HLS URL")

            val url = shouldNotThrowAny { StreamClient(http, videoController).getHttpLiveStreamUrl(videoId) }

            url shouldBe hlsUrl
        }

        @Test
        fun `should try the next client when VISIONOS has no HLS URL`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns botChecked()
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns live(hlsUrl)
            coEvery { http.getWithRetry(any(), any(), any()) } throws IOException("watch page has no HLS URL")

            val url = shouldNotThrowAny { StreamClient(http, videoController).getHttpLiveStreamUrl(videoId) }

            url shouldBe hlsUrl
        }

        @Test
        fun `should throw VideoUnplayableException when no client has an HLS URL`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns live(null)
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns live(null)
            coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns live(null)
            coEvery { videoController.getPlayerResponseViaIosClient(any()) } returns live(null)
            coEvery { http.getWithRetry(any(), any(), any()) } throws IOException("watch page has no HLS URL")

            val ex = shouldThrow<VideoUnplayableException> {
                StreamClient(http, videoController).getHttpLiveStreamUrl(videoId)
            }

            ex.message shouldContain videoId.value
        }

        // v1.3.3: any client's HLS URL is as good as another's, so a network error on one client
        // moves on to the next. But if none has a URL, that error is the answer, not "not a live
        // stream". A rate limit applies to the whole IP, so it stops the chain at once.

        @Test
        fun `should surface a network error instead of reporting no HLS URL`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } throws
                SocketTimeoutException("connect timed out")
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns live(null)
            coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns live(null)
            coEvery { videoController.getPlayerResponseViaIosClient(any()) } returns live(null)

            shouldThrow<SocketTimeoutException> {
                StreamClient(http, videoController).getHttpLiveStreamUrl(videoId)
            }
        }

        // Guard (passes before and after): a network error on one client still lets the next answer.
        @Test
        fun `should use the next client's HLS URL after a network error`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } throws
                SocketTimeoutException("connect timed out")
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns live(hlsUrl)

            StreamClient(http, videoController).getHttpLiveStreamUrl(videoId) shouldBe hlsUrl
        }

        @Test
        fun `should surface a rate limit immediately`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } throws
                RequestLimitExceededException("Exceeded request rate limit.")

            shouldThrow<RequestLimitExceededException> {
                StreamClient(http, videoController).getHttpLiveStreamUrl(videoId)
            }
            coVerify(exactly = 0) { videoController.getPlayerResponseViaAndroidClient(any(), any()) }
        }
    }

    @Nested
    @DisplayName("HLS bot check (v1.4.0)")
    inner class HttpLiveStreamBotCheckTests {

        private val videoId = VideoId("jfKfPfyJRdk")

        private fun botChecked() = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(
                status = "LOGIN_REQUIRED",
                reason = "Sign in to confirm you’re not a bot"
            )
        )

        private fun notLive() = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(status = "OK"),
            videoDetails = VideoDetailsDto(videoId = videoId.value, title = "not live"),
            streamingData = StreamingDataDto(hlsManifestUrl = null)
        )

        @Test
        fun `should throw BotCheckException when every client is bot-checked`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns botChecked()
            coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns botChecked()
            coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns botChecked()
            coEvery { videoController.getPlayerResponseViaIosClient(any()) } returns botChecked()

            val ex = shouldThrow<BotCheckException> {
                StreamClient(http, videoController).getHttpLiveStreamUrl(videoId)
            }

            ex.message shouldContain videoId.value
            ex.message shouldContain "not a bot"
        }

        // Guard (passes before and after): one client saw the video and it isn't live, so that's
        // the answer, not a bot check.
        @Test
        fun `should throw a plain VideoUnplayableException when one client answers and the rest are bot-checked`() =
            runTest {
                val http = mockk<HttpController>()
                val videoController = mockk<VideoController>()
                coEvery { videoController.getPlayerResponseViaVisionosClient(any()) } returns botChecked()
                coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns botChecked()
                coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns notLive()
                coEvery { videoController.getPlayerResponseViaIosClient(any()) } returns botChecked()

                val ex = shouldThrow<VideoUnplayableException> {
                    StreamClient(http, videoController).getHttpLiveStreamUrl(videoId)
                }

                ex::class shouldBe VideoUnplayableException::class
            }
    }
}
