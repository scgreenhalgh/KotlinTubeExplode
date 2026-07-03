package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.exceptions.VideoRequiresPurchaseException
import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.internal.VideoPageParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StreamClient")
class StreamClientTest {

    @Nested
    @DisplayName("pay-to-play")
    inner class PayToPlayTests {
        // Mirror of upstream StreamClient.cs GetStreamInfosAsync (6.6/prime L214-221): a player
        // response advertising a free preview/trailer id inside playabilityStatus.errorScreen
        // must surface as VideoRequiresPurchaseException, not a generic unplayable/parse error.
        // KNOWN_DRIFT #5.

        @Test
        fun `getManifest throws VideoRequiresPurchaseException when errorScreen exposes a preview id`() =
            runTest {
                val http = mockk<HttpController>(relaxed = true)
                val videoController = mockk<VideoController>()
                val streamClient = StreamClient(http, videoController)

                // Parse via the production parser so this compiles before the errorScreen field
                // exists (ignoreUnknownKeys) and only yields a preview id once the port lands.
                val payToPlayJson = """
                    {
                      "playabilityStatus": {
                        "status": "UNPLAYABLE",
                        "reason": "This video requires payment to watch.",
                        "errorScreen": {
                          "playerLegacyDesktopYpcTrailerRenderer": {
                            "trailerVideoId": "9bZkp7q19f0"
                          }
                        }
                      }
                    }
                """.trimIndent()
                val payToPlayResponse = VideoPageParser().parsePlayerResponse(payToPlayJson)

                coEvery {
                    videoController.getPlayerResponseViaAndroidClient(any(), any())
                } returns payToPlayResponse

                val ex = shouldThrow<VideoRequiresPurchaseException> {
                    streamClient.getManifest(VideoId("p3dDcKOFXQg"))
                }

                // previewVideoId is String today, VideoId after the fix; toString() bridges both.
                ex.previewVideoId.toString() shouldBe "9bZkp7q19f0"
            }
    }
}
