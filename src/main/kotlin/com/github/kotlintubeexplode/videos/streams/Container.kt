package com.github.kotlintubeexplode.videos.streams

/**
 * Represents a media container format.
 */
@JvmInline
value class Container(val name: String) {

    /**
     * Returns true if this container is audio-only.
     */
    val isAudioOnly: Boolean
        get() = name.lowercase() in AUDIO_ONLY_CONTAINERS

    override fun toString(): String = name

    companion object {
        private val AUDIO_ONLY_CONTAINERS = setOf(
            "mp3", "m4a", "wav", "wma", "ogg", "aac", "opus"
        )

        val Mp3 = Container("mp3")
        val Mp4 = Container("mp4")
        val WebM = Container("webm")
        val Tgpp = Container("3gpp")
    }
}
