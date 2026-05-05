package com.github.kotlintubeexplode.internal.dto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("StreamFormatDto")
class StreamFormatDtoTest {

    @Nested
    @DisplayName("videoCodec")
    inner class VideoCodecTests {
        // Drift #31: YouTube returns the literal string "unknown" as the codecs value
        // for some AV1 streams. Upstream maps this to "av01.0.05M.08". We previously
        // passed it through, colliding with our null-fallback default of "unknown".

        @Test
        fun `should map unknown codec to av01`() {
            val dto = StreamFormatDto(
                itag = 397,
                mimeType = "video/mp4; codecs=\"unknown\""
            )
            dto.videoCodec shouldBe "av01.0.05M.08"
        }

        @Test
        fun `should map UNKNOWN case-insensitively`() {
            val dto = StreamFormatDto(
                itag = 397,
                mimeType = "video/mp4; codecs=\"UNKNOWN\""
            )
            dto.videoCodec shouldBe "av01.0.05M.08"
        }

        @Test
        fun `should pass through real codec strings`() {
            val dto = StreamFormatDto(
                itag = 137,
                mimeType = "video/mp4; codecs=\"avc1.640028\""
            )
            dto.videoCodec shouldBe "avc1.640028"
        }
    }

    @Nested
    @DisplayName("isVideoUpscaled")
    inner class IsVideoUpscaledTests {

        // Base64 of protobuf-encoded map: {"sr": "1"}
        // Bytes: 0x0A 0x07 0x0A 0x02 0x73 0x72 0x12 0x01 0x31
        private val srEqualsOneXtags = "CgcKAnNyEgEx"

        // Base64 of {"sr": "0"}
        // Bytes: 0x0A 0x07 0x0A 0x02 0x73 0x72 0x12 0x01 0x30
        private val srEqualsZeroXtags = "CgcKAnNyEgEw"

        // Base64 of {"abc": "1"}  (no sr key)
        // Bytes: 0x0A 0x08 0x0A 0x03 0x61 0x62 0x63 0x12 0x01 0x31
        private val noSrKeyXtags = "CggKA2FiYxIBMQ=="

        @Test
        fun `should be true when xtags carries sr=1`() {
            val dto = StreamFormatDto(itag = 137, xtags = srEqualsOneXtags)
            dto.isVideoUpscaled shouldBe true
        }

        @Test
        fun `should be false when xtags carries sr=0`() {
            val dto = StreamFormatDto(itag = 137, xtags = srEqualsZeroXtags)
            dto.isVideoUpscaled shouldBe false
        }

        @Test
        fun `should be false when xtags has no sr key`() {
            val dto = StreamFormatDto(itag = 137, xtags = noSrKeyXtags)
            dto.isVideoUpscaled shouldBe false
        }

        @Test
        fun `should be false when xtags is null`() {
            val dto = StreamFormatDto(itag = 137, xtags = null)
            dto.isVideoUpscaled shouldBe false
        }

        @Test
        fun `should be false when xtags is blank`() {
            val dto = StreamFormatDto(itag = 137, xtags = "   ")
            dto.isVideoUpscaled shouldBe false
        }

        @Test
        fun `should be false when xtags is invalid base64`() {
            val dto = StreamFormatDto(itag = 137, xtags = "!!!not-base64!!!")
            dto.isVideoUpscaled shouldBe false
        }
    }
}
