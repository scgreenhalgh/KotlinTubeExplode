package com.github.kotlintubeexplode.integration

import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.internal.VideoController
import com.github.kotlintubeexplode.testdata.VideoIds
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Live integration tests for the poToken-free fallback clients (VISIONOS, iOS) added in B1.
 *
 * These hit the real player API directly through VideoController to prove the request builders
 * work end-to-end (real visitorData resolution + HTTP + parse), independently of the mocked
 * fallback-ordering unit tests in StreamClientFallbackTest.
 */
@Tag("integration")
@DisplayName("VISIONOS / iOS fallback client integration")
class VisionosIosFallbackIntegrationTest {

    private val controller = VideoController()
    private val normal = VideoId.parse(VideoIds.Normal)

    @Test
    fun `VISIONOS client returns playable plain-URL streams`() = runTest(timeout = 60.seconds) {
        val response = controller.getPlayerResponseViaVisionosClient(normal)

        response.playabilityStatus?.isPlayable shouldBe true

        val formats = response.streamingData?.adaptiveFormats.orEmpty()
        formats.shouldNotBeEmpty()

        // VISIONOS is a poToken-free plain-URL client: formats carry a direct googlevideo URL,
        // no signatureCipher.
        val withUrl = formats.filter { it.url != null }
        withUrl.shouldNotBeEmpty()
        withUrl.first().url!! shouldContain "googlevideo.com"
    }

    @Test
    fun `iOS client returns playable plain-URL streams`() = runTest(timeout = 60.seconds) {
        val response = controller.getPlayerResponseViaIosClient(normal)

        response.playabilityStatus?.isPlayable shouldBe true

        val formats = response.streamingData?.adaptiveFormats.orEmpty()
        formats.shouldNotBeEmpty()

        val withUrl = formats.filter { it.url != null }
        withUrl.shouldNotBeEmpty()
        withUrl.first().url!! shouldContain "googlevideo.com"
    }
}
