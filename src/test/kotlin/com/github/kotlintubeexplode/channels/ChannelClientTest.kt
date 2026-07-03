package com.github.kotlintubeexplode.channels

import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.playlists.PlaylistClient
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChannelClient")
class ChannelClientTest {

    @Nested
    @DisplayName("broken-page retry (drift #38)")
    inner class BrokenPageRetryTests {

        // HTTP 200 but no og:url meta — YouTube occasionally serves this transient "broken page".
        private val brokenPage = "<html><head><title>YouTube</title></head><body>please try again</body></html>"

        private val goodPage = """
            <html><head>
            <meta property="og:url" content="https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw">
            <meta property="og:title" content="Google Developers">
            <meta property="og:image" content="https://yt3.ggpht.com/abc=s176-c-k">
            </head><body></body></html>
        """.trimIndent()

        @Test
        fun `retries a broken 200 channel page instead of failing immediately`() = runTest {
            val http = mockk<HttpController>()
            val playlists = mockk<PlaylistClient>(relaxed = true)

            // Upstream ChannelController retries the fetch+parse when the page comes back HTTP 200
            // but without a parseable og:url. Return two broken pages, then a good one.
            coEvery { http.getWithRetry(any(), any()) } returnsMany listOf(brokenPage, brokenPage, goodPage)

            val client = ChannelClient(http, playlists)

            // Pre-fix: getByHandle throws ChannelUnavailableException on the first broken page.
            val channel = shouldNotThrowAny { client.getByHandle("@GoogleDevelopers") }

            channel.id.value shouldBe "UC_x5XG1OV2P6uZZ5FSM9Ttw"
            channel.title shouldBe "Google Developers"
            // The two broken pages were retried, not thrown on.
            coVerify(exactly = 3) { http.getWithRetry(any(), any()) }
        }
    }

    @Nested
    @DisplayName("reordered meta attributes (drift #39)")
    inner class ReorderedMetaTests {

        // Attribute order reversed: `content` BEFORE `property`. Upstream (AngleSharp DOM) is
        // order-agnostic; our order-locked regex misses these tags entirely.
        //
        // Pre-fix this is red two ways: the order-locked og:url regex misses -> channel id is
        // null on every retry -> getByHandle throws (fallback pattern 3 captures only 23 chars,
        // an invalid id). Even if that fallback matched, og:title/og:image would still be missed,
        // so title/thumbnails would be wrong. Post-fix, order-agnostic DOM parsing resolves all three.
        private val reorderedPage = """
            <html><head>
            <meta content="https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw" property="og:url">
            <meta content="Google Developers" property="og:title">
            <meta content="https://yt3.ggpht.com/abc=s176-c-k" property="og:image">
            </head><body></body></html>
        """.trimIndent()

        @Test
        fun `resolves a channel page whose og meta attributes are reordered`() = runTest {
            val http = mockk<HttpController>()
            val playlists = mockk<PlaylistClient>(relaxed = true)

            coEvery { http.getWithRetry(any(), any()) } returns reorderedPage

            val client = ChannelClient(http, playlists)

            val channel = shouldNotThrowAny { client.getByHandle("@GoogleDevelopers") }

            channel.id.value shouldBe "UC_x5XG1OV2P6uZZ5FSM9Ttw"
            channel.title shouldBe "Google Developers"
            channel.thumbnails.shouldNotBeEmpty()
        }
    }
}
