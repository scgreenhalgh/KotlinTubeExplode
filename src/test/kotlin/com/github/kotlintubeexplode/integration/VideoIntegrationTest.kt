package com.github.kotlintubeexplode.integration

import com.github.kotlintubeexplode.client.YoutubeClient
import com.github.kotlintubeexplode.exceptions.VideoUnplayableException
import com.github.kotlintubeexplode.testdata.VideoIds
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for video metadata against live YouTube.
 *
 * Test cases derived from upstream's VideoSpecs.cs.
 */
@Tag("integration")
@DisplayName("Video Integration Tests")
class VideoIntegrationTest {

    private val client = YoutubeClient()

    @Nested
    @DisplayName("Any available video")
    inner class AnyAvailableVideoTests {
        // Mirror of upstream's I_can_get_the_metadata_of_any_available_video.

        @ParameterizedTest(name = "should fetch metadata for {0}")
        @ValueSource(strings = [
            VideoIds.Normal,
            VideoIds.Unlisted,
            VideoIds.RequiresPurchaseDistributed,
            VideoIds.EmbedRestrictedByYouTube,
            VideoIds.EmbedRestrictedByAuthor,
            VideoIds.ContentCheckViolent,
            VideoIds.WithBrokenTitle,
        ])
        fun `should fetch metadata for available video`(videoId: String) = runTest(timeout = 60.seconds) {
            val video = client.videos.get(videoId)

            // Title can legitimately be empty for some videos (broken-title case).
            video.id.value shouldBe videoId
            video.author.channelTitle.shouldNotBeBlank()
            video.thumbnails.shouldNotBeEmpty()
        }
    }

    @Nested
    @DisplayName("Highest resolution thumbnail")
    inner class ThumbnailTests {
        // Mirror of upstream's I_can_get_the_highest_resolution_thumbnail_from_a_video.

        @Test
        fun `should expose at least one thumbnail with non-zero resolution`() = runTest(timeout = 60.seconds) {
            val video = client.videos.get(VideoIds.Normal)
            val thumbnails = video.thumbnails

            thumbnails.shouldNotBeEmpty()
            val highest = thumbnails.maxByOrNull { it.resolution.width * it.resolution.height }!!

            assert(highest.resolution.width > 0) { "Expected non-zero width, got ${highest.resolution.width}" }
            assert(highest.resolution.height > 0) { "Expected non-zero height, got ${highest.resolution.height}" }
        }
    }

    @Nested
    @DisplayName("Video metadata errors")
    inner class VideoErrorTests {
        // Mirror of upstream's I_can_try_to_get_the_metadata_of_a_video_and_get_an_error_*.

        @Test
        fun `should throw for private video metadata`() = runTest(timeout = 60.seconds) {
            shouldThrow<VideoUnplayableException> {
                client.videos.get(VideoIds.Private)
            }
        }

        @Test
        fun `should throw for non-existing video metadata`() = runTest(timeout = 60.seconds) {
            shouldThrow<VideoUnplayableException> {
                client.videos.get("xxxxxxxxxxx")
            }
        }
    }
}
