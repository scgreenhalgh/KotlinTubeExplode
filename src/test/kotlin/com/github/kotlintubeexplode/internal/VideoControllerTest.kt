package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.cipher.PlayerScriptParser
import com.github.kotlintubeexplode.internal.dto.*
import com.github.kotlintubeexplode.videos.Video
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

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
}
