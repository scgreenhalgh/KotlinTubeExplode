package com.github.kotlintubeexplode.videos.streams

import com.github.kotlintubeexplode.common.Resolution
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Streams module")
class StreamsTest {

    @Nested
    @DisplayName("Container")
    inner class ContainerTests {

        @Test
        fun `should have correct name for Mp4`() {
            Container.Mp4.name shouldBe "mp4"
        }

        @Test
        fun `should have correct name for WebM`() {
            Container.WebM.name shouldBe "webm"
        }

        @Test
        fun `should have correct name for Tgpp`() {
            Container.Tgpp.name shouldBe "3gpp"
        }

        @Test
        fun `should have correct name for Mp3`() {
            Container.Mp3.name shouldBe "mp3"
        }

        @Test
        fun `should create custom container`() {
            val container = Container("mkv")
            container.name shouldBe "mkv"
        }

        @Test
        fun `should detect audio-only containers`() {
            Container.Mp3.isAudioOnly shouldBe true
            Container("opus").isAudioOnly shouldBe true
            Container("m4a").isAudioOnly shouldBe true
        }

        @Test
        fun `should not be audio-only for video containers`() {
            Container.Mp4.isAudioOnly shouldBe false
            Container.WebM.isAudioOnly shouldBe false
        }

        @Test
        fun `should have string representation`() {
            Container.Mp4.toString() shouldBe "mp4"
        }
    }

    @Nested
    @DisplayName("Bitrate")
    inner class BitrateTests {

        @Test
        fun `should calculate kilo bits per second`() {
            // Uses binary: 1024
            val bitrate = Bitrate(1024)
            bitrate.kiloBitsPerSecond shouldBe 1.0
        }

        @Test
        fun `should calculate mega bits per second`() {
            // Uses binary: 1024 * 1024
            val bitrate = Bitrate(1024L * 1024)
            bitrate.megaBitsPerSecond shouldBe 1.0
        }

        @Test
        fun `should format to string with Kbit per second`() {
            val bitrate = Bitrate(128_000)
            // 128000 / 1024 = 125.0
            bitrate.toString() shouldBe "125.00 Kbit/s"
        }

        @Test
        fun `should format to string with Mbit for high bitrates`() {
            val bitrate = Bitrate(5_000_000)
            // 5000000 / 1024 / 1024 ≈ 4.77
            bitrate.toString() shouldBe "4.77 Mbit/s"
        }

        @Test
        fun `should compare bitrates`() {
            val low = Bitrate(128_000)
            val high = Bitrate(320_000)

            (high > low) shouldBe true
            (low < high) shouldBe true
        }
    }

    @Nested
    @DisplayName("FileSize")
    inner class FileSizeTests {

        @Test
        fun `should calculate kilo bytes`() {
            val size = FileSize(1024)
            size.kiloBytes shouldBe 1.0
        }

        @Test
        fun `should calculate mega bytes`() {
            val size = FileSize(1024 * 1024)
            size.megaBytes shouldBe 1.0
        }

        @Test
        fun `should calculate giga bytes`() {
            val size = FileSize(1024L * 1024 * 1024)
            size.gigaBytes shouldBe 1.0
        }

        @Test
        fun `should format to string with appropriate unit`() {
            FileSize(512).toString() shouldBe "512 B"
            FileSize(1024).toString() shouldBe "1.00 KB"
            FileSize(1024 * 1024).toString() shouldBe "1.00 MB"
            FileSize(1024L * 1024 * 1024).toString() shouldBe "1.00 GB"
        }

        @Test
        fun `should compare file sizes`() {
            val small = FileSize(1024)
            val large = FileSize(2048)

            (large > small) shouldBe true
            (small < large) shouldBe true
        }
    }

