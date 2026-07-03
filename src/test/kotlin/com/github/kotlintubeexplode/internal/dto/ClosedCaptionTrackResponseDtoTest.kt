package com.github.kotlintubeexplode.internal.dto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@DisplayName("ClosedCaptionTrackResponseDto.parse")
class ClosedCaptionTrackResponseDtoTest {

    @Nested
    @DisplayName("timedtext <p> format")
    inner class PFormatTests {

        @Test
        fun `parses p-format captions with text offset and duration`() {
            val xml = """
                <timedtext format="3">
                  <body>
                    <p t="0" d="1500">Hello</p>
                    <p t="1500" d="2000">World</p>
                  </body>
                </timedtext>
            """.trimIndent()

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 2
            result.captions[0].text shouldBe "Hello"
            result.captions[0].offset shouldBe 0.milliseconds
            result.captions[0].duration shouldBe 1500.milliseconds
            result.captions[1].text shouldBe "World"
            result.captions[1].offset shouldBe 1500.milliseconds
            result.captions[1].duration shouldBe 2000.milliseconds
        }

        @Test
        fun `parses word-level s parts within a p element`() {
            val xml = """
                <timedtext format="3">
                  <body>
                    <p t="0" d="3000"><s t="0">Hello</s><s t="500">World</s></p>
                  </body>
                </timedtext>
            """.trimIndent()

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 1
            val parts = result.captions[0].parts
            parts.size shouldBe 2
            parts[0].text shouldBe "Hello"
            parts[0].offset shouldBe 0.milliseconds
            parts[1].text shouldBe "World"
            parts[1].offset shouldBe 500.milliseconds
        }

        @Test
        fun `decodes XML entities in caption text`() {
            val xml = """
                <timedtext format="3">
                  <body>
                    <p t="0" d="1000">let&#39;s go &amp; win</p>
                  </body>
                </timedtext>
            """.trimIndent()

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 1
            result.captions[0].text shouldBe "let's go & win"
        }
    }

    @Nested
    @DisplayName("legacy <text start dur> format (must be ignored)")
    inner class LegacyFormatTests {

        // Upstream only understands the <p t d> timedtext format. The old
        // <transcript><text start dur> path was dead code written from memory.
        @Test
        fun `does not parse legacy transcript text format`() {
            val xml = """
                <transcript>
                  <text start="0" dur="5.5">Legacy line one</text>
                  <text start="5.5" dur="3.2">Legacy line two</text>
                </transcript>
            """.trimIndent()

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 0
        }
    }

    @Nested
    @DisplayName("element-walk robustness (attr order, self-closing, nested <s>)")
    inner class ElementWalkTests {

        @Test
        fun `parses p element regardless of attribute order`() {
            // d before t: a positional regex misses this entirely.
            val xml = """<timedtext format="3"><body><p d="2000" t="500">Reversed</p></body></timedtext>"""

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 1
            result.captions[0].offset shouldBe 500.milliseconds
            result.captions[0].duration shouldBe 2000.milliseconds
            result.captions[0].text shouldBe "Reversed"
        }

        @Test
        fun `handles self-closing p without swallowing following captions`() {
            // A self-closing <p/> has no </p>; a regex keyed on </p> merges it with the next.
            val xml = """<timedtext format="3"><body><p t="1000" d="500" a="1"/><p t="2000" d="800">After</p></body></timedtext>"""

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 2
            result.captions[0].text.isNullOrEmpty() shouldBe true
            result.captions[1].text shouldBe "After"
            result.captions[1].offset shouldBe 2000.milliseconds
            result.captions[1].duration shouldBe 800.milliseconds
        }

        @Test
        fun `prefers t over ac for s part offset regardless of attribute order`() {
            // ac appears before t; upstream still prefers t.
            val xml = """<timedtext format="3"><body><p t="0" d="3000"><s ac="99" t="1000">Word</s></p></body></timedtext>"""

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 1
            val parts = result.captions[0].parts
            parts.size shouldBe 1
            parts[0].text shouldBe "Word"
            parts[0].offset shouldBe 1000.milliseconds
        }

        @Test
        fun `includes p element with a missing duration attribute`() {
            // Upstream returns every <p>; the caption-level filter (null duration -> skip)
            // lives in the client, not the parser.
            val xml = """<timedtext format="3"><body><p t="500">NoDuration</p></body></timedtext>"""

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 1
            result.captions[0].offset shouldBe 500.milliseconds
            result.captions[0].duration shouldBe null
        }

        @Test
        fun `preserves whitespace-only caption text`() {
            // https://github.com/Tyrrrz/YoutubeExplode/issues/671 — whitespace-only
            // captions are real and must not be trimmed away by the parser.
            val xml = """<timedtext format="3"><body><p t="100" d="200"> </p></body></timedtext>"""

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 1
            result.captions[0].text shouldBe " "
        }

        @Test
        fun `parses real-world p with extra attributes and ac-only leading part`() {
            val xml = """<timedtext format="3"><body><p t="6830" d="10150" w="1"><s ac="252">let&#39;s</s><s t="1000" ac="252"> do</s></p></body></timedtext>"""

            val result = ClosedCaptionTrackResponseDto.parse(xml)

            result.captions.size shouldBe 1
            val caption = result.captions[0]
            caption.text shouldBe "let's do"
            caption.offset shouldBe 6830.milliseconds
            caption.duration shouldBe 10150.milliseconds

            caption.parts.size shouldBe 2
            caption.parts[0].text shouldBe "let's"
            caption.parts[0].offset shouldBe 252.milliseconds
            // Leading space preserved (upstream does not trim part text).
            caption.parts[1].text shouldBe " do"
            caption.parts[1].offset shouldBe 1000.milliseconds
        }
    }
}
