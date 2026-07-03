package com.github.kotlintubeexplode.internal.cipher

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

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

    @Nested
    inner class ReDoSGuardTests {
        @Test
        fun `should not hang on a pathologically crafted player script`() {
            // A valid sts (so parsing proceeds past the timestamp) followed by ~3.5MB of decipher
            // function *prefixes* that never terminate with `return b.join("")` — sized just under
            // the parser's script cap so it exercises the per-anchor BODY BOUND, not the size
            // rejection. With a tight bound the near-cap input fails fast; a loose bound (or the old
            // unbounded capture) spends tens of seconds here (~23s at 3.5MB with a 30k bound).
            val pathological = "sts:12345;" + "a=function(b){b=b.split(\"\");zzz;".repeat(113_000)
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                runCatching { parser.parse(pathological) }
            }
        }

        @Test
        fun `should not hang on a container-name flood`() {
            // A valid decipher fn whose body references a 1-char container name "Z", followed by
            // ~3.5MB of unclosed `Z={aa` prefixes (just under the cap). The old container regex
            // (find + lazy body capture over the whole script) backtracks catastrophically here
            // (~68s); a linear brace-scan resolves it in ms (no balanced container -> not found).
            val decipher = "abc=function(a){a=a.split(\"\");Z.d(a,3);return a.join(\"\")};"
            val pathological = "sts:12345;" + decipher + "Z={aa".repeat(700_000)
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                runCatching { parser.parse(pathological) }
            }
        }

        @Test
        fun `should not hang on a word-run in the decipher-function name position`() {
            // The decipher pattern's leading `([$\w]+)=` is run over the FULL script by find(). A
            // long word-run (no `=`) makes the greedy name group scan+backtrack the whole run at
            // every start position -> O(n^2) (~72s at 100KB). Bounding the name quantifier fixes it.
            val pathological = "sts:12345;" + "z".repeat(100_000)
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                runCatching { parser.parse(pathological) }
            }
        }

        @Test
        fun `should not hang on a word-run inside the cipher container body`() {
            // parseFunctionMap's SWAP/SPLICE/REVERSE patterns share the same leading `([$\w]+):`
            // name group; a word-run in the container body blows them up O(n^2) (~54s each). The
            // container-body cap plus the bounded name quantifier keep it fast.
            val decipher = "Z=function(a){a=a.split(\"\");Z.d(a,3);return a.join(\"\")};"
            val pathological = "sts:12345;" + decipher + "Z={A:function(a,b){" + "z".repeat(98_000) + "}}"
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                runCatching { parser.parse(pathological) }
            }
        }

        @Test
        fun `should reject an oversized player script instead of scanning it`() {
            val huge = " ".repeat(11 * 1024 * 1024) // 11MB, above the parse cap
            val ex = shouldThrow<CipherParseException> { parser.parse(huge) }
            ex.message shouldContain "too large"
        }
    }
}
