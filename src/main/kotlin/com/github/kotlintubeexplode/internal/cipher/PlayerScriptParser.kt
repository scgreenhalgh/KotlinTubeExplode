package com.github.kotlintubeexplode.internal.cipher

/**
 * Parses YouTube's player JavaScript (base.js) to extract cipher operations.
 *
 * This is a SAFE implementation that:
 * 1. Treats JavaScript as plain text
 * 2. Uses regex to identify function patterns
 * 3. Maps obfuscated function names to known operations
 * 4. Does NOT execute any JavaScript code
 *
 * The parsing strategy:
 * 1. Find the signature timestamp (sts) value
 * 2. Find the main decipher function (has split/join pattern)
 * 3. Extract the cipher container object name
 * 4. Parse the container object to map function names to operation types
 * 5. Parse the decipher function body to get the operation sequence
 */
internal class PlayerScriptParser {

    companion object {
        /**
         * Pattern to find signature timestamp.
         * Matches: signatureTimestamp:12345 or sts:12345
         */
        private val SIGNATURE_TIMESTAMP_PATTERN = Regex(
            """(?:signatureTimestamp|sts)\s*:\s*(\d{5})"""
        )

        /**
         * Pattern to find the main decipher function.
         * Matches functions that split a string, manipulate it, and join it back.
         *
         * Example: function(a){a=a.split("");Xy.Dz(a,3);Xy.Tk(a,45);return a.join("")}
         */
        private val DECIPHER_FUNCTION_PATTERN = Regex(
            """([$\w]+)\s*=\s*function\s*\(\s*[$\w]+\s*\)\s*\{\s*([$\w]+)\s*=\s*\2\.split\s*\(\s*""\s*\)\s*;(.+?)return\s+\2\.join\s*\(\s*""\s*\)\s*\}"""
        )

        /**
         * Alternative pattern for decipher function.
         */
        private val DECIPHER_FUNCTION_PATTERN_ALT = Regex(
            """function\s+([$\w]+)\s*\(\s*[$\w]+\s*\)\s*\{\s*([$\w]+)\s*=\s*\2\.split\s*\(\s*""\s*\)\s*;(.+?)return\s+\2\.join\s*\(\s*""\s*\)\s*\}"""
        )

        /**
         * Pattern to extract cipher container name from function calls.
         * Matches: Xy.Dz(a,3) or Xy["Dz"](a,3)
         */
        private val CONTAINER_NAME_PATTERN = Regex(
            """([$\w]+)(?:\.|(?:\[["']))[$\w]+(?:["']\])?\s*\(\s*[$\w]+\s*,\s*\d+\s*\)"""
        )

        /**
         * Pattern to find the cipher container object definition.
         * Matches: var Xy={Dz:function(a,b){...},Tk:function(a){...}};
         */
        private fun cipherContainerPattern(name: String) = Regex(
            """(?:var\s+)?${Regex.escape(name)}\s*=\s*\{(.+?)\};""",
            RegexOption.DOT_MATCHES_ALL
        )

        /**
         * Pattern to identify SWAP operation in function body.
         * Swap functions contain modulo (%) for index calculation.
         */
        private val SWAP_FUNCTION_PATTERN = Regex(
            """([$\w]+)\s*:\s*function\s*\(\s*[$\w]+\s*,\s*[$\w]+\s*\)\s*\{[^}]*?%[^}]*?\}"""
        )

        /**
         * Pattern to identify SLICE/SPLICE operation in function body.
         * Splice functions remove elements from array.
         */
        private val SPLICE_FUNCTION_PATTERN = Regex(
            """([$\w]+)\s*:\s*function\s*\(\s*[$\w]+\s*,\s*[$\w]+\s*\)\s*\{[^}]*?splice[^}]*?\}"""
        )

        /**
         * Pattern to identify REVERSE operation in function body.
         * Reverse functions have only one parameter and call reverse().
         */
        private val REVERSE_FUNCTION_PATTERN = Regex(
            """([$\w]+)\s*:\s*function\s*\(\s*[$\w]+\s*\)\s*\{[^}]*?reverse[^}]*?\}"""
        )

        /**
         * Pattern to extract function calls from decipher body.
         * Matches: Xy.Dz(a,3) or Xy["Dz"](a,3)
         */
        private val FUNCTION_CALL_PATTERN = Regex(
            """[$\w]+(?:\.|(?:\[["']))([$\w]+)(?:["']\])?\s*\(\s*[$\w]+\s*(?:,\s*(\d+)\s*)?\)"""
        )
    }

