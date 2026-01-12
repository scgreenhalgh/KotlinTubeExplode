package com.github.kotlintubeexplode.core

import java.net.URLDecoder

/**
 * Represents a syntactically valid YouTube video ID.
 *
 * YouTube video IDs are exactly 11 characters long and can contain:
 * - Alphanumeric characters (a-z, A-Z, 0-9)
 * - Underscores (_)
 * - Hyphens (-)
 *
 * This value object can parse IDs from:
 * - Raw 11-character IDs
 * - Full YouTube URLs (youtube.com/watch?v=...)
 * - Short URLs (youtu.be/...)
 * - Embed URLs (youtube.com/embed/...)
 * - Shorts URLs (youtube.com/shorts/...)
 * - Live URLs (youtube.com/live/...)
 */
@JvmInline
value class VideoId internal constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        /**
         * Maximum input length to prevent ReDoS attacks.
         * YouTube URLs should never exceed this length.
         */
        private const val MAX_INPUT_LENGTH = 2048

        /**
         * Validates that a string is a valid raw video ID.
         * Must be exactly 11 characters: alphanumeric, underscore, or hyphen.
         */
        private fun isValidId(videoId: String): Boolean =
            videoId.length == 11 && videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }

        /**
         * URL decodes a string safely.
         */
        private fun urlDecode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

        /**
         * Attempts to normalize the input to a valid video ID.
         */
        private fun tryNormalize(videoIdOrUrl: String?): String? {
            if (videoIdOrUrl.isNullOrBlank()) return null

            // Reject excessively long inputs to prevent ReDoS
            if (videoIdOrUrl.length > MAX_INPUT_LENGTH) return null

            val input = videoIdOrUrl.trim()

            // Check if already a valid ID
            if (isValidId(input)) return input

            // Try to extract ID from watch URL
            // https://www.youtube.com/watch?v=yIVRs6YSbOM
            // Use word boundary to avoid matching "notyoutube.com" etc.
            Regex("""(?:^|://|www\.)youtube\..+?/watch.*?v=(.*?)(?:&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from youtu.be/watch URL (partially shortened)
            // https://youtu.be/watch?v=Fcds0_MrgNU
            Regex("""youtu\.be/watch.*?v=(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from short URL
            // https://youtu.be/yIVRs6YSbOM
            Regex("""youtu\.be/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from embed URL
            // https://www.youtube.com/embed/yIVRs6YSbOM
            Regex("""(?:^|://|www\.)youtube\..+?/embed/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from v/ URL
            // https://www.youtube.com/v/yIVRs6YSbOM
            Regex("""(?:^|://|www\.)youtube\..+?/v/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from shorts URL
            // https://www.youtube.com/shorts/sKL1vjP0tIo
            Regex("""(?:^|://|www\.)youtube\..+?/shorts/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Try to extract ID from live URL
            // https://www.youtube.com/live/jfKfPfyJRdk
            Regex("""(?:^|://|www\.)youtube\..+?/live/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            // Invalid input
            return null
        }

        /**
         * Parses a video ID from a raw ID string or YouTube URL.
         *
         * @param input Raw video ID or YouTube URL
         * @return VideoId instance containing the validated ID
         * @throws IllegalArgumentException if the input is not a valid video ID or URL
         */
        fun parse(input: String): VideoId {
            return tryParse(input)
                ?: throw IllegalArgumentException("Invalid YouTube video ID or URL: $input")
        }

        /**
         * Attempts to parse a video ID from a raw ID string or YouTube URL.
         *
         * @param input Raw video ID or YouTube URL
         * @return VideoId instance if valid, null otherwise
         */
        fun tryParse(input: String): VideoId? =
            tryNormalize(input)?.let { VideoId(it) }

        /**
         * Checks if the input is a valid video ID or YouTube URL.
         *
         * @param input Raw video ID or YouTube URL
         * @return true if valid, false otherwise
         */
        fun isValid(input: String): Boolean = tryParse(input) != null
    }
}
