package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.VideoParseException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException

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

    @Nested
    @DisplayName("network errors during a check (v1.3.3)")
    inner class NetworkErrorTests {
        // A manifest checks ~25 formats, and any check that throws aborts the whole getManifest
        // attempt (every player request and every check starts over). On a network that drops 1 in
        // 10 connections, all 25 succeed only ~7% of the time. Retrying the one check first keeps a
        // blip local; persistent failures still propagate so getManifest can retry the attempt.

        @Test
        fun `should retry a range check that fails with a network error`() = runTest {
            val http = mockk<HttpController>()
            coEvery { http.getContentLength(match { it.contains("range=") }) } throws
                SocketTimeoutException("connect timed out") andThen 2L

            val result = shouldNotThrowAny { verifyStreamUrl(http, url, providedContentLength = 1000L) }

            result shouldBe 1000L
            coVerify(exactly = 2) { http.getContentLength(any()) }
        }

        @Test
        fun `should retry the length HEAD when it fails with a network error`() = runTest {
            val http = mockk<HttpController>()
            coEvery { http.getContentLength(url) } throws SocketTimeoutException("connect timed out") andThen 1000L
            coEvery { http.getContentLength(match { it.contains("range=") }) } returns 2L

            val result = shouldNotThrowAny { verifyStreamUrl(http, url, providedContentLength = null) }

            result shouldBe 1000L
            coVerify(exactly = 2) { http.getContentLength(url) }
        }

        @Test
        fun `should rethrow after three failed attempts`() = runTest {
            val http = mockk<HttpController>()
            coEvery { http.getContentLength(any()) } throws SocketTimeoutException("connect timed out")

            shouldThrow<SocketTimeoutException> { verifyStreamUrl(http, url, providedContentLength = 1000L) }
            coVerify(exactly = 3) { http.getContentLength(any()) }
        }

        // Guard (passes before and after): a parser regression is not a network blip, so it must
        // surface on the first attempt (see StreamClientRetryTest).
        @Test
        fun `should not retry a non-network failure`() = runTest {
            val http = mockk<HttpController>()
            coEvery { http.getContentLength(any()) } throws VideoParseException("response shape changed")

            shouldThrow<VideoParseException> { verifyStreamUrl(http, url, providedContentLength = 1000L) }
            coVerify(exactly = 1) { http.getContentLength(any()) }
        }
    }
}
