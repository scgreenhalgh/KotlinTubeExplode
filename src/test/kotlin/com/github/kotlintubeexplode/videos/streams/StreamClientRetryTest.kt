package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.VideoParseException
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import com.github.kotlintubeexplode.internal.dto.StreamFormatDto
import com.github.kotlintubeexplode.internal.dto.StreamingDataDto
import com.github.kotlintubeexplode.internal.dto.VideoDetailsDto
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StreamClient.getManifest retry policy")
class StreamClientRetryTest {

    private val videoId = VideoId.parse("dQw4w9WgXcQ")

    @Nested
    @DisplayName("when getStreamInfos throws a non-IOException")
    inner class NonIoFailureTests {

        @Test
        fun `surfaces a parser regression immediately instead of retrying`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()

            // The cipher-less Android client returns a playable response with a single audio
            // format, so stream extraction stays on the Android path (drift #9 removed the
            // web-client fallback the retry loop used to be driven through).
            coEvery {
                videoController.getPlayerResponseViaAndroidClient(any(), any())
            } returns PlayerResponseDto(
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

            // Simulate a parser regression surfacing while verifying the stream URL: a
            // deterministic non-IOException. VideoParseException is exactly what the parsing
            // layer throws when a response shape drifts; it extends Exception, not IOException.
            coEvery { http.getContentLength(any()) } throws
                VideoParseException("player response shape changed")

            val client = StreamClient(http, videoController)

            shouldThrow<VideoParseException> {
                client.getManifest(videoId)
            }

            // A parser regression must NOT be retried/masked: the failing probe is hit once,
            // not five times. Pre-fix the catch-all `catch (e: Exception)` retries it 5x.
            coVerify(exactly = 1) { http.getContentLength(any()) }
        }
    }
}
