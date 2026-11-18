package com.github.kotlintubeexplode.videos.closedcaptions

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.dto.CaptionNameDto
import com.github.kotlintubeexplode.internal.dto.CaptionTrackDto
import com.github.kotlintubeexplode.internal.dto.CaptionsDto
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerCaptionsTracklistRendererDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

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

    @Nested
    @DisplayName("client fallback (v1.3.2)")
    inner class ClientFallbackTests {
        // ANDROID_VR now hits YouTube's bot check for many videos and returns no captions, so the
        // manifest came back empty. Checked live 2026-11-18 on YltHGKX80Y8: VISIONOS 0 tracks,
        // ANDROID_VR bot-checked, ANDROID and iOS 10 tracks each, and their caption URLs download.

        private val videoId = VideoId("YltHGKX80Y8")

        private fun noCaptions() = PlayerResponseDto(playabilityStatus = PlayabilityStatusDto(status = "OK"))

        private fun botChecked() = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(
                status = "LOGIN_REQUIRED",
                reason = "Sign in to confirm you’re not a bot"
            )
        )

        @Test
        fun `should take tracks from the first client that has any`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(videoId) } returns noCaptions()
            coEvery { videoController.getPlayerResponseViaAndroidClient(videoId, any()) } returns botChecked()
            coEvery {
                videoController.getPlayerResponseViaAndroidMobileClient(videoId)
            } returns playerResponseWithTrackUrl("https://android/api/timedtext")

            val manifest = ClosedCaptionClient(http, videoController).getManifest(videoId)

            manifest.tracks.size shouldBe 1
            manifest.tracks[0].url shouldBe "https://android/api/timedtext"
            coVerify(exactly = 0) { videoController.getPlayerResponseViaIosClient(videoId) }
        }

        @Test
        fun `should not ask later clients when VISIONOS has tracks`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val videoController = mockk<VideoController>()
            coEvery {
                videoController.getPlayerResponseViaVisionosClient(videoId)
            } returns playerResponseWithTrackUrl("https://visionos/api/timedtext")

            val manifest = shouldNotThrowAny { ClosedCaptionClient(http, videoController).getManifest(videoId) }

            manifest.tracks.size shouldBe 1
            manifest.tracks[0].url shouldBe "https://visionos/api/timedtext"
            coVerify(exactly = 0) { videoController.getPlayerResponseViaAndroidClient(videoId, any()) }
        }

        @Test
        fun `should return an empty manifest after asking every client when none has tracks`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(videoId) } returns noCaptions()
            coEvery { videoController.getPlayerResponseViaAndroidClient(videoId, any()) } returns noCaptions()
            coEvery { videoController.getPlayerResponseViaAndroidMobileClient(videoId) } returns noCaptions()
            coEvery { videoController.getPlayerResponseViaIosClient(videoId) } returns noCaptions()

            val manifest = ClosedCaptionClient(http, videoController).getManifest(videoId)

            manifest.tracks.size shouldBe 0
            coVerify(exactly = 1) { videoController.getPlayerResponseViaVisionosClient(videoId) }
            coVerify(exactly = 1) { videoController.getPlayerResponseViaAndroidClient(videoId, any()) }
            coVerify(exactly = 1) { videoController.getPlayerResponseViaAndroidMobileClient(videoId) }
            coVerify(exactly = 1) { videoController.getPlayerResponseViaIosClient(videoId) }
        }

        // Characterization: a network failure on every client must surface as the error, not be
        // flattened into an empty manifest. Passes on the pre-fix code too (ANDROID_VR's error
        // propagated); it guards that behavior through the change.
        @Test
        fun `should propagate the error when every client request fails`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val videoController = mockk<VideoController>()
            coEvery { videoController.getPlayerResponseViaVisionosClient(videoId) } throws IOException("offline")
            coEvery { videoController.getPlayerResponseViaAndroidClient(videoId, any()) } throws IOException("offline")
            coEvery { videoController.getPlayerResponseViaAndroidMobileClient(videoId) } throws IOException("offline")
            coEvery { videoController.getPlayerResponseViaIosClient(videoId) } throws IOException("offline")

            shouldThrow<IOException> { ClosedCaptionClient(http, videoController).getManifest(videoId) }
        }
    }
}
