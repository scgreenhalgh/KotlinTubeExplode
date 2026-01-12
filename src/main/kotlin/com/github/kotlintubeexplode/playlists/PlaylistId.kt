package com.github.kotlintubeexplode.playlists

import java.net.URLDecoder

/**
 * Represents a syntactically valid YouTube playlist ID.
 *
 * Playlist IDs are at least 2 characters long and can contain:
 * - Alphanumeric characters (a-z, A-Z, 0-9)
 * - Underscores (_)
 * - Hyphens (-)
 *
 * This value object can parse IDs from:
 * - Raw playlist IDs
 * - Full URLs (youtube.com/playlist?list=...)
 * - Watch URLs with playlist (youtube.com/watch?v=...&list=...)
 * - Embed URLs with playlist
 */
@JvmInline
value class PlaylistId(val value: String) {

    override fun toString(): String = value

    /**
     * The full URL to this playlist on YouTube.
     */
    val url: String get() = "https://www.youtube.com/playlist?list=$value"

    companion object {
        /**
         * Maximum input length to prevent ReDoS attacks.
         * YouTube URLs should never exceed this length.
         */
        private const val MAX_INPUT_LENGTH = 2048

        /**
         * Validates that a string is a valid raw playlist ID.
         * Must be at least 2 characters: alphanumeric, underscore, or hyphen.
         */
        private fun isValidId(playlistId: String): Boolean =
            playlistId.length >= 2 && playlistId.all { it.isLetterOrDigit() || it == '_' || it == '-' }

        /**
         * URL decodes a string safely.
         */
        private fun urlDecode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

        /**
         * Attempts to normalize the input to a valid playlist ID.
         */
        private fun tryNormalize(playlistIdOrUrl: String?): String? {
            if (playlistIdOrUrl.isNullOrBlank()) return null

            // Reject excessively long inputs to prevent ReDoS
            if (playlistIdOrUrl.length > MAX_INPUT_LENGTH) return null

            val input = playlistIdOrUrl.trim()

            // Check if already a valid ID
            if (isValidId(input)) return input

            // Try to extract ID from playlist URL
            // https://www.youtube.com/playlist?list=PLxxxxxx
            Regex("""youtube\..+?/playlist.*?list=(.*?)(?:&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from watch URL with list parameter
            // https://www.youtube.com/watch?v=xxx&list=PLxxxxxx
            Regex("""youtube\..+?/watch.*?list=(.*?)(?:&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from shortened URL
            // https://youtu.be/xxx?list=PLxxxxxx
            Regex("""youtu\.be/.*?list=(.*?)(?:&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from embed URL
            // https://www.youtube.com/embed/xxx?list=PLxxxxxx
            Regex("""youtube\..+?/embed/.*?list=(.*?)(?:&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            return null
        }

        /**
         * Parses a playlist ID from a raw ID string or YouTube URL.
         *
         * @param input Raw playlist ID or YouTube URL
         * @return PlaylistId instance containing the validated ID
         * @throws IllegalArgumentException if the input is not a valid playlist ID or URL
         */
        fun parse(input: String): PlaylistId {
            return tryParse(input)
                ?: throw IllegalArgumentException("Invalid YouTube playlist ID or URL: $input")
        }

        /**
         * Attempts to parse a playlist ID from a raw ID string or YouTube URL.
         *
         * @param input Raw playlist ID or YouTube URL
         * @return PlaylistId instance if valid, null otherwise
         */
        fun tryParse(input: String): PlaylistId? =
            tryNormalize(input)?.let { PlaylistId(it) }

        /**
         * Checks if the input is a valid playlist ID or YouTube URL.
         *
         * @param input Raw playlist ID or YouTube URL
         * @return true if valid, false otherwise
         */
        fun isValid(input: String): Boolean = tryParse(input) != null
    }
}
