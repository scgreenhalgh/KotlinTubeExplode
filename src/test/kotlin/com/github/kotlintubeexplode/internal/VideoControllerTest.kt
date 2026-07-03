package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.cipher.PlayerScriptParser
import com.github.kotlintubeexplode.internal.dto.*
import com.github.kotlintubeexplode.videos.Video
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VideoController")
class VideoControllerTest {

    @Test
    fun `getVideo should return video when parsing is successful`() = runTest {
        val httpController = mockk<HttpController>()
        val pageParser = mockk<VideoPageParser>()
        val cipherParser = mockk<PlayerScriptParser>()

        val controller = VideoController(httpController, pageParser, cipherParser)

        val videoId = VideoId("dQw4w9WgXcQ")
        val watchPageHtml = "<html>...</html>"
        val playerScriptUrl = "https://youtube.com/player.js"

        // Mock responses
        coEvery { httpController.getWithRetry(any(), any(), any()) } returns watchPageHtml
        coEvery { pageParser.extractPlayerScriptUrl(watchPageHtml) } returns playerScriptUrl

        // create a minimal valid PlayerResponseDto
        val mockDetails = VideoDetailsDto(
            videoId = videoId.value,
            title = "Never Gonna Give You Up",
            author = "Rick Astley",
            channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
            shortDescription = "Music video",
            lengthSeconds = "212",
            keywords = listOf("rick", "roll"),
            viewCount = "1000000",
            thumbnail = ThumbnailContainerDto(emptyList())
        )
        val mockMicroformat = PlayerMicroformatRendererDto(
            uploadDate = "2009-10-25",
            publishDate = "2009-10-25",
            ownerChannelName = "Rick Astley"
        )

        val playerResponse = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(status = "OK"),
            videoDetails = mockDetails,
            microformat = MicroformatDto(mockMicroformat)
        )

        coEvery { pageParser.parseWatchPage(watchPageHtml) } returns playerResponse

        // Act
        val video = controller.getVideo(videoId)

