package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.common.Resolution

/**
 * Represents video quality information.
 */
data class VideoQuality(
    /**
     * Quality label (e.g., "1080p", "720p60", "4K").
     */
    val label: String,

    /**
     * Maximum video height in pixels.
     */
    val maxHeight: Int,

    /**
     * Frames per second.
     */
    val framerate: Int
) : Comparable<VideoQuality> {

    /**
     * Returns true if this is high definition (1080p or higher).
     */
    val isHighDefinition: Boolean get() = maxHeight >= 1080

    override fun compareTo(other: VideoQuality): Int {
        val heightComparison = maxHeight.compareTo(other.maxHeight)
        return if (heightComparison != 0) heightComparison else framerate.compareTo(other.framerate)
    }

    override fun toString(): String = label

    /**
     * Gets the default resolution for this video quality.
     */
    fun getDefaultResolution(): Resolution = when (maxHeight) {
        144 -> Resolution(256, 144)
        240 -> Resolution(426, 240)
        360 -> Resolution(640, 360)
        480 -> Resolution(854, 480)
        720 -> Resolution(1280, 720)
        1080 -> Resolution(1920, 1080)
        1440 -> Resolution(2560, 1440)
        2160 -> Resolution(3840, 2160)
        4320 -> Resolution(7680, 4320)
        else -> Resolution((maxHeight * 16) / 9, maxHeight)
    }

    companion object {
        /**
         * Parses a quality label like "1080p60" into a VideoQuality object.
         */
        fun fromLabel(label: String, framerateFallback: Int = 30): VideoQuality {
            val match = Regex("""^(\d+)\D?(\d+)?""").find(label)
            val height = match?.groupValues?.get(1)?.toIntOrNull() ?: 360
            val framerate = match?.groupValues?.get(2)?.toIntOrNull() ?: framerateFallback
            return VideoQuality(label, height, framerate)
        }

        /**
         * Maps an itag to a VideoQuality based on known YouTube itag mappings.
         */
        fun fromItag(itag: Int, framerate: Int = 30): VideoQuality {
            val height = ITAG_TO_HEIGHT[itag] ?: 360
            val label = if (framerate > 30) "${height}p$framerate" else "${height}p"
            return VideoQuality(label, height, framerate)
        }

        /**
         * Mapping of known itags to video heights.
         */
        private val ITAG_TO_HEIGHT = mapOf(
            // Legacy itags
            5 to 144,
            6 to 240,
            17 to 144,
            18 to 360,
            22 to 720,
            34 to 360,
            35 to 480,
            36 to 240,
            37 to 1080,
            38 to 3072,
            43 to 360,
            44 to 480,
            45 to 720,
            46 to 1080,
            59 to 480,
            78 to 480,
            82 to 360,
            83 to 480,
            84 to 720,
            85 to 1080,
            91 to 144,
            92 to 240,
            93 to 360,
            94 to 480,
            95 to 720,
            96 to 1080,
            100 to 360,
            101 to 480,
            102 to 720,
            132 to 240,
            151 to 144,
            // DASH video
            133 to 240,
            134 to 360,
            135 to 480,
            136 to 720,
            137 to 1080,
            138 to 2160,
            160 to 144,
            167 to 360,
            168 to 480,
            169 to 720,
            170 to 1080,
            212 to 480,
            213 to 480,
            214 to 720,
            215 to 720,
            216 to 1080,
            217 to 1080,
            218 to 480,
            219 to 480,
            242 to 240,
            243 to 360,
            244 to 480,
            245 to 480,
            246 to 480,
            247 to 720,
            248 to 1080,
            264 to 1440,
            266 to 2160,
            271 to 1440,
            272 to 2160,
            278 to 144,
            298 to 720,
            299 to 1080,
            302 to 720,
            303 to 1080,
            308 to 1440,
            313 to 2160,
            315 to 2160,
            330 to 144,
            331 to 240,
            332 to 360,
            333 to 480,
            334 to 720,
            335 to 1080,
            336 to 1440,
            337 to 2160,
            394 to 144,
            395 to 240,
            396 to 360,
            397 to 480,
            398 to 720,
            399 to 1080,
            400 to 1440,
            401 to 2160,
            402 to 4320,
            571 to 4320,
            694 to 144,
            695 to 240,
            696 to 360,
            697 to 480,
            698 to 720,
            699 to 1080,
            700 to 1440,
            701 to 2160,
            702 to 4320
        )
    }
}
