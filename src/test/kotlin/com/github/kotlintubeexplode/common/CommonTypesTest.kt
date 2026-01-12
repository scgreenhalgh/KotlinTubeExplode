package com.github.kotlintubeexplode.common

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Common types")
class CommonTypesTest {

    @Nested
    @DisplayName("Resolution")
    inner class ResolutionTests {

        @Test
        fun `should create resolution with width and height`() {
            val res = Resolution(1920, 1080)

            res.width shouldBe 1920
            res.height shouldBe 1080
        }

        @Test
        fun `area should be width times height`() {
            val res = Resolution(1920, 1080)

            res.area shouldBe 1920 * 1080
        }

        @Test
        fun `toString should show WxH format`() {
            val res = Resolution(1920, 1080)

            res.toString() shouldBe "1920x1080"
        }

        @Test
        fun `should be comparable`() {
            val sd = Resolution(854, 480)
            val hd = Resolution(1280, 720)
            val fullHd = Resolution(1920, 1080)

            (sd < hd) shouldBe true
            (hd < fullHd) shouldBe true
            (fullHd > sd) shouldBe true
        }

        @Test
        fun `should have common resolution constants`() {
            Resolution.HD_720.width shouldBe 1280
            Resolution.HD_1080.width shouldBe 1920
            Resolution.UHD_4K.width shouldBe 3840
        }
    }

    @Nested
    @DisplayName("Thumbnail")
    inner class ThumbnailTests {

        @Test
        fun `should create thumbnail with url and dimensions`() {
            val thumb = Thumbnail("https://example.com/thumb.jpg", 480, 360)

            thumb.url shouldBe "https://example.com/thumb.jpg"
            thumb.width shouldBe 480
            thumb.height shouldBe 360
        }

        @Test
        fun `resolution should return Resolution object`() {
            val thumb = Thumbnail("https://example.com/thumb.jpg", 1280, 720)

            thumb.resolution.width shouldBe 1280
            thumb.resolution.height shouldBe 720
        }

        @Test
        fun `getWithHighestResolution should find largest`() {
            val thumbnails = listOf(
                Thumbnail("small.jpg", 120, 90),
                Thumbnail("medium.jpg", 480, 360),
                Thumbnail("large.jpg", 1280, 720)
            )

            val best = thumbnails.getWithHighestResolution()
            best shouldNotBe null
            best?.url shouldBe "large.jpg"
        }

        @Test
        fun `getWithLowestResolution should find smallest`() {
            val thumbnails = listOf(
                Thumbnail("small.jpg", 120, 90),
                Thumbnail("medium.jpg", 480, 360),
                Thumbnail("large.jpg", 1280, 720)
            )

            val smallest = thumbnails.getWithLowestResolution()
            smallest shouldNotBe null
            smallest?.url shouldBe "small.jpg"
        }

        @Test
        fun `getClosestTo should find closest resolution`() {
            val thumbnails = listOf(
                Thumbnail("small.jpg", 120, 90),
                Thumbnail("medium.jpg", 480, 360),
                Thumbnail("large.jpg", 1280, 720)
            )

            val closest = thumbnails.getClosestTo(500, 400)
            closest shouldNotBe null
            closest?.url shouldBe "medium.jpg"
        }

        @Test
        fun `extension functions should return null for empty list`() {
            val empty = emptyList<Thumbnail>()

            empty.getWithHighestResolution() shouldBe null
            empty.getWithLowestResolution() shouldBe null
            empty.getClosestTo(100, 100) shouldBe null
        }
    }

    @Nested
    @DisplayName("Author")
    inner class AuthorTests {

        @Test
        fun `should create author with id and title`() {
            val author = Author("UCxxxxxx", "Channel Name")

            author.channelId shouldBe "UCxxxxxx"
            author.channelTitle shouldBe "Channel Name"
        }

        @Test
        fun `channelUrl should be correct`() {
            val author = Author("UCuAXFkgsw1L7xaCfnd5JJOw", "Test Channel")

            author.channelUrl shouldBe "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
        }

        @Test
        @Suppress("DEPRECATION")
        fun `deprecated title property should return channelTitle`() {
            val author = Author("UC123", "My Channel")

            author.title shouldBe "My Channel"
        }
    }
}
