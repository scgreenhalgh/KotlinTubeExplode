package com.github.kotlintubeexplode.integration

import com.github.kotlintubeexplode.client.YoutubeClient
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests that verify the library works against real YouTube.
 *
 * These tests make actual network requests to YouTube and should be run
 * sparingly to avoid rate limiting.
 *
 * Run with: ./gradlew test --tests "*IntegrationTest*"
 */
@Tag("integration")
@DisplayName("YouTube Client Integration Tests")
class YoutubeClientIntegrationTest {

    private val client = YoutubeClient()

    // Test video: Rick Astley - Never Gonna Give You Up (stable, long-lived)
    private val testVideoId = "dQw4w9WgXcQ"

    @Nested
    @DisplayName("Stream Manifest (Multi-Client)")
    inner class StreamManifestTests {

        @Test
        fun `should fetch stream manifest for standard video`() = runTest(timeout = 60.seconds) {
            println("Testing stream manifest for video: $testVideoId")

            val manifest = client.streams.getManifest(testVideoId)

            println("=== Stream Manifest Results ===")
            println("Total streams: ${manifest.streams.size}")

            val audioStreams = manifest.getAudioOnlyStreams()
            val videoStreams = manifest.getVideoOnlyStreams()
            val muxedStreams = manifest.getMuxedStreams()

            println("Audio-only streams: ${audioStreams.size}")
            println("Video-only streams: ${videoStreams.size}")
            println("Muxed streams: ${muxedStreams.size}")

            // Print some stream details
            audioStreams.take(3).forEach { stream ->
                println("  Audio: ${stream.container} | ${stream.bitrate} | ${stream.audioCodec}")
            }
            videoStreams.take(3).forEach { stream ->
                println("  Video: ${stream.container} | ${stream.videoQuality.label} | ${stream.videoCodec}")
            }
            muxedStreams.take(3).forEach { stream ->
                println("  Muxed: ${stream.container} | ${stream.videoQuality.label}")
            }

            // Assertions
            manifest.streams.shouldNotBeEmpty()
            println("\n✅ Stream manifest fetched successfully!")
        }

        @Test
        fun `should have audio streams available`() = runTest(timeout = 60.seconds) {
            val manifest = client.streams.getManifest(testVideoId)

            val audioStreams = manifest.getAudioOnlyStreams()
            audioStreams.shouldNotBeEmpty()

            // Best audio stream should exist
            val bestAudio = manifest.getBestAudioStream()
            bestAudio shouldNotBe null
            println("Best audio: ${bestAudio?.bitrate} | ${bestAudio?.audioCodec}")
        }

        @Test
        fun `should have video streams available`() = runTest(timeout = 60.seconds) {
            val manifest = client.streams.getManifest(testVideoId)

            val videoStreams = manifest.getVideoOnlyStreams()
            videoStreams.shouldNotBeEmpty()

            // Best video stream should exist
            val bestVideo = manifest.getBestVideoStream()
            bestVideo shouldNotBe null
            println("Best video: ${bestVideo?.videoQuality?.label} | ${bestVideo?.videoCodec}")
        }

        @Test
        fun `stream URLs should be valid HTTPS URLs`() = runTest(timeout = 60.seconds) {
            val manifest = client.streams.getManifest(testVideoId)

            manifest.streams.forEach { stream ->
                stream.url shouldContain "https://"
                stream.url shouldContain "googlevideo.com"
            }
            println("✅ All ${manifest.streams.size} stream URLs are valid")
        }
    }

    @Nested
    @DisplayName("Video Metadata")
    inner class VideoMetadataTests {

        @Test
        fun `should fetch video metadata`() = runTest(timeout = 60.seconds) {
            println("Testing video metadata for: $testVideoId")

            val video = client.videos.get(testVideoId)

            println("=== Video Metadata ===")
            println("Title: ${video.title}")
            println("Author: ${video.author.channelTitle}")
            println("Duration: ${video.duration}")
            println("Thumbnails: ${video.thumbnails.size}")

            // Assertions
            video.title.shouldNotBeBlank()
            video.title shouldContain "Rick" // Should contain "Rick" in the title
            video.author.channelTitle.shouldNotBeBlank()
            video.duration shouldNotBe null
            video.thumbnails.shouldNotBeEmpty()

            println("\n✅ Video metadata fetched successfully!")
        }

        @Test
        fun `should fetch video by full URL`() = runTest(timeout = 60.seconds) {
            val video = client.videos.get("https://www.youtube.com/watch?v=$testVideoId")

            video.id.value shouldBe testVideoId
            video.title.shouldNotBeBlank()
        }

        @Test
        fun `should fetch video by short URL`() = runTest(timeout = 60.seconds) {
            val video = client.videos.get("https://youtu.be/$testVideoId")

            video.id.value shouldBe testVideoId
            video.title.shouldNotBeBlank()
        }
    }

    @Nested
    @DisplayName("Different Video Types")
    inner class VideoTypeTests {

        @Test
        fun `should fetch high-view-count video`() = runTest(timeout = 60.seconds) {
            // Gangnam Style - one of the most viewed videos
            val manifest = client.streams.getManifest("9bZkp7q19f0")

            manifest.streams.shouldNotBeEmpty()
            println("✅ High-view video: ${manifest.streams.size} streams")
        }

        @Test
        fun `should fetch first YouTube video`() = runTest(timeout = 60.seconds) {
            // "Me at the zoo" - first YouTube video ever
            val manifest = client.streams.getManifest("jNQXAC9IVRw")

            manifest.streams.shouldNotBeEmpty()
            println("✅ First YT video: ${manifest.streams.size} streams")
        }
    }
}
