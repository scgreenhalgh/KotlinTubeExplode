package com.github.kotlintubeexplode.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("VideoId")
class VideoIdTest {

    @Nested
    @DisplayName("when parsing raw video IDs")
    inner class RawVideoIds {

        @ParameterizedTest
        @ValueSource(strings = [
            "9bZkp7q19f0",  // Standard ID
            "dQw4w9WgXcQ",  // Another standard ID
            "AI7ULzgf8RU",  // ID with uppercase
            "_abc123-XYZ",  // ID with underscore and hyphen
            "12345678901"   // All numeric
        ])
        fun `should accept valid 11-character video IDs`(rawId: String) {
            val videoId = VideoId.parse(rawId)
            videoId.value shouldBe rawId
        }

        @Test
        fun `should preserve the original ID value`() {
            val rawId = "9bZkp7q19f0"
            val videoId = VideoId.parse(rawId)
            videoId.value shouldBe rawId
            videoId.toString() shouldBe rawId
        }
    }

    @Nested
    @DisplayName("when parsing full YouTube URLs")
    inner class FullUrls {

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.youtube.com/watch?v=9bZkp7q19f0",
            "http://www.youtube.com/watch?v=9bZkp7q19f0",
            "https://youtube.com/watch?v=9bZkp7q19f0",
            "http://youtube.com/watch?v=9bZkp7q19f0",
            "www.youtube.com/watch?v=9bZkp7q19f0",
            "youtube.com/watch?v=9bZkp7q19f0"
        ])
        fun `should extract ID from standard watch URLs`(url: String) {
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&ab_channel=Test"
        ])
        fun `should extract ID from URLs with additional query parameters`(url: String) {
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "dQw4w9WgXcQ"
        }

        @Test
        fun `should handle URL with v parameter not first`() {
            val url = "https://www.youtube.com/watch?feature=share&v=9bZkp7q19f0"
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }
    }

    @Nested
    @DisplayName("when parsing short YouTube URLs")
    inner class ShortUrls {

        @ParameterizedTest
        @ValueSource(strings = [
            "https://youtu.be/9bZkp7q19f0",
            "http://youtu.be/9bZkp7q19f0",
            "youtu.be/9bZkp7q19f0"
        ])
        fun `should extract ID from youtu-be short URLs`(url: String) {
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }

        @Test
        fun `should extract ID from short URL with timestamp`() {
            val url = "https://youtu.be/9bZkp7q19f0?t=120"
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }

        @Test
        fun `should extract ID from short URL with si parameter`() {
            val url = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123456"
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "dQw4w9WgXcQ"
        }
    }

    @Nested
    @DisplayName("when parsing embed URLs")
    inner class EmbedUrls {

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.youtube.com/embed/9bZkp7q19f0",
            "http://www.youtube.com/embed/9bZkp7q19f0",
            "https://youtube.com/embed/9bZkp7q19f0",
            "www.youtube.com/embed/9bZkp7q19f0"
        ])
        fun `should extract ID from embed URLs`(url: String) {
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }

        @Test
        fun `should extract ID from embed URL with parameters`() {
            val url = "https://www.youtube.com/embed/9bZkp7q19f0?autoplay=1"
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }
    }

    @Nested
    @DisplayName("when parsing v/ URLs")
    inner class VPathUrls {

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.youtube.com/v/9bZkp7q19f0",
            "http://www.youtube.com/v/9bZkp7q19f0",
            "https://youtube.com/v/9bZkp7q19f0"
        ])
        fun `should extract ID from v-path URLs`(url: String) {
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }
    }

    @Nested
    @DisplayName("when parsing shorts URLs")
    inner class ShortsUrls {

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.youtube.com/shorts/9bZkp7q19f0",
            "https://youtube.com/shorts/9bZkp7q19f0",
            "www.youtube.com/shorts/9bZkp7q19f0"
        ])
        fun `should extract ID from shorts URLs`(url: String) {
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }
    }

    @Nested
    @DisplayName("when parsing live URLs")
    inner class LiveUrls {

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.youtube.com/live/9bZkp7q19f0",
            "https://youtube.com/live/9bZkp7q19f0"
        ])
        fun `should extract ID from live URLs`(url: String) {
            val videoId = VideoId.parse(url)
            videoId.value shouldBe "9bZkp7q19f0"
        }
    }

    @Nested
    @DisplayName("when given invalid input")
    inner class InvalidInput {

        @ParameterizedTest
        @ValueSource(strings = [
            "",                     // Empty string
            "   ",                  // Whitespace only
            "abc",                  // Too short
            "abcdefghij",           // 10 characters (too short)
            "abcdefghijkl",         // 12 characters (too long)
            "abcdefghijklmnop",     // Way too long
            "abc def ghij",         // Contains spaces
            "abc!@#ghijk",          // Contains invalid special characters
            "abc<script>x"          // Contains HTML-like characters
        ])
        fun `should throw IllegalArgumentException for invalid raw IDs`(invalidInput: String) {
            shouldThrow<IllegalArgumentException> {
                VideoId.parse(invalidInput)
            }
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.youtube.com/watch",               // Missing v parameter
            "https://www.youtube.com/watch?v=",            // Empty v parameter
            "https://www.youtube.com/watch?v=abc",         // v parameter too short
            "https://www.youtube.com/watch?list=123",      // Wrong parameter
            "https://www.youtube.com/",                    // No video ID
            "https://www.youtube.com/channel/UCtest",      // Channel URL
            "https://www.youtube.com/playlist?list=PL123", // Playlist URL
            "https://www.youtube.com/user/testuser"        // User URL
        ])
        fun `should throw IllegalArgumentException for invalid URLs`(invalidUrl: String) {
            shouldThrow<IllegalArgumentException> {
                VideoId.parse(invalidUrl)
            }
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "https://vimeo.com/123456789",        // Different video platform
            "https://dailymotion.com/video/x123", // Different video platform
            "https://google.com/watch?v=abc123",  // Wrong domain
            "https://notyoutube.com/watch?v=9bZkp7q19f0" // Similar but wrong domain
        ])
        fun `should throw IllegalArgumentException for non-YouTube URLs`(invalidUrl: String) {
            shouldThrow<IllegalArgumentException> {
                VideoId.parse(invalidUrl)
            }
        }

        @Test
        fun `should throw IllegalArgumentException with descriptive message`() {
            val exception = shouldThrow<IllegalArgumentException> {
                VideoId.parse("invalid")
            }
            exception.message shouldBe "Invalid YouTube video ID or URL: invalid"
        }
    }

    @Nested
    @DisplayName("tryParse method")
    inner class TryParse {

        @Test
        fun `should return VideoId for valid input`() {
            val result = VideoId.tryParse("9bZkp7q19f0")
            result shouldBe VideoId("9bZkp7q19f0")
        }

        @Test
        fun `should return null for invalid input`() {
            val result = VideoId.tryParse("invalid")
            result shouldBe null
        }

        @Test
        fun `should return null for empty string`() {
            val result = VideoId.tryParse("")
            result shouldBe null
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    inner class EqualityTests {

        @Test
        fun `should be equal when values are the same`() {
            val id1 = VideoId.parse("9bZkp7q19f0")
            val id2 = VideoId.parse("9bZkp7q19f0")
            id1 shouldBe id2
        }

        @Test
        fun `should be equal when parsed from different URL formats`() {
            val fromRaw = VideoId.parse("9bZkp7q19f0")
            val fromUrl = VideoId.parse("https://www.youtube.com/watch?v=9bZkp7q19f0")
            val fromShort = VideoId.parse("https://youtu.be/9bZkp7q19f0")

            fromRaw shouldBe fromUrl
            fromUrl shouldBe fromShort
            fromRaw shouldBe fromShort
        }

        @Test
        fun `should have same hashCode when equal`() {
            val id1 = VideoId.parse("9bZkp7q19f0")
            val id2 = VideoId.parse("https://youtu.be/9bZkp7q19f0")
            id1.hashCode() shouldBe id2.hashCode()
        }

        @Test
        fun `should work correctly in collections`() {
            val set = setOf(
                VideoId.parse("9bZkp7q19f0"),
                VideoId.parse("https://youtu.be/9bZkp7q19f0"),
                VideoId.parse("https://www.youtube.com/watch?v=9bZkp7q19f0")
            )
            set.size shouldBe 1
        }
    }

    @Nested
    @DisplayName("isValid companion method")
    inner class IsValidTests {

        @Test
        fun `should return true for valid raw ID`() {
            VideoId.isValid("9bZkp7q19f0") shouldBe true
        }

        @Test
        fun `should return true for valid URL`() {
            VideoId.isValid("https://www.youtube.com/watch?v=9bZkp7q19f0") shouldBe true
        }

        @Test
        fun `should return false for invalid input`() {
            VideoId.isValid("invalid") shouldBe false
        }

        @Test
        fun `should return false for empty string`() {
            VideoId.isValid("") shouldBe false
        }
    }
}
