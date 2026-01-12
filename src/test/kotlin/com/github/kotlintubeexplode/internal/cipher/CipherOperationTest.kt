package com.github.kotlintubeexplode.internal.cipher

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("CipherOperation")
class CipherOperationTest {

    @Nested
    @DisplayName("Reverse operation")
    inner class ReverseTests {

        @Test
        fun `should reverse string`() {
            CipherOperation.Reverse.apply("abcdef") shouldBe "fedcba"
        }

        @Test
        fun `should handle single character`() {
            CipherOperation.Reverse.apply("a") shouldBe "a"
        }

        @Test
        fun `should handle empty string`() {
            CipherOperation.Reverse.apply("") shouldBe ""
        }

        @Test
        fun `should handle signature-like string`() {
            val signature = "AOq0QJ8wRAIgZJKl"
            CipherOperation.Reverse.apply(signature) shouldBe "lKJZgIARw8JQ0qOA"
        }
    }

    @Nested
    @DisplayName("Swap operation")
    inner class SwapTests {

        @ParameterizedTest
        @CsvSource(
            "abcdef, 3, dbcaef",  // Swap position 0 with position 3
            "abcdef, 5, fbcdea",  // Swap position 0 with position 5
            "abcdef, 0, abcdef",  // Swap with self (no change)
            "abcdef, 1, bacdef"   // Swap first two characters
        )
        fun `should swap first character with character at index`(
            input: String,
            index: Int,
            expected: String
        ) {
            CipherOperation.Swap(index).apply(input) shouldBe expected
        }

        @Test
        fun `should use modulo for index out of bounds`() {
            // Index 8 % length 6 = 2, swap position 0 with position 2
            // Original: a b c d e f -> swap a and c -> c b a d e f
            CipherOperation.Swap(8).apply("abcdef") shouldBe "cbadef"
        }

        @Test
        fun `should handle single character`() {
            CipherOperation.Swap(5).apply("a") shouldBe "a"
        }

        @Test
        fun `should handle empty string`() {
            CipherOperation.Swap(3).apply("") shouldBe ""
        }
    }

    @Nested
    @DisplayName("Slice operation")
    inner class SliceTests {

        @ParameterizedTest
        @CsvSource(
            "abcdef, 0, abcdef",  // Remove 0 characters
            "abcdef, 1, bcdef",   // Remove first character
            "abcdef, 3, def",     // Remove first 3 characters
            "abcdef, 5, f"        // Remove first 5 characters
        )
        fun `should remove first n characters`(
            input: String,
            count: Int,
            expected: String
        ) {
            CipherOperation.Slice(count).apply(input) shouldBe expected
        }

        @Test
        fun `should return empty for count greater than or equal to length`() {
            CipherOperation.Slice(6).apply("abcdef") shouldBe ""
            CipherOperation.Slice(10).apply("abc") shouldBe ""
        }

        @Test
        fun `should handle empty string`() {
            CipherOperation.Slice(3).apply("") shouldBe ""
        }
    }

    @Nested
    @DisplayName("Combined operations (decipher)")
    inner class DecipherTests {

        @Test
        fun `should apply operations in sequence`() {
            val operations = listOf(
                CipherOperation.Reverse,           // "fedcba"
                CipherOperation.Slice(1),          // "edcba"
                CipherOperation.Swap(2)            // "cdeba"
            )

            CipherOperation.decipher("abcdef", operations) shouldBe "cdeba"
        }

        @Test
        fun `should handle empty operations list`() {
            CipherOperation.decipher("abcdef", emptyList()) shouldBe "abcdef"
        }

        @Test
        fun `should handle realistic cipher sequence`() {
            // Simulating a typical YouTube cipher sequence
            val signature = "AOq0QJ8wRAIgZ"
            val operations = listOf(
                CipherOperation.Slice(2),          // Remove first 2 chars
                CipherOperation.Reverse,           // Reverse
                CipherOperation.Swap(1),           // Swap 0 with 1
                CipherOperation.Slice(1),          // Remove first char
                CipherOperation.Reverse            // Reverse again
            )

            // Just verify it produces a result without error
            val result = CipherOperation.decipher(signature, operations)
            result.isNotEmpty() shouldBe true
        }
    }
}
