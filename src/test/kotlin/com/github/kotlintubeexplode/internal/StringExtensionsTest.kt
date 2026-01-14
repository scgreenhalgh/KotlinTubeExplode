package com.github.kotlintubeexplode.internal

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StringExtensions")
class StringExtensionsTest {

    @Nested
    @DisplayName("substringBetween")
    inner class SubstringBetweenTests {

        @Test
        fun `should extract content between delimiters`() {
            "Hello [World] Goodbye".substringBetween("[", "]") shouldBe "World"
        }

        @Test
        fun `should handle nested content`() {
            "start<div>content</div>end".substringBetween("<div>", "</div>") shouldBe "content"
        }

        @Test
        fun `should return null when start delimiter not found`() {
            "Hello World".substringBetween("[", "]").shouldBeNull()
        }

        @Test
        fun `should return null when end delimiter not found`() {
            "Hello [World".substringBetween("[", "]").shouldBeNull()
        }

        @Test
        fun `should handle empty content between delimiters`() {
            "Hello [] Goodbye".substringBetween("[", "]") shouldBe ""
        }

        @Test
        fun `should handle multi-character delimiters`() {
            "var x = {value: 123};".substringBetween("= ", ";") shouldBe "{value: 123}"
        }
    }

    @Nested
    @DisplayName("substringAfterOrNull and substringBeforeOrNull")
    inner class SubstringAfterBeforeTests {

        @Test
        fun `should extract after delimiter`() {
            "key=value".substringAfterOrNull("=") shouldBe "value"
        }

        @Test
        fun `should extract before delimiter`() {
            "key=value".substringBeforeOrNull("=") shouldBe "key"
        }

        @Test
        fun `should return null when delimiter not found`() {
            "keyvalue".substringAfterOrNull("=").shouldBeNull()
            "keyvalue".substringBeforeOrNull("=").shouldBeNull()
        }

        @Test
        fun `should handle empty result after delimiter`() {
            "key=".substringAfterOrNull("=") shouldBe ""
        }

        @Test
        fun `should handle delimiter at start`() {
            "=value".substringBeforeOrNull("=") shouldBe ""
        }
    }

    @Nested
    @DisplayName("stripNonDigits and parseViewCount")
    inner class NumericParsingTests {

        @Test
        fun `should strip all non-digit characters`() {
            "1,234,567 views".stripNonDigits() shouldBe "1234567"
        }

        @Test
        fun `should handle string with no digits`() {
            "no digits here".stripNonDigits() shouldBe ""
        }

        @Test
        fun `should parse view count with commas`() {
            "1,234,567 views".parseViewCount() shouldBe 1234567L
        }

        @Test
        fun `should parse view count with decimals in some locales`() {
            "1.234.567 Aufrufe".parseViewCount() shouldBe 1234567L
        }
    }

    @Nested
    @DisplayName("parseDurationSeconds")
    inner class DurationParsingTests {

        @Test
        fun `should parse H_M_S format`() {
            "1:23:45".parseDurationSeconds() shouldBe 5025L
        }

        @Test
        fun `should parse M_S format`() {
            "3:45".parseDurationSeconds() shouldBe 225L
        }

        @Test
        fun `should parse seconds only`() {
            "45".parseDurationSeconds() shouldBe 45L
        }

        @Test
        fun `should parse ISO 8601 format`() {
            "PT1H23M45S".parseDurationSeconds() shouldBe 5025L
        }

        @Test
        fun `should parse ISO 8601 with only minutes and seconds`() {
            "PT23M45S".parseDurationSeconds() shouldBe 1425L
        }

        @Test
        fun `should parse ISO 8601 with only seconds`() {
            "PT45S".parseDurationSeconds() shouldBe 45L
        }
    }

    @Nested
    @DisplayName("swapChars")
    inner class SwapCharsTests {

        @Test
        fun `should swap characters at given positions`() {
            "abcdef".swapChars(0, 3) shouldBe "dbcaef"
        }

        @Test
        fun `should handle same index`() {
            "abcdef".swapChars(2, 2) shouldBe "abcdef"
        }

        @Test
        fun `should handle out of bounds indices`() {
            "abc".swapChars(0, 10) shouldBe "abc"
            "abc".swapChars(-1, 2) shouldBe "abc"
        }
    }

