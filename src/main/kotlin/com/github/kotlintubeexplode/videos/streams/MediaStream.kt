package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.internal.HttpController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * A stream wrapper that handles YouTube's rate throttling by downloading in segments.
 *
 * YouTube limits transfer speed for most streams to match video playback rate.
 * This class works around that by dividing the stream into segments and
 * downloading them separately with Range headers.
 *
 * @param httpController HTTP controller for making requests
 * @param streamInfo The stream information
 */
internal class MediaStream(
    private val httpController: HttpController,
    private val streamInfo: IStreamInfo
) : InputStream() {

    companion object {
        /**
         * Segment size for throttled streams (~9.9MB).
         * This is the optimal size that YouTube allows without throttling.
         */
        private const val THROTTLED_SEGMENT_SIZE = 9_898_989L

        /**
         * Maximum number of retries for segment downloads.
         */
        private const val MAX_RETRIES = 5

        /**
         * Builds a segment URL with Range query parameter.
         */
        fun getSegmentUrl(streamUrl: String, from: Long, to: Long): String {
            val separator = if (streamUrl.contains("?")) "&" else "?"
            return "${streamUrl}${separator}range=$from-$to"
        }
    }

    private val segmentLength: Long = if (streamInfo.isThrottled) {
        THROTTLED_SEGMENT_SIZE
    } else {
        streamInfo.size.bytes
    }

    private val totalLength: Long = streamInfo.size.bytes

    private var currentSegmentStream: InputStream? = null
    private var position: Long = 0
    private var actualPosition: Long = 0

    /**
     * Resets the current segment, closing any open stream.
     */
    private fun resetSegment() {
        currentSegmentStream?.close()
        currentSegmentStream = null
    }

    /**
     * Resolves and returns the current segment stream.
     * If position changed, fetches a new segment.
     */
    private suspend fun resolveSegment(): InputStream {
        currentSegmentStream?.let { return it }

        val from = position
        val to = minOf(position + segmentLength - 1, totalLength - 1)
        val url = getSegmentUrl(streamInfo.url, from, to)

        val stream = withContext(Dispatchers.IO) {
            httpController.getStreamWithRetry(url, MAX_RETRIES)
        }

        currentSegmentStream = stream
        return stream
    }

    /**
     * Reads from the current segment with retry logic.
     */
    private suspend fun readSegment(buffer: ByteArray, offset: Int, length: Int): Int {
        var retriesRemaining = MAX_RETRIES

        while (true) {
            try {
                val stream = resolveSegment()
                return withContext(Dispatchers.IO) {
                    stream.read(buffer, offset, length)
                }
            } catch (e: IOException) {
                if (retriesRemaining > 0) {
                    retriesRemaining--
                    resetSegment()
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Reads bytes into the buffer, handling segment boundaries.
     *
     * This is the suspend version that should be used from coroutines.
     */
    suspend fun readAsync(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            val requestedPosition = position

            // If position changed since last read, reset segment
            if (actualPosition != requestedPosition) {
                resetSegment()
            }

            // Exit if we reached end of stream
            if (requestedPosition >= totalLength) {
                return -1
            }

            val bytesRead = readSegment(buffer, offset, length)
            position = requestedPosition + bytesRead
            actualPosition = position

            if (bytesRead > 0) {
                return bytesRead
            }

            // End of segment, load next one
            resetSegment()
        }
    }

    /**
     * Seeks to a position in the stream.
     *
     * @param newPosition The position to seek to
     */
    fun seek(newPosition: Long) {
        require(newPosition >= 0) { "Position cannot be negative" }
        require(newPosition <= totalLength) { "Position cannot exceed stream length" }
        position = newPosition
    }

    /**
     * Returns the current position in the stream.
     */
    fun getPosition(): Long = position

    /**
     * Returns the total length of the stream.
     */
    fun getLength(): Long = totalLength

    /**
     * Returns true if the stream is throttled.
     */
    fun isThrottled(): Boolean = streamInfo.isThrottled

    // Standard InputStream methods (blocking versions)

    override fun read(): Int {
        val buffer = ByteArray(1)
        val result = read(buffer, 0, 1)
        return if (result == -1) -1 else buffer[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray): Int = read(b, 0, b.size)

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        // Blocking read - use runBlocking in a coroutine context
        return kotlinx.coroutines.runBlocking {
            readAsync(b, off, len)
        }
    }

    override fun available(): Int {
        return (totalLength - position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun close() {
        resetSegment()
    }

    override fun skip(n: Long): Long {
        val oldPosition = position
        val newPosition = minOf(position + n, totalLength)
        seek(newPosition)
        return newPosition - oldPosition
    }

    override fun markSupported(): Boolean = false
}

/**
 * Extension function for HttpController to get a stream with retry.
 */
internal suspend fun HttpController.getStreamWithRetry(
    url: String,
    maxRetries: Int
): InputStream {
    var lastException: Exception? = null

    repeat(maxRetries) { attempt ->
        try {
            return getStream(url)
        } catch (e: IOException) {
            lastException = e
            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(100L * (1 shl attempt))
            }
        }
    }

    throw lastException ?: IOException("Failed after $maxRetries retries")
}
