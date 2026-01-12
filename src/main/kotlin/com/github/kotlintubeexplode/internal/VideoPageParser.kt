package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.internal.dto.PlayerResponseDto
import kotlinx.serialization.json.Json

/**
 * Parser for YouTube video watch pages.
 *
 * Extracts the embedded `ytInitialPlayerResponse` JSON from the HTML
 * and parses it into a structured DTO.
 */
internal class VideoPageParser {

    companion object {
        /**
         * Regex patterns to find ytInitialPlayerResponse in HTML.
         *
         * YouTube embeds this data in various ways depending on page type.
         */
        private val PLAYER_RESPONSE_PATTERNS = listOf(
            // Standard pattern: var ytInitialPlayerResponse = {...};
            Regex("""var\s+ytInitialPlayerResponse\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),

            // Alternative pattern with window assignment
            Regex("""window\["ytInitialPlayerResponse"\]\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),

            // Pattern in ytplayer.config
            Regex("""ytplayer\.config\s*=\s*\{.*?"args"\s*:\s*\{.*?"player_response"\s*:\s*"(.+?)".*?\}""", RegexOption.DOT_MATCHES_ALL)
        )

        /**
         * Pattern to extract player script URL from HTML.
         */
        private val PLAYER_SCRIPT_PATTERN = Regex(
            """(?:/s/player/|/player/)([a-zA-Z0-9_-]+)/.*?(?:base|player_ias(?:\.vflset)?/[a-zA-Z_]+/base)\.js"""
        )

        /**
         * Pattern to find player version from iframe_api.
         */
        private val PLAYER_VERSION_PATTERN = Regex(
            """player\\?/([0-9a-fA-F]{8})\\?/"""
        )

        /**
         * JSON parser configured for YouTube responses.
         */
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    /**
     * Parses the video watch page HTML and extracts player response data.
     *
     * @param html The raw HTML content of the watch page
     * @return Parsed PlayerResponseDto
     * @throws VideoParseException if parsing fails
     */
    fun parseWatchPage(html: String): PlayerResponseDto {
        val jsonString = extractPlayerResponseJson(html)
            ?: throw VideoParseException("Could not find ytInitialPlayerResponse in HTML")

        return try {
            json.decodeFromString<PlayerResponseDto>(jsonString)
        } catch (e: Exception) {
            throw VideoParseException("Failed to parse player response JSON: ${e.message}", e)
        }
    }

    /**
     * Extracts the raw JSON string for ytInitialPlayerResponse from HTML.
     *
     * @param html The raw HTML content
     * @return The JSON string, or null if not found
     */
    fun extractPlayerResponseJson(html: String): String? {
        for ((index, pattern) in PLAYER_RESPONSE_PATTERNS.withIndex()) {
            val match = pattern.find(html)
            if (match != null) {
                val jsonCandidate = match.groupValues.getOrNull(1) ?: continue

                // Only unescape for the third pattern (ytplayer.config) where
                // JSON is embedded inside a JavaScript string and thus double-escaped.
                // Pattern index 2 is the ytplayer.config pattern.
                val processed = if (index == 2) {
                    jsonCandidate
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\/", "/")
                } else {
                    jsonCandidate
                }

                // Validate it's actual JSON by checking balanced braces
                if (isValidJson(processed)) {
                    return processed
                }

                // Try to extract balanced JSON object
                html.extractJsonObject(match.range.first)?.let { extracted ->
                    if (isValidJson(extracted)) {
                        return extracted
                    }
                }
            }
        }
        return null
    }

    /**
     * Extracts the player script URL from the watch page HTML.
     *
     * @param html The raw HTML content
     * @return The full URL to base.js, or null if not found
     */
    fun extractPlayerScriptUrl(html: String): String? {
        val match = PLAYER_SCRIPT_PATTERN.find(html) ?: return null
        val path = match.value
        return "https://www.youtube.com$path"
    }

    /**
     * Extracts player version from iframe_api response.
     *
     * @param iframeApiContent The content of /iframe_api endpoint
     * @return The player version string (8 hex characters)
     */
    fun extractPlayerVersion(iframeApiContent: String): String? {
        return PLAYER_VERSION_PATTERN.find(iframeApiContent)?.groupValues?.getOrNull(1)
    }

    /**
     * Parses a raw player response JSON string.
     *
     * Used when the player response is obtained directly from an API call
     * rather than extracted from an HTML page.
     *
     * @param jsonString The raw JSON content
     * @return Parsed PlayerResponseDto
     * @throws VideoParseException if parsing fails
     */
    fun parsePlayerResponse(jsonString: String): PlayerResponseDto {
        return try {
            json.decodeFromString<PlayerResponseDto>(jsonString)
        } catch (e: Exception) {
            throw VideoParseException("Failed to parse player response JSON: ${e.message}", e)
        }
    }

    /**
     * Validates that a string appears to be valid JSON.
     * This is a quick check, not a full parse.
     */
    private fun isValidJson(json: String): Boolean {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false
        }

        // Quick balance check
        var depth = 0
        var inString = false
        var escape = false

        for (char in trimmed) {
            if (escape) {
                escape = false
                continue
            }
            when {
                char == '\\' && inString -> escape = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> depth++
                !inString && char == '}' -> depth--
            }
        }

        return depth == 0
    }
}

/**
 * Exception thrown when video page parsing fails.
 */
class VideoParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