    /**
     * Parses the player JavaScript to extract the cipher manifest.
     *
     * @param playerScript The complete base.js content
     * @return The parsed CipherManifest
     * @throws CipherParseException if parsing fails
     */
    fun parse(playerScript: String): CipherManifest {
        // Step 1: Extract signature timestamp
        val signatureTimestamp = extractSignatureTimestamp(playerScript)
            ?: throw CipherParseException("Could not find signature timestamp")

        // Step 2: Find the decipher function
        val decipherFunctionMatch = findDecipherFunction(playerScript)
            ?: throw CipherParseException("Could not find decipher function")

        val decipherBody = decipherFunctionMatch.groupValues[3]

        // Step 3: Extract the cipher container name
        val containerName = extractContainerName(decipherBody)
            ?: throw CipherParseException("Could not find cipher container name")

        // Step 4: Find and parse the container object
        val containerDefinition = findContainerDefinition(playerScript, containerName)
            ?: throw CipherParseException("Could not find cipher container definition for: $containerName")

        // Step 5: Map function names to operation types
        val functionMap = parseFunctionMap(containerDefinition)

        // Step 6: Parse the operation sequence
        val operations = parseOperationSequence(decipherBody, functionMap)

        if (operations.isEmpty()) {
            throw CipherParseException("No cipher operations found")
        }

        return CipherManifest(signatureTimestamp, operations)
    }

    /**
     * Extracts the signature timestamp from the player script.
     */
    fun extractSignatureTimestamp(playerScript: String): String? {
        return SIGNATURE_TIMESTAMP_PATTERN.find(playerScript)?.groupValues?.getOrNull(1)
    }

    /**
     * Finds the decipher function in the script.
     */
    private fun findDecipherFunction(playerScript: String): MatchResult? {
        return DECIPHER_FUNCTION_PATTERN.find(playerScript)
            ?: DECIPHER_FUNCTION_PATTERN_ALT.find(playerScript)
    }

    /**
     * Extracts the cipher container name from function calls.
     */
    private fun extractContainerName(decipherBody: String): String? {
        return CONTAINER_NAME_PATTERN.find(decipherBody)?.groupValues?.getOrNull(1)
    }

    /**
     * Finds the container object definition in the script.
     */
    private fun findContainerDefinition(playerScript: String, containerName: String): String? {
        return cipherContainerPattern(containerName).find(playerScript)?.groupValues?.getOrNull(1)
    }

    /**
     * Parses the container definition to map function names to operation types.
     */
    private fun parseFunctionMap(containerDefinition: String): Map<String, OperationType> {
        val functionMap = mutableMapOf<String, OperationType>()

        // Find SWAP functions (contain %)
        SWAP_FUNCTION_PATTERN.findAll(containerDefinition).forEach { match ->
            match.groupValues.getOrNull(1)?.let { name ->
                functionMap[name] = OperationType.SWAP
            }
        }

        // Find SPLICE functions (contain splice)
        SPLICE_FUNCTION_PATTERN.findAll(containerDefinition).forEach { match ->
            match.groupValues.getOrNull(1)?.let { name ->
                functionMap[name] = OperationType.SLICE
            }
        }

        // Find REVERSE functions (contain reverse)
        REVERSE_FUNCTION_PATTERN.findAll(containerDefinition).forEach { match ->
            match.groupValues.getOrNull(1)?.let { name ->
                functionMap[name] = OperationType.REVERSE
            }
        }

        return functionMap
    }

    /**
     * Parses the operation sequence from the decipher function body.
     */
    private fun parseOperationSequence(
        decipherBody: String,
        functionMap: Map<String, OperationType>
    ): List<CipherOperation> {
        val operations = mutableListOf<CipherOperation>()

        // Find all function calls in order
        FUNCTION_CALL_PATTERN.findAll(decipherBody).forEach { match ->
            val functionName = match.groupValues.getOrNull(1) ?: return@forEach
            val argument = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0

            val operationType = functionMap[functionName] ?: return@forEach

            val operation = when (operationType) {
                OperationType.REVERSE -> CipherOperation.Reverse
                OperationType.SWAP -> CipherOperation.Swap(argument)
                OperationType.SLICE -> CipherOperation.Slice(argument)
            }

            operations.add(operation)
        }

        return operations
    }

    /**
     * Operation types that can be identified from function signatures.
     */
    private enum class OperationType {
        REVERSE,
        SWAP,
        SLICE
    }
}

/**
 * Exception thrown when cipher parsing fails.
 */
class CipherParseException(message: String) : Exception(message)
