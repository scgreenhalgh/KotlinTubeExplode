package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.VideoUnplayableException
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.dto.PlayabilityStatusDto
import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StreamClient web-client fallback removal (drift #9)")
class StreamClientWebFallbackTest {

    @Nested
    @DisplayName("when the cipher-less and TV-embedded clients yield no streams")
    inner class NoStreamsTests {

        @Test
        fun `reports the video unplayable without falling back to the web client`() = runTest {
            val http = mockk<HttpController>()
            val videoController = mockk<VideoController>()

            // Cipher-less (ANDROID_VR) client returns a playable-but-empty response:
            // no streamingData, so no streams are extracted. status = "OK" means it is
            // NOT age-restricted, so the TV-embedded fallback is skipped as well.
            coEvery {
                videoController.getPlayerResponseViaAndroidClient(any(), any())
            } returns PlayerResponseDto(
                playabilityStatus = PlayabilityStatusDto(status = "OK"),
                streamingData = null
            )

            // The removed web-client fallback fetched the watch page via getWithRetry.
            // If that path ever runs again this stub throws, so the test can never pass
            // by accident on the old code.
            coEvery { http.getWithRetry(any(), any(), any()) } throws
                IllegalStateException("web-client fallback was reached")

            val client = StreamClient(http, videoController)

            // Upstream (StreamClient.cs GetStreamInfosAsync, tag 6.6 AND branch prime) throws
            // VideoUnplayableException when neither client yields a stream. It has no third
            // watch-page/web-client stream path.
            shouldThrow<VideoUnplayableException> {
                client.getManifest(VideoId.parse("dQw4w9WgXcQ"))
            }

            // Proof the web-client path is gone: the watch page is never fetched.
            coVerify(exactly = 0) { http.getWithRetry(any(), any(), any()) }
        }
    }
}
