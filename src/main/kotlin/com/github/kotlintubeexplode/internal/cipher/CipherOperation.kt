package com.github.kotlintubeexplode.internal.cipher

/**
 * Represents a single cipher operation used to decrypt YouTube signatures.
 *
 * YouTube encrypts stream URLs with signatures that must be decrypted using
 * a sequence of operations parsed from the player JavaScript. These operations
 * are simple string manipulations: reverse, swap, and slice.
 *
 * SECURITY NOTE: This implementation is SAFE because it:
 * 1. Does NOT use any JavaScript engine (no javax.script, no eval)
 * 2. Parses JavaScript as text using regex
 * 3. Emulates only 3 simple mathematical operations in Kotlin
 */
sealed class CipherOperation {

    /**
     * Applies this operation to the input string.
     *
     * @param input The signature string to transform
     * @return The transformed string
     */
    abstract fun apply(input: String): String

    /**
     * Reverses the entire string.
     *
     * JavaScript equivalent: `a.reverse()` where a is char array
     */
    data object Reverse : CipherOperation() {
        override fun apply(input: String): String = input.reversed()
    }

    /**
     * Swaps the character at position 0 with the character at position [index].
     *
     * JavaScript equivalent: `var c=a[0];a[0]=a[b%a.length];a[b]=c;`
     *
     * @param index The position to swap with (uses modulo for bounds safety)
     */
    data class Swap(val index: Int) : CipherOperation() {
        override fun apply(input: String): String {
            if (input.isEmpty()) return input
            val actualIndex = index % input.length
            return input.swapCharsAt(0, actualIndex)
        }

        private fun String.swapCharsAt(i: Int, j: Int): String {
            if (i == j) return this
            val chars = toCharArray()
            val temp = chars[i]
            chars[i] = chars[j]
            chars[j] = temp
            return String(chars)
        }
    }

    /**
     * Removes the first [count] characters from the string.
     *
     * JavaScript equivalent: `a.splice(0, b)` or `a.slice(b)`
     *
     * @param count The number of characters to remove from the start
     */
    data class Slice(val count: Int) : CipherOperation() {
        override fun apply(input: String): String {
            if (count >= input.length) return ""
            return input.substring(count)
        }
    }

    companion object {
        /**
         * Applies a sequence of operations to decrypt a signature.
         *
         * @param signature The encrypted signature
         * @param operations The ordered list of operations to apply
         * @return The decrypted signature
         */
        fun decipher(signature: String, operations: List<CipherOperation>): String {
            return operations.fold(signature) { acc, op -> op.apply(acc) }
        }
    }
}
