package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import com.github.kotlintubeexplode.internal.dto.VideoDetailsDto
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Offline regression lock for the made-for-kids audio fallback. Mirrors the live
 * MadeForKidsAudioIntegrationTest but with the network mocked out, so the wiring is guarded in the
 * default `./gradlew test` gate even when live YouTube is unavailable.
 */
@DisplayName("StreamClient ANDROID muxed fallback (made-for-kids audio)")
class StreamClientAndroidMuxedFallbackTest {
    private val videoId = VideoId.parse("XqZsoesa55w")

    @Test
    @DisplayName("uses the plain ANDROID client's muxed stream for audio when ANDROID_VR yields none")
    fun `android muxed stream provides audio when android_vr yields no streams`() = runTest {
        val http = mockk<HttpController>()
        val videoController = mockk<VideoController>()

        // ANDROID_VR: available (status != "error", videoDetails present) but exposes no streams —
        // the made-for-kids pattern that leaves the chain audio-less before the ANDROID fallback.
        val androidVrResponse = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(status = "OK"),
            videoDetails = VideoDetailsDto(videoId = videoId.value, title = "Baby Shark"),
            streamingData = StreamingDataDto()
        )
        coEvery { videoController.getPlayerResponseViaAndroidClient(any(), any()) } returns androidVrResponse

        // Plain ANDROID mobile: the legacy muxed itag-18 (video+audio combined). audioQuality is set
        // so the DTO classifies it as muxed (not video-only), matching a real itag-18 response.
        val androidMobileResponse = PlayerResponseDto(
            playabilityStatus = PlayabilityStatusDto(status = "OK"),
            videoDetails = VideoDetailsDto(videoId = videoId.value, title = "Baby Shark"),
            streamingData = StreamingDataDto(
                formats = listOf(
                    StreamFormatDto(
                        itag = 18,
                        url = "https://rr2---sn-test.googlevideo.com/videoplayback?itag=18",
                        mimeType = "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                        bitrate = 500_000,
                        width = 640,
                        height = 360,
                        contentLength = "1000",
                        qualityLabel = "360p",
                        audioQuality = "AUDIO_QUALITY_LOW",
                        audioChannels = 2
                    )
                )
            )
        )
        coEvery { videoController.getPlayerResponseViaAndroidMobileClient(any()) } returns androidMobileResponse

        coEvery { http.getContentLength(any()) } returns 1000L

        val streamClient = StreamClient(http, videoController)
        val manifest = streamClient.getManifest(videoId)

        // The muxed stream is audio-bearing, so getBestAudioStream() returns it.
        val audio = manifest.getBestAudioStream()
        audio.shouldNotBeNull()
        audio.shouldBeInstanceOf<MuxedStreamInfo>()

        // The ANDROID mobile client is consulted, and its result wins before iOS is ever tried.
        coVerify(exactly = 1) { videoController.getPlayerResponseViaAndroidMobileClient(any()) }
        coVerify(exactly = 0) { videoController.getPlayerResponseViaIosClient(any()) }
    }
}
