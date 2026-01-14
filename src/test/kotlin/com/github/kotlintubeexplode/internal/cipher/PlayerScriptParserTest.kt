package com.github.kotlintubeexplode.internal.cipher

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlayerScriptParserTest {

    private val parser = PlayerScriptParser()

    @Nested
    inner class ParsingTests {
        @Test
        fun `should parse valid player script`() {
            // A minimal mock player script (minified to single line as regex expects)
            val script = "var config={sts:19000};var Xy={Dz:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c},Tk:function(a){a.reverse()},Spl:function(a,b){a.splice(0,b)}};abc=function(a){a=a.split(\"\");Xy.Dz(a,3);Xy.Tk(a,45);Xy.Spl(a,2);return a.join(\"\")}"

            val manifest = parser.parse(script)

            manifest.signatureTimestamp shouldBe "19000"
            manifest.operations shouldHaveSize 3
            
            // Verify operations
            manifest.operations[0] shouldBe CipherOperation.Swap(3)
            manifest.operations[1] shouldBe CipherOperation.Reverse
            manifest.operations[2] shouldBe CipherOperation.Slice(2)
        }

        @Test
        fun `should extract signature timestamp`() {
            val script = "signatureTimestamp:12345"
            parser.extractSignatureTimestamp(script) shouldBe "12345"
        }

        @Test
        fun `should extract sts timestamp`() {
            val script = "sts:54321"
            parser.extractSignatureTimestamp(script) shouldBe "54321"
        }
    }

    @Nested
    inner class ErrorHandlingTests {
        @Test
        fun `should throw when timestamp is missing`() {
            val script = "var nothing = 0;"
            
            val exception = shouldThrow<CipherParseException> {
                parser.parse(script)
            }
            exception.message shouldBe "Could not find signature timestamp"
        }

        @Test
        fun `should throw when decipher function is missing`() {
            val script = "sts:19000,var nothing = function(){};"

            val exception = shouldThrow<CipherParseException> {
                parser.parse(script)
            }
            exception.message shouldBe "Could not find decipher function"
        }
    }

    @Nested
    inner class DecipherTests {
        @Test
        fun `should decipher signature correctly`() {
            // "abcdefg" -> Swap(1) -> "bacdefg" -> Reverse -> "gfedcab" -> Slice(2) -> "edcab"
            val operations = listOf(
                CipherOperation.Swap(1),
                CipherOperation.Reverse,
                CipherOperation.Slice(2)
            )
            val manifest = CipherManifest("1000", operations)
            
            val signature = "abcdefg"
            // Trace:
            // Swap(1): a<->b => bacdefg
            // Reverse: => gfedcab
            // Slice(2): remove first 2 => edcab
            
            manifest.decipher(signature) shouldBe "edcab"
        }

        @Test
        fun `should handle empty signature`() {
            val manifest = CipherManifest("1000", emptyList())
            manifest.decipher("") shouldBe ""
        }
    }
}
