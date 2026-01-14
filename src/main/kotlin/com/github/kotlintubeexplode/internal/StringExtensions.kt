package com.github.kotlintubeexplode.internal

/**
 * String extension functions for parsing and manipulation.
 *
 * These utilities are essential for extracting data from YouTube's
 * HTML pages and JavaScript files.
 */

/**
 * Extracts the substring between two delimiter strings.
 *
 * @param start The starting delimiter (exclusive)
 * @param end The ending delimiter (exclusive)
 * @return The substring between the delimiters, or null if not found
 *
 * Example:
 * ```
 * "Hello [World] Goodbye".substringBetween("[", "]") // returns "World"
 * ```
 */
fun String.substringBetween(start: String, end: String): String? {
    val startIndex = indexOf(start)
    if (startIndex == -1) return null

    val contentStart = startIndex + start.length
    val endIndex = indexOf(end, contentStart)
    if (endIndex == -1) return null

    return substring(contentStart, endIndex)
}

/**
 * Extracts the substring after the first occurrence of a delimiter.
 *
 * @param delimiter The delimiter string
 * @return The substring after the delimiter, or null if delimiter not found
 *
 * Example:
 * ```
 * "key=value".substringAfterOrNull("=") // returns "value"
 * ```
 */
fun String.substringAfterOrNull(delimiter: String): String? {
    val index = indexOf(delimiter)
    return if (index == -1) null else substring(index + delimiter.length)
}

/**
 * Extracts the substring before the first occurrence of a delimiter.
 *
 * @param delimiter The delimiter string
 * @return The substring before the delimiter, or null if delimiter not found
 *
 * Example:
 * ```
 * "key=value".substringBeforeOrNull("=") // returns "key"
 * ```
 */
fun String.substringBeforeOrNull(delimiter: String): String? {
    val index = indexOf(delimiter)
    return if (index == -1) null else substring(0, index)
}

/**
 * Extracts the substring after the first occurrence, or returns the original string.
 *
 * @param delimiter The delimiter string
 * @return The substring after the delimiter, or the original string if not found
 */
fun String.substringAfterFirst(delimiter: String): String =
    substringAfterOrNull(delimiter) ?: this

/**
 * Extracts the substring before the first occurrence, or returns the original string.
 *
 * @param delimiter The delimiter string
 * @return The substring before the delimiter, or the original string if not found
 */
fun String.substringBeforeFirst(delimiter: String): String =
    substringBeforeOrNull(delimiter) ?: this

/**
 * Extracts the substring after the last occurrence of a delimiter.
 *
 * @param delimiter The delimiter string
 * @return The substring after the last delimiter, or null if not found
 */
fun String.substringAfterLastOrNull(delimiter: String): String? {
    val index = lastIndexOf(delimiter)
    return if (index == -1) null else substring(index + delimiter.length)
}

/**
 * Strips all non-digit characters from the string.
 *
 * @return String containing only digit characters
 *
 * Example:
 * ```
 * "1,234,567 views".stripNonDigits() // returns "1234567"
 * ```
 */
fun String.stripNonDigits(): String = filter { it.isDigit() }

/**
 * Parses a view count string (e.g., "1,234,567 views") to a Long.
 *
 * @return The numeric value, or null if parsing fails
 */
fun String.parseViewCount(): Long? = stripNonDigits().toLongOrNull()

/**
 * Parses a duration string (e.g., "1:23:45" or "PT1H23M45S") to seconds.
 *
 * @return Duration in seconds, or null if parsing fails
 */
fun String.parseDurationSeconds(): Long? {
    // Try ISO 8601 format first (PT1H23M45S)
    if (startsWith("PT")) {
        return parseIsoDuration()
    }

    // Try colon-separated format (1:23:45 or 23:45)
    val parts = split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2] // H:M:S
        2 -> parts[0] * 60 + parts[1]                    // M:S
        1 -> parts[0]                                     // S
        else -> null
    }
}

/**
 * Parses an ISO 8601 duration string (e.g., "PT1H23M45S") to seconds.
 */
