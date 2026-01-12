package com.github.kotlintubeexplode.channels

import java.net.URLDecoder

/**
 * Represents a syntactically valid YouTube channel ID.
 *
 * Channel IDs:
 * - Start with "UC"
 * - Are exactly 24 characters long
 * - Contain alphanumeric characters, underscores, and hyphens
 */
@JvmInline
value class ChannelId(val value: String) {

    override fun toString(): String = value

    /**
     * The full URL to this channel on YouTube.
     */
    val url: String get() = "https://www.youtube.com/channel/$value"

    companion object {
        /**
         * Maximum input length to prevent ReDoS attacks.
         * YouTube URLs should never exceed this length.
         */
        private const val MAX_INPUT_LENGTH = 2048

        /**
         * Validates that a string is a valid raw channel ID.
         */
        private fun isValidId(channelId: String): Boolean =
            channelId.length == 24 &&
            channelId.startsWith("UC") &&
            channelId.all { it.isLetterOrDigit() || it == '_' || it == '-' }

        /**
         * URL decodes a string safely.
         */
        private fun urlDecode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

        /**
         * Attempts to normalize the input to a valid channel ID.
         */
        private fun tryNormalize(channelIdOrUrl: String?): String? {
            if (channelIdOrUrl.isNullOrBlank()) return null

            // Reject excessively long inputs to prevent ReDoS
            if (channelIdOrUrl.length > MAX_INPUT_LENGTH) return null

            val input = channelIdOrUrl.trim()

            // Check if already a valid ID
            if (isValidId(input)) return input

            // Try to extract ID from channel URL
            // https://www.youtube.com/channel/UCxxxxxx
            Regex("""youtube\..+?/channel/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidId(it) }
                ?.let { return it }

            return null
        }

        /**
         * Parses a channel ID from a raw ID string or YouTube URL.
         *
         * @param input Raw channel ID or YouTube URL
         * @return ChannelId instance containing the validated ID
         * @throws IllegalArgumentException if the input is not a valid channel ID or URL
         */
        fun parse(input: String): ChannelId {
            return tryParse(input)
                ?: throw IllegalArgumentException("Invalid YouTube channel ID or URL: $input")
        }

        /**
         * Attempts to parse a channel ID from a raw ID string or YouTube URL.
         *
         * @param input Raw channel ID or YouTube URL
         * @return ChannelId instance if valid, null otherwise
         */
        fun tryParse(input: String): ChannelId? =
            tryNormalize(input)?.let { ChannelId(it) }

        /**
         * Checks if the input is a valid channel ID or YouTube URL.
         */
        fun isValid(input: String): Boolean = tryParse(input) != null
    }
}

/**
 * Represents a YouTube user name (legacy format).
 *
 * User names are at most 20 characters and alphanumeric only.
 */
@JvmInline
value class UserName(val value: String) {

    override fun toString(): String = value

    /**
     * The full URL to this user's channel on YouTube.
     */
    val url: String get() = "https://www.youtube.com/user/$value"

    companion object {
        private const val MAX_INPUT_LENGTH = 2048

        private fun isValidName(name: String): Boolean =
            name.length <= 20 && name.isNotEmpty() && name.all { it.isLetterOrDigit() }

        private fun urlDecode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

        private fun tryNormalize(nameOrUrl: String?): String? {
            if (nameOrUrl.isNullOrBlank()) return null
            if (nameOrUrl.length > MAX_INPUT_LENGTH) return null

            val input = nameOrUrl.trim()

            if (isValidName(input)) return input

            // https://www.youtube.com/user/username
            Regex("""youtube\..+?/user/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidName(it) }
                ?.let { return it }

            return null
        }

        fun parse(input: String): UserName {
            return tryParse(input)
                ?: throw IllegalArgumentException("Invalid YouTube user name or URL: $input")
        }

        fun tryParse(input: String): UserName? =
            tryNormalize(input)?.let { UserName(it) }

        fun isValid(input: String): Boolean = tryParse(input) != null
    }
}

/**
 * Represents a YouTube channel slug (custom URL format).
 *
 * Channel slugs can contain alphanumeric characters, hyphens, underscores, and dots.
 */
@JvmInline
value class ChannelSlug(val value: String) {

    override fun toString(): String = value

    /**
     * The full URL to this channel on YouTube.
     */
    val url: String get() = "https://www.youtube.com/c/$value"

    companion object {
        private const val MAX_INPUT_LENGTH = 2048

        private fun isValidSlug(slug: String): Boolean =
            slug.isNotEmpty() && slug.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

        private fun urlDecode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

        private fun tryNormalize(slugOrUrl: String?): String? {
            if (slugOrUrl.isNullOrBlank()) return null
            if (slugOrUrl.length > MAX_INPUT_LENGTH) return null

            val input = slugOrUrl.trim()

            if (isValidSlug(input)) return input

            // https://www.youtube.com/c/channelslug
            Regex("""youtube\..+?/c/(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidSlug(it) }
                ?.let { return it }

            return null
        }

        fun parse(input: String): ChannelSlug {
            return tryParse(input)
                ?: throw IllegalArgumentException("Invalid YouTube channel slug or URL: $input")
        }

        fun tryParse(input: String): ChannelSlug? =
            tryNormalize(input)?.let { ChannelSlug(it) }

        fun isValid(input: String): Boolean = tryParse(input) != null
    }
}

/**
 * Represents a YouTube channel handle (@handle format).
 *
 * Channel handles can contain alphanumeric characters, underscores, hyphens, and periods.
 */
@JvmInline
value class ChannelHandle(val value: String) {

    override fun toString(): String = value

    /**
     * The full URL to this channel on YouTube.
     */
    val url: String get() = "https://www.youtube.com/@$value"

    companion object {
        private const val MAX_INPUT_LENGTH = 2048

        private fun isValidHandle(handle: String): Boolean =
            handle.isNotEmpty() && handle.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }

        private fun urlDecode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }

        private fun tryNormalize(handleOrUrl: String?): String? {
            if (handleOrUrl.isNullOrBlank()) return null
            if (handleOrUrl.length > MAX_INPUT_LENGTH) return null

            val input = handleOrUrl.trim()

            // Remove @ prefix if present
            val withoutAt = if (input.startsWith("@")) input.substring(1) else input

            if (isValidHandle(withoutAt)) return withoutAt

            // https://www.youtube.com/@handle
            Regex("""youtube\..+?/@(.*?)(?:\?|&|/|$)""")
                .find(input)
                ?.groupValues?.get(1)
                ?.let { urlDecode(it) }
                ?.takeIf { isValidHandle(it) }
                ?.let { return it }

            return null
        }

        fun parse(input: String): ChannelHandle {
            return tryParse(input)
                ?: throw IllegalArgumentException("Invalid YouTube channel handle or URL: $input")
        }

        fun tryParse(input: String): ChannelHandle? =
            tryNormalize(input)?.let { ChannelHandle(it) }

        fun isValid(input: String): Boolean = tryParse(input) != null
    }
}
