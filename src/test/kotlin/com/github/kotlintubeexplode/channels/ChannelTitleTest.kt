package com.github.kotlintubeexplode.channels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Channel title HTML entity decoding")
class ChannelTitleTest {

    @Nested
    @DisplayName("decodeHtmlEntities")
    inner class DecodeHtmlEntitiesTests {

        @Test
        fun `decodes the basic four entities`() {
            decodeHtmlEntities("Rock &amp; Roll &lt;3&gt; &quot;x&quot;") shouldBe "Rock & Roll <3> \"x\""
        }

        @Test
        fun `decodes numeric decimal apostrophe`() {
            // Drift #28: &#39; is the common apostrophe entity in a channel og:title.
            decodeHtmlEntities("Bob&#39;s Channel") shouldBe "Bob's Channel"
        }

        @Test
        fun `decodes numeric hex entities`() {
            decodeHtmlEntities("Quote &#x27;here&#x27;") shouldBe "Quote 'here'"
        }

        @Test
        fun `decodes named em-dash`() {
            decodeHtmlEntities("News &mdash; Live") shouldBe "News — Live"
        }

        @Test
        fun `decodes numeric em-dash`() {
            decodeHtmlEntities("News &#8212; Live") shouldBe "News — Live"
        }

        @Test
        fun `decodes named apostrophe and curly quotes`() {
            decodeHtmlEntities("&apos;a&apos; &lsquo;b&rsquo;") shouldBe "'a' ‘b’"
        }

        @Test
        fun `decodes supplementary-plane numeric entity via surrogate pair`() {
            // Code points above U+FFFF must be emitted as a surrogate pair, not a truncated char.
            decodeHtmlEntities("smile &#128512; end") shouldBe "smile 😀 end"
        }

        @Test
        fun `leaves unknown named entities untouched`() {
            decodeHtmlEntities("A &bogus; B") shouldBe "A &bogus; B"
        }

        @Test
        fun `leaves a bare ampersand untouched`() {
            decodeHtmlEntities("AT&T Corp") shouldBe "AT&T Corp"
        }

        @Test
        fun `returns plain text unchanged`() {
            decodeHtmlEntities("Just A Channel") shouldBe "Just A Channel"
        }

        @Test
        fun `does not re-decode produced output`() {
            // "&amp;#39;" is a literal "&#39;", not an apostrophe. A single left-to-right
            // pass must not re-interpret the '&' it just produced (WebUtility.HtmlDecode semantics).
            decodeHtmlEntities("x &amp;#39; y") shouldBe "x &#39; y"
        }
    }
}
