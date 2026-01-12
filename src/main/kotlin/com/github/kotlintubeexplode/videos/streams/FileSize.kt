package com.github.kotlintubeexplode.videos.streams

/**
 * Represents a file size value.
 */
@JvmInline
value class FileSize(val bytes: Long) : Comparable<FileSize> {

    /**
     * Size in kilobytes.
     */
    val kiloBytes: Double get() = bytes / 1024.0

    /**
     * Size in megabytes.
     */
    val megaBytes: Double get() = kiloBytes / 1024.0

    /**
     * Size in gigabytes.
     */
    val gigaBytes: Double get() = megaBytes / 1024.0

    override fun compareTo(other: FileSize): Int = bytes.compareTo(other.bytes)

    override fun toString(): String = when {
        gigaBytes >= 1 -> "%.2f GB".format(gigaBytes)
        megaBytes >= 1 -> "%.2f MB".format(megaBytes)
        kiloBytes >= 1 -> "%.2f KB".format(kiloBytes)
        else -> "$bytes B"
    }

    companion object {
        val Zero = FileSize(0)
    }
}
