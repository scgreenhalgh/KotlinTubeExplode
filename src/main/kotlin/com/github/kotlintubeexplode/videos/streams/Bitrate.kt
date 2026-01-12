package com.github.kotlintubeexplode.videos.streams

/**
 * Represents a bitrate value.
 */
@JvmInline
value class Bitrate(val bitsPerSecond: Long) : Comparable<Bitrate> {

    /**
     * Bitrate in kilobits per second.
     */
    val kiloBitsPerSecond: Double get() = bitsPerSecond / 1024.0

    /**
     * Bitrate in megabits per second.
     */
    val megaBitsPerSecond: Double get() = kiloBitsPerSecond / 1024.0

    /**
     * Bitrate in gigabits per second.
     */
    val gigaBitsPerSecond: Double get() = megaBitsPerSecond / 1024.0

    override fun compareTo(other: Bitrate): Int = bitsPerSecond.compareTo(other.bitsPerSecond)

    override fun toString(): String = when {
        gigaBitsPerSecond >= 1 -> "%.2f Gbit/s".format(gigaBitsPerSecond)
        megaBitsPerSecond >= 1 -> "%.2f Mbit/s".format(megaBitsPerSecond)
        kiloBitsPerSecond >= 1 -> "%.2f Kbit/s".format(kiloBitsPerSecond)
        else -> "$bitsPerSecond Bit/s"
    }

    companion object {
        val Zero = Bitrate(0)
    }
}
