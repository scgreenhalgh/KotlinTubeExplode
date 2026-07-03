package com.github.kotlintubeexplode.internal

import com.github.kotlintubeexplode.playlists.PlaylistId
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PlaylistController")
class PlaylistControllerTest {

    private val playlistId = PlaylistId.parse("PLI5YfMzCfRtZ8eV576YoY3vIYrHjyVm_e")

    // Minimal "available" next-response so getPlaylistNextResponse returns after a single POST
    // (isAvailable == contents.twoColumnWatchNextResults.playlist.playlist != null).
    private val availableResponseJson =
        """{"contents":{"twoColumnWatchNextResults":{"playlist":{"playlist":{"contents":[]}}}}}"""

    @Nested
    @DisplayName("next request body")
    inner class NextRequestBodyTests {

        @Test
        fun `emits visitorData null when unset to match upstream Json_Encode`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val bodySlot = slot<String>()
            coEvery { http.postJson(any(), capture(bodySlot), any()) } returns availableResponseJson

            val controller = PlaylistController(http)
            controller.getPlaylistNextResponse(
                playlistId = playlistId,
                videoId = null,
                index = 0,
                visitorData = null
            )

            val client = Json.parseToJsonElement(bodySlot.captured)
                .jsonObject["context"]!!
                .jsonObject["client"]!!
                .jsonObject

            // Upstream always writes "visitorData": Json.Encode(visitorData); a null visitorData
            // serializes to a literal JSON null (key present, value null). Current code omits the
            // key entirely via if (!visitorData.isNullOrBlank()), so containsKey is false today.
            client.containsKey("visitorData") shouldBe true
            client["visitorData"] shouldBe JsonNull
        }
    }
}
