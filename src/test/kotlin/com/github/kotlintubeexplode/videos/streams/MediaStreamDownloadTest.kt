package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.internal.HttpController
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.InputStream

@DisplayName("MediaStream download byte accounting")
class MediaStreamDownloadTest {

    @Nested
    @DisplayName("segment boundary byte count")
    inner class SegmentBoundaryByteCountTests {

        // Reproduces KNOWN_DRIFT #17. A throttled stream is split into 9,898,989-byte
        // segments. Java's InputStream.read returns -1 at EOF (unlike .NET's 0); the port
        // folded that -1 into `position`, rewinding one byte per segment boundary and
        // re-reading it, so the drained stream was (segments - 1) bytes too long.
        // Upstream C# is byte-exact.

        private fun fixedByteStream(count: Long): InputStream = object : InputStream() {
            private var remaining = count
            override fun read(): Int = if (remaining > 0) { remaining--; 0 } else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val n = minOf(len.toLong(), remaining).toInt()
                remaining -= n
                return n
            }
        }

        private suspend fun drain(stream: MediaStream): Long {
            var total = 0L
            val buffer = ByteArray(81920)
            while (true) {
                val read = stream.readAsync(buffer, 0, buffer.size)
                if (read == -1) break
                total += read
            }
            return total
        }

        @Test
        fun `throttled stream spanning three segments reads exactly size bytes`() = runTest {
            // 25 MB throttled stream => ceil(25_000_000 / 9_898_989) = 3 segments,
            // 2 boundaries. Buggy code re-reads 1 byte per boundary => 25_000_002.
            val totalSize = 25_000_000L
            val http = mockk<HttpController>()
            coEvery { http.getStream(any(), any()) } answers {
                val requestUrl = firstArg<String>()
                val m = Regex("""range=(\d+)-(\d+)""").find(requestUrl)
                    ?: error("segment URL missing range param: $requestUrl")
                val from = m.groupValues[1].toLong()
                val to = m.groupValues[2].toLong()
                fixedByteStream(to - from + 1)
            }

            // URL without ratebypass=yes => IStreamInfo.isThrottled == true => segmented.
            val streamInfo = AudioOnlyStreamInfo(
                url = "https://r1---sn-foo.googlevideo.com/videoplayback?expire=123",
                container = Container.Mp4,
                size = FileSize(totalSize),
                bitrate = Bitrate(128_000),
                audioCodec = "mp4a.40.2"
            )

            val mediaStream = MediaStream(http, streamInfo)
            val bytesRead = drain(mediaStream)

            bytesRead shouldBe totalSize
        }
    }
}
