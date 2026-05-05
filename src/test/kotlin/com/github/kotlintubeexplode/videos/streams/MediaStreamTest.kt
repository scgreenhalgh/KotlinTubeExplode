package com.github.kotlintubeexplode.videos.streams

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MediaStream.getSegmentUrl")
class MediaStreamTest {

    @Nested
    @DisplayName("range parameter handling")
    inner class RangeParameterTests {

        @Test
        fun `should append range to URL with no query string`() {
            val url = "https://example.googlevideo.com/path"
            val result = MediaStream.getSegmentUrl(url, 0, 100)
            result shouldContain "range=0-100"
        }

        @Test
        fun `should append range to URL with existing query string`() {
            val url = "https://example.googlevideo.com/path?a=1"
            val result = MediaStream.getSegmentUrl(url, 0, 100)
            result shouldContain "a=1"
            result shouldContain "range=0-100"
        }

        @Test
        fun `should replace pre-existing range parameter rather than append duplicate`() {
            // Security finding #2: previously used string concat, so a stream URL with
            // `range=` already would yield duplicate params and undefined CDN behavior.
            val url = "https://example.googlevideo.com/path?range=0-1"
            val result = MediaStream.getSegmentUrl(url, 1000, 2000)

            // Only the new range should be present.
            result shouldContain "range=1000-2000"
            // Old range should not appear.
            val rangeOccurrences = "range=".toRegex().findAll(result).count()
            assert(rangeOccurrences == 1) {
                "Expected exactly one range= param, got $rangeOccurrences in: $result"
            }
        }

        @Test
        fun `should leave other query parameters intact`() {
            val url = "https://example.googlevideo.com/path?expire=999&sig=abc"
            val result = MediaStream.getSegmentUrl(url, 0, 100)
            result shouldContain "expire=999"
            result shouldContain "sig=abc"
            result shouldContain "range=0-100"
        }
    }
}
