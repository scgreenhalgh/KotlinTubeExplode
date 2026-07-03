package com.github.kotlintubeexplode.videos.closedcaptions

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.dto.CaptionNameDto
import com.github.kotlintubeexplode.internal.dto.CaptionTrackDto
import com.github.kotlintubeexplode.internal.dto.CaptionsDto
import com.github.kotlintubeexplode.internal.dto.PlayerCaptionsTracklistRendererDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ClosedCaptionClient")
class ClosedCaptionClientTest {

    private fun playerResponseWithTrackUrl(url: String) = PlayerResponseDto(
        captions = CaptionsDto(
            playerCaptionsTracklistRenderer = PlayerCaptionsTracklistRendererDto(
                captionTracks = listOf(
                    CaptionTrackDto(
                        baseUrl = url,
                        name = CaptionNameDto(simpleText = "English"),
                        languageCode = "en",
                        kind = "asr"
                    )
                )
            )
        )
    )

    // Upstream's ClosedCaptionController extends VideoController and pulls the caption
    // tracklist from GetPlayerResponseAsync (the ANDROID_VR youtubei/v1/player API), NOT
    // the watch page. Watch-page (WEB) caption baseUrls now return HTTP 200 with an empty
    // body, so sourcing from the wrong client silently yields zero captions (drift #16).
    @Test
    fun `getManifest sources tracks from the ANDROID_VR player response`() = runTest {
        val http = mockk<HttpController>(relaxed = true)
        val videoController = mockk<VideoController>()
        val videoId = VideoId("YltHGKX80Y8")

        coEvery {
            videoController.getPlayerResponseViaAndroidClient(videoId, any())
        } returns playerResponseWithTrackUrl("https://android-vr/api/timedtext")
        coEvery {
            videoController.getPlayerResponse(videoId)
        } returns playerResponseWithTrackUrl("https://watch-page/api/timedtext")

        val client = ClosedCaptionClient(http, videoController)

        val manifest = client.getManifest(videoId)

        manifest.tracks.size shouldBe 1
        manifest.tracks[0].url shouldBe "https://android-vr/api/timedtext"

        coVerify(exactly = 1) { videoController.getPlayerResponseViaAndroidClient(videoId, any()) }
        coVerify(exactly = 0) { videoController.getPlayerResponse(videoId) }
    }
}
