package com.github.kotlintubeexplode.search

import com.github.kotlintubeexplode.channels.ChannelId
import com.github.kotlintubeexplode.core.VideoId
import com.github.kotlintubeexplode.playlists.PlaylistId
import com.github.kotlintubeexplode.common.Author
import com.github.kotlintubeexplode.common.Thumbnail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@DisplayName("Search Results")
class SearchResultTest {

    @Nested
    @DisplayName("SearchFilter")
    inner class SearchFilterTests {

        @Test
        fun `None filter should be default`() {
            val filter = SearchFilter.None
            filter shouldBe SearchFilter.None
        }

        @Test
        fun `Video filter should filter videos only`() {
            val filter = SearchFilter.Video
            filter shouldBe SearchFilter.Video
        }

        @Test
        fun `Playlist filter should filter playlists only`() {
            val filter = SearchFilter.Playlist
            filter shouldBe SearchFilter.Playlist
        }

        @Test
        fun `Channel filter should filter channels only`() {
            val filter = SearchFilter.Channel
            filter shouldBe SearchFilter.Channel
        }
    }

    @Nested
    @DisplayName("VideoSearchResult")
    inner class VideoSearchResultTests {

        @Test
        fun `should create video search result`() {
            val result = VideoSearchResult(
                id = VideoId("dQw4w9WgXcQ"),
                title = "Rick Astley - Never Gonna Give You Up",
                author = Author("UCuAXFkgsw1L7xaCfnd5JJOw", "RickAstleyVEVO"),
                duration = 3.minutes + 33.seconds,
                thumbnails = listOf(
                    Thumbnail("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", 480, 360)
                )
            )

            result.shouldBeInstanceOf<ISearchResult>()
            result.id.value shouldBe "dQw4w9WgXcQ"
            result.title shouldBe "Rick Astley - Never Gonna Give You Up"
            result.author.channelTitle shouldBe "RickAstleyVEVO"
            result.duration shouldBe 3.minutes + 33.seconds
        }

        @Test
        fun `should generate correct URL`() {
            val result = VideoSearchResult(
                id = VideoId("dQw4w9WgXcQ"),
                title = "Test",
                author = Author("UC123", "Test"),
                duration = null,
                thumbnails = emptyList()
            )

            result.url shouldBe "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        }

        @Test
        fun `should handle null duration for live streams`() {
            val result = VideoSearchResult(
                id = VideoId("liveVideoId"),
                title = "Live Stream",
                author = Author("UC123", "Streamer"),
                duration = null,
                thumbnails = emptyList()
            )

            result.duration shouldBe null
        }
    }

    @Nested
    @DisplayName("PlaylistSearchResult")
    inner class PlaylistSearchResultTests {

        @Test
        fun `should create playlist search result`() {
            val result = PlaylistSearchResult(
                id = PlaylistId("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"),
                title = "My Playlist",
                author = Author("UCuAXFkgsw1L7xaCfnd5JJOw", "ChannelName"),
                thumbnails = listOf(
                    Thumbnail("https://i.ytimg.com/vi/xxx/hqdefault.jpg", 480, 360)
                )
            )

            result.shouldBeInstanceOf<ISearchResult>()
            result.id.value shouldBe "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
            result.title shouldBe "My Playlist"
            result.author?.channelTitle shouldBe "ChannelName"
        }

        @Test
        fun `should generate correct URL`() {
            val result = PlaylistSearchResult(
                id = PlaylistId("PLxxxxxxxx"),
                title = "Test",
                author = null,
                thumbnails = emptyList()
            )

            result.url shouldBe "https://www.youtube.com/playlist?list=PLxxxxxxxx"
        }

        @Test
        fun `should handle null author for auto-generated playlists`() {
            val result = PlaylistSearchResult(
                id = PlaylistId("RDxxxxxxxx"),
                title = "Mix - Something",
                author = null,
                thumbnails = emptyList()
            )

            result.author shouldBe null
        }
    }

    @Nested
    @DisplayName("ChannelSearchResult")
    inner class ChannelSearchResultTests {

        @Test
        fun `should create channel search result`() {
            val result = ChannelSearchResult(
                id = ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw"),
                title = "RickAstleyVEVO",
                thumbnails = listOf(
                    Thumbnail("https://yt3.ggpht.com/xxx", 88, 88)
                )
            )

            result.shouldBeInstanceOf<ISearchResult>()
            result.id.value shouldBe "UCuAXFkgsw1L7xaCfnd5JJOw"
            result.title shouldBe "RickAstleyVEVO"
        }

        @Test
        fun `should generate correct URL`() {
            val result = ChannelSearchResult(
                id = ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw"),
                title = "Test",
                thumbnails = emptyList()
            )

            result.url shouldBe "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"
        }
    }

    @Nested
    @DisplayName("ISearchResult polymorphism")
    inner class PolymorphismTests {

        @Test
        fun `should handle mixed search results`() {
            val results: List<ISearchResult> = listOf(
                VideoSearchResult(
                    id = VideoId("videoId"),
                    title = "Video",
                    author = Author("UC123", "Author"),
                    duration = 1.minutes,
                    thumbnails = emptyList()
                ),
                PlaylistSearchResult(
                    id = PlaylistId("PLxxxxxxxx"),
                    title = "Playlist",
                    author = null,
                    thumbnails = emptyList()
                ),
                ChannelSearchResult(
                    id = ChannelId("UCxxxxxxxx"),
                    title = "Channel",
                    thumbnails = emptyList()
                )
            )

            results.size shouldBe 3

            val video = results.filterIsInstance<VideoSearchResult>().first()
            video.title shouldBe "Video"

            val playlist = results.filterIsInstance<PlaylistSearchResult>().first()
            playlist.title shouldBe "Playlist"

            val channel = results.filterIsInstance<ChannelSearchResult>().first()
            channel.title shouldBe "Channel"
        }
    }
}
