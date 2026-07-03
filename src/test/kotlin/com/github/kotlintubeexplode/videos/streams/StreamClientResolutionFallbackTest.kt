package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.common.Resolution
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StreamClient resolution fallback")
class StreamClientResolutionFallbackTest {

    @Nested
    @DisplayName("when a video format is missing width/height")
    inner class NullResolutionTests {

        @Test
        fun `keeps the stream and falls back to the quality default resolution`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()

            // Tail-probe (range) content-length check always succeeds so streams are
            // not dropped by verifyStreamUrl (the separate drift #33 path).
            coEvery { http.getContentLength(any()) } returns 2L

            // Android client returns a playable response with:
            //  - one audio-only format (anchors the manifest so extraction does NOT
            //    fall through to the web client, which would hit the network)
            //  - one video-only format (itag 137 = 1080p) with width/height = null
            val response = PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                streamingData = StreamingDataDto(
                    adaptiveFormats = listOf(
                        StreamFormatDto(
                            itag = 140,
                            url = "https://rr---sn-a.googlevideo.com/videoplayback?itag=140",
                            mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                            bitrate = 128_000,
                            contentLength = "1000"
                        ),
                        StreamFormatDto(
                            itag = 137,
                            url = "https://rr---sn-v.googlevideo.com/videoplayback?itag=137",
                            mimeType = "video/mp4; codecs=\"avc1.640028\"",
                            bitrate = 2_500_000,
                            width = null,
                            height = null,
                            contentLength = "5000"
                        )
                    )
                )
            )
            coEvery {
                videoController.getPlayerResponseViaAndroidClient(any(), any())
            } returns response

            val client = StreamClient(http, videoController)
            val manifest = client.getManifest(VideoId.parse("dQw4w9WgXcQ"))

            // The video-only stream must NOT be dropped: both streams present.
            manifest.streams.size shouldBe 2

            // Resolution falls back to VideoQuality(itag 137 -> 1080p).getDefaultResolution().
            val video = manifest.getVideoOnlyStreams().single()
            video.videoResolution shouldBe Resolution(1920, 1080)
        }
    }
}