    @Nested
    @DisplayName("VideoQuality")
    inner class VideoQualityTests {

        @Test
        fun `should parse quality from label`() {
            val quality = VideoQuality.fromLabel("1080p", 30)
            quality.maxHeight shouldBe 1080
            quality.framerate shouldBe 30
            quality.label shouldBe "1080p"
        }

        @Test
        fun `should parse high framerate quality from label`() {
            val quality = VideoQuality.fromLabel("1080p60", 60)
            quality.maxHeight shouldBe 1080
            quality.framerate shouldBe 60
        }

        @Test
        fun `should parse 4K quality from label`() {
            val quality = VideoQuality.fromLabel("2160p", 30)
            quality.maxHeight shouldBe 2160
        }

        @Test
        fun `should map itag 22 to 720p`() {
            val quality = VideoQuality.fromItag(22, 30)
            quality.maxHeight shouldBe 720
        }

        @Test
        fun `should map itag 137 to 1080p`() {
            val quality = VideoQuality.fromItag(137, 30)
            quality.maxHeight shouldBe 1080
        }

        @Test
        fun `should map itag 313 to 2160p`() {
            val quality = VideoQuality.fromItag(313, 30)
            quality.maxHeight shouldBe 2160
        }

        @Test
        fun `should return 360p for unknown itag`() {
            val quality = VideoQuality.fromItag(99999, 30)
            quality.maxHeight shouldBe 360
        }

        @Test
        fun `should order qualities correctly`() {
            val q1080 = VideoQuality("1080p", 1080, 30)
            val q720 = VideoQuality("720p", 720, 30)
            val q480 = VideoQuality("480p", 480, 30)
            val q2160 = VideoQuality("2160p", 2160, 30)
            val q4320 = VideoQuality("4320p", 4320, 30)

            (q1080 > q720) shouldBe true
            (q720 > q480) shouldBe true
            (q4320 > q2160) shouldBe true
        }

        @Test
        fun `should have correct height for qualities`() {
            val q1080 = VideoQuality.fromLabel("1080p", 30)
            val q720 = VideoQuality.fromLabel("720p", 30)
            val q2160 = VideoQuality.fromLabel("2160p", 30)

            q1080.maxHeight shouldBe 1080
            q720.maxHeight shouldBe 720
            q2160.maxHeight shouldBe 2160
        }

        @Test
        fun `should detect high definition`() {
            val q1080 = VideoQuality("1080p", 1080, 30)
            val q720 = VideoQuality("720p", 720, 30)
            val q2160 = VideoQuality("4K", 2160, 30)

            q1080.isHighDefinition shouldBe true
            q720.isHighDefinition shouldBe false
            q2160.isHighDefinition shouldBe true
        }

        @Test
        fun `should get default resolution`() {
            val q1080 = VideoQuality("1080p", 1080, 30)
            val resolution = q1080.getDefaultResolution()

            resolution.width shouldBe 1920
            resolution.height shouldBe 1080
        }
    }

    @Nested
    @DisplayName("StreamInfo types")
    inner class StreamInfoTests {

        @Test
        fun `AudioOnlyStreamInfo should implement IAudioStreamInfo`() {
            val stream = AudioOnlyStreamInfo(
                url = "https://example.com/audio.mp4",
                container = Container.Mp4,
                size = FileSize(1000),
                bitrate = Bitrate(128000),
                audioCodec = "mp4a.40.2"
            )

            stream.shouldBeInstanceOf<IAudioStreamInfo>()
            stream.audioCodec shouldBe "mp4a.40.2"
        }

        @Test
        fun `VideoOnlyStreamInfo should implement IVideoStreamInfo`() {
            val stream = VideoOnlyStreamInfo(
                url = "https://example.com/video.mp4",
                container = Container.Mp4,
                size = FileSize(10000),
                bitrate = Bitrate(2500000),
                videoCodec = "avc1.4d401f",
                videoQuality = VideoQuality("1080p", 1080, 30),
                videoResolution = Resolution(1920, 1080)
            )

            stream.shouldBeInstanceOf<IVideoStreamInfo>()
            stream.videoCodec shouldBe "avc1.4d401f"
            stream.videoQuality.maxHeight shouldBe 1080
            stream.videoResolution.width shouldBe 1920
        }

        @Test
        fun `MuxedStreamInfo should implement both interfaces`() {
            val stream = MuxedStreamInfo(
                url = "https://example.com/muxed.mp4",
                container = Container.Mp4,
                size = FileSize(50000),
                bitrate = Bitrate(3000000),
                audioCodec = "mp4a.40.2",
                videoCodec = "avc1.4d401f",
                videoQuality = VideoQuality("720p", 720, 30),
                videoResolution = Resolution(1280, 720)
            )

            stream.shouldBeInstanceOf<IAudioStreamInfo>()
            stream.shouldBeInstanceOf<IVideoStreamInfo>()
            stream.audioCodec shouldBe "mp4a.40.2"
            stream.videoCodec shouldBe "avc1.4d401f"
        }
    }

