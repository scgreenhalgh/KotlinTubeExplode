package com.github.kotlintubeexplode.videos

import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Resolution
import com.github.kotlintubeexplode.common.Thumbnail
import com.github.kotlintubeexplode.core.VideoId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@DisplayName("Video domain models")
class VideoTest {

    @Nested
    @DisplayName("Video")
    inner class VideoTests {

        @Test
        fun `should create video with correct URL`() {
            val video = createTestVideo()

            video.url shouldBe "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        }

        @Test
        fun `should identify non-live video`() {
            val video = createTestVideo(durationSeconds = 212L)

            video.isLiveStream shouldBe false
            video.duration shouldBe 212.seconds
        }

        @Test
        fun `should identify live stream`() {
            val video = createTestVideo(durationSeconds = null)

            video.isLiveStream shouldBe true
            video.duration shouldBe null
        }
    }

    @Nested
    @DisplayName("Author")
    inner class AuthorTests {

        @Test
        fun `should generate correct channel URL`() {
            val author = Author("UC38IQsAvIsxxjztdMZQtwHA", "RickAstleyVEVO")

            author.channelUrl shouldBe "https://www.youtube.com/channel/UC38IQsAvIsxxjztdMZQtwHA"
        }
    }

    @Nested
    @DisplayName("Thumbnail")
    inner class ThumbnailTests {

        @Test
        fun `should calculate resolution correctly`() {
            val thumbnail = Thumbnail("https://example.com/thumb.jpg", 1920, 1080)

            thumbnail.resolution.width shouldBe 1920
            thumbnail.resolution.height shouldBe 1080
            thumbnail.resolution.area shouldBe 2073600
            thumbnail.resolution.toString() shouldBe "1920x1080"
        }
    }

    @Nested
    @DisplayName("Engagement")
    inner class EngagementTests {

        @Test
        fun `should calculate average rating with likes only`() {
            val engagement = Engagement(viewCount = 1000000, likeCount = 50000, dislikeCount = 0)

            engagement.averageRating shouldBe 5.0
        }

        @Test
        fun `should calculate average rating with mixed ratings`() {
            val engagement = Engagement(viewCount = 1000000, likeCount = 80, dislikeCount = 20)

            // 1 + 4 * 80 / 100 = 1 + 3.2 = 4.2
            engagement.averageRating shouldBe 4.2
        }

        @Test
        fun `should return zero rating when no votes`() {
            val engagement = Engagement(viewCount = 1000000, likeCount = 0, dislikeCount = 0)

            engagement.averageRating shouldBe 0.0
        }
    }

    @Nested
    @DisplayName("Resolution")
    inner class ResolutionTests {

        @Test
        fun `should compare resolutions by area`() {
            val hd = Resolution(1920, 1080)
            val sd = Resolution(640, 480)

            (hd.area > sd.area) shouldBe true
        }
    }

    private fun createTestVideo(
        durationSeconds: Long? = 212L
    ): Video = Video.create(
        id = VideoId.parse("dQw4w9WgXcQ"),
        title = "Rick Astley - Never Gonna Give You Up",
        author = Author("UC38IQsAvIsxxjztdMZQtwHA", "RickAstleyVEVO"),
        uploadDate = "2009-10-25",
        description = "Official video",
        durationSeconds = durationSeconds,
        thumbnails = listOf(
            Thumbnail("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", 1280, 720)
        ),
        keywords = listOf("rick", "astley", "never", "gonna", "give", "you", "up"),
        engagement = Engagement(viewCount = 1400000000, likeCount = 15000000, dislikeCount = 0)
    )
}
