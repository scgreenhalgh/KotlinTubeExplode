package com.github.kotlintubeexplode.integration

import com.github.kotlintubeexplode.client.YoutubeClient
import com.github.kotlintubeexplode.exceptions.PlaylistUnavailableException
import com.github.kotlintubeexplode.playlists.PlaylistId
import com.github.kotlintubeexplode.testdata.PlaylistIds
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for playlist pagination against live YouTube.
 *
 * Test playlist IDs come from the shared [com.github.kotlintubeexplode.testdata.PlaylistIds]
 * object, which mirrors upstream YoutubeExplode's TestData/PlaylistIds.cs.
 *
 * Test cases derived from upstream's PlaylistSpecs.cs.
 */
@Tag("integration")
@DisplayName("Playlist Integration Tests")
class PlaylistIntegrationTest {

    private val client = YoutubeClient()

    @Nested
    @DisplayName("Mix playlists (RD prefix)")
    inner class MixPlaylistTests {

        @Test
        fun `should paginate Video Mix playlist`() = runTest(timeout = 60.seconds) {
            val videos = client.playlists
                .getVideos(PlaylistId.parse(PlaylistIds.VideoMix))
                .take(20)
                .toList()

            println("Fetched ${videos.size} videos from Video Mix ${PlaylistIds.VideoMix}")
            videos.size shouldBeGreaterThanOrEqual 20
        }

        @Test
        fun `should paginate Music Mix playlist`() = runTest(timeout = 60.seconds) {
            val videos = client.playlists
                .getVideos(PlaylistId.parse(PlaylistIds.MusicMix))
                .take(20)
                .toList()

            println("Fetched ${videos.size} videos from Music Mix ${PlaylistIds.MusicMix}")
            videos.shouldNotBeEmpty()
        }
    }

    @Nested
    @DisplayName("Normal playlists")
    inner class NormalPlaylistTests {

        @Test
        fun `should fetch playlist metadata`() = runTest(timeout = 60.seconds) {
            val playlist = client.playlists.get(PlaylistId.parse(PlaylistIds.Normal))
            playlist.title.shouldNotBeBlank()
        }

        @Test
        fun `should paginate normal playlist`() = runTest(timeout = 60.seconds) {
            val videos = client.playlists
                .getVideos(PlaylistId.parse(PlaylistIds.Normal))
                .take(20)
                .toList()

            videos.shouldNotBeEmpty()
        }
    }

    @Nested
    @DisplayName("Large playlists")
    inner class LargePlaylistTests {

        @Test
        fun `should paginate beyond first batch`() = runTest(timeout = 120.seconds) {
            val videos = client.playlists
                .getVideos(PlaylistId.parse(PlaylistIds.Large))
                .take(150)
                .toList()

            println("Fetched ${videos.size} videos from large playlist ${PlaylistIds.Large}")
            // Beyond a single batch (typically ~100 videos per batch); confirms pagination is wired.
            videos.size shouldBeGreaterThanOrEqual 150
        }
    }

    @Nested
    @DisplayName("Various playlist types")
    inner class VariousPlaylistTypeTests {
        // Mirror of upstream's I_can_get_videos_included_in_any_available_playlist:
        // verifies pagination works across the full spectrum of playlist shapes.

        @ParameterizedTest(name = "should yield videos from {0}")
        @ValueSource(strings = [
            PlaylistIds.Normal,
            PlaylistIds.MusicMix,
            PlaylistIds.VideoMix,
            PlaylistIds.MusicAlbum,
            PlaylistIds.UserUploads,
            PlaylistIds.Weird,
            PlaylistIds.ContainsLongVideos,
            PlaylistIds.ContainsUnavailableVideos,
        ])
        fun `should paginate playlist`(playlistId: String) = runTest(timeout = 60.seconds) {
            val videos = client.playlists
                .getVideos(PlaylistId.parse(playlistId))
                .take(50)
                .toList()

            videos.shouldNotBeEmpty()
        }
    }

    @Nested
    @DisplayName("Unavailable playlists")
    inner class UnavailablePlaylistTests {
        // Mirror of upstream's I_can_try_to_get_*_and_get_an_error_*:
        // confirms PlaylistUnavailableException propagates for private and non-existing IDs.

        @Test
        fun `should throw for private playlist`() = runTest(timeout = 60.seconds) {
            shouldThrow<PlaylistUnavailableException> {
                client.playlists
                    .getVideos(PlaylistId.parse(PlaylistIds.Private))
                    .take(1)
                    .toList()
            }
        }

        @Test
        fun `should throw for non-existing playlist`() = runTest(timeout = 60.seconds) {
            shouldThrow<PlaylistUnavailableException> {
                client.playlists
                    .getVideos(PlaylistId.parse(PlaylistIds.NonExisting))
                    .take(1)
                    .toList()
            }
        }
    }
}
