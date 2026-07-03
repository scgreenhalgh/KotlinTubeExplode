package com.github.kotlintubeexplode.search

import com.github.kotlintubeexplode.internal.HttpController
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SearchClient")
class SearchClientTest {

    private fun clientContext(body: String): JsonObject =
        Json.parseToJsonElement(body).jsonObject["context"]!!.jsonObject["client"]!!.jsonObject

    @Nested
    @DisplayName("request body")
    inner class RequestBodyTests {

        // drift #8: upstream SearchController writes "utcOffsetMinutes": 0 into the client context.
        @Test
        fun `search context includes utcOffsetMinutes 0 to match upstream`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val bodySlot = slot<String>()
            coEvery { http.postJson(any(), capture(bodySlot), any()) } returns """{"contents":{}}"""

            SearchClient(http).getResultBatches("test", SearchFilter.None).toList()

            val client = clientContext(bodySlot.captured)
            client.containsKey("utcOffsetMinutes") shouldBe true
            client["utcOffsetMinutes"]!!.jsonPrimitive.int shouldBe 0
        }

        // drift #24: upstream pins the WEB clientVersion at the older, wider-tested 2.20210408.08.00.
        @Test
        fun `search uses upstream WEB clientVersion`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val bodySlot = slot<String>()
            coEvery { http.postJson(any(), capture(bodySlot), any()) } returns """{"contents":{}}"""

            SearchClient(http).getResultBatches("test", SearchFilter.None).toList()

            val client = clientContext(bodySlot.captured)
            client["clientVersion"]!!.jsonPrimitive.content shouldBe "2.20210408.08.00"
        }

        // drift #25: upstream sends ONE body shape for both the initial and continuation
        // calls -- query + params + continuation (null on the first call) + context. Today
        // the initial call omits `continuation` and the continuation call omits `query`.
        @Test
        fun `initial and continuation requests share one body shape`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            val bodies = mutableListOf<String>()
            coEvery { http.postJson(any(), capture(bodies), any()) } returnsMany listOf(
                """{"contents":{},"continuationCommand":{"token":"TOKEN123"}}""",
                """{"contents":{}}"""
            )

            SearchClient(http).getResultBatches("kotlin", SearchFilter.None).toList()

            bodies.size shouldBe 2

            val first = Json.parseToJsonElement(bodies[0]).jsonObject
            val second = Json.parseToJsonElement(bodies[1]).jsonObject

            // Initial call carries a literal continuation: null, plus the query.
            first.containsKey("continuation") shouldBe true
            first["continuation"] shouldBe JsonNull
            first["query"]!!.jsonPrimitive.content shouldBe "kotlin"

            // Continuation call reuses the same shape: query stays present and the token is set.
            second["query"]!!.jsonPrimitive.content shouldBe "kotlin"
            second["continuation"]!!.jsonPrimitive.content shouldBe "TOKEN123"
        }
    }

    @Nested
    @DisplayName("lockupViewModel playlist parsing")
    inner class LockupPlaylistTests {

        // A playlist result in YouTube's newer lockupViewModel format.
        private fun playlistLockup(
            contentId: String = "PLtest1234567",
            title: String = "Cool Kotlin Playlist"
        ) = """
            {
              "lockupViewModel": {
                "contentId": "$contentId",
                "contentType": "LOCKUP_CONTENT_TYPE_PLAYLIST",
                "metadata": {
                  "lockupMetadataViewModel": {
                    "title": { "content": "$title" },
                    "metadata": {
                      "contentMetadataViewModel": {
                        "metadataRows": [
                          {
                            "metadataParts": [
                              {
                                "text": {
                                  "content": "Kotlin Channel",
                                  "commandRuns": [
                                    {
                                      "onTap": {
                                        "innertubeCommand": {
                                          "browseEndpoint": { "browseId": "UCcoolkotlin000000000" }
                                        }
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        ]
                      }
                    }
                  }
                },
                "contentImage": {
                  "collectionThumbnailViewModel": {
                    "primaryThumbnail": {
                      "thumbnailViewModel": {
                        "image": {
                          "sources": [
                            { "url": "https://i.ytimg.com/vi/abc/hqdefault.jpg", "width": 480, "height": 270 }
                          ]
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        // A video result in lockupViewModel format -- must NOT be parsed as a playlist.
        private val videoLockup = """
            {
              "lockupViewModel": {
                "contentId": "vid12345678",
                "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
                "metadata": {
                  "lockupMetadataViewModel": { "title": { "content": "Some Video" } }
                }
              }
            }
        """.trimIndent()

        // drift #4: parse the new lockupViewModel renderer so playlist results aren't dropped.
        @Test
        fun `parses a playlist from lockupViewModel`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            coEvery { http.postJson(any(), any(), any()) } returns
                """{"contents":{"itemSectionRenderer":{"contents":[${playlistLockup()}]}}}"""

            val results = SearchClient(http).getPlaylists("kotlin").toList()

            results.size shouldBe 1
            val p = results[0]
            p.id.value shouldBe "PLtest1234567"
            p.title shouldBe "Cool Kotlin Playlist"
            p.author?.channelId shouldBe "UCcoolkotlin000000000"
            p.author?.channelTitle shouldBe "Kotlin Channel"
            p.thumbnails.size shouldBe 1
            p.thumbnails[0].url shouldBe "https://i.ytimg.com/vi/abc/hqdefault.jpg"
            p.thumbnails[0].width shouldBe 480
            p.thumbnails[0].height shouldBe 270
        }

        // drift #4: lockupViewModel is a generic renderer; only playlist-type lockups are playlists.
        @Test
        fun `ignores non-playlist lockupViewModel entries`() = runTest {
            val http = mockk<HttpController>(relaxed = true)
            coEvery { http.postJson(any(), any(), any()) } returns
                """{"contents":{"itemSectionRenderer":{"contents":[${playlistLockup()},$videoLockup]}}}"""

            val results = SearchClient(http).getPlaylists("kotlin").toList()

            results.size shouldBe 1
            results[0].id.value shouldBe "PLtest1234567"
        }
    }
}
