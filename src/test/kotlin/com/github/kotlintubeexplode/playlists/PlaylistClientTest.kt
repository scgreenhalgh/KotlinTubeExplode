package com.github.kotlintubeexplode.playlists

import com.github.kotlintubeexplode.internal.HttpController
import com.github.kotlintubeexplode.internal.PlaylistController
import com.github.kotlintubeexplode.internal.dto.PlaylistNextResponseDto
import com.github.kotlintubeexplode.internal.dto.PlaylistPanelVideoRendererDto
import com.github.kotlintubeexplode.internal.dto.WatchEndpointDto
import com.github.kotlintubeexplode.internal.dto.NavigationEndpointDto
import com.github.kotlintubeexplode.internal.dto.TextRunDto
import com.github.kotlintubeexplode.internal.dto.TextRunsDto
import com.github.kotlintubeexplode.internal.dto.BrowseEndpointDto
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PlaylistClient")
class PlaylistClientTest {

    private val playlistId = PlaylistId.parse("PLI5YfMzCfRtZ8eV576YoY3vIYrHjyVm_e")

    @Nested
    @DisplayName("getVideoBatches")
    inner class GetVideoBatchesTests {

        @Test
        fun `should continue iteration when an entire batch is filtered out`() = runTest {
            // The bug: cursor advances on every entry processed, but newVideos only grows on
            // entries that pass the dedupe + author skip checks. If a whole batch is filtered
            // (e.g., 5 deleted videos with no byline followed by valid videos), the prior code
            // would `break` after the empty filtered batch — silently truncating the playlist.

            val http = mockk<HttpController>(relaxed = true)
            val ctrl = mockk<PlaylistController>()

            // First response: 3 unparseable entries (no author info — would all be skipped).
            // Cursor moves to index 3.
            val skippedBatch = PlaylistNextResponseDto(
                contents = makeContents(
                    (1..3).map { idx ->
                        // Has videoId + index, but no longByline / shortByline → both author
                        // fields null → skip-on-missing.
                        PlaylistPanelVideoRendererDto(
                            videoId = "skip$idx",
                            navigationEndpoint = NavigationEndpointDto(
                                watchEndpoint = WatchEndpointDto(index = idx)
                            )
                        )
                    }
                )
            )

            // Second response: 2 valid entries, indices 4 and 5.
            val validBatch = PlaylistNextResponseDto(
                contents = makeContents(
                    (4..5).map { idx ->
                        validRenderer(videoId = "good$idx", index = idx, author = "Author $idx")
                    }
                )
            )

            // Third response: empty (genuine end of playlist).
            val emptyBatch = PlaylistNextResponseDto(contents = makeContents(emptyList()))

            coEvery {
                ctrl.getPlaylistNextResponse(playlistId, null, 0, null)
            } returns skippedBatch

            coEvery {
                ctrl.getPlaylistNextResponse(playlistId, "skip3", 3, any())
            } returns validBatch

            coEvery {
                ctrl.getPlaylistNextResponse(playlistId, "good5", 5, any())
            } returns emptyBatch

            val client = PlaylistClient(http, ctrl)
            val videos = client.getVideoBatches(playlistId).toList()
                .flatMap { it.items }

            videos shouldHaveSize 2
            videos[0].id.value shouldBe "good4"
            videos[1].id.value shouldBe "good5"
        }

        @Test
        fun `should stop when response yields no new entries`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val ctrl = mockk<PlaylistController>()

            val firstBatch = PlaylistNextResponseDto(
                contents = makeContents(listOf(
                    validRenderer(videoId = "a", index = 1, author = "Author A"),
                    validRenderer(videoId = "b", index = 2, author = "Author B"),
                ))
            )
            val emptyBatch = PlaylistNextResponseDto(contents = makeContents(emptyList()))

            coEvery {
                ctrl.getPlaylistNextResponse(playlistId, null, 0, null)
            } returns firstBatch

            coEvery {
                ctrl.getPlaylistNextResponse(playlistId, "b", 2, any())
            } returns emptyBatch

            val client = PlaylistClient(http, ctrl)
            val videos = client.getVideoBatches(playlistId).toList()
                .flatMap { it.items }

            videos shouldHaveSize 2
        }
    }

    private fun makeContents(renderers: List<PlaylistPanelVideoRendererDto>) =
        com.github.kotlintubeexplode.internal.dto.NextContentsDto(
            twoColumnWatchNextResults = com.github.kotlintubeexplode.internal.dto.TwoColumnWatchNextResultsDto(
                playlist = com.github.kotlintubeexplode.internal.dto.NextPlaylistContainerDto(
                    playlist = com.github.kotlintubeexplode.internal.dto.PlaylistPanelDto(
                        contents = renderers.map {
                            com.github.kotlintubeexplode.internal.dto.PlaylistPanelEntryDto(
                                playlistPanelVideoRenderer = it
                            )
                        }
                    )
                )
            )
        )

    private fun validRenderer(videoId: String, index: Int, author: String) =
        PlaylistPanelVideoRendererDto(
            videoId = videoId,
            title = TextRunsDto(simpleText = "Title $videoId"),
            longBylineText = TextRunsDto(
                runs = listOf(
                    TextRunDto(
                        text = author,
                        navigationEndpoint = NavigationEndpointDto(
                            browseEndpoint = BrowseEndpointDto(browseId = "UC$author")
                        )
                    )
                )
            ),
            navigationEndpoint = NavigationEndpointDto(
                watchEndpoint = WatchEndpointDto(index = index)
            )
        )
}