private fun String.parseIsoDuration(): Long? {
    var seconds = 0L
    var currentNumber = StringBuilder()

    for (char in this) {
        when {
            char.isDigit() -> currentNumber.append(char)
            char == 'H' -> {
                seconds += (currentNumber.toString().toLongOrNull() ?: 0) * 3600
                currentNumber = StringBuilder()
            }
            char == 'M' -> {
                seconds += (currentNumber.toString().toLongOrNull() ?: 0) * 60
                currentNumber = StringBuilder()
            }
            char == 'S' -> {
                seconds += currentNumber.toString().toLongOrNull() ?: 0
                currentNumber = StringBuilder()
            }
        }
    }

    return if (seconds > 0) seconds else null
}

/**
 * Returns null if this string is empty or contains only whitespace.
 */
fun String.nullIfBlank(): String? = ifBlank { null }

/**
 * Swaps characters at two positions in the string.
 *
 * @param index1 First position
 * @param index2 Second position
 * @return New string with characters swapped
 */
fun String.swapChars(index1: Int, index2: Int): String {
    if (index1 == index2 || index1 < 0 || index2 < 0 || index1 >= length || index2 >= length) {
        return this
    }
    val chars = toCharArray()
    val temp = chars[index1]
    chars[index1] = chars[index2]
    chars[index2] = temp
    return String(chars)
}

/**
 * Extracts a balanced JSON object from a string starting at the given position.
 *
 * This handles nested braces correctly, which simple regex cannot do.
 *
 * @param startIndex The position of the opening brace
 * @return The complete JSON object string, or null if not found
 */
fun String.extractJsonObject(startIndex: Int = 0): String? {
    val openBrace = indexOf('{', startIndex)
    if (openBrace == -1) return null

    var depth = 0
    var inString = false
    var escape = false

    for (i in openBrace until length) {
        val char = this[i]

        if (escape) {
            escape = false
            continue
        }

        when {
            char == '\\' && inString -> escape = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> depth++
            !inString && char == '}' -> {
                depth--
                if (depth == 0) {
                    return substring(openBrace, i + 1)
                }
            }
        }
    }

    return null
}

/**
 * URL decodes a string safely.
 *
 * @return Decoded string, or original if decoding fails
 */
fun String.urlDecode(): String = try {
    java.net.URLDecoder.decode(this, "UTF-8")
} catch (e: Exception) {
    this
}

/**
 * URL encodes a string.
 *
 * @return Encoded string
 */
fun String.urlEncode(): String = try {
    java.net.URLEncoder.encode(this, "UTF-8")
} catch (e: Exception) {
    this
}

/**
 * Sanitizes a string for use as a filename.
 *
 * Replaces illegal characters (/, \, :, *, ?, ", <, >, |) with underscores.
 * Also trims the string and ensures it's not empty.
 *
 * @return Safe filename string
 */
fun String.sanitizeFileName(): String {
    val invalidChars = Regex("""[\\/:*?"<>|]""")
    val sanitized = replace(invalidChars, "_").trim()
    return sanitized.ifBlank { "video" }
}

/**
 * Parses URL query parameters into a map.
 *
 * @return Map of parameter names to values
 */
fun String.parseQueryParameters(): Map<String, String> {
    val query = substringAfterOrNull("?") ?: this
    return query.split("&")
        .mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                parts[0].urlDecode() to parts[1].urlDecode()
            } else null
        }
        .toMap()
}

/**
 * Gets a query parameter value from a URL.
 *
 * @param name The parameter name
 * @return The parameter value, or null if not found
 */
fun String.getQueryParameter(name: String): String? = parseQueryParameters()[name]

/**
 * Sets or replaces a query parameter in a URL.
 *
 * @param name The parameter name
 * @param value The parameter value
 * @return The URL with the parameter set
 */
fun String.setQueryParameter(name: String, value: String): String {
    val baseUrl = substringBeforeFirst("?")
    val params = parseQueryParameters().toMutableMap()
    params[name] = value

    val queryString = params.entries.joinToString("&") { (k, v) ->
        "${k.urlEncode()}=${v.urlEncode()}"
    }

    return "$baseUrl?$queryString"
}