    @Nested
    @DisplayName("StreamManifest")
    inner class StreamManifestTests {

        private val testStreams = listOf(
            AudioOnlyStreamInfo(
                url = "https://example.com/audio1.mp4",
                container = Container.Mp4,
                size = FileSize(1000),
                bitrate = Bitrate(128000),
                audioCodec = "mp4a.40.2"
            ),
            AudioOnlyStreamInfo(
                url = "https://example.com/audio2.mp4",
                container = Container.Mp4,
                size = FileSize(2000),
                bitrate = Bitrate(256000),
                audioCodec = "mp4a.40.2"
            ),
            VideoOnlyStreamInfo(
                url = "https://example.com/video1.mp4",
                container = Container.Mp4,
                size = FileSize(10000),
                bitrate = Bitrate(2500000),
                videoCodec = "avc1.4d401f",
                videoQuality = VideoQuality("720p", 720, 30),
                videoResolution = Resolution(1280, 720)
            ),
            VideoOnlyStreamInfo(
                url = "https://example.com/video2.mp4",
                container = Container.Mp4,
                size = FileSize(20000),
                bitrate = Bitrate(5000000),
                videoCodec = "avc1.4d401f",
                videoQuality = VideoQuality("1080p", 1080, 30),
                videoResolution = Resolution(1920, 1080)
            ),
            MuxedStreamInfo(
                url = "https://example.com/muxed.mp4",
                container = Container.Mp4,
                size = FileSize(50000),
                bitrate = Bitrate(3000000),
                audioCodec = "mp4a.40.2",
                videoCodec = "avc1.4d401f",
                videoQuality = VideoQuality("480p", 480, 30),
                videoResolution = Resolution(854, 480)
            )
        )

        @Test
        fun `should get all audio streams`() {
            val manifest = StreamManifest(testStreams)
            val audioStreams = manifest.getAudioStreams()

            audioStreams.size shouldBe 3 // 2 audio-only + 1 muxed
        }

        @Test
        fun `should get all video streams`() {
            val manifest = StreamManifest(testStreams)
            val videoStreams = manifest.getVideoStreams()

            videoStreams.size shouldBe 3 // 2 video-only + 1 muxed
        }

        @Test
        fun `should get audio-only streams`() {
            val manifest = StreamManifest(testStreams)
            val audioOnlyStreams = manifest.getAudioOnlyStreams()

            audioOnlyStreams.size shouldBe 2
        }

        @Test
        fun `should get video-only streams`() {
            val manifest = StreamManifest(testStreams)
            val videoOnlyStreams = manifest.getVideoOnlyStreams()

            videoOnlyStreams.size shouldBe 2
        }

        @Test
        fun `should get muxed streams`() {
            val manifest = StreamManifest(testStreams)
            val muxedStreams = manifest.getMuxedStreams()

            muxedStreams.size shouldBe 1
        }

        @Test
        fun `should get best audio stream by bitrate`() {
            val manifest = StreamManifest(testStreams)
            val bestAudio = manifest.getBestAudioStream()

            // Best audio includes muxed streams, muxed has highest bitrate (3000000)
            bestAudio?.bitrate?.bitsPerSecond shouldBe 3000000L
        }

        @Test
        fun `should get best video stream by quality`() {
            val manifest = StreamManifest(testStreams)
            val bestVideo = manifest.getBestVideoStream()

            bestVideo?.videoQuality?.maxHeight shouldBe 1080
        }

        @Test
        fun `should get best muxed stream`() {
            val manifest = StreamManifest(testStreams)
            val bestMuxed = manifest.getBestMuxedStream()

            bestMuxed?.videoQuality?.maxHeight shouldBe 480
        }
    }
}
