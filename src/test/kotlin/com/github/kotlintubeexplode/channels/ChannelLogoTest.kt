package com.github.kotlintubeexplode.channels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Channel logo parsing")
class ChannelLogoTest {

    @Nested
    @DisplayName("parseLogoSize")
    inner class ParseLogoSizeTests {

        @Test
        fun `should return last s-prefixed number, not first`() {
            // Drift #26: real channel avatar URLs commonly have multiple s-tokens, e.g.
            // a crop coordinate followed by the actual size. Upstream picks the last;
            // we previously picked the first (often a crop, not a size).
            val url = "https://yt3.googleusercontent.com/foo/s48-c-k-c0x00ffffff/photo.jpg=s176-c-k-c0x00ffffff"
            parseLogoSize(url) shouldBe 176
        }

        @Test
        fun `should parse single s-token`() {
            parseLogoSize("https://yt3.googleusercontent.com/foo/s100/photo.jpg") shouldBe 100
        }

        @Test
        fun `should default to 100 when no s-token found`() {
            // Default of 100 matches upstream's Thumbnail.GetDefaultSet behavior.
            parseLogoSize("https://yt3.googleusercontent.com/foo/no-size/photo.jpg") shouldBe 100
        }

        @Test
        fun `should not match s in word boundary positions`() {
            // 'sport' contains 's' but the regex requires a word boundary, so 's800' inside
            // a longer word shouldn't match. URLs typically use s as a slash-bounded token.
            parseLogoSize("https://example.com/sports/photo.jpg") shouldBe 100
        }
    }
}
