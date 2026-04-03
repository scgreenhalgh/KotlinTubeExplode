package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.cipher.PlayerScriptParser
import com.github.kotlintubeexplode.internal.dto.*
import com.github.kotlintubeexplode.videos.Video
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
    }
}
