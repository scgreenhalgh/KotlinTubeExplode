package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.cipher.CipherManifest
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import com.github.kotlintubeexplode.internal.dto.VideoDetailsDto
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
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
}
