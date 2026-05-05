package com.github.kotlintubeexplode.internal

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for the protobuf map<string, string> decoder.
 *
 * Encoding reference:
 * - Tag byte = (field_number << 3) | wire_type
 * - LEN wire type = 2
 * - Outer map entry: tag 0x0A (field 1, LEN), then varint length, then submessage
 * - Submessage fields: 0x0A = key (field 1, string), 0x12 = value (field 2, string)
 */
@DisplayName("Protobuf")
class ProtobufTest {

    @Nested
    @DisplayName("tryDeserializeMap(ByteArray)")
    inner class ByteArrayTests {

        @Test
        fun `should return empty map for empty input`() {
            val result = Protobuf.tryDeserializeMap(byteArrayOf())
            result.shouldNotBeNull()
            result shouldBe emptyMap()
        }

        @Test
        fun `should decode single entry sr to 1`() {
            // Encoding of {"sr": "1"}:
            // 0x0A    outer tag (field 1, LEN)
            // 0x07    outer length (7 bytes)
            // 0x0A    field 1 (key string)
            // 0x02    string length 2
            // 0x73 0x72   "sr"
            // 0x12    field 2 (value string)
            // 0x01    string length 1
            // 0x31    "1"
            val bytes = byteArrayOf(
                0x0A, 0x07,
                0x0A, 0x02, 0x73, 0x72,
                0x12, 0x01, 0x31
            )

            val result = Protobuf.tryDeserializeMap(bytes)

            result.shouldNotBeNull()
            result shouldContainExactly mapOf("sr" to "1")
        }

        @Test
        fun `should decode multiple entries`() {
            // {"sr": "1", "ab": "cd"}
            val bytes = byteArrayOf(
                // first entry: sr=1
                0x0A, 0x07,
                0x0A, 0x02, 0x73, 0x72,
                0x12, 0x01, 0x31,
                // second entry: ab=cd
                0x0A, 0x08,
                0x0A, 0x02, 0x61, 0x62,
                0x12, 0x02, 0x63, 0x64
            )

            val result = Protobuf.tryDeserializeMap(bytes)

            result.shouldNotBeNull()
            result shouldContainExactly mapOf("sr" to "1", "ab" to "cd")
        }

        @Test
        fun `should decode entry with empty value`() {
            // {"k": ""}
            val bytes = byteArrayOf(
                0x0A, 0x05,
                0x0A, 0x01, 0x6B,
                0x12, 0x00
            )

            val result = Protobuf.tryDeserializeMap(bytes)

            result.shouldNotBeNull()
            result shouldContainExactly mapOf("k" to "")
        }

        @Test
        fun `should return null when outer field is not LEN wire type`() {
            // 0x08 = field 1, wire type 0 (VARINT) — not allowed at top level
            val bytes = byteArrayOf(0x08, 0x01)

            val result = Protobuf.tryDeserializeMap(bytes)

            result.shouldBeNull()
        }

        @Test
        fun `should return null when entry length exceeds remaining bytes`() {
            // outer tag says LEN with length 99, but only 2 bytes follow
            val bytes = byteArrayOf(0x0A, 0x63, 0x0A, 0x01)

            val result = Protobuf.tryDeserializeMap(bytes)

            result.shouldBeNull()
        }
    }

    @Nested
    @DisplayName("tryDeserializeMap(String) base64")
    inner class Base64Tests {

        @Test
        fun `should decode valid base64 of sr to 1`() {
            // Bytes 0x0A 0x07 0x0A 0x02 0x73 0x72 0x12 0x01 0x31 base64-encoded
            val base64 = "CgcKAnNyEgEx"

            val result = Protobuf.tryDeserializeMap(base64)

            result.shouldNotBeNull()
            result shouldContainExactly mapOf("sr" to "1")
        }

        @Test
        fun `should return null for invalid base64`() {
            val result = Protobuf.tryDeserializeMap("not!valid!base64!@#$")

            result.shouldBeNull()
        }

        @Test
        fun `should return empty map for empty base64 string`() {
            val result = Protobuf.tryDeserializeMap("")

            result.shouldNotBeNull()
            result shouldBe emptyMap()
        }
    }
}
