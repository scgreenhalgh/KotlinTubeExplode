package com.github.kotlintubeexplode.internal.cipher

/**
 * Holds the parsed cipher manifest extracted from YouTube's player JavaScript.
 *
 * Contains the signature timestamp (used for some API requests) and the
 * ordered list of cipher operations needed to decrypt stream signatures.
 *
 * @param signatureTimestamp The signature timestamp (sts) value
 * @param operations The ordered list of cipher operations
 */
data class CipherManifest(
    val signatureTimestamp: String,
    val operations: List<CipherOperation>
) {
    /**
     * Decrypts a signature using this manifest's operations.
     *
     * @param signature The encrypted signature
     * @return The decrypted signature
     */
    fun decipher(signature: String): String {
        return CipherOperation.decipher(signature, operations)
    }

    companion object {
        /**
         * Creates an empty manifest (for cases where cipher is not needed).
         */
        val EMPTY = CipherManifest("0", emptyList())
    }
}
