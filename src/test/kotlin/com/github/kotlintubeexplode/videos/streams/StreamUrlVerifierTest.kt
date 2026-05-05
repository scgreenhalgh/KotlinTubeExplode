package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.internal.HttpController
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("verifyStreamUrl")
class StreamUrlVerifierTest {

    private val url = "https://r1---sn-foo.googlevideo.com/videoplayback?expire=123"

    @Nested
    @DisplayName("when provided content length is non-null")
    inner class WithProvidedLengthTests {

        @Test
        fun `should return provided length when range fetch succeeds`() = runTest {
            val http = mockk<HttpController>()
            // Range request returns its 2-byte content length on success.
            coEvery { http.getContentLength(match { it.contains("range=") }) } returns 2L

            val result = verifyStreamUrl(http, url, providedContentLength = 1000L)

            result shouldBe 1000L
            coVerify(exactly = 1) { http.getContentLength(any()) }
        }

        @Test
        fun `should return null when range fetch returns null (stale URL)`() = runTest {
            val http = mockk<HttpController>()
            coEvery { http.getContentLength(any()) } returns null

            val result = verifyStreamUrl(http, url, providedContentLength = 1000L)

            result shouldBe null
        }
    }

    @Nested
    @DisplayName("when content length is too small to range-verify")
    inner class TinyContentLengthTests {
        // Range params are computed as (contentLength - 2, contentLength - 1). For
        // contentLength of 0 or 1, this produces negative offsets and an invalid Range
        // header. Skip the range probe in that case and pass through.

        @Test
        fun `should pass through provided length of 1 without range probe`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val result = verifyStreamUrl(http, url, providedContentLength = 1L)
            result shouldBe 1L
            coVerify(exactly = 0) { http.getContentLength(any()) }
        }

        @Test
        fun `should pass through provided length of 0 without range probe`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val result = verifyStreamUrl(http, url, providedContentLength = 0L)
            result shouldBe 0L
            coVerify(exactly = 0) { http.getContentLength(any()) }
        }
    }

    @Nested
    @DisplayName("when provided content length is null")
    inner class WithoutProvidedLengthTests {

        @Test
        fun `should HEAD for content length and verify range`() = runTest {
            val http = mockk<HttpController>()
            // First call (no range) returns the actual content length;
            // second call (with range) returns 2 to confirm the URL works.
            coEvery { http.getContentLength(match { !it.contains("range=") }) } returns 5_000L
            coEvery { http.getContentLength(match { it.contains("range=") }) } returns 2L

            val result = verifyStreamUrl(http, url, providedContentLength = null)

            result shouldBe 5_000L
            coVerify(exactly = 2) { http.getContentLength(any()) }
        }

        @Test
        fun `should return null when HEAD fails (no Content-Length)`() = runTest {
            val http = mockk<HttpController>()
            coEvery { http.getContentLength(match { !it.contains("range=") }) } returns null

            val result = verifyStreamUrl(http, url, providedContentLength = null)

            result shouldBe null
            // Should not attempt range request if we can't even get content length.
            coVerify(exactly = 1) { http.getContentLength(any()) }
        }

        @Test
        fun `should return null when HEAD succeeds but range fetch fails`() = runTest {
            val http = mockk<HttpController>()
            coEvery { http.getContentLength(match { !it.contains("range=") }) } returns 5_000L
            coEvery { http.getContentLength(match { it.contains("range=") }) } returns null

            val result = verifyStreamUrl(http, url, providedContentLength = null)

            result shouldBe null
        }
    }
}
