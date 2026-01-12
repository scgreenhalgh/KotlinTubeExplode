package com.github.kotlintubeexplode.exceptions

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Exception hierarchy")
class ExceptionsTest {

    @Nested
    @DisplayName("KotlinTubeExplodeException")
    inner class KotlinTubeExplodeExceptionTests {

        @Test
        fun `should have correct message`() {
            val exception = KotlinTubeExplodeException("Test message")
            exception.message shouldBe "Test message"
        }

        @Test
        fun `should extend Exception`() {
            val exception = KotlinTubeExplodeException("Test")
            exception.shouldBeInstanceOf<Exception>()
        }
    }

    @Nested
    @DisplayName("VideoUnplayableException")
    inner class VideoUnplayableExceptionTests {

        @Test
        fun `should extend KotlinTubeExplodeException`() {
            val exception = VideoUnplayableException("Video unplayable")
            exception.shouldBeInstanceOf<KotlinTubeExplodeException>()
        }

        @Test
        fun `should have correct message`() {
            val exception = VideoUnplayableException("Video is unplayable because of reasons")
            exception.message shouldBe "Video is unplayable because of reasons"
        }
    }

    @Nested
    @DisplayName("VideoUnavailableException")
    inner class VideoUnavailableExceptionTests {

        @Test
        fun `should extend VideoUnplayableException`() {
            val exception = VideoUnavailableException("Video unavailable")
            exception.shouldBeInstanceOf<VideoUnplayableException>()
        }

        @Test
        fun `should also be KotlinTubeExplodeException`() {
            val exception = VideoUnavailableException("Video unavailable")
            exception.shouldBeInstanceOf<KotlinTubeExplodeException>()
        }
    }

    @Nested
    @DisplayName("VideoRequiresPurchaseException")
    inner class VideoRequiresPurchaseExceptionTests {

        @Test
        fun `should extend VideoUnplayableException`() {
            val exception = VideoRequiresPurchaseException("Requires purchase", "previewId123")
            exception.shouldBeInstanceOf<VideoUnplayableException>()
        }

        @Test
        fun `should have preview video ID`() {
            val exception = VideoRequiresPurchaseException("Requires purchase", "previewId123")
            exception.previewVideoId shouldBe "previewId123"
        }

        @Test
        fun `should have correct message`() {
            val exception = VideoRequiresPurchaseException("This video requires purchase", "abc123")
            exception.message shouldBe "This video requires purchase"
        }
    }

    @Nested
    @DisplayName("RequestLimitExceededException")
    inner class RequestLimitExceededExceptionTests {

        @Test
        fun `should extend KotlinTubeExplodeException`() {
            val exception = RequestLimitExceededException("Rate limit exceeded")
            exception.shouldBeInstanceOf<KotlinTubeExplodeException>()
        }
    }

    @Nested
    @DisplayName("PlaylistUnavailableException")
    inner class PlaylistUnavailableExceptionTests {

        @Test
        fun `should extend KotlinTubeExplodeException`() {
            val exception = PlaylistUnavailableException("Playlist not found")
            exception.shouldBeInstanceOf<KotlinTubeExplodeException>()
        }
    }

    @Nested
    @DisplayName("ChannelUnavailableException")
    inner class ChannelUnavailableExceptionTests {

        @Test
        fun `should extend KotlinTubeExplodeException`() {
            val exception = ChannelUnavailableException("Channel not found")
            exception.shouldBeInstanceOf<KotlinTubeExplodeException>()
        }
    }

    @Nested
    @DisplayName("ClosedCaptionTrackUnavailableException")
    inner class ClosedCaptionTrackUnavailableExceptionTests {

        @Test
        fun `should extend KotlinTubeExplodeException`() {
            val exception = ClosedCaptionTrackUnavailableException("Captions not available")
            exception.shouldBeInstanceOf<KotlinTubeExplodeException>()
        }
    }

    @Nested
    @DisplayName("Exception catching")
    inner class ExceptionCatchingTests {

        @Test
        fun `catching KotlinTubeExplodeException should catch all library exceptions`() {
            val exceptions = listOf<Exception>(
                KotlinTubeExplodeException("base"),
                VideoUnplayableException("unplayable"),
                VideoUnavailableException("unavailable"),
                VideoRequiresPurchaseException("purchase", "preview"),
                RequestLimitExceededException("rate limit"),
                PlaylistUnavailableException("playlist"),
                ChannelUnavailableException("channel"),
                ClosedCaptionTrackUnavailableException("captions")
            )

            exceptions.forEach { e ->
                e.shouldBeInstanceOf<KotlinTubeExplodeException>()
            }
        }

        @Test
        fun `catching VideoUnplayableException should catch video-related exceptions`() {
            val videoExceptions = listOf<Exception>(
                VideoUnplayableException("unplayable"),
                VideoUnavailableException("unavailable"),
                VideoRequiresPurchaseException("purchase", "preview")
            )

            videoExceptions.forEach { e ->
                e.shouldBeInstanceOf<VideoUnplayableException>()
            }
        }
    }
}
