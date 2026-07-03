package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.internal.HttpController
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MediaStream.seek")
class MediaStreamSeekTest {

    @Nested
    @DisplayName("seeking past the end of a short stream")
    inner class SeekPastLengthTests {

        @Test
        fun `seek past the end of a sub-1KB init-segment stream should not throw`() {
            // Upstream YoutubeExplode's MediaStream.Seek (6.6 and prime) has no upper-bound
            // guard: seeking past Length just sets Position, and reads past the end return
            // EOF. A normal video's smallest audio stream can be <1000 bytes
            // (init-segment-style), so seek(1000) must not throw.
            //
            // HttpController is never touched on the offline seek/EOF path (seek() only sets
            // position; readAsync returns -1 before any network once position >= totalLength),
            // so an unstubbed mock is sufficient and this test makes no network calls.
            val http = mockk<HttpController>()
            val streamInfo = AudioOnlyStreamInfo(
                url = "https://example.googlevideo.com/videoplayback?itag=140",
                container = Container.Mp4,
                size = FileSize(800),
                bitrate = Bitrate(0),
                audioCodec = "mp4a.40.2"
            )
            val stream = MediaStream(http, streamInfo)

            // Currently FAILS: throws IllegalArgumentException("Position cannot exceed stream length").
            shouldNotThrowAny { stream.seek(1000) }
            stream.getPosition() shouldBe 1000L

            // Reading past the end returns EOF (-1) with no network call, mirroring upstream
            // whose ReadAsync returns 0 once Position >= Length.
            stream.read() shouldBe -1
        }
    }
}
