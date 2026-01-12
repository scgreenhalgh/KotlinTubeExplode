package com.github.kotlintubeexplode.common

/**
 * Represents image/video resolution.
 */
data class Resolution(
    val width: Int,
    val height: Int
) : Comparable<Resolution> {
    /**
     * The total pixel area.
     */
    val area: Int
        get() = width * height

    override fun toString(): String = "${width}x${height}"

    override fun compareTo(other: Resolution): Int = area.compareTo(other.area)

    companion object {
        /**
         * Common video resolutions.
         */
        val SD_480 = Resolution(854, 480)
        val HD_720 = Resolution(1280, 720)
        val HD_1080 = Resolution(1920, 1080)
        val QHD_1440 = Resolution(2560, 1440)
        val UHD_4K = Resolution(3840, 2160)
        val UHD_8K = Resolution(7680, 4320)
    }
}