    @Nested
    @DisplayName("extractJsonObject")
    inner class ExtractJsonObjectTests {

        @Test
        fun `should extract simple JSON object`() {
            val input = "var x = {\"key\": \"value\"}"
            input.extractJsonObject().shouldNotBeNull() shouldBe "{\"key\": \"value\"}"
        }

        @Test
        fun `should handle nested objects`() {
            val input = "var x = {\"outer\": {\"inner\": 123}}"
            input.extractJsonObject().shouldNotBeNull() shouldBe "{\"outer\": {\"inner\": 123}}"
        }

        @Test
        fun `should handle braces inside strings`() {
            val input = "var x = {\"text\": \"contains { and } braces\"}"
            input.extractJsonObject().shouldNotBeNull() shouldBe "{\"text\": \"contains { and } braces\"}"
        }

        @Test
        fun `should handle escaped quotes`() {
            val input = """var x = {"text": "escaped \" quote"}"""
            input.extractJsonObject().shouldNotBeNull() shouldBe """{"text": "escaped \" quote"}"""
        }

        @Test
        fun `should return null when no JSON object found`() {
            "no json here".extractJsonObject().shouldBeNull()
        }
    }

    @Nested
    @DisplayName("URL parameter handling")
    inner class UrlParameterTests {

        @Test
        fun `should parse query parameters`() {
            val url = "https://example.com?foo=bar&baz=qux"
            val params = url.parseQueryParameters()
            params["foo"] shouldBe "bar"
            params["baz"] shouldBe "qux"
        }

        @Test
        fun `should decode URL encoded parameters`() {
            val url = "https://example.com?text=hello%20world"
            url.getQueryParameter("text") shouldBe "hello world"
        }

        @Test
        fun `should set query parameter`() {
            val url = "https://example.com?existing=value"
            val result = url.setQueryParameter("new", "param")
            result.getQueryParameter("new") shouldBe "param"
            result.getQueryParameter("existing") shouldBe "value"
        }

        @Test
        fun `should replace existing query parameter`() {
            val url = "https://example.com?key=old"
            val result = url.setQueryParameter("key", "new")
            result.getQueryParameter("key") shouldBe "new"
        }
    }

    @Nested
    @DisplayName("nullIfBlank")
    inner class NullIfBlankTests {

        @Test
        fun `should return null for empty string`() {
            "".nullIfBlank().shouldBeNull()
        }

        @Test
        fun `should return null for whitespace only`() {
            "   ".nullIfBlank().shouldBeNull()
        }

        @Test
        fun `should return original for non-blank string`() {
            "hello".nullIfBlank() shouldBe "hello"
        }
    }
    
    @Nested
    @DisplayName("sanitizeFileName")
    inner class SanitizeFileNameTests {
    
        @Test
        fun `sanitizeFileName should replace illegal characters`() {
            // "file/with\illegal:chars*and?others"<>|.mp4"
            // We use standard string with escaping for safety
            val input = "file/with\\illegal:chars*and?others\"<>|.mp4"
            // Expected: "file_with_illegal_chars_and_others____.mp4"
            val expected = "file_with_illegal_chars_and_others____.mp4"
            
            input.sanitizeFileName() shouldBe expected
        }
    
        @Test
        fun `sanitizeFileName should preserve dots (including double dots)`() {
            // Double dots are safe as long as separators are removed
            val input = "my..file.mp4"
            input.sanitizeFileName() shouldBe "my..file.mp4"
        }
    
        @Test
        fun `sanitizeFileName should handle path traversal attempts`() {
            // ../ should become .._
            val input = "../../etc/passwd"
            val expected = ".._.._etc_passwd"
            
            input.sanitizeFileName() shouldBe expected
        }
    
        @Test
        fun `sanitizeFileName should handle empty or blank strings`() {
            "".sanitizeFileName() shouldBe "video"
            "   ".sanitizeFileName() shouldBe "video"
        }
    }
}
