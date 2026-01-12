package com.github.kotlintubeexplode.common

/**
 * Represents a language with its code and display name.
 *
 * Used for audio tracks and closed captions.
 *
 * @param code The language code (e.g., "en", "en-US", "eng")
 * @param name The full language name (e.g., "English", "English (United States)")
 */
data class Language(
    val code: String,
    val name: String
) {
    override fun toString(): String = "$name ($code)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Language) return false
        return code.equals(other.code, ignoreCase = true)
    }

    override fun hashCode(): Int = code.lowercase().hashCode()

    companion object {
        /**
         * Creates a Language from a language code, with the code as the default name.
         */
        fun fromCode(code: String): Language = Language(code, code)
    }
}
