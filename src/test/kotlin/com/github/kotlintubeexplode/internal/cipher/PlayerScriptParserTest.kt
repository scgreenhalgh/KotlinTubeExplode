package com.github.kotlintubeexplode.internal.cipher

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PlayerScriptParser")
class PlayerScriptParserTest {

    private lateinit var parser: PlayerScriptParser

    @BeforeEach
    fun setup() {
        parser = PlayerScriptParser()
    }

    @Nested
    @DisplayName("extractSignatureTimestamp")
    inner class SignatureTimestampTests {

        @Test
        fun `should extract signatureTimestamp format`() {
            val script = """
                var config = {
                    signatureTimestamp: 19876
                };
            """.trimIndent()

            parser.extractSignatureTimestamp(script) shouldBe "19876"
        }

        @Test
        fun `should extract sts format`() {
            val script = """
                sts:20001,
                other: "value"
            """.trimIndent()

            parser.extractSignatureTimestamp(script) shouldBe "20001"
        }

        @Test
        fun `should return null when not found`() {
            val script = "no timestamp here"
            parser.extractSignatureTimestamp(script) shouldBe null
        }
    }

    @Nested
    @DisplayName("parse - full cipher manifest extraction")
    inner class FullParseTests {

        @Test
        fun `should parse complete cipher manifest from mock player script`() {
            val script = createMockPlayerScript()

            val manifest = parser.parse(script)

            manifest.signatureTimestamp shouldBe "19876"
            manifest.operations.shouldHaveSize(4)
        }

        @Test
        fun `should identify correct operation types in order`() {
            val script = createMockPlayerScript()

            val manifest = parser.parse(script)

            // The mock script defines: Xy.Tk (reverse), Xy.Dz (splice/slice), Xy.Qm (swap)
            // Decipher function calls: Xy.Tk(a,5); Xy.Dz(a,3); Xy.Qm(a,7); Xy.Tk(a,0);
            manifest.operations[0].shouldBeInstanceOf<CipherOperation.Reverse>()
            manifest.operations[1].shouldBeInstanceOf<CipherOperation.Slice>()
            (manifest.operations[1] as CipherOperation.Slice).count shouldBe 3
            manifest.operations[2].shouldBeInstanceOf<CipherOperation.Swap>()
            (manifest.operations[2] as CipherOperation.Swap).index shouldBe 7
            manifest.operations[3].shouldBeInstanceOf<CipherOperation.Reverse>()
        }

        @Test
        fun `should correctly decipher with parsed operations`() {
            val script = createMockPlayerScript()
            val manifest = parser.parse(script)

            // Apply the operations manually to verify
            val input = "abcdefghij"

            // Step 1: Reverse -> jihgfedcba
            // Step 2: Slice(3) -> gfedcba
            // Step 3: Swap(7 % 7 = 0) -> gfedcba (swap with self)
            // Step 4: Reverse -> abcdefg

            val result = manifest.decipher(input)
            result shouldBe "abcdefg"
        }

        @Test
        fun `should throw CipherParseException when timestamp not found`() {
            val script = """
                var someFunc = function(a) { a = a.split(""); Xy.do(a,3); return a.join(""); };
                var Xy = { do: function(a,b) { a.splice(0,b); } };
            """.trimIndent()

            shouldThrow<CipherParseException> {
                parser.parse(script)
            }
        }

        @Test
        fun `should throw CipherParseException when decipher function not found`() {
            val script = """
                signatureTimestamp: 19876,
                var notADecipher = function(a) { return a.toUpperCase(); };
            """.trimIndent()

            shouldThrow<CipherParseException> {
                parser.parse(script)
            }
        }
    }

    @Nested
    @DisplayName("CipherManifest.decipher")
    inner class CipherManifestDecipherTests {

        @Test
        fun `should apply all operations in sequence`() {
            val manifest = CipherManifest(
                signatureTimestamp = "12345",
                operations = listOf(
                    CipherOperation.Slice(2),
                    CipherOperation.Reverse,
                    CipherOperation.Swap(1)
                )
            )

            // "abcdefgh" -> Slice(2) -> "cdefgh"
            // "cdefgh" -> Reverse -> "hgfedc"
            // "hgfedc" -> Swap(1) -> "ghfedc"
            manifest.decipher("abcdefgh") shouldBe "ghfedc"
        }

        @Test
        fun `empty manifest should return input unchanged`() {
            CipherManifest.EMPTY.decipher("test") shouldBe "test"
        }
    }

    /**
     * Creates a mock player script that mimics YouTube's base.js structure.
     *
     * This is a simplified version that contains all the patterns the parser looks for.
     * The script is minified (single-line functions) as that's how YouTube's actual base.js looks.
     */
    private fun createMockPlayerScript(): String {
        // Minified format matching YouTube's actual base.js structure
        return """signatureTimestamp:19876,var Xy={Tk:function(a){a.reverse()},Dz:function(a,b){a.splice(0,b)},Qm:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c}};var decipherSignature=function(a){a=a.split("");Xy.Tk(a,5);Xy.Dz(a,3);Xy.Qm(a,7);Xy.Tk(a,0);return a.join("")}"""
    }
}
