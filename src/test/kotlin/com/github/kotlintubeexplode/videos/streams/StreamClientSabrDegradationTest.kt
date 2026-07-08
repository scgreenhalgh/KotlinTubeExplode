package com.github.kotlintubeexplode.videos.streams

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

/**
 * ANDROID_VR intermittently returns a SABR-degraded response — its adaptive formats are stripped,
 * leaving only the muxed 360p (itag 18). The degradation is session-based, so a fresh request
 * usually recovers the full format set (yt-dlp #16150). StreamClient should retry a degraded
 * ANDROID_VR response before returning it, rather than handing back a 360p-only manifest.
 */
@DisplayName("StreamClient ANDROID_VR SABR-degradation retry")
class StreamClientSabrDegradationTest {

    private val degraded = PlayerResponseDto(
        playabilityStatus = PlayabilityStatusDto(status = "OK"),
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

    @Nested
    @DisplayName("when the first ANDROID_VR response is SABR-degraded")
    inner class DegradationRetryTests {

        @Test
        fun `retries and returns the full adaptive set once a healthy response arrives`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { http.getContentLength(any()) } returns 2L
            coEvery {
                videoController.getPlayerResponseViaAndroidClient(any(), any())
            } returnsMany listOf(degraded, healthy)

            val client = StreamClient(http, videoController)
            val manifest = client.getManifest(VideoId.parse("dQw4w9WgXcQ"))

            // Must NOT hand back the degraded 360p muxed stream — it should retry to the full set.
            manifest.streams.filterIsInstance<AudioOnlyStreamInfo>().size shouldBe 1
            manifest.streams.filterIsInstance<VideoOnlyStreamInfo>().size shouldBe 1
            manifest.streams.filterIsInstance<MuxedStreamInfo>().size shouldBe 0
        }

        @Test
        fun `falls back to the 360p muxed stream when every retry stays degraded`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()
            coEvery { http.getContentLength(any()) } returns 2L
            // Always degraded: the retry loop must terminate and return the 360p muxed stream
            // (playback beats nothing), not throw or loop forever.
            coEvery {
                videoController.getPlayerResponseViaAndroidClient(any(), any())
            } returns degraded

            val client = StreamClient(http, videoController)
            val manifest = client.getManifest(VideoId.parse("dQw4w9WgXcQ"))

            manifest.streams.filterIsInstance<MuxedStreamInfo>().size shouldBe 1
        }
    }
}