        // Assert
        video.id shouldBe videoId
        video.title shouldBe "Never Gonna Give You Up"
        video.author.channelTitle shouldBe "Rick Astley"
        video.duration?.inWholeSeconds shouldBe 212
    }

    @Nested
    @DisplayName("ANDROID_VR client")
    inner class AndroidVrClientTests {

        @Test
        fun `should use ANDROID_VR client name instead of ANDROID`() = runTest {
            val httpController = mockk<HttpController>()
            val pageParser = mockk<VideoPageParser>()
            val cipherParser = mockk<PlayerScriptParser>()
            val controller = VideoController(httpController, pageParser, cipherParser)

            val videoId = VideoId("dQw4w9WgXcQ")

            // Capture the request body sent to postJson
            val bodySlot = slot<String>()
            coEvery { httpController.get(any(), any()) } returns """)}]'
[[[null,null,[[[null,null,null,null,null,null,null,null,null,null,null,null,null,"visitor-data-value"]]]]]"""
            coEvery { httpController.postJson(any(), capture(bodySlot), any()) } returns """{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"dQw4w9WgXcQ","title":"Test","channelId":"UCtest123456789012345","lengthSeconds":"100"}}"""
            coEvery { pageParser.parsePlayerResponse(any()) } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                videoDetails = VideoDetailsDto(videoId = "dQw4w9WgXcQ", title = "Test")
            )

            controller.getPlayerResponseViaAndroidClient(videoId)

            val body = bodySlot.captured
            body shouldContain "ANDROID_VR"
            body shouldNotContain """"clientName":"ANDROID""""
            body shouldContain "1.60.19"
            body shouldContain "Oculus"
            body shouldContain "Quest 3"
            body shouldContain "12L"
        }

        @Test
        fun `should use ANDROID_VR user agent`() = runTest {
            val httpController = mockk<HttpController>()
            val pageParser = mockk<VideoPageParser>()
            val cipherParser = mockk<PlayerScriptParser>()
            val controller = VideoController(httpController, pageParser, cipherParser)

            val videoId = VideoId("dQw4w9WgXcQ")

            // Capture headers
            val headersSlot = slot<Map<String, String>>()
            coEvery { httpController.get(any(), any()) } returns """)}]'
[[[null,null,[[[null,null,null,null,null,null,null,null,null,null,null,null,null,"visitor-data-value"]]]]]"""
            coEvery { httpController.postJson(any(), any(), capture(headersSlot)) } returns """{"playabilityStatus":{"status":"OK"}}"""
            coEvery { pageParser.parsePlayerResponse(any()) } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK")
            )

            controller.getPlayerResponseViaAndroidClient(videoId)

            val userAgent = headersSlot.captured["User-Agent"]
            userAgent shouldContain "youtube.vr.oculus"
            userAgent shouldContain "1.60.19"
            userAgent shouldContain "Quest 3"
        }

        @Test
        fun `should never send signatureTimestamp or playbackContext (drift #23)`() = runTest {
            // ANDROID_VR returns plain URLs and never needs signature deciphering, so
            // upstream never puts a signatureTimestamp / playbackContext in this request.
            // Even when a caller supplies one, we must not add it to the wire body.
            val httpController = mockk<HttpController>()
            val pageParser = mockk<VideoPageParser>()
            val cipherParser = mockk<PlayerScriptParser>()
            val controller = VideoController(httpController, pageParser, cipherParser)

            val videoId = VideoId("dQw4w9WgXcQ")

            val bodySlot = slot<String>()
            coEvery { httpController.get(any(), any()) } returns """)]}'[[null,null,[[[null,null,null,null,null,null,null,null,null,null,null,null,null,"visitor-data-value"]]]]]"""
            coEvery { httpController.postJson(any(), capture(bodySlot), any()) } returns """{"playabilityStatus":{"status":"OK"}}"""
            coEvery { pageParser.parsePlayerResponse(any()) } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK")
            )

            controller.getPlayerResponseViaAndroidClient(videoId, "19834")

            val body = bodySlot.captured
            body shouldContain "ANDROID_VR"
            body shouldNotContain "playbackContext"
            body shouldNotContain "signatureTimestamp"
            body shouldNotContain "19834"
        }
    }

    @Nested
    @DisplayName("TVHTML5 embedded client (drift #22)")
    inner class TvEmbeddedClientTests {

        // sw.js_data blob shaped so resolveVisitorData() reads json[0][2][0][0][13].
        private val visitorDataResponse =
            """)]}'[[null,null,[[[null,null,null,null,null,null,null,null,null,null,null,null,null,"visitor-data-value"]]]]]"""

        @Test
        fun `TVHTML5 body should include visitorData and utcOffsetMinutes`() = runTest {
            val httpController = mockk<HttpController>()
            val pageParser = mockk<VideoPageParser>()
            val cipherParser = mockk<PlayerScriptParser>()
            val controller = VideoController(httpController, pageParser, cipherParser)

            val videoId = VideoId("dQw4w9WgXcQ")

            val bodySlot = slot<String>()
            coEvery { httpController.get(any(), any()) } returns visitorDataResponse
            coEvery { httpController.postJson(any(), capture(bodySlot), any()) } returns """{"playabilityStatus":{"status":"OK"}}"""
            coEvery { pageParser.parsePlayerResponse(any()) } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK")
            )

            controller.getPlayerResponseViaTVEmbeddedClient(videoId, "19834")

            val body = bodySlot.captured
            body shouldContain "TVHTML5_SIMPLY_EMBEDDED_PLAYER"
            body shouldContain """"visitorData":"visitor-data-value""""
            body shouldContain "utcOffsetMinutes"
            // signatureTimestamp must survive the body changes
            body shouldContain "19834"
        }
    }

    @Nested
    @DisplayName("getVideo watch-page fallback (drift #21)")
    inner class GetVideoFallbackTests {
        // Upstream VideoClient.GetAsync resolves metadata from the watch page's embedded
        // player response and only falls back to the ANDROID_VR player API when the watch
        // page has none: `watchPage.PlayerResponse ?? GetPlayerResponseAsync(videoId)`.
        // The watch page stays the primary source; the API is a resilience fallback.

        private val visitorDataResponse =
            """)]}'[[null,null,[[[null,null,null,null,null,null,null,null,null,null,null,null,null,"visitor-data-value"]]]]]"""

        @Test
        fun `getVideo should fall back to the player API when the watch page has no details`() = runTest {
            val httpController = mockk<HttpController>()
            val pageParser = mockk<VideoPageParser>()
            val cipherParser = mockk<PlayerScriptParser>()
            val controller = VideoController(httpController, pageParser, cipherParser)

            val videoId = VideoId("dQw4w9WgXcQ")

            coEvery { httpController.getWithRetry(any(), any(), any()) } returns "<html>...</html>"
            coEvery { pageParser.extractPlayerScriptUrl(any()) } returns null
            // Watch page parses but embeds no usable video details.
            coEvery { pageParser.parseWatchPage(any()) } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                videoDetails = null
            )
            // ANDROID_VR fallback returns the real metadata.
            coEvery { httpController.get(any(), any()) } returns visitorDataResponse
            coEvery { httpController.postJson(any(), any(), any()) } returns """{"playabilityStatus":{"status":"OK"}}"""
            coEvery { pageParser.parsePlayerResponse(any()) } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                videoDetails = VideoDetailsDto(
                    videoId = videoId.value,
                    title = "Recovered Via Player API",
                    channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
                    author = "Rick Astley",
                    lengthSeconds = "212"
                )
            )

            val video = shouldNotThrowAny { controller.getVideo(videoId) }

            video.title shouldBe "Recovered Via Player API"
            coVerify { httpController.postJson(match { it.contains("youtubei/v1/player") }, any(), any()) }
        }

        @Test
        fun `getVideo should not call the player API when the watch page has details`() = runTest {
            val httpController = mockk<HttpController>()
            val pageParser = mockk<VideoPageParser>()
            val cipherParser = mockk<PlayerScriptParser>()
            val controller = VideoController(httpController, pageParser, cipherParser)

            val videoId = VideoId("dQw4w9WgXcQ")

            coEvery { httpController.getWithRetry(any(), any(), any()) } returns "<html>...</html>"
            coEvery { pageParser.extractPlayerScriptUrl(any()) } returns null
            coEvery { pageParser.parseWatchPage(any()) } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                videoDetails = VideoDetailsDto(
                    videoId = videoId.value,
                    title = "From Watch Page",
                    channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
                    author = "Rick Astley",
                    lengthSeconds = "212"
                )
            )

            val video = controller.getVideo(videoId)

            video.title shouldBe "From Watch Page"
            coVerify(exactly = 0) { httpController.postJson(any(), any(), any()) }
            coVerify(exactly = 0) { pageParser.parsePlayerResponse(any()) }
        }
    }
}
